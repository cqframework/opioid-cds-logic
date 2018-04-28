package org.opencds.cqf.opioidcds;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import org.cqframework.cql.elm.execution.Library;
import org.hl7.fhir.dstu3.model.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opencds.cqf.cql.data.fhir.BaseFhirDataProvider;
import org.opencds.cqf.cql.execution.Context;
import org.opencds.cqf.cql.execution.CqlLibraryReader;
import org.opencds.cqf.cql.terminology.TerminologyProvider;
import org.opencds.cqf.cql.terminology.fhir.FhirTerminologyProvider;

import javax.xml.bind.JAXBException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;

public class TestRecommendations {

    // private members and methods
    private IGenericClient client;
    private final String baseUrl = "http://measure.eval.kanvix.com/cqf-ruler/baseDstu3/";

    private void loadTerminologyCreateBundleAndCodeSystems() {
        String terminologyPath = "src/test/resources/org/opencds/cqf/opioidcds/terminology";
        String outputTerminologyPath = "src/test/resources/org/opencds/cqf/opioidcds/output/terminology/";
        File terminologyDirectory = new File(Paths.get(terminologyPath).toAbsolutePath().toString());

        TerminologyHelper helper = new TerminologyHelper();
        helper.loadValuesetsCreateCodeSystems(terminologyDirectory, client, baseUrl, outputTerminologyPath);
    }

    private Context getStu3Context(String sourceFileName) throws IOException, JAXBException {
        java.io.InputStream input = TestOmtkDataProvider.class.getResourceAsStream(sourceFileName);
        Library library = CqlLibraryReader.read(input);
        Context context = new Context(library);
        context.registerLibraryLoader(new TestLibraryLoader());
        BaseFhirDataProvider fhirDataProvider = new TestFhirProvider().setEndpoint(baseUrl);
        TerminologyProvider terminologyProvider = new FhirTerminologyProvider().setEndpoint(baseUrl, true);
        fhirDataProvider.setTerminologyProvider(terminologyProvider);
//        fhirDataProvider.setExpandValueSets(true);
        context.registerDataProvider("http://hl7.org/fhir", fhirDataProvider);
        context.registerTerminologyProvider(terminologyProvider);
        this.client = fhirDataProvider.getFhirClient();
        return context;
    }

    // Resolve client and terminology
    @Before
    public void setup() {
        BaseFhirDataProvider fhirDataProvider = new TestFhirProvider().setEndpoint(baseUrl);
        TerminologyProvider terminologyProvider = new FhirTerminologyProvider().setEndpoint(baseUrl, true);
        fhirDataProvider.setTerminologyProvider(terminologyProvider);
        this.client = fhirDataProvider.getFhirClient();
        // loadTerminologyCreateBundleAndCodeSystems();
    }

    // Unit Tests

    @Test
    public void TestRecommendation04_LongActingOpioid() throws IOException, JAXBException {
        String id = "example-rec-04-long-acting-opioid";
        String contextId = id + "-context";
        String prefetchId = id + "-prefetch";
        String patientRef = "Patient/" + id;
        String contextEncounterRef = "Encounter/" + id + "-context";
        String prefetchEncounterRef = "Encounter/" + id + "-prefetch";

        Context context = getStu3Context("OpioidCDS_STU3_REC_04.xml");
        context.enterContext("Patient");
        context.setContextValue(context.getCurrentContext(), id);

        Patient patient = TestData.getPatient(id, "male", "1982-01-07");
        client.update().resource(patient).execute();

        Encounter contextEncounter = TestData.getEncounterWithExtension(
                contextId, patientRef, null
        );
        client.update().resource(contextEncounter).execute();
        Encounter dynamicContextEncounter = TestData.getEncounterWithExtension(
                contextId, patientRef, "Today()"
        );

        String todayMinus4Months = LocalDate.now().minusMonths(4L).toString();
        Encounter prefetchEncounter = TestData.getEncounter(
                prefetchId, patientRef, todayMinus4Months
        );
        client.update().resource(prefetchEncounter).execute();
        Encounter dynamicPrefetchEncounter = TestData.getEncounterWithExtension(
                prefetchId, patientRef, "Today() - 4 months"
        );

        MedicationRequest contextPrescription = TestData.getMedicationRequestDefaultDoseDispense(
                contextId, "1049502", "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet",
                patientRef, contextEncounterRef, null
        );
        context.setParameter(null, "ContextPrescriptions", Collections.singletonList(contextPrescription));
        MedicationRequest dynamicContextPrescription = TestData.getMedicationRequestWithExtensionsDefaultDispense(
                contextId, "1049502", "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet",
                patientRef, contextEncounterRef, "Today()"
        );

        MedicationRequest prefetchPrescription = TestData.getMedicationRequestDefaultDoseDispense(
                prefetchId, "1049502", "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet",
                patientRef, prefetchEncounterRef, todayMinus4Months
        );
        client.update().resource(prefetchPrescription).execute();
        MedicationRequest dynamicPrefetchPrescription = TestData.getMedicationRequestWithExtensionsDefaultDispense(
                prefetchId, "1049502", "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet",
                patientRef, prefetchEncounterRef, "Today() - 4 months"
        );

        Object result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertTrue((Boolean) result);

        result = context.resolveExpressionRef("Get Summary").getExpression().evaluate(context);
        Assert.assertTrue(result.equals("Recommend use of immediate-release opioids instead of extended release/long acting opioids when starting patient on opioids."));

        result = context.resolveExpressionRef("Get Detail").getExpression().evaluate(context);
        Assert.assertTrue(result.equals("The following medication requests(s) release rates should be re-evaluated: 12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet"));

        Bundle testBundle =
                new TransactionBundleBuilder()
                        .buildId(id)
                        .buildEntry(baseUrl + patientRef, patient, null)
//                        .buildEntry(baseUrl + "MedicationRequest/" + prefetchId, dynamicPrefetchPrescription, null)
//                        .buildEntry(baseUrl + "Encounter/" + prefetchId, dynamicPrefetchEncounter, null)
                        .buildEntry(baseUrl + "Encounter/" + contextId, dynamicContextEncounter, null)
                        .build();
        TestData.writeBundleToFile("bundle-" + id + ".xml", testBundle);

        Map<String, Resource> prefetch = new LinkedHashMap<>();
        prefetch.put("item1", dynamicPrefetchPrescription);
        prefetch.put("item2", dynamicPrefetchEncounter);
        prefetch.put("item3", patient);
        // these items are needed to satisfy dynamic prefetch validation
        prefetch.put("item4", null); prefetch.put("item5", null); prefetch.put("item6", null); prefetch.put("item7", null);
        prefetch.put("item8", null); prefetch.put("item9", null); prefetch.put("item10", null);
        prefetch.put("item11", null); prefetch.put("item12", null); prefetch.put("item13", null);
        TestData.writeCdsRequestToFile(
                "request-" + id + ".json",
                patientRef, contextEncounterRef,
                dynamicContextPrescription, prefetch
        );
    }

    @Test
    public void TestRecommendation04_NewPatient() throws IOException, JAXBException {
        String id = "example-rec-04-new-patient";
        String contextId = id + "-context";
        String patientRef = "Patient/" + id;
        String contextEncounterRef = "Encounter/" + id + "-context";

        Context context = getStu3Context("OpioidCDS_STU3_REC_04.xml");
        context.enterContext("Patient");
        context.setContextValue(context.getCurrentContext(), id);

        Patient patient = TestData.getPatient(id, "male", "1982-01-07");
        client.update().resource(patient).execute();

        Encounter contextEncounter = TestData.getEncounter(contextId, patientRef, null);
        client.update().resource(contextEncounter).execute();
        Encounter dynamicContextEncounter = TestData.getEncounterWithExtension(contextId, patientRef, "Today()");

        MedicationRequest contextPrescription = TestData.getMedicationRequestDefaultDoseDispense(
                contextId, "1049502", "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet",
                patientRef, contextEncounterRef, null
        );
        context.setParameter(null, "ContextPrescriptions", Collections.singletonList(contextPrescription));
        MedicationRequest dynamicContextPrescription = TestData.getMedicationRequestWithExtensionsDefaultDispense(
                contextId, "1049502", "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet",
                patientRef, contextEncounterRef, "Today()"
        );

        Object result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertFalse((Boolean) result);

        Bundle testBundle =
                new TransactionBundleBuilder()
                        .buildId(id)
                        .buildEntry(baseUrl + patientRef, patient, null)
                        .buildEntry(baseUrl + contextEncounterRef, dynamicContextEncounter, null)
                        .build();
        TestData.writeBundleToFile("bundle-" + id + ".xml", testBundle);

        Map<String, Resource> prefetch = new LinkedHashMap<>();
        prefetch.put("item1", null);
        prefetch.put("item2", null);
        prefetch.put("item3", patient);
        // these items are needed to satisfy dynamic prefetch validation
        prefetch.put("item4", null); prefetch.put("item5", null); prefetch.put("item6", null); prefetch.put("item7", null);
        prefetch.put("item8", null); prefetch.put("item9", null); prefetch.put("item10", null);
        prefetch.put("item11", null); prefetch.put("item12", null); prefetch.put("item13", null);
        TestData.writeCdsRequestToFile(
                "request-" + id + ".json",
                patientRef, contextEncounterRef,
                dynamicContextPrescription, prefetch
        );
    }

    @Test
    public void TestRecommendation04_NotLongActingOpioid() throws IOException, JAXBException {
        String id = "example-rec-04-not-long-acting-opioid";
        String contextId = id + "-context";
        String prefetchId = id + "-prefetch";
        String patientRef = "Patient/" + id;
        String contextEncounterRef = "Encounter/" + id + "-context";
        String prefetchEncounterRef = "Encounter/" + id + "-prefetch";

        Context context = getStu3Context("OpioidCDS_STU3_REC_04.xml");
        context.enterContext("Patient");
        context.setContextValue(context.getCurrentContext(), id);

        Patient patient = TestData.getPatient(id, "male", "1982-01-07");
        client.update().resource(patient).execute();

        Encounter contextEncounter = TestData.getEncounter(contextId, patientRef, null);
        client.update().resource(contextEncounter).execute();
        Encounter dynamicContextEncounter = TestData.getEncounterWithExtension(contextId, patientRef, "Today()");

        String todayMinus4Months = LocalDate.now().minusMonths(4L).toString();
        Encounter prefetchEncounter = TestData.getEncounter(prefetchId, patientRef, todayMinus4Months);
        client.update().resource(prefetchEncounter).execute();
        Encounter dynamicPrefetchEncounter = TestData.getEncounterWithExtension(prefetchId, patientRef, "Today() - 4 months");

        // Opioid has abuse potential, but not long acting case
        MedicationRequest contextPrescription = TestData.getMedicationRequestDefaultDoseDispense(
                contextId, "1010600", "Buprenorphine 2 MG / Naloxone 0.5 MG Oral Strip",
                patientRef, contextEncounterRef, null
        );
        context.setParameter(null, "ContextPrescriptions", Collections.singletonList(contextPrescription));
        MedicationRequest dynamicContextPrescription = TestData.getMedicationRequestWithExtensionsDefaultDispense(
                contextId, "1010600", "Buprenorphine 2 MG / Naloxone 0.5 MG Oral Strip",
                patientRef, contextEncounterRef, "Today()"
        );

        MedicationRequest prefetchPrescription = TestData.getMedicationRequestDefaultDoseDispense(
                prefetchId, "1049502", "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet",
                patientRef, prefetchEncounterRef, todayMinus4Months
        );
        client.update().resource(prefetchPrescription).execute();
        MedicationRequest dynamicPrefetchPrescription = TestData.getMedicationRequestWithExtensionsDefaultDispense(
                prefetchId, "1049502", "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet",
                patientRef, prefetchEncounterRef, "Today() - 4 months"
        );

        Object result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertFalse((Boolean) result);

        Bundle testBundle =
                new TransactionBundleBuilder()
                        .buildId(id)
                        .buildEntry(baseUrl + patientRef, patient, null)
//                        .buildEntry(baseUrl + "MedicationRequest/" + prefetchId, dynamicPrefetchPrescription, null)
//                        .buildEntry(baseUrl + prefetchEncounterRef, dynamicPrefetchEncounter, null)
                        .buildEntry(baseUrl + contextEncounterRef, dynamicContextEncounter, null)
                        .build();
        TestData.writeBundleToFile("bundle-" + id + ".xml", testBundle);

        Map<String, Resource> prefetch = new LinkedHashMap<>();
        prefetch.put("item1", dynamicPrefetchPrescription);
        prefetch.put("item2", dynamicPrefetchEncounter);
        prefetch.put("item3", patient);
        // these items are needed to satisfy dynamic prefetch validation
        prefetch.put("item4", null); prefetch.put("item5", null); prefetch.put("item6", null); prefetch.put("item7", null);
        prefetch.put("item8", null); prefetch.put("item9", null); prefetch.put("item10", null);
        prefetch.put("item11", null); prefetch.put("item12", null); prefetch.put("item13", null);
        TestData.writeCdsRequestToFile(
                "request-" + id + ".json",
                patientRef, contextEncounterRef,
                dynamicContextPrescription, prefetch
        );
    }

