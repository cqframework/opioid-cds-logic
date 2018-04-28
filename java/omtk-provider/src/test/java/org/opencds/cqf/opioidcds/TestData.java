package org.opencds.cqf.opioidcds;

import ca.uhn.fhir.context.FhirContext;
import com.google.gson.*;
import org.hl7.fhir.dstu3.model.*;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

class TestData {

    static Patient getPatient(String id, String gender, String birthDate) {
        return new PatientBuilder().buildId(id).buildGender(gender).buildBirthDate(birthDate).build();
    }

    static Encounter getEncounter(String id, String subject, String period) {
        return new EncounterBuilder()
                .buildId(id)
                .buildStatus("finished")
                .buildSubject(subject)
                .buildPeriod(period, period)
                .build();
    }

    static Encounter getEncounterWithExtension(String id, String subject, String expression) {
        return new EncounterBuilder()
                .buildId(id)
                .buildStatus("finished")
                .buildSubject(subject)
                .buildPeriodExtension(expression)
                .build();
    }

    static MedicationRequest getMedicationRequestDefaultDoseDispense(String id, String code, String display, String subject,
                                                                     String context, String authoredOn)
    {
        String todayPlusThreeMonths = LocalDate.now().plusMonths(6L).toString();
        return new MedicationRequestBuilder()
                .buildId(id)
                .buildStatus("active")
                .buildIntent("order")
                .buildCategory("outpatient")
                .buildMedicationCodeableConcept(code, display)
                .buildSubject(subject)
                .buildContext(context)
                .buildAuthoredOn(authoredOn)
                .buildDosageInstruction(
                        new DosageBuilder().buildTiming(1, 3.0, "d")
                                .buildDose(1.0, "patch")
                                .buildAsNeeded(false)
                                .build()
                )
                .buildDispenseRequest(authoredOn, todayPlusThreeMonths, 3, 30.0, "d")
                .build();
    }

    static MedicationRequest getMedicationRequestWithExtensionsDefaultDispense
            (
                    String id, String code, String display, String subject, String context,
                    String authoredOnExpression
            )
    {
        return new MedicationRequestBuilder()
                .buildId(id)
                .buildStatus("active")
                .buildIntent("order")
                .buildCategory("outpatient")
                .buildMedicationCodeableConcept(code, display)
                .buildSubject(subject)
                .buildContext(context)
                .buildAuthoredOnExtension(authoredOnExpression)
                .buildDosageInstruction(
                        new DosageBuilder().buildTiming(1, 3.0, "d")
                                .buildDose(1.0, "patch")
                                .buildAsNeeded(false)
                                .build()
                )
                .buildDispenseRequestExtension(3, 30.0, "d",
                        authoredOnExpression, "Today() + 3 months")
                .build();
    }

    static MedicationRequest getMedicationRequest(int frequency, double period, double dose, int repeats,
                                                  double supply, String... stringArgs)
    {
        return new MedicationRequestBuilder()
                .buildId(stringArgs[0])
                .buildStatus("active")
                .buildIntent("order")
                .buildCategory("outpatient")
                .buildMedicationCodeableConcept(stringArgs[1], stringArgs[2])
                .buildSubject(stringArgs[3])
                .buildContext(stringArgs[4])
                .buildAuthoredOn(stringArgs[5])
                .buildDosageInstruction(
                        new DosageBuilder().buildTiming(frequency, period, stringArgs[6])
                                .buildDose(dose, stringArgs[7])
                                .buildAsNeeded(false)
                                .build()
                )
                .buildDispenseRequest(stringArgs[8], stringArgs[9], repeats, supply, stringArgs[10])
                .build();
    }

    static MedicationRequest getMedicationRequestWithExtensions(int frequency, double period, double dose, int repeats,
                                                                double supply, String... stringArgs)
    {
        return new MedicationRequestBuilder()
                .buildId(stringArgs[0])
                .buildStatus("active")
                .buildIntent("order")
                .buildCategory("outpatient")
                .buildMedicationCodeableConcept(stringArgs[1], stringArgs[2])
                .buildSubject(stringArgs[3])
                .buildContext(stringArgs[4])
                .buildAuthoredOnExtension(stringArgs[5])
                .buildDosageInstruction(
                        new DosageBuilder().buildTiming(frequency, period, stringArgs[6])
                                .buildDose(dose, stringArgs[7])
                                .buildAsNeeded(false)
                                .build()
                )
                .buildDispenseRequestExtension(repeats, supply, stringArgs[9], stringArgs[5], stringArgs[8])
                .build();
    }

    static Procedure getProcedure(String id, String code, String display, String subject, String context, String period)
    {
        return new ProcedureBuilder()
                .buildId(id)
                .buildStatus("completed")
                .builCode(code, display)
                .buildSubject(subject)
                .buildContext(context)
                .buildPerformedPeriod(period, period)
                .build();
    }

