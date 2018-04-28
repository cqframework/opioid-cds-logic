package org.opencds.cqf.opioidcds;

import org.hl7.fhir.dstu3.model.*;
import org.hl7.fhir.exceptions.FHIRException;

import java.time.LocalDate;
import java.util.Date;

public class ObservationBuilder extends BaseBuilder<Observation> {

    public ObservationBuilder() {
        super(new Observation());
    }

    /*
    *
    * Supported Attributes:
    *   id
    *   code
    *   subject
    *   effective
    *   interpretation
    *   component
    *       code
    *       interpretation
    *
    * */

    public ObservationBuilder buildId(String id) {
        complexProperty.setId(id);
        return this;
    }

    public ObservationBuilder buildStatus(String status) {
        try {
            complexProperty.setStatus(Observation.ObservationStatus.fromCode(status));
        } catch (FHIRException e) {
            throw new RuntimeException("Invalid status code: " + status + "\nMessage: " + e.getMessage());
        }
        return this;
    }

    public ObservationBuilder buildCode(String code) {
        complexProperty.setCode(new CodeableConcept().addCoding(new Coding().setSystem("http://loinc.org").setCode(code)));
        return this;
    }

    public ObservationBuilder buildCode(String code, String display) {
        complexProperty.setCode(
                new CodeableConcept().addCoding(
                        new Coding().setSystem("http://loinc.org").setCode(code).setDisplay(display)
                )
        );
        return this;
    }

    public ObservationBuilder buildSubject(String reference) {
        complexProperty.setSubject(new Reference().setReference(reference));
        return this;
    }

    public ObservationBuilder buildInterpretation(String code) {
        complexProperty.setInterpretation(
                new CodeableConcept().addCoding(
                        new Coding()
                                .setSystem("http://hl7.org/fhir/v2/0078")
                                .setCode(code)
                )
        );
        return this;
    }

    public ObservationBuilder buildEffective(String date) {
        complexProperty.setEffective(new DateTimeType().setValue(date == null ? new Date() : java.sql.Date.valueOf(LocalDate.parse(date))));
        return this;
    }

    public ObservationBuilder buildEffectiveExtension(String expression) {
        complexProperty.setEffective(new DateTimeType());
        try {
            complexProperty.getEffectiveDateTimeType().addExtension(new Extension()
                    .setUrl("http://hl7.org/fhir/StructureDefinition/cqif-cqlExpression")
                    .setValue(new StringType(expression)));
        } catch (FHIRException e) {
            throw new RuntimeException("Error creating extension for effectiveDateTime: " + e.getMessage());
        }
        return this;
    }

    public ObservationBuilder buildComponent(String code, String interpretation) {
        complexProperty.addComponent(
                new Observation.ObservationComponentComponent()
                        .setCode(
                                new CodeableConcept().addCoding(
                                        new Coding().setSystem("http://loinc.org").setCode(code)
                                )
                        )
                        .setInterpretation(new CodeableConcept().addCoding(new Coding().setCode(interpretation)))
        );
        return this;
    }
}