    @Test
    public void TestRecommendation04_OpioidWithAbusePotential() throws IOException, JAXBException {
        String id = "example-rec-04-opioid-with-abuse-potential";
        String contextId = id + "-context";
        String prefetchId = id + "-prefetch";
        String patientRef = "Patient/" + id;
        String contextEncounterRef = "Encounter/" + id + "-context";
        String prefetchEncounterRef = "Encounter/" + id + "-prefetch";

        Context context = getStu3Context("OpioidCDS_STU3_REC_04.xml");
        context.enterContext("Patient");
        context.setContextValue(context.getCurrentContext(), id);

        Patient patient = TestData.getPatient(id, "male", "1982-01-07");
        client.update().resource(patient).execute();

        Encounter contextEncounter = TestData.getEncounter(contextId, patientRef, null);
        client.update().resource(contextEncounter).execute();
        Encounter dynamicContextEncounter = TestData.getEncounterWithExtension(contextId, patientRef, "Today()");

        String todayMinusTwoWeeks = LocalDate.now().minusWeeks(2L).toString();
        Encounter prefetchEncounter = TestData.getEncounter(prefetchId, patientRef, todayMinusTwoWeeks);
        client.update().resource(prefetchEncounter).execute();
        Encounter dynamicPrefetchEncounter = TestData.getEncounterWithExtension(prefetchId, patientRef, "Today() - 2 weeks");

        // Prescribed opioid with abuse potential in past 90 days case
        MedicationRequest contextPrescription = TestData.getMedicationRequestDefaultDoseDispense(
                contextId, "1049502", "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet",
                patientRef, contextEncounterRef, null
        );
        context.setParameter(null, "ContextPrescriptions", Collections.singletonList(contextPrescription));
        MedicationRequest dynamicContextPrescription = TestData.getMedicationRequestWithExtensionsDefaultDispense(
                contextId, "1049502", "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet",
                patientRef, contextEncounterRef, "Today()"
        );

        MedicationRequest prefetchPrescription = TestData.getMedicationRequestDefaultDoseDispense(
                prefetchId, "1049502", "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet",
                patientRef, prefetchEncounterRef, todayMinusTwoWeeks
        );
        client.update().resource(prefetchPrescription).execute();
        MedicationRequest dynamicPrefetchPrescription = TestData.getMedicationRequestWithExtensionsDefaultDispense(
                prefetchId, "1049502", "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet",
                patientRef, prefetchEncounterRef, "Today() - 2 weeks"
        );

        Object result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertFalse((Boolean) result);

        Bundle testBundle =
                new TransactionBundleBuilder()
                        .buildId(id)
                        .buildEntry(baseUrl + patientRef, patient, null)
//                        .buildEntry(baseUrl + "MedicationRequest/" + prefetchId, dynamicPrefetchPrescription, null)
//                        .buildEntry(baseUrl + prefetchEncounterRef, dynamicPrefetchEncounter, null)
                        .buildEntry(baseUrl + contextEncounterRef, dynamicContextEncounter, null)
                        .build();
        TestData.writeBundleToFile("bundle-" + id + ".xml", testBundle);

        Map<String, Resource> prefetch = new LinkedHashMap<>();
        prefetch.put("item1", dynamicPrefetchPrescription);
        prefetch.put("item2", dynamicPrefetchEncounter);
        prefetch.put("item3", patient);
        // these items are needed to satisfy dynamic prefetch validation
        prefetch.put("item4", null); prefetch.put("item5", null); prefetch.put("item6", null); prefetch.put("item7", null);
        prefetch.put("item8", null); prefetch.put("item9", null); prefetch.put("item10", null);
        prefetch.put("item11", null); prefetch.put("item12", null); prefetch.put("item13", null);
        TestData.writeCdsRequestToFile(
                "request-" + id + ".json",
                patientRef, contextEncounterRef,
                dynamicContextPrescription, prefetch
        );
    }

    @Test
    public void TestRecommendation05_MMEGreaterThan50() throws IOException, JAXBException {
        String id = "example-rec-05-mme-greater-than-fifty";
        String contextId = id + "-context";
        String patientRef = "Patient/" + id;
        String contextEncounterRef = "Encounter/" + id + "-context";

        Context context = getStu3Context("OpioidCDS_STU3_REC_05.xml");
        context.enterContext("Patient");
        context.setContextValue(context.getCurrentContext(), id);

        Patient patient = TestData.getPatient(id, "female", "1982-01-07");
        client.update().resource(patient).execute();

        Encounter contextEncounter = TestData.getEncounter(contextId, patientRef, null);
        client.update().resource(contextEncounter).execute();
        Encounter dynamicContextEncounter = TestData.getEncounterWithExtension(contextId, patientRef, "Today()");

        String todayPlusSixMonths = LocalDate.now().plusMonths(6L).toString();
        MedicationRequest contextPrescription = TestData.getMedicationRequest(
                1, 3.0, 1.0, 3, 30.0,
                contextId, "197696", "72 HR Fentanyl 0.075 MG/HR Transdermal System", patientRef, contextEncounterRef,
                null, "d", "patch", null, todayPlusSixMonths, "d");
        context.setParameter(null, "ContextPrescriptions", Collections.singletonList(contextPrescription));
        MedicationRequest dynamicContextPrescription = TestData.getMedicationRequestWithExtensions(
                1, 3.0, 1.0, 3, 30.0,
                contextId, "197696", "72 HR Fentanyl 0.075 MG/HR Transdermal System", patientRef, contextEncounterRef,
                "Today()", "d", "patch", "Today() + 6 months", "d");

        Object result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertTrue((Boolean) result);

        result = context.resolveExpressionRef("Get Summary").getExpression().evaluate(context);
        Assert.assertTrue(result.equals("High risk for opioid overdose - taper now"));

        result = context.resolveExpressionRef("Get Detail").getExpression().evaluate(context);
        Assert.assertTrue(result.equals("Total morphine milligram equivalent (MME) is 179.99999820mg/d. Taper to less than 50."));

        Bundle testBundle =
                new TransactionBundleBuilder()
                        .buildId(id)
                        .buildEntry(baseUrl + patientRef, patient, null)
                        .buildEntry(baseUrl + contextEncounterRef, dynamicContextEncounter, null)
                        .build();
        TestData.writeBundleToFile("bundle-" + id + ".xml", testBundle);

        Map<String, Resource> prefetch = new LinkedHashMap<>();
        prefetch.put("item1", null);
        prefetch.put("item2", null);
        prefetch.put("item3", patient);
        // these items are needed to satisfy dynamic prefetch validation
        prefetch.put("item4", null); prefetch.put("item5", null); prefetch.put("item6", null); prefetch.put("item7", null);
        prefetch.put("item8", null); prefetch.put("item9", null); prefetch.put("item10", null);
        prefetch.put("item11", null); prefetch.put("item12", null); prefetch.put("item13", null);
        TestData.writeCdsRequestToFile(
                "request-" + id + ".json",
                patientRef, contextEncounterRef,
                dynamicContextPrescription, prefetch
        );
    }

    @Test
    public void TestRecommendation05_MMELessThan50() throws IOException, JAXBException {
        String id = "example-rec-05-mme-less-than-fifty";
        String contextId = id + "-context";
        String patientRef = "Patient/" + id;
        String contextEncounterRef = "Encounter/" + id + "-context";

        Context context = getStu3Context("OpioidCDS_STU3_REC_05.xml");
        context.enterContext("Patient");
        context.setContextValue(context.getCurrentContext(), id);

        Patient patient = TestData.getPatient(id, "female", "1982-01-07");
        client.update().resource(patient).execute();

        Encounter contextEncounter = TestData.getEncounter(contextId, patientRef, null);
        client.update().resource(contextEncounter).execute();
        Encounter dynamicContextEncounter = TestData.getEncounterWithExtension(contextId, patientRef, "Today()");

        String todayPlusSixMonths = LocalDate.now().plusMonths(6L).toString();
        MedicationRequest contextPrescription = TestData.getMedicationRequest(
                1, 12.0, 1.0, 3, 30.0, contextId, "197696",
                "72 HR Fentanyl 0.075 MG/HR Transdermal System", patientRef, contextEncounterRef, null, "d", "patch",
                null, todayPlusSixMonths, "d"
        );
        context.setParameter(null, "ContextPrescriptions", Collections.singletonList(contextPrescription));
        MedicationRequest dynamicContextPrescription = TestData.getMedicationRequestWithExtensions(
                1, 12.0, 1.0, 3, 30.0, contextId, "197696",
                "72 HR Fentanyl 0.075 MG/HR Transdermal System", patientRef, contextEncounterRef, "Today()", "d", "patch",
                "Today() + 6 months", "d"
        );

        Object result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertFalse((Boolean) result);

        Bundle testBundle =
                new TransactionBundleBuilder()
                        .buildId(id)
                        .buildEntry(baseUrl + patientRef, patient, null)
                        .buildEntry(baseUrl + contextEncounterRef, dynamicContextEncounter, null)
                        .build();
        TestData.writeBundleToFile("bundle-" + id + ".xml", testBundle);

        Map<String, Resource> prefetch = new LinkedHashMap<>();
        prefetch.put("item1", null);
        prefetch.put("item2", null);
        prefetch.put("item3", patient);
        // these items are needed to satisfy dynamic prefetch validation
        prefetch.put("item4", null); prefetch.put("item5", null); prefetch.put("item6", null); prefetch.put("item7", null);
        prefetch.put("item8", null); prefetch.put("item9", null); prefetch.put("item10", null);
        prefetch.put("item11", null); prefetch.put("item12", null); prefetch.put("item13", null);
        TestData.writeCdsRequestToFile(
                "request-" + id + ".json",
                patientRef, contextEncounterRef,
                dynamicContextPrescription, prefetch
        );
    }

    @Test
    public void TestRecommendation07_7OfPast10Days() throws IOException, JAXBException {
        String id = "example-rec-07-seven-of-past-ten-days";
        String contextId = id + "-context";
        String prefetchId = id + "-prefetch";
        String patientRef = "Patient/" + id;
        String contextEncounterRef = "Encounter/" + id + "-context";
        String prefetchEncounterRef = "Encounter/" + id + "-prefetch";

        Context context = getStu3Context("OpioidCDS_STU3_REC_07.xml");
        context.enterContext("Patient");
        context.setContextValue(context.getCurrentContext(), id);

        Patient patient = TestData.getPatient(id, "male", "1982-01-07");
        client.update().resource(patient).execute();

        Encounter contextEncounter = TestData.getEncounter(contextId, patientRef, null);
        client.update().resource(contextEncounter).execute();
        Encounter dynamicContextEncounter = TestData.getEncounterWithExtension(contextId, patientRef, "Today()");

        String todayMinusTenDays = LocalDate.now().minusDays(10L).toString();
        Encounter prefetchEncounter = TestData.getEncounter(prefetchId, patientRef, todayMinusTenDays);
        client.update().resource(prefetchEncounter).execute();
        Encounter dynamicPrefetchEncounter = TestData.getEncounterWithExtension(prefetchId, patientRef, "Today() - 10 days");

        // True case taken 7 of past 10 days
        String todayPlusThreeMonths = LocalDate.now().plusMonths(3L).toString();
        MedicationRequest contextPrescription = TestData.getMedicationRequest(
                1, 1.0, 1.0, 1, 7.0, contextId, "1049502",
                "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet", patientRef, contextEncounterRef,
                null, "d", "tablet", null, todayPlusThreeMonths, "d"
        );
        context.setParameter(null, "ContextPrescriptions", Collections.singletonList(contextPrescription));
        MedicationRequest dynamicContextPrescription = TestData.getMedicationRequestWithExtensions(
                1, 1.0, 1.0, 1, 7.0, contextId, "1049502",
                "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet", patientRef, contextEncounterRef,
                "Today()", "d", "tablet", "Today() + 3 months", "d"
        );

        MedicationRequest prefetchPrescription = TestData.getMedicationRequest(
                1, 1.0, 1.0, 1, 7.0, prefetchId, "1049502",
                "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet", patientRef, prefetchEncounterRef,
                todayMinusTenDays, "d", "tablet", todayMinusTenDays, null, "d"
        );
        client.update().resource(prefetchPrescription).execute();
        MedicationRequest dynamicPrefetchPrescription = TestData.getMedicationRequestWithExtensions(
                1, 1.0, 1.0, 1, 7.0, prefetchId, "1049502",
                "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet", patientRef, prefetchEncounterRef,
                "Today() - 10 days", "d", "tablet", "Today()", "d"
        );

        Object result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertTrue((Boolean) result);

        result = context.resolveExpressionRef("Get Detail").getExpression().evaluate(context);
        Assert.assertTrue(result.equals("No evaluation for benefits and harms has been performed for the patient starting opioid therapy"));

        Bundle testBundle =
                new TransactionBundleBuilder()
                        .buildId(id)
                        .buildEntry(baseUrl + patientRef, patient, null)
//                        .buildEntry(baseUrl + "MedicationRequest/" + prefetchId, dynamicPrefetchPrescription, null)
//                        .buildEntry(baseUrl + prefetchEncounterRef, dynamicPrefetchEncounter, null)
                        .buildEntry(baseUrl + contextEncounterRef, dynamicContextEncounter, null)
                        .build();
        TestData.writeBundleToFile("bundle-" + id + ".xml", testBundle);

        Map<String, Resource> prefetch = new LinkedHashMap<>();
        prefetch.put("item1", dynamicPrefetchPrescription);
        prefetch.put("item2", dynamicPrefetchEncounter);
        prefetch.put("item3", patient);
        // these items are needed to satisfy dynamic prefetch validation
        prefetch.put("item4", null); prefetch.put("item5", null); prefetch.put("item6", null); prefetch.put("item7", null);
        prefetch.put("item8", null); prefetch.put("item9", null); prefetch.put("item10", null); prefetch.put("item11", null);
        prefetch.put("item12", null); prefetch.put("item13", null); prefetch.put("item14", null);
        TestData.writeCdsRequestToFile(
                "request-" + id + ".json",
                patientRef, contextEncounterRef,
                dynamicContextPrescription, prefetch
        );
    }

