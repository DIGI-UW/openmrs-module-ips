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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renders a FHIR IPS {@code Bundle} JSON string into a self-contained, bilingual (FR/EN) HTML
 * fragment for the legacy UI. No HAPI/Groovy — the bundle is walked with Jackson.
 *
 * <p>
 * The bundle aggregates data from several facilities (cross-facility golden-record summary), so
 * every clinical row shows its facility of origin (the {@code mspp-site} meta tag). Technical
 * noise is filtered out (registration/visit-container encounters, source-key and UUID
 * identifiers, raw category codes, nominal status pills), dates render as DD/MM/YYYY, rows sort
 * newest-first, and date-valued observations, medication reasons and humanized dosages are shown.
 * </p>
 */
public class IpsHtmlRenderer {

	private static final Logger log = LoggerFactory.getLogger(IpsHtmlRenderer.class);

	private static final String DASH = "--";

	private static final String MSPP_SITE_TAG_SYSTEM = "mspp-site";

	private static final Pattern UUID_VALUE = Pattern
	        .compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

	public String render(String bundleJson) {

		StringBuilder sb = new StringBuilder();
		sb.append(styleBlock());
		sb.append("<div class=\"ips-card\">");

		if (bundleJson == null || bundleJson.trim().isEmpty()) {
			sb.append(emptyState()).append("</div>");
			return sb.toString();
		}

		JsonNode bundle;
		try {
			bundle = new ObjectMapper().readTree(bundleJson);
		}
		catch (Throwable e) {
			log.warn("Could not parse IPS bundle", e);
			sb.append(title());
			sb.append("<p class=\"ips-pending\">Impossible de lire le résumé. <span class=\"en\">/ Could not read the summary.</span></p>");
			sb.append("</div>");
			return sb.toString();
		}

		Map<String, List<JsonNode>> byType = new LinkedHashMap<String, List<JsonNode>>();
		Set<String> sites = new LinkedHashSet<String>();
		JsonNode entries = bundle.path("entry");
		if (entries.isArray()) {
			for (JsonNode entry : entries) {
				JsonNode r = entry.has("resource") ? entry.path("resource") : entry;
				String type = r.path("resourceType").asText("");
				if (type.isEmpty()) {
					continue;
				}
				if (!byType.containsKey(type)) {
					byType.put(type, new ArrayList<JsonNode>());
				}
				byType.get(type).add(r);
				String site = siteOf(r);
				if (!site.isEmpty()) {
					sites.add(site);
				}
			}
		}

		sb.append(title());
		sb.append(header(byType.get("Patient"), sites));

		sb.append(section("Allergies et intolérances", "Allergies &amp; Intolerances",
		    new String[] { "Description", "Criticité / Criticality", "Site" },
		    allergyRows(resources(byType, "AllergyIntolerance"))));
		sb.append(section("Problèmes", "Problems",
		    new String[] { "Nom / Name", "Statut clinique / Clinical Status", "Sévérité / Severity", "Site" },
		    conditionRows(resources(byType, "Condition"))));
		sb.append(section("Médicaments", "Medications",
		    new String[] { "Médicament / Medication", "Posologie / Dosage", "Motif / Reason", "Statut / Status", "Date",
		            "Site" },
		    medicationRows(resources(byType, "MedicationStatement", "MedicationRequest"))));
		sb.append(section("Vaccinations", "Immunizations",
		    new String[] { "Vaccin / Vaccine", "Date", "Statut / Status", "Site" },
		    immunizationRows(resources(byType, "Immunization"))));
		sb.append(section("Résultats et observations", "Results &amp; Observations",
		    new String[] { "Nom / Name", "Valeur / Value", "Date", "Site" },
		    observationRows(resources(byType, "Observation"))));
		sb.append(section("Actes", "Procedures",
		    new String[] { "Acte / Procedure", "Statut / Status", "Date", "Site" },
		    procedureRows(resources(byType, "Procedure"))));
		sb.append(section("Comptes rendus", "Diagnostic Reports",
		    new String[] { "Compte rendu / Report", "Statut / Status", "Date", "Site" },
		    diagnosticRows(resources(byType, "DiagnosticReport"))));
		sb.append(section("Consultations", "Encounters",
		    new String[] { "Type", "Statut / Status", "Date", "Site" },
		    encounterRows(resources(byType, "Encounter"))));

		sb.append("</div>");

		return sb.toString();
	}

