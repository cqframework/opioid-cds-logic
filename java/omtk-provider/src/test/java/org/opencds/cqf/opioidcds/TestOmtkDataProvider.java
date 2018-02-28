package org.opencds.cqf.opioidcds;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.dstu2.resource.MedicationOrder;
import org.cqframework.cql.elm.execution.Library;
import org.cqframework.cql.elm.execution.VersionedIdentifier;
import org.hl7.fhir.dstu3.model.*;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.junit.Assert;
import org.junit.Test;
import org.opencds.cqf.cql.data.fhir.BaseFhirDataProvider;
import org.opencds.cqf.cql.data.fhir.FhirDataProviderDstu2;
import org.opencds.cqf.cql.data.fhir.FhirDataProviderStu3;
import org.opencds.cqf.cql.execution.Context;
import org.opencds.cqf.cql.execution.CqlLibraryReader;
import org.opencds.cqf.cql.execution.LibraryLoader;
import org.opencds.cqf.cql.runtime.Quantity;
import org.opencds.cqf.cql.terminology.fhir.FhirTerminologyProvider;

import javax.xml.bind.JAXBException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;

/**
 * Created by Bryn on 4/24/2017.
 */
public class TestOmtkDataProvider {

    private String pathToDB = Paths.get("src/test/resources/org/opencds/cqf/opioidcds/LocalDataStore_RxNav_OpioidCds.db").toAbsolutePath().toString();

    @Test
    public void testBasicAccess() {
        OmtkDataProvider provider = new OmtkDataProvider("jdbc:sqlite://" + pathToDB);
        Iterable<Object> result = provider.retrieve(null, null, "MED_INGREDIENT", null, null, null, null, null, null, null, null);
        for (Object row : result) {
            OmtkRow omtkRow = (OmtkRow)row;
            String rowString = String.format("INGREDIENT_RXCUI: %s, INGREDIENT_NAME: %s, USE_TO_POPULATE_DB: %s, SKIP: %s, UPDATE_DTM: %s",
                    omtkRow.getValue("INGREDIENT_RXCUI"), omtkRow.getValue("INGREDIENT_NAME"),
                    omtkRow.getValue("USE_TO_POPULATE_DB"), omtkRow.getValue("SKIP"), omtkRow.getValue("UPDATE_DTM"));
            System.out.println(rowString);
        }
    }

    @Test
    public void testOmtkLogic() throws IOException, JAXBException {
        java.io.InputStream input = TestOmtkDataProvider.class.getResourceAsStream("OMTKLogic-0.1.0.xml");
        org.cqframework.cql.elm.execution.Library library = CqlLibraryReader.read(input);
        Context context = new Context(library);
        OmtkDataProvider omtkDataProvider = new OmtkDataProvider("jdbc:sqlite://" + pathToDB);
        context.registerDataProvider("http://org.opencds/opioid-cds", omtkDataProvider);
        Object result = context.resolveExpressionRef("TestCalculateMMEs").getExpression().evaluate(context);
        if (result == null) {
            throw new RuntimeException("Test failed");
        }
    }

    private void loadTerminology(BaseFhirDataProvider fhirDataProvider) {
        InputStream is = TestOmtkDataProvider.class.getResourceAsStream("cdc-opioid-guidance-opioid-screening-valueset.json");
        Scanner scanner = new Scanner(is).useDelimiter("\\A");
        String json = scanner.hasNext() ? scanner.next() : "";
        IBaseResource resource = FhirContext.forDstu3().newJsonParser().parseResource(json);
        fhirDataProvider.getFhirClient().transaction().withBundle((Bundle) resource).execute();

        is = TestOmtkDataProvider.class.getResourceAsStream("opioidcds-terminology-bundle.json");
        scanner = new Scanner(is).useDelimiter("\\A");
        json = scanner.hasNext() ? scanner.next() : "";
        resource = FhirContext.forDstu3().newJsonParser().parseResource(json);
        fhirDataProvider.getFhirClient().transaction().withBundle((Bundle) resource).execute();
    }