    @Test
    public void TestRecommendation07_6OfPast10Days() throws IOException, JAXBException {
        String id = "example-rec-07-six-of-past-ten-days";
        String contextId = id + "-context";
        String prefetchId = id + "-prefetch";
        String patientRef = "Patient/" + id;
        String contextEncounterRef = "Encounter/" + id + "-context";
        String prefetchEncounterRef = "Encounter/" + id + "-prefetch";

        Context context = getStu3Context("OpioidCDS_STU3_REC_07.xml");
        context.enterContext("Patient");
        context.setContextValue(context.getCurrentContext(), id);

        Patient patient = TestData.getPatient(id, "male", "1982-01-07");
        client.update().resource(patient).execute();

        Encounter contextEncounter = TestData.getEncounter(contextId, patientRef, null);
        client.update().resource(contextEncounter).execute();
        Encounter dynamicContextEncounter = TestData.getEncounterWithExtension(contextId, patientRef, "Today()");

        String todayMinusTenDays = LocalDate.now().minusDays(10L).toString();
        Encounter prefetchEncounter = TestData.getEncounter(prefetchId, patientRef, todayMinusTenDays);
        client.update().resource(prefetchEncounter).execute();
        Encounter dynamicPrefetchEncounter = TestData.getEncounterWithExtension(prefetchId, patientRef, "Today() - 10 days");

        // False case taken 6 of past 10 days
        String todayPlusThreeMonths = LocalDate.now().plusMonths(3L).toString();
        MedicationRequest contextPrescription = TestData.getMedicationRequest(
                1, 1.0, 1.0, 1, 6.0, contextId, "1049502",
                "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet", patientRef, contextEncounterRef,
                null, "d", "tablet", null, todayPlusThreeMonths, "d"
        );
        context.setParameter(null, "ContextPrescriptions", Collections.singletonList(contextPrescription));
        MedicationRequest dynamicContextPrescription = TestData.getMedicationRequestWithExtensions(
                1, 1.0, 1.0, 1, 6.0, contextId, "1049502",
                "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet", patientRef, contextEncounterRef,
                "Today()", "d", "tablet", "Today() + 3 months", "d"
        );

        MedicationRequest prefetchPrescription = TestData.getMedicationRequest(
                1, 1.0, 1.0, 1, 6.0, prefetchId, "1049502",
                "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet", patientRef, prefetchEncounterRef,
                todayMinusTenDays, "d", "tablet", todayMinusTenDays, null, "d"
        );
        client.update().resource(prefetchPrescription).execute();
        MedicationRequest dynamicPrefetchPrescription = TestData.getMedicationRequestWithExtensions(
                1, 1.0, 1.0, 1, 6.0, prefetchId, "1049502",
                "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet", patientRef, prefetchEncounterRef,
                "Today() - 10 days", "d", "tablet", "Today()", "d"
        );

        Object result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertFalse((Boolean) result);

        Bundle testBundle =
                new TransactionBundleBuilder()
                        .buildId(id)
                        .buildEntry(baseUrl + patientRef, patient, null)
//                        .buildEntry(baseUrl + "MedicationRequest/" + prefetchId, dynamicPrefetchPrescription, null)
//                        .buildEntry(baseUrl + prefetchEncounterRef, dynamicPrefetchEncounter, null)
                        .buildEntry(baseUrl + contextEncounterRef, dynamicContextEncounter, null)
                        .build();
        TestData.writeBundleToFile("bundle-" + id + ".xml", testBundle);

        Map<String, Resource> prefetch = new LinkedHashMap<>();
        prefetch.put("item1", dynamicPrefetchPrescription);
        prefetch.put("item2", dynamicPrefetchEncounter);
        prefetch.put("item3", patient);
        // these items are needed to satisfy dynamic prefetch validation
        prefetch.put("item4", null); prefetch.put("item5", null); prefetch.put("item6", null); prefetch.put("item7", null);
        prefetch.put("item8", null); prefetch.put("item9", null); prefetch.put("item10", null); prefetch.put("item11", null);
        prefetch.put("item12", null); prefetch.put("item13", null); prefetch.put("item14", null);
        TestData.writeCdsRequestToFile(
                "request-" + id + ".json",
                patientRef, contextEncounterRef,
                dynamicContextPrescription, prefetch
        );
    }

    @Test
    public void TestRecommendation07_62OfPast90Days() throws IOException, JAXBException {
        String id = "example-rec-07-sixtytwo-of-past-ninety-days";
        String contextId = id + "-context";
        String prefetchId = id + "-prefetch";
        String patientRef = "Patient/" + id;
        String contextEncounterRef = "Encounter/" + id + "-context";
        String prefetchEncounterRef = "Encounter/" + id + "-prefetch";

        Context context = getStu3Context("OpioidCDS_STU3_REC_07.xml");
        context.enterContext("Patient");
        context.setContextValue(context.getCurrentContext(), "example-rec-07-sixtytwo-of-past-ninety-days");

        Patient patient = TestData.getPatient(id, "male", "1982-01-07");
        client.update().resource(patient).execute();

        Encounter contextEncounter = TestData.getEncounter(contextId, patientRef, null);
        client.update().resource(contextEncounter).execute();
        Encounter dynamicContextEncounter = TestData.getEncounterWithExtension(contextId, patientRef, "Today()");

        String todayMinusNinetyDays = LocalDate.now().minusDays(90L).toString();
        Encounter prefetchEncounter = TestData.getEncounter(prefetchId, patientRef, todayMinusNinetyDays);
        client.update().resource(prefetchEncounter).execute();
        Encounter dynamicPrefetchEncounter = TestData.getEncounterWithExtension(prefetchId, patientRef, "Today() - 90 days");

        // False case taken 62 of past 90 days
        String todayPlusThreeMonths = LocalDate.now().plusMonths(3L).toString();
        MedicationRequest contextPrescription = TestData.getMedicationRequest(
                1, 1.0, 1.0, 1, 30.0, contextId, "1049502",
                "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet", patientRef, contextEncounterRef,
                null, "d", "tablet", null, todayPlusThreeMonths, "d"
        );
        context.setParameter(null, "ContextPrescriptions", Collections.singletonList(contextPrescription));
        MedicationRequest dynamicContextPrescription = TestData.getMedicationRequestWithExtensions(
                1, 1.0, 1.0, 1, 30.0, contextId, "1049502",
                "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet", patientRef, contextEncounterRef,
                "Today()", "d", "tablet", "Today() + 3 months", "d"
        );

        MedicationRequest prefetchPrescription = TestData.getMedicationRequest(
                1, 1.0, 1.0, 1, 62.0, prefetchId, "1049502",
                "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet", patientRef, prefetchEncounterRef,
                todayMinusNinetyDays, "d", "tablet", todayMinusNinetyDays, null, "d"
        );
        client.update().resource(prefetchPrescription).execute();
        MedicationRequest dynamicPrefetchPrescription = TestData.getMedicationRequestWithExtensions(
                1, 1.0, 1.0, 1, 62.0, prefetchId, "1049502",
                "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet", patientRef, prefetchEncounterRef,
                "Today() - 90 days", "d", "tablet", "Today()", "d"
        );

        Object result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertFalse((Boolean) result);

        Bundle testBundle =
                new TransactionBundleBuilder()
                        .buildId(id)
                        .buildEntry(baseUrl + patientRef, patient, null)
//                        .buildEntry(baseUrl + "MedicationRequest/" + prefetchId, dynamicPrefetchPrescription, null)
//                        .buildEntry(baseUrl + prefetchEncounterRef, dynamicPrefetchEncounter, null)
                        .buildEntry(baseUrl + contextEncounterRef, dynamicContextEncounter, null)
                        .build();
        TestData.writeBundleToFile("bundle-" + id + ".xml", testBundle);

        Map<String, Resource> prefetch = new LinkedHashMap<>();
        prefetch.put("item1", dynamicPrefetchPrescription);
        prefetch.put("item2", dynamicPrefetchEncounter);
        prefetch.put("item3", patient);
        // these items are needed to satisfy dynamic prefetch validation
        prefetch.put("item4", null); prefetch.put("item5", null); prefetch.put("item6", null); prefetch.put("item7", null);
        prefetch.put("item8", null); prefetch.put("item9", null); prefetch.put("item10", null); prefetch.put("item11", null);
        prefetch.put("item12", null); prefetch.put("item13", null); prefetch.put("item14", null);
        TestData.writeCdsRequestToFile(
                "request-" + id + ".json",
                patientRef, contextEncounterRef,
                dynamicContextPrescription, prefetch
        );
    }

    @Test
    public void TestRecommendation07_63OfPast90Days() throws IOException, JAXBException {
        String id = "example-rec-07-sixtythree-of-past-ninety-days";
        String contextId = id + "-context";
        String prefetchId = id + "-prefetch";
        String patientRef = "Patient/" + id;
        String contextEncounterRef = "Encounter/" + id + "-context";
        String prefetchEncounterRef = "Encounter/" + id + "-prefetch";

        Context context = getStu3Context("OpioidCDS_STU3_REC_07.xml");
        context.enterContext("Patient");
        context.setContextValue(context.getCurrentContext(), "example-rec-07-sixtythree-of-past-ninety-days");

        Patient patient = TestData.getPatient(id, "male", "1982-01-07");
        client.update().resource(patient).execute();

        Encounter contextEncounter = TestData.getEncounter(contextId, patientRef, null);
        client.update().resource(contextEncounter).execute();
        Encounter dynamicContextEncounter = TestData.getEncounterWithExtension(contextId, patientRef, "Today()");

        String todayMinusNinetyDays = LocalDate.now().minusDays(90L).toString();
        Encounter prefetchEncounter = TestData.getEncounter(prefetchId, patientRef, todayMinusNinetyDays);
        client.update().resource(prefetchEncounter).execute();
        Encounter dynamicPrefetchEncounter = TestData.getEncounterWithExtension(prefetchId, patientRef, "Today() - 90 days");

        // True case taken 63 of past 90 days
        String todayPlusThreeMonths = LocalDate.now().plusMonths(3L).toString();
        MedicationRequest contextPrescription = TestData.getMedicationRequest(
                1, 1.0, 1.0, 1, 30.0, contextId, "1049502",
                "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet", patientRef, contextEncounterRef,
                null, "d", "tablet", null, todayPlusThreeMonths, "d"
        );
        context.setParameter(null, "ContextPrescriptions", Collections.singletonList(contextPrescription));
        MedicationRequest dynamicContextPrescription = TestData.getMedicationRequestWithExtensions(
                1, 1.0, 1.0, 1, 30.0, contextId, "1049502",
                "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet", patientRef, contextEncounterRef,
                "Today()", "d", "tablet", "Today() + 3 months", "d"
        );

        MedicationRequest prefetchPrescription = TestData.getMedicationRequest(
                1, 1.0, 1.0, 1, 63.0, prefetchId, "1049502",
                "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet", patientRef, prefetchEncounterRef,
                todayMinusNinetyDays, "d", "tablet", todayMinusNinetyDays, null, "d"
        );
        client.update().resource(prefetchPrescription).execute();
        MedicationRequest dynamicPrefetchPrescription = TestData.getMedicationRequestWithExtensions(
                1, 1.0, 1.0, 1, 63.0, prefetchId, "1049502",
                "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet", patientRef, prefetchEncounterRef,
                "Today() - 90 days", "d", "tablet", "Today()", "d"
        );

        Object result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertTrue((Boolean) result);

        result = context.resolveExpressionRef("Get Detail").getExpression().evaluate(context);
        Assert.assertTrue(result.equals("No evaluation for benefits and harms associated with opioid therapy has been performed for the patient in the past 3 months"));

        Bundle testBundle =
                new TransactionBundleBuilder()
                        .buildId(id)
                        .buildEntry(baseUrl + patientRef, patient, null)
//                        .buildEntry(baseUrl + "MedicationRequest/" + prefetchId, dynamicPrefetchPrescription, null)
//                        .buildEntry(baseUrl + prefetchEncounterRef, dynamicPrefetchEncounter, null)
                        .buildEntry(baseUrl + contextEncounterRef, dynamicContextEncounter, null)
                        .build();
        TestData.writeBundleToFile("bundle-" + id + ".xml", testBundle);

        Map<String, Resource> prefetch = new LinkedHashMap<>();
        prefetch.put("item1", dynamicPrefetchPrescription);
        prefetch.put("item2", dynamicPrefetchEncounter);
        prefetch.put("item3", patient);
        // these items are needed to satisfy dynamic prefetch validation
        prefetch.put("item4", null); prefetch.put("item5", null); prefetch.put("item6", null); prefetch.put("item7", null);
        prefetch.put("item8", null); prefetch.put("item9", null); prefetch.put("item10", null); prefetch.put("item11", null);
        prefetch.put("item12", null); prefetch.put("item13", null); prefetch.put("item14", null);
        TestData.writeCdsRequestToFile(
                "request-" + id + ".json",
                patientRef, contextEncounterRef,
                dynamicContextPrescription, prefetch
        );
    }

