package org.opencds.cqf.opioidcds;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import org.hl7.fhir.dstu3.model.CodeSystem;
import org.hl7.fhir.dstu3.model.Enumerations;
import org.hl7.fhir.dstu3.model.ValueSet;
import org.hl7.fhir.instance.model.api.IBaseResource;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

class TerminologyHelper {

    private Map<String, CodeSystem> codeSystemMap;

    TerminologyHelper()
    {
        codeSystemMap = new HashMap<>();
    }

    void loadValuesetsCreateCodeSystems(File terminologyDirectory, IGenericClient client, String baseUrl, String output)
    {
        TransactionBundleBuilder valueSets = new TransactionBundleBuilder();
        if (terminologyDirectory.isDirectory()) {
            try {
                // Read valuesets from file, update client value sets, build code systems, and output bundle
                for (File file : terminologyDirectory.listFiles()) {
                    InputStream is = new FileInputStream(file);
                    Scanner scanner = new Scanner(is).useDelimiter("\\A");
                    String json = scanner.hasNext() ? scanner.next() : "";
                    ValueSet valueset = (ValueSet) FhirContext.forDstu3().newXmlParser().parseResource(json);
                    client.update().resource(valueset).execute();
                    valueSets.buildEntry(baseUrl + "ValueSet/" + valueset.getIdElement().getIdPart(), valueset, null);
                    buildCodeSystem(valueset);
                }
                output(output, "valuesets.xml", valueSets);

                // Update client code systems and output bundle
                TransactionBundleBuilder codeSystems = new TransactionBundleBuilder();
                for (Map.Entry<String, CodeSystem> entrySet : codeSystemMap.entrySet()) {
                    client.update().resource(entrySet.getValue()).execute();
                    codeSystems.buildEntry(baseUrl + "CodeSystem/" + entrySet.getValue().getIdElement().getIdPart(), entrySet.getValue(), null);
                }
                output(output, "codesystems.xml", codeSystems);

            } catch (NullPointerException | IOException e) {
                throw new RuntimeException("Error reading valuesets in terminology directory: " + e.getMessage());
            }
        }
    }

    // check for duplicate codes
    private boolean validateCodeNotInCodeSystem(CodeSystem codeSystem, ValueSet.ConceptReferenceComponent concept)
    {
        if (codeSystem.hasConcept()) {
            for (CodeSystem.ConceptDefinitionComponent codeSystemConcept : codeSystem.getConcept()) {
                if (codeSystemConcept.getCode().equals(concept.getCode())) {
                    return false;
                }
            }
        }

        return true;
    }

    // Construct code system from value set
    private void buildCodeSystem(ValueSet valueSet)
    {
        for (ValueSet.ConceptSetComponent include : valueSet.getCompose().getInclude()) {
            CodeSystem codeSystem;
            if (codeSystemMap.containsKey(include.getSystem())) {
                codeSystem = codeSystemMap.get(include.getSystem());
            }
            else {
                codeSystem = new CodeSystem()
                        .setStatus(Enumerations.PublicationStatus.ACTIVE)
                        .setUrl(include.getSystem())
                        .setContent(CodeSystem.CodeSystemContentMode.FRAGMENT);
                codeSystem.setId(getIdFromSystem(include.getSystem()));
                codeSystemMap.put(include.getSystem(), codeSystem);
            }

            for (ValueSet.ConceptReferenceComponent concept : include.getConcept()) {
                if (validateCodeNotInCodeSystem(codeSystem, concept)) {
                    codeSystem.addConcept().setCode(concept.getCode()).setDisplay(concept.getDisplay());
                }
            }
        }
    }

    // Write builder resource to file
    private void output(String output, String fileName, BaseBuilder builder) throws IOException {
        File outputFile = new File(output + fileName);
        outputFile.createNewFile();
        FileWriter writer = new FileWriter(outputFile);
        writer.write(FhirContext.forDstu3().newXmlParser().setPrettyPrint(true).encodeResourceToString((IBaseResource) builder.build()));
        writer.close();
    }

    // mapping from url to OID
    private String getIdFromSystem(String system) {
        switch (system) {
            case "http://snomed.info/sct": return "2.16.840.1.113883.6.96";
            case "http://www.nlm.nih.gov/research/umls/rxnorm": return "2.16.840.1.113883.6.88";
            case "http://loinc.org": return "2.16.840.1.113883.6.1";
            case "http://hl7.org/fhir/sid/icd-10-cm": return "2.16.840.1.113883.6.90";
            case "http://hl7.org/fhir/sid/icd-9-cm": return "2.16.840.1.113883.6.42";
            case "http://hl7.org/fhir/ig/opioid-cds/CodeSystem/opioidcds-indicator": return "opioidcds-indicator";
            default: throw new RuntimeException("Unknown system: " + system);
        }
    }
}
