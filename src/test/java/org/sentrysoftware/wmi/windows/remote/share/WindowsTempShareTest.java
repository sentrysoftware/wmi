package org.sentrysoftware.wmi.windows.remote.share;

import org.sentrysoftware.wmi.Utils;
import org.sentrysoftware.wmi.exceptions.WindowsRemoteException;
import org.sentrysoftware.wmi.exceptions.WqlQuerySyntaxException;
import org.sentrysoftware.wmi.windows.remote.WindowsRemoteCommandResult;
import org.sentrysoftware.wmi.windows.remote.WindowsRemoteExecutor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.Optional;

class WindowsTempShareTest {

	private static final MockedStatic<Utils> MOCKED_UTILS = Mockito.mockStatic(Utils.class);

	private static final long NOW = 10000L;

	@BeforeAll
	static void initMockedUtils() throws Exception {
		MOCKED_UTILS.when(() -> Utils.checkNonNull(Mockito.isNull(), Mockito.anyString())).thenCallRealMethod();
		MOCKED_UTILS.when(() -> Utils.checkNonNull(Mockito.any(), Mockito.anyString())).thenCallRealMethod();

		MOCKED_UTILS.when(() -> Utils.checkArgumentNotZeroOrNegative(Mockito.anyLong(), Mockito.anyString()))
		.thenCallRealMethod();

		MOCKED_UTILS.when(() -> Utils.isBlank(Mockito.isNull())).thenCallRealMethod();
		MOCKED_UTILS.when(() -> Utils.isBlank(Mockito.anyString())).thenCallRealMethod();

		MOCKED_UTILS.when(() -> Utils.isEmpty(Mockito.isNull())).thenCallRealMethod();
		MOCKED_UTILS.when(() -> Utils.isEmpty(Mockito.anyString())).thenCallRealMethod();

		MOCKED_UTILS.when(() -> Utils.checkNonBlank(Mockito.isNull(), Mockito.anyString())).thenCallRealMethod();
		MOCKED_UTILS.when(() -> Utils.checkNonBlank(Mockito.anyString(), Mockito.anyString()))
		.thenCallRealMethod();

		MOCKED_UTILS.when(Utils::getComputerName).thenReturn("PC-TEST");

		MOCKED_UTILS.when(Utils::getCurrentTimeMillis).thenReturn(NOW);
	}

	@AfterAll
	static void closeMockedUtils() {
		MOCKED_UTILS.close();
	}

