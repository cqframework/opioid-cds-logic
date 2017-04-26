package org.opencds.opioidcds;

import org.cqframework.cql.cql2elm.ModelInfoProvider;
import org.hl7.elm_modelinfo.r1.ModelInfo;

import javax.xml.bind.JAXB;

public class OmtkModelInfoProvider implements ModelInfoProvider {
    private String version;
    public String getVersion() {
        return version;
    }
    public void setVersion(String version) {
        this.version = version;
    }
    public OmtkModelInfoProvider withVersion(String version) {
        setVersion(version);
        return this;
    }

    public ModelInfo load() {
        String localVersion = version == null ? "" : version;
        switch (localVersion) {
            case "0.1.0":
                return JAXB.unmarshal(OmtkModelInfoProvider.class.getResourceAsStream("/org/opencds/opioidcds/OMTK-modelinfo-0.1.0.xml"),
                        ModelInfo.class);
            default:
                throw new IllegalArgumentException(String.format("Unknown version %s of the OMTK model.", localVersion));
        }
    }
}
