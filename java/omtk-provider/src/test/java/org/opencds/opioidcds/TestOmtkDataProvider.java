package org.opencds.opioidcds;

import org.junit.Test;
import org.opencds.cqf.cql.execution.Context;
import org.opencds.cqf.cql.execution.CqlLibraryReader;

import javax.xml.bind.JAXBException;
import java.io.IOException;

/**
 * Created by Bryn on 4/24/2017.
 */
public class TestOmtkDataProvider {

    @Test
    public void testBasicAccess() {
        OmtkDataProvider provider = new OmtkDataProvider("jdbc:ucanaccess://C:/Users/hoofs/Desktop/Work/CDC_Opiod/OpioidManagementTerminologyKnowledge.accdb;memory=false;keepMirror=true");
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
        OmtkDataProvider omtkDataProvider = new OmtkDataProvider("jdbc:ucanaccess://C:/Users/hoofs/Desktop/Work/CDC_Opiod/OpioidManagementTerminologyKnowledge.accdb;memory=false;keepMirror=true");
        context.registerDataProvider("http://org.opencds/opioid-cds", omtkDataProvider);
        Object result = context.resolveExpressionRef("TestCalculateMMEs").getExpression().evaluate(context);
        System.out.println(result.toString());
        if (result == null) {
            throw new RuntimeException("Test failed");
        }
    }
}
