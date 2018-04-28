package org.opencds.cqf.opioidcds;

import org.junit.Assert;
import org.junit.Test;
import org.opencds.cqf.cql.execution.Context;
import org.opencds.cqf.cql.execution.CqlLibraryReader;
import org.opencds.cqf.cql.runtime.Tuple;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.List;

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
    public void testOmtkLogic010() throws IOException, JAXBException {
        java.io.InputStream input = TestOmtkDataProvider.class.getResourceAsStream("OMTKLogic-0.1.0.xml");
        org.cqframework.cql.elm.execution.Library library = CqlLibraryReader.read(input);
        Context context = new Context(library);
        OmtkDataProvider omtkDataProvider = new OmtkDataProvider("jdbc:sqlite://" + pathToDB);
        context.registerDataProvider("http://org.opencds/opioid-cds", omtkDataProvider);
        Object result = context.resolveExpressionRef("TestCalculateMMEs").getExpression().evaluate(context);

        Assert.assertTrue(result instanceof Iterable);
        Assert.assertTrue(((List) result).get(0) instanceof Tuple);
        String mme = ((Tuple)((List) result).get(0)).getElement("mme").toString();
        Assert.assertTrue(mme.equals("145.53000000 mcg/h/d"));
    }

    @Test
    public void testOmtkLogic001() throws IOException, JAXBException {
        java.io.InputStream input = TestOmtkDataProvider.class.getResourceAsStream("OMTKLogic-0.0.1.xml");
        org.cqframework.cql.elm.execution.Library library = CqlLibraryReader.read(input);
        Context context = new Context(library);
        context.registerLibraryLoader(new TestLibraryLoader());
        Object result = context.resolveExpressionRef("TestCalculateMMEs").getExpression().evaluate(context);

        Assert.assertTrue(result instanceof Iterable);
        Assert.assertTrue(((List) result).get(0) instanceof Tuple);
        String mme = ((Tuple)((List) result).get(0)).getElement("mme").toString();
        Assert.assertTrue(mme.equals("145.53000000 mcg/h/d"));
    }
}
