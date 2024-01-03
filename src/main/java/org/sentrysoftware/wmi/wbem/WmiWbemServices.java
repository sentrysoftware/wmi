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

import org.sentrysoftware.wmi.AutoCloseableReadWriteLock;
import org.sentrysoftware.wmi.Utils;
import org.sentrysoftware.wmi.WmiHelper;
import org.sentrysoftware.wmi.WqlQuery;
import org.sentrysoftware.wmi.exceptions.WqlQuerySyntaxException;
import org.sentrysoftware.wmi.windows.remote.WindowsRemoteCommandResult;
import org.sentrysoftware.wmi.windows.remote.WindowsRemoteExecutor;
import org.sentrysoftware.wmi.exceptions.WmiComException;
import org.sentrysoftware.wmi.remotecommand.RemoteProcess;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.platform.win32.COM.COMException;
import com.sun.jna.platform.win32.COM.COMUtils;
import com.sun.jna.platform.win32.COM.Wbemcli;
import com.sun.jna.platform.win32.COM.Wbemcli.*;
import com.sun.jna.platform.win32.Guid.CLSID;
import com.sun.jna.platform.win32.Guid.GUID;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.platform.win32.OleAuto;
import com.sun.jna.platform.win32.Variant;
import com.sun.jna.platform.win32.Variant.VARIANT;
import com.sun.jna.platform.win32.Variant.VARIANT.ByReference;
import com.sun.jna.platform.win32.WTypes;
import com.sun.jna.platform.win32.WTypes.BSTR;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Class for WBEM Services handling.
 *
 */
public class WmiWbemServices implements WindowsRemoteExecutor {