    @Test
    public void TestRecommendation07_EndOfLifeExclusion() throws IOException, JAXBException {
        String id = "example-rec-07-end-of-life-exclusion";
        String contextId = id + "-context";
        String prefetchId = id + "-prefetch";
        String patientRef = "Patient/" + id;
        String contextEncounterRef = "Encounter/" + id + "-context";
        String prefetchEncounterRef = "Encounter/" + id + "-prefetch";

        Context context = getStu3Context("OpioidCDS_STU3_REC_07.xml");
        context.enterContext("Patient");
        context.setContextValue(context.getCurrentContext(), id);

        Patient patient = TestData.getPatient(id, "male", "1982-01-07");
        client.update().resource(patient).execute();

        Encounter contextEncounter = TestData.getEncounter(contextId, patientRef, null);
        client.update().resource(contextEncounter).execute();
        Encounter dynamicContextEncounter = TestData.getEncounterWithExtension(contextId, patientRef, "Today()");

        String todayMinusSixWeeks = LocalDate.now().minusWeeks(6L).toString();
        Encounter prefetchEncounter = TestData.getEncounter(prefetchId, patientRef, todayMinusSixWeeks);
        client.update().resource(prefetchEncounter).execute();
        Encounter dynamicPrefetchEncounter = TestData.getEncounterWithExtension(prefetchId, patientRef, "Today() - 6 weeks");

        // Exclusion criteria end of life opioid case
        String todayPlusThreeMonths = LocalDate.now().plusMonths(3L).toString();
        MedicationRequest contextPrescription = TestData.getMedicationRequest(
                1, 1.0, 1.0, 1, 30.0, contextId, "1049502",
                "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet", patientRef, contextEncounterRef,
                null, "d", "tablet", null, todayPlusThreeMonths, "d"
        );
        context.setParameter(null, "ContextPrescriptions", Collections.singletonList(contextPrescription));
        MedicationRequest dynamicContextPrescription = TestData.getMedicationRequestWithExtensions(
                1, 1.0, 1.0, 1, 30.0, contextId, "1049502",
                "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet", patientRef, contextEncounterRef,
                "Today()", "d", "tablet", "Today() + 3 months", "d"
        );

        String todayPlusSixWeeks = LocalDate.now().plusWeeks(6L).toString();
        MedicationRequest prefetchPrescription = TestData.getMedicationRequest(
                3, 1.0, 1.0, 3, 30.0, prefetchId, "1012727",
                "Carbinoxamine maleate 0.4 MG/ML / Hydrocodone Bitartrate 1 MG/ML / Pseudoephedrine Hydrochloride 6 MG/ML Oral Solution",
                patientRef, prefetchEncounterRef, todayMinusSixWeeks, "d", "tablet", todayMinusSixWeeks, todayPlusSixWeeks, "d"
        );
        client.update().resource(prefetchPrescription).execute();
        MedicationRequest dynamicPrefetchPrescription = TestData.getMedicationRequestWithExtensions(
                3, 1.0, 1.0, 3, 30.0, prefetchId, "1012727",
                "Carbinoxamine maleate 0.4 MG/ML / Hydrocodone Bitartrate 1 MG/ML / Pseudoephedrine Hydrochloride 6 MG/ML Oral Solution",
                patientRef, prefetchEncounterRef, "Today() - 6 weeks", "d", "tablet", "Today() + 6 weeks", "d"
        );

        Object result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertFalse((Boolean) result);

        Bundle testBundle =
                new TransactionBundleBuilder()
                        .buildId(id)
                        .buildEntry(baseUrl + patientRef, patient, null)
//                        .buildEntry(baseUrl + "MedicationRequest/" + prefetchId, dynamicPrefetchPrescription, null)
//                        .buildEntry(baseUrl + prefetchEncounterRef, dynamicPrefetchEncounter, null)
                        .buildEntry(baseUrl + contextEncounterRef, dynamicContextEncounter, null)
                        .build();
        TestData.writeBundleToFile("bundle-" + id + ".xml", testBundle);

        Map<String, Resource> prefetch = new LinkedHashMap<>();
        prefetch.put("item1", dynamicPrefetchPrescription);
        prefetch.put("item2", dynamicPrefetchEncounter);
        prefetch.put("item3", patient);
        // these items are needed to satisfy dynamic prefetch validation
        prefetch.put("item4", null); prefetch.put("item5", null); prefetch.put("item6", null); prefetch.put("item7", null);
        prefetch.put("item8", null); prefetch.put("item9", null); prefetch.put("item10", null); prefetch.put("item11", null);
        prefetch.put("item12", null); prefetch.put("item13", null); prefetch.put("item14", null);
        TestData.writeCdsRequestToFile(
                "request-" + id + ".json",
                patientRef, contextEncounterRef,
                dynamicContextPrescription, prefetch
        );
    }

    @Test
    public void TestRecommendation07_RiskAssessmentInPast90Days() throws IOException, JAXBException {
        String id = "example-rec-07-risk-assessment";
        String contextId = id + "-context";
        String prefetchId = id + "-prefetch";
        String patientRef = "Patient/" + id;
        String contextEncounterRef = "Encounter/" + id + "-context";
        String prefetchEncounterRef = "Encounter/" + id + "-prefetch";

        Context context = getStu3Context("OpioidCDS_STU3_REC_07.xml");
        context.enterContext("Patient");
        context.setContextValue(context.getCurrentContext(), id);

        Patient patient = TestData.getPatient(id, "male", "1982-01-07");
        client.update().resource(patient).execute();

        Encounter contextEncounter = TestData.getEncounter(contextId, patientRef, null);
        client.update().resource(contextEncounter).execute();
        Encounter dynamicContextEncounter = TestData.getEncounterWithExtension(contextId, patientRef, "Today()");

        String todayMinusTwoWeeks = LocalDate.now().minusWeeks(2L).toString();
        Encounter prefetchEncounter = TestData.getEncounter(prefetchId, patientRef, todayMinusTwoWeeks);
        client.update().resource(prefetchEncounter).execute();
        Encounter dynamicPrefetchEncounter = TestData.getEncounterWithExtension(prefetchId, patientRef, "Today() - 2 weeks");

        // Risk Assessment Procedure Case
        String todayPlusThreeMonths = LocalDate.now().plusMonths(3L).toString();
        MedicationRequest contextPrescription = TestData.getMedicationRequest(
                1, 1.0, 1.0, 1, 30.0, contextId, "1049502",
                "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet", patientRef, contextEncounterRef,
                null, "d", "tablet", null, todayPlusThreeMonths, "d"
        );
        context.setParameter(null, "ContextPrescriptions", Collections.singletonList(contextPrescription));
        MedicationRequest dynamicContextPrescription = TestData.getMedicationRequestWithExtensions(
                1, 1.0, 1.0, 1, 30.0, contextId, "1049502",
                "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet", patientRef, contextEncounterRef,
                "Today()", "d", "tablet", "Today() + 3 months", "d"
        );

        Procedure riskAssessment = TestData.getProcedure(
                prefetchId, "268525008", "High risk drug monitoring (regime/therapy)",
                patientRef, prefetchEncounterRef, todayMinusTwoWeeks
        );
        client.update().resource(riskAssessment).execute();
        Procedure dynamicRiskAssessment = TestData.getProcedureWithExtensions(
                prefetchId, "268525008", "High risk drug monitoring (regime/therapy)",
                patientRef, prefetchEncounterRef, "Today() - 2 weeks"
        );

        Object result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertFalse((Boolean) result);

        result = context.resolveExpressionRef("Opioid Risk Assessment in Past 90 Days").getExpression().evaluate(context);
        Assert.assertTrue(((List) result).size() > 0);

        Bundle testBundle =
                new TransactionBundleBuilder()
                        .buildId(id)
                        .buildEntry(baseUrl + patientRef, patient, null)
//                        .buildEntry(baseUrl + "Procedure/" + prefetchId, dynamicRiskAssessment, null)
//                        .buildEntry(baseUrl + prefetchEncounterRef, dynamicPrefetchEncounter, null)
                        .buildEntry(baseUrl + contextEncounterRef, dynamicContextEncounter, null)
                        .build();
        TestData.writeBundleToFile("bundle-" + id + ".xml", testBundle);

        Map<String, Resource> prefetch = new LinkedHashMap<>();
        prefetch.put("item1", null);
        prefetch.put("item2", dynamicPrefetchEncounter);
        prefetch.put("item3", patient);
        // these items are needed to satisfy dynamic prefetch validation
        prefetch.put("item4", dynamicRiskAssessment); prefetch.put("item5", null); prefetch.put("item6", null); prefetch.put("item7", null);
        prefetch.put("item8", null); prefetch.put("item9", null); prefetch.put("item10", null); prefetch.put("item11", null);
        prefetch.put("item12", null); prefetch.put("item13", null); prefetch.put("item14", null);
        TestData.writeCdsRequestToFile(
                "request-" + id + ".json",
                patientRef, contextEncounterRef,
                dynamicContextPrescription, prefetch
        );
    }

    @Test
    public void TestRecommendation08_MMEGreaterThan50() throws IOException, JAXBException, InterruptedException {
        String id = "example-rec-08-mme-greater-than-fifty";
        String contextId = id + "-context";
        String patientRef = "Patient/" + id;
        String contextEncounterRef = "Encounter/" + id + "-context";

        Context context = getStu3Context("OpioidCDS_STU3_REC_08.xml");
        context.enterContext("Patient");
        context.setContextValue(context.getCurrentContext(), id);

        Patient patient = TestData.getPatient(id, "male", "1982-01-07");
        client.update().resource(patient).execute();

        Encounter contextEncounter = TestData.getEncounter(contextId, patientRef, null);
        client.update().resource(contextEncounter).execute();
        Encounter dynamicContextEncounter = TestData.getEncounterWithExtension(contextId, patientRef, "Today()");

        // True case MME > 50 mg/d
        String todayPlusThreeMonths = LocalDate.now().plusMonths(3L).toString();
        MedicationRequest contextPrescription = TestData.getMedicationRequest(
                1, 10.0, 1.0, 3, 30.0, contextId, "197696",
                "72 HR Fentanyl 0.075 MG/HR Transdermal System", patientRef, contextEncounterRef,
                null, "d", "patch", null, todayPlusThreeMonths, "d"
        );
        context.setParameter(null, "ContextPrescriptions", Collections.singletonList(contextPrescription));
        MedicationRequest dynamicContextPrescription = TestData.getMedicationRequestWithExtensions(
                1, 10.0, 1.0, 3, 30.0, contextId, "197696",
                "72 HR Fentanyl 0.075 MG/HR Transdermal System", patientRef, contextEncounterRef,
                "Today()", "d", "patch", "Today() + 3 months", "d"
        );

        Object result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertTrue((Boolean) result);

        result = context.resolveExpressionRef("Get Detail").getExpression().evaluate(context);
        Assert.assertTrue(result.equals("Consider offering naloxone given following risk factor(s) for opioid overdose: Average MME (54.000000mg/d) >= 50 mg/day, "));

        Bundle testBundle =
                new TransactionBundleBuilder()
                        .buildId(id)
                        .buildEntry(baseUrl + patientRef, patient, null)
                        .buildEntry(baseUrl + contextEncounterRef, dynamicContextEncounter, null)
                        .build();
        TestData.writeBundleToFile("bundle-" + id + ".xml", testBundle);

        Map<String, Resource> prefetch = new LinkedHashMap<>();
        prefetch.put("item1", null);
        prefetch.put("item2", null);
        prefetch.put("item3", patient);
        // these items are needed to satisfy dynamic prefetch validation
        prefetch.put("item4", null); prefetch.put("item5", null); prefetch.put("item6", null); prefetch.put("item7", null);
        prefetch.put("item8", null); prefetch.put("item9", null); prefetch.put("item10", null); prefetch.put("item11", null);
        prefetch.put("item12", null); prefetch.put("item13", null); prefetch.put("item14", null); prefetch.put("item15", null);
        prefetch.put("item16", null);
        TestData.writeCdsRequestToFile(
                "request-" + id + ".json",
                patientRef, contextEncounterRef,
                dynamicContextPrescription, prefetch
        );
    }

    @Test
    public void TestRecommendation08_MMELessThan50() throws IOException, JAXBException {
        String id = "example-rec-08-mme-less-than-fifty";
        String contextId = id + "-context";
        String patientRef = "Patient/" + id;
        String contextEncounterRef = "Encounter/" + id + "-context";

        Context context = getStu3Context("OpioidCDS_STU3_REC_08.xml");
        context.enterContext("Patient");
        context.setContextValue(context.getCurrentContext(), id);

        Patient patient = TestData.getPatient(id, "male", "1982-01-07");
        client.update().resource(patient).execute();

        Encounter contextEncounter = TestData.getEncounter(contextId, patientRef, null);
        client.update().resource(contextEncounter).execute();
        Encounter dynamicContextEncounter = TestData.getEncounterWithExtension(contextId, patientRef, "Today()");

        // False case MME < 50 mg/d
        String todayPlusThreeMonths = LocalDate.now().plusMonths(3L).toString();
        MedicationRequest contextPrescription = TestData.getMedicationRequest(
                1, 12.0, 1.0, 3, 30.0, contextId, "197696",
                "72 HR Fentanyl 0.075 MG/HR Transdermal System", patientRef, contextEncounterRef,
                null, "d", "patch", null, todayPlusThreeMonths, "d"
        );
        context.setParameter(null, "ContextPrescriptions", Collections.singletonList(contextPrescription));
        MedicationRequest dynamicContextPrescription = TestData.getMedicationRequestWithExtensions(
                1, 12.0, 1.0, 3, 30.0, contextId, "197696",
                "72 HR Fentanyl 0.075 MG/HR Transdermal System", patientRef, contextEncounterRef,
                "Today()", "d", "patch", "Today() + 3 months", "d"
        );

        Object result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertFalse((Boolean) result);

        Bundle testBundle =
                new TransactionBundleBuilder()
                        .buildId(id)
                        .buildEntry(baseUrl + patientRef, patient, null)
                        .buildEntry(baseUrl + contextEncounterRef, dynamicContextEncounter, null)
                        .build();
        TestData.writeBundleToFile("bundle-" + id + ".xml", testBundle);

        Map<String, Resource> prefetch = new LinkedHashMap<>();
        prefetch.put("item1", null);
        prefetch.put("item2", null);
        prefetch.put("item3", patient);
        // these items are needed to satisfy dynamic prefetch validation
        prefetch.put("item4", null); prefetch.put("item5", null); prefetch.put("item6", null); prefetch.put("item7", null);
        prefetch.put("item8", null); prefetch.put("item9", null); prefetch.put("item10", null); prefetch.put("item11", null);
        prefetch.put("item12", null); prefetch.put("item13", null); prefetch.put("item14", null); prefetch.put("item15", null);
        prefetch.put("item16", null);
        TestData.writeCdsRequestToFile(
                "request-" + id + ".json",
                patientRef, contextEncounterRef,
                dynamicContextPrescription, prefetch
        );
    }

