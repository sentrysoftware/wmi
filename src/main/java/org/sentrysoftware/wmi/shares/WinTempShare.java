package org.sentrysoftware.wmi.shares;

/*-
 * ╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲
 * WMI Java Client
 * ჻჻჻჻჻჻
 * Copyright (C) 2023 - 2024 Sentry Software
 * ჻჻჻჻჻჻
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱╲╱
 */

import org.sentrysoftware.wmi.Utils;
import org.sentrysoftware.wmi.WmiHelper;
import org.sentrysoftware.wmi.exceptions.WindowsRemoteException;
import org.sentrysoftware.wmi.windows.remote.WindowsRemoteExecutor;
import org.sentrysoftware.wmi.windows.remote.share.WindowsTempShare;
import org.sentrysoftware.wmi.exceptions.WmiComException;
import org.sentrysoftware.wmi.wbem.WmiWbemServices;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.COM.WbemcliUtil;
import com.sun.jna.platform.win32.COM.util.Factory;
import com.sun.jna.platform.win32.LMShare.SHARE_INFO_502;
import com.sun.jna.platform.win32.WinDef.DWORD;
import com.sun.jna.platform.win32.WinNT.*;
import com.sun.jna.ptr.IntByReference;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Class representing a connection to a Windows share
 *
 */
public class WinTempShare extends WindowsTempShare implements AutoCloseable {

	private static final int CONNECTION_MAX = 10;
	private static final String SHARE_DESCRIPTION = "Share created by Sentry Software to store results of commands";
	private static final int SUCCESS = 0;
	private static final int ALREADY_EXIST = 2118;


	private static final Factory FACTORY = new Factory();
	private static final WindowsScriptHostNetworkInterface WSH = FACTORY.createObject(WindowsScriptHostNetworkInterface.class);;

	private static final ConcurrentHashMap<String, WinTempShare> CONNECTIONS_CACHE = new ConcurrentHashMap<>();

	/** The hostname of the server */
	private final String hostname;

	/** How many "clients" are using this instance */
	private final AtomicInteger useCount = new AtomicInteger(1);


	/**
	 * Constructor of WinTempShare
	 * <p>
	 * <b>DON'T USE IT OUTSIDE OF WinTempShare</b>
	 * <p>
	 * @param wmiWbemServices the WbemServices instance connected to the remote host
	 * @param shareNameOrUnc The name of the share, or its full UNC path
	 * @param remotePath The path on the remote system of the directory being shared
	 */
	WinTempShare(
			final WmiWbemServices wmiWbemServices,
			final String shareNameOrUnc,
			final String remotePath) {
		super(wmiWbemServices, shareNameOrUnc, remotePath);
		this.hostname = wmiWbemServices.getHostname();
	}