	/**
	 * WMI error codes messages Map.
	 *
	 * @see <a href="https://docs.microsoft.com/en-us/windows/win32/wmisdk/wmi-error-constants">WMI Error Constants</a>
	 */
	private static final Map<Long, String> MAP_HRESULT_MESSAGE;
	static {
		final Map<Long, String> map = new HashMap<>();
		map.put(2147749889L, "WBEM_E_FAILED: Call failed. (0x80041001)");
		map.put(2147749890L, "WBEM_E_NOT_FOUND: Object cannot be found. (0x80041002)");
		map.put(2147749891L, "WBEM_E_ACCESS_DENIED: Current user does not have permission to perform the action. (0x80041003)");
		map.put(2147749892L, "WBEM_E_PROVIDER_FAILURE: Provider has failed at some time other than during initialization. (0x80041004)");
		map.put(2147749893L, "WBEM_E_TYPE_MISMATCH: Type mismatch occurred. (0x80041005)");
		map.put(2147749894L, "WBEM_E_OUT_OF_MEMORY: Not enough memory for the operation. (0x80041006)");
		map.put(2147749895L, "WBEM_E_INVALID_CONTEXT: The IWbemContext object is not valid. (0x80041007)");
		map.put(2147749896L, "WBEM_E_INVALID_PARAMETER: One of the parameters to the call is not correct. (0x80041008)");
		map.put(2147749897L, "WBEM_E_NOT_AVAILABLE: Resource, typically a remote server, is not currently available. (0x80041009)");
		map.put(2147749898L, "WBEM_E_CRITICAL_ERROR: Internal, critical, and unexpected error occurred. Report the error to Microsoft Technical Support. (0x8004100A)");
		map.put(2147749899L, "WBEM_E_INVALID_STREAM: One or more network packets were corrupted during a remote session. (0x8004100B)");
		map.put(2147749900L, "WBEM_E_NOT_SUPPORTED: Feature or operation is not supported. (0x8004100C)");
		map.put(2147749901L, "WBEM_E_INVALID_SUPERCLASS: Parent class specified is not valid. (0x8004100D)");
		map.put(2147749902L, "WBEM_E_INVALID_NAMESPACE: Namespace specified cannot be found. (0x8004100E)");
		map.put(2147749903L, "WBEM_E_INVALID_OBJECT: Specified instance is not valid. (0x8004100F)");
		map.put(2147749904L, "WBEM_E_INVALID_CLASS: Specified class is not valid. (0x80041010)");
		map.put(2147749905L, "WBEM_E_PROVIDER_NOT_FOUND: Provider referenced in the schema does not have a corresponding registration. (0x80041011)");
		map.put(2147749906L, "WBEM_E_INVALID_PROVIDER_REGISTRATION: Provider referenced in the schema has an incorrect or incomplete registration. (0x80041012)");
		map.put(2147749907L, "WBEM_E_PROVIDER_LOAD_FAILURE: COM cannot locate a provider referenced in the schema. (0x80041013)");
		map.put(2147749908L, "WBEM_E_INITIALIZATION_FAILURE: Component, such as a provider, failed to initialize for internal reasons. (0x80041014)");
		map.put(2147749909L, "WBEM_E_TRANSPORT_FAILURE: Networking error that prevents normal operation has occurred. (0x80041015)");
		map.put(2147749910L, "WBEM_E_INVALID_OPERATION: Requested operation is not valid. This error usually applies to invalid attempts to delete classes or properties. (0x80041016)");
		map.put(2147749911L, "WBEM_E_INVALID_QUERY: Query was not syntactically valid. (0x80041017)");
		map.put(2147749912L, "WBEM_E_INVALID_QUERY_TYPE: Requested query language is not supported. (0x80041018)");
		map.put(2147749913L, "WBEM_E_ALREADY_EXISTS: In a put operation, the wbemChangeFlagCreateOnly flag was specified, but the instance already exists. (0x80041019)");
		map.put(2147749914L, "WBEM_E_OVERRIDE_NOT_ALLOWED: Not possible to perform the add operation on this qualifier because the owning object does not permit overrides. (0x8004101A)");
		map.put(2147749915L, "WBEM_E_PROPAGATED_QUALIFIER: User attempted to delete a qualifier that was not owned. The qualifier was inherited from a parent class. (0x8004101B)");
		map.put(2147749916L, "WBEM_E_PROPAGATED_PROPERTY: User attempted to delete a property that was not owned. The property was inherited from a parent class. (0x8004101C)");
		map.put(2147749917L, "WBEM_E_UNEXPECTED: Client made an unexpected and illegal sequence of calls, such as calling EndEnumeration before calling BeginEnumeration. (0x8004101D)");
		map.put(2147749918L, "WBEM_E_ILLEGAL_OPERATION: User requested an illegal operation, such as spawning a class from an instance. (0x8004101E)");
		map.put(2147749919L, "WBEM_E_CANNOT_BE_KEY: Illegal attempt to specify a key qualifier on a property that cannot be a key. The keys are specified in the class definition for an object and cannot be altered on a per-instance basis. (0x8004101F)");
		map.put(2147749920L, "WBEM_E_INCOMPLETE_CLASS: Current object is not a valid class definition. Either it is incomplete or it has not been registered with WMI using SWbemObject.Put_. (0x80041020)");
		map.put(2147749921L, "WBEM_E_INVALID_SYNTAX: Query is syntactically not valid. (0x80041021)");
		map.put(2147749922L, "WBEM_E_NONDECORATED_OBJECT: Reserved for future use. (0x80041022)");
		map.put(2147749923L, "WBEM_E_READ_ONLY: An attempt was made to modify a read-only property. (0x80041023)");
		map.put(2147749924L, "WBEM_E_PROVIDER_NOT_CAPABLE: Provider cannot perform the requested operation. This can include a query that is too complex, retrieving an instance, creating or updating a class, deleting a class, or enumerating a class. (0x80041024)");
		map.put(2147749925L, "WBEM_E_CLASS_HAS_CHILDREN: Attempt was made to make a change that invalidates a subclass. (0x80041025)");
		map.put(2147749926L, "WBEM_E_CLASS_HAS_INSTANCES: Attempt was made to delete or modify a class that has instances. (0x80041026)");
		map.put(2147749927L, "WBEM_E_QUERY_NOT_IMPLEMENTED: Reserved for future use. (0x80041027)");
		map.put(2147749928L, "WBEM_E_ILLEGAL_NULL: Value of Nothing/NULL was specified for a property that must have a value, such as one that is marked by a Key, Indexed, or Not_Null qualifier. (0x80041028)");
		map.put(2147749929L, "WBEM_E_INVALID_QUALIFIER_TYPE: Variant value for a qualifier was provided that is not a legal qualifier type. (0x80041029)");
		map.put(2147749930L, "WBEM_E_INVALID_PROPERTY_TYPE: CIM type specified for a property is not valid. (0x8004102A)");
		map.put(2147749931L, "WBEM_E_VALUE_OUT_OF_RANGE: Request was made with an out-of-range value or it is incompatible with the type. (0x8004102B)");
		map.put(2147749932L, "WBEM_E_CANNOT_BE_SINGLETON: Illegal attempt was made to make a class singleton, such as when the class is derived from a non-singleton class. (0x8004102C)");
		map.put(2147749933L, "WBEM_E_INVALID_CIM_TYPE: CIM type specified is not valid. (0x8004102D)");
		map.put(2147749934L, "WBEM_E_INVALID_METHOD: Requested method is not available. (0x8004102E)");
		map.put(2147749935L, "WBEM_E_INVALID_METHOD_PARAMETERS: Parameters provided for the method are not valid. (0x8004102F)");
		map.put(2147749936L, "WBEM_E_SYSTEM_PROPERTY: There was an attempt to get qualifiers on a system property. (0x80041030)");
		map.put(2147749937L, "WBEM_E_INVALID_PROPERTY: Property type is not recognized. (0x80041031)");
		map.put(2147749938L, "WBEM_E_CALL_CANCELLED: Asynchronous process has been canceled internally or by the user. Note that due to the timing and nature of the asynchronous operation, the operation may not have been truly canceled. (0x80041032)");
		map.put(2147749939L, "WBEM_E_SHUTTING_DOWN: User has requested an operation while WMI is in the process of shutting down. (0x80041033)");
		map.put(2147749940L, "WBEM_E_PROPAGATED_METHOD: Attempt was made to reuse an existing method name from a parent class and the signatures do not match. (0x80041034)");
		map.put(2147749941L, "WBEM_E_UNSUPPORTED_PARAMETER: One or more parameter values, such as a query text, is too complex or unsupported. WMI is therefore requested to retry the operation with simpler parameters. (0x80041035)");
		map.put(2147749942L, "WBEM_E_MISSING_PARAMETER_ID: Parameter was missing from the method call. (0x80041036)");
		map.put(2147749943L, "WBEM_E_INVALID_PARAMETER_ID: Method parameter has an ID qualifier that is not valid. (0x80041037)");
		map.put(2147749944L, "WBEM_E_NONCONSECUTIVE_PARAMETER_IDS: One or more of the method parameters have ID qualifiers that are out of sequence. (0x80041038)");
		map.put(2147749945L, "WBEM_E_PARAMETER_ID_ON_RETVAL: Return value for a method has an ID qualifier. (0x80041039)");
		map.put(2147749946L, "WBEM_E_INVALID_OBJECT_PATH: Specified object path was not valid. (0x8004103A)");
		map.put(2147749947L, "WBEM_E_OUT_OF_DISK_SPACE: Disk is out of space or the 4 GB limit on WMI repository (CIM repository) size is reached. (0x8004103B)");
		map.put(2147749948L, "WBEM_E_BUFFER_TOO_SMALL: Supplied buffer was too small to hold all of the objects in the enumerator or to read a string property. (0x8004103C)");
		map.put(2147749949L, "WBEM_E_UNSUPPORTED_PUT_EXTENSION: Provider does not support the requested put operation. (0x8004103D)");
		map.put(2147749950L, "WBEM_E_UNKNOWN_OBJECT_TYPE: Object with an incorrect type or version was encountered during marshaling. (0x8004103E)");
		map.put(2147749951L, "WBEM_E_UNKNOWN_PACKET_TYPE: Packet with an incorrect type or version was encountered during marshaling. (0x8004103F)");
		map.put(2147749952L, "WBEM_E_MARSHAL_VERSION_MISMATCH: Packet has an unsupported version. (0x80041040)");
		map.put(2147749953L, "WBEM_E_MARSHAL_INVALID_SIGNATURE: Packet appears to be corrupt. (0x80041041)");
		map.put(2147749954L, "WBEM_E_INVALID_QUALIFIER: Attempt was made to mismatch qualifiers, such as putting [key] on an object instead of a property. (0x80041042)");
		map.put(2147749955L, "WBEM_E_INVALID_DUPLICATE_PARAMETER: Duplicate parameter was declared in a CIM method. (0x80041043)");
		map.put(2147749956L, "WBEM_E_TOO_MUCH_DATA: Reserved for future use. (0x80041044)");
		map.put(2147749957L, "WBEM_E_SERVER_TOO_BUSY: Call to IWbemObjectSink::Indicate has failed. The provider can refire the event. (0x80041045)");
		map.put(2147749958L, "WBEM_E_INVALID_FLAVOR: Specified qualifier flavor was not valid. (0x80041046)");
		map.put(2147749959L, "WBEM_E_CIRCULAR_REFERENCE: Attempt was made to create a reference that is circular (for example, deriving a class from itself). (0x80041047)");
		map.put(2147749960L, "WBEM_E_UNSUPPORTED_CLASS_UPDATE: Specified class is not supported. (0x80041048)");
		map.put(2147749961L, "WBEM_E_CANNOT_CHANGE_KEY_INHERITANCE: Attempt was made to change a key when instances or subclasses are already using the key. (0x80041049)");
		map.put(2147749968L, "WBEM_E_CANNOT_CHANGE_INDEX_INHERITANCE: An attempt was made to change an index when instances or subclasses are already using the index. (0x80041050)");
		map.put(2147749969L, "WBEM_E_TOO_MANY_PROPERTIES: Attempt was made to create more properties than the current version of the class supports. (0x80041051)");
		map.put(2147749970L, "WBEM_E_UPDATE_TYPE_MISMATCH: Property was redefined with a conflicting type in a derived class. (0x80041052)");
		map.put(2147749971L, "WBEM_E_UPDATE_OVERRIDE_NOT_ALLOWED: Attempt was made in a derived class to override a qualifier that cannot be overridden. (0x80041053)");
		map.put(2147749972L, "WBEM_E_UPDATE_PROPAGATED_METHOD: Method was re-declared with a conflicting signature in a derived class. (0x80041054)");
		map.put(2147749973L, "WBEM_E_METHOD_NOT_IMPLEMENTED: Attempt was made to execute a method not marked with [implemented] in any relevant class. (0x80041055)");
		map.put(2147749974L, "WBEM_E_METHOD_DISABLED: Attempt was made to execute a method marked with [disabled]. (0x80041056)");
		map.put(2147749975L, "WBEM_E_REFRESHER_BUSY: Refresher is busy with another operation. (0x80041057)");
		map.put(2147749976L, "WBEM_E_UNPARSABLE_QUERY: Filtering query is syntactically not valid. (0x80041058)");
		map.put(2147749977L, "WBEM_E_NOT_EVENT_CLASS: The FROM clause of a filtering query references a class that is not an event class (not derived from __Event). (0x80041059)");
		map.put(2147749978L, "WBEM_E_MISSING_GROUP_WITHIN: A GROUP BY clause was used without the corresponding GROUP WITHIN clause. (0x8004105A)");
		map.put(2147749979L, "WBEM_E_MISSING_AGGREGATION_LIST: A GROUP BY clause was used. Aggregation on all properties is not supported. (0x8004105B)");
		map.put(2147749980L, "WBEM_E_PROPERTY_NOT_AN_OBJECT: Dot notation was used on a property that is not an embedded object. (0x8004105C)");
		map.put(2147749981L, "WBEM_E_AGGREGATING_BY_OBJECT: A GROUP BY clause references a property that is an embedded object without using dot notation. (0x8004105D)");
		map.put(2147749983L, "WBEM_E_UNINTERPRETABLE_PROVIDER_QUERY: Event provider registration query (__EventProviderRegistration) did not specify the classes for which events were provided. (0x8004105F)");
		map.put(2147749984L, "WBEM_E_BACKUP_RESTORE_WINMGMT_RUNNING: Request was made to back up or restore the repository while it was in use by WinMgmt.exe, or by the SVCHOST process that contains the WMI service. (0x80041060)");
		map.put(2147749985L, "WBEM_E_QUEUE_OVERFLOW: Asynchronous delivery queue overflowed from the event consumer being too slow. (0x80041061)");
		map.put(2147749986L, "WBEM_E_PRIVILEGE_NOT_HELD: Operation failed because the client did not have the necessary security privilege. (0x80041062)");
		map.put(2147749987L, "WBEM_E_INVALID_OPERATOR: Operator is not valid for this property type. (0x80041063)");
		map.put(2147749988L, "WBEM_E_LOCAL_CREDENTIALS: User specified a username/password/authority on a local connection. The user must use a blank username/password and rely on default security. (0x80041064)");
		map.put(2147749989L, "WBEM_E_CANNOT_BE_ABSTRACT: Class was made abstract when its parent class is not abstract. (0x80041065)");
		map.put(2147749990L, "WBEM_E_AMENDED_OBJECT: Amended object was written without the WBEM_FLAG_USE_AMENDED_QUALIFIERS flag being specified. (0x80041066)");
		map.put(2147749991L, "WBEM_E_CLIENT_TOO_SLOW: Client did not retrieve objects quickly enough from an enumeration. This constant is returned when a client creates an enumeration object, but does not retrieve objects from the enumerator in a timely fashion, causing the enumerator's object caches to back up. (0x80041067)");
		map.put(2147749992L, "WBEM_E_NULL_SECURITY_DESCRIPTOR: Null security descriptor was used. (0x80041068)");
		map.put(2147749993L, "WBEM_E_TIMED_OUT: Operation timed out. (0x80041069)");
		map.put(2147749994L, "WBEM_E_INVALID_ASSOCIATION: Association is not valid. (0x8004106A)");
		map.put(2147749995L, "WBEM_E_AMBIGUOUS_OPERATION: Operation was ambiguous. (0x8004106B)");
		map.put(2147749996L, "WBEM_E_QUOTA_VIOLATION: WMI is taking up too much memory. This can be caused by low memory availability or excessive memory consumption by WMI. (0x8004106C)");
		map.put(2147749997L, "WBEM_E_TRANSACTION_CONFLICT: Operation resulted in a transaction conflict. (0x8004106D)");
		map.put(2147749998L, "WBEM_E_FORCED_ROLLBACK: Transaction forced a rollback. (0x8004106E)");
		map.put(2147749999L, "WBEM_E_UNSUPPORTED_LOCALE: Locale used in the call is not supported. (0x8004106F)");
		map.put(2147750000L, "WBEM_E_HANDLE_OUT_OF_DATE: Object handle is out-of-date. (0x80041070)");
		map.put(2147750001L, "WBEM_E_CONNECTION_FAILED: Connection to the SQL database failed. (0x80041071)");
		map.put(2147750002L, "WBEM_E_INVALID_HANDLE_REQUEST: Handle request was not valid. (0x80041072)");
		map.put(2147750003L, "WBEM_E_PROPERTY_NAME_TOO_WIDE: Property name contains more than 255 characters. (0x80041073)");
		map.put(2147750004L, "WBEM_E_CLASS_NAME_TOO_WIDE: Class name contains more than 255 characters. (0x80041074)");
		map.put(2147750005L, "WBEM_E_METHOD_NAME_TOO_WIDE: Method name contains more than 255 characters. (0x80041075)");
		map.put(2147750006L, "WBEM_E_QUALIFIER_NAME_TOO_WIDE: Qualifier name contains more than 255 characters. (0x80041076)");
		map.put(2147750007L, "WBEM_E_RERUN_COMMAND: The SQL command must be rerun because there is a deadlock in SQL. This can be returned only when data is being stored in an SQL database. (0x80041077)");
		map.put(2147750008L, "WBEM_E_DATABASE_VER_MISMATCH: The database version does not match the version that the repository driver processes. (0x80041078)");
		map.put(2147750009L, "WBEM_E_VETO_DELETE: WMI cannot execute the delete operation because the provider does not allow it. (0x80041079)");
		map.put(2147750010L, "WBEM_E_VETO_PUT: WMI cannot execute the put operation because the provider does not allow it. (0x8004107A)");
		map.put(2147750016L, "WBEM_E_INVALID_LOCALE: Specified locale identifier was not valid for the operation. (0x80041080)");
		map.put(2147750017L, "WBEM_E_PROVIDER_SUSPENDED: Provider is suspended. (0x80041081)");
		map.put(2147750018L, "WBEM_E_SYNCHRONIZATION_REQUIRED: Object must be written to the WMI repository and retrieved again before the requested operation can succeed. This constant is returned when an object must be committed and retrieved to see the property value. (0x80041082)");
		map.put(2147750019L, "WBEM_E_NO_SCHEMA: Operation cannot be completed; no schema is available. (0x80041083)");
		map.put(2147750020L, "WBEM_E_PROVIDER_ALREADY_REGISTERED: Provider cannot be registered because it is already registered. (0x119FD010)");
		map.put(2147750021L, "WBEM_E_PROVIDER_NOT_REGISTERED: Provider was not registered. (0x80041085)");
		map.put(2147750022L, "WBEM_E_FATAL_TRANSPORT_ERROR: A fatal transport error occurred. (0x80041086)");
		map.put(2147750023L, "WBEM_E_ENCRYPTED_CONNECTION_REQUIRED: User attempted to set a computer name or domain without an encrypted connection. (0x80041087)");
		map.put(2147750024L, "WBEM_E_PROVIDER_TIMED_OUT: A provider failed to report results within the specified timeout. (0x80041088)");
		map.put(2147750025L, "WBEM_E_NO_KEY: User attempted to put an instance with no defined key. (0x80041089)");
		map.put(2147750026L, "WBEM_E_PROVIDER_DISABLED: User attempted to register a provider instance but the COM server for the provider instance was unloaded. (0x8004108A)");
		map.put(2147753985L, "WBEMESS_E_REGISTRATION_TOO_BROAD: Provider registration overlaps with the system event domain. (0x80042001)");
		map.put(2147753986L, "WBEMESS_E_REGISTRATION_TOO_PRECISE: A WITHIN clause was not used in this query. (0x80042002)");
		map.put(2147753987L, "WBEMESS_E_AUTHZ_NOT_PRIVILEGED: This computer does not have the necessary domain permissions to support the security functions that relate to the created subscription instance. Contact the Domain Administrator to get this computer added to the Windows Authorization Access Group. (0x80042003)");
		map.put(2147758081L, "WBEM_E_RETRY_LATER: Reserved for future use. (0x80043001)");
		map.put(2147758082L, "WBEM_E_RESOURCE_CONTENTION: Reserved for future use. (0x80043002)");
		map.put(2147762177L, "WBEMMOF_E_EXPECTED_QUALIFIER_NAME: Expected a qualifier name. (0x80044001)");
		map.put(2147762178L, "WBEMMOF_E_EXPECTED_SEMI: Expected semicolon or '='. (0x80044002)");
		map.put(2147762179L, "WBEMMOF_E_EXPECTED_OPEN_BRACE: Expected an opening brace. (0x80044003)");
		map.put(2147762180L, "WBEMMOF_E_EXPECTED_CLOSE_BRACE: Missing closing brace or an illegal array element (0x80044004)");
		map.put(2147762181L, "WBEMMOF_E_EXPECTED_CLOSE_BRACKET: Expected a closing bracket. (0x80044005)");
		map.put(2147762182L, "WBEMMOF_E_EXPECTED_CLOSE_PAREN: Expected closing parenthesis. (0x80044006)");
		map.put(2147762183L, "WBEMMOF_E_ILLEGAL_CONSTANT_VALUE: Numeric value out of range or strings without quotes. (0x80044007)");
		map.put(2147762184L, "WBEMMOF_E_EXPECTED_TYPE_IDENTIFIER: Expected a type identifiers. (0x80044008)");
		map.put(2147762185L, "WBEMMOF_E_EXPECTED_OPEN_PAREN: Expected an open parenthesis. (0x80044009)");
		map.put(2147762186L, "WBEMMOF_E_UNRECOGNIZED_TOKEN: Unexpected token in the file. (0x8004400A)");
		map.put(2147762187L, "WBEMMOF_E_UNRECOGNIZED_TYPE: Unrecognized or unsupported type identifier. (0x8004400B)");
		map.put(2147762188L, "WBEMMOF_E_EXPECTED_PROPERTY_NAME: Expected property or method name. (0x8004400C)");
		map.put(2147762189L, "WBEMMOF_E_TYPEDEF_NOT_SUPPORTED: Typedefs and enumerated types are not supported. (0x8004400D)");
		map.put(2147762190L, "WBEMMOF_E_UNEXPECTED_ALIAS: Only a reference to a class object can have an alias value. (0x8004400E)");
		map.put(2147762191L, "WBEMMOF_E_UNEXPECTED_ARRAY_INIT: Unexpected array initialization. Arrays must be declared with []. (0x8004400F)");
		map.put(2147762192L, "WBEMMOF_E_INVALID_AMENDMENT_SYNTAX: Namespace path syntax is not valid. (0x80044010)");
		map.put(2147762193L, "WBEMMOF_E_INVALID_DUPLICATE_AMENDMENT: Duplicate amendment specifiers. (0x80044011)");
		map.put(2147762194L, "WBEMMOF_E_INVALID_PRAGMA: #pragma must be followed by a valid keyword. (0x80044012)");
		map.put(2147762195L, "WBEMMOF_E_INVALID_NAMESPACE_SYNTAX: Namespace path syntax is not valid. (0x80044013)");
		map.put(2147762196L, "WBEMMOF_E_EXPECTED_CLASS_NAME: Unexpected character in class name must be an identifier. (0x80044014)");
		map.put(2147762197L, "WBEMMOF_E_TYPE_MISMATCH: The value specified cannot be made into the appropriate type. (0x80044015)");
		map.put(2147762198L, "WBEMMOF_E_EXPECTED_ALIAS_NAME: Dollar sign must be followed by an alias name as an identifier. (0x80044016)");
		map.put(2147762199L, "WBEMMOF_E_INVALID_CLASS_DECLARATION: Class declaration is not valid. (0x80044017)");
		map.put(2147762200L, "WBEMMOF_E_INVALID_INSTANCE_DECLARATION: The instance declaration is not valid. It must start with \"instance of\". (0x80044018)");
		map.put(2147762201L, "WBEMMOF_E_EXPECTED_DOLLAR: Expected dollar sign. An alias in the form \"$name\" must follow the \"as\" keyword. (0x80044019)");
		map.put(2147762202L, "WBEMMOF_E_CIMTYPE_QUALIFIER: \"CIMTYPE\" qualifier cannot be specified directly in a MOF file. Use standard type notation. (0x8004401A)");
		map.put(2147762203L, "WBEMMOF_E_DUPLICATE_PROPERTY: Duplicate property name was found in the MOF. (0x8004401B)");
		map.put(2147762204L, "WBEMMOF_E_INVALID_NAMESPACE_SPECIFICATION: Namespace syntax is not valid. References to other servers are not allowed. (0x8004401C)");
		map.put(2147762205L, "WBEMMOF_E_OUT_OF_RANGE: Value out of range. (0x8004401D)");
		map.put(2147762206L, "WBEMMOF_E_INVALID_FILE: The file is not a valid text MOF file or binary MOF file. (0x8004401E)");
		map.put(2147762207L, "WBEMMOF_E_ALIASES_IN_EMBEDDED: Embedded objects cannot be aliases. (0x8004401F)");
		map.put(2147762208L, "WBEMMOF_E_NULL_ARRAY_ELEM: NULL elements in an array are not supported. (0x80044020)");
		map.put(2147762209L, "WBEMMOF_E_DUPLICATE_QUALIFIER: Qualifier was used more than once on the object. (0x80044021)");
		map.put(2147762210L, "WBEMMOF_E_EXPECTED_FLAVOR_TYPE: Expected a flavor type such as ToInstance, ToSubClass, EnableOverride, or DisableOverride. (0x80044022)");
		map.put(2147762211L, "WBEMMOF_E_INCOMPATIBLE_FLAVOR_TYPES: Combining EnableOverride and DisableOverride on same qualifier is not legal. (0x80044023)");
		map.put(2147762212L, "WBEMMOF_E_MULTIPLE_ALIASES: An alias cannot be used twice. (0x80044024)");
		map.put(2147762213L, "WBEMMOF_E_INCOMPATIBLE_FLAVOR_TYPES2: Combining Restricted, and ToInstance or ToSubClass is not legal. (0x80044025)");
		map.put(2147762214L, "WBEMMOF_E_NO_ARRAYS_RETURNED: Methods cannot return array values. (0x80044026)");
		map.put(2147762215L, "WBEMMOF_E_MUST_BE_IN_OR_OUT: Arguments must have an In or Out qualifier. (0x80044027)");
		map.put(2147762216L, "WBEMMOF_E_INVALID_FLAGS_SYNTAX: Flags syntax is not valid. (0x80044028)");
		map.put(2147762217L, "WBEMMOF_E_EXPECTED_BRACE_OR_BAD_TYPE: The final brace and semi-colon for a class are missing. (0x80044029)");
		map.put(2147762218L, "WBEMMOF_E_UNSUPPORTED_CIMV22_QUAL_VALUE: A CIM version 2.2 feature is not supported for a qualifier value. (0x8004402A)");
		map.put(2147762219L, "WBEMMOF_E_UNSUPPORTED_CIMV22_DATA_TYPE: The CIM version 2.2 data type is not supported. (0x8004402B)");
		map.put(2147762220L, "WBEMMOF_E_INVALID_DELETEINSTANCE_SYNTAX: The delete instance syntax is not valid. It should be #pragma DeleteInstance(\"instancepath\", FAIL|NOFAIL). (0x8004402C)");
		map.put(2147762221L, "WBEMMOF_E_INVALID_QUALIFIER_SYNTAX: The qualifier syntax is not valid. It should be qualifiername:type=value,scope(class|instance), flavorname. (0x8004402D)");
		map.put(2147762222L, "WBEMMOF_E_QUALIFIER_USED_OUTSIDE_SCOPE: The qualifier is used outside of its scope. (0x8004402E)");
		map.put(2147762223L, "WBEMMOF_E_ERROR_CREATING_TEMP_FILE: Error creating temporary file. The temporary file is an intermediate stage in the MOF compilation. (0x8004402F)");
		map.put(2147762224L, "WBEMMOF_E_ERROR_INVALID_INCLUDE_FILE: A file included in the MOF by the preprocessor command #include is not valid. (0x80044030)");
		map.put(2147762225L, "WBEMMOF_E_INVALID_DELETECLASS_SYNTAX: The syntax for the preprocessor commands #pragma deleteinstance or #pragma deleteclass is not valid. (0x80044031)");
		MAP_HRESULT_MESSAGE = Collections.unmodifiableMap(map);
	}

