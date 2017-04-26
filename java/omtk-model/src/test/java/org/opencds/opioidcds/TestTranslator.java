package org.opencds.opioidcds;

import org.cqframework.cql.cql2elm.CqlTranslator;
import org.cqframework.cql.cql2elm.CqlTranslatorException;
import org.cqframework.cql.cql2elm.LibraryManager;
import org.cqframework.cql.cql2elm.ModelInfoLoader;
import org.cqframework.cql.elm.tracking.TrackBack;
import org.hl7.elm.r1.VersionedIdentifier;
import org.junit.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Paths;

/**
 * Created by Bryn on 4/23/2017.
 */
public class TestTranslator {

    @Test
    public void testTranslator() throws IOException {
        OmtkModelInfoProvider provider = new OmtkModelInfoProvider().withVersion("0.1.0");
        ModelInfoLoader.registerModelInfoProvider(new VersionedIdentifier().withId("OMTK").withVersion("0.1.0"), provider);
        java.io.InputStream test = TestTranslator.class.getResourceAsStream("OMTKLogic-0.1.0.cql");
        CqlTranslator translator = CqlTranslator.fromStream(test, new LibraryManager(), CqlTranslator.Options.EnableDetailedErrors);
        org.cqframework.cql.cql2elm.model.TranslatedLibrary library = translator.getTranslatedLibrary();

        if (translator.getErrors().size() > 0) {
            System.err.println("Translation failed due to errors:");
            for (CqlTranslatorException error : translator.getErrors()) {
                TrackBack tb = error.getLocator();
                String lines = tb == null ? "[n/a]" : String.format("[%d:%d, %d:%d]",
                        tb.getStartLine(), tb.getStartChar(), tb.getEndLine(), tb.getEndChar());
                System.err.printf("%s %s%n", lines, error.getMessage());
            }
            throw new RuntimeException("Translation failed due to errors");
        }
        else {
            try (PrintWriter pw = new PrintWriter(Paths.get("OMTKLogic-0.1.0.xml").toFile(), "UTF-8")) {
                pw.println(translator.toXml());
            }
        }
    }
}
