package org.sentrysoftware.wmi.shares;

import org.sentrysoftware.wmi.Utils;
import org.sentrysoftware.wmi.windows.remote.WindowsRemoteExecutor;
import org.sentrysoftware.wmi.windows.remote.share.ShareRemoteDirectoryConsumer;
import org.sentrysoftware.wmi.windows.remote.share.WindowsTempShare;
import org.sentrysoftware.wmi.wbem.WmiWbemServices;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.WINDOWS)
class WinTempShareTest {

	@TempDir
	static Path tempDir;

	@SuppressWarnings("unchecked")
	@Test
	void testGetInstance() throws Exception {

		final WmiWbemServices wmiWbemServices = Mockito.mock(WmiWbemServices.class);
		final WindowsScriptHostNetworkInterface windowsScriptHostNetwork = Mockito.mock(WindowsScriptHostNetworkInterface.class);
		final String hostname = "localhost";
		final String networkResource = "\\\\localhost\\ROOT\\CIMV2";
		Mockito.when(wmiWbemServices.getHostname()).thenReturn(hostname);
		final String username = null;
		final char[] password = null;
		final long timeout = 60*1000;
		final String uncSharePath = "\\\\localhost\\test";
		final String remotePath = "c:\\windows\\temp";
		final WindowsTempShare windowsTempShare1 = new WindowsTempShare(wmiWbemServices, uncSharePath, remotePath);
		final WindowsTempShare windowsTempShare2 = new WindowsTempShare(wmiWbemServices, uncSharePath, remotePath);

		// check wbemServices hostname null
		assertThrows(IllegalArgumentException.class, () -> WinTempShare.getInstance(null, username, password, timeout));

		// check timeout negative or zero
		assertThrows(IllegalArgumentException.class, () -> WinTempShare.getInstance(hostname, username, password, -1));
		assertThrows(IllegalArgumentException.class, () -> WinTempShare.getInstance(hostname, username, password, 0));

		// check create instance
		try (final MockedStatic<WinTempShare> mockedWinTempShareClass = Mockito.mockStatic(WinTempShare.class);
				final MockedStatic<WindowsTempShare>mockedWindowsTempShare = Mockito.mockStatic(WindowsTempShare.class);
				final MockedStatic<WmiWbemServices> mockedWmiWbemServices = Mockito.mockStatic(WmiWbemServices.class)) {
			mockedWmiWbemServices.when(() -> WmiWbemServices.getInstance(networkResource, username, password)).thenReturn(wmiWbemServices);
			mockedWinTempShareClass.when(WinTempShare::getWindowsScriptHostNetwork).thenReturn(windowsScriptHostNetwork);
			mockedWindowsTempShare.when(() -> WindowsTempShare.getOrCreateShare(
				Mockito.any(WindowsRemoteExecutor.class),
				Mockito.anyLong(),
				Mockito.any(ShareRemoteDirectoryConsumer.class)))
			.thenReturn(windowsTempShare1, windowsTempShare2);

			Mockito.doNothing().when(windowsScriptHostNetwork).mapNetworkDrive(Utils.EMPTY, uncSharePath, false, null, null);

			mockedWinTempShareClass.when(() -> WinTempShare.getInstance(hostname, username, password, timeout)).thenCallRealMethod();

			final WinTempShare winTempShare = WinTempShare.getInstance(hostname, username, password, timeout);

			assertNotNull(winTempShare);
			assertEquals(1, winTempShare.getUseCount());
			assertEquals(uncSharePath, winTempShare.getUncSharePath());
			assertEquals(remotePath, winTempShare.getRemotePath());

			assertTrue(winTempShare.isConnected());

			// check WinTempShare come from the cache
			final WinTempShare sharesHandler2 = WinTempShare.getInstance(hostname, username, password, timeout);
			assertEquals(2, winTempShare.getUseCount());
			assertEquals(2, sharesHandler2.getUseCount());
			assertEquals(sharesHandler2.toString(), winTempShare.toString());
			assertEquals(sharesHandler2.getUncSharePath(), winTempShare.getUncSharePath());
			assertEquals(sharesHandler2.getRemotePath(), winTempShare.getRemotePath());

			winTempShare.close();
			assertEquals(1, winTempShare.getUseCount());
			assertEquals(1, sharesHandler2.getUseCount());

			assertTrue(winTempShare.isConnected());
			assertTrue(sharesHandler2.isConnected());

			sharesHandler2.close();
			assertEquals(0, winTempShare.getUseCount());
			assertEquals(0, sharesHandler2.getUseCount());

			assertFalse(winTempShare.isConnected());
			assertFalse(sharesHandler2.isConnected());

			// New instance
			final WinTempShare sharesHandler3 = WinTempShare.getInstance(hostname, username, password, timeout);
			assertEquals(0, winTempShare.getUseCount());
			assertEquals(0, sharesHandler2.getUseCount());
			assertEquals(1, sharesHandler3.getUseCount());

			assertNotEquals(sharesHandler3.toString(), winTempShare.toString());

			assertTrue(sharesHandler3.isConnected());
		}
	}

	@Test
	void testGetWindowsDirectory() throws Exception {
		try (final WmiWbemServices wmiWbemServices = WmiWbemServices.getInstance("root\\cimv2", null, null)) {
			assertEquals(System.getenv("windir"), WinTempShare.getWindowsDirectory(wmiWbemServices, 60 * 1000L));
		}
	}

	@Test
	void testCreateRemoteDirectory() throws Exception {

		final Path remotePath = Paths.get(tempDir.toAbsolutePath().toString(), "remoteDirectory");
		final long timeout = 60 * 1000L;

		try (final WmiWbemServices wmiWbemServices = WmiWbemServices.getInstance("root\\cimv2", null, null)) {
			Assertions.assertFalse(Files.exists(remotePath));

			WinTempShare.createRemoteDirectory(wmiWbemServices, remotePath.toString(), timeout);

			Assertions.assertTrue(Files.exists(remotePath));
		}
	}

}