	/**
	 * <p>Check if the result.</p>
	 * <p>If the result is failed, throw a COMException.</p>
	 *
	 * @param hResult The result to check.
	 * @param message The error message if result failed.
	 * @throws WmiComException For any problem encountered with JNA.
	 */
	static void checkHResult(final HRESULT hResult, final String message) throws WmiComException {
		Utils.checkNonNull(hResult, "hResult");
		if (COMUtils.FAILED(hResult)) {
			throw new WmiComException("%s. %s", message, getWmiComErrorMessage(hResult));
		}
	}

	/**
	 * Get the message from the error code.
	 *
	 * @param hResult The result
	 * @return The error code message or juste the error code if unknown.
	 */
	static String getWmiComErrorMessage(final HRESULT hResult) {
		final int code = hResult.intValue();
		return MAP_HRESULT_MESSAGE.getOrDefault(
				Integer.toUnsignedLong(code),
				String.format("code: 0x%s.", Integer.toHexString(code)));
	}

	/**
	 * IWbemContext unmarshaler Class Identifier CLSID.
	 *
	 *  @see <a href="https://docs.microsoft.com/en-us/openspecs/windows_protocols/ms-wmi/18c8f1a1-652f-43b7-9186-6098bc303a8d">2.2.13 IWbemContext Interface</a>
	 */
	private static final CLSID CLSID_WBEM_CONTEXT = new CLSID("674B6698-EE92-11D0-AD71-00C04FD8FDFF");

