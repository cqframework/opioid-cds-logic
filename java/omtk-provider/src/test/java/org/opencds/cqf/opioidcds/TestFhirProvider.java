package org.opencds.cqf.opioidcds;

import org.opencds.cqf.cql.data.fhir.FhirDataProviderStu3;

public class TestFhirProvider extends FhirDataProviderStu3 {

    @Override
    protected String convertPathToSearchParam(String path) {
        if (path.contains("medication")) {
            return "code";
        }
        if (path.contains("performed")) {
            return "date";
        }

        return path;
    }
}