	// --- rows ------------------------------------------------------------------------------------

	/** One rendered table row: HTML-ready cells plus the raw ISO date used for newest-first sorting. */
	private static final class Row {

		final String[] cells;

		final String sortDate;

		Row(String sortDate, String... cells) {
			this.sortDate = sortDate == null ? "" : sortDate;
			this.cells = cells;
		}
	}

	private List<JsonNode> resources(Map<String, List<JsonNode>> byType, String... types) {
		List<JsonNode> out = new ArrayList<JsonNode>();
		for (String t : types) {
			if (byType.containsKey(t)) {
				out.addAll(byType.get(t));
			}
		}
		return out;
	}

	private List<Row> allergyRows(List<JsonNode> resources) {
		List<Row> rows = new ArrayList<Row>();
		for (JsonNode r : resources) {
			String crit = r.path("criticality").asText("");
			String critCell = crit.isEmpty() ? DASH
			        : tag(crit, crit.equalsIgnoreCase("high") ? "red" : crit.equalsIgnoreCase("low") ? "green" : "blue");
			rows.add(new Row(r.path("recordedDate").asText(""), txt(codeable(r.path("code"))), critCell, siteCell(r)));
		}
		return rows;
	}

	private List<Row> conditionRows(List<JsonNode> resources) {
		List<Row> rows = new ArrayList<Row>();
		for (JsonNode r : resources) {
			String clin = codingCode(r.path("clinicalStatus"));
			String sev = codeable(r.path("severity"));
			String clinCell = clin.isEmpty() ? DASH : tag(clin, clin.toLowerCase().contains("active") ? "red" : "green");
			String sevCell = sev.isEmpty() ? DASH : tag(sev, sev.toLowerCase().contains("severe") ? "red" : "blue");
			rows.add(new Row(firstNonEmpty(r.path("onsetDateTime").asText(""), r.path("recordedDate").asText("")),
			        txt(codeable(r.path("code"))), clinCell, sevCell, siteCell(r)));
		}
		return rows;
	}

	private List<Row> medicationRows(List<JsonNode> resources) {
		List<Row> rows = new ArrayList<Row>();
		for (JsonNode r : resources) {
			String date = firstNonEmpty(r.path("effectiveDateTime").asText(""),
			    r.path("effectivePeriod").path("start").asText(""), r.path("authoredOn").asText(""));
			String status = r.path("status").asText("");
			// medications always show their status — "active" is clinically significant
			String statusCell = status.isEmpty() ? DASH
			        : tag(status, status.equalsIgnoreCase("active") || status.equalsIgnoreCase("completed") ? "green"
			                : "blue");
			rows.add(new Row(date, txt(codeable(bestNode(r, "medicationCodeableConcept", "medication"))),
			        txt(dosageSummary(r)), txt(reason(r)), statusCell, txt(dateFr(date)), siteCell(r)));
		}
		return rows;
	}

	private List<Row> immunizationRows(List<JsonNode> resources) {
		List<Row> rows = new ArrayList<Row>();
		for (JsonNode r : resources) {
			String date = r.path("occurrenceDateTime").asText("");
			rows.add(new Row(date, txt(codeable(r.path("vaccineCode"))), txt(dateFr(date)),
			        statusCell(r.path("status").asText(""), "completed"), siteCell(r)));
		}
		return rows;
	}

