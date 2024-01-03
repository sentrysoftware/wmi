package org.sentrysoftware.wmi;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import org.sentrysoftware.wmi.exceptions.WqlQuerySyntaxException;

class WqlQueryTest {

	@Test
	void testBuildSelectedProperties() {
		assertEquals(Collections.emptyList(), WqlQuery.buildSelectedProperties(null));
		assertEquals(Collections.emptyList(), WqlQuery.buildSelectedProperties(""));
		assertEquals(Collections.emptyList(), WqlQuery.buildSelectedProperties("    "));
		assertEquals(Arrays.asList("propa"), WqlQuery.buildSelectedProperties("PropA"));
		assertEquals(Arrays.asList("propa", "propb"), WqlQuery.buildSelectedProperties("propA,propB"));
		assertEquals(Arrays.asList("propa", "propb", "propc"), WqlQuery.buildSelectedProperties("   propA  ,  propB,propC   "));
	}

	@Test
	void testBuildSupPropertiesMap() {
		assertEquals(Collections.emptyMap(), WqlQuery.buildSupPropertiesMap(null));
		assertEquals(Collections.emptyMap(), WqlQuery.buildSupPropertiesMap(Collections.emptyList()));

		final List<String> propertiesNames = Arrays.asList(
				"active",
				"instancename",
				"attributes.portwwn",
				"attributes.portspeed",
				"attributes.portwwn",
				"attributes.portwwn",
				"attributes.porttype"
		);

		final Map<String, Set<String>> expected = new TreeMap<>();
		expected.put("active", Collections.emptySet());
		expected.put("instancename", Collections.emptySet());
		Set<String> expectedSubProperties = new TreeSet<>();
		expectedSubProperties.add("portwwn");
		expectedSubProperties.add("portspeed");
		expectedSubProperties.add("porttype");
		expected.put("attributes", expectedSubProperties);

		assertEquals(expected, WqlQuery.buildSupPropertiesMap(propertiesNames));
	}