    private Context setupStu3(String sourceFileName) throws IOException, JAXBException {
        java.io.InputStream input = TestOmtkDataProvider.class.getResourceAsStream(sourceFileName);
        Library library = CqlLibraryReader.read(input);
        Context context = new Context(library);
        context.registerLibraryLoader(new TestLibraryLoader());
        OmtkDataProvider omtkDataProvider = new OmtkDataProvider("jdbc:sqlite://" + pathToDB);
        context.registerDataProvider("http://org.opencds/opioid-cds", omtkDataProvider);
        BaseFhirDataProvider fhirDataProvider = new FhirDataProviderStu3().setEndpoint("http://measure.eval.kanvix.com/cqf-ruler/baseDstu3");
        context.registerDataProvider("http://hl7.org/fhir", fhirDataProvider);
        context.registerTerminologyProvider(new FhirTerminologyProvider().withEndpoint("http://measure.eval.kanvix.com/cqf-ruler/baseDstu3"));
        loadTerminology(fhirDataProvider);
        return context;
    }

    private FhirContext stu3Context;
    private FhirContext getStu3Context() {
        if (stu3Context == null) {
            stu3Context = FhirContext.forDstu3();
        }
        return stu3Context;
    }

    @Test
    public void testCdsOpioidStu3Recommendation04() throws IOException, JAXBException {
        Context context = setupStu3("OpioidCDS_STU3_REC_04.xml");

        MedicationRequest contextPrescription =
                new MedicationRequestBuilder()
                        .buildIntent("order")
                        .buildMedicationCodeableConcept("1049502", "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet")
                        .buildSubject("Patient/example-rec-04")
                        .buildAuthoredOn(null)
                        .build();
        context.setParameter(null, "ContextPrescription", contextPrescription);

        MedicationRequest orders =
                new MedicationRequestBuilder()
                        .buildIntent("order")
                        .buildMedicationCodeableConcept("1049502", "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet")
                        .buildSubject("Patient/example-rec-04")
                        .buildAuthoredOn("2017-10-25")
                        .build();
        context.setParameter(null, "Orders", Collections.singletonList(orders));

        String todayMinusTwoWeeks = LocalDate.now().minusWeeks(2L).toString();
        Encounter encounter =
                new EncounterBuilder()
                        .buildStatus("finished")
                        .buildSubject("Patient/example-rec-04")
                        .buildPeriod(todayMinusTwoWeeks, todayMinusTwoWeeks).build();
        context.setParameter(null, "Encounters", Collections.singletonList(encounter));

        Object result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertTrue((Boolean) result);

        // Missing encounter case
        context.setParameter(null, "Encounters", Collections.emptyList());
        result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertFalse((Boolean) result);

        // New Patient case
        Encounter newPatientEncounter =
                new EncounterBuilder()
                        .buildStatus("finished")
                        .buildSubject("Patient/example-rec-04")
                        .buildPeriod(null, null).build();
        context.setParameter(null, "Encounters", Collections.singletonList(newPatientEncounter));
        result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertFalse((Boolean) result);

        context.setParameter(null, "Encounters", Collections.singletonList(encounter));
        result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertTrue((Boolean) result);

        // Opioid has abuse potential, but not long acting case
        MedicationRequest contextPrescription2 =
                new MedicationRequestBuilder()
                        .buildIntent("order")
                        .buildMedicationCodeableConcept("1010600", "Buprenorphine 2 MG / Naloxone 0.5 MG Oral Strip")
                        .buildSubject("Patient/example-rec-04")
                        .buildAuthoredOn(null)
                        .build();
        context.setParameter(null, "ContextPrescription", contextPrescription2);
        result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertFalse((Boolean) result);

        context.setParameter(null, "ContextPrescription", contextPrescription);
        result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertTrue((Boolean) result);

        // Prescribed opioid with abuse potential in past 90 days case
        orders =
                new MedicationRequestBuilder()
                        .buildIntent("order")
                        .buildMedicationCodeableConcept("1049502", "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet")
                        .buildSubject("Patient/example-rec-04")
                        .buildAuthoredOn(todayMinusTwoWeeks)
                        .build();
        context.setParameter(null, "Orders", Collections.singletonList(orders));
        result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertFalse((Boolean) result);
    }

