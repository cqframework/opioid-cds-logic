package org.opencds.cqf.opioidcds;

import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.DateType;
import org.hl7.fhir.dstu3.model.Observation;

import java.time.LocalDate;
import java.util.Date;

public class ObservationBuilder extends BaseBuilder<Observation> {

    public ObservationBuilder() {
        super(new Observation());
    }

    /*
    *
    * Supported Attributes:
    *   code
    *   effective
    *   interpretation
    *   component
    *       code
    *       interpretation
    *
    * */

    public ObservationBuilder buildCode(String code) {
        complexProperty.setCode(new CodeableConcept().addCoding(new Coding().setCode(code)));
        return this;
    }

    public ObservationBuilder buildInterpretation(String code) {
        complexProperty.setInterpretation(new CodeableConcept().addCoding(new Coding().setCode(code)));
        return this;
    }

    public ObservationBuilder buildEffective(String date) {
        complexProperty.setEffective(new DateType().setValue(date == null ? new Date() : java.sql.Date.valueOf(LocalDate.parse(date))));
        return this;
    }

    public ObservationBuilder buildComponent(String code, String interpretation) {
        complexProperty.addComponent(
                new Observation.ObservationComponentComponent()
                        .setCode(
                                new CodeableConcept().addCoding(
                                        new Coding().setCode(code)
                                )
                        )
                        .setInterpretation(new CodeableConcept().addCoding(new Coding().setCode(interpretation)))
        );
        return this;
    }
}