	/**
	 * Create or get a cached instance of a shared temporary directory on the specified host.
	 * <p>
	 * This method ensures only one instance of this class is created per host.
	 * @param hostname Host to connect to
	 * @param username Username (may be null)
	 * @param password Password (may be null)
	 * @param timeout Timeout in milliseconds
	 * @return the WinTempShare instance
	 *
	 * @throws TimeoutException To notify userName of timeout.
	 * @throws WmiComException For any problem encountered with JNA.
	 */
	public static WinTempShare getInstance(
			final String hostname,
			final String username,
			final char[] password,
			final long timeout
			) throws TimeoutException, WmiComException {

		Utils.checkNonNull(hostname, "hostname");
		Utils.checkArgumentNotZeroOrNegative(timeout, "timeout");

		try {
			return CONNECTIONS_CACHE.compute(
					hostname.toLowerCase(),
					(h, winTempShare) -> {
						if (winTempShare == null) {
							WmiWbemServices wmiWbemServices = null;

							try {

								// Get the WbemServices
								final String networkResource = WmiHelper.createNetworkResource(hostname, WbemcliUtil.DEFAULT_NAMESPACE);
								wmiWbemServices = WmiWbemServices.getInstance(networkResource, username, password);

								// Get the share if it exists, or create it
								final WindowsTempShare share = getOrCreateShare(
										wmiWbemServices,
										timeout,
										(w, r, s, t) -> {
											try {
												shareRemoteDirectory(w, r, s, t);
											} catch (final WindowsRemoteException e) {
												// Throw as a RuntimeException, so we catch it later (see below)
												throw new RuntimeException(e);
											}
										});

								// Connect to it
								getWindowsScriptHostNetwork().mapNetworkDrive(
										Utils.EMPTY,
										share.getUncSharePath(),
										false,
										username,
										password == null ? null : String.valueOf(password));

								return new WinTempShare(
										(WmiWbemServices) share.getWindowsRemoteExecutor(),
										share.getUncSharePath(),
										share.getRemotePath());

							} catch (final RuntimeException e) {
								if (wmiWbemServices != null) {
									wmiWbemServices.close();
								}

								throw e;

							} catch (final Exception e) {
								if (wmiWbemServices != null) {
									wmiWbemServices.close();
								}

								// Throw as a RuntimeException, so we catch it later (see below)
								throw new RuntimeException(e);
							}
						} else {

							// We already have this share
							synchronized (winTempShare) {

								// Increment the number of callers
								winTempShare.incrementUseCount();

								// And simply return the same reference (so that the map is not changed)
								return winTempShare;
							}
						}
					});
		} catch (final RuntimeException e) {
			final Throwable cause = e.getCause();
			if (cause != null) {
				if (cause instanceof TimeoutException) {
					throw (TimeoutException) cause;

				} else if (cause instanceof WmiComException) {
					throw (WmiComException) cause;

				} else if (cause instanceof InterruptedException) {
					throw new TimeoutException(cause.getClass().getSimpleName() + ": " + cause.getMessage());
				}
			}

			throw e;
		}
	}

	@Override
	public synchronized void close() {

		if (useCount.decrementAndGet() == 0) {
			CONNECTIONS_CACHE.remove(hostname.toLowerCase());

			((WmiWbemServices) getWindowsRemoteExecutor()).close();

			// Disconnect from the share
			getWindowsScriptHostNetwork().removeNetworkDrive(getUncSharePath(), true, false);
		}
	}


	/**
	 * @return whether we are connected to this share and can interact with it
	 */
	public boolean isConnected() {
		return useCount.get() > 0;
	}

	/**
	 * Check if it's connected. If not, throw an IllegalStateException.
	 */
	public void checkConnectedFirst() {
		if (!isConnected()) {
			throw new IllegalStateException("This instance has been closed and a new one must be created.");
		}
	}

	/**
	 * Share the remote directory on the host.
	 *
	 * @param wmiWbemServices WbemServices connected to the host
	 * @param remotePath The remote path.
	 * @param shareName The Share Name.
	 * @param timeout Timeout in milliseconds.
	 *
	 * @throws WmiComException For any problem encountered with JNA
	 */
	private static void shareRemoteDirectory(
			final WindowsRemoteExecutor wmiWbemServices,
			final String remotePath,
			final String shareName,
			final long timeout
			) throws WmiComException {

		final SECURITY_DESCRIPTOR securityDescriptor = initializeSecurityDescriptor();

		final SHARE_INFO_502 shareInfo502 = new SHARE_INFO_502();
		shareInfo502.shi502_netname = shareName;
		shareInfo502.shi502_type = LMShare.STYPE_DISKTREE;
		shareInfo502.shi502_remark = SHARE_DESCRIPTION;
		shareInfo502.shi502_permissions = LMAccess.ACCESS_ALL;
		shareInfo502.shi502_max_uses = CONNECTION_MAX;
		shareInfo502.shi502_current_uses = 0;
		shareInfo502.shi502_path = remotePath;
		shareInfo502.shi502_passwd = null;
		shareInfo502.shi502_reserved = 0;
		shareInfo502.shi502_security_descriptor = securityDescriptor.getPointer();

		// Write from struct to native memory.
		shareInfo502.write();

		final IntByReference parmErr = new IntByReference(0);
		final int result = Netapi32.INSTANCE.NetShareAdd(
				wmiWbemServices.getHostname(),
				502,
				shareInfo502.getPointer(),
				parmErr);
		if (result != SUCCESS && result != ALREADY_EXIST) {
			final Exception e = new Win32Exception(result);
			throw new WmiComException(e, e.getMessage());
		}
	}