    @Test
    public void testCdsOpioidStu3Recommendation05() throws IOException, JAXBException {
        Context context = setupStu3("OpioidCDS_STU3_REC_05.xml");

        String todayMinusSixMonths = LocalDate.now().minusMonths(6L).toString();
        String todayPlusSixMonths = LocalDate.now().plusMonths(6L).toString();
        MedicationRequest contextPrescription =
                new MedicationRequestBuilder()
                        .buildIntent("order")
                        .buildCategory("outpatient")
                        .buildMedicationCodeableConcept("197696", "72 HR Fentanyl 0.075 MG/HR Transdermal System")
                        .buildSubject("Patient/example-rec-05")
                        .buildAuthoredOn(null)
                        .buildDosageInstruction(
                                new DosageBuilder().buildTiming(1, 3.0, "d")
                                        .buildDose(1.0, "patch")
                                        .buildAsNeeded(false)
                                        .build()
                        )
                        .buildDispenseRequest(todayMinusSixMonths, todayPlusSixMonths, 3, 30.0, "d")
                        .build();
        context.setParameter(null, "Orders", Collections.singletonList(contextPrescription));
        Object result = context.resolveExpressionRef("IsMME50OrMore").getExpression().evaluate(context);

        Assert.assertTrue((Boolean) result);
    }

    @Test
    public void testCdsOpioidStu3Recommendation07() throws IOException, JAXBException {
        Context context = setupStu3("OpioidCDS_STU3_REC_07.xml");

        // Minimal false case
        String todayMinusSixMonths = LocalDate.now().minusMonths(6L).toString();
        String todayPlusSixMonths = LocalDate.now().plusMonths(6L).toString();
        MedicationRequest contextPrescription =
                new MedicationRequestBuilder()
                        .buildIntent("order")
                        .buildCategory("outpatient")
                        .buildMedicationCodeableConcept("1049502", "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet")
                        .buildSubject("Patient/example-rec-07")
                        .buildAuthoredOn(null)
                        .buildDosageInstruction(
                                new DosageBuilder().buildTiming(3, 1.0, "d").build()
                        )
                        .buildDispenseRequest(todayMinusSixMonths, todayPlusSixMonths, 3, 30.0, "d")
                        .build();
        context.setParameter(null, "ContextPrescription", contextPrescription);
        context.setParameter(null, "Orders", Collections.singletonList(contextPrescription));

        Object result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertFalse((Boolean) result);

        // Minimal true case
        String todayMinusSixWeeks = LocalDate.now().minusWeeks(6L).toString();
        String todayPlusSixWeeks = LocalDate.now().plusWeeks(6L).toString();
        contextPrescription =
                new MedicationRequestBuilder()
                        .buildIntent("order")
                        .buildCategory("outpatient")
                        .buildMedicationCodeableConcept("1049502", "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet")
                        .buildSubject("Patient/example-rec-07")
                        .buildAuthoredOn(null)
                        .buildDosageInstruction(
                                new DosageBuilder().buildTiming(3, 1.0, "d").build()
                        )
                        .buildDispenseRequest(todayMinusSixWeeks, todayPlusSixWeeks, 3, 30.0, "d")
                        .build();
        context.setParameter(null, "ContextPrescription", contextPrescription);
        context.setParameter(null, "Orders", Collections.singletonList(contextPrescription));

        result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertTrue((Boolean) result);

        // Exclusion criteria end of life opioid case
        String todayMinusTwoWeeks = LocalDate.now().minusWeeks(2L).toString();
        MedicationRequest orders =
                new MedicationRequestBuilder()
                        .buildIntent("order")
                        .buildMedicationCodeableConcept("1012727", "Carbinoxamine maleate 0.4 MG/ML / Hydrocodone Bitartrate 1 MG/ML / Pseudoephedrine Hydrochloride 6 MG/ML Oral Solution")
                        .buildSubject("Patient/example-rec-07")
                        .buildAuthoredOn(todayMinusTwoWeeks)
                        .buildDosageInstruction(
                                new DosageBuilder().buildTiming(3, 1.0, "d").build()
                        )
                        .buildDispenseRequest(todayMinusSixWeeks, todayPlusSixWeeks, 3, 30.0, "d")
                        .build();
        context.setParameter(null, "Orders", Arrays.asList(contextPrescription, orders));

        result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertFalse((Boolean) result);

        // TODO - more tests for "Opioid Risk Assessment in Past 90 Days" and Exlusion Criteria EndOfLifeConditions
    }

