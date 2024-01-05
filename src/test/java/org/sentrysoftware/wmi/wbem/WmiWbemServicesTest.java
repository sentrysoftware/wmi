package org.sentrysoftware.wmi.wbem;

import org.sentrysoftware.wmi.exceptions.WqlQuerySyntaxException;
import org.sentrysoftware.wmi.exceptions.WmiComException;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.OleAuto;
import com.sun.jna.platform.win32.Variant;
import com.sun.jna.platform.win32.WinNT.HRESULT;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.Thread.UncaughtExceptionHandler;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.WINDOWS)
@TestMethodOrder(OrderAnnotation.class)
class WmiWbemServicesTest {

	@Test
	@Order(1)
	void testNonConnected() throws Exception {
		final WmiWbemServices wmiWbemServices = WmiWbemServices.getInstance("root/cimv2", null, null);
		wmiWbemServices.close();
		assertThrows(IllegalStateException.class, () -> wmiWbemServices.executeWql("SELECT Name FROM Win32_OperatingSystem", 30000));
	}

	@Test
	@Order(3)
	void testSimpleWql() throws Exception {
		try (WmiWbemServices wmiWbemServices = WmiWbemServices.getInstance("root/cimv2", null, null)) {
			final List<Map<String, Object>> result = wmiWbemServices.executeWql("SELECT Name FROM Win32_OperatingSystem", 30000);
			assertEquals(1, result.size());
		}
	}

	@Test
	@Order(4)
	void testDoubleWql() throws Exception {
		try (WmiWbemServices wmiWbemServices = WmiWbemServices.getInstance("root/cimv2", null, null)) {
			final List<Map<String, Object>> result = wmiWbemServices.executeWql("SELECT Name FROM Win32_OperatingSystem", 30000);
			assertEquals(1, result.size());
			final List<Map<String, Object>> result2 = wmiWbemServices.executeWql("SELECT Name FROM Win32_Processor", 30000);
			assertTrue(result2.size() > 0);
		}
	}

	@Test
	@Order(5)
	void testDoubleWbemService() throws Exception {
		try (WmiWbemServices wmiWbemServices = WmiWbemServices.getInstance("root/cimv2", null, null)) {
			final List<Map<String, Object>> result = wmiWbemServices.executeWql("SELECT Name FROM Win32_OperatingSystem", 30000);
			assertEquals(1, result.size());
		}
		try (WmiWbemServices wmiWbemServices = WmiWbemServices.getInstance("root/cimv2", null, null)) {
			final List<Map<String, Object>> result = wmiWbemServices.executeWql("SELECT Name FROM Win32_OperatingSystem", 30000);
			assertEquals(1, result.size());
		}
	}

	@Test
	@Order(7)
	void testReconnect() throws Exception {
		WmiWbemServices wmiWbemServices = WmiWbemServices.getInstance("root/cimv2", null, null);
		try {
			final List<Map<String, Object>> result = wmiWbemServices.executeWql("SELECT Name FROM Win32_OperatingSystem", 30000);
			assertEquals(1, result.size());
		} finally {
			wmiWbemServices.close();
		}
		try {
			wmiWbemServices = WmiWbemServices.getInstance("root/cimv2", null, null);
			final List<Map<String, Object>> result = wmiWbemServices.executeWql("SELECT Name FROM Win32_OperatingSystem", 30000);
			assertEquals(1, result.size());
		} finally {
			wmiWbemServices.close();
		}
	}

	@Test
	@Order(8)
	void testInvalidWqlReportedByWmi() throws Exception {
		try (WmiWbemServices wmiWbemServices = WmiWbemServices.getInstance("root/cimv2", null, null)) {
			assertThrows(WqlQuerySyntaxException.class, () -> wmiWbemServices.executeWql("SELECT 1 FROM 2 WHERE 3 FROM 1", 30000));
		}
	}

	@Test
	@Order(9)
	void testInvalidWqlReportedByUs() throws Exception {
		try (WmiWbemServices wmiWbemServices = WmiWbemServices.getInstance("root/cimv2", null, null)) {
			assertThrows(WqlQuerySyntaxException.class, () -> wmiWbemServices.executeWql("SELECT", 30000));
		}
	}

	@Test
	@Order(10)
	void testInvalidWmiClass() throws Exception {
		try (WmiWbemServices wmiWbemServices = WmiWbemServices.getInstance("root/cimv2", null, null)) {
			assertThrows(WmiComException.class, () -> wmiWbemServices.executeWql("SELECT * FROM nonexistent", 30000));
		}
	}

