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

/**
 * Builds the SEDISH source-key ({@code <mspp_code>-<patient_id>}) and the mediator fetch URL.
 *
 * <p>
 * iSantePlus IDs are issued per facility and are NOT nationally unique — the same value can belong
 * to different people at different sites, so fetching by iSantePlus ID alone can return another
 * patient's summary. The source-key (also stamped on every record exported to OpenCR by the
 * mpi-client module) is nationally unique and resolves to exactly this patient.
 * </p>
 */
public final class SourceKey {

	private SourceKey() {
	}

	/**
	 * @return {@code <msppCode>-<patientId>} (e.g. {@code 54111-35}), or {@code null} when either
	 *         part is missing — callers then fall back to the legacy iSantePlus-ID lookup.
	 */
	public static String build(String msppCode, Integer patientId) {
		if (msppCode == null || msppCode.trim().isEmpty() || patientId == null) {
			return null;
		}
		return msppCode.trim() + "-" + patientId;
	}

	/**
	 * @return the mediator URL to fetch the IPS from: {@code <base>/Patient/source-key/<key>} when a
	 *         source-key is available, else the legacy {@code <base>/Patient/isanteplus/<id>}.
	 */
	public static String buildFetchUrl(String baseUrl, String sourceKey, String isantePlusId) {
		String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		if (sourceKey != null) {
			return base + "/Patient/source-key/" + sourceKey;
		}
		return base + "/Patient/isanteplus/" + isantePlusId;
	}
}
