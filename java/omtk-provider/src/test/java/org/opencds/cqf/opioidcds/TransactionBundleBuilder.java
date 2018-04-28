package org.opencds.cqf.opioidcds;

import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Resource;
import org.hl7.fhir.exceptions.FHIRException;

public class TransactionBundleBuilder extends BaseBuilder<Bundle> {

    public TransactionBundleBuilder() {
        super(new Bundle().setType(Bundle.BundleType.TRANSACTION));
    }

    /*
    *
    * Supported Attributes:
    *   id
    *   entry
    *       .fullUrl
    *       .resource
    *       .request
    *
    * */

    public TransactionBundleBuilder buildId(String id) {
        complexProperty.setId(id);
        return this;
    }

    public TransactionBundleBuilder buildEntry(String url, Resource resource, String method) {
        try {
            complexProperty.addEntry(
                    new Bundle.BundleEntryComponent()
                            .setFullUrl(url)
                            .setResource(resource)
                            .setRequest(
                                    new Bundle.BundleEntryRequestComponent()
                                            .setMethod(method == null ? Bundle.HTTPVerb.PUT : Bundle.HTTPVerb.fromCode(method))
                                            .setUrl(url)
                            )
            );
        } catch (FHIRException e) {
            throw new RuntimeException("Invalid method HTTP verb: " + method + "\nMessage: " + e.getMessage());
        }
        return this;
    }

    public TransactionBundleBuilder buildDeleteEntry(String url) {
        complexProperty.addEntry(
                new Bundle.BundleEntryComponent()
                        .setRequest(
                                new Bundle.BundleEntryRequestComponent()
                                        .setMethod(Bundle.HTTPVerb.DELETE)
                                        .setUrl(url)
                        )
        );
        return this;
    }
}
