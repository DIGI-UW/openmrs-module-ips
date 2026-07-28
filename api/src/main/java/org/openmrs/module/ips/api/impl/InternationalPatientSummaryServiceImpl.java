/**
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */
package org.openmrs.module.ips.api.impl;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.openmrs.Concept;
import org.openmrs.Obs;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.Person;
import org.openmrs.api.AdministrationService;
import org.openmrs.api.ConceptService;
import org.openmrs.api.DatatypeService;
import org.openmrs.api.ObsService;
import org.openmrs.api.PatientService;
import org.openmrs.api.PersonService;
import org.openmrs.api.context.Context;
import org.openmrs.api.db.ClobDatatypeStorage;
import org.openmrs.api.impl.BaseOpenmrsService;
import org.openmrs.module.ips.InternationalPatientSummaryConstants;
import org.openmrs.module.ips.api.InternationalPatientSummaryService;
import org.openmrs.module.ips.fetch.IpsPatientMatcher;
import org.openmrs.module.ips.fetch.SourceKey;
import org.openmrs.module.ips.render.IpsHtmlRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

public class InternationalPatientSummaryServiceImpl extends BaseOpenmrsService implements InternationalPatientSummaryService {

	private static final Logger log = LoggerFactory.getLogger(InternationalPatientSummaryServiceImpl.class);

	private static final int TIMEOUT_MS = 60000;

	@Autowired
	private ObsService obsService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private PersonService personService;

	@Autowired
	private DatatypeService datatypeService;

	@Autowired
	@Qualifier("adminService")
	private AdministrationService administrationService;

	private final IpsHtmlRenderer renderer = new IpsHtmlRenderer();

	@Override
	public String fetchAndStoreIps(Patient patient) throws Exception {

		String sourceKey = buildLocalSourceKey(patient);
		String identifier = sourceKey == null ? getConfiguredIdentifier(patient) : null;

		if (sourceKey == null && identifier == null) {
			log.warn("Patient " + patient.getUuid() + " has no source-key (GP '"
			        + InternationalPatientSummaryConstants.MPI_MSPP_CODE + "') and no '" + getIdentifierTypeSetting()
			        + "' identifier; cannot fetch IPS");
			return null;
		}

		FetchOutcome outcome = doFetchAndStore(patient, sourceKey, identifier);
		return outcome.mismatch ? null : outcome.json;
	}

	/**
	 * Fetches from the mediator — by SEDISH source-key when the site's MSPP code is configured
	 * (iSantePlus IDs are not nationally unique), else by iSantePlus ID — then verifies the returned
	 * bundle really belongs to this patient before storing it. A non-matching bundle is NEVER stored.
	 */
	private FetchOutcome doFetchAndStore(Patient patient, String sourceKey, String identifier) throws Exception {
		String base = administrationService.getGlobalProperty(InternationalPatientSummaryConstants.IPS_URL);
		if (isBlank(base)) {
			throw new IllegalStateException("Global property '" + InternationalPatientSummaryConstants.IPS_URL
			        + "' is not set");
		}
		if (sourceKey == null) {
			log.warn("GP '" + InternationalPatientSummaryConstants.MPI_MSPP_CODE
			        + "' is not set; falling back to the ambiguous iSantePlus-ID lookup for patient "
			        + patient.getUuid());
		}
		String url = SourceKey.buildFetchUrl(base, sourceKey, identifier);

		String json = httpGet(url);
		if (isBlank(json)) {
			log.warn("Mediator returned an empty IPS for patient {}", patient.getUuid());
			return new FetchOutcome(null, false);
		}

		if (!isLocalPatient(patient, json)) {
			log.warn("IPS returned by the mediator does NOT match patient " + patient.getUuid()
			        + " (source-key/demographics check failed); discarding it — not stored, not shown");
			return new FetchOutcome(null, true);
		}

		storeIps(patient, json);
		return new FetchOutcome(json, false);
	}

	private static final class FetchOutcome {

		final String json;

		/** true when the mediator returned a bundle that belongs to ANOTHER patient. */
		final boolean mismatch;

		FetchOutcome(String json, boolean mismatch) {
			this.json = json;
			this.mismatch = mismatch;
		}
	}

	@Override
	public String getStoredIps(Patient patient) {
		try {
			Obs obs = getIpsObs(patient);
			if (obs == null || obs.getValueComplex() == null) {
				return null;
			}
			ClobDatatypeStorage clob = datatypeService.getClobDatatypeStorageByUuid(obs.getValueComplex());
			return clob != null ? clob.getValue() : null;
		}
		catch (Exception ex) {
			// misconfiguration (e.g. ips.concept unset) must not cascade into a page error
			log.warn("Could not read stored IPS for patient " + patient.getUuid() + ": " + ex.getMessage());
			return null;
		}
	}

