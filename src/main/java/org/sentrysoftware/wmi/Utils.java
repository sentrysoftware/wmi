/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
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

import org.sentrysoftware.wmi.exceptions.BadMatsyaQueryException;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Utility Class (static), to be used anywhere in  Matsya, including in
 * standalone JARs (in CLI mode)
 *
 * @author Bertrand
 */
public class Utils {

	private Utils() { }

	public static final String EMPTY = "";
	public static final String NEW_LINE = "\n";

	/**
	 * Default separator for array values
	 */
	public static final String DEFAULT_ARRAY_SEPARATOR = "|";

	public static final String DEFAULT_COLUMN_SEPARATOR = ";";

	public static final String STATUS_SUCCESS = "SUCCESS";
	public static final String STATUS_ERROR = "ERROR";

	/**
	 * Build an Matsya error response from the specified message and exception
	 * @param label A description of the problem
	 * @param exception The associated exception (if any)
	 * @return the response to be sent to the PSL
	 */
	public static StringBuilder buildErrorResponse(
			final String label,
			final Throwable exception
	) {

		final StringBuilder response = new StringBuilder(Utils.STATUS_ERROR);

		// Add label if any
		if (label != null) {
			response
					.append(Utils.NEW_LINE)
					.append(label);
		}

		// Add exception if any
		if (exception != null) {
			response
					.append(Utils.NEW_LINE)
					.append(exception.getClass().getSimpleName());

			String message = exception.getMessage();
			if (message != null) {
				response
						.append(": ")
						.append(message);
			}

			// Add cause if any
			final Throwable cause = exception.getCause();
			if (cause != null) {
				response
						.append(Utils.NEW_LINE)
						.append(cause.getClass().getSimpleName());

				String causeMessage = cause.getMessage();
				if (causeMessage != null) {
					response
							.append(": ")
							.append(causeMessage);
				}
			}

		}

		return response;
	}


	/**
	 * Build an Matsya error response from the specified message
	 * @param label A description of the problem
	 * @return the response to be sent to the PSL
	 */
	public static StringBuilder buildErrorResponse(String label) {
		return buildErrorResponse(label, null);
	}


	/**
	 * Build an Matsya error response from the specified exception
	 * @param exception The associated exception (if any)
	 * @return the response to be sent to the PSL
	 */
	public static StringBuilder buildErrorResponse(Exception exception) {
		// Special case for BadMatsyaQueryException, we just show its message
		if (exception instanceof BadMatsyaQueryException) {
			return buildErrorResponse(exception.getMessage());
		}
		return buildErrorResponse(null, exception);
	}


	/**
	 * Build an Matsya error response with no details
	 * @return the response to be sent to the PSL
	 */
	public static StringBuilder buildErrorResponse() {
		return buildErrorResponse(null, null);
	}


	/**
	 * Regex that matches with a CIM_DATETIME string format
	 * <p>
	 * See https://docs.microsoft.com/en-us/windows/win32/wmisdk/cim-datetime
	 * <p>
	 * <ul>
	 * <li>group(1): <code>yyyymmddHHMMSS</code>
	 * <li>group(2): optional fraction of seconds
	 * <li>group(3): timezone offset... in minutes (sigh)
	 */
	private static final Pattern CIM_DATETIME_PATTERN =
			Pattern.compile("^([0-9]{14})(?:\\.([0-9]{3,6}))?([+-][0-9]{3})$");

	/**
	 * Formatter/Parser of the first part of CIM_DATETIME
	 */
	public static final DateTimeFormatter WBEM_CIM_DATETIME_FORMATTER =
			DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	/**
	 * Returns the proper Charset that can decode/encode the specified locale
	 * @param locale The locale we're dealing with, as formatted in the LANG environment variable (e.g. zh_CN.utf8)
	 * @param defaultCharset The default Charset to use if the specified locale doesn't match any supported Charset
	 * @return The appropriate Charset instance
	 */
	public static Charset getCharsetFromLocale(final String locale, final Charset defaultCharset) {

		// What charset will we be dealing with coming from and going to the PATROL Agent
		Charset charset = defaultCharset;
		if (locale != null && !locale.isEmpty()) {
			final String[] localeElements = locale.split("\\.");
			if (localeElements.length > 0) {
				String charsetName = localeElements[localeElements.length - 1];
				if (charsetName != null) {
					charsetName = charsetName.trim();
					if (!charsetName.isEmpty() && !"c".equalsIgnoreCase(charsetName)) {
						if ("gb".equalsIgnoreCase(charsetName)) {
							charsetName = "GBK";
						}
   						try {
							charset = Charset.forName(charsetName);
						} catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
							/* Unfortunately, there is nothing we can do here, as debug is not even set yet */
						}
					}
				}
			}
	 	}

