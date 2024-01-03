package org.sentrysoftware.wmi.windows.remote;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

class WindowsRemoteProcessUtilsTest {

	private static final Charset DEFAULT_WINDOWS_CHARSET = Charset.forName("windows-1252");

	private static File testDir;
	private static File uncShareDir;
	private static File remoteDir;

	private static File localFile1;
	private static File localFile2;

	@TempDir
	static File tempDir;

	@BeforeAll
	static void initFiles() throws Exception {

		testDir = new File(tempDir, "testDir");
		testDir.mkdir();
		Assertions.assertTrue(testDir.exists());

		localFile1 = new File(testDir, "local-test-file-1.txt");
		localFile1.createNewFile();
		Assertions.assertTrue(localFile1.exists());

		localFile2 = new File(testDir, "local-test-file-2.txt");
		localFile2.createNewFile();
		Assertions.assertTrue(localFile2.exists());

		Assertions.assertEquals(2, testDir.listFiles().length);

		uncShareDir = new File(tempDir, "uncShareDir");
		uncShareDir.mkdir();
		Assertions.assertTrue(uncShareDir.exists());
		Assertions.assertEquals(0, uncShareDir.listFiles().length);

		remoteDir = new File(tempDir, "remote");
		remoteDir.mkdir();
		Assertions.assertTrue(remoteDir.exists());
		Assertions.assertEquals(0, remoteDir.listFiles().length);
	}

	@Test
	void testGetWindowsEncodingCharset() throws Exception {

		final int timeout = 1;

		assertEquals(DEFAULT_WINDOWS_CHARSET, WindowsRemoteProcessUtils.getWindowsEncodingCharset(null, timeout));
		assertEquals(DEFAULT_WINDOWS_CHARSET, WindowsRemoteProcessUtils.getWindowsEncodingCharset(Mockito.mock(WindowsRemoteExecutor.class), -1));
		assertEquals(DEFAULT_WINDOWS_CHARSET, WindowsRemoteProcessUtils.getWindowsEncodingCharset(Mockito.mock(WindowsRemoteExecutor.class), 0));


		// check CodeSet absent from Win32_OperatingSystem
		{
			final WindowsRemoteExecutor windowsRemoteExecutor = Mockito.mock(WindowsRemoteExecutor.class);
			Mockito.doReturn(Collections.emptyList()).when(windowsRemoteExecutor).executeWql("SELECT CodeSet FROM Win32_OperatingSystem", timeout);

			assertEquals(DEFAULT_WINDOWS_CHARSET, WindowsRemoteProcessUtils.getWindowsEncodingCharset(windowsRemoteExecutor, timeout));
		}
		{
			final WindowsRemoteExecutor windowsRemoteExecutor = Mockito.mock(WindowsRemoteExecutor.class);
			Mockito.doReturn(Collections.singletonList(Collections.emptyMap())).when(windowsRemoteExecutor).executeWql("SELECT CodeSet FROM Win32_OperatingSystem", timeout);

			assertEquals(DEFAULT_WINDOWS_CHARSET, WindowsRemoteProcessUtils.getWindowsEncodingCharset(windowsRemoteExecutor, timeout));
		}

		// check CodeSet unknown
		{
			final WindowsRemoteExecutor windowsRemoteExecutor = Mockito.mock(WindowsRemoteExecutor.class);
			Mockito.doReturn(Collections.singletonList(Collections.singletonMap("CodeSet", "999999"))).when(windowsRemoteExecutor).executeWql("SELECT CodeSet FROM Win32_OperatingSystem", timeout);

			assertEquals(DEFAULT_WINDOWS_CHARSET, WindowsRemoteProcessUtils.getWindowsEncodingCharset(windowsRemoteExecutor, timeout));
		}

		// check map values
		{
			final WindowsRemoteExecutor windowsRemoteExecutor = Mockito.mock(WindowsRemoteExecutor.class);
			Mockito.doReturn(Collections.singletonList(Collections.singletonMap("CodeSet", "1250"))).when(windowsRemoteExecutor).executeWql("SELECT CodeSet FROM Win32_OperatingSystem", timeout);

			assertEquals(Charset.forName("windows-1250"), WindowsRemoteProcessUtils.getWindowsEncodingCharset(windowsRemoteExecutor, timeout));
		}
	}

