/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.ips.render;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The test bundle mirrors the live SEDISH SHR shape: bare-resource entries, each clinical
 * resource tagged with its facility ({@code http://sedish-haiti.org/fhir/mspp-site}), and field
 * patterns copied from real patients (1001PD / 1000PE).
 */
public class IpsHtmlRendererTest {

	private static final String SITE_TAG_HELENE = "{\"system\":\"http://sedish-haiti.org/fhir/mspp-site\",\"code\":\"73106\",\"display\":\"Ste Hélène\"}";

	private static final String SITE_TAG_ANNE = "{\"system\":\"http://sedish-haiti.org/fhir/mspp-site\",\"code\":\"75101\",\"display\":\"Ste Anne\"}";

	private final IpsHtmlRenderer renderer = new IpsHtmlRenderer();

	private static String bundle(String... resources) {
		StringBuilder sb = new StringBuilder("{\"resourceType\":\"Bundle\",\"type\":\"document\",\"entry\":[");
		for (int i = 0; i < resources.length; i++) {
			if (i > 0) {
				sb.append(",");
			}
			sb.append(resources[i]);
		}
		return sb.append("]}").toString();
	}

	private static String patient() {
		return "{\"resourceType\":\"Patient\",\"id\":\"p1\",\"gender\":\"male\",\"birthDate\":\"2001-02-17\","
		        + "\"name\":[{\"text\":\"America Captain\",\"family\":\"Captain\",\"given\":[\"America\"]}],"
		        + "\"identifier\":["
		        + "{\"type\":{\"text\":\"iSantePlus ID\"},\"system\":\"http://isanteplus.org/openmrs/fhir2/3-isanteplus-id\",\"value\":\"1001PD\"},"
		        + "{\"type\":{\"text\":\"SEDISH Source Key\"},\"system\":\"http://sedish-haiti.org/fhir/source-key\",\"value\":\"73106-59\"},"
		        + "{\"type\":{\"text\":\"OpenMRS UUID\"},\"system\":\"urn:x\",\"value\":\"5bd9eabe-dbea-48d2-9b8b-471941f044bd\"}"
		        + "]}";
	}

	private static String medication(String siteTag) {
		return "{\"resourceType\":\"MedicationStatement\",\"status\":\"active\","
		        + "\"meta\":{\"tag\":[" + siteTag + "]},"
		        + "\"medicationCodeableConcept\":{\"text\":\"ABACAVIR\"},"
		        + "\"effectiveDateTime\":\"2026-07-24T16:55:00\","
		        + "\"reasonCode\":[{\"coding\":[{\"display\":\"HUMAN IMMUNODEFICIENCY VIRUS (HIV) DISEASE\"}]}],"
		        + "\"dosage\":[{\"text\":\"600mg | 60 day(s)\"}]}";
	}

	private static String encounter(String type, String status, String date, String siteTag, String extraTag) {
		StringBuilder sb = new StringBuilder("{\"resourceType\":\"Encounter\",\"status\":\"").append(status)
		        .append("\",\"class\":{\"system\":\"http://terminology.hl7.org/CodeSystem/v3-ActCode\",\"code\":\"AMB\"},");
		sb.append("\"meta\":{\"tag\":[").append(siteTag);
		if (extraTag != null) {
			sb.append(",").append(extraTag);
		}
		sb.append("]},");
		if (type != null) {
			sb.append("\"type\":[{\"text\":\"").append(type).append("\"}],");
		}
		sb.append("\"period\":{\"start\":\"").append(date).append("\"}}");
		return sb.toString();
	}

	private static String dateObservation(String siteTag) {
		return "{\"resourceType\":\"Observation\",\"status\":\"final\","
		        + "\"meta\":{\"tag\":[" + siteTag + "]},"
		        + "\"category\":[{\"coding\":[{\"code\":\"exam\"}]}],"
		        + "\"code\":{\"text\":\"Date medication refills due\"},"
		        + "\"effectiveDateTime\":\"2026-07-24T16:55:00\",\"valueDateTime\":\"2026-09-23T00:00:00\"}";
	}

	private static String immunization(String vaccine, String date, String siteTag) {
		return "{\"resourceType\":\"Immunization\",\"status\":\"completed\","
		        + "\"meta\":{\"tag\":[" + siteTag + "]},"
		        + "\"vaccineCode\":{\"text\":\"" + vaccine + "\"},"
		        + "\"occurrenceDateTime\":\"" + date + "\"}";
	}

	// --- site attribution -------------------------------------------------------------------------

	@Test
	public void render_shouldShowTheSiteNameOnEachClinicalRow() {
		String html = renderer.render(bundle(patient(), medication(SITE_TAG_HELENE)));
		assertTrue(html.contains("Site"));
		assertTrue(html.contains("Ste Hélène"));
	}

	@Test
	public void render_shouldListAllSitesInThePatientHeader() {
		String html = renderer.render(bundle(patient(), medication(SITE_TAG_HELENE),
		    encounter("Ord. Médicale", "finished", "2026-07-10T04:52:30", SITE_TAG_ANNE, null)));
		assertTrue(html.contains("Sites"));
		assertTrue(html.indexOf("Ste Anne") > 0);
		assertTrue(html.indexOf("Ste Hélène") > 0);
	}

	// --- noise filtering --------------------------------------------------------------------------

	@Test
	public void render_shouldDropRegistrationEncounters() {
		String html = renderer.render(bundle(patient(),
		    encounter("Enregistrement de patient", "finished", "2026-07-10T04:52:30", SITE_TAG_HELENE, null)));
		assertFalse(html.contains("Enregistrement de patient"));
	}

	@Test
	public void render_shouldDropVisitContainerEncounters() {
		String visitTag = "{\"system\":\"http://x/encounter-tag\",\"code\":\"visit\",\"display\":\"Visit\"}";
		String html = renderer.render(bundle(patient(),
		    encounter(null, "in-progress", "2026-07-24T16:46:54", SITE_TAG_HELENE, visitTag)));
		assertFalse(html.contains("Consultations"));
	}

	@Test
	public void render_shouldDropUntypedUnfinishedEncounters() {
		String html = renderer.render(bundle(patient(),
		    encounter(null, "in-progress", "2026-07-24T16:46:54", SITE_TAG_HELENE, null)));
		assertFalse(html.contains("Consultations"));
	}

	@Test
	public void render_shouldHideTechnicalIdentifiersInTheHeader() {
		String html = renderer.render(bundle(patient()));
		assertTrue(html.contains("1001PD"));
		assertFalse(html.contains("SEDISH Source Key"));
		assertFalse(html.contains("5bd9eabe-dbea-48d2-9b8b-471941f044bd"));
	}

	@Test
	public void render_shouldNotShowCategoryColumns() {
		String html = renderer.render(bundle(patient(), dateObservation(SITE_TAG_HELENE)));
		assertFalse(html.contains("Catégorie"));
		assertFalse(html.contains(">exam<"));
	}

	@Test
	public void render_shouldHideNominalStatusesButKeepInformativeOnes() {
		String html = renderer.render(bundle(patient(),
		    encounter("Ord. Médicale", "finished", "2026-07-24T16:55:00", SITE_TAG_HELENE, null),
		    medication(SITE_TAG_HELENE)));
		// finished encounter: no pill
		assertFalse(html.contains(">finished<"));
		// active medication: pill kept
		assertTrue(html.contains(">active<"));
	}

	// --- completeness / readability ---------------------------------------------------------------

	@Test
	public void render_shouldShowDateValuedObservations() {
		String html = renderer.render(bundle(patient(), dateObservation(SITE_TAG_HELENE)));
		assertTrue(html.contains("23/09/2026"));
	}

	@Test
	public void render_shouldShowTheMedicationReason() {
		String html = renderer.render(bundle(patient(), medication(SITE_TAG_HELENE)));
		assertTrue(html.contains("Motif"));
		assertTrue(html.contains("HUMAN IMMUNODEFICIENCY VIRUS (HIV) DISEASE"));
	}

	@Test
	public void render_shouldFallBackToTheTranslatedClassForUntypedFinishedEncounters() {
		String html = renderer.render(bundle(patient(),
		    encounter(null, "finished", "2026-07-24T16:55:00", SITE_TAG_HELENE, null)));
		assertTrue(html.contains("Ambulatoire"));
	}

	@Test
	public void render_shouldHumanizeTheDosageText() {
		String html = renderer.render(bundle(patient(), medication(SITE_TAG_HELENE)));
		assertTrue(html.contains("600 mg · 60 jour(s)"));
		assertFalse(html.contains("600mg | 60 day(s)"));
	}

	@Test
	public void render_shouldFormatDatesAsDayMonthYear() {
		String html = renderer.render(bundle(patient(), medication(SITE_TAG_HELENE)));
		assertTrue(html.contains("24/07/2026"));
		assertFalse(html.contains("2026-07-24"));
	}

	@Test
	public void render_shouldSortRowsByDateDescending() {
		String html = renderer.render(bundle(patient(),
		    immunization("POLIO VACCINATION, ORAL", "2025-01-01T00:00:00", SITE_TAG_HELENE),
		    immunization("Moderna COVID-19 vaccine", "2026-07-22T16:06:49", SITE_TAG_HELENE)));
		int newer = html.indexOf("Moderna COVID-19 vaccine");
		int older = html.indexOf("POLIO VACCINATION, ORAL");
		assertTrue(newer > 0 && older > 0);
		assertTrue(newer < older);
	}
}