    @Test
    public void TestRecommendation08_OnBenzodiazepine() throws IOException, JAXBException, InterruptedException {
        String id = "example-rec-08-on-benzodiazepine";
        String contextId = id + "-context";
        String prefetchId = id + "-prefetch";
        String patientRef = "Patient/" + id;
        String contextEncounterRef = "Encounter/" + id + "-context";
        String prefetchEncounterRef = "Encounter/" + id + "-prefetch";

        Context context = getStu3Context("OpioidCDS_STU3_REC_08.xml");
        context.enterContext("Patient");
        context.setContextValue(context.getCurrentContext(), id);

        Patient patient = TestData.getPatient(id, "female", "1982-01-07");
        client.update().resource(patient).execute();

        Encounter contextEncounter = TestData.getEncounter(contextId, patientRef, null);
        client.update().resource(contextEncounter).execute();
        Encounter dynamicContextEncounter = TestData.getEncounterWithExtension(contextId, patientRef, "Today()");

        String todayMinusTwoWeeks = LocalDate.now().minusWeeks(2L).toString();
        Encounter prefetchEncounter = TestData.getEncounter(prefetchId, patientRef, todayMinusTwoWeeks);
        client.update().resource(prefetchEncounter).execute();
        Encounter dynamicPrefetchEncounter = TestData.getEncounterWithExtension(prefetchId, patientRef, "Today() - 2 weeks");

        // True case on benzodiazepine
        String todayPlusThreeMonths = LocalDate.now().plusMonths(3L).toString();
        MedicationRequest contextPrescription = TestData.getMedicationRequest(
                1, 12.0, 1.0, 3, 30.0, contextId, "197696",
                "72 HR Fentanyl 0.075 MG/HR Transdermal System", patientRef, contextEncounterRef,
                null, "d", "patch", null, todayPlusThreeMonths, "d"
        );
        context.setParameter(null, "ContextPrescriptions", Collections.singletonList(contextPrescription));
        MedicationRequest dynamicContextPrescription = TestData.getMedicationRequestWithExtensions(
                1, 12.0, 1.0, 3, 30.0, contextId, "197696",
                "72 HR Fentanyl 0.075 MG/HR Transdermal System", patientRef, contextEncounterRef,
                "Today()", "d", "patch", "Today() + 3 months", "d"
        );

        MedicationRequest prefetchPrescription = TestData.getMedicationRequest(
                1, 1.0, 1.0, 3, 30.0, prefetchId, "104693",
                "Temazepam 20 MG Oral Tablet", patientRef, prefetchEncounterRef,
                todayMinusTwoWeeks, "d", "tablet", todayMinusTwoWeeks, todayPlusThreeMonths, "d"
        );
        client.update().resource(prefetchPrescription).execute();
        MedicationRequest dynamicPrefetchPrescription = TestData.getMedicationRequestWithExtensions(
                1, 1.0, 1.0, 3, 30.0, prefetchId, "104693",
                "Temazepam 20 MG Oral Tablet", patientRef, prefetchEncounterRef,
                "Today() - 2 weeks", "d", "tablet", "Today() + 3 months", "d"
        );

        Object result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertTrue((Boolean) result);

        result = context.resolveExpressionRef("On Benzodiazepine").getExpression().evaluate(context);
        Assert.assertTrue((Boolean) result);

        result = context.resolveExpressionRef("Get Detail").getExpression().evaluate(context);
        Assert.assertTrue(result.equals("Consider offering naloxone given following risk factor(s) for opioid overdose: concurrent use of benzodiazepine, "));

        Bundle testBundle =
                new TransactionBundleBuilder()
                        .buildId(id)
                        .buildEntry(baseUrl + patientRef, patient, null)
//                        .buildEntry(baseUrl + "MedicationRequest/" + prefetchId, dynamicPrefetchPrescription, null)
//                        .buildEntry(baseUrl + prefetchEncounterRef, dynamicPrefetchEncounter, null)
                        .buildEntry(baseUrl + contextEncounterRef, dynamicContextEncounter, null)
                        .build();
        TestData.writeBundleToFile("bundle-" + id + ".xml", testBundle);

        Map<String, Resource> prefetch = new LinkedHashMap<>();
        prefetch.put("item1", dynamicPrefetchPrescription);
        prefetch.put("item2", dynamicPrefetchEncounter);
        prefetch.put("item3", patient);
        // these items are needed to satisfy dynamic prefetch validation
        prefetch.put("item4", null); prefetch.put("item5", null); prefetch.put("item6", null); prefetch.put("item7", null);
        prefetch.put("item8", null); prefetch.put("item9", null); prefetch.put("item10", null); prefetch.put("item11", null);
        prefetch.put("item12", null); prefetch.put("item13", null); prefetch.put("item14", null); prefetch.put("item15", null);
        prefetch.put("item16", null);
        TestData.writeCdsRequestToFile(
                "request-" + id + ".json",
                patientRef, contextEncounterRef,
                dynamicContextPrescription, prefetch
        );
    }

    @Test
    public void TestRecommendation08_OnNaloxone() throws IOException, JAXBException {
        String id = "example-rec-08-on-naloxone";
        String contextId = id + "-context";
        String prefetchId = id + "-prefetch";
        String patientRef = "Patient/" + id;
        String contextEncounterRef = "Encounter/" + id + "-context";
        String prefetchEncounterRef = "Encounter/" + id + "-prefetch";

        Context context = getStu3Context("OpioidCDS_STU3_REC_08.xml");
        context.enterContext("Patient");
        context.setContextValue(context.getCurrentContext(), id);

        Patient patient = TestData.getPatient(id, "female", "1982-01-07");
        client.update().resource(patient).execute();

        Encounter contextEncounter = TestData.getEncounter(contextId, patientRef, null);
        client.update().resource(contextEncounter).execute();
        Encounter dynamicContextEncounter = TestData.getEncounterWithExtension(contextId, patientRef, "Today()");

        String todayMinusTwoWeeks = LocalDate.now().minusWeeks(2L).toString();
        Encounter prefetchEncounter = TestData.getEncounter(prefetchId, patientRef, todayMinusTwoWeeks);
        client.update().resource(prefetchEncounter).execute();
        Encounter dynamicPrefetchEncounter = TestData.getEncounterWithExtension(prefetchId, patientRef, "Today() - 2 weeks");

        // Exclusion Criteria is on Naloxone
        String todayPlusThreeMonths = LocalDate.now().plusMonths(3L).toString();
        MedicationRequest contextPrescription = TestData.getMedicationRequest(
                1, 12.0, 1.0, 3, 30.0, contextId, "197696",
                "72 HR Fentanyl 0.075 MG/HR Transdermal System", patientRef, contextEncounterRef,
                null, "d", "patch", null, todayPlusThreeMonths, "d"
        );
        context.setParameter(null, "ContextPrescriptions", Collections.singletonList(contextPrescription));
        MedicationRequest dynamicContextPrescription = TestData.getMedicationRequestWithExtensions(
                1, 12.0, 1.0, 3, 30.0, contextId, "197696",
                "72 HR Fentanyl 0.075 MG/HR Transdermal System", patientRef, contextEncounterRef,
                "Today()", "d", "patch", "Today() + 3 months", "d"
        );

        MedicationRequest prefetchPrescription = TestData.getMedicationRequest(
                1, 1.0, 5.0, 3, 30.0, prefetchId, "1191212",
                "Naloxone Hydrochloride 0.02 MG/ML Injectable Solution", patientRef, prefetchEncounterRef,
                todayMinusTwoWeeks, "d", "mL", todayMinusTwoWeeks, todayPlusThreeMonths, "d"
        );
        client.update().resource(prefetchPrescription).execute();
        MedicationRequest dynamicPrefetchPrescription = TestData.getMedicationRequestWithExtensions(
                1, 1.0, 5.0, 3, 30.0, prefetchId, "1191212",
                "Naloxone Hydrochloride 0.02 MG/ML Injectable Solution", patientRef, prefetchEncounterRef,
                "Today() - 2 weeks", "d", "mL", "Today() + 3 months", "d"
        );

        Object result = context.resolveExpressionRef("On Naloxone").getExpression().evaluate(context);
        Assert.assertTrue((Boolean) result);

        result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertFalse((Boolean) result);

        Bundle testBundle =
                new TransactionBundleBuilder()
                        .buildId(id)
                        .buildEntry(baseUrl + patientRef, patient, null)
//                        .buildEntry(baseUrl + "MedicationRequest/" + prefetchId, dynamicPrefetchPrescription, null)
//                        .buildEntry(baseUrl + prefetchEncounterRef, dynamicPrefetchEncounter, null)
                        .buildEntry(baseUrl + contextEncounterRef, dynamicContextEncounter, null)
                        .build();
        TestData.writeBundleToFile("bundle-" + id + ".xml", testBundle);

        Map<String, Resource> prefetch = new LinkedHashMap<>();
        prefetch.put("item1", dynamicPrefetchPrescription);
        prefetch.put("item2", dynamicPrefetchEncounter);
        prefetch.put("item3", patient);
        // these items are needed to satisfy dynamic prefetch validation
        prefetch.put("item4", null); prefetch.put("item5", null); prefetch.put("item6", null); prefetch.put("item7", null);
        prefetch.put("item8", null); prefetch.put("item9", null); prefetch.put("item10", null); prefetch.put("item11", null);
        prefetch.put("item12", null); prefetch.put("item13", null); prefetch.put("item14", null); prefetch.put("item15", null);
        prefetch.put("item16", null);
        TestData.writeCdsRequestToFile(
                "request-" + id + ".json",
                patientRef, contextEncounterRef,
                dynamicContextPrescription, prefetch
        );
    }

    @Test
    public void TestRecommendation08_SubstanceAbuseHistory() throws IOException, JAXBException {
        String id = "example-rec-08-substance-abuse";
        String contextId = id + "-context";
        String prefetchId = id + "-prefetch";
        String patientRef = "Patient/" + id;
        String contextEncounterRef = "Encounter/" + id + "-context";

        Context context = getStu3Context("OpioidCDS_STU3_REC_08.xml");
        context.enterContext("Patient");
        context.setContextValue(context.getCurrentContext(), id);

        Patient patient = TestData.getPatient(id, "female", "1982-01-07");
        client.update().resource(patient).execute();

        Encounter contextEncounter = TestData.getEncounter(contextId, patientRef, null);
        client.update().resource(contextEncounter).execute();
        Encounter dynamicContextEncounter = TestData.getEncounterWithExtension(contextId, patientRef, "Today()");

        String todayMinusTwoWeeks = LocalDate.now().minusWeeks(2L).toString();
        Encounter prefetchEncounter = TestData.getEncounter(prefetchId, patientRef, todayMinusTwoWeeks);
        client.update().resource(prefetchEncounter).execute();
        Encounter dynamicPrefetchEncounter = TestData.getEncounterWithExtension(prefetchId, patientRef, "Today() - 2 weeks");

        // Exclusion Criteria has substance abuse history
        String todayPlusThreeMonths = LocalDate.now().plusMonths(3L).toString();
        MedicationRequest contextPrescription = TestData.getMedicationRequest(
                1, 12.0, 1.0, 3, 30.0, contextId, "197696",
                "72 HR Fentanyl 0.075 MG/HR Transdermal System", patientRef, contextEncounterRef,
                null, "d", "patch", null, todayPlusThreeMonths, "d"
        );
        context.setParameter(null, "ContextPrescriptions", Collections.singletonList(contextPrescription));
        MedicationRequest dynamicContextPrescription = TestData.getMedicationRequestWithExtensions(
                1, 12.0, 1.0, 3, 30.0, contextId, "197696",
                "72 HR Fentanyl 0.075 MG/HR Transdermal System", patientRef, contextEncounterRef,
                "Today()", "d", "patch", "Today() + 3 months", "d"
        );

        Condition substanceAbuseCondition = new Condition()
                .setCode(
                        new CodeableConcept().addCoding(
                                new Coding().setSystem("http://hl7.org/fhir/sid/icd-10-cm").setCode("F15.229")
                        )
                )
                .setSubject(
                        new Reference(patientRef)
                );
        substanceAbuseCondition.setId(id);
        client.update().resource(substanceAbuseCondition).execute();

        Object result = context.resolveExpressionRef("Has Substance Abuse History").getExpression().evaluate(context);
        Assert.assertTrue((Boolean) result);

        result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertTrue((Boolean) result);

        result = context.resolveExpressionRef("Get Detail").getExpression().evaluate(context);
        Assert.assertTrue(result.equals("Consider offering naloxone given following risk factor(s) for opioid overdose: history of alcohol or drug abuse."));

        Bundle testBundle =
                new TransactionBundleBuilder()
                        .buildId(id)
                        .buildEntry(baseUrl + patientRef, patient, null)
//                        .buildEntry(baseUrl + "MedicationRequest/" + prefetchId, dynamicPrefetchPrescription, null)
//                        .buildEntry(baseUrl + prefetchEncounterRef, dynamicPrefetchEncounter, null)
                        .buildEntry(baseUrl + contextEncounterRef, dynamicContextEncounter, null)
                        .build();
        TestData.writeBundleToFile("bundle-" + id + ".xml", testBundle);

        Map<String, Resource> prefetch = new LinkedHashMap<>();
        prefetch.put("item1", null);
        prefetch.put("item2", dynamicPrefetchEncounter);
        prefetch.put("item3", patient);
        // these items are needed to satisfy dynamic prefetch validation
        prefetch.put("item4", substanceAbuseCondition); prefetch.put("item5", null); prefetch.put("item6", null);
        prefetch.put("item7", null); prefetch.put("item8", null); prefetch.put("item9", null); prefetch.put("item10", null);
        prefetch.put("item11", null); prefetch.put("item12", null); prefetch.put("item13", null); prefetch.put("item14", null);
        prefetch.put("item15", null); prefetch.put("item16", null);
        TestData.writeCdsRequestToFile(
                "request-" + id + ".json",
                patientRef, contextEncounterRef,
                dynamicContextPrescription, prefetch
        );
    }

