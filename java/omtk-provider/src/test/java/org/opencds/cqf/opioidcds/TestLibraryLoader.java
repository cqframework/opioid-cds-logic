package org.opencds.cqf.opioidcds;

import org.cqframework.cql.elm.execution.Library;
import org.cqframework.cql.elm.execution.VersionedIdentifier;
import org.opencds.cqf.cql.execution.CqlLibraryReader;
import org.opencds.cqf.cql.execution.LibraryLoader;

import javax.xml.bind.JAXBException;
import java.io.IOException;

class TestLibraryLoader implements LibraryLoader {

    @Override
    public Library load(VersionedIdentifier versionedIdentifier) {
        if (versionedIdentifier.getId().equals("OMTKLogic") && versionedIdentifier.getVersion().equals("0.1.0")) {
            java.io.InputStream input = TestLibraryLoader.class.getResourceAsStream("OMTKLogic-0.1.0.xml");
            try {
                return CqlLibraryReader.read(input);
            } catch (IOException | JAXBException e) {
                e.printStackTrace();
            }
        }
        else if (versionedIdentifier.getId().equals("OMTKLogic") && versionedIdentifier.getVersion().equals("0.0.1")) {
            java.io.InputStream input = TestLibraryLoader.class.getResourceAsStream("OMTKLogic-0.0.1.xml");
            try {
                return CqlLibraryReader.read(input);
            } catch (IOException | JAXBException e) {
                e.printStackTrace();
            }
        }
        else if (versionedIdentifier.getId().equals("OMTKData") && versionedIdentifier.getVersion().equals("0.0.0")) {
            java.io.InputStream input = TestLibraryLoader.class.getResourceAsStream("OMTKData-0.0.0.xml");
            try {
                return CqlLibraryReader.read(input);
            } catch (IOException | JAXBException e) {
                e.printStackTrace();
            }
        }
        else if (versionedIdentifier.getId().equals("OpioidCDSCommonSTU3") && versionedIdentifier.getVersion().equals("0.1.0")) {
            java.io.InputStream input = TestLibraryLoader.class.getResourceAsStream("OpioidCDS_STU3_Common.xml");
            try {
                return CqlLibraryReader.read(input);
            } catch (IOException | JAXBException e) {
                e.printStackTrace();
            }
        }
        else if (versionedIdentifier.getId().equals("FHIRHelpers") && versionedIdentifier.getVersion().equals("3.0.0")) {
            java.io.InputStream input = TestLibraryLoader.class.getResourceAsStream("FHIRHelpers-3.0.0.xml");
            try {
                return CqlLibraryReader.read(input);
            } catch (IOException | JAXBException e) {
                e.printStackTrace();
            }
        }
        else if (versionedIdentifier.getId().equals("FHIRHelpers") && versionedIdentifier.getVersion().equals("1.0.2")) {
            java.io.InputStream input = TestLibraryLoader.class.getResourceAsStream("FHIRHelpers-1.0.2.xml");
            try {
                return CqlLibraryReader.read(input);
            } catch (IOException | JAXBException e) {
                e.printStackTrace();
            }
        }

        throw new RuntimeException(String.format("Could not load library %s-%s.", versionedIdentifier.getId(), versionedIdentifier.getVersion()));
    }
}
