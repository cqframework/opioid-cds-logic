package org.opencds.cqf.opioidcds;

import ca.uhn.fhir.context.FhirContext;
import org.cqframework.cql.cql2elm.*;
import org.cqframework.cql.cql2elm.model.TranslatedLibrary;
import org.cqframework.cql.elm.tracking.TrackBack;
import org.hl7.elm.r1.VersionedIdentifier;
import org.hl7.fhir.dstu3.model.*;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.nio.file.Paths;

/**
 * Created by Bryn on 4/23/2017.
 */
public class TestTranslator {

    public enum FhirVersion {
        DSTU2, STU3, STU4
    }

    private LibraryManager dstu2LibraryManager;
    private LibraryManager stu3LibraryManager;
    private LibraryManager stu4LibraryManager;
    private LibraryGenerator stu3Generator;
    private LibraryGenerator stu4Generator;

    @Before
    public void setup() {
        FhirModelInfoProvider fhirProvider = new FhirModelInfoProvider().withVersion("1.0.2");
        ModelInfoLoader.registerModelInfoProvider(new VersionedIdentifier().withId("FHIR").withVersion("1.0.2"), fhirProvider);
        dstu2LibraryManager = new LibraryManager(new ModelManager());
        dstu2LibraryManager.getLibrarySourceLoader().clearProviders();
        dstu2LibraryManager.getLibrarySourceLoader().registerProvider(new TestLibrarySourceProvider());
        dstu2LibraryManager.getLibrarySourceLoader().registerProvider(new FhirLibrarySourceProvider());
        fhirProvider = new FhirModelInfoProvider().withVersion("3.0.0");
        ModelInfoLoader.registerModelInfoProvider(new VersionedIdentifier().withId("FHIR").withVersion("3.0.0"), fhirProvider);
        stu3LibraryManager = new LibraryManager(new ModelManager());
        stu3LibraryManager.getLibrarySourceLoader().clearProviders();
        stu3LibraryManager.getLibrarySourceLoader().registerProvider(new TestLibrarySourceProvider());
        stu3LibraryManager.getLibrarySourceLoader().registerProvider(new FhirLibrarySourceProvider());
        fhirProvider = new FhirModelInfoProvider().withVersion("3.2.0");
        ModelInfoLoader.registerModelInfoProvider(new VersionedIdentifier().withId("FHIR").withVersion("3.2.0"), fhirProvider);
        stu4LibraryManager = new LibraryManager(new ModelManager());
        stu4LibraryManager.getLibrarySourceLoader().clearProviders();
        stu4LibraryManager.getLibrarySourceLoader().registerProvider(new TestLibrarySourceProvider());
        stu4LibraryManager.getLibrarySourceLoader().registerProvider(new FhirLibrarySourceProvider());
        stu3Generator = new Stu3LibraryGenerator();
        stu4Generator = new Stu4LibraryGenerator();
    }

    private CqlTranslator runTest(String fileName, FhirVersion version) throws IOException {
        LibraryManager libraryManager;
        if (version == FhirVersion.DSTU2) {
            libraryManager = dstu2LibraryManager;
        }
        else if (version == FhirVersion.STU3) {
            libraryManager = stu3LibraryManager;
        }
        else {
            libraryManager = stu4LibraryManager;
        }
        InputStream test = TestTranslator.class.getResourceAsStream(fileName + ".cql");
        CqlTranslator translator =
                CqlTranslator.fromStream(
                        test,
                        new ModelManager(),
                        libraryManager,
                        CqlTranslator.Options.EnableDetailedErrors
                );

        checkErrors(translator);
        try (PrintWriter pw = new PrintWriter(Paths.get(fileName + ".xml").toFile(), "UTF-8")) {
            pw.println(translator.toXml());
        }

        return translator;
    }

    private void createStu3LibraryArtifact(CqlTranslator translator,  String id) throws FileNotFoundException, UnsupportedEncodingException {
        Library library = (Library) stu3Generator.generate(translator, id);

        try (PrintWriter pw = new PrintWriter(Paths.get("library-" + id + ".xml").toFile(), "UTF-8")) {
            pw.println(
                    FhirContext.forDstu3().newXmlParser().setPrettyPrint(true).encodeResourceToString(library)
                            .replaceAll("></.*", "/>")
            );
        }
    }

    private void createStu4LibraryArtifact(CqlTranslator translator,  String id) throws FileNotFoundException, UnsupportedEncodingException {
        org.hl7.fhir.r4.model.Library library = (org.hl7.fhir.r4.model.Library) stu4Generator.generate(translator, id);

        try (PrintWriter pw = new PrintWriter(Paths.get("library-" + id + ".xml").toFile(), "UTF-8")) {
            pw.println(
                    FhirContext.forR4().newXmlParser().setPrettyPrint(true).encodeResourceToString(library)
                            .replaceAll("></.*", "/>")
            );
        }
    }

