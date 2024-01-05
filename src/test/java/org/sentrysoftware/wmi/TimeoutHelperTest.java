package org.sentrysoftware.wmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TimeoutHelperTest {

	private static final String MESSAGE = "timeout";

	/**
	 * Fake time counter
	 */
	private static AtomicLong time = new AtomicLong(0);
	private static MockedStatic<Utils> mockedUtils;

	@BeforeAll
	static void setup() {

		// Create a fake time counter
		// Each time Utils.getCurrentTimeMillis() is called, we increment the time counter by 1 ms
		// Each time Utils.sleep(millis) is called, we increment the time counter by the specified number
		// This creates a fake (but stable) time line
		// Other benefit: it runs faster as we don't really wait
		mockedUtils = Mockito.mockStatic(Utils.class);

		mockedUtils.when(Utils::getCurrentTimeMillis).thenAnswer(new Answer<Long>() {
			@Override
			public Long answer(InvocationOnMock invocation) throws Throwable {
				return time.incrementAndGet();
			}
		});

		mockedUtils.when(() -> Utils.sleep(ArgumentMatchers.anyLong())).thenAnswer(new Answer<Long>() {
			@Override
			public Long answer(InvocationOnMock invocation) throws Throwable {
				return time.addAndGet(invocation.getArgument(0));
			}
		});

	}

	@Test
	void testGetRemainingTime() throws Exception {

		assertThrows(TimeoutException.class, () -> TimeoutHelper.getRemainingTime(100, Utils.getCurrentTimeMillis() - 10000, "test 1"));
		assertTrue(TimeoutHelper.getRemainingTime(10000, Utils.getCurrentTimeMillis(), "nothing") > 0);
	}

	@Test
	void testStagedSleep() throws Exception {

		// Staged sleep returns quickly (less than a second) at the beginning of the wait
		{
			long start = Utils.getCurrentTimeMillis();
			TimeoutHelper.stagedSleep(10000, Utils.getCurrentTimeMillis(), MESSAGE);
			long duration = Utils.getCurrentTimeMillis() - start;
			assertTrue(duration < 1000, "Duration must be < 1000 (but was " + duration + ")");
		}

		// Staged sleep returns a bit slower afterward
		{
			long start = Utils.getCurrentTimeMillis();
			TimeoutHelper.stagedSleep(30000, start - 10000, MESSAGE);
			long duration = Utils.getCurrentTimeMillis() - start;
			assertTrue(duration >= 1000, "Duration must be >= 1000 (but was " + duration + ")");
		}

		// Staged sleep throws a TimeoutException when times out
		assertThrows(TimeoutException.class, () -> TimeoutHelper.stagedSleep(5000, Utils.getCurrentTimeMillis() - 10000, MESSAGE));

		// TimeoutException contains the message
		try {
			TimeoutHelper.stagedSleep(5000, Utils.getCurrentTimeMillis() - 10000, MESSAGE);
		} catch (TimeoutException e) {
			assertEquals(MESSAGE, e.getMessage());
		}
	}

	@AfterAll
	static void cleanup() {
		mockedUtils.close();
	}
}