    private List<MedicationRequest> loadPrescriptions(Context context, String contextId, String prefetchId,
                                                      String patientRef, String contextEncounterRef,
                                                      String prefetchEncounterRef, boolean updateFentanyl)
    {
        String todayPlusThreeMonths = LocalDate.now().plusMonths(3L).toString();
        String todayMinusNinetyDays = LocalDate.now().minusDays(90L).toString();
        MedicationRequest contextPrescription = TestData.getMedicationRequest(
                1, 12.0, 1.0, 3, 30.0, contextId, "197696",
                "72 HR Fentanyl 0.075 MG/HR Transdermal System", patientRef, contextEncounterRef,
                null, "d", "patch", null, todayPlusThreeMonths, "d"
        );
        context.setParameter(null, "ContextPrescriptions", Collections.singletonList(contextPrescription));
        MedicationRequest dynamicContextPrescription = TestData.getMedicationRequestWithExtensions(
                1, 12.0, 1.0, 3, 30.0, contextId, "197696",
                "72 HR Fentanyl 0.075 MG/HR Transdermal System", patientRef, contextEncounterRef,
                "Today()", "d", "patch", "Today() + 3 months", "d"
        );

        MedicationRequest prefetchPrescription = TestData.getMedicationRequest(
                3, 1.0, 1.0, 3, 30.0, prefetchId, "1049502",
                "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet", patientRef, prefetchEncounterRef,
                todayMinusNinetyDays, "d", "tablet", todayMinusNinetyDays, null, "d"
        );
        client.update().resource(prefetchPrescription).execute();
        MedicationRequest dynamicPrefetchPrescription = TestData.getMedicationRequestWithExtensions(
                3, 1.0, 1.0, 3, 30.0, prefetchId, "1049502",
                "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet", patientRef, prefetchEncounterRef,
                "Today() - 90 days", "d", "tablet", "Today()", "d"
        );

        MedicationRequest prefetchFentanylPrescription = TestData.getMedicationRequest(
                3, 1.0, 1.0, 3, 30.0, prefetchId + "-fentanyl", "197696",
                "72 HR Fentanyl 0.075 MG/HR Transdermal System", patientRef, prefetchEncounterRef,
                todayMinusNinetyDays, "d", "tablet", todayMinusNinetyDays, null, "d"
        );
        if (updateFentanyl) {
            client.update().resource(prefetchFentanylPrescription).execute();
        }
        MedicationRequest dynamicFentanylPrefetchPrescription = TestData.getMedicationRequestWithExtensions(
                3, 1.0, 1.0, 3, 30.0, prefetchId + "-fentanyl", "197696",
                "72 HR Fentanyl 0.075 MG/HR Transdermal System", patientRef, prefetchEncounterRef,
                "Today() - 90 days", "d", "tablet", "Today()", "d"
        );

        return Arrays.asList(dynamicContextPrescription, dynamicPrefetchPrescription, dynamicFentanylPrefetchPrescription);
    }

    @Test
    public void TestRecommendation10_NoScreenings() throws IOException, JAXBException {
        String id = "example-rec-10-no-screenings";
        String contextId = id + "-context";
        String prefetchId = id + "-prefetch";
        String patientRef = "Patient/" + id;
        String contextEncounterRef = "Encounter/" + id + "-context";
        String prefetchEncounterRef = "Encounter/" + id + "-prefetch";

        Context context = getStu3Context("OpioidCDS_STU3_REC_10.xml");
        context.enterContext("Patient");
        context.setContextValue(context.getCurrentContext(), id);

        Patient patient = TestData.getPatient(id, "female", "1982-01-07");
        client.update().resource(patient).execute();

        Encounter contextEncounter = TestData.getEncounter(contextId, patientRef, null);
        client.update().resource(contextEncounter).execute();
        Encounter dynamicContextEncounter = TestData.getEncounterWithExtension(contextId, patientRef, "Today()");

        String todayMinusNinetyDays = LocalDate.now().minusDays(90L).toString();
        Encounter prefetchEncounter = TestData.getEncounter(prefetchId, patientRef, todayMinusNinetyDays);
        client.update().resource(prefetchEncounter).execute();
        Encounter dynamicPrefetchEncounter = TestData.getEncounterWithExtension(prefetchId, patientRef, "Today() - 90 days");

        // No urine screenings in past year case
        List<MedicationRequest> requests = loadPrescriptions(context, contextId, prefetchId, patientRef, contextEncounterRef, prefetchEncounterRef, false);

        Object result = context.resolveExpressionRef("No Urine Screening In Last 12 Months").getExpression().evaluate(context);
        Assert.assertTrue((Boolean) result);

        result = context.resolveExpressionRef("Exclusion Criteria").getExpression().evaluate(context);
        Assert.assertFalse((Boolean) result);

        result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertTrue((Boolean) result);

        Bundle testBundle =
                new TransactionBundleBuilder()
                        .buildId(id)
                        .buildEntry(baseUrl + patientRef, patient, null)
//                        .buildEntry(baseUrl + "MedicationRequest/" + prefetchId, requests.get(1), null)
//                        .buildEntry(baseUrl + prefetchEncounterRef, dynamicPrefetchEncounter, null)
                        .buildEntry(baseUrl + contextEncounterRef, dynamicContextEncounter, null)
                        .build();
        TestData.writeBundleToFile("bundle-" + id + ".xml", testBundle);

        Map<String, Resource> prefetch = new LinkedHashMap<>();
        prefetch.put("item1", requests.get(1));
        prefetch.put("item2", dynamicPrefetchEncounter);
        prefetch.put("item3", patient);
        // these items are needed to satisfy dynamic prefetch validation
        prefetch.put("item4", null); prefetch.put("item5", null); prefetch.put("item6", null); prefetch.put("item7", null);
        prefetch.put("item8", null); prefetch.put("item9", null); prefetch.put("item10", null); prefetch.put("item11", null);
        prefetch.put("item12", null); prefetch.put("item13", null); prefetch.put("item14", null); prefetch.put("item15", null);
        TestData.writeCdsRequestToFile(
                "request-" + id + ".json",
                patientRef, contextEncounterRef,
                requests.get(0), prefetch
        );
    }

    @Test
    public void TestRecommendation10_MissingPrescribedOpioids() throws IOException, JAXBException {
        String id = "example-rec-10-missing-prescribed-opioids";
        String contextId = id + "-context";
        String prefetchId = id + "-prefetch";
        String patientRef = "Patient/" + id;
        String contextEncounterRef = "Encounter/" + id + "-context";
        String prefetchEncounterRef = "Encounter/" + id + "-prefetch";

        Context context = getStu3Context("OpioidCDS_STU3_REC_10.xml");
        context.enterContext("Patient");
        context.setContextValue(context.getCurrentContext(), id);

        Patient patient = TestData.getPatient(id, "female", "1982-01-07");
        client.update().resource(patient).execute();

        Encounter contextEncounter = TestData.getEncounter(contextId, patientRef, null);
        client.update().resource(contextEncounter).execute();
        Encounter dynamicContextEncounter = TestData.getEncounterWithExtension(contextId, patientRef, "Today()");

        String todayMinusNinetyDays = LocalDate.now().minusDays(90L).toString();
        Encounter prefetchEncounter = TestData.getEncounter(prefetchId, patientRef, todayMinusNinetyDays);
        client.update().resource(prefetchEncounter).execute();
        Encounter dynamicPrefetchEncounter = TestData.getEncounterWithExtension(prefetchId, patientRef, "Today() - 90 days");

        List<MedicationRequest> requests = loadPrescriptions(context, contextId, prefetchId, patientRef, contextEncounterRef, prefetchEncounterRef, true);

        // Missing prescribed opioids case
        String todayMinusFourWeeks = LocalDate.now().minusWeeks(4L).toString();
        Observation prefetchScreening = TestData.getObservation(
                prefetchId, "10998-3", "Oxycodone [Presence] in Urine", patientRef, todayMinusFourWeeks
        );
        client.update().resource(prefetchScreening).execute();
        Observation dynamicPrefetchScreening = TestData.getObservationWithExtension(
                prefetchId, "10998-3", "Oxycodone [Presence] in Urine", patientRef, "Today() - 28 days"
        );

        Object result = context.resolveExpressionRef("Has Missing Opioids?").getExpression().evaluate(context);
        Assert.assertTrue((Boolean) result);

        result = context.resolveExpressionRef("Inconsistent Missing Opioids").getExpression().evaluate(context);
        Assert.assertTrue(result.equals("The following opioids are missing from the screening: fentanyl"));

        result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertTrue((Boolean) result);

        Bundle testBundle =
                new TransactionBundleBuilder()
                        .buildId(id)
                        .buildEntry(baseUrl + patientRef, patient, null)
//                        .buildEntry(baseUrl + "MedicationRequest/" + prefetchId, requests.get(1), null)
//                        .buildEntry(baseUrl + "Observation/" + prefetchId, dynamicPrefetchScreening, null)
//                        .buildEntry(baseUrl + prefetchEncounterRef, dynamicPrefetchEncounter, null)
                        .buildEntry(baseUrl + contextEncounterRef, dynamicContextEncounter, null)
                        .build();
        TestData.writeBundleToFile("bundle-" + id + ".xml", testBundle);

        Map<String, Resource> prefetch = new LinkedHashMap<>();
        prefetch.put("item1", new Bundle().setType(Bundle.BundleType.SEARCHSET)
                .addEntry(new Bundle.BundleEntryComponent().setResource(requests.get(1)))
                .addEntry(new Bundle.BundleEntryComponent().setResource(requests.get(2)))
        );
        prefetch.put("item2", dynamicPrefetchEncounter);
        prefetch.put("item3", dynamicPrefetchScreening);
        // these items are needed to satisfy dynamic prefetch validation
        prefetch.put("item4", patient); prefetch.put("item5", null); prefetch.put("item6", null); prefetch.put("item7", null);
        prefetch.put("item8", null); prefetch.put("item9", null); prefetch.put("item10", null); prefetch.put("item11", null);
        prefetch.put("item12", null); prefetch.put("item13", null); prefetch.put("item14", null); prefetch.put("item15", null);
        TestData.writeCdsRequestToFile(
                "request-" + id + ".json",
                patientRef, contextEncounterRef,
                requests.get(0), prefetch
        );
    }

    @Test
    public void TestRecommendation10_NotMissingPrescribedOpioids() throws IOException, JAXBException {
        String id = "example-rec-10-not-missing-prescribed-opioids";
        String contextId = id + "-context";
        String prefetchId = id + "-prefetch";
        String patientRef = "Patient/" + id;
        String contextEncounterRef = "Encounter/" + id + "-context";
        String prefetchEncounterRef = "Encounter/" + id + "-prefetch";

        Context context = getStu3Context("OpioidCDS_STU3_REC_10.xml");
        context.enterContext("Patient");
        context.setContextValue(context.getCurrentContext(), id);

        Patient patient = TestData.getPatient(id, "female", "1982-01-07");
        client.update().resource(patient).execute();

        Encounter contextEncounter = TestData.getEncounter(contextId, patientRef, null);
        client.update().resource(contextEncounter).execute();
        Encounter dynamicContextEncounter = TestData.getEncounterWithExtension(contextId, patientRef, "Today()");

        String todayMinusNinetyDays = LocalDate.now().minusDays(90L).toString();
        Encounter prefetchEncounter = TestData.getEncounter(prefetchId, patientRef, todayMinusNinetyDays);
        client.update().resource(prefetchEncounter).execute();
        Encounter dynamicPrefetchEncounter = TestData.getEncounterWithExtension(prefetchId, patientRef, "Today() - 90 days");

        List<MedicationRequest> requests = loadPrescriptions(context, contextId, prefetchId, patientRef, contextEncounterRef, prefetchEncounterRef, false);

        // Not missing prescribed opioids case
        String todayMinusFourWeeks = LocalDate.now().minusWeeks(4L).toString();
        Observation prefetchScreening = TestData.getObservation(
                prefetchId, "10998-3", "Oxycodone [Presence] in Urine", patientRef, todayMinusFourWeeks
        );
        client.update().resource(prefetchScreening).execute();
        Observation dynamicPrefetchScreening = TestData.getObservationWithExtension(
                prefetchId, "10998-3", "Oxycodone [Presence] in Urine", patientRef, "Today() - 4 weeks"
        );

        Object result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertFalse((Boolean) result);

        Bundle testBundle =
                new TransactionBundleBuilder()
                        .buildId(id)
                        .buildEntry(baseUrl + patientRef, patient, null)
//                        .buildEntry(baseUrl + "MedicationRequest/" + prefetchId, requests.get(1), null)
//                        .buildEntry(baseUrl + "Observation/" + prefetchId, dynamicPrefetchScreening, null)
//                        .buildEntry(baseUrl + prefetchEncounterRef, dynamicPrefetchEncounter, null)
                        .buildEntry(baseUrl + contextEncounterRef, dynamicContextEncounter, null)
                        .build();
        TestData.writeBundleToFile("bundle-" + id + ".xml", testBundle);

        Map<String, Resource> prefetch = new LinkedHashMap<>();
        prefetch.put("item1", requests.get(1));
        prefetch.put("item2", dynamicPrefetchEncounter);
        prefetch.put("item3", dynamicPrefetchScreening);
        // these items are needed to satisfy dynamic prefetch validation
        prefetch.put("item4", patient); prefetch.put("item5", null); prefetch.put("item6", null); prefetch.put("item7", null);
        prefetch.put("item8", null); prefetch.put("item9", null); prefetch.put("item10", null); prefetch.put("item11", null);
        prefetch.put("item12", null); prefetch.put("item13", null); prefetch.put("item14", null); prefetch.put("item15", null);
        TestData.writeCdsRequestToFile(
                "request-" + id + ".json",
                patientRef, contextEncounterRef,
                requests.get(0), prefetch
        );
    }

