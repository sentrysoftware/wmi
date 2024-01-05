package org.sentrysoftware.wmi.wbem;

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

import org.sentrysoftware.wmi.exceptions.WmiComException;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.COM.COMUtils;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WinError;
import com.sun.jna.platform.win32.WinNT.HRESULT;

/**
 * Class with various static methods to help with COM interaction.
 */
public class WmiComHelper {

	/**
	 * Private constructor, as this class cannot be instantiated (it's pure static)
	 */
	private WmiComHelper() {}

	/**
	 * Whether COM Library has been initialized for current Thread
	 */
	private static ThreadLocal<Boolean> comLibraryInitialized = ThreadLocal.withInitial(() -> false);

	/**
	 * Initializes COM library and sets security to impersonate the local userName.
	 * This function needs to be called at least once for each thread, as COM must be
	 * initialized for each thread. You can call this function even if COM has already
	 * been initialized. It will verify first whether COM initialization has already
	 * been done for this thread before proceeding.
	 * The threading model is <b>multi-threaded</b>.
	 * @throws WmiComException when COM library cannot be instantiated
	 * @throws IllegalStateException when COM library has already been initialized with a different
	 * threading model
	 */
	public static void initializeComLibrary() throws WmiComException {

		// Do nothing if COM Library already initialized
		if (Boolean.TRUE.equals(comLibraryInitialized.get())) {
			return;
		}

		// Initialize COM parameters
		// Step 1 from: <a href="https://docs.microsoft.com/en-us/windows/win32/wmisdk/example--getting-wmi-data-from-a-remote-computer">Example: Getting WMI Data from a Remote Computer</a>
		final HRESULT hCoInitResult = Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_MULTITHREADED);
		final int coInitResult = hCoInitResult.intValue();
		if (coInitResult == WinError.RPC_E_CHANGED_MODE) {
			throw new IllegalStateException(
					"CoInitializeEx() has already been called with a different threading model");
		} else if (coInitResult != COMUtils.S_OK && coInitResult != COMUtils.S_FALSE) {
			throw new WmiComException(
					"Failed to initialize the COM Library (HRESULT=0x%s)", Integer.toHexString(coInitResult));
		}

		// Initialize COM process security
		// Step 2 from: <a href="https://docs.microsoft.com/en-us/windows/win32/wmisdk/example--getting-wmi-data-from-a-remote-computer">Example: Getting WMI Data from a Remote Computer</a>
		final HRESULT hResult = Ole32.INSTANCE.CoInitializeSecurity(
				null,
				-1,
				null,
				null,
				Ole32.RPC_C_AUTHN_LEVEL_DEFAULT,
				Ole32.RPC_C_IMP_LEVEL_IMPERSONATE,
				null,
				Ole32.EOAC_NONE,
				null
		);

		// If security was already initialized, we'll get RPC_E_TOO_LATE
		// which can be safely ignored
		if (COMUtils.FAILED(hResult) && hResult.intValue() != WinError.RPC_E_TOO_LATE) {
			unInitializeCom();
			throw new WmiComException(
					"Failed to initialize security (HRESULT=0x%s)", Integer.toHexString(hResult.intValue()));
		}

		// We're good!
		comLibraryInitialized.set(true);

	}

	/**
	 * UnInitialize the COM library.
	 */
	public static void unInitializeCom() {

		// Do nothing if COM has not been initialized
		if (Boolean.FALSE.equals(comLibraryInitialized.get())) {
			return;
		}

		Ole32.INSTANCE.CoUninitialize();
		comLibraryInitialized.set(false);

	}

	/**
	 * @return whether COM has been initialized for the current thread
	 */
	public static boolean isComInitialized() {
		return comLibraryInitialized.get();
	}


	/**
	 * Same function than in com.sun.jna.platform.win32.COM.COMInvoker._invokeNativeInt
	 *
	 * @param contextPointer
	 * @param vtableId
	 * @param args
	 * @param returnType
	 * @return whatever the specified function returns
	 */
	public static Object comInvokerInvokeNativeObject(
			final Pointer contextPointer,
			final int vtableId,
			final Object[] args,
			final Class<?> returnType
	) {

		final Pointer vptr = contextPointer.getPointer(0);
		final com.sun.jna.Function func = com.sun.jna.Function.getFunction(vptr.getPointer(vtableId * Native.POINTER_SIZE * 1L));
		return func.invoke(returnType, args);

	}

}
