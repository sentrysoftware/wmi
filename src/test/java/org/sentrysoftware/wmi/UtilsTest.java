package org.sentrysoftware.wmi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.io.Console;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;


class UtilsTest {

	@TempDir
	static Path tempDir;

	@Test
	void testCheckNonNull() {
		assertThrows(IllegalArgumentException.class, () -> Utils.checkNonNull(null, "name"));
		assertThrows(IllegalArgumentException.class, () -> Utils.checkNonNull(null, null));
		Utils.checkNonNull("value", null);
	}

	@Test
	void testCheckNonBlank() {
		assertThrows(IllegalArgumentException.class, () -> Utils.checkNonBlank(null, "name"));
		assertThrows(IllegalArgumentException.class, () -> Utils.checkNonBlank("",  "name"));
		assertThrows(IllegalArgumentException.class, () -> Utils.checkNonBlank("  ",  "name"));
		Utils.checkNonBlank("value", null);
	}

	@Test
	void testCheckArgumentNotZeroOrNegative() {
		assertThrows(IllegalArgumentException.class, () -> Utils.checkArgumentNotZeroOrNegative(-1, "name"));
		assertThrows(IllegalArgumentException.class, () -> Utils.checkArgumentNotZeroOrNegative(-1L, "name"));
		assertThrows(IllegalArgumentException.class, () -> Utils.checkArgumentNotZeroOrNegative((short) -1, "name"));
		assertThrows(IllegalArgumentException.class, () -> Utils.checkArgumentNotZeroOrNegative((byte) -1, "name"));
		assertThrows(IllegalArgumentException.class, () -> Utils.checkArgumentNotZeroOrNegative(0, "name"));
		assertThrows(IllegalArgumentException.class, () -> Utils.checkArgumentNotZeroOrNegative(0L, "name"));
		assertThrows(IllegalArgumentException.class, () -> Utils.checkArgumentNotZeroOrNegative((short) 0, "name"));
		assertThrows(IllegalArgumentException.class, () -> Utils.checkArgumentNotZeroOrNegative((byte) 0, "name"));

		Utils.checkArgumentNotZeroOrNegative(1, "name");
		Utils.checkArgumentNotZeroOrNegative(1L, "name");
		Utils.checkArgumentNotZeroOrNegative((short) 1, "name");
		Utils.checkArgumentNotZeroOrNegative((byte) 1, "name");
	}

	@Test
	void testCheckNonNullField() {
		assertThrows(IllegalStateException.class, () -> Utils.checkNonNullField(null, "name"));
		assertThrows(IllegalStateException.class, () -> Utils.checkNonNullField(null, null));
		Utils.checkNonNullField("value", null);
	}

	@Test
	void testConvertCimDateTime() {
		assertNull(Utils.convertCimDateTime(null));

		assertThrows(IllegalArgumentException.class, () -> Utils.convertCimDateTime("2021-06-16 15:38:30"));
		assertThrows(IllegalArgumentException.class, () -> Utils.convertCimDateTime("20101208134410.000000"));

		final OffsetDateTime offsetDateTime = OffsetDateTime.of(1975, 3, 24, 19, 30, 00, 0, ZoneOffset.ofHours(1));
		assertEquals(offsetDateTime, Utils.convertCimDateTime("19750324193000.000000+060"));
	}

	@Test
	void testIsEmpty() {
		assertThrows(NullPointerException.class, () -> Utils.isEmpty(null));
		assertTrue(Utils.isEmpty(""));
		assertTrue(Utils.isEmpty("  "));
		assertTrue(Utils.isEmpty("\t \t"));
		assertFalse(Utils.isEmpty("."));
		assertFalse(Utils.isEmpty(" ."));
	}

	@Test
	void testIsBlank() {
		assertTrue(Utils.isBlank(null));
		assertTrue(Utils.isBlank(""));
		assertTrue(Utils.isBlank("  "));
		assertTrue(Utils.isBlank("\t  \t"));
		assertFalse(Utils.isBlank("a"));
		assertFalse(Utils.isBlank("   \t.   \t"));
	}

	@Test
	void testIsNotBlank() {
		assertFalse(Utils.isNotBlank(null));
		assertFalse(Utils.isNotBlank(""));
		assertFalse(Utils.isNotBlank("  "));
		assertFalse(Utils.isNotBlank("\t  \t"));
		assertTrue(Utils.isNotBlank("a"));
		assertTrue(Utils.isNotBlank("   \t.   \t"));
	}

	@Test
	void testReadText() throws Exception {

		assertEquals(Utils.EMPTY, Utils.readText(tempDir, StandardCharsets.UTF_8), "\"Reading\" a directory must return empty");
		assertEquals(Utils.EMPTY, Utils.readText(tempDir.resolve("non-existent"), StandardCharsets.UTF_8), "Non-existing file must return empty");

		final Path tempFile = tempDir.resolve("testReadText");
		Files.write(tempFile, "Line1\r\n2\r3\n4\n".getBytes(StandardCharsets.UTF_8));
		assertTrue(Files.isRegularFile(tempFile) && Files.isReadable(tempFile));
		assertEquals("Line1\n2\n3\n4\n", Utils.readText(tempFile, StandardCharsets.UTF_8));
	}

}
