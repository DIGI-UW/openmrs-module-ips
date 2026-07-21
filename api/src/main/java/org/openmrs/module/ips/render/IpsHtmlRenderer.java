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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renders a FHIR IPS {@code Bundle} JSON string into a self-contained, bilingual (FR/EN) HTML
 * fragment for the legacy UI.
 *
 * <p>
 * The layout mirrors the OpenMRS 3.x IPS ESM (DIGI-UW/openmrs-esm-ips): per-section structured
 * columns and colored status tags (red = critical/active/severe, green = completed/normal,
 * blue = other), so the legacy render matches how the IPS shows in O3. No HAPI/Groovy — the bundle
 * is walked with Jackson.
 * </p>
 */
public class IpsHtmlRenderer {

	private static final Logger log = LoggerFactory.getLogger(IpsHtmlRenderer.class);

	private static final String DASH = "--";

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
			}
		}

		sb.append(title());
		sb.append(header(byType.get("Patient")));

		sb.append(section("Allergies et intolérances", "Allergies &amp; Intolerances", byType,
		    new String[] { "AllergyIntolerance" },
		    new String[] { "Catégorie / Category", "Criticité / Criticality", "Description" }, Kind.ALLERGY));
		sb.append(section("Problèmes", "Problems", byType,
		    new String[] { "Condition" },
		    new String[] { "Nom / Name", "Statut clinique / Clinical Status", "Sévérité / Severity", "Catégorie / Category" },
		    Kind.CONDITION));
		sb.append(section("Médicaments", "Medications", byType,
		    new String[] { "MedicationStatement", "MedicationRequest" },
		    new String[] { "Médicament / Medication", "Voie / Route", "Posologie / Dosage", "Statut / Status", "Date" },
		    Kind.MEDICATION));
		sb.append(section("Vaccinations", "Immunizations", byType,
		    new String[] { "Immunization" },
		    new String[] { "Date", "Statut / Status", "Vaccin / Vaccine", "Voie / Route" }, Kind.IMMUNIZATION));
		sb.append(section("Résultats et observations", "Results &amp; Observations", byType,
		    new String[] { "Observation" },
		    new String[] { "Nom / Name", "Date", "Valeur / Value", "Catégorie / Category" }, Kind.OBSERVATION));
		sb.append(section("Actes", "Procedures", byType,
		    new String[] { "Procedure" },
		    new String[] { "Acte / Procedure", "Statut / Status", "Date" }, Kind.PROCEDURE));
		sb.append(section("Comptes rendus", "Diagnostic Reports", byType,
		    new String[] { "DiagnosticReport" },
		    new String[] { "Compte rendu / Report", "Statut / Status", "Date" }, Kind.DIAGNOSTIC));
		sb.append(section("Consultations", "Encounters", byType,
		    new String[] { "Encounter" },
		    new String[] { "Type", "Statut / Status", "Date" }, Kind.ENCOUNTER));

		sb.append("</div>");
		return sb.toString();
	}

	private enum Kind {
		ALLERGY, CONDITION, MEDICATION, IMMUNIZATION, OBSERVATION, PROCEDURE, DIAGNOSTIC, ENCOUNTER
	}

	// --- section rendering -----------------------------------------------------------------------

	private String section(String frTitle, String enTitle, Map<String, List<JsonNode>> byType, String[] types,
	        String[] columns, Kind kind) {
		List<JsonNode> resources = new ArrayList<JsonNode>();
		for (String t : types) {
			if (byType.containsKey(t)) {
				resources.addAll(byType.get(t));
			}
		}
		StringBuilder sb = new StringBuilder();
		sb.append("<h3>").append(frTitle).append(" <span class=\"en\">/ ").append(enTitle).append("</span></h3>");
		if (resources.isEmpty()) {
			sb.append("<p class=\"ips-pending\">Aucune donnée disponible <span class=\"en\">/ No data available</span></p>");
			return sb.toString();
		}
		sb.append("<div class=\"table-wrap\"><table class=\"ips-table\"><thead><tr>");
		for (String c : columns) {
			sb.append("<th>").append(c).append("</th>");
		}
		sb.append("</tr></thead><tbody>");
		for (JsonNode r : resources) {
			sb.append("<tr>");
			for (String cell : cellsFor(kind, r)) {   // cells are already HTML-ready
				sb.append("<td>").append(cell).append("</td>");
			}
			sb.append("</tr>");
		}
		sb.append("</tbody></table></div>");
		return sb.toString();
	}

	/** Per-type cells, mirroring the O3 ESM templates (columns + colored tags). Cells are HTML. */
	private String[] cellsFor(Kind kind, JsonNode r) {
		switch (kind) {
			case ALLERGY: {
				String crit = r.path("criticality").asText("");
				String color = crit.equalsIgnoreCase("high") ? "red"
				        : (!crit.isEmpty() && !crit.equalsIgnoreCase("normal")) ? "blue" : "green";
				return new String[] { txt(categoryText(r.path("category"))),
				        tag(crit.isEmpty() ? "Unknown" : crit, color), txt(codeable(r.path("code"))) };
			}
			case CONDITION: {
				String clin = codingCode(r.path("clinicalStatus"));
				String sev = codeable(r.path("severity"));
				String clinColor = clin.toLowerCase().contains("active") ? "red" : "green";
				String sevColor = sev.toLowerCase().contains("severe") ? "red" : "blue";
				return new String[] { txt(codeable(r.path("code"))),
				        tag(clin.isEmpty() ? "Unknown" : clin, clinColor),
				        sev.isEmpty() ? DASH : tag(sev, sevColor), txt(categoryText(r.path("category"))) };
			}
			case MEDICATION: {
				String status = r.path("status").asText("");
				String date = firstNonEmpty(r.path("effectiveDateTime").asText(""),
				    r.path("effectivePeriod").path("start").asText(""), r.path("authoredOn").asText(""));
				return new String[] { txt(codeable(bestNode(r, "medicationCodeableConcept", "medication"))),
				        txt(dosageRoute(r)), txt(dosageText(r)),
				        status.isEmpty() ? DASH : tag(status, status.equalsIgnoreCase("active") || status.equalsIgnoreCase("completed") ? "green" : "blue"),
				        txt(dateShort(date)) };
			}
			case IMMUNIZATION: {
				String status = r.path("status").asText("");
				return new String[] { txt(dateShort(r.path("occurrenceDateTime").asText(""))),
				        status.isEmpty() ? DASH : tag(status, status.equalsIgnoreCase("completed") ? "green" : "blue"),
				        txt(codeable(r.path("vaccineCode"))), txt(codeable(r.path("route"))) };
			}
			case OBSERVATION:
				return new String[] { txt(codeable(r.path("code"))),
				        txt(dateShort(firstNonEmpty(r.path("effectiveDateTime").asText(""),
				            r.path("effectivePeriod").path("start").asText(""), r.path("issued").asText("")))),
				        txt(observationValue(r)), txt(categoryText(r.path("category"))) };
			case PROCEDURE: {
				String status = r.path("status").asText("");
				return new String[] { txt(codeable(r.path("code"))),
				        status.isEmpty() ? DASH : tag(status, status.equalsIgnoreCase("completed") ? "green" : "blue"),
				        txt(dateShort(firstNonEmpty(r.path("performedDateTime").asText(""),
				            r.path("performedPeriod").path("start").asText("")))) };
			}
			case DIAGNOSTIC: {
				String status = r.path("status").asText("");
				return new String[] { txt(codeable(r.path("code"))),
				        status.isEmpty() ? DASH : tag(status, status.equalsIgnoreCase("final") ? "green" : "blue"),
				        txt(dateShort(firstNonEmpty(r.path("effectiveDateTime").asText(""), r.path("issued").asText("")))) };
			}
			case ENCOUNTER: {
				String status = r.path("status").asText("");
				return new String[] { txt(encounterType(r)),
				        status.isEmpty() ? DASH : tag(status, status.equalsIgnoreCase("finished") ? "green" : "blue"),
				        txt(dateShort(r.path("period").path("start").asText(""))) };
			}
			default:
				return new String[] { DASH };
		}
	}

	// --- patient header --------------------------------------------------------------------------

	private String header(List<JsonNode> patients) {
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
			sub.append(esc(birth));
		}
		if (sub.length() > 0) {
			sb.append("<p class=\"ips-sub\">").append(sub).append("</p>");
		}
		JsonNode ids = p.path("identifier");
		if (ids.isArray() && ids.size() > 0) {
			sb.append("<div class=\"ips-ids\">");
			for (JsonNode id : ids) {
				String value = id.path("value").asText("");
				if (value.isEmpty()) {
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
		return sb.toString();
	}

	// --- FHIR field helpers ----------------------------------------------------------------------

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

	/** category may be an array of strings (AllergyIntolerance) or of CodeableConcept (Condition/Observation). */
	private String categoryText(JsonNode cat) {
		if (cat == null || !cat.isArray() || cat.size() == 0) {
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (JsonNode item : cat) {
			String v = item.isTextual() ? item.asText("") : codeable(item);
			if (v == null || v.isEmpty()) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append(", ");
			}
			sb.append(v);
		}
		return sb.toString();
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

	private String dosageRoute(JsonNode med) {
		JsonNode dosage = med.path("dosage");
		if (dosage.isArray() && dosage.size() > 0) {
			return codeable(dosage.get(0).path("route"));
		}
		return "";
	}

	/** Compact dose+frequency summary from dosage[0], best-effort. */
	private String dosageText(JsonNode med) {
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
		String txt = d.path("text").asText("");
		if (sb.length() == 0 && !txt.isEmpty()) {
			return txt;
		}
		return sb.toString().trim();
	}

	private String encounterType(JsonNode enc) {
		JsonNode types = enc.path("type");
		if (types.isArray() && types.size() > 0) {
			String t = codeable(types.get(0));
			if (!t.isEmpty()) {
				return t;
			}
		}
		String cls = enc.path("class").path("display").asText("");
		return !cls.isEmpty() ? cls : enc.path("class").path("code").asText("");
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

	private String dateShort(String d) {
		if (d == null || d.isEmpty()) {
			return "";
		}
		return d.length() >= 10 ? d.substring(0, 10) : d;
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
		return "<style>"
		        + ".ips-card{background:#fff;border-radius:12px;box-shadow:0 3px 3px -4px rgba(0,0,0,.35);"
		        + "border-top:5px solid #566a8b;padding:24px 28px;margin-top:16px;"
		        + "font-family:Inter,system-ui,-apple-system,'Segoe UI',Roboto,'Helvetica Neue',Arial;color:#0f172a;}"
		        + ".ips-card h2{margin-top:0;font-size:20px;color:#0f172a;}"
		        + ".ips-card h3{font-size:16px;color:#586674;margin:22px 0 8px;}"
		        + ".ips-card .en{color:#8a94a6;font-weight:400;font-size:.9em;}"
		        + ".ips-card .ips-patient{font-size:18px;font-weight:700;margin:4px 0 0;}"
		        + ".ips-card .ips-sub{color:#586674;margin:2px 0 0;}"
		        + ".ips-card .ips-ids{margin:10px 0 0;}"
		        + ".ips-card .ips-ident{margin:2px 0;color:#1f3a5f;}"
		        + ".ips-card .ips-pending{color:#9aa4b2;font-style:italic;}"
		        + ".ips-card table.ips-table{width:100%;border-collapse:collapse;margin-top:6px;font-size:14px;}"
		        + ".ips-card table.ips-table th{text-align:left;background:#eef3f9;color:#1f3a5f;padding:9px 10px;"
		        + "border-bottom:2px solid #d3e0ef;font-weight:600;font-size:12.5px;}"
		        + ".ips-card table.ips-table td{padding:9px 10px;border-bottom:1px solid #eef1f4;vertical-align:top;}"
		        + ".ips-card table.ips-table tr:nth-child(even) td{background:#fafbfc;}"
		        + ".ips-card .ips-tag{display:inline-block;padding:2px 10px;border-radius:999px;font-size:12px;font-weight:600;white-space:nowrap;}"
		        + ".ips-card .ips-tag-red{background:#ffd7d9;color:#a2191f;}"
		        + ".ips-card .ips-tag-green{background:#a7f0ba;color:#0e6027;}"
		        + ".ips-card .ips-tag-blue{background:#d0e2ff;color:#0043ce;}"
		        + ".ips-card .table-wrap{overflow-x:auto;}"
		        + "</style>";
	}
}
