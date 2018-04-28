package org.opencds.cqf.opioidcds;

import org.hl7.fhir.dstu3.model.*;
import org.hl7.fhir.exceptions.FHIRException;

import java.time.LocalDate;
import java.util.Date;

public class EncounterBuilder extends BaseBuilder<Encounter> {

    public EncounterBuilder() {
        super(new Encounter());
    }

    /*
    *
    * Supported Attributes:
    *   id
    *   status
    *   subject
    *   period
    *
    * */

    public EncounterBuilder buildId(String id) {
        complexProperty.setId(id);
        return this;
    }

    public EncounterBuilder buildStatus(String status) {
        try {
            complexProperty.setStatus(Encounter.EncounterStatus.fromCode(status));
        } catch (FHIRException e) {
            throw new RuntimeException("Invalid status code: " + status + "\nMessage: " + e.getMessage());
        }
        return this;
    }

    public EncounterBuilder buildSubject(String reference) {
        complexProperty.setSubject(new Reference().setReference(reference));
        return this;
    }

    public EncounterBuilder buildPeriod(String start, String end) {
        complexProperty.setPeriod(
                new Period()
                        .setStart(start == null ? new Date() : java.sql.Date.valueOf(LocalDate.parse(start)))
                        .setEnd(end == null ? new Date() : java.sql.Date.valueOf(LocalDate.parse(end)))
        );
        return this;
    }

    public EncounterBuilder buildPeriodExtension(String expression) {
        complexProperty.getPeriod().addExtension(
                new Extension()
                        .setUrl("http://hl7.org/fhir/StructureDefinition/cqif-cqlExpression")
                        .setValue(
                                new StringType(
                                        String.format(
                                                "FHIR.Period { start: FHIR.dateTime { value: %s }, end: FHIR.dateTime { value: %s } }",
                                                expression, expression
                                        )
                                )
                        )
        );
        return this;
    }
}