    static Procedure getProcedureWithExtensions(String id, String code, String display, String subject,
                                                String context, String expression)
    {
        return new ProcedureBuilder()
                .buildId(id)
                .buildStatus("completed")
                .builCode(code, display)
                .buildSubject(subject)
                .buildContext(context)
                .buildPerformedPeriodExtension(expression)
                .build();
    }

    static Observation getObservation(String id, String code, String display, String subject, String effective) {
        return new ObservationBuilder()
                .buildId(id)
                .buildStatus("final")
                .buildCode(code, display)
                .buildSubject(subject)
                .buildEffective(effective)
                .buildInterpretation("POS")
                .build();
    }

    static Observation getObservationWithExtension(String id, String code, String display, String subject, String expression) {
        return new ObservationBuilder()
                .buildId(id)
                .buildStatus("final")
                .buildCode(code, display)
                .buildSubject(subject)
                .buildEffectiveExtension(expression)
                .buildInterpretation("POS")
                .build();
    }

    static void writeBundleToFile(String fileName, Bundle testBundle) throws IOException {
        String outputBundleDirectory = "src/test/resources/org/opencds/cqf/opioidcds/output/bundles/";
        File testBundleFile = new File(outputBundleDirectory + fileName);
        testBundleFile.createNewFile();
        FileWriter writer = new FileWriter(testBundleFile);
        writer.write(FhirContext.forDstu3().newXmlParser().setPrettyPrint(true).encodeResourceToString(testBundle));
        writer.close();
        writeBundleResourcesToFile(testBundle);
    }

    private static void writeBundleResourcesToFile(Bundle bundle) throws IOException {
        String outputBundleDirectory = "src/test/resources/org/opencds/cqf/opioidcds/output/resources/";
        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            if (entry.hasResource()) {
                Resource resource = entry.getResource();
                String fileName = resource.fhirType().toLowerCase() + "-" + resource.getIdElement().getIdPart() + ".xml";
                File testResourceFile = new File(outputBundleDirectory + fileName);
                testResourceFile.createNewFile();
                FileWriter writer = new FileWriter(testResourceFile);
                writer.write(FhirContext.forDstu3().newXmlParser().setPrettyPrint(true).encodeResourceToString(resource));
                writer.close();
            }
        }
    }

    static void writeCdsRequestToFile(String fileName, String patientId, String encounterId,
                                      Resource contextPrescription, Map<String, Resource> prefetchResources)
            throws IOException
    {
        String outputRequestDirectory = "src/test/resources/org/opencds/cqf/opioidcds/output/requests/";
        JsonObject request = new JsonObject();
        request.add("hookInstance", new JsonPrimitive(UUID.randomUUID().toString()));
        request.add("fhirServer", new JsonPrimitive("http://measure.eval.kanvix.com/cqf-ruler/baseDstu3"));
        request.add("hook", new JsonPrimitive("medication-prescribe"));
        request.add("user", new JsonPrimitive("Practitioner/example"));
        request.add("applyCql", new JsonPrimitive(true));

        JsonObject context = new JsonObject();
        context.add("patientId", new JsonPrimitive(patientId));
        if (encounterId != null) {
            context.add("encounterId", new JsonPrimitive(encounterId));
        }
        JsonArray medications = new JsonArray();
        medications.add(
                new JsonParser().parse(FhirContext.forDstu3().newJsonParser().setPrettyPrint(true).encodeResourceToString(contextPrescription))
        );
        context.add("medications", medications);
        request.add("context", context);

        if (!prefetchResources.isEmpty()) {
            JsonObject prefetch = new JsonObject();
            for (Map.Entry<String, Resource> entrySet : prefetchResources.entrySet()) {
                JsonObject response = new JsonObject();
                response.add("status", new JsonPrimitive("200 OK"));
                JsonObject prefetchIdElement = new JsonObject();
                prefetchIdElement.add("response", response);
                if (entrySet.getValue() == null) {
                    prefetchIdElement.add("resource", null);
                }
                else {
                    JsonElement resourceJson = new JsonParser().parse(
                            FhirContext.forDstu3().newJsonParser().setPrettyPrint(true).encodeResourceToString(entrySet.getValue())
                    );
                    prefetchIdElement.add("resource", resourceJson);
                }
                prefetch.add(entrySet.getKey(), prefetchIdElement);
            }
            request.add("prefetch", prefetch);
        }

        File cqlRequestFile = new File(outputRequestDirectory + fileName);
        cqlRequestFile.createNewFile();
        FileWriter writer = new FileWriter(cqlRequestFile);
        writer.write(new GsonBuilder().setPrettyPrinting().create().toJson(request));
        writer.close();
    }
}
