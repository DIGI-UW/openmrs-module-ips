# International Patient Summary Module

> **⚠️ Branch `1.x` — legacy OpenMRS Platform 1.x/2.0.x variant.**
> This is a dependency-light backport for old OpenMRS platforms (e.g. iSantePlus, Platform 2.0.5).
> It is **not** the modern module — for OpenMRS Platform 2.6.0+ use `develop`/`main`.
>
> Differences from `develop`: Platform **2.0.5**; **Jackson only** (no HAPI FHIR / Groovy / XDS.b);
> no REST controller / O3 ESM frontend — instead a **server-side HTML renderer** (`render/IpsHtmlRenderer`)
> for the legacy registrationapp GSP UI. Mechanism (fetch → store as complex obs → retrieve) mirrors the
> modern module. See `DEPLOY.md` for setup.

## Overview

The **International Patient Summary** (IPS) module provides functionality for fetching, storing, and retrieving FHIR International Patient Summaries within the OpenMRS ecosystem. This module is designed to operate in a headless manner, meaning it does not include any user interface components.

## Features

- **Fetch IPS**: Retrieve International Patient Summaries from a configured FHIR server.
- **Store IPS**: Save the fetched summaries in the OpenMRS database.
- **Retrieve IPS**: Access stored summaries as needed for patient management.

## Configuration

To set up the IPS module, configure the following global properties in your OpenMRS instance:

| Property             | Description                                                                                                   |
|----------------------|---------------------------------------------------------------------------------------------------------------|
| `ips.url`            | Base URL of the IPS source that returns the consolidated IPS bundle (e.g. an SHR / OpenHIM IPS mediator). The per-patient path is appended at fetch time (see lookup below). |
| `ips.concept`        | UUID of the **complex** concept used to store the fetched IPS bundle.                                          |
| `ips.identifierType` | Name **or** UUID of the patient identifier type whose value is sent to the IPS source (e.g. `iSantePlus ID`). Only used for the legacy fallback lookup. |
| `ips.username`       | Basic-auth username for the IPS source / OpenHIM channel (blank = no auth header).                             |
| `ips.password`       | Basic-auth password for the IPS source / OpenHIM channel (blank = no auth header).                            |
| `mpi-client.source.mspp` | (shared with the mpi-client module) The site's MSPP facility code. **Set it** — it switches the lookup to the unambiguous source-key path. |
| `mpi-client.source.keySystem` | (shared with the mpi-client module) FHIR system of the SEDISH source-key. Default `http://sedish-haiti.org/fhir/source-key`. |

### Patient lookup: source-key first (wrong-patient guard)

iSantePlus IDs are issued **per facility** and are NOT nationally unique — the same value can belong
to different people at different sites, so fetching by iSantePlus ID alone can return **another
patient's summary**. Therefore:

- When `mpi-client.source.mspp` is set, the module fetches by **SEDISH source-key**
  (`<mspp_code>-<patient_id>`, the same key the mpi-client module stamps on every record exported to
  OpenCR): `GET <ips.url>/Patient/source-key/<mspp>-<patient_id>`. The mediator must expose this
  route (shared-health-record ≥ the source-key patch).
- When it is not set, the legacy `GET <ips.url>/Patient/isanteplus/<iSantePlus ID>` lookup is used.
- In **both** cases a guard verifies the returned bundle's Patient really is the local patient
  (source-key identifier match, else birth date + family name). A non-matching bundle is never
  stored and never shown — the UI gets a bilingual notice instead. A previously stored bundle that
  fails the guard is ignored and refetched.

> Note: this `1.x` (legacy) branch differs from the modern module — it uses `ips.identifierType`
> (name or UUID), not `ips.identifierType.uuid`, and adds `ips.username` / `ips.password` for a
> Basic-auth-protected source. The `ips.url` is an SHR/OpenHIM IPS mediator, not a FHIR `$summary` endpoint.

### Example Configuration

```plaintext
ips.url            = https://<openhim-host>/SHR/ips
ips.concept        = <UUID of a complex concept>
ips.identifierType = iSantePlus ID
ips.username       = <mediator client id>   # optional
ips.password       = <mediator client secret> # optional
```

### Usage
Once configured, the module fetches the consolidated IPS from `ips.url`, stores it against the complex `ips.concept`, and retrieves it on demand. Ensure the properties are set correctly for proper communication and data handling.

### UI
This legacy branch renders the IPS **server-side** (bilingual FR/EN HTML) for the OpenMRS legacy web UI (the registrationapp Continuity-of-Care fragment) — there is no O3 ESM frontend on these platforms. For OpenMRS 3.x, use the modern module (`develop`/`main`) with the frontend ESM: https://github.com/I-TECH-UW/openmrs-esm-ips

### Contributing
Contributions to enhance the IPS module are welcome! Please follow the standard OpenMRS contribution guidelines for submitting issues and pull requests.