	@Test
	@Order(11)
	void testPasswordOnLocal() throws Exception {
		try (WmiWbemServices wmiWbemServices = WmiWbemServices.getInstance("root/cimv2", null, null)) {
			assertThrows(IllegalArgumentException.class, () -> WmiWbemServices.getInstance("root/cimv2", "invalid", "invalid".toCharArray()));
		}
	}

	@Test
	@Order(12)
	void testConsecutive() throws Exception {
		try (WmiWbemServices wmiWbemServices = WmiWbemServices.getInstance("root/cimv2", null, null)) {
			assertThrows(WqlQuerySyntaxException.class, () -> wmiWbemServices.executeWql("SELECT", 30000));
		}
		try (WmiWbemServices wmiWbemServices = WmiWbemServices.getInstance("root/cimv2", null, null)) {
			final List<Map<String, Object>> result = wmiWbemServices.executeWql("SELECT Name FROM Win32_OperatingSystem", 30000);
			assertEquals(1, result.size());
		}
	}

	@Test
	@Order(13)
	void testReferenceValue() throws Exception {
		try (WmiWbemServices wmiWbemServices = WmiWbemServices.getInstance("root/cimv2", null, null)) {
			final List<Map<String, Object>> result = wmiWbemServices.executeWql("SELECT Antecedent FROM Win32_LoggedOnUser", 30000);
			final String ref = (String) result.get(0).get("Antecedent");
			assertFalse(ref.contains(":"));
		}

		try (WmiWbemServices wmiWbemServices = WmiWbemServices.getInstance("root/cimv2", null, null)) {
			final List<Map<String, Object>> result = wmiWbemServices.executeWql("SELECT __PATH FROM Win32_OperatingSystem", 30000);
			final String path = (String) result.get(0).get("__PATH");
			assertFalse(path.contains(":"));
		}
}

	@Test
	@Order(14)
	void testDateTimeValue() throws Exception {
		try (WmiWbemServices wmiWbemServices = WmiWbemServices.getInstance("root/cimv2", null, null)) {
			final List<Map<String, Object>> result = wmiWbemServices.executeWql("SELECT LocalDateTime FROM Win32_OperatingSystem", 30000);
			final OffsetDateTime time = (OffsetDateTime) result.get(0).get("LocalDateTime");

			final long timeDiff = Math.abs(time.toEpochSecond() * 1000 - System.currentTimeMillis());
			assertTrue(timeDiff < 10000);
		}
	}

	@Test
	@Order(15)
	void testArrayValue() throws Exception {
		try (WmiWbemServices wmiWbemServices = WmiWbemServices.getInstance("root/cimv2", null, null)) {
			final List<Map<String, Object>> result = wmiWbemServices.executeWql("SELECT MUILanguages FROM Win32_OperatingSystem", 30000);
			assertTrue(result.get(0).get("MUILanguages").getClass().isArray());
		}
	}

	@Test
	@Order(16)
	void testCimObjectDefaultValue() throws Exception {
		try (WmiWbemServices wmiWbemServices = WmiWbemServices.getInstance("root/WMI", null, null)) {
			final List<Map<String, Object>> result = wmiWbemServices.executeWql("SELECT PerfData FROM MSDiskDriver_Performance", 30000);
			if (result.size() > 0) {
				assertEquals("CIM_OBJECT", result.get(0).get("PerfData"));
			}
		}
	}

	@Test
	@Order(17)
	void testSystemProperties() throws Exception {
		try (WmiWbemServices wmiWbemServices = WmiWbemServices.getInstance("root/cimv2", null, null)) {
			final List<Map<String, Object>> result = wmiWbemServices.executeWql("SELECT * FROM CIM_Realizes", 30000);
			assertTrue(result.get(0).entrySet().size() > 5);
			assertFalse(result.get(0).get("__PATH") == null);
			assertFalse(((String) result.get(0).get("__PATH")).isEmpty());
		}
	}

