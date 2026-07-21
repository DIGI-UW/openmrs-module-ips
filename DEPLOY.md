# Deploying the `ips` module (and retiring `xds-sender`)

This module replaces the heavyweight **xds-sender** IPS pull/render path on the iSantePlus EMR
(OpenMRS Platform **2.0.5**). It pulls the consolidated IPS bundle from the SHR mediator, optionally
stores it as a complex obs, and renders it as bilingual (FR/EN) HTML for the legacy UI — behind the
existing **Continuité des Soins → Importer** button.

## Artifacts

| artifact | where |
|---|---|
| `ips` omod | build from **`DIGI-UW/openmrs-module-ips` branch `1.x`** (`git checkout 1.x && mvn -DskipTests clean package`, JDK 8); the omod is `omod/target/ips-*.omod` |
| repointed `registrationapp` omod | build from charess-org/iSantePlus branch `feat/ips-module-migration` (needs GitHub-Packages access for the iSantePlus private deps, e.g. `m2sys-biometrics-api`) — same build env used for the other iSantePlus omods |

The `registrationapp` change is only two controllers + poms + `config.xml` (Importer now calls
`InternationalPatientSummaryService` instead of `xdsSender.CcdService`); it cannot be built in a
sandbox without the private artifact repo.

## Global properties

| GP | value |
|---|---|
| `ips.url` | `https://openhimcore.sedishtest.live/SHR/ips` (default; `/Patient/isanteplus/<id>` is appended) |
| `ips.identifierType` | `iSantePlus ID` (default) |
| `ips.username` / `ips.password` | the OpenHIM client creds for the `/SHR/ips` channel — copy from the old `xdssender.oshr.username` / `xdssender.oshr.password`. Leave blank if the channel is public. |
| `ips.concept` | **optional** — UUID of a Complex concept (see below). If unset, the IPS is fetched fresh every time and simply not persisted (the *Importer* button still works; you just won't get the *View / Refresh* state). |

## Optional: create the storage concept (enables persistence)

Run against the EMR MySQL. The generated UUID goes into `ips.concept`.

```sql
SET @uuid := 'e1b7d6c4-0a1f-4c2e-8b3a-9f5e2d1c4b60';   -- paste this into the ips.concept GP
INSERT INTO concept (datatype_id, class_id, is_set, retired, creator, date_created, uuid)
SELECT dt.concept_datatype_id, cc.concept_class_id, 0, 0, 1, NOW(), @uuid
FROM concept_datatype dt, concept_class cc
WHERE dt.name = 'Complex' AND cc.name = 'Misc' LIMIT 1;

SET @cid := (SELECT concept_id FROM concept WHERE uuid = @uuid);
INSERT INTO concept_name (concept_id, name, locale, creator, date_created, concept_name_type, locale_preferred, voided, uuid)
VALUES (@cid, 'International Patient Summary', 'en', 1, NOW(), 'FULLY_SPECIFIED', 1, 0, UUID());
INSERT INTO concept_complex (concept_id, handler) VALUES (@cid, 'BinaryDataHandler');
```

The `handler` is never invoked (the module writes the clob directly via `DatatypeService` and sets
`obs.valueComplex`); any registered handler name is fine.

## Deploy order

1. Copy `ips-1.0.0.omod` into the modules dir (or upload via **Manage Modules**).
2. Copy the rebuilt `registrationapp` omod (it now `require_module`s `ips`, so ips must be present).
3. Remove `xds-sender` (delete the omod / uninstall in **Manage Modules**).
4. Delete `.openmrs-lib-cache`, restart Tomcat.
5. Set the GPs above (and `ips.concept` if you created the concept).

## Verify

- Open a patient's registration summary → **Continuité des Soins** → **Importer**. It should pull the
  IPS and render the bilingual card page (Medications / Allergies / Problems / Immunizations /
  Results / Procedures / Reports / Encounters).
- `GET https://openhimcore.sedishtest.live/SHR/ips/Patient/isanteplus/<iSantePlusID>` should still
  return the IPS bundle (mediator unchanged).

## What was removed

`xds-sender` carried the entire XDS.b CDA/Everest document builder, the WS-Notification pull-point
SOAP stack, the `EncounterEventListener`, and the `PullNotificationsTask` scheduler (the
`notificationsPullPoint` requirement that repeatedly crashed the EMR). The EMR→SHR write path is now
the SQLMesh pipeline → OpenHIM `/consolidated/fhir` → fhir-router, so none of that is needed. The IPS
read/render is all this ~22 KB module now does.
