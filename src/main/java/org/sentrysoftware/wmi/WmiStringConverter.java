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

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WmiStringConverter {

	/**
	 * String used to report true
	 */
	protected static final String TRUE_STRING = "True";

	/**
	 * String used to report false
	 */
	protected static final String FALSE_STRING = "False";

	/**
	 * Format for date/times (non-Epoch)
	 */
	private static final DateTimeFormatter CSV_US_DATE_TIME_FORMATTER =
			DateTimeFormatter.ofPattern("EE MMM dd HH:mm:ss yyyy", Locale.US);

	private String arraySeparator;
	private boolean epoch;

	/**
	 * Create a new converter with the specified characteristics.
	 * Use this class to convert values in the returned list of map
	 * in WmiWbemServicesHanlde#executeWQLQuery.
	 * @param arraySeparator What character to use to separate values in arrays
	 * @param useEpoch Whether to convert date/times to seconds to Epoch or not
	 */
	public WmiStringConverter(String arraySeparator, boolean useEpoch) {
		this.arraySeparator = arraySeparator;
		this.epoch = useEpoch;
	}

	/**
	 * Create a new converter with the default characteristics:
	 * <ul>
	 * <li>pipe to separate values in arrays
	 * <li>convert date/time values to seconds to Epoch
	 * </ul>
	 * Use this class to convert values in the returned list of map
	 * in WmiWbemServicesHanlde#executeWQLQuery.
	 */
	public WmiStringConverter() {
		this(Utils.DEFAULT_ARRAY_SEPARATOR, true);
	}

	/**
	 * Convert the specified value/object to a string:
	 * <ul>
	 * <li>OffsetDateTime: to number of seconds since Epoch, or standard English date/time
	 * <li>Array: items separated with specified <em>arraySeparator</em>
	 * <li>null: empty string
	 * <li>Boolean: True or False
	 * <li>Anything else: using <code>String.valueOf()</code>
	 * </ul>
	 * @param value to convert to string
	 * @return a string
	 */
	public String convert(Object value) {

		if (value == null) {
			return Utils.EMPTY;
		}

		if (value.getClass().isArray()) {
			return Stream.of((Object[])value)
					.map(this::convert)
					.map(item -> item.replace(arraySeparator, Utils.EMPTY))
					.collect(Collectors.joining(arraySeparator, Utils.EMPTY, arraySeparator));
		}

		if (value instanceof String) {
			return (String)value;
		} else if (value instanceof Boolean) {
			return (Boolean)value ? TRUE_STRING : FALSE_STRING;
		} else if (value instanceof OffsetDateTime) {
			return epoch ?
					String.valueOf(((OffsetDateTime)value).toEpochSecond()) : ((OffsetDateTime)value).format(CSV_US_DATE_TIME_FORMATTER);
		} else {
			return String.valueOf(value);
		}
	}

}