	private List<Row> observationRows(List<JsonNode> resources) {
		List<Row> rows = new ArrayList<Row>();
		for (JsonNode r : resources) {
			String date = firstNonEmpty(r.path("effectiveDateTime").asText(""),
			    r.path("effectivePeriod").path("start").asText(""), r.path("issued").asText(""));
			rows.add(new Row(date, txt(codeable(r.path("code"))), txt(observationValue(r)), txt(dateFr(date)),
			        siteCell(r)));
		}
		return rows;
	}

	private List<Row> procedureRows(List<JsonNode> resources) {
		List<Row> rows = new ArrayList<Row>();
		for (JsonNode r : resources) {
			String date = firstNonEmpty(r.path("performedDateTime").asText(""),
			    r.path("performedPeriod").path("start").asText(""));
			rows.add(new Row(date, txt(codeable(r.path("code"))), statusCell(r.path("status").asText(""), "completed"),
			        txt(dateFr(date)), siteCell(r)));
		}
		return rows;
	}

	private List<Row> diagnosticRows(List<JsonNode> resources) {
		List<Row> rows = new ArrayList<Row>();
		for (JsonNode r : resources) {
			String date = firstNonEmpty(r.path("effectiveDateTime").asText(""), r.path("issued").asText(""));
			rows.add(new Row(date, txt(codeable(r.path("code"))), statusCell(r.path("status").asText(""), "final"),
			        txt(dateFr(date)), siteCell(r)));
		}
		return rows;
	}

	private List<Row> encounterRows(List<JsonNode> resources) {
		List<Row> rows = new ArrayList<Row>();
		for (JsonNode r : resources) {
			if (skipEncounter(r)) {
				continue;
			}
			String date = r.path("period").path("start").asText("");
			rows.add(new Row(date, txt(encounterType(r)), statusCell(r.path("status").asText(""), "finished"),
			        txt(dateFr(date)), siteCell(r)));
		}
		return rows;
	}

	/**
	 * Administrative noise: registration encounters, visit-container encounters (tagged
	 * {@code encounter-tag=visit}) and untyped encounters that are not even finished carry no
	 * clinical information — drop them.
	 */
	private boolean skipEncounter(JsonNode r) {
		String type = encounterTypeText(r);
		if (type.equalsIgnoreCase("Enregistrement de patient")) {
			return true;
		}
		for (JsonNode t : r.path("meta").path("tag")) {
			if (t.path("system").asText("").contains("encounter-tag") && t.path("code").asText("").equals("visit")) {
				return true;
			}
		}
		return type.isEmpty() && !r.path("status").asText("").equalsIgnoreCase("finished");
	}

	// --- section rendering -----------------------------------------------------------------------

	private String section(String frTitle, String enTitle, String[] columns, List<Row> rows) {
		// Omit sections with no data entirely rather than rendering a header + "No data available".
		if (rows.isEmpty()) {
			return "";
		}
		// newest first, undated rows last (ISO dates compare lexicographically)
		Collections.sort(rows, new Comparator<Row>() {

			@Override
			public int compare(Row a, Row b) {
				if (a.sortDate.isEmpty() || b.sortDate.isEmpty()) {
					return a.sortDate.isEmpty() ? (b.sortDate.isEmpty() ? 0 : 1) : -1;
				}
				return b.sortDate.compareTo(a.sortDate);
			}
		});
		StringBuilder sb = new StringBuilder();
		sb.append("<h3>").append(frTitle).append(" <span class=\"en\">/ ").append(enTitle).append("</span></h3>");
		sb.append("<div class=\"table-wrap\"><table class=\"ips-table\"><thead><tr>");
		for (String c : columns) {
			sb.append("<th>").append(c).append("</th>");
		}
		sb.append("</tr></thead><tbody>");
		for (Row row : rows) {
			sb.append("<tr>");
			for (String cell : row.cells) {   // cells are already HTML-ready
				sb.append("<td>").append(cell).append("</td>");
			}
			sb.append("</tr>");
		}
		sb.append("</tbody></table></div>");
		return sb.toString();
	}

