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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.sentrysoftware.wmi.TimeoutHelper;
import org.sentrysoftware.wmi.Utils;
import org.sentrysoftware.wmi.WmiHelper;
import org.sentrysoftware.wmi.exceptions.WqlQuerySyntaxException;
import org.sentrysoftware.wmi.exceptions.ProcessNotFoundException;
import org.sentrysoftware.wmi.exceptions.WmiComException;
import org.sentrysoftware.wmi.wbem.WmiWbemServices;

/**
 * Class for the Win32 related methods.
 *
 */
public class RemoteProcess {

	private RemoteProcess() { }

	private static final String TERMINATE = "Terminate";
	private static final String CREATE = "Create";

	private static final String CIMV2_NAMESPACE = "ROOT\\CIMV2";

	private static final String WIN32_PROCESS = "Win32_Process";

	/**
	 * Map of the possible (and known) ReturnValue of the Win32_Process methods
	 */
	private static final Map<Integer, String> METHOD_RETURNVALUE_MAP;
	static {
		final Map<Integer, String> map = new HashMap<>();
		map.put(2, "Access denied");
		map.put(3, "Insufficient privilege");
		map.put(8, "Unknown failure");
		map.put(9, "Path not found");
		map.put(21, "Invalid parameter");
		METHOD_RETURNVALUE_MAP = Collections.unmodifiableMap(map);
	}


	/**
	 * Execute the command on the remote
	 * @param command The command to execute
	 * @param hostname Hostname of IP address where to execute the command
	 * @param username Username (may be null)
	 * @param password Password (may be null)
	 * @param workingDirectory Path of the directory for the spawned process on the remote system (can be null)
	 * @param timeout Timeout in milliseconds
	 * @return the command status code
	 * @throws WmiComException  For any problem encountered with JNA
	 * @throws TimeoutException To notify userName of timeout.
	 */
	public static int executeCommand(
			final String command,
			final String hostname,
			final String username,
			final char[] password,
			final String workingDirectory,
			final long timeout
	) throws WmiComException, TimeoutException {

		Utils.checkNonNull(command, "command");
		Utils.checkArgumentNotZeroOrNegative(timeout, "timeout");

		final long start = Utils.getCurrentTimeMillis();

		final String networkResource = WmiHelper.createNetworkResource(hostname, CIMV2_NAMESPACE);
		try (final WmiWbemServices wmiWbemServices = WmiWbemServices.getInstance(networkResource, username, password)) {

			// Execute Win32_Process::Create
			final Map<String, Object> createInputs = new HashMap<>();
			createInputs.put("CommandLine", command);
			if (!Utils.isBlank(workingDirectory)) {
				createInputs.put("CurrentDirectory", workingDirectory.trim());
			}
			final Map<String, Object> createResult = wmiWbemServices.executeMethod(WIN32_PROCESS, WIN32_PROCESS, CREATE, createInputs);

			// Extract ProcessId from the result
			final Integer processId = (Integer) createResult.get("ProcessId");
			if (processId == null || processId.intValue() < 1) {
				throw new WmiComException("Could not spawn the process: No ProcessId was returned by Win32_Process::Create");
			}

			// Wait for the process to complete
			try {
				while (existProcess(wmiWbemServices, processId, TimeoutHelper.getRemainingTime(timeout, start, "No time left to check if the process exists"))) {
					TimeoutHelper.stagedSleep(timeout, start, String.format("Command %s execution has timed out", command));
				}
			} catch (final TimeoutException e) {
				// TIME'S UP!
				// Kill the process and its children (and give us a 10-second extra time to do this)
				killProcessWithChildren(wmiWbemServices, processId, 10000);
				throw e;
			}

			return  (Integer) createResult.get("ReturnValue");
		}
	}

	/**
	 * Check if a process exist in Win32_Process.
	 *
	 * @param wbemServices WBEM Services handling
	 * @param pid The process Id.
	 * @param timeout Timeout in milliseconds.
	 * @return true if the process exist false otherwise.
	 *
	 * @throws WmiComException  For any problem encountered with JNA
	 * @throws TimeoutException To notify userName of timeout.
	 */
	static boolean existProcess(
			final WmiWbemServices wbemServices,
			final int pid,
			final long timeout
	) throws WmiComException, TimeoutException {

		try {

			return !wbemServices.executeWql(
					String.format("SELECT Handle FROM Win32_Process WHERE Handle = '%d'", pid),
					timeout
			).isEmpty();

		} catch (final WqlQuerySyntaxException e) {
			throw new WmiComException(e);
		}
	}

	/**
	 * Kill the specified process and all its children.
	 *
	 * @param wmiWbemServices WBEM Services handling
	 * @param pid The process Id.
	 * @param timeout Timeout in milliseconds.
	 * @throws WmiComException For any problem encountered with JNA.
	 * @throws TimeoutException To notify userName of timeout.
	 */
	private static void killProcessWithChildren(
			final WmiWbemServices wmiWbemServices,
			final int pid,
			final long timeout
	) throws WmiComException, TimeoutException {

		// First, get the children
		try {

			final long start = Utils.getCurrentTimeMillis();

			final List<Integer> pidToKillList = new ArrayList<>();
			pidToKillList.add(pid);
			pidToKillList.addAll(
					wmiWbemServices.executeWql(
									String.format("SELECT Handle FROM Win32_Process WHERE ParentProcessId = '%d'", pid),
									timeout
							).stream()
							.map(row -> (String) row.get("Handle"))
							.filter(Objects::nonNull)
							.map(Integer::parseInt)
							.collect(Collectors.toList())
			);



			// Kill
			for (final int pidToKill : pidToKillList) {
				if (TimeoutHelper.getRemainingTime(timeout, start, "No time left to kill the process") < 0) {
					throw new TimeoutException("Timeout while killing remaining processes");
				}
				try {
					killProcess(wmiWbemServices, pidToKill);
				} catch (final ProcessNotFoundException e) { /* Do nothing, just ignore */ }
			}

		} catch (final WqlQuerySyntaxException e) {
			throw new WmiComException(e); // Impossible
		}

	}

	/**
	 * Kill the process with id.
	 *
	 * @param wmiWbemServices WBEM Services handling
	 * @param pid The process Id.
	 * @throws WmiComException For any problem encountered with JNA.
	 */
	private static void killProcess(
			final WmiWbemServices wmiWbemServices,
			final int pid
	) throws WmiComException, ProcessNotFoundException {

		// Call the Terminate method of the Win32_Process class
		// Reason is set to 1, just so that process exit code is non-zero, to indicate a failure
		final Map<String, Object> inputs = Collections.singletonMap("Reason", 1);
		final Map<String, Object> terminateResult;
		try {
			terminateResult = wmiWbemServices.executeMethod(
					String.format("Win32_Process.Handle='%d'", pid),
					WIN32_PROCESS,
					TERMINATE,
					inputs
			);
		} catch (final WmiComException e) {

			// Special case if we got an exception because the specified process could not be found
			if (e.getMessage().contains("WBEM_E_NOT_FOUND")) {
				throw new ProcessNotFoundException(pid);
			}
			throw e;

		}

		// Check ReturnValue
		final Integer returnCode = (Integer) terminateResult.get("ReturnValue");
		if (returnCode == null || returnCode.intValue() != 0) {
			throw new WmiComException("Could not terminate the process (%d): %s",
					pid, getReturnErrorMessage(returnCode));
		}
	}

	private static String getReturnErrorMessage(final int returnCode) {
		return METHOD_RETURNVALUE_MAP.getOrDefault(
				returnCode,
				String.format("Unknown return code (%d)", returnCode));
	}
}
