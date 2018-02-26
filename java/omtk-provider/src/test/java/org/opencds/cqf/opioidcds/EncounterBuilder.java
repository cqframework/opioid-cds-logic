package org.opencds.cqf.opioidcds;

import org.hl7.fhir.dstu3.model.Encounter;
import org.hl7.fhir.dstu3.model.Period;
import org.hl7.fhir.dstu3.model.Reference;
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
    *   status
    *   subject
    *   period
    *
    * */

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
}
