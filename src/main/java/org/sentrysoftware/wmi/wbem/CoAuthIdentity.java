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

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

/**
 * COAUTHIDENTITY struct
 * <p>
 * See https://docs.microsoft.com/en-us/windows/win32/api/wtypesbase/ns-wtypesbase-coauthidentity
 */
@Structure.FieldOrder({"user", "userLength", "domain", "domainLength", "password", "passwordLength", "flags"})
public class CoAuthIdentity extends Structure {

	public Pointer user;
	public int userLength;
	public Pointer domain;
	public int domainLength;
	public Pointer password;
	public int passwordLength;
	public int flags;

	/**
	 * Creates a new COAUTHIDENTITY struct from the specified username and password
	 * @param username username, or domain\\username, or username@domain
	 * @param password associated password
	 */
	public CoAuthIdentity(final String username, final char[] password) {

		String domain = null;
		String user = username;

		// Extract domain from username, which can be domain\\user or
		// user@domain
		final int backslashIndex = username.indexOf('\\');
		if (backslashIndex > -1) {
			domain = username.substring(0, backslashIndex);
			user = username.substring(backslashIndex + 1);
		} else {
			final int atIndex = username.indexOf('@');
			if (atIndex > -1) {
				user = username.substring(0, atIndex);
				domain = username.substring(atIndex + 1);
			}
		}

		// Sets the user field (native)
		this.user = new Memory(Native.WCHAR_SIZE * (user.length() + 1L));
		this.user.setWideString(0, user);
		this.userLength = user.length();

		// Sets the domain field
		if (domain != null) {
			this.domain = new Memory(Native.WCHAR_SIZE * (domain.length() + 1L));
			this.domain.setWideString(0, domain);
			this.domainLength = domain.length();
		} else {
			this.domain = null;
			this.domainLength = 0;
		}

		// Sets the password field (native)
		// Note: too bad we couldn't manage to copy the password as a char array
		// without creating a String object
		if (password != null) {
			this.password = new Memory(Native.WCHAR_SIZE * (password.length + 1L));
			this.password.setWideString(0, String.valueOf(password));
			this.passwordLength = password.length;
		} else {
			this.password = null;
			this.passwordLength = 0;
		}

		// Unicode
		this.flags = 0x2;

	}
}
