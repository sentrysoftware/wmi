package org.sentrysoftware.wmi.remotecommand;

import org.sentrysoftware.wmi.shares.WinTempShare;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.WINDOWS)
class WinRemoteCommandExecutorTest {

	@TempDir
	static Path tempDir;

	@Test
	void testExecute() throws Exception {
		final String command = "echo test&& echo error>&2";
		final String hostname = "localhost";
		final long timeout = 60 * 1000;

		final Path testDir = Paths.get(tempDir.toAbsolutePath().toString(), "testExecute");
		Files.createDirectories(testDir);

		// Check invalid arguments
		Assertions.assertThrows(IllegalArgumentException.class, () -> WinRemoteCommandExecutor.execute(null, hostname, null, null, null, timeout, null, false));
		Assertions.assertThrows(IllegalArgumentException.class, () -> WinRemoteCommandExecutor.execute(command, null, null, null, null, timeout, null, false));
		Assertions.assertThrows(IllegalArgumentException.class, () -> WinRemoteCommandExecutor.execute(command, hostname, null, null, null, -1, null, false));
		Assertions.assertThrows(IllegalArgumentException.class, () -> WinRemoteCommandExecutor.execute(command, hostname, null, null, null, 0, null, false));

		final WinTempShare mockedTempShare = Mockito.mock(WinTempShare.class);
		Mockito.when(mockedTempShare.getUncSharePath()).thenReturn(testDir.toString());
		Mockito.when(mockedTempShare.getRemotePath()).thenReturn(testDir.toString());
		assertEquals(testDir.toString(), mockedTempShare.getUncSharePath());

		try (final MockedStatic<WinTempShare> mockedWinTempShareClass = Mockito.mockStatic(WinTempShare.class)) {

			mockedWinTempShareClass.when(() -> WinTempShare.getInstance(
					ArgumentMatchers.eq(hostname),
					ArgumentMatchers.isNull(),
					ArgumentMatchers.isNull(),
					ArgumentMatchers.anyLong())).thenReturn(mockedTempShare);

			{
				final WinRemoteCommandExecutor winRemoteCommandExecutor =
						WinRemoteCommandExecutor.execute(command, hostname, null, null, null, timeout, null, false);

				assertNotNull(winRemoteCommandExecutor);
				assertEquals("test\n", winRemoteCommandExecutor.getStdout());
				assertEquals("error\n", winRemoteCommandExecutor.getStderr());
				float execTime = winRemoteCommandExecutor.getExecutionTime();
				assertTrue(execTime >= 0.0f && execTime < timeout, execTime + " must be between 0 and " + timeout);
			}

			{
				final WinRemoteCommandExecutor winRemoteCommandExecutor =
						WinRemoteCommandExecutor.execute(command, hostname, null, null, null, timeout, null, true);

				assertNotNull(winRemoteCommandExecutor);
				assertEquals("test\nerror\n", winRemoteCommandExecutor.getStdout());
				assertEquals("", winRemoteCommandExecutor.getStderr());
			}

		}
	}

}