    // This is for order (cds hooks library generation depends on stu3 test run first)
    @Test
    public void runAllTests() throws IOException {
        testOMTKLogic000();
        testOMTKLogic010();
        testOMTKLogic001();
        testOMTKData();
        testOpioidCDSDSTU2_Common();
        testOpioidCDSSTU3_Common();
        testOpioidCDSSTU4_Common();
        testOpioidCDSDSTU2_REC04();
        testOpioidCDSSTU3_REC04();
        testOpioidCDSSTU4_REC04();
        testOpioidCDSDSTU2_REC05();
        testOpioidCDSSTU3_REC05();
        testOpioidCDSSTU4_REC05();
        testOpioidCDSDSTU2_REC07();
        testOpioidCDSSTU3_REC07();
        testOpioidCDSSTU4_REC07();
        testOpioidCDSDSTU2_REC08();
        testOpioidCDSSTU3_REC08();
        testOpioidCDSSTU4_REC08();
        testOpioidCDSDSTU2_REC10();
        testOpioidCDSSTU3_REC10();
        testOpioidCDSSTU4_REC10();
        testOpioidCDSDSTU2_REC11();
        testOpioidCDSSTU3_REC11();
        testOpioidCDSSTU4_REC11();
    }

    private void testOMTKLogic000() throws IOException {
        OmtkModelInfoProvider provider = new OmtkModelInfoProvider().withVersion("0.0.0");
        ModelInfoLoader.registerModelInfoProvider(new VersionedIdentifier().withId("OMTK").withVersion("0.0.0"), provider);
        java.io.InputStream test = TestTranslator.class.getResourceAsStream("OMTKLogic-0.0.0.cql");
        CqlTranslator translator = CqlTranslator.fromStream(test, new ModelManager(), new LibraryManager(new ModelManager()), CqlTranslator.Options.EnableDetailedErrors);
        org.cqframework.cql.cql2elm.model.TranslatedLibrary library = translator.getTranslatedLibrary();

        checkErrors(translator);
        try (PrintWriter pw = new PrintWriter(Paths.get("OMTKLogic-0.0.0.xml").toFile(), "UTF-8")) {
            pw.println(translator.toXml());
        }

        createStu3LibraryArtifact(translator, "omtk-logic-0.0.0");
    }

    private void testOMTKLogic010() throws IOException {
        OmtkModelInfoProvider provider = new OmtkModelInfoProvider().withVersion("0.1.0");
        ModelInfoLoader.registerModelInfoProvider(new VersionedIdentifier().withId("OMTK").withVersion("0.1.0"), provider);
        java.io.InputStream test = TestTranslator.class.getResourceAsStream("OMTKLogic-0.1.0.cql");
        CqlTranslator translator = CqlTranslator.fromStream(test, new ModelManager(), new LibraryManager(new ModelManager()), CqlTranslator.Options.EnableDetailedErrors);
        org.cqframework.cql.cql2elm.model.TranslatedLibrary library = translator.getTranslatedLibrary();

        checkErrors(translator);
        try (PrintWriter pw = new PrintWriter(Paths.get("OMTKLogic-0.1.0.xml").toFile(), "UTF-8")) {
            pw.println(translator.toXml());
        }

        createStu3LibraryArtifact(translator, "omtk-logic-0.1.0");
    }

    private void testOMTKLogic001() throws IOException {
        java.io.InputStream test = TestTranslator.class.getResourceAsStream("OMTKLogic-0.0.1.cql");
        CqlTranslator translator = CqlTranslator.fromStream(test, new ModelManager(), stu3LibraryManager, CqlTranslator.Options.EnableDetailedErrors);
        org.cqframework.cql.cql2elm.model.TranslatedLibrary library = translator.getTranslatedLibrary();

        checkErrors(translator);
        try (PrintWriter pw = new PrintWriter(Paths.get("OMTKLogic-0.0.1.xml").toFile(), "UTF-8")) {
            pw.println(translator.toXml());
        }

        createStu3LibraryArtifact(translator, "omtk-logic-0.0.1");
    }

    private void testOMTKData() throws IOException {
        java.io.InputStream test = TestTranslator.class.getResourceAsStream("OMTKData-0.0.0.cql");
        CqlTranslator translator = CqlTranslator.fromStream(test, new ModelManager(), stu3LibraryManager, CqlTranslator.Options.EnableDetailedErrors);
        org.cqframework.cql.cql2elm.model.TranslatedLibrary library = translator.getTranslatedLibrary();

        checkErrors(translator);
        try (PrintWriter pw = new PrintWriter(Paths.get("OMTKData-0.0.0.xml").toFile(), "UTF-8")) {
            pw.println(translator.toXml());
        }

        createStu3LibraryArtifact(translator, "omtk-data-0.0.0");
    }

