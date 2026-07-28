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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Guard against the wrong-patient hazard of non-unique iSantePlus IDs: verifies that the Patient
 * resource inside a fetched IPS bundle really is the local patient before the summary is stored or
 * shown.
 *
 * <p>
 * A cross-facility bundle carries several Patient resources (the golden record plus every linked
 * source record). The bundle is accepted when ANY of them carries the local patient's SEDISH
 * source-key identifier, or — the golden record may not list our site's source-key — when both the
 * birth date and the family name match. Anything else (including a bundle with no Patient resource,
 * or unparseable JSON) is rejected.
 * </p>
 */
public final class IpsPatientMatcher {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private IpsPatientMatcher() {
	}

	public static boolean matches(String bundleJson, String sourceKeySystem, String expectedSourceKey,
	        String expectedBirthDate, String expectedFamilyName) {
		try {
			// A cross-facility bundle carries the golden record AND every linked source record, in
			// arbitrary order — the local patient's own record may not come first. Accept as soon
			// as ANY of them matches; reject only when none does.
			for (JsonNode entry : MAPPER.readTree(bundleJson).path("entry")) {
				// SEDISH bundles carry entries as bare resources; standard FHIR wraps them in "resource"
				JsonNode resource = entry.has("resourceType") ? entry : entry.path("resource");
				if (!"Patient".equals(resource.path("resourceType").asText())) {
					continue;
				}
				if (hasSourceKey(resource, sourceKeySystem, expectedSourceKey)
				        || matchesDemographics(resource, expectedBirthDate, expectedFamilyName)) {
					return true;
				}
			}
			return false;
		}
		catch (Exception e) {
			return false;
		}
	}

	private static boolean hasSourceKey(JsonNode patient, String system, String expectedValue) {
		if (system == null || expectedValue == null) {
			return false;
		}
		for (JsonNode identifier : patient.path("identifier")) {
			if (system.equals(identifier.path("system").asText())
			        && expectedValue.equals(identifier.path("value").asText())) {
				return true;
			}
		}
		return false;
	}

	private static boolean matchesDemographics(JsonNode patient, String expectedBirthDate, String expectedFamilyName) {
		if (expectedBirthDate == null || expectedFamilyName == null) {
			return false;
		}
		if (!expectedBirthDate.equals(patient.path("birthDate").asText())) {
			return false;
		}
		for (JsonNode name : patient.path("name")) {
			String family = name.path("family").asText();
			if (expectedFamilyName.trim().equalsIgnoreCase(family.trim())) {
				return true;
			}
		}
		return false;
	}
}