	/**
	 * IWbemContext Interface Universally Unique Identifier UUID.
	 *
	 *  @see <a href="https://docs.microsoft.com/en-us/openspecs/windows_protocols/ms-wmi/18c8f1a1-652f-43b7-9186-6098bc303a8d">2.2.13 IWbemContext Interface</a>
	 */
	private static final GUID IID_WBEM_CONTEXT = new GUID("44ACA674-E8FC-11D0-A07C-00C04FB68820");

	/** WbemContext property for Provider Architecture */
	private static final String WBEMCONTEXT_NAME_FOR_PROVIDER_ARCHITECTURE = "__ProviderArchitecture";

	private static final int SET_VALUE_FUNCTION_VTABLE_ID_IN_WBEM_CONTEXT_INTERFACE = 8;

	private static final int WBEM_SERVICES_GET_OBJECT_VTABLE_ID = 6;
	private static final int WBEM_SERVICES_EXEC_METHOD_VTABLE_ID = 24;

	private static final int WBEM_CLASS_OBJECT_PUT_VTABLE_ID = 5;
	private static final int WBEM_CLASS_OBJECT_SPAWN_INSTANCE_VTABLE_ID = 15;
	private static final int WBEM_CLASS_OBJECT_GET_METHOD_VTABLE_ID = 19;