    private void testOpioidCDSDSTU2_Common() throws IOException {
        CqlTranslator translator = runTest("OpioidCDS_DSTU2_Common", FhirVersion.DSTU2);
        // todo - create dstu2 library artifact?
        createStu3LibraryArtifact(translator, "opioidcds-common-dstu2");
    }

    private void testOpioidCDSSTU3_Common() throws IOException {
        CqlTranslator translator = runTest("OpioidCDS_STU3_Common", FhirVersion.STU3);
        createStu3LibraryArtifact(translator, "opioidcds-common-stu3");
    }

    private void testOpioidCDSSTU4_Common() throws IOException {
        CqlTranslator translator = runTest("OpioidCDS_STU4_Common", FhirVersion.STU4);
        createStu4LibraryArtifact(translator, "opioidcds-common-stu4");
    }

    private void testOpioidCDSDSTU2_REC04() throws IOException {
        CqlTranslator translator = runTest("OpioidCDS_DSTU2_REC_04", FhirVersion.DSTU2);
        createStu3LibraryArtifact(translator, "opioidcds-recommendation-04-dstu2");
    }

    private void testOpioidCDSSTU3_REC04() throws IOException {
        CqlTranslator translator = runTest("OpioidCDS_STU3_REC_04", FhirVersion.STU3);
        createStu3LibraryArtifact(translator, "opioidcds-recommendation-04-stu3");
    }

    private void testOpioidCDSSTU4_REC04() throws IOException {
        CqlTranslator translator = runTest("OpioidCDS_STU4_REC_04", FhirVersion.STU4);
        createStu4LibraryArtifact(translator, "opioidcds-recommendation-04-stu4");
    }

    private void testOpioidCDSDSTU2_REC05() throws IOException {
        CqlTranslator translator = runTest("OpioidCDS_DSTU2_REC_05", FhirVersion.DSTU2);
        createStu3LibraryArtifact(translator, "opioidcds-recommendation-05-dstu2");
    }

    private void testOpioidCDSSTU3_REC05() throws IOException {
        CqlTranslator translator = runTest("OpioidCDS_STU3_REC_05", FhirVersion.STU3);
        createStu3LibraryArtifact(translator, "opioidcds-recommendation-05-stu3");
    }

    private void testOpioidCDSSTU4_REC05() throws IOException {
        CqlTranslator translator = runTest("OpioidCDS_STU4_REC_05", FhirVersion.STU4);
        createStu4LibraryArtifact(translator, "opioidcds-recommendation-05-stu4");
    }

    private void testOpioidCDSDSTU2_REC07() throws IOException {
        CqlTranslator translator = runTest("OpioidCDS_DSTU2_REC_07", FhirVersion.DSTU2);
        createStu3LibraryArtifact(translator, "opioidcds-recommendation-07-dstu2");
    }

    private void testOpioidCDSSTU3_REC07() throws IOException {
        CqlTranslator translator = runTest("OpioidCDS_STU3_REC_07", FhirVersion.STU3);
        createStu3LibraryArtifact(translator, "opioidcds-recommendation-07-stu3");
    }

    private void testOpioidCDSSTU4_REC07() throws IOException {
        CqlTranslator translator = runTest("OpioidCDS_STU4_REC_07", FhirVersion.STU4);
        createStu4LibraryArtifact(translator, "opioidcds-recommendation-07-stu4");
    }

    private void testOpioidCDSDSTU2_REC08() throws IOException {
        CqlTranslator translator = runTest("OpioidCDS_DSTU2_REC_08", FhirVersion.DSTU2);
        createStu3LibraryArtifact(translator, "opioidcds-recommendation-08-dstu2");
    }

    private void testOpioidCDSSTU3_REC08() throws IOException {
        CqlTranslator translator = runTest("OpioidCDS_STU3_REC_08", FhirVersion.STU3);
        createStu3LibraryArtifact(translator, "opioidcds-recommendation-08-stu3");
    }

    private void testOpioidCDSSTU4_REC08() throws IOException {
        CqlTranslator translator = runTest("OpioidCDS_STU4_REC_08", FhirVersion.STU4);
        createStu4LibraryArtifact(translator, "opioidcds-recommendation-08-stu4");
    }

    private void testOpioidCDSDSTU2_REC10() throws IOException {
        CqlTranslator translator = runTest("OpioidCDS_DSTU2_REC_10", FhirVersion.DSTU2);
        createStu3LibraryArtifact(translator, "opioidcds-recommendation-10-dstu2");
    }

