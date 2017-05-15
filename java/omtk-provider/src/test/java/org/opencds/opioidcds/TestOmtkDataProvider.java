package org.opencds.opioidcds;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.dstu2.resource.MedicationOrder;
import org.cqframework.cql.elm.execution.Library;
import org.cqframework.cql.elm.execution.VersionedIdentifier;
import org.hl7.fhir.dstu3.model.MedicationRequest;
import org.junit.Test;
import org.opencds.cqf.cql.data.fhir.FhirDataProvider;
import org.opencds.cqf.cql.data.fhir.FhirDataProviderDstu2;
import org.opencds.cqf.cql.execution.Context;
import org.opencds.cqf.cql.execution.CqlLibraryReader;
import org.opencds.cqf.cql.execution.LibraryLoader;

import javax.xml.bind.JAXBException;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Bryn on 4/24/2017.
 */
public class TestOmtkDataProvider {

    private String pathToDB = Paths.get("src/test/resources/org/opencds/opioidcds/OpioidManagementTerminologyKnowledge.db").toAbsolutePath().toString();

    @Test
    public void testBasicAccess() {
        OmtkDataProvider provider = new OmtkDataProvider("jdbc:sqlite://" + pathToDB);
        Iterable<Object> result = provider.retrieve(null, null, "MED_INGREDIENT", null, null, null, null, null, null, null, null);
        for (Object row : result) {
            OmtkRow omtkRow = (OmtkRow)row;
            String rowString = String.format("INGREDIENT_RXCUI: %s, INGREDIENT_NAME: %s, MANUALLY_ENTERED: %s, UPDATE_DTM: %s",
                    omtkRow.getValue("INGREDIENT_RXCUI"), omtkRow.getValue("INGREDIENT_NAME"),
                    omtkRow.getValue("MANUALLY_ENTERED"), omtkRow.getValue("UPDATE_DTM"));
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

    private Context setupDSTU2() throws IOException, JAXBException {
        java.io.InputStream input = TestOmtkDataProvider.class.getResourceAsStream("OpioidCDS_DSTU2-0.1.0.xml");
        Library library = CqlLibraryReader.read(input);
        Context context = new Context(library);
        context.registerLibraryLoader(new TestLibraryLoader());
        OmtkDataProvider omtkDataProvider = new OmtkDataProvider("jdbc:sqlite://" + pathToDB);
        context.registerDataProvider("http://org.opencds/opioid-cds", omtkDataProvider);
        FhirDataProviderDstu2 fhirDataProvider = new FhirDataProviderDstu2().withPackageName("ca.uhn.fhir.model.dstu2.resource");
        context.registerDataProvider("http://hl7.org/fhir", fhirDataProvider);
        FhirDataProviderDstu2 primitivefhirDataProvider = new FhirDataProviderDstu2().withPackageName("ca.uhn.fhir.model.primitive");
        context.registerDataProvider("http://hl7.org/fhir", primitivefhirDataProvider);
        FhirDataProviderDstu2 compositefhirDataProvider = new FhirDataProviderDstu2().withPackageName("ca.uhn.fhir.model.dstu2.composite");
        context.registerDataProvider("http://hl7.org/fhir", compositefhirDataProvider);
        return context;
    }

    private Context setupStu3() throws IOException, JAXBException {
        java.io.InputStream input = TestOmtkDataProvider.class.getResourceAsStream("OpioidCDS_STU3-0.1.0.xml");
        Library library = CqlLibraryReader.read(input);
        Context context = new Context(library);
        context.registerLibraryLoader(new TestLibraryLoader());
        OmtkDataProvider omtkDataProvider = new OmtkDataProvider("jdbc:sqlite://" + pathToDB);
        context.registerDataProvider("http://org.opencds/opioid-cds", omtkDataProvider);
        FhirDataProvider fhirDataProvider = new FhirDataProvider();
        context.registerDataProvider("http://hl7.org/fhir", fhirDataProvider);
        context.setExpressionCaching(true);
        return context;
    }

    @Test
    public void testCdsOpioidLogic() throws IOException, JAXBException {
        Context context = setupDSTU2();
        Object result = context.resolveExpressionRef("IsMME50OrMore").getExpression().evaluate(context);
        if (result == null) {
            throw new RuntimeException("Test failed");
        }
    }

    private FhirContext dstu2Context;
    public FhirContext getDstu2Context() {
        if (dstu2Context == null) {
            dstu2Context = FhirContext.forDstu2();
        }
        return dstu2Context;
    }

    private FhirContext stu3Context;
    public FhirContext getStu3Context() {
        if (stu3Context == null) {
            stu3Context = FhirContext.forDstu3();
        }
        return stu3Context;
    }

    private List<MedicationOrder> loadDstu2MedOrders() {
        ArrayList<MedicationOrder> orders = new ArrayList<MedicationOrder>();
        ca.uhn.fhir.parser.IParser parser = getDstu2Context().newJsonParser();
        java.io.InputStream input = TestOmtkDataProvider.class.getResourceAsStream("medorder_buprenorphine_patch_draft.json");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
            MedicationOrder order = parser.parseResource(MedicationOrder.class, reader);
            orders.add(order);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return orders;
    }

    private List<MedicationRequest> loadStu3MedOrders() {
        ArrayList<MedicationRequest> orders = new ArrayList<MedicationRequest>();
        ca.uhn.fhir.parser.IParser parser = getStu3Context().newJsonParser();
        java.io.InputStream input = TestOmtkDataProvider.class.getResourceAsStream("medrequest_buprenorphine_patch_draft.json");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input))) {
            MedicationRequest order = parser.parseResource(MedicationRequest.class, reader);
            orders.add(order);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return orders;
    }