	/**
	 * This flag makes the operation synchronous. This is the default behavior and so this flag need not be explicitly specified.
	 *
	 *  @see <a href="https://docs.microsoft.com/en-us/openspecs/windows_protocols/ms-wmi/2bbdf995-93d8-4902-a39d-38f2a9790b85">2.2.6 WBEM_GENERIC_FLAG_TYPE Enumeration</a>
	 */
	private static final int WBEM_FLAG_RETURN_WBEM_COMPLETE = 0;

	private static final String WQL = "WQL";

	/** //hostname/namespace or just namespace for localhost */
	private final String networkResource;

	/** Hostname we're connected to */
	private final String hostname;

	/** Username (may be null) */
	private final String username;

	/** Password (may be null) */
	private final char[] password;

	/** WbemService instance (once it's connected) */
	private final IWbemServices wbemServices;

	/** WbemLocator instance */
	private final IWbemLocator wbemLocator;

	/** WbemContext to be used for this connection */
	private final IWbemContext context;

	/** COAUTHIDENTITY structure holding domain, username and password */
	private final CoAuthIdentity authIdent;

	/** Whether the instance has been closed */
	private boolean isClosed = false;

	/** ReadWriteLock */
	private AutoCloseableReadWriteLock lock;

	/**
	 * The WmiWbemServices constructor.
	 *
	 * @param networkResource //hostname/namespace or just namespace for localhost.
	 * @param username Username (may be null).
	 * @param password Password (may be null).
	 * @param wbemServices WbemService instance (once it's connected).
	 * @param wbemLocator WbemLocator instance.
	 * @param context WbemContext to be used for this connection.
	 * @param authIdent COAUTHIDENTITY structure holding domain, username and password.
	 */
	private WmiWbemServices(
			final String networkResource,
			final String username,
			final char[] password,
			final IWbemServices wbemServices,
			final IWbemLocator wbemLocator,
			final IWbemContext context,
			final CoAuthIdentity authIdent) {
		this.networkResource = networkResource;
		this.username = username;
		this.password = password;
		this.wbemServices = wbemServices;
		this.wbemLocator = wbemLocator;
		this.context = context;
		this.authIdent = authIdent;
		this.hostname = (networkResource != null && networkResource.startsWith("\\\\"))
				? networkResource.split("\\\\")[2]
						: null;
				this.lock = new AutoCloseableReadWriteLock();
	}


	/**
	 * Get a WmiWbemServices instance for the specified networkResource.
	 * @param networkResource \\hostname\namespace or just namespace for localhost (mandatory)
	 * @param username The username name (may be null).
	 * @param password The password (may be null).
	 * @return WmiWbemServices instance
	 *
	 * @throws WmiComException For any problem encountered with JNA
	 */
	public static WmiWbemServices getInstance(
			final String networkResource,
			final String username,
			final char[] password) throws WmiComException {

		Utils.checkNonNull(networkResource, "networkResource");

		// Make sure username and password are null if networkResource is local
		if (WmiHelper.isLocalNetworkResource(networkResource) && (username != null || password != null)) {
			throw new IllegalArgumentException("A local resource must be accessed without specific credentials");
		}

		IWbemLocator jnaWbemLocator = null;
		IWbemContext jnaWbemContext = null;
		IWbemServices jnaWbemServices = null;

		try {
			// Initialize COM
			// Step 1 and 2 from: "https://docs.microsoft.com/en-us/windows/win32/wmisdk/example--getting-wmi-data-from-a-remote-computer"
			WmiComHelper.initializeComLibrary();

			// Get a WbemLocator object (if we don't already have one)
			// Step 3 from: "https://docs.microsoft.com/en-us/windows/win32/wmisdk/example--getting-wmi-data-from-a-remote-computer"
			jnaWbemLocator = IWbemLocator.create();
			if (jnaWbemLocator == null) {
				throw new COMException("Failed to create WbemLocator object.");
			}

			// WbemContext
			jnaWbemContext = createWbemContextFor64BitWbemProvider();

			// Get the WbemServices
			jnaWbemServices = jnaWbemLocator.ConnectServer(
					networkResource,
					username,
					password == null ? null : String.valueOf(password),
							null,
							0,
							null,
							jnaWbemContext
					);

			final CoAuthIdentity authIdent = username != null && !username.isEmpty() ?
					new CoAuthIdentity(username, password) :
						null;

					// The WbemServices instances is actually a proxy
					// Set the proxy blanket (cloaking)
					WmiWbemServices.setProxySecurity(jnaWbemServices.getPointer(), authIdent);

					return new WmiWbemServices(
							networkResource,
							username,
							password,
							jnaWbemServices,
							jnaWbemLocator,
							jnaWbemContext,
							authIdent
							);

		} catch (final COMException e) {

			// Release what can be released
			if (jnaWbemServices != null) {
				jnaWbemServices.Release();
			}

			if (jnaWbemLocator != null) {
				jnaWbemLocator.Release();
			}

			if (jnaWbemContext != null) {
				jnaWbemContext.Release();
			}

			throw new WmiComException(e, e.getMessage());
		}
	}

	/**
	 * Release the WBEM services, disconnect and remove from the cache
	 */
	@Override
	public synchronized void close() {

		try (AutoCloseableReadWriteLock.AutoCloseableWriteLock writeLock = lock.write()) {

			isClosed = true;

			if (context != null) {
				context.Release();
			}

			if (wbemServices != null) {
				wbemServices.Release();
			}

			if (wbemLocator != null) {
				wbemLocator.Release();
			}

		}
	}


	/**
	 * Throws an IllegalStateException if the instance has been closed.
	 */
	private void checkState() {
		if (isClosed) {
			throw new IllegalStateException("This instance was closed");
		}
	}