	@Test
	void testCopyLocalFilesToShare() throws Exception {

		clearDirectories();
		Assertions.assertEquals(0, uncShareDir.listFiles().length);
		Assertions.assertEquals(0, remoteDir.listFiles().length);

		final String commandFormat = "launch %s; launch %s";
		final String command = String.format(commandFormat, localFile1.getAbsolutePath(), localFile2.getAbsolutePath());
		final List<String> localFiles = Stream.of(testDir.listFiles()).map(File::getAbsolutePath).collect(Collectors.toList());
		final String uncSharePath = uncShareDir.getAbsolutePath();
		final String remotePath = remoteDir.getAbsolutePath();

		// check arguments
		Assertions.assertThrows(
				IllegalArgumentException.class,
				() -> WindowsRemoteProcessUtils.copyLocalFilesToShare(null, localFiles, uncSharePath, remotePath));
		Assertions.assertThrows(
				IllegalArgumentException.class,
				() -> WindowsRemoteProcessUtils.copyLocalFilesToShare(command, localFiles, null, remotePath));
		Assertions.assertThrows(
				IllegalArgumentException.class,
				() -> WindowsRemoteProcessUtils.copyLocalFilesToShare(command, localFiles, uncSharePath, null));

		// case localFiles null or empty
		assertEquals(
				command,
				WindowsRemoteProcessUtils.copyLocalFilesToShare(command, null, null, null));
		assertEquals(
				command,
				WindowsRemoteProcessUtils.copyLocalFilesToShare(command, Collections.emptyList(), null, null));

		// check copy local files and command update
		final String actual = WindowsRemoteProcessUtils.copyLocalFilesToShare(command, localFiles, uncSharePath, remotePath);

		Assertions.assertEquals(2, uncShareDir.listFiles().length);

		final File targetUncFile1 = uncShareDir.listFiles((dir, name) -> name.equals(localFile1.getName()))[0];
		Assertions.assertEquals(localFile1.getName(), targetUncFile1.getName());
		Assertions.assertEquals(localFile1.lastModified(), targetUncFile1.lastModified());

		final File targetUncFile2 = uncShareDir.listFiles((dir, name) -> name.equals(localFile2.getName()))[0];
		Assertions.assertEquals(localFile2.getName(), targetUncFile2.getName());
		Assertions.assertEquals(localFile2.lastModified(), targetUncFile2.lastModified());

		final String expected = String.format(commandFormat,
				Paths.get(remotePath, localFile1.getName()),
				Paths.get(remotePath, localFile2.getName()));
		Assertions.assertEquals(expected, actual);
	}

	@Test
	void testCopyToShare() throws Exception {

		final long localFileTime = localFile1.lastModified();

		clearDirectories();
		Assertions.assertEquals(0, uncShareDir.listFiles().length);
		Assertions.assertEquals(0, remoteDir.listFiles().length);

		// Case not targetUncPath not exists
		{
			final Path remoteFilePath = WindowsRemoteProcessUtils.copyToShare(
					localFile1.toPath(),
					uncShareDir.getAbsolutePath(),
					remoteDir.getAbsolutePath());

			Assertions.assertNotNull(remoteFilePath);
			Assertions.assertTrue(remoteFilePath.startsWith(remoteDir.toPath()));
			Assertions.assertTrue(remoteFilePath.endsWith(localFile1.getName()));

			Assertions.assertEquals(1, uncShareDir.listFiles().length);
			final File targetUncFile = uncShareDir.listFiles()[0];
			// check same file
			Assertions.assertEquals(localFile1.getName(), targetUncFile.getName());
			Assertions.assertEquals(localFile1.lastModified(), targetUncFile.lastModified());
		}

		// Case targetUncPath exists
		{
			Assertions.assertEquals(1, uncShareDir.listFiles().length);
			// update time
			final long newLocalFileTime = localFileTime + 10;
			(uncShareDir.listFiles()[0]).setLastModified(newLocalFileTime);

			final Path remoteFilePath = WindowsRemoteProcessUtils.copyToShare(
					localFile1.toPath(),
					uncShareDir.getAbsolutePath(),
					remoteDir.getAbsolutePath());

			Assertions.assertNotNull(remoteFilePath);
			Assertions.assertTrue(remoteFilePath.startsWith(remoteDir.toPath()));
			Assertions.assertTrue(remoteFilePath.endsWith(localFile1.getName()));

			Assertions.assertEquals(1, Stream.of(uncShareDir.listFiles()).filter(File::isFile).count());
			final File targetUncFile = uncShareDir.listFiles()[0];
			Assertions.assertEquals(localFile1.getName(), targetUncFile.getName());
			Assertions.assertEquals(newLocalFileTime, targetUncFile.lastModified());
		}
	}

	@Test
	void testCaseInsensitiveReplace() {
		assertEquals("not changed", WindowsRemoteProcessUtils.caseInsensitiveReplace("not changed", "not here", "replaced"));
		assertEquals("cscript new new", WindowsRemoteProcessUtils.caseInsensitiveReplace("cscript d:\\Test d:\\TEST", "D:\\teSt", "new"));
		assertEquals("type C:\\1", WindowsRemoteProcessUtils.caseInsensitiveReplace("type c:\\(n).$$$", "C:\\(n).$$$", "C:\\1"));
		assertEquals("type Agitignore", WindowsRemoteProcessUtils.caseInsensitiveReplace("type Agitignore", ".gitignore", "replaced"));
		assertNull(WindowsRemoteProcessUtils.caseInsensitiveReplace(null, "test", "replaced"));
		assertEquals("not changed", WindowsRemoteProcessUtils.caseInsensitiveReplace("not changed", null, "replaced"));
		assertEquals("not changed", WindowsRemoteProcessUtils.caseInsensitiveReplace("not changed", "not here", null));
		assertEquals("I am  happy", WindowsRemoteProcessUtils.caseInsensitiveReplace("I am NOT happy", "not", null));
	}

	private void clearDirectories() {
		// reset uncShareDir and remoteDir
		if (uncShareDir.listFiles().length > 0) {
			for (final File file : uncShareDir.listFiles()) {
				file.delete();
			}
		}
		if (remoteDir.listFiles().length > 0) {
			for (final File file : remoteDir.listFiles()) {
				file.delete();
			}
		}
	}
}
