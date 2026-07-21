/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.ips.api;

import java.util.Date;

import org.openmrs.Patient;
import org.openmrs.api.OpenmrsService;

/**
 * Pulls a consolidated FHIR International Patient Summary from the SHR IPS mediator, stores it as a
 * complex obs, and renders it as HTML for the legacy UI.
 *
 * <p>
 * The mechanism (fetch remote {@code $summary}-style bundle &rarr; store as a clob-backed complex obs
 * &rarr; retrieve) mirrors the DIGI-UW {@code openmrs-module-ips}, reworked for OpenMRS Platform 2.0.x
 * and without HAPI / Groovy / XDS.b dependencies.
 * </p>
 */
public interface InternationalPatientSummaryService extends OpenmrsService {

	/**
	 * Fetches the patient's IPS from the mediator, stores it (replacing any previous copy), and returns
	 * the raw FHIR bundle JSON.
	 *
	 * @return the stored IPS bundle JSON, or {@code null} if the patient has no configured identifier or
	 *         the mediator returned nothing.
	 */
	String fetchAndStoreIps(Patient patient) throws Exception;

	/** @return the last-stored IPS bundle JSON for the patient, or {@code null}. */
	String getStoredIps(Patient patient);

	/**
	 * Returns the stored IPS if present, otherwise fetches (and stores, if a concept is configured) a
	 * fresh copy. Lets the view page work whether or not persistence is configured.
	 */
	String getOrFetchIps(Patient patient) throws Exception;

	/** @return when the patient's IPS was last stored, or {@code null} if never. */
	Date getIpsDate(Patient patient);

	/** Renders an IPS bundle JSON string as a self-contained bilingual (FR/EN) HTML fragment. */
	String renderHtml(String ipsBundleJson);

	/**
	 * One-call helper for the UI: returns rendered HTML — the stored/fetched summary, or a bilingual
	 * notice explaining why it couldn't be fetched (no identifier, empty mediator response, or error).
	 * Never throws.
	 */
	String getIpsHtml(Patient patient);
}