	/**
	 * <p>Execute a WQL query and process its result.</p>
	 * <p>Use the IWbemServices pointer to make requests of WMI.</p>
	 *
	 * <p>Step 6 from: <a href="https://docs.microsoft.com/en-us/windows/win32/wmisdk/example--getting-wmi-data-from-a-remote-computer">Example: Getting WMI Data from a Remote Computer</a></p>
	 *
	 * @param wql the WQL query (required)
	 * @param timeout Timeout in milliseconds (throws an IllegalArgumentException if negative or zero)
	 * @return a list of result rows. A result row is a Map(LinkedHashMap to preserve the query order) of properties/values.
	 * @throws TimeoutException to notify userName of timeout.
	 * @throws WqlQuerySyntaxException if WQL query syntax is invalid
	 * @throws WmiComException on any COM problem
	 */
	@Override
	public List<Map<String, Object>> executeWql(final String wql, final long timeout)
			throws TimeoutException, WqlQuerySyntaxException, WmiComException {

		// Parse the WQL
		WqlQuery wqlQuery = WqlQuery.newInstance(wql);

		return executeWql(wqlQuery, timeout);

	}

	/**
	 * <p>Execute a WQL query and process its result.</p>
	 * <p>Use the IWbemServices pointer to make requests of WMI.</p>
	 *
	 * <p>Step 6 from: <a href="https://docs.microsoft.com/en-us/windows/win32/wmisdk/example--getting-wmi-data-from-a-remote-computer">Example: Getting WMI Data from a Remote Computer</a></p>
	 *
	 * @param wqlQuery the WQL query (as a {@link WqlQuery} instance)
	 * @param timeout Timeout in milliseconds (throws an IllegalArgumentException if negative or zero)
	 * @return a list of result rows. A result row is a Map(LinkedHashMap to preserve the query order) of properties/values.
	 * @throws TimeoutException to notify userName of timeout.
	 * @throws WqlQuerySyntaxException if WQL query syntax is invalid
	 * @throws WmiComException on any COM problem
	 */
	public List<Map<String, Object>> executeWql(final WqlQuery wqlQuery, final long timeout)
			throws TimeoutException, WmiComException, WqlQuerySyntaxException {

		try (AutoCloseableReadWriteLock.AutoCloseableReadLock readLock = lock.read()) {

			checkState();

			// Sanity check
			Utils.checkNonNullField(wbemServices, "wbemServices");
			Utils.checkNonNullField(wqlQuery, "wqlQuery");
			Utils.checkArgumentNotZeroOrNegative(timeout, "timeout");

			// Get and parse the result
			IEnumWbemClassObject wbemClassObjectQueryResult = null;
			try {

				// Execute the query
				wbemClassObjectQueryResult = wbemServices.ExecQuery(
						WQL,
						wqlQuery.getCleanWql(),
						Wbemcli.WBEM_FLAG_FORWARD_ONLY | Wbemcli.WBEM_FLAG_RETURN_IMMEDIATELY,
						null
						);

				return processWqlResult(
						wbemClassObjectQueryResult,
						authIdent,
						timeout,
						wqlQuery.getSubPropertiesMap()
				);
			} catch (final COMException e) {
				// And forward this error as a regular exception
				throw new WmiComException(e, e.getClass().getSimpleName() + ": " + e.getMessage());
			} finally {
				if (wbemClassObjectQueryResult != null) {
					wbemClassObjectQueryResult.Release();
				}
			}

		}
	}

	@Override
	public WindowsRemoteCommandResult executeCommand(
			final String command,
			final String workingDirectory,
			final Charset charset,
			final long timeout)
					throws WmiComException, TimeoutException {

		RemoteProcess.executeCommand(command, hostname, username, password, workingDirectory, timeout);
		return null;
	}

	/**
	 * Sets the security and authentication blanket on the specified proxy pointer.
	 * <p>
	 * Step 5 from: <a href="https://docs.microsoft.com/en-us/windows/win32/wmisdk/example--getting-wmi-data-from-a-remote-computer">Example: Getting WMI Data from a Remote Computer</a>
	 * @param pProxy Pointer to the proxy object (WMI service, or enumerator)
	 * @param authIdent COAUTHIDENTITY structure holding domain, username and password.
	 * @throws WmiComException when security blanket cannot be configured
	 */
	static void setProxySecurity(
			final Pointer pProxy,
			final CoAuthIdentity authIdent) throws WmiComException {

		// Set proxy security so the WMI service can impersonate the client
		//
		final HRESULT hResult = Ole32Enhanced.INSTANCE.CoSetProxyBlanket(
				pProxy,
				Ole32Enhanced.RPC_C_AUTHN_DEFAULT,
				Ole32Enhanced.RPC_C_AUTHZ_DEFAULT,
				null,
				Ole32.RPC_C_AUTHN_LEVEL_CALL,
				Ole32.RPC_C_IMP_LEVEL_IMPERSONATE,
				authIdent,
				Ole32.EOAC_NONE);
		checkHResult(hResult, "Could not set proxy blanket");
	}

	/**
	 * Create a context to force the 64 bits WBEM provider;
	 * We need to do that because the PATROL Agent will run in 32 bits mode
	 * and will thus access only the 32 bits version of the WBEM providers.
	 * The problem is that some WBEM providers are only available in 64 bits
	 * (notably some performance counters classes).
	 * <p>
	 * @see <a href="https://docs.microsoft.com/en-us/windows/win32/api/wbemcli/nf-wbemcli-iwbemcontext-setvalue">IWbemContext::SetValue method (wbemcli.h)</a>
	 * @return a WBEM context
	 */
	static IWbemContext createWbemContextFor64BitWbemProvider() {

		// Initialize COM (required)
		try {
			WmiComHelper.initializeComLibrary();
		} catch (final WmiComException e) {
			return null;
		}

		// Create a WBEM Context instance
		final PointerByReference contextPointer = new PointerByReference();
		final HRESULT hCoCreateInstanceResult = Ole32.INSTANCE.CoCreateInstance(
				CLSID_WBEM_CONTEXT,
				null,
				WTypes.CLSCTX_INPROC_SERVER,
				IID_WBEM_CONTEXT,
				contextPointer
				);
		if (COMUtils.FAILED(hCoCreateInstanceResult)) {
			return null;
		}

		// Set __ProviderArchitecture to 64
		final VARIANT value64 = new VARIANT(64);
		final ByReference byReference64 = new ByReference(value64);
		try {
			final HRESULT hResult = (HRESULT) WmiComHelper.comInvokerInvokeNativeObject(
					contextPointer.getValue(),
					SET_VALUE_FUNCTION_VTABLE_ID_IN_WBEM_CONTEXT_INTERFACE,
					new Object[] {
							contextPointer.getValue(),
							new WString(WBEMCONTEXT_NAME_FOR_PROVIDER_ARCHITECTURE),
							0,
							byReference64},
					HRESULT.class);
			return COMUtils.FAILED(hResult) ?
					null :
						// Return the WBEM Context object
						new IWbemContext(contextPointer.getValue());
		} finally {
			OleAuto.INSTANCE.VariantClear(byReference64);
			OleAuto.INSTANCE.VariantClear(value64);
		}

	}

