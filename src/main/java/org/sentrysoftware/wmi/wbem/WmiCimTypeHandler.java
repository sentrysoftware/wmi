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

import org.sentrysoftware.wmi.Utils;
import com.sun.jna.platform.win32.COM.COMUtils;
import com.sun.jna.platform.win32.COM.IUnknown;
import com.sun.jna.platform.win32.COM.Unknown;
import com.sun.jna.platform.win32.COM.Wbemcli;
import com.sun.jna.platform.win32.COM.Wbemcli.IWbemClassObject;
import com.sun.jna.platform.win32.Guid.REFIID;
import com.sun.jna.platform.win32.OaIdl.SAFEARRAY;
import com.sun.jna.platform.win32.OleAuto;
import com.sun.jna.platform.win32.Variant.VARIANT.ByReference;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WmiCimTypeHandler {

	/**
	 * Private constructor, as this class cannot be instantiated (it's pure static)
	 */
	private WmiCimTypeHandler() {}

	/**
	 * Map of functions that convert WBEM types to Java corresponding class/type
	 */
	private static final Map<Integer, Function<ByReference, Object>> CIMTYPE_TO_CONVERTER_MAP;
	static {
		final Map<Integer, Function<ByReference, Object>> map = new HashMap<>();

		map.put(Wbemcli.CIM_EMPTY, value -> null);

		map.put(Wbemcli.CIM_BOOLEAN, ByReference::booleanValue);

		map.put(Wbemcli.CIM_UINT8, ByReference::byteValue);
		map.put(Wbemcli.CIM_UINT16, ByReference::intValue);
		map.put(Wbemcli.CIM_UINT32, ByReference::intValue);
		map.put(Wbemcli.CIM_UINT64, ByReference::stringValue);
		map.put(Wbemcli.CIM_SINT8, ByReference::shortValue);
		map.put(Wbemcli.CIM_SINT16, ByReference::shortValue);
		map.put(Wbemcli.CIM_SINT32, ByReference::intValue);
		map.put(Wbemcli.CIM_SINT64, ByReference::stringValue);

		map.put(Wbemcli.CIM_REAL32, ByReference::floatValue);
		map.put(Wbemcli.CIM_REAL64, ByReference::doubleValue);

		map.put(Wbemcli.CIM_CHAR16, ByReference::shortValue);
		map.put(Wbemcli.CIM_STRING, ByReference::stringValue);

		map.put(Wbemcli.CIM_REFERENCE, WmiCimTypeHandler::convertCimReference);
		map.put(Wbemcli.CIM_DATETIME, WmiCimTypeHandler::convertCimDateTime);

		CIMTYPE_TO_CONVERTER_MAP = Collections.unmodifiableMap(map);
	}

	private static final String CIM_OBJECT_LABEL = "CIM_OBJECT";

	/**
	 * Convert a ByReference value holding a CIM_DATETIME (i.e. a string in the form
	 * of <code>yyyymmddHHMMSS.mmmmmmsUUU</code>) to an OffsetDateTime object
	 * @param value ByReference value with a CIM_DATETIME string
	 * @return OffsetDateTime instance
	 */
	static OffsetDateTime convertCimDateTime(final ByReference value) {
		return Utils.convertCimDateTime(value.stringValue());
	}

	/**
	 * Convert a CIM_REFERENCE into a String
	 * @param value CIM_REFERENCE
	 * @return a proper String
	 */
	static String convertCimReference(final ByReference value) {
		return WmiCimTypeHandler.convertCimReference(value.stringValue());
	}

	/**
	 * Convert a CIM_REFERENCE into a String
	 * @param value CIM_REFERENCE
	 * @return a proper String
	 */
	static String convertCimReference(final String reference) {

		if (reference == null) {
			return null;
		}

		// Remove the "\\hostname\namespace:" prefix (i.e. anything before the first colon)
		final int colonIndex = reference.indexOf(':');
		return colonIndex > -1 ? reference.substring(colonIndex + 1) : reference;

	}

	/**
	 * Convert the WBEM SAFEARRAY value type into an array.
	 *
	 * @param array Reference to SAFEARRAY
	 * @param property The Property to retrieve. An Entry with the property name as the key and a set of sub properties to retrieve if exists.
	 * @return A Map with the property value converted as a Java object, or null if property cannot be retrieved. The key is the property name as defined in the select request. (example: DriveInfo.Name)
	 */
	static Map<String, Object> convertSafeArray(
			final ByReference array,
			final int cimType,
			final Entry<String, Set<String>> property) {

		// Get the SAFEARRAY
		final SAFEARRAY safeArray = (SAFEARRAY) array.getValue();
		if (safeArray == null) {
			return Collections.singletonMap(property.getKey(), null);
		}

		safeArray.lock();

		// Get the properties of the array
		final int lowerBound = safeArray.getLBound(0);
		final int length =  safeArray.getUBound(0) - lowerBound + 1;

		// Convert to a Java array
		final Object[] resultArray = new Object[length];
		for (int i = 0; i < length; i++) {
			resultArray[i] = safeArray.getElement(lowerBound + i);
		}

		safeArray.unlock();

		// Simplified conversion of the values, since SAFEARRAY.getElement()
		// did most of the job already, except for CIM_REFERENCE and CIM_DATETIME
		if (cimType == Wbemcli.CIM_REFERENCE) {
			return Collections.singletonMap(
					property.getKey(),
					Stream.of(resultArray)
						.map(String.class::cast)
						.map(WmiCimTypeHandler::convertCimReference)
						.toArray());
		}
		if (cimType == Wbemcli.CIM_DATETIME) {
			return Collections.singletonMap(
					property.getKey(),
					Stream.of(resultArray)
						.map(String.class::cast)
						.map(Utils::convertCimDateTime)
						.toArray());
		}
		if (cimType == Wbemcli.CIM_OBJECT) {
			if (property.getValue().isEmpty()) {
				return Collections.singletonMap(property.getKey(), new String[] {CIM_OBJECT_LABEL});
			}

			final Map<String, List<Object>> resulMap = new HashMap<>();

			for (final Object resultValue : resultArray) {
				final Optional<IWbemClassObject> maybeClassObject =
						getUnknownWbemClassObject(resultValue);
				if (!maybeClassObject.isPresent()) {
					continue;
				}

				final Map<String, String> subPropertiesNames =
						getSubPropertiesNamesFromClass(maybeClassObject.get());

				try {
					property.getValue().stream()
					.map(subProperty -> subPropertiesNames.get(subProperty.toLowerCase()))
					.forEach(subProperty -> resulMap
								.computeIfAbsent(
										buildCimObjectSubPropertyName(property, subProperty),
										key -> new ArrayList<>())
								.add(
										getPropertyValue(
												maybeClassObject.get(),
												new AbstractMap.SimpleEntry<String, Set<String>>(
														subProperty,
														Collections.emptySet()))
										.get(subProperty)));
				} finally {
					maybeClassObject.get().Release();
				}
			}

			return resulMap.entrySet().stream()
					.collect(Collectors.toMap(Entry::getKey, entry -> entry.getValue().toArray()));
		}

		// Default: return the array straight away
		return Collections.singletonMap(property.getKey(), resultArray);
	}

	/**
	 * Convert the wanted values in the CIM Object structure into a Map.
	 *
	 * @param value The value of the property.
	 * @param property The CIM Object Properties to retrieve.An Entry with the CIM Object Class name as the key and a set of sub properties.
	 * @return A Map with the properties from the CIM Object and their Values, converted as a Java object, or null if property cannot be retrieved. The key is the property as defined in the select request. (example: DriveInfo.Name)
	 */
	static Map<String, Object> convertCimObject(
			final ByReference value,
			final Entry<String, Set<String>> property) {

		final Optional<IWbemClassObject> maybeClassObject =
				getUnknownWbemClassObject(value.getValue());
		if (!maybeClassObject.isPresent()) {
			return property.getValue().stream()
					.collect(
							HashMap::new,
							(map, subProperty) -> map.put(
									buildCimObjectSubPropertyName(property, subProperty),
									null),
							HashMap::putAll);
		}

		try {
			final Map<String, String> subPropertiesNames =
					getSubPropertiesNamesFromClass(maybeClassObject.get());

			return property.getValue().stream()
					.map(subProperty -> subPropertiesNames.get(subProperty.toLowerCase()))
					.collect(
							HashMap::new,
							(map, subProperty) -> map.put(
									buildCimObjectSubPropertyName(property, subProperty),
									getPropertyValue(
											maybeClassObject.get(),
											new AbstractMap.SimpleEntry<String, Set<String>>(
													subProperty,
													Collections.emptySet()))
									.get(subProperty)),
							HashMap::putAll);
		} finally {
			maybeClassObject.get().Release();
		}
	}

	/**
	 * Get a Map of all subProperties names from the class
	 * @param wbemClassObject
	 * @return
	 */
	static Map<String, String> getSubPropertiesNamesFromClass(final IWbemClassObject wbemClassObject) {

		String[] names;
		try {
			names = wbemClassObject.GetNames(null, 0, null);
		} catch (final Throwable e) {
			names = wbemClassObject.GetNames(null, 0, null);
		}

		return Stream.of(names)
				.collect(Collectors.toMap(
						String::toLowerCase,
						Function.identity()));
	}

	/**
	 * Build the CIM Object sub property name as it was in the WQL query.
	 * For example: "CimObjectClass.subProperty"
	 *
	 * @param property The Property to retrieve. An Entry with the property name as the key and a set of sub properties to retrieve if exists.
	 * @param subProperty The Sub property name in the CIM Object structure.
	 * @return
	 */
	static String buildCimObjectSubPropertyName(
			final Entry<String, Set<String>> property,
			final String subProperty) {
		return new StringBuilder()
				.append(property.getKey())
				.append(".")
				.append(subProperty)
				.toString();
	}

	/**
	 * Get the WbemClassObject representing the CIM Object class from the Unknown Object request value.
	 *
	 * @param value The value of the property.
	 * @return An optional with the WbemClassObject representing the CIM Object class. empty optional otherwise.
	 */
	static Optional<IWbemClassObject> getUnknownWbemClassObject(final Object value) {
		if (value == null) {
			return Optional.empty();
		}

		final Unknown unknown = (Unknown) value;
		try {
			final PointerByReference pointerByReference = new PointerByReference();

			final HRESULT hResult = unknown.QueryInterface(
					new REFIID(IUnknown.IID_IUNKNOWN), pointerByReference);
			return COMUtils.FAILED(hResult) ?
					Optional.empty() :
						Optional.of(new IWbemClassObject(pointerByReference.getValue()));
		} finally {
			unknown.Release();
		}
	}

	/**
	 * Convert the specified CIM value into a Java Object depending of its CIM type.
	 * @param value CIM value (ByReference)
	 * @param cimType CIM Type
	 * @param property The Property to retrieve. An Entry with the property name as the key and a set of sub properties to retrieve if exists.
	 * @return A Map with the property value converted as a Java object, or null if property cannot be retrieved. The key is the property name as defined in the select request. (example: DriveInfo.Name)
	 */
	static Map<String, Object> convert(
			final ByReference value,
			final int cimType,
			final Entry<String, Set<String>> property) {

		if (value.getValue() == null) {
			return Collections.singletonMap(property.getKey(), null);
		}

		// Array?
		if ((cimType & Wbemcli.CIM_FLAG_ARRAY) > 0) {
			return convertSafeArray(value, cimType ^ Wbemcli.CIM_FLAG_ARRAY, property);
		}

		if (cimType == Wbemcli.CIM_OBJECT) {
			return property.getValue().isEmpty() ?
					Collections.singletonMap(property.getKey(), CIM_OBJECT_LABEL) :
						convertCimObject(value, property);
		}

		return Collections.singletonMap(
				property.getKey(),
				CIMTYPE_TO_CONVERTER_MAP.getOrDefault(cimType, v -> "Unsupported type").apply(value));

	}

	/**
	 * Get the value of the specified property from the specified WbemClassObject
	 * @see <a href="https://docs.microsoft.com/en-us/windows/win32/api/wbemcli/nf-wbemcli-iwbemclassobject-get">IWbemClassObject::Get method (wbemcli.h)</a>
	 * @param wbemClassObject WbemClassObject
	 * @param property The Property to retrieve. An Entry with the property name as the key and a set of sub properties to retrieve if exists.
	 * @return A Map with the property value converted as a Java object, or null if property cannot be retrieved. The key is the property name as defined in the select request. (example: DriveInfo.Name)
	 */
	public static Map<String, Object> getPropertyValue(
			final IWbemClassObject wbemClassObject,
			final Entry<String, Set<String>> property) {
		try {
			return getPropertyValueFromWbemObject(wbemClassObject, property);
		} catch (final Throwable e) {
			// Retry
			return getPropertyValueFromWbemObject(wbemClassObject, property);
		}
	}

	private static Map<String, Object> getPropertyValueFromWbemObject(
			final IWbemClassObject wbemClassObject,
			final Entry<String, Set<String>> property) {
		final ByReference value = new ByReference();
		final IntByReference pType = new IntByReference();

		// Initialize value, to make sure *VariantClear()* won't throw an exception
		// See https://docs.microsoft.com/en-us/windows/win32/api/wbemcli/nf-wbemcli-iwbemclassobject-get
		OleAuto.INSTANCE.VariantInit(value);

		try {
			final HRESULT hResult = wbemClassObject.Get(
					property.getKey(),
					0,
					value,
					pType,
					new IntByReference()
			);
			if (COMUtils.FAILED(hResult)) {
				return Collections.singletonMap(property.getKey(), null);
			}

			// Special case for __PATH
			if ("__PATH".equalsIgnoreCase(property.getKey())) {
				return Collections.singletonMap(property.getKey(), convertCimReference(value));
			}

			return convert(value, pType.getValue(), property);

		} finally {
			try {
				OleAuto.INSTANCE.VariantClear(value);
			} catch (final Throwable t) {
				/* Do nothing -- This condition rarely happens, but it does, and there's nothing we can do about it */
			}
		}
	}

}
