package org.sentrysoftware.wmi.wbem;

import org.sentrysoftware.wmi.exceptions.WmiComException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.WINDOWS)
class WinComHelperTest {

	@BeforeEach
	void prepare() {
		// If COM has already been initialized (by another test)
		if (WmiComHelper.isComInitialized()) {
			WmiComHelper.unInitializeCom();
		}
	}

	@AfterAll
	static void tearDown() {
		if (WmiComHelper.isComInitialized()) {
			WmiComHelper.unInitializeCom();
		}
	}

	@Test
	void testInitializeCOMLibrary() throws Exception {

		assertFalse(WmiComHelper.isComInitialized(), "COM must not be initialized, at first");
		WmiComHelper.initializeComLibrary();
		WmiComHelper.initializeComLibrary(); // Second call must not throw any exception
		assertTrue(WmiComHelper.isComInitialized(), "COM must be reported as initialized now");
		WmiComHelper.unInitializeCom();
		assertFalse(WmiComHelper.isComInitialized(), "COM must be reported as uninitialized now");
		WmiComHelper.unInitializeCom(); // Second call must not throw any exception
	}

	@Test
	void testInitializeCOMLibraryMultiThread() throws Exception {

		AtomicInteger failureCount = new AtomicInteger(0);

		UncaughtExceptionHandler exHandler = new UncaughtExceptionHandler() {
			@Override
			public void uncaughtException(Thread t, Throwable e) {
				failureCount.incrementAndGet();
			}
		};

		Runnable testInThread = new Runnable() {
			@Override
			public void run() {
				if (WmiComHelper.isComInitialized()) {
					throw new RuntimeException("COM must not be initialized yet");
				}

				try {
					WmiComHelper.initializeComLibrary();
				} catch (WmiComException e) {
					throw new RuntimeException("COM initialization must work");
				}

				if (!WmiComHelper.isComInitialized()) {
					throw new RuntimeException("COM must be initialized now");
				}

				WmiComHelper.unInitializeCom();
				if (WmiComHelper.isComInitialized()) {
					throw new RuntimeException("COM must be uninitialized now");
				}

			}
		};

		WmiComHelper.initializeComLibrary();
		assertTrue(WmiComHelper.isComInitialized());

		Thread t = new Thread(testInThread);
		t.setUncaughtExceptionHandler(exHandler);
		t.start();
		t.join();

		assertEquals(0, failureCount.get(), "No exception from thread must have been thrown");
		assertTrue(WmiComHelper.isComInitialized(), "COM must still be initialized");

	}

}