	/**
	 * <p>Get the data from the WQL query.</p>
	 *
	 * <p>Step 7 from: <a href="https://docs.microsoft.com/en-us/windows/win32/wmisdk/example--getting-wmi-data-from-a-remote-computer">Example: Getting WMI Data from a Remote Computer</a></p>
	 *
	 * @param wbemClassObjectQueryResult
	 * @param authIdent COAUTHIDENTITY structure holding domain, username and password.
	 * @param timeout Timeout in milliseconds
	 * @param properties The properties to get from the request
	 * @return a list of result rows. A result row is a Map(LinkedHashMap to preserve the query order) of properties/values.
	 * @throws TimeoutException To notify userName of timeout.
	 * @throws WmiComException on any COM problem
	 * @throws WqlQuerySyntaxException on WQL syntax errors (as reported by WMI itself)
	 */
	private List<Map<String, Object>> processWqlResult(
			final IEnumWbemClassObject wbemClassObjectQueryResult,
			final CoAuthIdentity authIdent,
			final long timeout,
			final Map<String, Set<String>> properties)
					throws TimeoutException, WmiComException, WqlQuerySyntaxException {

		final Pointer[] pointersOnWbemClassObject = new Pointer[1];
		final List<Map<String, Object>> resultRows = new ArrayList<>();
		String[] names = null;
		Map<String, Set<String>> normalizedProperties = null;

		if (wbemClassObjectQueryResult.getPointer() != Pointer.NULL) {
			setProxySecurity(wbemClassObjectQueryResult.getPointer(), authIdent);
		}

		while (wbemClassObjectQueryResult.getPointer() != Pointer.NULL) {

			// Get the next record
			final HRESULT hResult = wbemClassObjectQueryResult.Next(
					(int)timeout,
					pointersOnWbemClassObject.length,
					pointersOnWbemClassObject,
					new IntByReference(0)
					);

			// Any problem?
			if (hResult == null ||
					hResult.intValue() == Wbemcli.WBEM_S_FALSE ||
					hResult.intValue() == Wbemcli.WBEM_S_NO_MORE_DATA) {
				return resultRows;
			}
			if (hResult.intValue() == Wbemcli.WBEM_S_TIMEDOUT) {
				throw new TimeoutException("No results after " + timeout + " ms.");
			}
			if (Integer.toUnsignedLong(hResult.intValue()) == 2147749911L) {
				throw new WqlQuerySyntaxException("The query was not syntactically valid.");
			}
			checkHResult(hResult, "Failed to enumerate results");

			// Get the actual WbemClassObject
			final IWbemClassObject wbemClassObject = new IWbemClassObject(pointersOnWbemClassObject[0]);

			try {
				// Get the list of properties in the first record
				// Note: this is done only the first time in this loop
				if (names == null) {
					try {
						names = wbemClassObject.GetNames(null, 0, null);
					} catch (final Throwable e) {
						names = wbemClassObject.GetNames(null, 0, null);
					}
				}

				if (normalizedProperties == null) {
					normalizedProperties = normalizeProperties(names, properties);
				}

				// Add to the result
				final Map<String, Object> values = getPropertiesValues(normalizedProperties, wbemClassObject);
				resultRows.add(values);

			} finally {

				// Free memory for this WBEM object instance
				wbemClassObject.Release();

			}

		}

		return resultRows;
	}

	/**
	 * Get all the values from the properties.
	 *
	 * @param properties
	 * @param wbemClassObject wbemClassObject
	 * @return
	 */
	private static HashMap<String, Object> getPropertiesValues(
			final Map<String, Set<String>> properties,
			final IWbemClassObject wbemClassObject) {

		return properties.entrySet().stream()
				.map(entry -> WmiCimTypeHandler.getPropertyValue(wbemClassObject, entry))
				.flatMap(map -> map.entrySet().stream())
				.collect(
						HashMap::new,
						(map, entry) -> map.put(entry.getKey(), entry.getValue()),
						HashMap::putAll);
	}

	/**
	 * Normalize the properties in initializing them if empty or null and replace their names with the one in the class.
	 *
	 * @param names
	 * @param properties
	 * @return
	 */
	private static Map<String, Set<String>> normalizeProperties(
			final String[] names,
			final Map<String, Set<String>> properties) {

		final Map<String, String> propertiesNames = Stream.of(names)
				.collect(Collectors.toMap(
						String::toLowerCase,
						Function.identity()));

		return properties == null || properties.isEmpty() ?
				propertiesNames.values().stream()
				.collect(Collectors.toMap(
						Function.identity(),
						p -> Collections.emptySet())) :
							// Put the real property name from the class
							properties.entrySet().stream()
							.collect(
									HashMap::new,
									(map, entry) -> map.put(
											propertiesNames.get(entry.getKey().toLowerCase()),
											entry.getValue()),
									HashMap::putAll);
	}

	/**
	 * <p>Invoke the IWbemServices::GetObject method.</p>
	 * <p>The IWbemServices::GetObject method retrieves a class or instance.</p>
	 * @see <a href="https://docs.microsoft.com/en-us/windows/win32/api/wbemcli/nf-wbemcli-iwbemservices-getobject">IWbemServices::GetObject method (wbemcli.h)</a>
	 *
	 *  Pointer on IWbemServices instance.
	 *
	 * @param objectPath Path of the object to retrieve. (mandatory)
	 * @return A pointer on IWbemClassObject.
	 *
	 * @throws WmiComException For any problem encountered with JNA.
	 */
	public Pointer getObject(final String objectPath) throws WmiComException {

		try (AutoCloseableReadWriteLock.AutoCloseableReadLock readLock = lock.read()) {

			checkState();

			Utils.checkNonNull(objectPath, "objectPath");

			final Pointer pWbemServices = wbemServices.getPointer();

			final BSTR strObjectPath = OleAuto.INSTANCE.SysAllocString(objectPath);
			final int lFlags = WBEM_FLAG_RETURN_WBEM_COMPLETE;
			final Pointer pCtx = null;
			final PointerByReference ppObject = new PointerByReference();
			final PointerByReference ppCallResult = null;

			try {
				final HRESULT hResult = (HRESULT) WmiComHelper.comInvokerInvokeNativeObject(
						pWbemServices,
						WBEM_SERVICES_GET_OBJECT_VTABLE_ID,
						new Object[] {
								pWbemServices,
								strObjectPath,
								lFlags,
								pCtx,
								ppObject,
								ppCallResult},
						HRESULT.class);
				WmiWbemServices.checkHResult(hResult, "Fail to invoke WbemServices::GetObject method");

				return ppObject.getValue();
			} finally {
				OleAuto.INSTANCE.SysFreeString(strObjectPath);
			}

		}
	}


	/**
	 * Executes the specified method of the specified class on the specified object.
	 * Method inputs are specified as a Map&lt;String, Object&gt; mapping input names with
	 * their values.
	 * @param objectPath WBEM path to the object on which the method is to be executed
	 * @param className Name of the class where the method is defined
	 * @param methodName Name of the method to invoke
	 * @param inputMap Map of input parameters for the method
	 * @return a Map&lt;String, Object&gt; with the output parameters of the method
	 * @throws WmiComException if anything goes wrong
	 */
	public Map<String, Object> executeMethod(
			final String objectPath,
			final String className,
			final String methodName,
			final Map<String, Object> inputMap
			) throws WmiComException {

		try (AutoCloseableReadWriteLock.AutoCloseableReadLock readLock = lock.read()) {

			checkState();

			// Get the class definition
			final IWbemClassObject classDefinition = new IWbemClassObject(getObject(className));

			// Get the method definition
			final IWbemClassObject methodDefinition;
			try {
				methodDefinition = new IWbemClassObject(getMethod(classDefinition.getPointer(), methodName));
			} finally {
				classDefinition.Release();
			}

			// Create an "instance" of the method definition (to put inputs in there)
			final IWbemClassObject methodInputs;
			try {
				methodInputs = new IWbemClassObject(spawnInstance(methodDefinition.getPointer()));
			} finally {
				methodDefinition.Release();
			}

			// Create a VARIANT for each entry in the input map
			final Map<String, VARIANT> variantInputMap = new HashMap<>();
			inputMap.forEach((name, value) -> {
				VARIANT variant;
				if (value == null) { variant = new VARIANT(); }
				else if (value instanceof String) { variant = new VARIANT((String) value); }
				else if (value instanceof Integer) { variant = new VARIANT((int) value); }
				else if (value instanceof Pointer) { variant = new VARIANT((Pointer) value); }
				else {
					throw new IllegalArgumentException("Unsupported object type for " + name);
				}
				variantInputMap.put(name, variant);
			});

			// Call the method for real now
			final IWbemClassObject methodOutputs;
			try {
				for (final Map.Entry<String, VARIANT>inputEntry : variantInputMap.entrySet()) {
					objectPut(methodInputs.getPointer(), inputEntry.getKey(), inputEntry.getValue());
				}

				methodOutputs = new IWbemClassObject(executeMethod(objectPath, methodName, methodInputs.getPointer()));

			} finally {
				variantInputMap.forEach((name, value) -> OleAuto.INSTANCE.VariantClear(value));
				methodInputs.Release();
			}

			// Extract the values from the method output and build a HashMap
			try {
				String[] properties;
				try {
					properties = methodOutputs.GetNames(null, 0, null);
				} catch (final Throwable e) {
					properties = methodOutputs.GetNames(null, 0, null);
				}

				final Map<String, Set<String>> normalizedProperties = normalizeProperties(properties, null);

				return getPropertiesValues(normalizedProperties, methodOutputs);
			} finally {
				methodOutputs.Release();
			}

		}
	}


