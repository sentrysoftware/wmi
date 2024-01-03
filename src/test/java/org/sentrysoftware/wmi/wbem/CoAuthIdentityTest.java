package org.sentrysoftware.wmi.wbem;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CoAuthIdentityTest {

	@Test
	void testNoDomain() {
		CoAuthIdentity cai = new CoAuthIdentity("john", "pass".toCharArray());
		assertNull(cai.domain);
		assertEquals(0, cai.domainLength);
		assertEquals("john", cai.user.getWideString(0));
		assertEquals(4, cai.userLength);
		assertEquals("pass", cai.password.getWideString(0));
		assertEquals(4, cai.passwordLength);
	}

	@Test
	void testBackslashDomain() {
		CoAuthIdentity cai = new CoAuthIdentity("sentry\\johnny", "b. goode".toCharArray());
		assertEquals("sentry", cai.domain.getWideString(0));
		assertEquals(6, cai.domainLength);
		assertEquals("johnny", cai.user.getWideString(0));
		assertEquals(6, cai.userLength);
		assertEquals("b. goode", cai.password.getWideString(0));
		assertEquals(8, cai.passwordLength);
	}

	@Test
	void testAtDomain() {
		CoAuthIdentity cai = new CoAuthIdentity("nobody@sentrysoftware.com", null);
		assertEquals("sentrysoftware.com", cai.domain.getWideString(0));
		assertEquals(18, cai.domainLength);
		assertEquals("nobody", cai.user.getWideString(0));
		assertEquals(6, cai.userLength);
		assertNull(cai.password);
		assertEquals(0, cai.passwordLength);
	}

	@Test
	void testUnicode() {
		CoAuthIdentity cai = new CoAuthIdentity("user", "password".toCharArray());
		assertEquals(2, cai.flags);
	}
}
