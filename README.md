# International Patient Summary Module

## Overview

The **International Patient Summary** (IPS) module provides functionality for fetching, storing, and retrieving FHIR International Patient Summaries within the OpenMRS ecosystem. This module is designed to operate in a headless manner, meaning it does not include any user interface components.

## Features

- **Fetch IPS**: Retrieve International Patient Summaries from a configured FHIR server.
- **Store IPS**: Save the fetched summaries in the OpenMRS database.
- **Retrieve IPS**: Access stored summaries as needed for patient management.

## Configuration

To set up the IPS module, you need to configure the following global properties in your OpenMRS instance:

| Property                      | Description                                                                                     |
|-------------------------------|-------------------------------------------------------------------------------------------------|
| `ips.url`                     | The URL of the FHIR server endpoint that returns the IPS.                                      |
|                               | *Example*: `https://hapi.fhir.org/baseR4/Patient/$summary`                                   |
| `ips.concept`                 | The concept mapping or UUID of the complex concept used to store the IPS.                     |
| `ips.identifierType.uuid`     | The UUID of the identifier type used to query the server for the IPS.                          |

### Example Configuration

```plaintext
ips.url = https://hapi.fhir.org/baseR4/Patient/$summary
ips.concept = <UUID or concept mapping>
ips.identifierType.uuid = <UUID>
```

### Usage
Once configured, the module will automatically handle the interaction with the specified FHIR server. Ensure that the properties are correctly set to facilitate proper communication and data handling.

### UI
The OpenMRS 3.x has a frontend ESM to view the IPS. See [here](https://github.com/I-TECH-UW/openmrs-esm-ips)

### Contributing
Contributions to enhance the IPS module are welcome! Please follow the standard OpenMRS contribution guidelines for submitting issues and pull requests.