	@Override
	public String getOrFetchIps(Patient patient) throws Exception {
		String stored = getStoredIps(patient);
		if (!isBlank(stored) && isLocalPatient(patient, stored)) {
			return stored;
		}
		return fetchAndStoreIps(patient);
	}

	@Override
	public Date getIpsDate(Patient patient) {
		try {
			Obs obs = getIpsObs(patient);
			return obs != null ? obs.getObsDatetime() : null;
		}
		catch (Exception ex) {
			return null;
		}
	}

	@Override
	public String renderHtml(String ipsBundleJson) {
		return renderer.render(ipsBundleJson);
	}

	@Override
	public String getIpsHtml(Patient patient) {
		try {
			String stored = getStoredIps(patient);
			if (!isBlank(stored)) {
				if (isLocalPatient(patient, stored)) {
					return renderer.render(stored);
				}
				// A copy stored before the source-key fix may belong to another patient with the
				// same iSantePlus ID: never show it, try a fresh (verified) fetch instead.
				log.warn("Stored IPS for patient " + patient.getUuid()
				        + " does not match the patient; ignoring it and refetching");
			}

			String sourceKey = buildLocalSourceKey(patient);
			String identifier = sourceKey == null ? getConfiguredIdentifier(patient) : null;
			if (sourceKey == null && identifier == null) {
				String type = getIdentifierTypeSetting();
				return renderer.renderNotice(
				    "Ce patient n'a pas d'identifiant « " + type + " » (et le code MSPP du site n'est pas configuré), requis pour récupérer le résumé.",
				    "This patient has no '" + type + "' identifier (and the site's MSPP code is not configured), required to fetch the summary.");
			}

			FetchOutcome outcome = doFetchAndStore(patient, sourceKey, identifier);
			if (outcome.mismatch) {
				return renderer.renderNotice(
				    "Le résumé renvoyé par le SHR ne correspond pas à ce patient (identifiant partagé entre plusieurs sites) — affichage bloqué par sécurité.",
				    "The summary returned by the SHR does not match this patient (identifier shared across sites) — display blocked for safety.");
			}
			if (isBlank(outcome.json)) {
				String lookup = sourceKey != null ? sourceKey : identifier;
				return renderer.renderNotice(
				    "Aucun résumé renvoyé par le SHR pour l'identifiant " + lookup + ".",
				    "No summary returned by the SHR for identifier " + lookup + ".");
			}
			return renderer.render(outcome.json);
		}
		catch (Exception e) {
			log.error("Error building IPS HTML for patient " + patient.getUuid(), e);
			return renderer.renderNotice(
			    "Erreur lors de la récupération du résumé (voir les journaux du serveur).",
			    "Error fetching the summary (see server logs).");
		}
	}

	// --- internals -------------------------------------------------------------------------------

	private void storeIps(Patient patient, String json) {
		Concept concept = getIpsConcept();
		if (concept == null) {
			log.info("Global property '" + InternationalPatientSummaryConstants.IPS_CONCEPT
			        + "' is not set; IPS will be shown but not persisted");
			return;
		}
		Obs obs = getIpsObs(patient);

		String clobUuid;
		ClobDatatypeStorage clob;
		if (obs != null && obs.getValueComplex() != null
		        && datatypeService.getClobDatatypeStorageByUuid(obs.getValueComplex()) != null) {
			clobUuid = obs.getValueComplex();
			clob = datatypeService.getClobDatatypeStorageByUuid(clobUuid);
		} else {
			clobUuid = UUID.randomUUID().toString();
			clob = new ClobDatatypeStorage();
			clob.setUuid(clobUuid);
		}
		clob.setValue(json);
		datatypeService.saveClobDatatypeStorage(clob);

		if (obs == null) {
			obs = new Obs();
			obs.setPerson(patient);
			obs.setConcept(concept);
			obs.setValueComplex(clobUuid);
			obs.setObsDatetime(new Date());
			obsService.saveObs(obs, "Store IPS");
		} else {
			obs.setValueComplex(clobUuid);
			obs.setObsDatetime(new Date());
			obsService.saveObs(obs, "Update IPS");
		}
	}

	private Obs getIpsObs(Patient patient) {
		Concept concept = getIpsConcept();
		if (concept == null) {
			return null;
		}
		Person person = personService.getPersonByUuid(patient.getUuid());
		List<Obs> observations = obsService.getObservationsByPersonAndConcept(person, concept);
		return (observations != null && !observations.isEmpty()) ? observations.get(observations.size() - 1) : null;
	}

	/**
	 * @return the configured storage concept, or {@code null} if {@code ips.concept} is unset/unknown
	 *         (persistence is then skipped and the IPS is fetched fresh each time).
	 */
	private Concept getIpsConcept() {
		String ref = administrationService.getGlobalProperty(InternationalPatientSummaryConstants.IPS_CONCEPT);
		if (isBlank(ref)) {
			return null;
		}
		return conceptService.getConceptByUuid(ref);
	}