	// --- patient header --------------------------------------------------------------------------

	private String header(List<JsonNode> patients, Set<String> sites) {
		if (patients == null || patients.isEmpty()) {
			return "";
		}
		JsonNode p = patients.get(0);
		StringBuilder sb = new StringBuilder();
		sb.append("<p class=\"ips-patient\">").append(esc(humanName(p))).append("</p>");
		String gender = p.path("gender").asText("");
		String birth = p.path("birthDate").asText("");
		StringBuilder sub = new StringBuilder();
		if (!gender.isEmpty()) {
			sub.append(esc(gender));
		}
		if (!birth.isEmpty()) {
			if (sub.length() > 0) {
				sub.append(" &middot; ");
			}
			sub.append(esc(dateFr(birth)));
		}
		if (sub.length() > 0) {
			sb.append("<p class=\"ips-sub\">").append(sub).append("</p>");
		}
		JsonNode ids = p.path("identifier");
		if (ids.isArray() && ids.size() > 0) {
			sb.append("<div class=\"ips-ids\">");
			for (JsonNode id : ids) {
				String value = id.path("value").asText("");
				if (value.isEmpty() || isTechnicalIdentifier(id, value)) {
					continue;
				}
				String label = id.path("type").path("text").asText("");
				if (label.isEmpty()) {
					label = id.path("system").asText("");
				}
				sb.append("<div class=\"ips-ident\">").append(esc(label)).append(": <strong>").append(esc(value))
				        .append("</strong></div>");
			}
			sb.append("</div>");
		}
		if (!sites.isEmpty()) {
			StringBuilder list = new StringBuilder();
			for (String s : sites) {
				if (list.length() > 0) {
					list.append(", ");
				}
				list.append(esc(s));
			}
			sb.append("<p class=\"ips-sites\">Sites : ").append(list).append("</p>");
		}
		return sb.toString();
	}

	/** Source-keys and UUID-valued identifiers are plumbing, not clinical identity — hide them. */
	private boolean isTechnicalIdentifier(JsonNode id, String value) {
		if (UUID_VALUE.matcher(value).matches()) {
			return true;
		}
		String typeText = id.path("type").path("text").asText("");
		if (typeText.equalsIgnoreCase("SEDISH Source Key")) {
			return true;
		}
		return id.path("system").asText("").contains("source-key");
	}

	// --- FHIR field helpers ----------------------------------------------------------------------

	/** The facility of origin, from the resource's mspp-site meta tag (name, else MSPP code). */
	private String siteOf(JsonNode r) {
		for (JsonNode t : r.path("meta").path("tag")) {
			if (t.path("system").asText("").contains(MSPP_SITE_TAG_SYSTEM)) {
				String display = t.path("display").asText("");
				return !display.isEmpty() ? display : t.path("code").asText("");
			}
		}
		return "";
	}

	private String siteCell(JsonNode r) {
		String site = siteOf(r);
		return site.isEmpty() ? DASH : "<span class=\"ips-site\">" + esc(site) + "</span>";
	}

	/** A status pill only when it informs: the nominal status renders as an empty cell. */
	private String statusCell(String status, String nominal) {
		if (status.isEmpty() || status.equalsIgnoreCase(nominal)) {
			return "";
		}
		return tag(status, "blue");
	}

	private String humanName(JsonNode patient) {
		JsonNode names = patient.path("name");
		if (names.isArray() && names.size() > 0) {
			JsonNode n = names.get(0);
			String text = n.path("text").asText("");
			if (!text.isEmpty()) {
				return text;
			}
			StringBuilder sb = new StringBuilder();
			for (JsonNode g : n.path("given")) {
				if (sb.length() > 0) {
					sb.append(" ");
				}
				sb.append(g.asText(""));
			}
			String family = n.path("family").asText("");
			if (!family.isEmpty()) {
				if (sb.length() > 0) {
					sb.append(" ");
				}
				sb.append(family);
			}
			return sb.toString();
		}
		return "";
	}