    @Test
    public void testCdsOpioidDstu2LogicWithContext() throws IOException, JAXBException {
        // Deserialize medorder_buprenorphine_patch_draft.json as a MedicationOrder and pass in a list as the Orders argument to the Context
        Context context = setupDSTU2();
        context.setParameter(null, "Orders", loadDstu2MedOrders());
        Object result = context.resolveExpressionRef("IsMME50OrMore").getExpression().evaluate(context);
        if (result == null) {
            throw new RuntimeException("Test failed");
        }
    }

    @Test
    public void testCdsOpioidStu3LogicWithContext() throws IOException, JAXBException {
        Context context = setupStu3();
        context.setParameter(null, "Orders", loadStu3MedOrders());
        Object result = context.resolveExpressionRef("IsMME50OrMore").getExpression().evaluate(context);
        if (result == null) {
            throw new RuntimeException("Test failed");
        }
    }

    class TestLibraryLoader implements LibraryLoader {

        @Override
        public Library load(VersionedIdentifier versionedIdentifier) {
            if (versionedIdentifier.getId().equals("OMTKLogic") && versionedIdentifier.getVersion().equals("0.1.0")) {
                java.io.InputStream input = TestOmtkDataProvider.class.getResourceAsStream("OMTKLogic-0.1.0.xml");
                try {
                    return CqlLibraryReader.read(input);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (JAXBException e) {
                    e.printStackTrace();
                }
            }
            else if (versionedIdentifier.getId().equals("FHIRHelpers") && versionedIdentifier.getVersion().equals("3.0.0")) {
                java.io.InputStream input = TestOmtkDataProvider.class.getResourceAsStream("FHIRHelpers-3.0.0.xml");
                try {
                    return CqlLibraryReader.read(input);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (JAXBException e) {
                    e.printStackTrace();
                }
            }
            else if (versionedIdentifier.getId().equals("FHIRHelpers") && versionedIdentifier.getVersion().equals("1.0.2")) {
                java.io.InputStream input = TestOmtkDataProvider.class.getResourceAsStream("FHIRHelpers-1.0.2.xml");
                try {
                    return CqlLibraryReader.read(input);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (JAXBException e) {
                    e.printStackTrace();
                }
            }

            throw new RuntimeException(String.format("Could not load library %s-%s.", versionedIdentifier.getId(), versionedIdentifier.getVersion()));
        }
    }
}