    @Test
    public void TestRecommendation10_UnprescribedOpioids() throws IOException, JAXBException {
        String id = "example-rec-10-unprescribed-opioids";
        String contextId = id + "-context";
        String prefetchId = id + "-prefetch";
        String patientRef = "Patient/" + id;
        String contextEncounterRef = "Encounter/" + id + "-context";
        String prefetchEncounterRef = "Encounter/" + id + "-prefetch";

        Context context = getStu3Context("OpioidCDS_STU3_REC_10.xml");
        context.enterContext("Patient");
        context.setContextValue(context.getCurrentContext(), id);

        Patient patient = TestData.getPatient(id, "female", "1982-01-07");
        client.update().resource(patient).execute();

        Encounter contextEncounter = TestData.getEncounter(contextId, patientRef, null);
        client.update().resource(contextEncounter).execute();
        Encounter dynamicContextEncounter = TestData.getEncounterWithExtension(contextId, patientRef, "Today()");

        String todayMinusNinetyDays = LocalDate.now().minusDays(90L).toString();
        Encounter prefetchEncounter = TestData.getEncounter(prefetchId, patientRef, todayMinusNinetyDays);
        client.update().resource(prefetchEncounter).execute();
        Encounter dynamicPrefetchEncounter = TestData.getEncounterWithExtension(prefetchId, patientRef, "Today() - 90 days");

        List<MedicationRequest> requests = loadPrescriptions(context, contextId, prefetchId, patientRef, contextEncounterRef, prefetchEncounterRef, false);

        // Unprescribed opioids case
        String todayMinusThreeWeeks = LocalDate.now().minusWeeks(3L).toString();
        Observation prefetchCodeineScreening = TestData.getObservation(
                prefetchId + "-codeine", "3507-1", "Codeine [Presence] in Urine", patientRef, todayMinusThreeWeeks
        );
        client.update().resource(prefetchCodeineScreening).execute();
        Observation dynamicPrefetchCodeineScreening = TestData.getObservationWithExtension(
                prefetchId+ "-codeine", "3507-1", "Codeine [Presence] in Urine", patientRef, "Today() - 3 weeks"
        );

        String todayMinusFourWeeks = LocalDate.now().minusWeeks(4L).toString();
        Observation prefetchOxycodoneScreening = TestData.getObservation(
                prefetchId + "-oxycodone", "10998-3", "Oxycodone [Presence] in Urine", patientRef, todayMinusFourWeeks
        );
        client.update().resource(prefetchOxycodoneScreening).execute();
        Observation dynamicPrefetchOxycodoneScreening = TestData.getObservationWithExtension(
                prefetchId+ "-oxycodone", "10998-3", "Oxycodone [Presence] in Urine", patientRef, "Today() - 4 weeks"
        );

        Object result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertTrue((Boolean) result);

        result = context.resolveExpressionRef("Has Unprescribed Opioids?").getExpression().evaluate(context);
        Assert.assertTrue((Boolean) result);

        Bundle testBundle =
                new TransactionBundleBuilder()
                        .buildId(id)
                        .buildEntry(baseUrl + patientRef, patient, null)
//                        .buildEntry(baseUrl + "MedicationRequest/" + prefetchId, requests.get(1), null)
//                        .buildEntry(baseUrl + "Observation/" + prefetchId + "-codeine", dynamicPrefetchCodeineScreening, null)
//                        .buildEntry(baseUrl + "Observation/" + prefetchId + "-oxycodone", dynamicPrefetchOxycodoneScreening, null)
//                        .buildEntry(baseUrl + prefetchEncounterRef, dynamicPrefetchEncounter, null)
                        .buildEntry(baseUrl + contextEncounterRef, dynamicContextEncounter, null)
                        .build();
        TestData.writeBundleToFile("bundle-" + id + ".xml", testBundle);

        Map<String, Resource> prefetch = new LinkedHashMap<>();
        prefetch.put("item1", requests.get(1));
        prefetch.put("item2", dynamicPrefetchEncounter);
        prefetch.put(
                "item3",
                new Bundle()
                        .setType(Bundle.BundleType.SEARCHSET)
                        .addEntry(new Bundle.BundleEntryComponent().setResource(dynamicPrefetchCodeineScreening))
                        .addEntry(new Bundle.BundleEntryComponent().setResource(dynamicPrefetchOxycodoneScreening))
        );
        // these items are needed to satisfy dynamic prefetch validation
        prefetch.put("item4", patient); prefetch.put("item5", null); prefetch.put("item6", null); prefetch.put("item7", null);
        prefetch.put("item8", null); prefetch.put("item9", null); prefetch.put("item10", null); prefetch.put("item11", null);
        prefetch.put("item12", null); prefetch.put("item13", null); prefetch.put("item14", null); prefetch.put("item15", null);
        TestData.writeCdsRequestToFile(
                "request-" + id + ".json",
                patientRef, contextEncounterRef,
                requests.get(0), prefetch
        );
    }

    @Test
    public void TestRecommendation10_IllicitDrugs() throws IOException, JAXBException {
        String id = "example-rec-10-illicit-drugs";
        String contextId = id + "-context";
        String prefetchId = id + "-prefetch";
        String patientRef = "Patient/" + id;
        String contextEncounterRef = "Encounter/" + id + "-context";
        String prefetchEncounterRef = "Encounter/" + id + "-prefetch";

        Context context = getStu3Context("OpioidCDS_STU3_REC_10.xml");
        context.enterContext("Patient");
        context.setContextValue(context.getCurrentContext(), id);

        Patient patient = TestData.getPatient(id, "female", "1982-01-07");
        client.update().resource(patient).execute();

        Encounter contextEncounter = TestData.getEncounter(contextId, patientRef, null);
        client.update().resource(contextEncounter).execute();
        Encounter dynamicContextEncounter = TestData.getEncounterWithExtension(contextId, patientRef, "Today()");

        String todayMinusNinetyDays = LocalDate.now().minusDays(90L).toString();
        Encounter prefetchEncounter = TestData.getEncounter(prefetchId, patientRef, todayMinusNinetyDays);
        client.update().resource(prefetchEncounter).execute();
        Encounter dynamicPrefetchEncounter = TestData.getEncounterWithExtension(prefetchId, patientRef, "Today() - 90 days");

        List<MedicationRequest> requests = loadPrescriptions(context, contextId, prefetchId, patientRef, contextEncounterRef, prefetchEncounterRef, false);

        // Illicit drug case
        String todayMinusFourWeeks = LocalDate.now().minusWeeks(4L).toString();
        Observation prefetchScreening = TestData.getObservation(
                prefetchId, "3426-4", "Tetrahydrocannabinol [Presence] in Urine", patientRef, todayMinusFourWeeks
        );
        client.update().resource(prefetchScreening).execute();
        Observation dynamicPrefetchScreening = TestData.getObservationWithExtension(
                prefetchId, "3426-4", "Tetrahydrocannabinol [Presence] in Urine", patientRef, "Today() - 4 weeks"
        );

        Object result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertTrue((Boolean) result);

        result = context.resolveExpressionRef("Has Illicit Drugs in Screening?").getExpression().evaluate(context);
        Assert.assertTrue((Boolean) result);

        result = context.resolveExpressionRef("Inconsistent Illicit Drugs").getExpression().evaluate(context);
        Assert.assertTrue(result.equals("Found the following illicit drug(s) in urine drug screen: Tetrahydrocannabinol"));

        Bundle testBundle =
                new TransactionBundleBuilder()
                        .buildId(id)
                        .buildEntry(baseUrl + patientRef, patient, null)
//                        .buildEntry(baseUrl + "MedicationRequest/" + prefetchId, requests.get(1), null)
//                        .buildEntry(baseUrl + "Observation/" + prefetchId, dynamicPrefetchScreening, null)
//                        .buildEntry(baseUrl + prefetchEncounterRef, dynamicPrefetchEncounter, null)
                        .buildEntry(baseUrl + contextEncounterRef, dynamicContextEncounter, null)
                        .build();
        TestData.writeBundleToFile("bundle-" + id + ".xml", testBundle);

        Map<String, Resource> prefetch = new LinkedHashMap<>();
        prefetch.put("item1", requests.get(1));
        prefetch.put("item2", dynamicPrefetchEncounter);
        prefetch.put("item3", dynamicPrefetchScreening);
        // these items are needed to satisfy dynamic prefetch validation
        prefetch.put("item4", patient); prefetch.put("item5", null); prefetch.put("item6", null); prefetch.put("item7", null);
        prefetch.put("item8", null); prefetch.put("item9", null); prefetch.put("item10", null); prefetch.put("item11", null);
        prefetch.put("item12", null); prefetch.put("item13", null); prefetch.put("item14", null); prefetch.put("item15", null);
        TestData.writeCdsRequestToFile(
                "request-" + id + ".json",
                patientRef, contextEncounterRef,
                requests.get(0), prefetch
        );
    }

    @Test
    public void TestRecommendation10_EndOfLifeMedicationExclusion() throws IOException, JAXBException {
        String id = "example-rec-10-end-of-life-med-exclusion";
        String contextId = id + "-context";
        String patientRef = "Patient/" + id;
        String contextEncounterRef = "Encounter/" + id + "-context";

        Context context = getStu3Context("OpioidCDS_STU3_REC_10.xml");
        context.enterContext("Patient");
        context.setContextValue(context.getCurrentContext(), id);

        Patient patient = TestData.getPatient(id, "female", "1982-01-07");
        client.update().resource(patient).execute();

        Encounter contextEncounter = TestData.getEncounter(contextId, patientRef, null);
        client.update().resource(contextEncounter).execute();
        Encounter dynamicContextEncounter = TestData.getEncounterWithExtension(contextId, patientRef, "Today()");

        // End of life medication exclusion case
        String todayPlusThreeMonths = LocalDate.now().plusMonths(3L).toString();
        MedicationRequest contextPrescription = TestData.getMedicationRequest(
                1, 12.0, 5.0, 3, 30.0, contextId, "1012727",
                "Carbinoxamine maleate 0.4 MG/ML / Hydrocodone Bitartrate 1 MG/ML / Pseudoephedrine Hydrochloride 6 MG/ML Oral Solution",
                patientRef, contextEncounterRef, null, "d", "mL", null, todayPlusThreeMonths, "d"
        );
        context.setParameter(null, "ContextPrescriptions", Collections.singletonList(contextPrescription));
        MedicationRequest dynamicContextPrescription = TestData.getMedicationRequestWithExtensions(
                1, 12.0, 5.0, 3, 30.0, contextId, "1012727",
                "Carbinoxamine maleate 0.4 MG/ML / Hydrocodone Bitartrate 1 MG/ML / Pseudoephedrine Hydrochloride 6 MG/ML Oral Solution",
                patientRef, contextEncounterRef, "Today()", "d", "mL", "Today() + 3 months", "d"
        );

        Object result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertFalse((Boolean) result);

        result = context.resolveExpressionRef("Exclusion Criteria").getExpression().evaluate(context);
        Assert.assertTrue((Boolean) result);

        Bundle testBundle =
                new TransactionBundleBuilder()
                        .buildId(id)
                        .buildEntry(baseUrl + patientRef, patient, null)
                        .buildEntry(baseUrl + contextEncounterRef, dynamicContextEncounter, null)
                        .build();
        TestData.writeBundleToFile("bundle-" + id + ".xml", testBundle);

        Map<String, Resource> prefetch = new LinkedHashMap<>();
        prefetch.put("item1", null);
        prefetch.put("item2", null);
        prefetch.put("item3", null);
        // these items are needed to satisfy dynamic prefetch validation
        prefetch.put("item4", patient); prefetch.put("item5", null); prefetch.put("item6", null); prefetch.put("item7", null);
        prefetch.put("item8", null); prefetch.put("item9", null); prefetch.put("item10", null); prefetch.put("item11", null);
        prefetch.put("item12", null); prefetch.put("item13", null); prefetch.put("item14", null); prefetch.put("item15", null);
        TestData.writeCdsRequestToFile(
                "request-" + id + ".json",
                patientRef, contextEncounterRef,
                dynamicContextPrescription, prefetch
        );
    }