	/** CodeableConcept -> text | first coding display | first coding code. */
	private String codeable(JsonNode cc) {
		if (cc == null || cc.isMissingNode() || cc.isNull()) {
			return "";
		}
		String text = cc.path("text").asText("");
		if (!text.isEmpty()) {
			return text;
		}
		JsonNode coding = cc.path("coding");
		if (coding.isArray() && coding.size() > 0) {
			String d = coding.get(0).path("display").asText("");
			return !d.isEmpty() ? d : coding.get(0).path("code").asText("");
		}
		return "";
	}

	private String codingCode(JsonNode cc) {
		JsonNode coding = cc.path("coding");
		if (coding.isArray() && coding.size() > 0) {
			return coding.get(0).path("code").asText("");
		}
		return "";
	}

	private String observationValue(JsonNode obs) {
		JsonNode q = obs.path("valueQuantity");
		if (!q.isMissingNode() && q.has("value")) {
			String unit = q.path("unit").asText(q.path("code").asText(""));
			return (q.path("value").asText("") + " " + unit).trim();
		}
		if (obs.has("valueCodeableConcept")) {
			return codeable(obs.path("valueCodeableConcept"));
		}
		if (obs.has("valueString")) {
			return obs.path("valueString").asText("");
		}
		if (obs.has("valueBoolean")) {
			return obs.path("valueBoolean").asText("");
		}
		if (obs.has("valueDateTime")) {
			return dateFr(obs.path("valueDateTime").asText(""));
		}
		if (obs.has("valueDate")) {
			return dateFr(obs.path("valueDate").asText(""));
		}
		if (obs.has("valueInteger")) {
			return obs.path("valueInteger").asText("");
		}
		if (obs.path("component").isArray() && obs.path("component").size() > 0) {
			StringBuilder sb = new StringBuilder();
			for (JsonNode c : obs.path("component")) {
				if (sb.length() > 0) {
					sb.append("; ");
				}
				sb.append(codeable(c.path("code"))).append(": ").append(observationValue(c));
			}
			return sb.toString();
		}
		return "";
	}

	/** The reason the medication was given (e.g. the treated condition). */
	private String reason(JsonNode med) {
		JsonNode reasons = med.path("reasonCode");
		if (reasons.isArray() && reasons.size() > 0) {
			StringBuilder sb = new StringBuilder();
			for (JsonNode rc : reasons) {
				String v = codeable(rc);
				if (v.isEmpty()) {
					continue;
				}
				if (sb.length() > 0) {
					sb.append(", ");
				}
				sb.append(v);
			}
			return sb.toString();
		}
		return "";
	}

	/** Dosage (humanized text + route when present) in one cell. */
	private String dosageSummary(JsonNode med) {
		JsonNode dosage = med.path("dosage");
		if (!dosage.isArray() || dosage.size() == 0) {
			return "";
		}
		JsonNode d = dosage.get(0);
		StringBuilder sb = new StringBuilder();
		JsonNode dr = d.path("doseAndRate");
		if (dr.isArray() && dr.size() > 0) {
			JsonNode dq = dr.get(0).path("doseQuantity");
			if (dq.has("value")) {
				sb.append(dq.path("value").asText("")).append(" ").append(dq.path("unit").asText("")).append(" ");
			}
		}
		JsonNode repeat = d.path("timing").path("repeat");
		if (repeat.has("frequency") || repeat.has("period")) {
			sb.append(repeat.path("frequency").asText("")).append("x/").append(repeat.path("period").asText(""))
			        .append(repeat.path("periodUnit").asText(""));
		}
		String out = sb.toString().trim();
		if (out.isEmpty()) {
			out = humanizeDosageText(d.path("text").asText(""));
		}
		String route = codeable(d.path("route"));
		if (!route.isEmpty()) {
			out = out.isEmpty() ? route : out + " · " + route;
		}
		return out;
	}

