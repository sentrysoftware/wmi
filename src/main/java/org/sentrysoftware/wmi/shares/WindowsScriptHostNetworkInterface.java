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

import com.sun.jna.platform.win32.COM.util.annotation.ComMethod;
import com.sun.jna.platform.win32.COM.util.annotation.ComObject;

/**
 * Interface of Windows Script Host Network Object.
 *
 */
@ComObject(
		clsId="{093FF999-1EA0-4079-9525-9614C3504B74}",
		progId="WScript.Network")
public interface WindowsScriptHostNetworkInterface {

	/**
	 * Add a shared network drive mapping.
	 *
	 * @param localName The name by which the mapped drive will be known locally.
	 * @param remoteName The share's UNC name (\\xxx\yyy).
	 * @param updateProfile Indicate whether the mapping information is stored in the current user's profile.
	 * @param user The user name.
	 * @param password The user password.
	 */
	@ComMethod(name="MapNetworkDrive")
	void mapNetworkDrive (
			String localName,
			String remoteName,
			Boolean updateProfile,
			String user,
			String password);

	/**
	 * Remove a shared network drive mapping.
	 *
	 * @param name The name of the mapped drive you want to remove
	 * @param force Indicate whether to force the removal of the mapped drive.
	 * @param updateProfile Indicate whether to remove the mapping from the user's profile.
	 */
	@ComMethod(name="RemoveNetworkDrive")
	void removeNetworkDrive (
			String name,
			Boolean force,
			Boolean updateProfile) ;

}