	@Test
	@Order(18)
	void testAssociators() throws Exception {
		// Simple case
		try (WmiWbemServices wmiWbemServices = WmiWbemServices.getInstance("root/cimv2", null, null)) {
			final List<Map<String, Object>> result = wmiWbemServices.executeWql("ASSOCIATORS OF {Win32_OperatingSystem=@}", 30000);
			assertTrue(result.size() > 0, "There must be at least 1 result in ASSOCIATORS of {Win32_OperatingSystem=@}");
		}

		// Something more complicated
		try (WmiWbemServices wmiWbemServices = WmiWbemServices.getInstance("root/cimv2", null, null)) {
			String computerName = (String)wmiWbemServices.executeWql("SELECT Name FROM Win32_ComputerSystem", 30000).get(0).get("Name");
			assertNotNull(computerName);
			assertFalse(computerName.isEmpty());

			final List<Map<String, Object>> result = wmiWbemServices.executeWql("SELECT Name FROM ASSOCIATORS OF {Win32_OperatingSystem=@} WHERE ResultClass = Win32_ComputerSystem", 30000);
			assertEquals(computerName, result.get(0).get("Name"));
		}
}

	@Test
	@Order(19)
	void testGetObject() throws Exception {
		final String objectPah = "Win32_Processs";

		final WmiWbemServices wmiWbemServices = WmiWbemServices.getInstance("root/cimv2", null, null);

		// check objectPah null
		assertThrows(IllegalArgumentException.class, () -> wmiWbemServices.getObject(null));

		// check ok
		try (final MockedStatic<WmiComHelper> mockedWmiComHelper = Mockito.mockStatic(WmiComHelper.class)) {
			final Object hResult = new HRESULT(0);
			mockedWmiComHelper.when(() -> WmiComHelper.comInvokerInvokeNativeObject(
					ArgumentMatchers.any(Pointer.class),
					ArgumentMatchers.eq(6),
					ArgumentMatchers.any(Object[].class),
					ArgumentMatchers.any())).thenReturn(hResult);

			wmiWbemServices.getObject(objectPah);
		}

		wmiWbemServices.close();
		assertThrows(IllegalStateException.class, () -> wmiWbemServices.getObject(objectPah));
	}

	@Test
	@Order(20)
	void testExecuteMethod() throws Exception {
		final String objectPah = "Win32_Processs";
		final String methodName = "Create";
		final Pointer pInParams = Mockito.mock(Pointer.class);

		final WmiWbemServices wmiWbemServices = WmiWbemServices.getInstance("root/cimv2", null, null);

		// check objectPah null
		assertThrows(IllegalArgumentException.class, () -> wmiWbemServices.executeMethod(null, methodName, pInParams));

		// check methodName null
		assertThrows(IllegalArgumentException.class, () -> wmiWbemServices.executeMethod(objectPah, null, pInParams));

		// check ok
		try (final MockedStatic<WmiComHelper> mockedWmiComHelper = Mockito.mockStatic(WmiComHelper.class)) {
			final Object hResult = new HRESULT(0);
			mockedWmiComHelper.when(() -> WmiComHelper.comInvokerInvokeNativeObject(
					ArgumentMatchers.any(Pointer.class),
					ArgumentMatchers.eq(24),
					ArgumentMatchers.any(Object[].class),
					ArgumentMatchers.any())).thenReturn(hResult);

			wmiWbemServices.executeMethod(objectPah, methodName, pInParams);
		}

		wmiWbemServices.close();
	}

	@Test
	@Order(21)
	void testObjectPut() throws Exception {
		final Pointer pWbemClassObject = Mockito.mock(Pointer.class);
		final String propertyName = "CommandLine";
		final Variant.VARIANT pVal = new Variant.VARIANT("cmd.exe /c ipconfig");

		final WmiWbemServices wmiWbemServices = WmiWbemServices.getInstance("root/cimv2", null, null);

		// check pWbemClassObject null
		assertThrows(IllegalArgumentException.class, () -> wmiWbemServices.objectPut(null, propertyName, pVal));

		// check propertyName null
		assertThrows(IllegalArgumentException.class, () -> wmiWbemServices.objectPut(pWbemClassObject, null, pVal));

		// check ok
		try (final MockedStatic<WmiComHelper> mockedWmiComHelper = Mockito.mockStatic(WmiComHelper.class)) {
			final Object hResult = new HRESULT(0);
			mockedWmiComHelper.when(() -> WmiComHelper.comInvokerInvokeNativeObject(
					ArgumentMatchers.any(Pointer.class),
					ArgumentMatchers.eq(5),
					ArgumentMatchers.any(Object[].class),
					ArgumentMatchers.any())).thenReturn(hResult);

			wmiWbemServices.objectPut(pWbemClassObject, propertyName, pVal);
		}

		wmiWbemServices.close();
		assertThrows(IllegalStateException.class, () -> wmiWbemServices.objectPut(pWbemClassObject, propertyName, pVal));

		OleAuto.INSTANCE.VariantClear(pVal);
	}

