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

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.WTypes.LPOLESTR;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.win32.W32APIOptions;

/**
 * Enhanced version of the Ole32 interface, with a proper CoSetProxyBlanket() method that
 * manages to set authentication properly, including for a remote system.
 */
public interface Ole32Enhanced extends Ole32 {

	public Ole32Enhanced INSTANCE = Native.load("Ole32", Ole32Enhanced.class, W32APIOptions.DEFAULT_OPTIONS);

	/**
	 * Default authentication settings (automatic)
	 */
	public static final int RPC_C_AUTHN_DEFAULT = 0xFFFFFFFF;

	/**
	 * Default authorization settings (automatic)
	 */
	public static final int RPC_C_AUTHZ_DEFAULT = 0xFFFFFFFF;

	/**
	 * Sets the authentication information that will be used to make calls on the
	 * specified proxy. This is a helper function for IClientSecurity::SetBlanket.
	 *
	 * @param pProxy           [in] The proxy to be set.
	 * @param dwAuthnSvc       [in] The authentication service to be used. For a
	 *                         list of possible values, see Authentication Service
	 *                         Constants. Use RPC_C_AUTHN_NONE if no authentication
	 *                         is required. If RPC_C_AUTHN_DEFAULT is specified,
	 *                         DCOM will pick an authentication service following
	 *                         its normal security blanket negotiation algorithm.
	 * @param dwAuthzSvc       [in] The authorization service to be used. For a list
	 *                         of possible values, see Authorization Constants. If
	 *                         RPC_C_AUTHZ_DEFAULT is specified, DCOM will pick an
	 *                         authorization service following its normal security
	 *                         blanket negotiation algorithm. RPC_C_AUTHZ_NONE
	 *                         should be used as the authorization service if
	 *                         NTLMSSP, Kerberos, or Schannel is used as the
	 *                         authentication service.
	 * @param pServerPrincName [in, optional] The server principal name to be used
	 *                         with the authentication service. If
	 *                         COLE_DEFAULT_PRINCIPAL is specified, DCOM will pick a
	 *                         principal name using its security blanket negotiation
	 *                         algorithm. If Kerberos is used as the authentication
	 *                         service, this value must not be NULL. It must be the
	 *                         correct principal name of the server or the call will
	 *                         fail. If Schannel is used as the authentication
	 *                         service, this value must be one of the msstd or
	 *                         fullsic forms described in Principal Names, or NULL
	 *                         if you do not want mutual authentication. Generally,
	 *                         specifying NULL will not reset the server principal
	 *                         name on the proxy; rather, the previous setting will
	 *                         be retained. You must be careful when using NULL as
	 *                         pServerPrincName when selecting a different
	 *                         authentication service for the proxy, because there
	 *                         is no guarantee that the previously set principal
	 *                         name would be valid for the newly selected
	 *                         authentication service.
	 * @param dwAuthnLevel     [in] The authentication level to be used. For a list
	 *                         of possible values, see Authentication Level
	 *                         Constants. If RPC_C_AUTHN_LEVEL_DEFAULT is specified,
	 *                         DCOM will pick an authentication level following its
	 *                         normal security blanket negotiation algorithm. If
	 *                         this value is none, the authentication service must
	 *                         also be none.
	 * @param dwImpLevel       [in] The impersonation level to be used. For a list
	 *                         of possible values, see Impersonation Level
	 *                         Constants. If RPC_C_IMP_LEVEL_DEFAULT is specified,
	 *                         DCOM will pick an impersonation level following its
	 *                         normal security blanket negotiation algorithm. If
	 *                         NTLMSSP is the authentication service, this value
	 *                         must be RPC_C_IMP_LEVEL_IMPERSONATE or
	 *                         RPC_C_IMP_LEVEL_IDENTIFY. NTLMSSP also supports
	 *                         delegate-level impersonation
	 *                         (RPC_C_IMP_LEVEL_DELEGATE) on the same computer. If
	 *                         Schannel is the authentication service, this
	 *                         parameter must be RPC_C_IMP_LEVEL_IMPERSONATE.
	 * @param authInfo         [in, optional] COAUTHIDENTITY structure to be used for
	 *                         impersonation on a remote system through this proxy.
	 * @param dwCapabilities   [in] The capabilities of this proxy. For a list of
	 *                         possible values, see the
	 *                         EOLE_AUTHENTICATION_CAPABILITIES enumeration. The
	 *                         only flags that can be set through this function are
	 *                         EOAC_MUTUAL_AUTH, EOAC_STATIC_CLOAKING,
	 *                         EOAC_DYNAMIC_CLOAKING, EOAC_ANY_AUTHORITY (this flag
	 *                         is deprecated), EOAC_MAKE_FULLSIC, and EOAC_DEFAULT.
	 *                         Either EOAC_STATIC_CLOAKING or EOAC_DYNAMIC_CLOAKING
	 *                         can be set if pAuthInfo is not set and Schannel is
	 *                         not the authentication service. (See Cloaking for
	 *                         more information.) If any capability flags other than
	 *                         those mentioned here are set, CoSetProxyBlanket will
	 *                         fail.
	 * @return This function can return the following values.
	 *
	 *         S_OK The function was successful.
	 *
	 *         E_INVALIDARG One or more arguments is invalid.
	 */
	public HRESULT CoSetProxyBlanket(
			Pointer pProxy,
			int dwAuthnSvc,
			int dwAuthzSvc,
			LPOLESTR pServerPrincName,
			int dwAuthnLevel,
			int dwImpLevel,
			CoAuthIdentity authInfo,
			int dwCapabilities);
}
