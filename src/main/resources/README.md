# Sample Data

The provided sample data was generated using the excellent [Synthea](https://synthea.mitre.org) tool with some specific modifications that are required by the DPC project.

Interesting files in this directory include:

- [provider_bundle.json](./provider_bundle.json): A `Bundle` of `Practitioner` resources which have been generated by Synthea and modified to allow matching with the sample `Patient` resources.
Details on the data modifications are given [here](#practitioners).
- [patient_bundle.json](./patient_bundle.json) A `Bundle` of `Patient` resources which have been generated by Synthea and modified to match resources in the BlueButton backend (local development only).
Details on the data modifications are given [here](#patients).
  - [patient_bundle-dpr.json](./patient_bundle-dpr.json) Like `patient_bundle.json`, but with IDs matching the BlueButton sandbox environment. This is the file to use when testing against any Data at the Point of Care environment.
- [test_associations.csv](./test_associations.csv): A mapping of `Practitioner` resources to `Patient` resources (in a local environment only), which can be used to generate Patient rosters.
Sample code for generating the corresponding `Group` resources is provided [here](https://github.com/CMSgov/dpc-app/blob/master/dpc-attribution/src/test/java/gov/cms/dpc/attribution/scripts/GenerateRosters.java).
  - [test_associations-dpr.csv](./test_associations-dpr.csv): Like `test_associations.csv`, but with IDs matching the BlueButton sandbox environment. This is the file to use when testing against any Data at the Point of Care environment.
- [organization_bundle.json](./organization_bundle.json): A `Bundle` of 2 `Organization`s and their corresponding `Endpoint` resources.
These can be used for bootstrapping a local development environment in which no Organizations have been registered yet.
- [organization.tmpl.json](./organization.tmpl.json): The FHIR template used by DPC for registering organizations.

# Data Modifications 

## Practitioners

- Removed fullURL
- Manually modified IDs to match [test_associations](./test_associations.csv).

## Patients

- Added MBIs to match to match [test_associations](./test_associations.csv).
- Normalized some of the language codes to ensure that they match the corresponding FHIR value sets/

## Organizations

- Modified bundle to be a batch bundle, rather than a search
- Manually created (and linked) test FHIR endpoints
- Added missing (and required) address type/use fields


# Synthea citation

>Jason Walonoski, Mark Kramer, Joseph Nichols, Andre Quina, Chris Moesel, Dylan Hall, Carlton Duffett, Kudakwashe Dube, Thomas Gallagher, Scott McLachlan, Synthea: An approach, method, and software mechanism for generating synthetic patients and the synthetic electronic health care record, Journal of the American Medical Informatics Association, Volume 25, Issue 3, March 2018, Pages 230–238, https://doi.org/10.1093/jamia/ocx079