	/**
	 * @return this patient's SEDISH source-key ({@code <mspp>-<patient_id>}), or {@code null} when
	 *         the site's MSPP code (GP shared with mpi-client) is not configured.
	 */
	private String buildLocalSourceKey(Patient patient) {
		String mspp = administrationService.getGlobalProperty(InternationalPatientSummaryConstants.MPI_MSPP_CODE);
		return SourceKey.build(mspp, patient.getPatientId());
	}

	private String getSourceKeySystem() {
		String system = administrationService
		        .getGlobalProperty(InternationalPatientSummaryConstants.MPI_SOURCE_KEY_SYSTEM);
		return isBlank(system) ? InternationalPatientSummaryConstants.DEFAULT_SOURCE_KEY_SYSTEM : system;
	}

	/** @return whether the bundle's Patient resource really is this patient (never trust a bare-ID match). */
	private boolean isLocalPatient(Patient patient, String bundleJson) {
		String birthDate = patient.getBirthdate() != null
		        ? new SimpleDateFormat("yyyy-MM-dd").format(patient.getBirthdate())
		        : null;
		return IpsPatientMatcher.matches(bundleJson, getSourceKeySystem(), buildLocalSourceKey(patient), birthDate,
		    patient.getFamilyName());
	}

	private String getIdentifierTypeSetting() {
		String setting = administrationService.getGlobalProperty(InternationalPatientSummaryConstants.IPS_IDENTIFIER_TYPE);
		return isBlank(setting) ? InternationalPatientSummaryConstants.DEFAULT_IDENTIFIER_TYPE : setting;
	}

	private String getConfiguredIdentifier(Patient patient) {
		String setting = getIdentifierTypeSetting();
		PatientService patientService = Context.getPatientService();

		// Resolve the type by uuid or name, then read it off the patient.
		PatientIdentifierType type = patientService.getPatientIdentifierTypeByUuid(setting);
		if (type == null) {
			type = patientService.getPatientIdentifierTypeByName(setting);
		}
		if (type != null) {
			PatientIdentifier pi = patient.getPatientIdentifier(type);
			if (pi != null) {
				return pi.getIdentifier();
			}
		}

		// Fallback: scan the patient's own identifiers and match the type by name (or uuid),
		// case-insensitively. Mirrors the proven xds-sender lookup and is robust to type-lookup /
		// GP quirks — an iSantePlus patient always has an iSantePlus ID, so this must find it.
		for (PatientIdentifier pi : patient.getActiveIdentifiers()) {
			PatientIdentifierType pit = pi.getIdentifierType();
			if (pit == null) {
				continue;
			}
			String name = pit.getName() == null ? "" : pit.getName().trim();
			if (setting.trim().equalsIgnoreCase(name) || setting.equals(pit.getUuid())) {
				return pi.getIdentifier();
			}
		}

		log.warn("No '" + setting + "' identifier resolved for patient " + patient.getUuid()
		        + "; identifiers present: " + describeIdentifiers(patient));
		return null;
	}

	private String describeIdentifiers(Patient patient) {
		StringBuilder sb = new StringBuilder();
		for (PatientIdentifier pi : patient.getActiveIdentifiers()) {
			if (sb.length() > 0) {
				sb.append(", ");
			}
			sb.append(pi.getIdentifierType() != null ? pi.getIdentifierType().getName() : "?");
		}
		return sb.toString();
	}

	private String httpGet(String url) throws Exception {
		HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
		try {
			conn.setRequestMethod("GET");
			conn.setConnectTimeout(TIMEOUT_MS);
			conn.setReadTimeout(TIMEOUT_MS);
			conn.setRequestProperty("Accept", "application/fhir+json, application/json");

			String user = administrationService.getGlobalProperty(InternationalPatientSummaryConstants.IPS_USERNAME);
			String pass = administrationService.getGlobalProperty(InternationalPatientSummaryConstants.IPS_PASSWORD);
			if (!isBlank(user)) {
				String token = user + ":" + (pass == null ? "" : pass);
				String encoded = Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
				conn.setRequestProperty("Authorization", "Basic " + encoded);
			}

			int status = conn.getResponseCode();
			InputStream in = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();
			String body = readAll(in);
			if (status < 200 || status >= 300) {
				log.warn("IPS mediator returned HTTP " + status + " for " + url + ": " + body);
				return null;
			}
			return body;
		}
		finally {
			conn.disconnect();
		}
	}

	private String readAll(InputStream in) throws Exception {
		if (in == null) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
			char[] buf = new char[4096];
			int n;
			while ((n = reader.read(buf)) != -1) {
				sb.append(buf, 0, n);
			}
		}
		return sb.toString();
	}

	private static boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}
}
