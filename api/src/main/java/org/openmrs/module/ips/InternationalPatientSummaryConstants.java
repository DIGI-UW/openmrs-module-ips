/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.ips;

/**
 * Global-property keys for the International Patient Summary module.
 */
public final class InternationalPatientSummaryConstants {

	private InternationalPatientSummaryConstants() {
	}

	/**
	 * Base URL of the SHR IPS mediator, e.g.
	 * {@code https://openhimcore.sedishtest.live/SHR/ips}. The patient's iSantePlus ID is appended as
	 * {@code /Patient/isanteplus/<id>} at fetch time.
	 */
	public static final String IPS_URL = "ips.url";

	/** UUID of the complex concept the fetched IPS bundle is stored against. */
	public static final String IPS_CONCEPT = "ips.concept";

	/**
	 * Name (or UUID) of the patient identifier type whose value is sent to the mediator. Defaults to
	 * "iSantePlus ID".
	 */
	public static final String IPS_IDENTIFIER_TYPE = "ips.identifierType";

	/** Basic-auth username for the OpenHIM channel (blank = no auth header). */
	public static final String IPS_USERNAME = "ips.username";

	/** Basic-auth password for the OpenHIM channel (blank = no auth header). */
	public static final String IPS_PASSWORD = "ips.password";

	public static final String DEFAULT_IDENTIFIER_TYPE = "iSantePlus ID";

	/**
	 * GP shared with the mpi-client module: the site's MSPP facility code. When set, the IPS is
	 * fetched by SEDISH source-key ({@code <mspp>-<patient_id>}) instead of the nationally
	 * NON-unique iSantePlus ID, which can otherwise return another patient's summary.
	 */
	public static final String MPI_MSPP_CODE = "mpi-client.source.mspp";

	/** GP shared with the mpi-client module: FHIR system of the SEDISH source-key identifier. */
	public static final String MPI_SOURCE_KEY_SYSTEM = "mpi-client.source.keySystem";

	public static final String DEFAULT_SOURCE_KEY_SYSTEM = "http://sedish-haiti.org/fhir/source-key";
}
