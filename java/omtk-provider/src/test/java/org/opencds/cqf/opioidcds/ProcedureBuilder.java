package org.opencds.cqf.opioidcds;

import org.hl7.fhir.dstu3.model.*;
import org.hl7.fhir.exceptions.FHIRException;

import java.time.LocalDate;
import java.util.Date;

public class ProcedureBuilder extends BaseBuilder<Procedure> {

    public ProcedureBuilder() {
        super(new Procedure());
    }

    /*
    *
    * Supported Attributes:
    *   id
    *   status
    *   code
    *   subject
    *   context
    *   performed
    *
    * */

    public ProcedureBuilder buildId(String id) {
        complexProperty.setId(id);
        return this;
    }

    public ProcedureBuilder buildStatus(String status) {
        try {
            complexProperty.setStatus(Procedure.ProcedureStatus.fromCode(status));
        } catch (FHIRException e) {
            throw new RuntimeException("Invalid status code: " + status + "\nMessage: " + e.getMessage());
        }
        return this;
    }

    public ProcedureBuilder builCode(String code, String display) {
        complexProperty.setCode(
                new CodeableConcept().addCoding(
                        new Coding()
                                .setSystem("http://snomed.info/sct")
                                .setCode(code)
                                .setDisplay(display)
                )
        );
        return this;
    }

    public ProcedureBuilder buildSubject(String reference) {
        complexProperty.setSubject(new Reference().setReference(reference));
        return this;
    }

    public ProcedureBuilder buildContext(String reference) {
        complexProperty.setContext(new Reference().setReference(reference));
        return this;
    }

    public ProcedureBuilder buildPerformedPeriod(String start, String end) {
        complexProperty.setPerformed(
                new Period()
                        .setStart(start == null ? new Date() : java.sql.Date.valueOf(LocalDate.parse(start)))
                        .setEnd(end == null ? new Date() : java.sql.Date.valueOf(LocalDate.parse(end)))
        );
        return this;
    }

    public ProcedureBuilder buildPerformedPeriodExtension(String expression) {
        complexProperty.setPerformed(new Period());
        try {
            complexProperty.getPerformedPeriod().getStartElement().addExtension(
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
        } catch (FHIRException e) {
            throw new RuntimeException("Error creating performedPeriod extension: " + e.getMessage());
        }
        return this;
    }
}
