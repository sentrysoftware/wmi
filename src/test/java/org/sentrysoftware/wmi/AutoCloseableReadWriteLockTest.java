package org.sentrysoftware.wmi;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AutoCloseableReadWriteLockTest {

	@Test
	void testRead() {
		AutoCloseableReadWriteLock rwLock = new AutoCloseableReadWriteLock();
		assertEquals(0, rwLock.getReadHoldCount());
		assertEquals(0, rwLock.getWriteHoldCount());
		try (AutoCloseableReadWriteLock.AutoCloseableReadLock readLock = rwLock.read()) {
			assertEquals(1, rwLock.getReadHoldCount());
			assertEquals(0, rwLock.getWriteHoldCount());
			assertFalse(rwLock.isWriteLocked());
		}
		assertEquals(0, rwLock.getReadHoldCount());
		assertEquals(0, rwLock.getWriteHoldCount());
	}

	@Test
	void testWrite() {
		AutoCloseableReadWriteLock rwLock = new AutoCloseableReadWriteLock();
		assertEquals(0, rwLock.getReadHoldCount());
		assertEquals(0, rwLock.getWriteHoldCount());
		try (AutoCloseableReadWriteLock.AutoCloseableWriteLock writeLock = rwLock.write()) {
			assertEquals(0, rwLock.getReadHoldCount());
			assertEquals(1, rwLock.getWriteHoldCount());
			assertTrue(rwLock.isWriteLocked());
		}
		assertEquals(0, rwLock.getReadHoldCount());
		assertEquals(0, rwLock.getWriteHoldCount());
		assertFalse(rwLock.isWriteLocked());
	}

}
