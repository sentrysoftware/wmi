package org.sentrysoftware.wmi;

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

import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Auto-closeable version of {@link ReentrantReadWriteLock}
 * <p>
 * Example:
 * <pre><code>
 * AutoCloseableReadWriteLock rwLock = new AutoCloseableReadWriteLock();
 *
 * try (AutoCloseableReadWriteLock.AutoCloseableReadLock readLock = rwLock.read()) {
 * 	// Do things that do not require exclusive access
 * }
 * // Read lock (non-exclusive) is unlocked automatically here
 *
 * try (AutoCloseableReadWriteLock.AutoCloseableWriteLock writeLock = rwLock.write()) {
 * 	// Do things that require exclusive access
 * }
 * // Lock is unlocked automatically here
 * </code></pre>
 *
 */
public class AutoCloseableReadWriteLock extends ReentrantReadWriteLock {

	private static final long serialVersionUID = 1L;

	/**
	 * Constructs a new AutoCloseableReadWriteLock.
	 */
	public AutoCloseableReadWriteLock() {
		super();
	}
	/**
	 * Locks the "read lock" of the {@link ReentrantReadWriteLock}. Use this to enter
	 * a section of the code that doesn't require exclusive access.
	 * @return An auto-closeable lock, to be used in a try-with-resource block
	 */
	public AutoCloseableReadLock read() {
		return new AutoCloseableReadLock(this.readLock());
	}

	/**
	 * Locks the "write lock" of the {@link ReentrantReadWriteLock}. Use this to enter
	 * a section of the code that requires exclusive access.
	 * @return An auto-closeable lock, to be used in a try-with-resource block
	 */
	public AutoCloseableWriteLock write() {
		return new AutoCloseableWriteLock(this.writeLock());
	}


	/**
	 * Auto-closeable version of {@link ReadLock}
	 */
	public static class AutoCloseableReadLock implements AutoCloseable {

		final ReadLock readLock;

		protected AutoCloseableReadLock(ReadLock lock) {
			readLock = lock;
			readLock.lock();
		}

		@Override
		public void close() {
			readLock.unlock();
		}

	}


	/**
	 * Auto-closeable version of {@link WriteLock}
	 */
	public static class AutoCloseableWriteLock implements AutoCloseable {

		final WriteLock writeLock;

		protected AutoCloseableWriteLock(WriteLock lock) {
			writeLock = lock;
			writeLock.lock();
		}

		@Override
		public void close() {
			writeLock.unlock();
		}

	}

}
