package org.opencds.opioidcds;

import org.cqframework.cql.elm.execution.Library;
import org.cqframework.cql.elm.execution.VersionedIdentifier;
import org.junit.Test;
import org.opencds.cqf.cql.execution.Context;
import org.opencds.cqf.cql.execution.CqlLibraryReader;
import org.opencds.cqf.cql.execution.LibraryLoader;

import javax.xml.bind.JAXBException;
import java.io.IOException;

/**
 * Created by Bryn on 4/24/2017.
 */
public class TestOmtkDataProvider {

    @Test
    public void testBasicAccess() {
        OmtkDataProvider provider = new OmtkDataProvider("jdbc:ucanaccess://C:/Users/Christopher/Desktop/NewRepos/CDC_Opiod/OpioidManagementTerminologyKnowledge.accdb;memory=false;keepMirror=true");
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
        OmtkDataProvider omtkDataProvider = new OmtkDataProvider("jdbc:ucanaccess://C:/Users/Christopher/Desktop/NewRepos/CDC_Opiod/OpioidManagementTerminologyKnowledge.accdb;memory=false;keepMirror=true");
        context.registerDataProvider("http://org.opencds/opioid-cds", omtkDataProvider);
        Object result = context.resolveExpressionRef("TestCalculateMMEs").getExpression().evaluate(context);
        if (result == null) {
            throw new RuntimeException("Test failed");
        }
    }

    @Test
    public void testCdsOpioidLogic() throws IOException, JAXBException {
        java.io.InputStream input = TestOmtkDataProvider.class.getResourceAsStream("OpioidCDS_DSTU2-0.1.0.xml");
        Library library = CqlLibraryReader.read(input);
        Context context = new Context(library);
        context.registerLibraryLoader(new TestLibraryLoader());
        OmtkDataProvider omtkDataProvider = new OmtkDataProvider("jdbc:ucanaccess://c:/Users/Bryn/Documents/Src/SS/Pilots/Opioid/src/java/omtk-provider/data/OpioidManagementTerminologyKnowledge.accdb;memory=false;keepMirror=true");
        context.registerDataProvider("http://org.opencds/opioid-cds", omtkDataProvider);
        // TODO: Should run without errors even if no data is provided, getting a sort error right now
        // TODO: Deserialize medorder_buprenorphine_patch_draft.json as a MedicationOrder and pass in a list as the Orders argument to the Context
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
            return null;
        }
    }
}