		return charset;
	}

	/**
	 * Returns the proper Charset that can decode/encode the specified locale, or UTF-8 if specified locale
	 * cannot be converted to a Charset.
	 * @param locale The locale we're dealing with, as formatted in the LANG environment variable (e.g. zh_CN.utf8)
	 * @return The appropriate Charset instance
	 */
	public static Charset getCharsetFromLocale(final String locale) {
		return getCharsetFromLocale(locale, StandardCharsets.UTF_8);
	}

	/**
	 * Check if the required argument is not null.
	 *
	 * @param argument
	 * @param name
	 * @throws IllegalArgumentException if the argument is null
	 */
	public static <T> void checkNonNull(final T argument, final String name) {
		if (argument == null) {
			throw new IllegalArgumentException(name + " must not be null.");
		}
	}

	/**
	 * Check if the required argument is not blank (null or empty).
	 *
	 * @param argument
	 * @param name
	 * @throws IllegalArgumentException if the argument is null
	 */
	public static <T> void checkNonBlank(final String argument, final String name) {
		if (isBlank(argument)) {
			throw new IllegalArgumentException(name + " must not be null or empty.");
		}
	}

	/**
	 * Check if the required argument is not negative or zero.
	 *
	 * @param argument
	 * @param name
	 * @throws IllegalArgumentException if the argument is null
	 */
	public static void checkArgumentNotZeroOrNegative(final long argument, final String name) {
		if (argument <= 0) {
			throw new IllegalArgumentException(String.format("%s=%d must not be negative or zero.", name, argument));
		}
	}

	/**
	 * Check if the required field is not null.
	 *
	 * @param field
	 * @param name
	 * @throws IllegalStateException if the argument is null
	 */
	public static <T> void checkNonNullField(final T field, final String name) {
		if (field == null) {
			throw new IllegalStateException(name + " must not be null.");
		}
	}

	/**
	 * Convert a String holding a CIM_DATETIME (i.e. a string in the form
	 * of <code>yyyymmddHHMMSS.mmmmmmsUUU</code>) to an OffsetDateTime object
	 * @param stringValue String value with a CIM_DATETIME
	 * @return OffsetDateTime instance
	 */
	public static OffsetDateTime convertCimDateTime(final String stringValue) {

		if (stringValue == null) {
			return null;
		}

		final Matcher dateTimeMatcher = CIM_DATETIME_PATTERN.matcher(stringValue);
		if (!dateTimeMatcher.find()) {
			throw new IllegalArgumentException("Not a valid CIM_DATETIME value: " + stringValue);
		}

		// LocalDateTime
		final LocalDateTime localDateTime = LocalDateTime.parse(dateTimeMatcher.group(1), WBEM_CIM_DATETIME_FORMATTER);

		// Note: we're not taking the milliseconds and microseconds into account from group(2)

		// Zone Offset
		final String zoneOffset = dateTimeMatcher.group(3);
		if (zoneOffset == null) {
			throw new IllegalStateException("Unable to get the timezone offset from CIM_DATETIME value: " + stringValue);
		}
		final int secondsOffset = Integer.parseInt(zoneOffset) * 60;
		final ZoneOffset offset = ZoneOffset.ofTotalSeconds(secondsOffset);

		return OffsetDateTime.of(localDateTime, offset);
	}

	/**
	 * @return the name of the local computer (or "localhost" if it can't be determined)
	 */
	public static String getComputerName() {
		final String computerName = System.getenv("COMPUTERNAME");
		if (computerName == null) {
			return "localhost";
		}
		return computerName;
	}

	/**
	 * Get the current time in Milliseconds.
	 * @return the current time in Milliseconds.
	 */
	public static long getCurrentTimeMillis() {
		return System.currentTimeMillis();
	}

	/**
	 * @param value The value to check
	 * @return whether the value is null, empty or contains only blank chars
	 */
	public static boolean isBlank(String value) {
		return value == null || isEmpty(value);
	}

	/**
	 * @param value The value to check
	 * @return whether the value is not null, nor empty nor contains only blank chars
	 */
	public static boolean isNotBlank(final String value) {
		return !isBlank(value);
	}

	/**
	 * @param value The value to check
	 * @return whether the value is empty of non-blank chars
	 * @throws NullPointerException if value is <em>null</em>
	 */
	public static boolean isEmpty(String value) {
		return value.trim().isEmpty();
	}

	/**
	 * @param value The value to return
	 * @return the given value or empty string if the value is null or empty
	 */
	public static String getValueOrEmpty(String value) {
		return isBlank(value) ? EMPTY : value;
	}

	/**
	 * Run the given {@link Callable} using the passed timeout in seconds.
	 *
	 * @param <T>
	 * @param callable
	 * @param timeout
	 * @return {@link T} result returned by the callable
	 *
	 * @throws InterruptedException
	 * @throws ExecutionException
	 * @throws TimeoutException
	 */
	public static <T> T execute(Callable<T> callable, long timeout)
			throws InterruptedException, ExecutionException, TimeoutException {

		ExecutorService executorService = Executors.newSingleThreadExecutor();
		Future<T> future = executorService.submit(callable);

		try {
			return future.get(timeout, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw e;
		} catch (TimeoutException e) {
			future.cancel(true);
			throw e;
		} finally {
			executorService.shutdownNow();
		}
	}


	/**
	 * Read the content of the specified file. End-of-lines are normalized as <code>\n</code>.
	 * Non-existent or non-readable files will simply return an empty string.
	 *
	 * @param filePath Path of the file to rest
	 * @param charset The encoding charset
	 * @return The content of the file, as a String, or empty string if file doesn't exist
	 */
	public static String readText(final Path filePath, final Charset charset) {

		checkNonNull(filePath, "filePath");
		checkNonNull(charset, "charset");

		// Non-existent or non-readable doesn't throw an exception but returns an empty String
		if (!Files.isRegularFile(filePath) || !Files.isReadable(filePath)) {
			return EMPTY;
		}

		// Read line-by-line and store in a StringBuilder
		StringBuilder result = new StringBuilder();
		String line;
		try (BufferedReader reader = Files.newBufferedReader(filePath, charset)) {
			while ((line = reader.readLine()) != null) {
				result.append(line).append("\n");
			}
		} catch (IOException e) {
			return EMPTY;
		}

		return result.toString();
	}

	/**
	 * @see #readText(Path, Charset)
	 * @param filePath Path to the file
	 * @param charset Charset of the file
	 * @return The content of the file, as a String, or empty string if file doesn't exist
	 */
	public static String readText(final String filePath, final Charset charset) {
		return readText(Paths.get(filePath), charset);
	}

	/**
	 * @see #readText(Path, Charset)
	 * @param filePath Path to the file
	 * @return The content of the file, as a UTF-8 String, or empty string if file doesn't exist
	 */
	public static String readText(final Path filePath) {
		return readText(filePath, StandardCharsets.UTF_8);
	}

	/**
	 * @see #readText(Path, Charset)
	 * @param filePath Path to the file
	 * @return The content of the file, as a UTF-8 String, or empty string if file doesn't exist
	 */
	public static String readText(final String filePath) {
		return readText(filePath, StandardCharsets.UTF_8);
	}


	/**
	 * Wrapper for Thread.sleep(millis)
	 * @param millis Time to sleep (in milliseconds)
	 * @throws InterruptedException
	 */
	public static void sleep(final long millis) throws InterruptedException {
		Thread.sleep(millis);
	}

	/**
	 * <p>Get the password from:</p>
	 * <ul>
	 * <li>The CLI if the CLI password is not null nor empty</li>
	 * <li>The console if the CLI password is null or empty, the username is not empty and the console is available</li>
	 * <li>The password is null if the CLI password and username are null or empty or and no console available</li>
	 * </ul>
	 * @param username The username from the CLI
	 * @param password The password from the CLI
	 * @return The password used.
	 */
	public static char[] getPassword(final String username, final char[] password) {

		if (password != null && password.length != 0) {
			return password;
		}

		if (username == null || username.isEmpty()) {
			return null;
		}

		final Console console = getConsole();

		return console != null ? console.readPassword("Enter value for --password (Password): ") : null;
	}

	public static Console getConsole () {
		return System.console();
	}

	static BufferedReader createBufferedReader(final InputStreamReader inputStreamReader) {
		return new BufferedReader(inputStreamReader);
	}

	public static InputStream getStdin() {
		return System.in;
	}
}
