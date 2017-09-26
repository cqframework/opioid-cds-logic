package org.opencds.opioidcds;

import org.cqframework.cql.cql2elm.*;
import org.cqframework.cql.cql2elm.model.TranslatedLibrary;
import org.cqframework.cql.elm.tracking.TrackBack;
import org.hl7.elm.r1.VersionedIdentifier;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
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
        CqlTranslator translator = CqlTranslator.fromStream(test, new ModelManager(), new LibraryManager(new ModelManager()), CqlTranslator.Options.EnableDetailedErrors);
        org.cqframework.cql.cql2elm.model.TranslatedLibrary library = translator.getTranslatedLibrary();

        checkErrors(translator);
        try (PrintWriter pw = new PrintWriter(Paths.get("OMTKLogic-0.1.0.xml").toFile(), "UTF-8")) {
            pw.println(translator.toXml());
        }
    }

    @Test
    public void testOpioidCDSDSTU2() throws IOException {
        OmtkModelInfoProvider omtkProvider = new OmtkModelInfoProvider().withVersion("0.1.0");
        FhirModelInfoProvider fhirProvider = new FhirModelInfoProvider().withVersion("1.0.2");
        ModelInfoLoader.registerModelInfoProvider(new VersionedIdentifier().withId("OMTK").withVersion("0.1.0"), omtkProvider);
        ModelInfoLoader.registerModelInfoProvider(new VersionedIdentifier().withId("FHIR").withVersion("1.0.2"), fhirProvider);
        LibraryManager libraryManager = new LibraryManager(new ModelManager());
        libraryManager.getLibrarySourceLoader().clearProviders();
        libraryManager.getLibrarySourceLoader().registerProvider(new TestLibrarySourceProvider());
        libraryManager.getLibrarySourceLoader().registerProvider(new FhirLibrarySourceProvider());
        InputStream test = TestTranslator.class.getResourceAsStream("OpioidCDS_DSTU2-0.1.0.cql");
        CqlTranslator translator = CqlTranslator.fromStream(test, new ModelManager(), libraryManager, CqlTranslator.Options.EnableDetailedErrors);
        TranslatedLibrary library = translator.getTranslatedLibrary();

        checkErrors(translator);
        try (PrintWriter pw = new PrintWriter(Paths.get("OpioidCDS_DSTU2-0.1.0.xml").toFile(), "UTF-8")) {
            pw.println(translator.toXml());
        }
    }

    @Test
    public void testOpioidCDSSTU3() throws IOException {
        OmtkModelInfoProvider omtkProvider = new OmtkModelInfoProvider().withVersion("0.1.0");
        FhirModelInfoProvider fhirProvider = new FhirModelInfoProvider().withVersion("3.0.0");
        ModelInfoLoader.registerModelInfoProvider(new VersionedIdentifier().withId("OMTK").withVersion("0.1.0"), omtkProvider);
        ModelInfoLoader.registerModelInfoProvider(new VersionedIdentifier().withId("FHIR").withVersion("3.0.0"), fhirProvider);
        LibraryManager libraryManager = new LibraryManager(new ModelManager());
        libraryManager.getLibrarySourceLoader().clearProviders();
        libraryManager.getLibrarySourceLoader().registerProvider(new TestLibrarySourceProvider());
        libraryManager.getLibrarySourceLoader().registerProvider(new FhirLibrarySourceProvider());
        InputStream test = TestTranslator.class.getResourceAsStream("OpioidCDS_STU3-0.1.0.cql");
        CqlTranslator translator = CqlTranslator.fromStream(test, new ModelManager(), libraryManager, CqlTranslator.Options.EnableDetailedErrors);
        TranslatedLibrary library = translator.getTranslatedLibrary();

        checkErrors(translator);
        try (PrintWriter pw = new PrintWriter(Paths.get("OpioidCDS_STU3-0.1.0.xml").toFile(), "UTF-8")) {
            pw.println(translator.toXml());
        }
    }
    class TestLibrarySourceProvider implements LibrarySourceProvider {

        @Override
        public InputStream getLibrarySource(VersionedIdentifier libraryIdentifier) {
            if (libraryIdentifier.getId().equals("OMTKLogic") && libraryIdentifier.getVersion().equals("0.1.0")) {
                return TestTranslator.class.getResourceAsStream("OMTKLogic-0.1.0.cql");
            }

            return null;
        }
    }

    private void checkErrors(CqlTranslator translator) {
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
    }
}
