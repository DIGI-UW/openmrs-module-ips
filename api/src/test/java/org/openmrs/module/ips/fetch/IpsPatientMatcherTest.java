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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The bundles used here mirror the real SEDISH SHR response shape: {@code entry[]} items are the
 * resources themselves (no {@code resource} wrapper), the patient carries a
 * {@code http://sedish-haiti.org/fhir/source-key} identifier, and names/birthdates come from the
 * OpenCR golden record.
 */
public class IpsPatientMatcherTest {

	private static final String SOURCE_KEY_SYSTEM = "http://sedish-haiti.org/fhir/source-key";

	/** A bundle shaped like the live SHR one: entries are bare resources. */
	private static String bundle(String... patientJsons) {
		StringBuilder sb = new StringBuilder("{\"resourceType\":\"Bundle\",\"type\":\"document\",\"entry\":["
		        + "{\"resourceType\":\"Composition\",\"title\":\"International Patient Summary\"}");
		for (String p : patientJsons) {
			sb.append(",").append(p);
		}
		return sb.append("]}").toString();
	}

	private static String patient(String family, String birthDate, String sourceKeyValue) {
		StringBuilder sb = new StringBuilder();
		sb.append("{\"resourceType\":\"Patient\",\"id\":\"x\",");
		sb.append("\"name\":[{\"use\":\"official\",\"text\":\"America ").append(family).append("\",");
		sb.append("\"family\":\"").append(family).append("\",\"given\":[\"America\"]}],");
		sb.append("\"birthDate\":\"").append(birthDate).append("\",");
		sb.append("\"identifier\":[");
		sb.append("{\"type\":{\"text\":\"Code National\"},\"system\":\"http://isanteplus.org/openmrs/fhir2/5-code-national\",\"value\":\"CA0201P\"}");
		if (sourceKeyValue != null) {
			sb.append(",{\"type\":{\"text\":\"SEDISH Source Key\"},\"system\":\"").append(SOURCE_KEY_SYSTEM);
			sb.append("\",\"value\":\"").append(sourceKeyValue).append("\"}");
		}
		sb.append("]}");
		return sb.toString();
	}

	// --- source-key match -------------------------------------------------------------------------

	@Test
	public void matches_shouldAcceptWhenTheBundlePatientCarriesTheExpectedSourceKey() {
		String json = bundle(patient("Captain", "2001-02-17", "54111-35"));
		assertTrue(IpsPatientMatcher.matches(json, SOURCE_KEY_SYSTEM, "54111-35", "1990-01-01", "SomeoneElse"));
	}

	@Test
	public void matches_shouldRejectWhenSourceKeyAndDemographicsBothDiffer() {
		// the exact wrong-patient scenario seen on the test SHR: asked for one patient,
		// got another site's patient back
		String json = bundle(patient("Avi", "2026-07-09", "73106-34"));
		assertFalse(IpsPatientMatcher.matches(json, SOURCE_KEY_SYSTEM, "54111-35", "2001-02-17", "Captain"));
	}

	// --- demographic fallback (no source-key on the golden record) --------------------------------

	@Test
	public void matches_shouldAcceptOnBirthDateAndFamilyNameWhenNoSourceKeyIsPresent() {
		String json = bundle(patient("Captain", "2001-02-17", null));
		assertTrue(IpsPatientMatcher.matches(json, SOURCE_KEY_SYSTEM, "54111-35", "2001-02-17", "Captain"));
	}

	@Test
	public void matches_shouldCompareFamilyNamesCaseInsensitively() {
		String json = bundle(patient("CAPTAIN", "2001-02-17", null));
		assertTrue(IpsPatientMatcher.matches(json, SOURCE_KEY_SYSTEM, "54111-35", "2001-02-17", "captain"));
	}

	@Test
	public void matches_shouldRejectWhenOnlyTheBirthDateMatches() {
		String json = bundle(patient("Avi", "2001-02-17", null));
		assertFalse(IpsPatientMatcher.matches(json, SOURCE_KEY_SYSTEM, "54111-35", "2001-02-17", "Captain"));
	}

	@Test
	public void matches_shouldRejectWhenOnlyTheFamilyNameMatches() {
		String json = bundle(patient("Captain", "2026-07-09", null));
		assertFalse(IpsPatientMatcher.matches(json, SOURCE_KEY_SYSTEM, "54111-35", "2001-02-17", "Captain"));
	}

	// --- robustness -------------------------------------------------------------------------------

	@Test
	public void matches_shouldRejectWhenTheBundleHasNoPatientResource() {
		String json = "{\"resourceType\":\"Bundle\",\"entry\":[{\"resourceType\":\"Composition\"}]}";
		assertFalse(IpsPatientMatcher.matches(json, SOURCE_KEY_SYSTEM, "54111-35", "2001-02-17", "Captain"));
	}

	@Test
	public void matches_shouldRejectUnparseableJson() {
		assertFalse(IpsPatientMatcher.matches("not json", SOURCE_KEY_SYSTEM, "54111-35", "2001-02-17", "Captain"));
	}

	@Test
	public void matches_shouldAlsoHandleStandardFhirBundlesWithResourceWrappedEntries() {
		String json = "{\"resourceType\":\"Bundle\",\"entry\":[{\"resource\":"
		        + patient("Captain", "2001-02-17", "54111-35") + "}]}";
		assertTrue(IpsPatientMatcher.matches(json, SOURCE_KEY_SYSTEM, "54111-35", "1990-01-01", "SomeoneElse"));
	}

	@Test
	public void matches_shouldCheckEveryPatientOfACrossFacilityBundleNotJustTheFirst() {
		// A cross-facility bundle carries the golden record AND every linked source record, in
		// arbitrary order. Our own record (carrying our source-key) may not come first — the guard
		// must scan them all before rejecting.
		String otherSiteRecord = patient("Kaptenn", "2001-02-17", "73106-59");
		String ourRecord = patient("Captain", "2001-02-17", "54111-35");
		String json = bundle(otherSiteRecord, ourRecord);
		assertTrue(IpsPatientMatcher.matches(json, SOURCE_KEY_SYSTEM, "54111-35", "1990-01-01", "SomeoneElse"));
	}

	@Test
	public void matches_shouldStillRejectWhenNoPatientOfTheBundleMatches() {
		String json = bundle(patient("Avi", "2026-07-09", "73106-34"), patient("Kaptenn", "1999-05-05", "73106-59"));
		assertFalse(IpsPatientMatcher.matches(json, SOURCE_KEY_SYSTEM, "54111-35", "2001-02-17", "Captain"));
	}
}