    @Test
    public void TestRecommendation11_BenzodiazepineTriggerWithOpioid() throws IOException, JAXBException {
        String id = "example-rec-11-benzo-trigger-with-opioid";
        String contextId = id + "-context";
        String prefetchId = id + "-prefetch";
        String patientRef = "Patient/" + id;
        String contextEncounterRef = "Encounter/" + id + "-context";
        String prefetchEncounterRef = "Encounter/" + id + "-prefetch";

        Context context = getStu3Context("OpioidCDS_STU3_REC_11.xml");
        context.enterContext("Patient");
        context.setContextValue(context.getCurrentContext(), id);

        Patient patient = TestData.getPatient(id, "female", "1982-01-07");
        client.update().resource(patient).execute();

        Encounter contextEncounter = TestData.getEncounter(contextId, patientRef, null);
        client.update().resource(contextEncounter).execute();
        Encounter dynamicContextEncounter = TestData.getEncounterWithExtension(contextId, patientRef, "Today()");

        String todayMinusFourWeeks = LocalDate.now().minusWeeks(4L).toString();
        Encounter prefetchEncounter = TestData.getEncounter(prefetchId, patientRef, todayMinusFourWeeks);
        client.update().resource(prefetchEncounter).execute();
        Encounter dynamicPrefetchEncounter = TestData.getEncounterWithExtension(prefetchId, patientRef, "Today() - 28 days");

        // Benzodiazepine trigger with past opioid with abuse potential order
        String todayPlusThreeMonths = LocalDate.now().plusMonths(3L).toString();
        MedicationRequest contextPrescription = TestData.getMedicationRequest(
                1, 1.0, 1.0, 3, 30.0, contextId, "1298088",
                "Flurazepam Hydrochloride 15 MG Oral Capsule", patientRef, contextEncounterRef,
                null, "d", "capsule", null, todayPlusThreeMonths, "d"
        );
        context.setParameter(null, "ContextPrescriptions", Collections.singletonList(contextPrescription));
        MedicationRequest dynamicContextPrescription = TestData.getMedicationRequestWithExtensions(
                1, 1.0, 1.0, 3, 30.0, contextId, "1298088",
                "Flurazepam Hydrochloride 15 MG Oral Capsule", patientRef, contextEncounterRef,
                "Today()", "d", "capsule", "Today() + 3 months", "d"
        );

        MedicationRequest prefetchPrescription = TestData.getMedicationRequest(
                1, 1.0, 1.0, 3, 30.0, prefetchId, "1049502",
                "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet", patientRef, prefetchEncounterRef,
                todayMinusFourWeeks, "d", "tablet", todayMinusFourWeeks, todayPlusThreeMonths, "d"
        );
        client.update().resource(prefetchPrescription).execute();
        MedicationRequest dynamicPrefetchPrescription = TestData.getMedicationRequestWithExtensions(
                1, 1.0, 1.0, 3, 30.0, prefetchId, "1049502",
                "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet", patientRef, prefetchEncounterRef,
                "Today() - 28 days", "d", "tablet", "Today() + 3 months", "d"
        );

        Object result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertTrue((Boolean) result);

        result = context.resolveExpressionRef("Get Detail").getExpression().evaluate(context);
        Assert.assertTrue(result.equals("The benzodiazepine prescription request is concurrent with an active opioid prescription"));

        Bundle testBundle =
                new TransactionBundleBuilder()
                        .buildId(id)
                        .buildEntry(baseUrl + patientRef, patient, null)
//                        .buildEntry(baseUrl + "MedicationRequest/" + prefetchId, dynamicPrefetchPrescription, null)
//                        .buildEntry(baseUrl + prefetchEncounterRef, dynamicPrefetchEncounter, null)
                        .buildEntry(baseUrl + contextEncounterRef, dynamicContextEncounter, null)
                        .build();
        TestData.writeBundleToFile("bundle-" + id + ".xml", testBundle);

        Map<String, Resource> prefetch = new LinkedHashMap<>();
        prefetch.put("item1", dynamicPrefetchPrescription);
        prefetch.put("item2", dynamicPrefetchEncounter);
        prefetch.put("item3", patient);
        // these items are needed to satisfy dynamic prefetch validation
        prefetch.put("item4", null); prefetch.put("item5", null); prefetch.put("item6", null);
        prefetch.put("item7", null); prefetch.put("item8", null); prefetch.put("item9", null); prefetch.put("item10", null);
        prefetch.put("item11", null); prefetch.put("item12", null); prefetch.put("item13", null); prefetch.put("item14", null);
        TestData.writeCdsRequestToFile(
                "request-" + id + ".json",
                patientRef, contextEncounterRef,
                dynamicContextPrescription, prefetch
        );
    }

    @Test
    public void TestRecommendation11_OpioidTriggerWithBenzodiazepine() throws IOException, JAXBException {
        String id = "example-rec-11-opioid-trigger-with-benzo";
        String contextId = id + "-context";
        String prefetchId = id + "-prefetch";
        String patientRef = "Patient/" + id;
        String contextEncounterRef = "Encounter/" + id + "-context";
        String prefetchEncounterRef = "Encounter/" + id + "-prefetch";

        Context context = getStu3Context("OpioidCDS_STU3_REC_11.xml");
        context.enterContext("Patient");
        context.setContextValue(context.getCurrentContext(), id);

        Patient patient = TestData.getPatient(id, "female", "1982-01-07");
        client.update().resource(patient).execute();

        Encounter contextEncounter = TestData.getEncounter(contextId, patientRef, null);
        client.update().resource(contextEncounter).execute();
        Encounter dynamicContextEncounter = TestData.getEncounterWithExtension(contextId, patientRef, "Today()");

        String todayMinusFourWeeks = LocalDate.now().minusWeeks(4L).toString();
        Encounter prefetchEncounter = TestData.getEncounter(prefetchId, patientRef, todayMinusFourWeeks);
        client.update().resource(prefetchEncounter).execute();
        Encounter dynamicPrefetchEncounter = TestData.getEncounterWithExtension(prefetchId, patientRef, "Today() - 28 days");

        // Opioid with abuse potential trigger with past benzodiazepine order
        String todayPlusThreeMonths = LocalDate.now().plusMonths(3L).toString();
        MedicationRequest contextPrescription = TestData.getMedicationRequest(
                1, 1.0, 1.0, 3, 30.0, contextId, "1049502",
                "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet", patientRef, contextEncounterRef,
                null, "d", "tablet", null, todayPlusThreeMonths, "d"
        );
        context.setParameter(null, "ContextPrescriptions", Collections.singletonList(contextPrescription));
        MedicationRequest dynamicContextPrescription = TestData.getMedicationRequestWithExtensions(
                1, 1.0, 1.0, 3, 30.0, contextId, "1049502",
                "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet", contextId, contextEncounterRef,
                "Today()", "d", "tablet", "Today() + 3 months", "d"
        );

        MedicationRequest prefetchPrescription = TestData.getMedicationRequest(
                1, 1.0, 1.0, 3, 30.0, prefetchId, "1298088",
                "Flurazepam Hydrochloride 15 MG Oral Capsule", patientRef, prefetchEncounterRef,
                todayMinusFourWeeks, "d", "capsule", todayMinusFourWeeks, todayPlusThreeMonths, "d"
        );
        client.update().resource(prefetchPrescription).execute();
        MedicationRequest dynamicPrefetchPrescription = TestData.getMedicationRequestWithExtensions(
                1, 1.0, 1.0, 3, 30.0, prefetchId, "1298088",
                "Flurazepam Hydrochloride 15 MG Oral Capsule", patientRef, prefetchEncounterRef,
                "Today() - 28 days", "d", "capsule", "Today() + 3 months", "d"
        );

        Object result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertTrue((Boolean) result);

        result = context.resolveExpressionRef("Get Detail").getExpression().evaluate(context);
        Assert.assertTrue(result.equals("The opioid prescription request is concurrent with an active benzodiazepine prescription"));

        Bundle testBundle =
                new TransactionBundleBuilder()
                        .buildId(id)
                        .buildEntry(baseUrl + patientRef, patient, null)
//                        .buildEntry(baseUrl + "MedicationRequest/" + prefetchId, dynamicPrefetchPrescription, null)
//                        .buildEntry(baseUrl + prefetchEncounterRef, dynamicPrefetchEncounter, null)
                        .buildEntry(baseUrl + contextEncounterRef, dynamicContextEncounter, null)
                        .build();
        TestData.writeBundleToFile("bundle-" + id + ".xml", testBundle);

        Map<String, Resource> prefetch = new LinkedHashMap<>();
        prefetch.put("item1", dynamicPrefetchPrescription);
        prefetch.put("item2", dynamicPrefetchEncounter);
        prefetch.put("item3", patient);
        // these items are needed to satisfy dynamic prefetch validation
        prefetch.put("item4", null); prefetch.put("item5", null); prefetch.put("item6", null);
        prefetch.put("item7", null); prefetch.put("item8", null); prefetch.put("item9", null); prefetch.put("item10", null);
        prefetch.put("item11", null); prefetch.put("item12", null); prefetch.put("item13", null); prefetch.put("item14", null);
        TestData.writeCdsRequestToFile(
                "request-" + id + ".json",
                patientRef, contextEncounterRef,
                dynamicContextPrescription, prefetch
        );
    }

    @Test
    public void TestRecommendation11_BenzodiazepineTriggerWithoutOpioid() throws IOException, JAXBException {
        String id = "example-rec-11-benzo-trigger-without-opioid";
        String contextId = id + "-context";
        String patientRef = "Patient/" + id;
        String contextEncounterRef = "Encounter/" + id + "-context";

        Context context = getStu3Context("OpioidCDS_STU3_REC_11.xml");
        context.enterContext("Patient");
        context.setContextValue(context.getCurrentContext(), id);

        Patient patient = TestData.getPatient(id, "female", "1982-01-07");
        client.update().resource(patient).execute();

        Encounter contextEncounter = TestData.getEncounter(contextId, patientRef, null);
        client.update().resource(contextEncounter).execute();
        Encounter dynamicContextEncounter = TestData.getEncounterWithExtension(contextId, patientRef, "Today()");

        // Benzodiazepine trigger without past opioid with abuse potential order
        String todayPlusThreeMonths = LocalDate.now().plusMonths(3L).toString();
        MedicationRequest contextPrescription = TestData.getMedicationRequest(
                1, 1.0, 1.0, 3, 30.0, contextId, "1298088",
                "Flurazepam Hydrochloride 15 MG Oral Capsule", patientRef, contextEncounterRef,
                null, "d", "capsule", null, todayPlusThreeMonths, "d"
        );
        context.setParameter(null, "ContextPrescriptions", Collections.singletonList(contextPrescription));
        MedicationRequest dynamicContextPrescription = TestData.getMedicationRequestWithExtensions(
                1, 1.0, 1.0, 3, 30.0, contextId, "1298088",
                "Flurazepam Hydrochloride 15 MG Oral Capsule", patientRef, contextEncounterRef,
                "Today()", "d", "capsule", "Today() + 3 months", "d"
        );

        Object result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertFalse((Boolean) result);

        result = context.resolveExpressionRef("Opioid with Ambulatory Care Abuse Potential").getExpression().evaluate(context);
        Assert.assertFalse((Boolean) result);

        Bundle testBundle =
                new TransactionBundleBuilder()
                        .buildId(id)
                        .buildEntry(baseUrl + patientRef, patient, null)
                        .buildEntry(baseUrl + contextEncounterRef, dynamicContextEncounter, null)
                        .build();
        TestData.writeBundleToFile("bundle-" + id + ".xml", testBundle);

        Map<String, Resource> prefetch = new LinkedHashMap<>();
        prefetch.put("item1", null);
        prefetch.put("item2", null);
        prefetch.put("item3", patient);
        // these items are needed to satisfy dynamic prefetch validation
        prefetch.put("item4", null); prefetch.put("item5", null); prefetch.put("item6", null);
        prefetch.put("item7", null); prefetch.put("item8", null); prefetch.put("item9", null); prefetch.put("item10", null);
        prefetch.put("item11", null); prefetch.put("item12", null); prefetch.put("item13", null); prefetch.put("item14", null);
        TestData.writeCdsRequestToFile(
                "request-" + id + ".json",
                patientRef, contextEncounterRef,
                dynamicContextPrescription, prefetch
        );
    }

    @Test
    public void TestRecommendation11_OpioidTriggerWithoutBenzodiazepine() throws IOException, JAXBException {
        String id = "example-rec-11-opioid-trigger-without-benzo";
        String contextId = id + "-context";
        String patientRef = "Patient/" + id;
        String contextEncounterRef = "Encounter/" + id + "-context";

        Context context = getStu3Context("OpioidCDS_STU3_REC_11.xml");
        context.enterContext("Patient");
        context.setContextValue(context.getCurrentContext(), id);

        Patient patient = TestData.getPatient(id, "female", "1982-01-07");
        client.update().resource(patient).execute();

        Encounter contextEncounter = TestData.getEncounter(contextId, patientRef, null);
        client.update().resource(contextEncounter).execute();
        Encounter dynamicContextEncounter = TestData.getEncounterWithExtension(contextId, patientRef, "Today()");

        // Opioid without abuse potential trigger without past benzodiazepine order
        String todayPlusThreeMonths = LocalDate.now().plusMonths(3L).toString();
        MedicationRequest contextPrescription = TestData.getMedicationRequest(
                1, 1.0, 1.0, 3, 30.0, contextId, "1049502",
                "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet", patientRef, contextEncounterRef,
                null, "d", "tablet", null, todayPlusThreeMonths, "d"
        );
        context.setParameter(null, "ContextPrescriptions", Collections.singletonList(contextPrescription));
        MedicationRequest dynamicContextPrescription = TestData.getMedicationRequestWithExtensions(
                1, 1.0, 1.0, 3, 30.0, contextId, "1049502",
                "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet", contextId, contextEncounterRef,
                "Today()", "d", "tablet", "Today() + 3 months", "d"
        );

        Object result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertFalse((Boolean) result);

        result = context.resolveExpressionRef("On Benzodiazepine").getExpression().evaluate(context);
        Assert.assertFalse((Boolean) result);

        Bundle testBundle =
                new TransactionBundleBuilder()
                        .buildId(id)
                        .buildEntry(baseUrl + patientRef, patient, null)
                        .buildEntry(baseUrl + contextEncounterRef, dynamicContextEncounter, null)
                        .build();
        TestData.writeBundleToFile("bundle-" + id + ".xml", testBundle);

        Map<String, Resource> prefetch = new LinkedHashMap<>();
        prefetch.put("item1", null);
        prefetch.put("item2", null);
        prefetch.put("item3", patient);
        // these items are needed to satisfy dynamic prefetch validation
        prefetch.put("item4", null); prefetch.put("item5", null); prefetch.put("item6", null);
        prefetch.put("item7", null); prefetch.put("item8", null); prefetch.put("item9", null); prefetch.put("item10", null);
        prefetch.put("item11", null); prefetch.put("item12", null); prefetch.put("item13", null); prefetch.put("item14", null);
        TestData.writeCdsRequestToFile(
                "request-" + id + ".json",
                patientRef, contextEncounterRef,
                dynamicContextPrescription, prefetch
        );
    }
}
