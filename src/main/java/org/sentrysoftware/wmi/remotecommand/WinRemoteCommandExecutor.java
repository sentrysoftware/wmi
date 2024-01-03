package org.sentrysoftware.wmi.remotecommand;

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

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeoutException;

import org.sentrysoftware.wmi.TimeoutHelper;
import org.sentrysoftware.wmi.Utils;
import org.sentrysoftware.wmi.exceptions.WindowsRemoteException;
import org.sentrysoftware.wmi.exceptions.WqlQuerySyntaxException;
import org.sentrysoftware.wmi.windows.remote.WindowsRemoteProcessUtils;
import org.sentrysoftware.wmi.exceptions.WmiComException;
import org.sentrysoftware.wmi.shares.WinTempShare;
import com.sun.jna.platform.win32.COM.COMException;

public class WinRemoteCommandExecutor {

	private static final String OUT_EXT = ".out";
	private static final String ERR_EXT = ".err";

	private final float executionTime;
	private final String stdout;
	private final String stderr;
	private final int statusCode;

	private WinRemoteCommandExecutor(
			final String stdout,
			final String stderr,
			final float executionTime,
			final int statusCode) {
		this.stdout = stdout;
		this.stderr = stderr;
		this.executionTime = executionTime;
		this.statusCode = statusCode;
	}

	/**
	 * Get the execution time of the command in seconds.
	 * @return
	 */
	public float getExecutionTime() {
		return executionTime;
	}

	/**
	 * Get the stdout of the command.
	 * @return
	 */
	public String getStdout() {
		return stdout;
	}

	/**
	 * Get the stderr of the command.
	 * @return
	 */
	public String getStderr() {
		return stderr;
	}

	/**
	 * Get the return status code of the command
	 * @return
	 */
	public int getStatusCode() {
		return statusCode;
	}

	/**
	 * Execute a command on a remote Windows system and return an object with
	 * the output of the command.
	 * <p>
	 * You can specify local files to be copied to the remote system before executing the command.
	 * If the command contains references to these local files, it will be updated to reference the
	 * path on the remote system where the files have been copied.
	 * <p>
	 * Example:
	 * <p>
	 * <code>WinRemoteCommandExecutor.execute("CSCRIPT c:\\MyScript.vbs", "remote-srv", null, null, null, 30000, Arrays.asList("c:\\MyScript.vbs"), false);</code>
	 * <p>
	 * This will copy <b>c:\\MyScript.vbs</b> to <b>remote-srv</b>, typically in
	 * <b>C:\\Windows\\Temp\\SEN_ShareFor_MYHOST</b> and the command that is executed will therefore
	 * become:
	 * <code>CSCRIPT "C:\\Windows\\Temp\\SEN_ShareFor_MYHOST\\MyScript.vbs"</code>
	 * @param command The command to execute. (Mandatory)
	 * @param hostname Host to connect to.  (Mandatory)
	 * @param username The username name.
	 * @param password The password.
	 * @param workingDirectory Path of the directory for the spawned process on the remote system (can be null)
	 * @param timeout Timeout in milliseconds
	 * @param localFileToCopyList List of local files to copy to the remote before the execution
	 * @param mergeStdoutStderr Whether to merge stderr with stdout or retrieve them separately
	 *
	 * @return an instance of WinRemoteCommandExecutor with the result of the command
	 *
	 * @throws IOException If an I/O error occurs.
	 * @throws TimeoutException To notify userName of timeout.
	 * @throws WqlQuerySyntaxException On WQL syntax errors
	 * @throws WindowsRemoteException For any problem encountered on remote
	 */
	public static WinRemoteCommandExecutor execute(
			final String command,
			final String hostname,
			final String username,
			final char[] password,
			final String workingDirectory,
			final long timeout,
			final List<String> localFileToCopyList,
			final boolean mergeStdoutStderr
	) throws IOException, TimeoutException, WqlQuerySyntaxException, WindowsRemoteException {

		Utils.checkNonNull(command, "command");
		Utils.checkNonNull(hostname, "hostname");
		Utils.checkArgumentNotZeroOrNegative(timeout, "timeout");

		final long start = Utils.getCurrentTimeMillis();

		try (WinTempShare tempShare = WinTempShare.getInstance(
				hostname,
				username,
				password,
				TimeoutHelper.getRemainingTime(timeout, start, "No time left to access the temporary share"))
		) {

			tempShare.checkConnectedFirst();

			// Copy the list specified list of files, and update the command accordingly
			final String localFilesUpdatedCommand = WindowsRemoteProcessUtils.copyLocalFilesToShare(
					command,
					localFileToCopyList,
					tempShare.getUncSharePath(),
					tempShare.getRemotePath());

			// Update the command to capture its stdout and stderr
			final String outputFileBaseName = WindowsRemoteProcessUtils.buildNewOutputFileName();
			final Path outputFilePath = Paths.get(tempShare.getUncSharePath(), outputFileBaseName + OUT_EXT);
			final Path outputFilePathRemote = Paths.get(tempShare.getRemotePath(), outputFileBaseName + OUT_EXT);
			final Path errFilePath = Paths.get(tempShare.getUncSharePath(), outputFileBaseName + ERR_EXT);
			final Path errFilePathRemote = Paths.get(tempShare.getRemotePath(), outputFileBaseName + ERR_EXT);

			// If we are to merge stdout and stderr, redirect stderr to stdout (with "2>&1")
			final String redirectedCommand = String.format("CMD.EXE /C (%s) > \"%s\" 2>\"%s\"",
					localFilesUpdatedCommand,
					outputFilePathRemote.toString(),
					mergeStdoutStderr ? "&1" : errFilePathRemote.toString());

			// Create a process on the remote machine and execute the cmd
			final long startCommand = Utils.getCurrentTimeMillis();
			final int statusCode = RemoteProcess.executeCommand(
					redirectedCommand,
					hostname,
					username,
					password,
					workingDirectory,
					TimeoutHelper.getRemainingTime(timeout, start, "No time left to execute command")
			);
			final float executionTime = (Utils.getCurrentTimeMillis() - startCommand) / 1000.0f;

			// Wait for the stdout or stderr file to appear
			while (!Files.exists(outputFilePath)) {
				TimeoutHelper.stagedSleep(timeout, start, "Output files were not created");
			}

			final Charset charset = WindowsRemoteProcessUtils.getWindowsEncodingCharset(
					tempShare.getWindowsRemoteExecutor(),
					TimeoutHelper.getRemainingTime(timeout, start, "No time left to retrieve the code set"));

			// Reading the stdout
			final String outContent = Utils.readText(outputFilePath, charset);
			Files.deleteIfExists(outputFilePath);

			// Reading the stderr file
			final String errContent;
			if (mergeStdoutStderr) {
				errContent = Utils.EMPTY;
			} else {
				errContent = Utils.readText(errFilePath, charset);
				Files.deleteIfExists(errFilePath);
			}

			return new WinRemoteCommandExecutor(
					outContent,
					errContent,
					executionTime,
					statusCode);

		} catch (final COMException e) {
			// And forward this error as a regular exception
			throw new WmiComException(e, e.getClass().getSimpleName() + ": " + e.getMessage());
		}
	}

}