	/**
	 * The pipeline emits raw dosage strings like {@code 600mg | 60 day(s)}. Translate the English
	 * duration units, space out metric units and join the parts readably; unknown formats pass
	 * through unchanged.
	 */
	private String humanizeDosageText(String raw) {
		if (raw == null || raw.trim().isEmpty()) {
			return "";
		}
		String[] parts = raw.split("\\|");
		StringBuilder sb = new StringBuilder();
		for (String part : parts) {
			String p = part.trim();
			if (p.isEmpty()) {
				continue;
			}
			p = p.replace("day(s)", "jour(s)").replace("week(s)", "semaine(s)").replace("month(s)", "mois")
			        .replace("year(s)", "an(s)");
			p = p.replaceAll("(?i)(\\d)(mg|mcg|ml|g)\\b", "$1 $2");
			if (sb.length() > 0) {
				sb.append(" · ");
			}
			sb.append(p);
		}
		return sb.toString();
	}

	private String encounterTypeText(JsonNode enc) {
		JsonNode types = enc.path("type");
		if (types.isArray() && types.size() > 0) {
			return codeable(types.get(0));
		}
		return "";
	}

	private String encounterType(JsonNode enc) {
		String t = encounterTypeText(enc);
		if (!t.isEmpty()) {
			return t;
		}
		String cls = enc.path("class").path("code").asText("");
		if (cls.equalsIgnoreCase("AMB")) {
			return "Ambulatoire / Ambulatory";
		}
		if (cls.equalsIgnoreCase("IMP")) {
			return "Hospitalisation / Inpatient";
		}
		if (cls.equalsIgnoreCase("EMER")) {
			return "Urgence / Emergency";
		}
		if (cls.equalsIgnoreCase("HH")) {
			return "Domicile / Home";
		}
		String display = enc.path("class").path("display").asText("");
		return !display.isEmpty() ? display : cls;
	}

	private JsonNode bestNode(JsonNode parent, String... fields) {
		for (String f : fields) {
			JsonNode n = parent.path(f);
			if (!n.isMissingNode() && !n.isNull()) {
				return n;
			}
		}
		return parent.path(fields[0]);
	}

	private String firstNonEmpty(String... vals) {
		for (String v : vals) {
			if (v != null && !v.isEmpty()) {
				return v;
			}
		}
		return "";
	}

	/** ISO date(-time) -> DD/MM/YYYY; non-conforming values pass through unchanged. */
	private String dateFr(String d) {
		if (d == null || d.isEmpty()) {
			return "";
		}
		if (d.length() >= 10 && d.charAt(4) == '-' && d.charAt(7) == '-') {
			return d.substring(8, 10) + "/" + d.substring(5, 7) + "/" + d.substring(0, 4);
		}
		return d;
	}

	// --- HTML helpers ----------------------------------------------------------------------------

	/** Plain text cell with the ESM's "--" fallback. */
	private String txt(String s) {
		return (s == null || s.trim().isEmpty()) ? DASH : esc(s);
	}

	/** A colored status pill (red/green/blue), like the ESM's Carbon Tag. */
	private String tag(String text, String color) {
		return "<span class=\"ips-tag ips-tag-" + color + "\">" + esc(text) + "</span>";
	}

	public String renderNotice(String fr, String en) {
		return styleBlock() + "<div class=\"ips-card\">" + title()
		        + "<p class=\"ips-pending\">" + esc(fr) + " <span class=\"en\">/ " + esc(en) + "</span></p></div>";
	}

	private String title() {
		return "<h2>Résumé international du patient <span class=\"en\">/ International Patient Summary</span></h2>";
	}

	private String emptyState() {
		return title()
		        + "<p class=\"ips-pending\">Aucun résumé disponible. <span class=\"en\">/ No summary available.</span></p>";
	}

	private String esc(String s) {
		if (s == null) {
			return "";
		}
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}

	private String styleBlock() {
		return "";
	}
}
