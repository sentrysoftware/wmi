package org.sentrysoftware.wmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class WmiHelperTest {

	@Test
	void testCheckWQLQuerySyntax() throws Exception {
		assertFalse(WmiHelper.isValidWql(""));
		assertFalse(WmiHelper.isValidWql(" * From Win32_ComputerSystem"));
		assertFalse(WmiHelper.isValidWql("SELECT * "));
		assertFalse(WmiHelper.isValidWql("select *  from "));
		assertFalse(WmiHelper.isValidWql("select *  Win32_ComputerSystem "));
		assertFalse(WmiHelper.isValidWql("select   from   Win32_ComputerSystem "));
		assertFalse(WmiHelper.isValidWql("select DNSHostName,SystemType "));
		assertFalse(WmiHelper.isValidWql("SELECT * FROMWin32_ComputerSystem"));
		assertFalse(WmiHelper.isValidWql("SELECT * * FROM Win32_ComputerSystem"));
		assertFalse(WmiHelper.isValidWql("SELECT __PATH,    DNSHostName , FROM   from    Win32_ComputerSystem"));
		assertFalse(WmiHelper.isValidWql("SELECT select __PATH,    DNSHostName   FROM      Win32_ComputerSystem"));
		assertFalse(WmiHelper.isValidWql("SELECT* FROM Win32_ComputerSystem"));
		assertFalse(WmiHelper.isValidWql("SELECT __PATH,DNSHost-Name,SystemType FROM Win32_ComputerSystem"));
		assertFalse(WmiHelper.isValidWql("SELECT __PATH,DNSHostName,,SystemType FROM Win32_ComputerSystem"));
		assertFalse(WmiHelper.isValidWql("SELECT __PATH,DNSHostName, ,SystemType FROM Win32_ComputerSystem"));
		assertFalse(WmiHelper.isValidWql("SELECT ,__PATH,DNSHostName, SystemType FROM Win32_ComputerSystem"));
		assertFalse(WmiHelper.isValidWql("SELECT __PATH   ,  DNSHostName   ,   SystemType   ,   FROM Win32_ComputerSystem"));
		assertFalse(WmiHelper.isValidWql("SELECT __PATH, DNSHostName, $SystemType FROM Win32_ComputerSystem"));
		assertFalse(WmiHelper.isValidWql("SELECT*FROM Win32_ComputerSystem"));
		assertFalse(WmiHelper.isValidWql("select__PATH,DNSHostName,SystemType from Win32_ComputerSystem"));
		assertFalse(WmiHelper.isValidWql("select DNSHostName,SystemType fromWin32_ComputerSystem"));
		assertFalse(WmiHelper.isValidWql("select DNSHostName,SystemTypefrom Win32_ComputerSystem"));
		assertFalse(WmiHelper.isValidWql("select *, Model,Manufacturer from where Name = \"PC14\""));
		assertFalse(WmiHelper.isValidWql("select Model,Manufacturer where Name = \"PC14\""));
		assertFalse(WmiHelper.isValidWql("SELECT * FROM Win32_ComputerSystem$"));
		assertFalse(WmiHelper.isValidWql("SELECT * FROM Win32_ComputerSystem ,  "));
		assertFalse(WmiHelper.isValidWql("select    __PATH   ,    DNSHostName,   SystemType   from    where   "));
		assertFalse(WmiHelper.isValidWql("select    __PATH   ,    DNSHostName,   SystemType   from    from   "));
		assertFalse(WmiHelper.isValidWql("select    __PATH   ,    DNSHostName,   SystemType   from  ,  Win32_ComputerSystem  "));
		assertFalse(WmiHelper.isValidWql("select    __PATH   ,    DNSHostName,   SystemType   from    Win32_ComputerSystem    Win32_ComputerSystem  "));
		assertFalse(WmiHelper.isValidWql("select    __PATH   ,    DNSHostName,   SystemType   where    Win32_ComputerSystem    from   Name = \"PC14\"  "));
		assertFalse(WmiHelper.isValidWql("select    __PATH   ,    DNSHostName,   SystemType   from  where  Win32_ComputerSystem    Name = \"PC14\"  "));

		assertTrue(WmiHelper.isValidWql("SELECT * FROM Win32_ComputerSystem"));
		assertTrue(WmiHelper.isValidWql("      select    *     from     Win32_ComputerSystem    "));
		assertTrue(WmiHelper.isValidWql("    SELECT    Manufacturer    FROM     Win32_ComputerSystem     "));
		assertTrue(WmiHelper.isValidWql("SELECT __PATH,DNSHostName,SystemType FROM Win32_ComputerSystem"));
		assertTrue(WmiHelper.isValidWql("select    __PATH   ,    DNSHostName,   SystemType   from    Win32_ComputerSystem  "));
		assertTrue(WmiHelper.isValidWql("select Model, Manufacturer from Win32_ComputerSystem where Name = \"PC14\""));
		assertTrue(WmiHelper.isValidWql("SELECT * FROM Win32_Process WHERE CommandLine='bash select test 0'"));
		assertTrue(WmiHelper.isValidWql("SELECT DriveInfo.Name,DriveInfo.NumberPaths,DriveInfo.SerialNumber FROM MPIO_DISK_INFO"));
		assertTrue(WmiHelper.isValidWql("SELECT Active,InstanceName,Attributes.PortWWN,Attributes.PortSpeed,Attributes.PortWWN,Attributes.PortWWN,Attributes.PortType FROM MSFC_FibrePortHBAAttributes"));
	}

	@Test
	void testExtractPropertiesFromResult() {
		List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
		Map<String, Object> row = new HashMap<String, Object>();
		row.put("b", null);
		row.put("C", null);
		row.put("A", null);
		result.add(row);
		result.add(row);

		assertEquals(Collections.emptyList(), WmiHelper.extractPropertiesFromResult(Collections.emptyList(), "SELECT * FROM test"));
		assertEquals(Arrays.asList("a", "b"), WmiHelper.extractPropertiesFromResult(Collections.emptyList(), "SELECT A, B FROM test"));

		assertEquals(Arrays.asList("A", "b", "C"), WmiHelper.extractPropertiesFromResult(result, "SELECT * FROM test"));
		assertEquals(Arrays.asList("A", "b", "C"), WmiHelper.extractPropertiesFromResult(result, "SELECT a,b,c FROM test"));
		assertEquals(Arrays.asList("A", "b", "C", "d"), WmiHelper.extractPropertiesFromResult(result, "SELECT a,b,c,d FROM test"));
		assertEquals(Arrays.asList("C"), WmiHelper.extractPropertiesFromResult(result, "SELECT c FROM test"));
		assertEquals(Arrays.asList("A", "C", "d"), WmiHelper.extractPropertiesFromResult(result, "SELECT a,c,d FROM test"));
		assertEquals(Arrays.asList("A", "b"), WmiHelper.extractPropertiesFromResult(result, "SELECT A, B FROM test"));
	}
}