	@Test
	@Order(22)
	void testSpawnInstance() throws Exception {
		final Pointer pWbemClassObject = Mockito.mock(Pointer.class);

		final WmiWbemServices wmiWbemServices = WmiWbemServices.getInstance("root/cimv2", null, null);

		// check pWbemClassObject null
		assertThrows(IllegalArgumentException.class, () -> wmiWbemServices.spawnInstance(null));

		// check ok
		try (final MockedStatic<WmiComHelper> mockedWmiComHelper = Mockito.mockStatic(WmiComHelper.class)) {
			final Object hResult = new HRESULT(0);
			mockedWmiComHelper.when(() -> WmiComHelper.comInvokerInvokeNativeObject(
					ArgumentMatchers.any(Pointer.class),
					ArgumentMatchers.eq(15),
					ArgumentMatchers.any(Object[].class),
					ArgumentMatchers.any())).thenReturn(hResult);

			wmiWbemServices.spawnInstance(pWbemClassObject);
		}

		wmiWbemServices.close();
		assertThrows(IllegalStateException.class, () -> wmiWbemServices.spawnInstance(pWbemClassObject));
	}

	@Test
	@Order(23)
	void testGetMethod() throws Exception {
		final Pointer pWbemClassObject = Mockito.mock(Pointer.class);
		final String methodName = "Create";

		final WmiWbemServices wmiWbemServices = WmiWbemServices.getInstance("root/cimv2", null, null);

		// check pWbemClassObject null
		assertThrows(IllegalArgumentException.class, () -> wmiWbemServices.getMethod(null, methodName));

		// check methodName null
		assertThrows(IllegalArgumentException.class, () -> wmiWbemServices.getMethod(pWbemClassObject, null));

		// check ok
		try (final MockedStatic<WmiComHelper> mockedWmiComHelper = Mockito.mockStatic(WmiComHelper.class)) {
			final Object hResult = new HRESULT(0);
			mockedWmiComHelper.when(() -> WmiComHelper.comInvokerInvokeNativeObject(
					ArgumentMatchers.any(Pointer.class),
					ArgumentMatchers.eq(19),
					ArgumentMatchers.any(Object[].class),
					ArgumentMatchers.any())).thenReturn(hResult);

			wmiWbemServices.getMethod(pWbemClassObject, methodName);
		}

		wmiWbemServices.close();
	}

	@Test
	@Order(24)
	void testCheckHResult() throws Exception {
		assertThrows(IllegalArgumentException.class, () -> WmiWbemServices.checkHResult(null, "msg"));

		{
			final HRESULT hResult = Mockito.mock(HRESULT.class);
			Mockito.doReturn(-1).when(hResult).intValue();
			Mockito.verifyNoMoreInteractions(hResult);

			assertThrows(WmiComException.class, () -> WmiWbemServices.checkHResult(hResult, null));
		}
		{
			final HRESULT hResult = Mockito.mock(HRESULT.class);
			Mockito.doReturn(0).when(hResult).intValue();
			Mockito.verifyNoMoreInteractions(hResult);

			WmiWbemServices.checkHResult(hResult, null);
		}
	}

	@Test
	@Order(25)
	void testGetWmiComErrorMessage() {
		assertEquals("code: 0xffffffff.", WmiWbemServices.getWmiComErrorMessage(new HRESULT(-1)));

		assertEquals("WBEM_E_FAILED: Call failed. (0x80041001)", WmiWbemServices.getWmiComErrorMessage(new HRESULT(-2147217407)));
		assertEquals("WBEM_E_NOT_FOUND: Object cannot be found. (0x80041002)", WmiWbemServices.getWmiComErrorMessage(new HRESULT(-2147217406)));
		assertEquals("WBEM_E_ACCESS_DENIED: Current user does not have permission to perform the action. (0x80041003)", WmiWbemServices.getWmiComErrorMessage(new HRESULT(-2147217405)));
	}