    private void testOpioidCDSSTU3_REC10() throws IOException {
        CqlTranslator translator = runTest("OpioidCDS_STU3_REC_10", FhirVersion.STU3);
        createStu3LibraryArtifact(translator, "opioidcds-recommendation-10-stu3");
    }

    private void testOpioidCDSSTU4_REC10() throws IOException {
        CqlTranslator translator = runTest("OpioidCDS_STU4_REC_10", FhirVersion.STU4);
        createStu4LibraryArtifact(translator, "opioidcds-recommendation-10-stu4");
    }

    private void testOpioidCDSDSTU2_REC11() throws IOException {
        CqlTranslator translator = runTest("OpioidCDS_DSTU2_REC_11", FhirVersion.DSTU2);
        createStu3LibraryArtifact(translator, "opioidcds-recommendation-11-dstu2");
    }

    private void testOpioidCDSSTU3_REC11() throws IOException {
        CqlTranslator translator = runTest("OpioidCDS_STU3_REC_11", FhirVersion.STU3);
        createStu3LibraryArtifact(translator, "opioidcds-recommendation-11-stu3");
    }

    private void testOpioidCDSSTU4_REC11() throws IOException {
        CqlTranslator translator = runTest("OpioidCDS_STU4_REC_11", FhirVersion.STU4);
        createStu4LibraryArtifact(translator, "opioidcds-recommendation-11-stu4");
    }

    //    @Test
    public void testOpioidCDSSTU3_v18() throws IOException {
        OmtkModelInfoProvider omtkProvider = new OmtkModelInfoProvider().withVersion("0.1.0");
        FhirModelInfoProvider fhirProvider = new FhirModelInfoProvider().withVersion("1.8");
        ModelInfoLoader.registerModelInfoProvider(new VersionedIdentifier().withId("OMTK").withVersion("0.1.0"), omtkProvider);
        ModelInfoLoader.registerModelInfoProvider(new VersionedIdentifier().withId("FHIR").withVersion("3.0.0"), fhirProvider);
        LibraryManager libraryManager = new LibraryManager(new ModelManager());
        libraryManager.getLibrarySourceLoader().clearProviders();
        libraryManager.getLibrarySourceLoader().registerProvider(new TestLibrarySourceProvider());
        libraryManager.getLibrarySourceLoader().registerProvider(new FhirLibrarySourceProvider());
        InputStream test = TestTranslator.class.getResourceAsStream("OpioidCDS_STU3_1.8-0.1.0.cql");
        CqlTranslator translator = CqlTranslator.fromStream(test, new ModelManager(), libraryManager, CqlTranslator.Options.EnableDetailedErrors);
        TranslatedLibrary library = translator.getTranslatedLibrary();

        checkErrors(translator);
        try (PrintWriter pw = new PrintWriter(Paths.get("OpioidCDS_STU3_1.8-0.1.0.xml").toFile(), "UTF-8")) {
            pw.println(translator.toXml());
        }
    }

    static class TestLibrarySourceProvider implements LibrarySourceProvider {

        @Override
        public InputStream getLibrarySource(VersionedIdentifier libraryIdentifier) {
            if (libraryIdentifier.getId().equals("OMTKLogic") && libraryIdentifier.getVersion().equals("0.1.0")) {
                return TestTranslator.class.getResourceAsStream("OMTKLogic-0.1.0.cql");
            }
            if (libraryIdentifier.getId().equals("OMTKLogic") && libraryIdentifier.getVersion().equals("0.0.1")) {
                return TestTranslator.class.getResourceAsStream("OMTKLogic-0.0.1.cql");
            }
            if (libraryIdentifier.getId().equals("OMTKData") && libraryIdentifier.getVersion().equals("0.0.0")) {
                return TestTranslator.class.getResourceAsStream("OMTKData-0.0.0.cql");
            }
            if (libraryIdentifier.getId().equals("OpioidCDSCommonDSTU2") && libraryIdentifier.getVersion().equals("0.1.0")) {
                return TestTranslator.class.getResourceAsStream("OpioidCDS_DSTU2_Common.cql");
            }
            if (libraryIdentifier.getId().equals("OpioidCDSCommonSTU3") && libraryIdentifier.getVersion().equals("0.1.0")) {
                return TestTranslator.class.getResourceAsStream("OpioidCDS_STU3_Common.cql");
            }
            if (libraryIdentifier.getId().equals("OpioidCDSCommonSTU4") && libraryIdentifier.getVersion().equals("0.1.0")) {
                return TestTranslator.class.getResourceAsStream("OpioidCDS_STU4_Common.cql");
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
