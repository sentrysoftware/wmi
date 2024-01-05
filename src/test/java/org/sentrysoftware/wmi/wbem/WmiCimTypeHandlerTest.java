package org.sentrysoftware.wmi.wbem;

import org.sentrysoftware.wmi.Utils;
import com.sun.jna.platform.win32.COM.Wbemcli;
import com.sun.jna.platform.win32.COM.Wbemcli.IWbemClassObject;
import com.sun.jna.platform.win32.Variant.VARIANT;
import com.sun.jna.platform.win32.Variant.VARIANT.ByReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

@EnabledOnOs(OS.WINDOWS)
class WmiCimTypeHandlerTest {

	@Test
	void testConvertCimDateTime() {

		assertNull(WmiCimTypeHandler.convertCimDateTime(new ByReference()));

		ByReference invalid = new ByReference(new VARIANT("invalid"));
		assertThrows(IllegalArgumentException.class, () -> WmiCimTypeHandler.convertCimDateTime(invalid));

		OffsetDateTime t1 = OffsetDateTime.of(1975, 3, 24, 19, 30, 00, 0, ZoneOffset.ofHours(1));
		String t1CimString = t1.format(Utils.WBEM_CIM_DATETIME_FORMATTER) + ".000000+060";
		ByReference cimDateTime1 = new ByReference(new VARIANT(t1CimString));
		OffsetDateTime convertedT1 = WmiCimTypeHandler.convertCimDateTime(cimDateTime1);
		assertEquals(t1, convertedT1);
		assertEquals(164917800, convertedT1.toEpochSecond());

		String t2CimString = t1.format(Utils.WBEM_CIM_DATETIME_FORMATTER) + ".000000+059";
		ByReference cimDateTime2 = new ByReference(new VARIANT(t2CimString));
		OffsetDateTime t2 = WmiCimTypeHandler.convertCimDateTime(cimDateTime2);
		assertEquals(60, t2.toEpochSecond() - t1.toEpochSecond());
	}

	@Test
	void testConvertCimReference() {

		assertNull(WmiCimTypeHandler.convertCimReference(new ByReference()));

		{
			ByReference ref = new ByReference(new VARIANT("test"));
			assertEquals("test", WmiCimTypeHandler.convertCimReference(ref));
		}

		{
			ByReference ref = new ByReference(new VARIANT("\\\\hostname\\root\\cimv2:test:colon"));
			assertEquals("test:colon", WmiCimTypeHandler.convertCimReference(ref));
		}
	}

	@Test
	void testConvert() {

		assertEquals(
				Collections.singletonMap("property", null),
				WmiCimTypeHandler.convert(new ByReference(), 1, new AbstractMap.SimpleEntry<String, Set<String>>("property", Collections.emptySet())));

		ByReference r = new ByReference(new VARIANT(5));
		assertEquals(
				Collections.singletonMap("property", 5),
				WmiCimTypeHandler.convert(r, Wbemcli.CIM_SINT32, new AbstractMap.SimpleEntry<String, Set<String>>("property", Collections.emptySet())));
	}

	@Test
	void testConvertCimObject() {

		final ByReference value = Mockito.mock(ByReference.class);

		final AbstractMap.SimpleEntry<String, Set<String>> property = new AbstractMap.SimpleEntry<String, Set<String>>(
				"DriveInfo",
				Arrays.asList("name", "numberpaths", "serialnumber").stream().collect(Collectors.toSet()));

		final AbstractMap.SimpleEntry<String, Set<String>> nameSubProperty = new AbstractMap.SimpleEntry<String, Set<String>>(
				"Name",
				Collections.emptySet());

		final AbstractMap.SimpleEntry<String, Set<String>> numberPathsSubProperty = new AbstractMap.SimpleEntry<String, Set<String>>(
				"NumberPaths",
				Collections.emptySet());

		final AbstractMap.SimpleEntry<String, Set<String>> serialNumberSubProperty = new AbstractMap.SimpleEntry<String, Set<String>>(
				"SerialNumber",
				Collections.emptySet());

		final Map<String, String> subPropertiesNames = new HashMap<>();
		subPropertiesNames.put("name", "Name");
		subPropertiesNames.put("numberpaths", "NumberPaths");
		subPropertiesNames.put("serialnumber", "SerialNumber");

		final IWbemClassObject wbemClassObject = Mockito.mock(IWbemClassObject.class);

		// Case getUnknownWbemClassObject empty
		try (final MockedStatic<WmiCimTypeHandler> mockedWmiCimTypeHandler = Mockito.mockStatic(WmiCimTypeHandler.class)) {

			mockedWmiCimTypeHandler.when(() -> WmiCimTypeHandler.getUnknownWbemClassObject(any())).thenReturn(Optional.empty());
			mockedWmiCimTypeHandler.when(() -> WmiCimTypeHandler.buildCimObjectSubPropertyName(eq(property), anyString())).thenCallRealMethod();
			mockedWmiCimTypeHandler.when(() -> WmiCimTypeHandler.convertCimObject(value, property)).thenCallRealMethod();

			final Map<String, Object> expected = new HashMap<>();
			expected.put("DriveInfo.name", null);
			expected.put("DriveInfo.numberpaths", null);
			expected.put("DriveInfo.serialnumber", null);

			assertEquals(expected, WmiCimTypeHandler.convertCimObject(value, property));
		}

		try (final MockedStatic<WmiCimTypeHandler> mockedWmiCimTypeHandler = Mockito.mockStatic(WmiCimTypeHandler.class)) {

			mockedWmiCimTypeHandler.when(() -> WmiCimTypeHandler.getUnknownWbemClassObject(any())).thenReturn(Optional.of(wbemClassObject));

			mockedWmiCimTypeHandler.when(() -> WmiCimTypeHandler.getSubPropertiesNamesFromClass(wbemClassObject)).thenReturn(subPropertiesNames);

			mockedWmiCimTypeHandler.when(() -> WmiCimTypeHandler.getPropertyValue(wbemClassObject, numberPathsSubProperty))
			.thenReturn(Collections.singletonMap("NumberPaths", 5));

			mockedWmiCimTypeHandler.when(() -> WmiCimTypeHandler.getPropertyValue(wbemClassObject, nameSubProperty))
			.thenReturn(Collections.singletonMap("Name", "MPIO Disk0"));

			mockedWmiCimTypeHandler.when(() -> WmiCimTypeHandler.getPropertyValue(wbemClassObject, serialNumberSubProperty))
			.thenReturn(Collections.singletonMap("SerialNumber", "624A937068B04CE438B46F8A00011BBB"));

			mockedWmiCimTypeHandler.when(() -> WmiCimTypeHandler.buildCimObjectSubPropertyName(eq(property), anyString())).thenCallRealMethod();
			mockedWmiCimTypeHandler.when(() -> WmiCimTypeHandler.convertCimObject(value, property)).thenCallRealMethod();

			final Map<String, Object> expected = new HashMap<>();
			expected.put("DriveInfo.Name", "MPIO Disk0");
			expected.put("DriveInfo.NumberPaths", 5);
			expected.put("DriveInfo.SerialNumber", "624A937068B04CE438B46F8A00011BBB");

			assertEquals(expected, WmiCimTypeHandler.convertCimObject(value, property));
		}
	}
}