	@Test
	void testNewInstance() throws Exception {
		assertThrows(IllegalArgumentException.class, () -> WqlQuery.newInstance(null));
		assertThrows(WqlQuerySyntaxException.class, () -> WqlQuery.newInstance(""));
		assertThrows(WqlQuerySyntaxException.class, () -> WqlQuery.newInstance("    "));
		assertThrows(WqlQuerySyntaxException.class, () -> WqlQuery.newInstance(""));
		assertThrows(WqlQuerySyntaxException.class, () -> WqlQuery.newInstance(" * From Win32_ComputerSystem"));
		assertThrows(WqlQuerySyntaxException.class, () -> WqlQuery.newInstance("SELECT * "));
		assertThrows(WqlQuerySyntaxException.class, () -> WqlQuery.newInstance("select *  from "));
		assertThrows(WqlQuerySyntaxException.class, () -> WqlQuery.newInstance("select *  Win32_ComputerSystem "));
		assertThrows(WqlQuerySyntaxException.class, () -> WqlQuery.newInstance("select   from   Win32_ComputerSystem "));
		assertThrows(WqlQuerySyntaxException.class, () -> WqlQuery.newInstance("select DNSHostName,SystemType "));
		assertThrows(WqlQuerySyntaxException.class, () -> WqlQuery.newInstance("SELECT * FROMWin32_ComputerSystem"));
		assertThrows(WqlQuerySyntaxException.class, () -> WqlQuery.newInstance("SELECT * * FROM Win32_ComputerSystem"));
		assertThrows(WqlQuerySyntaxException.class, () -> WqlQuery.newInstance("SELECT select __PATH,    DNSHostName   FROM      Win32_ComputerSystem"));
		assertThrows(WqlQuerySyntaxException.class, () -> WqlQuery.newInstance("SELECT* FROM Win32_ComputerSystem"));
		assertThrows(WqlQuerySyntaxException.class, () -> WqlQuery.newInstance("SELECT __PATH,DNSHost-Name,SystemType FROM Win32_ComputerSystem"));
		assertThrows(WqlQuerySyntaxException.class, () -> WqlQuery.newInstance("SELECT __PATH,DNSHostName,,SystemType FROM Win32_ComputerSystem"));
		assertThrows(WqlQuerySyntaxException.class, () -> WqlQuery.newInstance("SELECT __PATH,DNSHostName, ,SystemType FROM Win32_ComputerSystem"));
		assertThrows(WqlQuerySyntaxException.class, () -> WqlQuery.newInstance("SELECT __PATH,DNSHostName,SystemType, FROM Win32_ComputerSystem"));
		assertThrows(WqlQuerySyntaxException.class, () -> WqlQuery.newInstance("SELECT ,__PATH,DNSHostName, SystemType FROM Win32_ComputerSystem"));
		assertThrows(WqlQuerySyntaxException.class, () -> WqlQuery.newInstance("SELECT __PATH   ,  DNSHostName   ,   SystemType   ,   FROM Win32_ComputerSystem"));
		assertThrows(WqlQuerySyntaxException.class, () -> WqlQuery.newInstance("SELECT __PATH, DNSHostName, $SystemType FROM Win32_ComputerSystem"));
		assertThrows(WqlQuerySyntaxException.class, () -> WqlQuery.newInstance("SELECT*FROM Win32_ComputerSystem"));
		assertThrows(WqlQuerySyntaxException.class, () -> WqlQuery.newInstance("select__PATH,DNSHostName,SystemType from Win32_ComputerSystem"));
		assertThrows(WqlQuerySyntaxException.class, () -> WqlQuery.newInstance("select DNSHostName,SystemType fromWin32_ComputerSystem"));
		assertThrows(WqlQuerySyntaxException.class, () -> WqlQuery.newInstance("select DNSHostName,SystemTypefrom Win32_ComputerSystem"));
		assertThrows(WqlQuerySyntaxException.class, () -> WqlQuery.newInstance("select *, Model,Manufacturer from where Name = \"PC14\""));
		assertThrows(WqlQuerySyntaxException.class, () -> WqlQuery.newInstance("select Model,Manufacturer where Name = \"PC14\""));
		assertThrows(WqlQuerySyntaxException.class, () -> WqlQuery.newInstance("select    __PATH   ,    DNSHostName,   SystemType   where    Win32_ComputerSystem    from   Name = \"PC14\"  "));
		assertThrows(WqlQuerySyntaxException.class, () -> WqlQuery.newInstance("ASSOCIATORS OF Win32_Process.ProcessId"));
		assertThrows(WqlQuerySyntaxException.class, () -> WqlQuery.newInstance("  SELECT  ASSOCIATORS OF\n{\tWin32_Process.ProcessId=1\n}"));
		assertThrows(WqlQuerySyntaxException.class, () -> WqlQuery.newInstance("SELECT Temperature, FROM ASSOCIATORS OF { Win32_Process.ProcessId=1 }"));
		assertThrows(WqlQuerySyntaxException.class, () -> WqlQuery.newInstance("SELECT Temperature, * FROM ASSOCIATORS OF { Win32_Process.ProcessId=1 }"));
		assertThrows(WqlQuerySyntaxException.class, () -> WqlQuery.newInstance("SELECT Temperature,,TemperatureMax FROM ASSOCIATORS OF { Win32_Process.ProcessId=1 }"));

		{
			WqlQuery wql = WqlQuery.newInstance("SELECT * FROM Win32_ComputerSystem");
			assertEquals(Collections.emptyList(), wql.getSelectedProperties());
		}

		{
			WqlQuery wql = WqlQuery.newInstance("select    __PATH   ,    SystemType,    DNSHostName  from    Win32_ComputerSystem");
			assertEquals(Arrays.asList("__path", "systemtype", "dnshostname"), wql.getSelectedProperties());
			assertEquals("SELECT __path,systemtype,dnshostname FROM Win32_ComputerSystem", wql.getCleanWql());
		}

		{
			WqlQuery wql = WqlQuery.newInstance("SELECT DriveInfo.Name,DriveInfo.NumberPaths,DriveInfo.SerialNumber FROM MPIO_DISK_INFO WHERE condition");
			assertEquals(Arrays.asList("driveinfo.name", "driveinfo.numberpaths", "driveinfo.serialnumber"), wql.getSelectedProperties());
			assertEquals("SELECT driveinfo FROM MPIO_DISK_INFO WHERE condition", wql.getCleanWql());
		}

		{
			WqlQuery wql = WqlQuery.newInstance("SELECT Temperature.Current, Temperature.Max FROM ASSOCIATORS OF {Win32_Process.ProcessId=1}");
			assertEquals(Arrays.asList("temperature.current", "temperature.max"), wql.getSelectedProperties());
			assertEquals("ASSOCIATORS OF {Win32_Process.ProcessId=1}", wql.getCleanWql());
			Map<String, Set<String>> propertiesMap = wql.getSubPropertiesMap();
			assertEquals(1, propertiesMap.size(), "Temperature.Current and Temperature.Max must have been merged under Temperature");
			assertEquals(2, propertiesMap.get("temperature").size());
		}

		{
			WqlQuery wql = WqlQuery.newInstance("  ASSOCIATORS OF {Win32_Process.ProcessId=1} WHERE AssocClass = Win32_Test");
			assertEquals(Collections.emptyList(), wql.getSelectedProperties());
			assertEquals("ASSOCIATORS OF {Win32_Process.ProcessId=1} WHERE AssocClass = Win32_Test", wql.getCleanWql());
		}

	}
}
