/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.ips.fetch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class SourceKeyTest {

	// --- build(msppCode, patientId) ---------------------------------------------------------------

	@Test
	public void build_shouldConcatenateMsppCodeAndPatientId() {
		assertEquals("54111-35", SourceKey.build("54111", 35));
	}

	@Test
	public void build_shouldTrimTheMsppCode() {
		assertEquals("54111-35", SourceKey.build(" 54111 ", 35));
	}

	@Test
	public void build_shouldReturnNullWhenMsppCodeIsMissing() {
		assertNull(SourceKey.build(null, 35));
		assertNull(SourceKey.build("", 35));
		assertNull(SourceKey.build("   ", 35));
	}

	@Test
	public void build_shouldReturnNullWhenPatientIdIsMissing() {
		assertNull(SourceKey.build("54111", null));
	}

	// --- buildFetchUrl(base, sourceKey, isantePlusId) ---------------------------------------------

	@Test
	public void buildFetchUrl_shouldUseSourceKeyPathWhenSourceKeyIsPresent() {
		assertEquals("https://shr/SHR/ips/Patient/source-key/54111-35",
		    SourceKey.buildFetchUrl("https://shr/SHR/ips", "54111-35", "1001PD"));
	}

	@Test
	public void buildFetchUrl_shouldFallBackToIsantePlusPathWhenSourceKeyIsAbsent() {
		assertEquals("https://shr/SHR/ips/Patient/isanteplus/1001PD",
		    SourceKey.buildFetchUrl("https://shr/SHR/ips", null, "1001PD"));
	}

	@Test
	public void buildFetchUrl_shouldStripTheTrailingSlashOfTheBaseUrl() {
		assertEquals("https://shr/SHR/ips/Patient/source-key/54111-35",
		    SourceKey.buildFetchUrl("https://shr/SHR/ips/", "54111-35", "1001PD"));
	}
}