    @Test
    public void testCdsOpioidStu3Recommendation08() throws IOException, JAXBException {
        Context context = setupStu3("OpioidCDS_STU3_REC_08.xml");

        // Minimal false case MME < 50 mg/d
        String todayMinusSixMonths = LocalDate.now().minusMonths(6L).toString();
        String todayPlusSixMonths = LocalDate.now().plusMonths(6L).toString();
        MedicationRequest contextPrescription =
                new MedicationRequestBuilder()
                        .buildIntent("order")
                        .buildCategory("outpatient")
                        .buildMedicationCodeableConcept("197696", "72 HR Fentanyl 0.075 MG/HR Transdermal System")
                        .buildSubject("Patient/example-rec-08")
                        .buildAuthoredOn(null)
                        .buildDosageInstruction(
                                new DosageBuilder().buildTiming(1, 12.0, "d")
                                        .buildDose(1.0, "patch")
                                        .buildAsNeeded(false)
                                        .build()
                        )
                        .buildDispenseRequest(todayMinusSixMonths, todayPlusSixMonths, 3, 30.0, "d")
                        .build();
        context.setParameter(null, "ContextPrescription", contextPrescription);
        context.setParameter(null, "Orders", Arrays.asList(contextPrescription, contextPrescription));

        Object result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertFalse((Boolean) result);

        // Minimal true case MME > 50 mg/d
        contextPrescription =
                new MedicationRequestBuilder()
                        .buildIntent("order")
                        .buildCategory("outpatient")
                        .buildMedicationCodeableConcept("197696", "72 HR Fentanyl 0.075 MG/HR Transdermal System")
                        .buildSubject("Patient/example-rec-08")
                        .buildAuthoredOn(null)
                        .buildDosageInstruction(
                                new DosageBuilder().buildTiming(1, 10.0, "d")
                                        .buildDose(1.0, "patch")
                                        .buildAsNeeded(false)
                                        .build()
                        )
                        .buildDispenseRequest(todayMinusSixMonths, todayPlusSixMonths, 3, 30.0, "d")
                        .build();
        context.setParameter(null, "ContextPrescription", contextPrescription);
        context.setParameter(null, "Orders", Arrays.asList(contextPrescription, contextPrescription));

        result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertTrue((Boolean) result);

        // Minimal true case on benzodiazepine
        contextPrescription =
                new MedicationRequestBuilder()
                        .buildIntent("order")
                        .buildCategory("outpatient")
                        .buildMedicationCodeableConcept("104693", "Temazepam 20 MG Oral Tablet")
                        .buildSubject("Patient/example-rec-08")
                        .buildAuthoredOn(null)
                        .buildDosageInstruction(
                                new DosageBuilder().buildTiming(1, 1.0, "d")
                                        .buildDose(1.0, "tablet")
                                        .buildAsNeeded(false)
                                        .build()
                        )
                        .buildDispenseRequest(todayMinusSixMonths, todayPlusSixMonths, 3, 30.0, "d")
                        .build();
        context.setParameter(null, "Orders", Arrays.asList(contextPrescription, contextPrescription));

        result = context.resolveExpressionRef("Average MME").getExpression().evaluate(context);
        Assert.assertTrue(((Quantity) result).getValue() == null);
        result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertTrue((Boolean) result);

        // Exclusion Criteria is on Naloxone
        MedicationRequest exclusionPrescription =
                new MedicationRequestBuilder()
                        .buildIntent("order")
                        .buildCategory("outpatient")
                        .buildMedicationCodeableConcept("1191212", "Naloxone Hydrochloride 0.02 MG/ML Injectable Solution")
                        .buildSubject("Patient/example-rec-84")
                        .buildAuthoredOn(null)
                        .buildDosageInstruction(
                                new DosageBuilder().buildTiming(1, 1.0, "d")
                                        .buildDose(1.0, "tablet")
                                        .buildAsNeeded(false)
                                        .build()
                        )
                        .buildDispenseRequest(todayMinusSixMonths, todayPlusSixMonths, 3, 30.0, "d")
                        .build();
        context.setParameter(null, "Orders", Collections.singletonList(exclusionPrescription));

        result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertFalse((Boolean) result);

        // TODO - additional test for substance abuse history condition
    }