	@Test
	void testWindowsTempShare() {
		final WindowsRemoteExecutor windowsRemoteExecutor = Mockito.mock(WindowsRemoteExecutor.class);

		// Check arguments
		Assertions.assertThrows(
				IllegalArgumentException.class,
				() ->new WindowsTempShare(null, "share", "remote"));

		Assertions.assertThrows(
				IllegalArgumentException.class,
				() -> new WindowsTempShare(windowsRemoteExecutor, null, "remote"));

		{
			final WindowsTempShare windowsTempShare =
					new WindowsTempShare(windowsRemoteExecutor, "\\\\localhost\\share", "remote");

			Assertions.assertNotNull(windowsTempShare);
			Assertions.assertEquals("\\\\localhost\\share", windowsTempShare.getUncSharePath());
			Assertions.assertEquals("share", windowsTempShare.getShareName());
			Assertions.assertEquals("remote", windowsTempShare.getRemotePath());
			Assertions.assertEquals(windowsRemoteExecutor, windowsTempShare.getWindowsRemoteExecutor());
		}
		{
			Mockito.doReturn("localhost").when(windowsRemoteExecutor).getHostname();

			final WindowsTempShare windowsTempShare =
					new WindowsTempShare(windowsRemoteExecutor, "share", null);

			Assertions.assertNotNull(windowsTempShare);
			Assertions.assertEquals("\\\\localhost\\share", windowsTempShare.getUncSharePath());
			Assertions.assertEquals("share", windowsTempShare.getShareName());
			Assertions.assertNull(windowsTempShare.getRemotePath());
			Assertions.assertEquals(windowsRemoteExecutor, windowsTempShare.getWindowsRemoteExecutor());
		}
		{
			Mockito.doReturn("localhost").when(windowsRemoteExecutor).getHostname();

			final WindowsTempShare windowsTempShare =
					new WindowsTempShare(windowsRemoteExecutor, "share", "remote");

			Assertions.assertNotNull(windowsTempShare);
			Assertions.assertEquals("\\\\localhost\\share", windowsTempShare.getUncSharePath());
			Assertions.assertEquals("share", windowsTempShare.getShareName());
			Assertions.assertEquals("remote", windowsTempShare.getRemotePath());
			Assertions.assertEquals(windowsRemoteExecutor, windowsTempShare.getWindowsRemoteExecutor());
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	void testGetOrCreateShare() throws Exception {

		final WindowsRemoteExecutor windowsRemoteExecutor = Mockito.mock(WindowsRemoteExecutor.class);
		final long timeout = NOW + (30 * 1000L);
		final ShareRemoteDirectoryConsumer<WindowsRemoteExecutor, String, String, Long> shareRemoteDirectory =
				Mockito.mock(ShareRemoteDirectoryConsumer.class);

		Mockito.doReturn("localhost").when(windowsRemoteExecutor).getHostname();

		final WindowsTempShare windowsTempShare =
				new WindowsTempShare(windowsRemoteExecutor, "share", "remote");

		// Check arguments
		Assertions.assertThrows(
				IllegalArgumentException.class,
				() -> WindowsTempShare.getOrCreateShare(null, timeout, shareRemoteDirectory));

		Assertions.assertThrows(
				IllegalArgumentException.class,
				() -> WindowsTempShare.getOrCreateShare(windowsRemoteExecutor, -1L, shareRemoteDirectory));

		Assertions.assertThrows(
				IllegalArgumentException.class,
				() -> WindowsTempShare.getOrCreateShare(windowsRemoteExecutor, 0L, shareRemoteDirectory));

		Assertions.assertThrows(
				IllegalArgumentException.class,
				() -> WindowsTempShare.getOrCreateShare(windowsRemoteExecutor, timeout, null));

		// Case clustered share present
		try (final MockedStatic<WindowsTempShare> mockedWindowsTempShare =
				Mockito.mockStatic(WindowsTempShare.class)) {
			mockedWindowsTempShare.when(() -> WindowsTempShare.getClusterShare(
					Mockito.eq(windowsRemoteExecutor),
					Mockito.anyLong(),
					Mockito.eq(NOW)))
			.thenReturn(Optional.of(windowsTempShare));

			mockedWindowsTempShare.when(() -> WindowsTempShare.getOrCreateShare(
					windowsRemoteExecutor,
					timeout,
					shareRemoteDirectory))
			.thenCallRealMethod();

			mockedWindowsTempShare.verify(WindowsTempShare::buildShareName, Mockito.times(0));
			mockedWindowsTempShare.verify(
					() -> WindowsTempShare.getShare(
							Mockito.any(WindowsRemoteExecutor.class),
							Mockito.anyString(),
							Mockito.anyLong()),
					Mockito.times(0));
			mockedWindowsTempShare.verify(
					() -> WindowsTempShare.createTempShare(
							Mockito.any(WindowsRemoteExecutor.class),
							Mockito.anyString(),
							Mockito.anyLong(),
							Mockito.any(ShareRemoteDirectoryConsumer.class)),
					Mockito.times(0));

			final WindowsTempShare actual =
					WindowsTempShare.getOrCreateShare(windowsRemoteExecutor, timeout, shareRemoteDirectory);

			Assertions.assertNotNull(actual);
			Assertions.assertEquals(windowsTempShare.getUncSharePath(), actual.getUncSharePath());
			Assertions.assertEquals(windowsTempShare.getShareName(), actual.getShareName());
			Assertions.assertEquals(windowsTempShare.getRemotePath(), actual.getRemotePath());
			Assertions.assertEquals(windowsRemoteExecutor, actual.getWindowsRemoteExecutor());
		}

		// Case non-clustered share present
		try (final MockedStatic<WindowsTempShare> mockedWindowsTempShare =
				Mockito.mockStatic(WindowsTempShare.class)) {
			mockedWindowsTempShare.when(() -> WindowsTempShare.getClusterShare(
					Mockito.eq(windowsRemoteExecutor),
					Mockito.anyLong(),
					Mockito.eq(NOW)))
			.thenReturn(Optional.empty());

			mockedWindowsTempShare.when(WindowsTempShare::buildShareName).thenCallRealMethod();

			mockedWindowsTempShare.when(() -> WindowsTempShare.getShare(
					Mockito.eq(windowsRemoteExecutor),
					Mockito.anyString(),
					Mockito.anyLong()))
			.thenReturn(Optional.of(windowsTempShare));

			mockedWindowsTempShare.when(() -> WindowsTempShare.getOrCreateShare(
					windowsRemoteExecutor,
					timeout,
					shareRemoteDirectory))
			.thenCallRealMethod();

			mockedWindowsTempShare.verify(
					() -> WindowsTempShare.createTempShare(
							Mockito.any(WindowsRemoteExecutor.class),
							Mockito.anyString(),
							Mockito.anyLong(),
							Mockito.any(ShareRemoteDirectoryConsumer.class)),
					Mockito.times(0));

			final WindowsTempShare actual =
					WindowsTempShare.getOrCreateShare(windowsRemoteExecutor, timeout, shareRemoteDirectory);

			Assertions.assertNotNull(actual);
			Assertions.assertEquals(windowsTempShare.getUncSharePath(), actual.getUncSharePath());
			Assertions.assertEquals(windowsTempShare.getShareName(), actual.getShareName());
			Assertions.assertEquals(windowsTempShare.getRemotePath(), actual.getRemotePath());
			Assertions.assertEquals(windowsRemoteExecutor, actual.getWindowsRemoteExecutor());
		}

		// Case create temp share
		try (final MockedStatic<WindowsTempShare> mockedWindowsTempShare =
				Mockito.mockStatic(WindowsTempShare.class)) {
			mockedWindowsTempShare.when(() -> WindowsTempShare.getClusterShare(
					Mockito.eq(windowsRemoteExecutor),
					Mockito.anyLong(),
					Mockito.eq(NOW)))
			.thenReturn(Optional.empty());

			mockedWindowsTempShare.when(WindowsTempShare::buildShareName).thenCallRealMethod();

			mockedWindowsTempShare.when(() -> WindowsTempShare.getShare(
					Mockito.eq(windowsRemoteExecutor),
					Mockito.anyString(),
					Mockito.anyLong()))
			.thenReturn(Optional.empty());

			mockedWindowsTempShare.when(() -> WindowsTempShare.createTempShare(
					Mockito.eq(windowsRemoteExecutor),
					Mockito.anyString(),
					Mockito.anyLong(),
					Mockito.eq(shareRemoteDirectory)))
			.thenReturn(windowsTempShare);

			mockedWindowsTempShare.when(() -> WindowsTempShare.getOrCreateShare(
					windowsRemoteExecutor,
					timeout,
					shareRemoteDirectory))
			.thenCallRealMethod();

			final WindowsTempShare actual =
					WindowsTempShare.getOrCreateShare(windowsRemoteExecutor, timeout, shareRemoteDirectory);

			Assertions.assertNotNull(actual);
			Assertions.assertEquals(windowsTempShare.getUncSharePath(), actual.getUncSharePath());
			Assertions.assertEquals(windowsTempShare.getShareName(), actual.getShareName());
			Assertions.assertEquals(windowsTempShare.getRemotePath(), actual.getRemotePath());
			Assertions.assertEquals(windowsRemoteExecutor, actual.getWindowsRemoteExecutor());
		}
	}

	@Test
	void testGetWindowsDirectory() throws Exception {
		final long timeout = 60 * 1000L;

		// Check arguments
		Assertions.assertThrows(
				IllegalArgumentException.class,
				() -> WindowsTempShare.getWindowsDirectory(null, timeout));

		Assertions.assertThrows(
				IllegalArgumentException.class,
				() -> WindowsTempShare.getWindowsDirectory(Mockito.mock(WindowsRemoteExecutor.class), -1));

		Assertions.assertThrows(
				IllegalArgumentException.class,
				() -> WindowsTempShare.getWindowsDirectory(Mockito.mock(WindowsRemoteExecutor.class), 0));

		// case invalid query syntax (impossible in real case)
		{
			final WindowsRemoteExecutor windowsRemoteExecutor = Mockito.mock(WindowsRemoteExecutor.class);
			Mockito.doThrow(WqlQuerySyntaxException.class)
			.when(windowsRemoteExecutor).executeWql(Mockito.anyString(), Mockito.anyLong());

			Assertions.assertThrows(
					WindowsRemoteException.class,
					() -> WindowsTempShare.getWindowsDirectory(windowsRemoteExecutor, timeout));
		}

		// case query without result
		{
			final WindowsRemoteExecutor windowsRemoteExecutor = Mockito.mock(WindowsRemoteExecutor.class);
			Mockito.doReturn(Collections.emptyList())
			.when(windowsRemoteExecutor).executeWql(
					Mockito.eq("SELECT WindowsDirectory FROM Win32_OperatingSystem"),
					Mockito.anyLong());

			Assertions.assertThrows(
					WindowsRemoteException.class,
					() -> WindowsTempShare.getWindowsDirectory(windowsRemoteExecutor, timeout));
		}

		// case query with result without WindowsDirectory field (impossible in real case)
		{
			final WindowsRemoteExecutor windowsRemoteExecutor = Mockito.mock(WindowsRemoteExecutor.class);
			Mockito.doReturn(Collections.singletonList(Collections.singletonMap("key", "value")))
			.when(windowsRemoteExecutor).executeWql(
					Mockito.eq("SELECT WindowsDirectory FROM Win32_OperatingSystem"),
					Mockito.anyLong());

			Assertions.assertThrows(
					WindowsRemoteException.class,
					() -> WindowsTempShare.getWindowsDirectory(windowsRemoteExecutor, timeout));
		}

		// Windows directory found
		{
			final String winDirectory = "Windows";

			final WindowsRemoteExecutor windowsRemoteExecutor = Mockito.mock(WindowsRemoteExecutor.class);
			Mockito.doReturn(Collections.singletonList(Collections.singletonMap("WindowsDirectory", winDirectory)))
			.when(windowsRemoteExecutor).executeWql(
					Mockito.eq("SELECT WindowsDirectory FROM Win32_OperatingSystem"),
					Mockito.anyLong());

			Assertions.assertEquals(
					winDirectory,
					WindowsTempShare.getWindowsDirectory(windowsRemoteExecutor, timeout));
		}
	}

	@Test
	void testCreateRemoteDirectory() throws Exception {
		final long timeout = 60 * 1000L;
		final WindowsRemoteExecutor windowsRemoteExecutor = Mockito.mock(WindowsRemoteExecutor.class);

		Mockito.doReturn(new WindowsRemoteCommandResult("stdout", "stderr", 1.0f, 0))
		.when(windowsRemoteExecutor).executeCommand(
				Mockito.anyString(),
				Mockito.isNull(),
				Mockito.isNull(),
				Mockito.anyLong());

		// Check arguments
		Assertions.assertThrows(
				IllegalArgumentException.class,
				() -> WindowsTempShare.createRemoteDirectory(null, "remotePath", timeout));

		Assertions.assertThrows(
				IllegalArgumentException.class,
				() -> WindowsTempShare.createRemoteDirectory(windowsRemoteExecutor, null, timeout));

		WindowsTempShare.createRemoteDirectory(windowsRemoteExecutor, "remotePath", timeout);
	}

	@Test
	void testBuildShareName() {
		Assertions.assertEquals("SEN_ShareFor_PC-TEST$", WindowsTempShare.buildShareName());
	}

	@Test
	void testBuildRemotePath() {

		Assertions.assertThrows(
				IllegalArgumentException.class,
				() -> WindowsTempShare.buildRemotePath(null, "SEN_ShareFor_TEST$"));

		Assertions.assertThrows(
				IllegalArgumentException.class,
				() -> WindowsTempShare.buildRemotePath("Windows", null));

		Assertions.assertThrows(
				IllegalArgumentException.class,
				() -> WindowsTempShare.buildRemotePath("Windows", ""));

		Assertions.assertThrows(
				IllegalArgumentException.class,
				() -> WindowsTempShare.buildRemotePath("Windows", " "));

		Assertions.assertEquals(
				"\\Temp\\SEN_ShareFor_TEST$",
				WindowsTempShare.buildRemotePath("", "SEN_ShareFor_TEST$"));

		Assertions.assertEquals(
				" \\Temp\\SEN_ShareFor_TEST$",
				WindowsTempShare.buildRemotePath(" ", "SEN_ShareFor_TEST$"));

		Assertions.assertEquals(
				"Windows\\Temp\\SEN_ShareFor_TEST$",
				WindowsTempShare.buildRemotePath("Windows", "SEN_ShareFor_TEST$"));
	}

	@Test
	void testBuildCreateRemoteDirectoryCommand() {

		Assertions.assertThrows(
				IllegalArgumentException.class,
				() -> WindowsTempShare.buildCreateRemoteDirectoryCommand(null));

		Assertions.assertThrows(
				IllegalArgumentException.class,
				() -> WindowsTempShare.buildCreateRemoteDirectoryCommand(""));

		Assertions.assertThrows(
				IllegalArgumentException.class,
				() -> WindowsTempShare.buildCreateRemoteDirectoryCommand("  "));

		Assertions.assertEquals(
				"CMD.EXE /C IF NOT EXIST \"\\Temp\\SEN_TempFor_PC-TEST\" MKDIR \\Temp\\SEN_TempFor_PC-TEST",
				WindowsTempShare.buildCreateRemoteDirectoryCommand("\\Temp\\SEN_TempFor_PC-TEST"));
	}

	@Test
	void tesBuildPathOnCluster() {

		Assertions.assertThrows(IllegalArgumentException.class, () -> WindowsTempShare.buildPathOnCluster(null));

		Assertions.assertEquals("\\Temp\\SEN_TempFor_PC-TEST", WindowsTempShare.buildPathOnCluster(""));

		Assertions.assertEquals(
				"\\path\\Temp\\SEN_TempFor_PC-TEST",
				WindowsTempShare.buildPathOnCluster("\\path"));
	}

	@Test
	void testBuildUncPath() {

		Assertions.assertThrows(IllegalArgumentException.class, () -> WindowsTempShare.buildUncPath(null, "share"));

		Assertions.assertEquals("\\\\localhost\\share", WindowsTempShare.buildUncPath("localhost", "share"));

		Assertions.assertEquals(
				"\\\\2001-db8--85b-3c51-f5ff-ffdb.ipv6-literal.net\\SEN_ShareFor_RD-VM$",
				WindowsTempShare.buildUncPath("2001:db8::85b:3c51:f5ff:ffdb", "SEN_ShareFor_RD-VM$"));

		Assertions.assertEquals(
				"\\\\fe80--1ff-fe23-4567-890aseth2.ipv6-literal.net\\SEN_ShareFor_RD-VM$",
				WindowsTempShare.buildUncPath("fe80::1ff:fe23:4567:890a%eth2", "SEN_ShareFor_RD-VM$"));
	}
}