	/**
	 * <p>Invoke the IWbemServices::ExecMethod method.</p>
	 * <p>The IWbemServices::ExecMethod method executes a method exported by a CIM object.</p>
	 *
	 * @see <a href="https://docs.microsoft.com/en-us/windows/win32/api/wbemcli/nf-wbemcli-iwbemservices-execmethod">IWbemServices::ExecMethod  method (wbemcli.h)</a>
	 *
	 * @param objectPath The object path of the object for which the method is executed. (mandatory)
	 * @param methodName Name of the method for the object. (mandatory)
	 * @param pInParams May be NULL if no in-parameters are required to execute the method. Otherwise, this points to an IWbemClassObject that contains the properties acting as inbound parameters for the method execution.
	 * @return A pointer on Execution method output.
	 *
	 * @throws WmiComException For any problem encountered with JNA.
	 */
	Pointer executeMethod(
			final String objectPath,
			final String methodName,
			final Pointer pInParams) throws WmiComException {

		Utils.checkNonNull(objectPath, "objectPath");
		Utils.checkNonNull(methodName, "methodName");

		final Pointer pWbemServices = wbemServices.getPointer();

		final BSTR strObjectPath = OleAuto.INSTANCE.SysAllocString(objectPath);
		final BSTR strMethodName = OleAuto.INSTANCE.SysAllocString(methodName);
		final int lFlags = 0;
		final Pointer pCtx = null;
		final PointerByReference ppOutParams = new PointerByReference();
		final PointerByReference ppCallResult = null;

		try {
			final HRESULT hResult = (HRESULT) WmiComHelper.comInvokerInvokeNativeObject(
					pWbemServices,
					WBEM_SERVICES_EXEC_METHOD_VTABLE_ID,
					new Object[] {
							pWbemServices,
							strObjectPath,
							strMethodName,
							lFlags,
							pCtx,
							pInParams,
							ppOutParams,
							ppCallResult},
					HRESULT.class);
			WmiWbemServices.checkHResult(hResult, "Failed to invoke WbemServices::ExecMethod method");
			return ppOutParams.getValue();
		} finally {
			OleAuto.INSTANCE.SysFreeString(strObjectPath);
			OleAuto.INSTANCE.SysFreeString(strMethodName);
		}
	}

	/**
	 * <p>Invoke the IWbemClassObject::Put method.</p>
	 * <p>The IWbemClassObject::Put method sets a named property to a new value.</p>
	 * @see <a href="https://docs.microsoft.com/en-us/windows/win32/api/wbemcli/nf-wbemcli-iwbemclassobject-put">IWbemClassObject::Put  method (wbemcli.h)</a>
	 *
	 * @param pWbemClassObject A pointer on IWbemClassObject. (mandatory)
	 * @param propertyName A parameter that must point to a valid property name. (mandatory)
	 * @param pVal A parameter that must point to a valid VARIANT, which becomes the new property value. If pVal is NULL or points to a VARIANT of type VT_NULL, the property is set to NULL, that is, no value.
	 *
	 * @throws WmiComException For any problem encountered with JNA.
	 */
	public void objectPut(
			final Pointer pWbemClassObject,
			final String propertyName,
			final Variant.VARIANT pVal) throws WmiComException {

		try (AutoCloseableReadWriteLock.AutoCloseableReadLock readLock = lock.read()) {

			checkState();

			Utils.checkNonNull(pWbemClassObject, "pWbemClassObject");
			Utils.checkNonNull(propertyName, "propertyName");

			final WString wszName = new WString(propertyName);

			final HRESULT hResult = (HRESULT) WmiComHelper.comInvokerInvokeNativeObject(
					pWbemClassObject,
					WBEM_CLASS_OBJECT_PUT_VTABLE_ID,
					new Object[] {
							pWbemClassObject,
							wszName,
							0, // lFlags -- Reserved, must be zero
							pVal},
					HRESULT.class);
			WmiWbemServices.checkHResult(hResult, "Fail to invoke IWbemClassObject::Put method");

		}
	}

	/**
	 * <p>Invoke the IWbemClassObject::SpawnInstance method.</p>
	 * <p>Use the IWbemClassObject::SpawnInstance method to create a new instance of a class.</p>
	 * @see <a href="https://docs.microsoft.com/en-us/windows/win32/api/wbemcli/nf-wbemcli-iwbemclassobject-spawninstance">IWbemClassObject::SpawnInstance  method (wbemcli.h)</a>
	 *
	 * @param pWbemClassObject A pointer on IWbemClassObject. (mandatory)
	 * @return A pointer on instantiated WBEM Class object.
	 *
	 * @throws WmiComException For any problem encountered with JNA.
	 */
	public Pointer spawnInstance(final Pointer pWbemClassObject) throws WmiComException {

		try (AutoCloseableReadWriteLock.AutoCloseableReadLock readLock = lock.read()) {

			checkState();

			Utils.checkNonNull(pWbemClassObject, "pWbemClassObject");

			final int lFlags = 0;
			final PointerByReference ppNewInstance = new PointerByReference();

			final HRESULT hResult = (HRESULT) WmiComHelper.comInvokerInvokeNativeObject(
					pWbemClassObject,
					WBEM_CLASS_OBJECT_SPAWN_INSTANCE_VTABLE_ID,
					new Object[] {
							pWbemClassObject,
							lFlags,
							ppNewInstance},
					HRESULT.class);
			WmiWbemServices.checkHResult(hResult, "Fail to invoke IWbemClassObject::SpawnInstance method");

			return ppNewInstance.getValue();

		}
	}

	/**
	 * <p>Invoke the IWbemClassObject::getMethod method.</p>
	 * <p>The IWbemClassObject::GetMethod method returns information about the requested method.</p>
	 * @see <a href="https://docs.microsoft.com/en-us/windows/win32/api/wbemcli/nf-wbemcli-iwbemclassobject-getmethod">IWbemClassObject::GetMethod   method (wbemcli.h)</a>

	 * @param pWbemClassObject A pointer on IWbemClassObject. (mandatory)
	 * @param methodName The method name. This cannot be NULL. (mandatory)
	 * @return A pointer on getMethod method.
	 *
	 * @throws WmiComException For any problem encountered with JNA.
	 */
	Pointer getMethod(
			final Pointer pWbemClassObject,
			final String methodName) throws WmiComException {

		Utils.checkNonNull(pWbemClassObject, "pWbemClassObject");
		Utils.checkNonNull(methodName, "methodName");

		final WString wszName = new WString(methodName);
		final int lFlags = 0;
		final PointerByReference ppInSignature = new PointerByReference();
		final PointerByReference ppOutSignature = null;

		final HRESULT hResult = (HRESULT) WmiComHelper.comInvokerInvokeNativeObject(
				pWbemClassObject,
				WBEM_CLASS_OBJECT_GET_METHOD_VTABLE_ID,
				new Object[] {
						pWbemClassObject,
						wszName,
						lFlags,
						ppInSignature,
						ppOutSignature},
				HRESULT.class);
		WmiWbemServices.checkHResult(hResult, "Fail to invoke IWbemClassObject::getMethod method");
		return ppInSignature.getValue();
	}

	public String getNetworkResource() {
		return networkResource;
	}

	@Override
	public String getHostname() {
		return hostname;
	}

	@Override
	public String getUsername() {
		return username;
	}

	@Override
	public char[] getPassword() {
		return password;
	}

	public IWbemServices getWbemServices() {
		return wbemServices;
	}

	public IWbemLocator getWbemLocator() {
		return wbemLocator;
	}

	public IWbemContext getContext() {
		return context;
	}

	public CoAuthIdentity getAuthIdent() {
		return authIdent;
	}

	public boolean isClosed() {
		return isClosed;
	}
}