	@Test
	@Order(31)
	void testMultithreadSameService() throws Exception {

		final int threadCount = 100;
		final int threadFrequency = 500; // Number of threads per second

		final AtomicInteger failureCount = new AtomicInteger(0);
		final AtomicInteger resultCount = new AtomicInteger(0);

		try (WmiWbemServices wmiWbemServices = WmiWbemServices.getInstance("root/cimv2", null, null)) {

			final Runnable okRunnable = new Runnable() {
				@Override
				public void run() {
					try {
						Thread.sleep((long)(Math.random() * 10));
						final List<Map<String, Object>> result = wmiWbemServices.executeWql("SELECT Name FROM Win32_OperatingSystem", 30000);
						if (result.size() != 1) {
							throw new RuntimeException("Query did not return exactly 1 record");
						}
						resultCount.addAndGet(result.size());
					} catch (final Exception e) {
						throw new RuntimeException(e);
					}
				}
			};

			final UncaughtExceptionHandler exHandler = new UncaughtExceptionHandler() {
				@Override
				public void uncaughtException(final Thread t, final Throwable e) {
					e.printStackTrace();
					failureCount.incrementAndGet();
				}
			};

			final List<Thread> threadList = new ArrayList<Thread>();
			for (int i = 0 ; i < threadCount ; i++) {
				final Thread okThread = new Thread(okRunnable);
				okThread.setUncaughtExceptionHandler(exHandler);
				try { Thread.sleep((long)(0.5 + Math.random() * 1000 / threadFrequency)); } catch (final InterruptedException e) { }
				okThread.start();
				threadList.add(okThread);
			}
			for (final Thread thread : threadList) {
				thread.join();
			}
		}

		assertEquals(0, failureCount.get(), "No failure must have been reported");
		assertEquals(threadCount, resultCount.get(), "Each successful query must have returned 1 record");
	}

	@Test
	@Order(32)
	void testMultithreadSuccess() throws Exception {

		final int threadCount = 1000;
		final int threadFrequency = 500; // Number of threads created, per second

		final AtomicInteger failureCount = new AtomicInteger(0);
		final AtomicInteger resultCount = new AtomicInteger(0);

		final Runnable okRunnable = new Runnable() {
			@Override
			public void run() {
				try (WmiWbemServices wmiWbemServices = WmiWbemServices.getInstance("root/cimv2", null, null)) {
					Thread.sleep((long)(Math.random() * 10));
					final List<Map<String, Object>> result = wmiWbemServices.executeWql("SELECT Name FROM Win32_OperatingSystem", 30000);
					if (result.size() != 1) {
						throw new RuntimeException("Query did not return exactly 1 record");
					}
					resultCount.addAndGet(result.size());
				} catch (final Exception e) {
					throw new RuntimeException(e);
				}
			}
		};

		final Runnable failRunnable = new Runnable() {
			@Override
			public void run() {
				try (WmiWbemServices wmiWbemServices = WmiWbemServices.getInstance("root/cimv2", null, null)) {
					Thread.sleep((long)(Math.random() * 10));
					wmiWbemServices.executeWql("SELECT Name FROM Win32_OperatingSyste", 30000);
				} catch (final Exception e) {
					throw new RuntimeException(e);
				}
			}
		};

		final UncaughtExceptionHandler exHandler = new UncaughtExceptionHandler() {
			@Override
			public void uncaughtException(final Thread t, final Throwable e) {
				if (!e.getMessage().contains("WBEM_E_INVALID_CLASS")) {
					e.printStackTrace();
				}
				failureCount.incrementAndGet();
			}
		};

		final List<Thread> threadList = new ArrayList<Thread>();
		for (int i = 0 ; i < threadCount / 2 ; i++) {
			final Thread okThread = new Thread(okRunnable);
			okThread.setUncaughtExceptionHandler(exHandler);
			try { Thread.sleep((long)(0.5 + Math.random() * 2000 / threadFrequency)); } catch (final InterruptedException e) { }
			okThread.start();
			threadList.add(okThread);

			final Thread failThread = new Thread(failRunnable);
			failThread.setUncaughtExceptionHandler(exHandler);
			try { Thread.sleep((long)(0.5 + Math.random() * 2000 / threadFrequency)); } catch (final InterruptedException e) { }
			failThread.start();
			threadList.add(failThread);
		}
		for (final Thread thread : threadList) {
			thread.join();
		}

		assertEquals(threadCount / 2, failureCount.get(), "Exception must have been thrown for each failed query");
		assertEquals(threadCount / 2, resultCount.get(), "Each successful query must have returned 1 record");
	}
}