	/**
	 * <p>Initialize a Security Descriptor with:
	 * <li>Full control permissions for system and administrator</li>
	 * <li>SE_DACL_PRESENT</li>
	 * <li>SE_DACL_DEFAULTED</li></p>
	 *
	 * @return The Security Descriptor
	 * @throws WmiComException For any problem encountered with JNA
	 */
	private static SECURITY_DESCRIPTOR initializeSecurityDescriptor()
			throws WmiComException {

		final PSID pSidSystem = createSID(
				WELL_KNOWN_SID_TYPE.WinLocalSystemSid);

		final PSID pSidAdministrator = createSID(
				WELL_KNOWN_SID_TYPE.WinBuiltinAdministratorsSid);

		final int sidSysLength = Advapi32.INSTANCE.GetLengthSid(pSidSystem);
		final int sidAdminLength = Advapi32.INSTANCE.GetLengthSid(pSidAdministrator);
		int cbAcl = Native.getNativeSize(ACL.class, null);
		cbAcl += Native.getNativeSize(ACCESS_ALLOWED_ACE.class, null);
		cbAcl += (sidSysLength - DWORD.SIZE);
		cbAcl += Native.getNativeSize(ACCESS_ALLOWED_ACE.class, null);
		cbAcl += (sidAdminLength - DWORD.SIZE);
		cbAcl = Advapi32Util.alignOnDWORD(cbAcl);
		final ACL pAcl = new ACL(cbAcl);

		checkWin32Result(
				Advapi32.INSTANCE.InitializeAcl(
						pAcl,
						cbAcl,
						WinNT.ACL_REVISION));

		checkWin32Result(
				Advapi32.INSTANCE.AddAccessAllowedAce(
						pAcl,
						WinNT.ACL_REVISION,
						WinNT.GENERIC_ALL,
						pSidSystem));

		checkWin32Result(
				Advapi32.INSTANCE.AddAccessAllowedAce(
						pAcl,
						WinNT.ACL_REVISION,
						WinNT.GENERIC_ALL,
						pSidAdministrator));

		final SECURITY_DESCRIPTOR securityDescriptor = new SECURITY_DESCRIPTOR(64 * 1024);

		checkWin32Result(
				Advapi32.INSTANCE.InitializeSecurityDescriptor(
						securityDescriptor,
						WinNT.SECURITY_DESCRIPTOR_REVISION));

		checkWin32Result(
				Advapi32.INSTANCE.SetSecurityDescriptorDacl(
						securityDescriptor,
						true,
						pAcl,
						false));

		return securityDescriptor;
	}

	/**
	 *  Create a SID for predefined SID type aliases.
	 *
	 * @param wellKnownSidTypeAlias Member of the WELL_KNOWN_SID_TYPE enumeration that specifies what the SID will identify.
	 * @return The new SID
	 * @throws WmiComException For any problem encountered with JNA.
	 */
	private static PSID createSID(final int wellKnownSidTypeAlias) throws WmiComException {
		final PSID pSid = new PSID(WinNT.SECURITY_MAX_SID_SIZE);
		final IntByReference cbSid = new IntByReference(WinNT.SECURITY_MAX_SID_SIZE);

		checkWin32Result(
				Advapi32.INSTANCE.CreateWellKnownSid(
						wellKnownSidTypeAlias,
						null,
						pSid,
						cbSid));
		return pSid;
	}

	/**
	 * Check the result from an Advanced 32 API function.
	 * Throw a WmiComException if the result is ko.
	 *
	 * @param result The result to check.
	 * @throws WmiComException For any problem encountered with JNA.
	 */
	private static void checkWin32Result(final boolean result) throws WmiComException {
		if (!result) {
			final Exception e = new Win32Exception(Kernel32.INSTANCE.GetLastError());
			throw new WmiComException(e, e.getMessage());
		}
	}

	int getUseCount() {
		return useCount.get();
	}

	void incrementUseCount() {
		useCount.incrementAndGet();
	}

	static WindowsScriptHostNetworkInterface getWindowsScriptHostNetwork() {
		return WSH;
	}

}