    @Test
    public void testCdsOpioidStu3Recommendation10() throws IOException, JAXBException {
        Context context = setupStu3("OpioidCDS_STU3_REC_10.xml");

        // No urine screenings in past year case
        String todayMinusSixWeeks = LocalDate.now().minusWeeks(6L).toString();
        String todayPlusSixWeeks = LocalDate.now().plusWeeks(6L).toString();
        MedicationRequest contextPrescription =
                new MedicationRequestBuilder()
                        .buildIntent("order")
                        .buildCategory("outpatient")
                        .buildMedicationCodeableConcept("1049502", "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet")
                        .buildSubject("Patient/example-rec-10")
                        .buildAuthoredOn(todayMinusSixWeeks)
                        .buildDosageInstruction(
                                new DosageBuilder().buildTiming(3, 1.0, "d").build()
                        )
                        .buildDispenseRequest(todayMinusSixWeeks, todayPlusSixWeeks, 3, 30.0, "d")
                        .build();
        context.setParameter(null, "ContextPrescription", contextPrescription);
        context.setParameter(null, "Orders", Collections.singletonList(contextPrescription));

        Object result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertTrue((Boolean) result);

        result = context.resolveExpressionRef("No Urine Screening In Last 12 Months").getExpression().evaluate(context);
        Assert.assertTrue((Boolean) result);

        // Missing prescribed opioids case
        String todayMinusFourWeeks = LocalDate.now().minusWeeks(4L).toString();
        Observation atropineScreening =
                new ObservationBuilder()
                        .buildCode("14184-6")
                        .buildEffective(todayMinusFourWeeks)
                        .buildInterpretation("POS")
                        .build();

        context.setParameter(null, "ObservationsInPastYear", Collections.singletonList(atropineScreening));

        result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertTrue((Boolean) result);

        result = context.resolveExpressionRef("Has Missing Opioids?").getExpression().evaluate(context);
        Assert.assertTrue((Boolean) result);

        // Not missing prescribed opioids case
        Observation oxycodoneScreening =
                new ObservationBuilder()
                        .buildCode("10998-3")
                        .buildEffective(todayMinusFourWeeks)
                        .buildInterpretation("POS")
                        .build();

        context.setParameter(null, "ObservationsInPastYear", Collections.singletonList(oxycodoneScreening));

        result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertFalse((Boolean) result);

        // Unprescribed opioid case
        String todayMinusThreeWeeks = LocalDate.now().minusWeeks(3L).toString();
        Observation codeineWithOxycodoneScreening =
                new ObservationBuilder()
                        .buildCode("3507-1")
                        .buildEffective(todayMinusThreeWeeks)
                        .buildInterpretation("POS")
                        .buildComponent("10998-3", "POS")
                        .build();

        context.setParameter(null, "ObservationsInPastYear", Arrays.asList(oxycodoneScreening, codeineWithOxycodoneScreening));

        result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertTrue((Boolean) result);

        result = context.resolveExpressionRef("Has Unprescribed Opioids?").getExpression().evaluate(context);
        Assert.assertTrue((Boolean) result);

        // Illicit drug case
        Observation illicitDrugScreening =
                new ObservationBuilder()
                        .buildCode("3426-4")
                        .buildEffective(todayMinusThreeWeeks)
                        .buildInterpretation("POS")
                        .build();

        context.setParameter(null, "ObservationsInPastYear", Collections.singletonList(illicitDrugScreening));

        result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertTrue((Boolean) result);

        result = context.resolveExpressionRef("Has Illicit Drugs in Screening?").getExpression().evaluate(context);
        Assert.assertTrue((Boolean) result);

        illicitDrugScreening =
                new ObservationBuilder()
                        .buildCode("10998-3")
                        .buildEffective(todayMinusThreeWeeks)
                        .buildInterpretation("POS")
                        .buildComponent("3397-7", "POS")
                        .build();

        context.setParameter(null, "ObservationsInPastYear", Collections.singletonList(illicitDrugScreening));

        result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertTrue((Boolean) result);

        result = context.resolveExpressionRef("Has Illicit Drugs in Screening?").getExpression().evaluate(context);
        Assert.assertTrue((Boolean) result);

        // Exclusion criteria case
        contextPrescription =
                new MedicationRequestBuilder()
                        .buildIntent("order")
                        .buildCategory("outpatient")
                        .buildMedicationCodeableConcept("1012727", "Carbinoxamine maleate 0.4 MG/ML / Hydrocodone Bitartrate 1 MG/ML / Pseudoephedrine Hydrochloride 6 MG/ML Oral Solution")
                        .buildSubject("Patient/example-rec-10")
                        .buildAuthoredOn(todayMinusSixWeeks)
                        .buildDosageInstruction(
                                new DosageBuilder().buildTiming(3, 1.0, "d").build()
                        )
                        .buildDispenseRequest(todayMinusSixWeeks, todayPlusSixWeeks, 3, 30.0, "d")
                        .build();
        context.setParameter(null, "ContextPrescription", contextPrescription);
        context.setParameter(null, "Orders", Collections.singletonList(contextPrescription));

        result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertFalse((Boolean) result);

        result = context.resolveExpressionRef("Exclusion Criteria").getExpression().evaluate(context);
        Assert.assertTrue((Boolean) result);
    }

