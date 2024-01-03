package org.sentrysoftware.wmi.remotecommand;

import org.sentrysoftware.wmi.exceptions.WmiComException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledOnOs(OS.WINDOWS)
class RemoteProcessTest {

	private static final String COMMAND = "CMD.EXE /C echo test";
	private static final int TIMEOUT = 60 * 1000;

	@Test
	void testInvalidArguments() throws Exception {

		// check illegal arguments
		{
			assertThrows(IllegalArgumentException.class, () -> RemoteProcess.executeCommand(null, null, null, null, null, TIMEOUT));
			assertThrows(IllegalArgumentException.class, () -> RemoteProcess.executeCommand(COMMAND, null, null, null, null, -1));
			assertThrows(IllegalArgumentException.class, () -> RemoteProcess.executeCommand(COMMAND, null, null, null, null, 0));
		}
	}

	@Test
	void testInvalidCommand() throws Exception {

		// Invalid COMMAND line triggers a proper exception
		{
			assertThrows(WmiComException.class, () -> RemoteProcess.executeCommand("invalidcommand", null, null, null, null, TIMEOUT));
		}
	}

	@Test
	void testRegularWorkingCommand() throws Exception {

		// No problem for a regular COMMAND
		{
			RemoteProcess.executeCommand(COMMAND, null, null, null, null, TIMEOUT);
		}
	}

	@Test
	void testBadWorkingDirectory() throws Exception {

		// Bad working directory
		{
			assertThrows(WmiComException.class, () -> RemoteProcess.executeCommand(COMMAND, null, null, null, "c:\\invalid3395409", TIMEOUT));
		}
	}

	@Test
	void testTimeout() throws Exception {

		// Timeout!
		{
			assertThrows(TimeoutException.class, () -> RemoteProcess.executeCommand("CHOICE.EXE /T 30 /D y", null, null, null, null, 500));
		}

	}


}