    @Test
    public void testCdsOpioidStu3Recommendation11() throws IOException, JAXBException {
        Context context = setupStu3("OpioidCDS_STU3_REC_11.xml");

        // Benzodiazepine trigger with past opioid with abuse potential order
        MedicationRequest contextPrescription =
                new MedicationRequestBuilder()
                        .buildIntent("order")
                        .buildMedicationCodeableConcept("1298088", "Flurazepam Hydrochloride 15 MG Oral Capsule")
                        .buildSubject("Patient/example-rec-11")
                        .buildAuthoredOn(null)
                        .build();
        context.setParameter(null, "ContextPrescription", contextPrescription);

        MedicationRequest orders =
                new MedicationRequestBuilder()
                        .buildIntent("order")
                        .buildMedicationCodeableConcept("1049502", "12 HR Oxycodone Hydrochloride 10 MG Extended Release Oral Tablet")
                        .buildSubject("Patient/example-rec-11")
                        .buildAuthoredOn("2017-10-25")
                        .build();
        context.setParameter(null, "Orders", Collections.singletonList(orders));

        Object result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertTrue((Boolean) result);

        // Opioid with abuse potential trigger with past benzodiazepine order
        context.setParameter(null, "ContextPrescription", orders);
        context.setParameter(null, "Orders", Collections.singletonList(contextPrescription));
        result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertTrue((Boolean) result);

        // Benzodiazepine trigger without past opioid with abuse potential order
        context.setParameter(null, "ContextPrescription", contextPrescription);
        context.setParameter(null, "Orders", Collections.singletonList(contextPrescription));
        result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertFalse((Boolean) result);

        // Opioid without abuse potential trigger without past benzodiazepine order
        context.setParameter(null, "ContextPrescription", orders);
        context.setParameter(null, "Orders", Collections.singletonList(orders));
        result = context.resolveExpressionRef("Inclusion Criteria").getExpression().evaluate(context);
        Assert.assertFalse((Boolean) result);
    }

//    @Test
//    public void testCdsOpioidStu3v18LogicWithContext() throws IOException, JAXBException {
//        Context context = setupStu3("OpioidCDS_STU3-0.1.0.xml");
//        context.setParameter(null, "Orders", loadStu3MedOrders("medreq_fentanyl_patch.json"));
//        Object result = context.resolveExpressionRef("IsMME50OrMore").getExpression().evaluate(context);
//        if (result == null) {
//            throw new RuntimeException("Test failed");
//        }
//    }

    class TestLibraryLoader implements LibraryLoader {

        @Override
        public Library load(VersionedIdentifier versionedIdentifier) {
            if (versionedIdentifier.getId().equals("OMTKLogic") && versionedIdentifier.getVersion().equals("0.1.0")) {
                java.io.InputStream input = TestOmtkDataProvider.class.getResourceAsStream("OMTKLogic-0.1.0.xml");
                try {
                    return CqlLibraryReader.read(input);
                } catch (IOException | JAXBException e) {
                    e.printStackTrace();
                }
            }
            else if (versionedIdentifier.getId().equals("OpioidCDS_STU3_Common") && versionedIdentifier.getVersion().equals("0.1.0")) {
                java.io.InputStream input = TestOmtkDataProvider.class.getResourceAsStream("OpioidCDS_STU3_Common.xml");
                try {
                    return CqlLibraryReader.read(input);
                } catch (IOException | JAXBException e) {
                    e.printStackTrace();
                }
            }
            else if (versionedIdentifier.getId().equals("FHIRHelpers") && versionedIdentifier.getVersion().equals("3.0.0")) {
                java.io.InputStream input = TestOmtkDataProvider.class.getResourceAsStream("FHIRHelpers-3.0.0.xml");
                try {
                    return CqlLibraryReader.read(input);
                } catch (IOException | JAXBException e) {
                    e.printStackTrace();
                }
            }
            else if (versionedIdentifier.getId().equals("FHIRHelpers") && versionedIdentifier.getVersion().equals("1.0.2")) {
                java.io.InputStream input = TestOmtkDataProvider.class.getResourceAsStream("FHIRHelpers-1.0.2.xml");
                try {
                    return CqlLibraryReader.read(input);
                } catch (IOException | JAXBException e) {
                    e.printStackTrace();
                }
            }

            throw new RuntimeException(String.format("Could not load library %s-%s.", versionedIdentifier.getId(), versionedIdentifier.getVersion()));
        }
    }
}
