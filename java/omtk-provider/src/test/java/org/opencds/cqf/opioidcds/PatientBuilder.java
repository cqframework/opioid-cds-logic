package org.opencds.cqf.opioidcds;

import org.hl7.fhir.dstu3.model.Enumerations;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.exceptions.FHIRException;

import java.time.LocalDate;
import java.util.Date;

public class PatientBuilder extends BaseBuilder<Patient> {

    public PatientBuilder() {
        super(new Patient());
    }

    /*
    *
    * Supported Attributes:
    *   id
    *   gender
    *   birthDate
    *
    * */

    public PatientBuilder buildId(String id) {
        complexProperty.setId(id);
        return this;
    }

    public PatientBuilder buildGender(String gender) {
        try {
            complexProperty.setGender(Enumerations.AdministrativeGender.fromCode(gender));
        } catch (FHIRException e) {
            throw new RuntimeException("Invalid gender code: " + gender + "\nMessage: " + e.getMessage());
        }
        return this;
    }

    public PatientBuilder buildBirthDate(String birthDate) {
        complexProperty.setBirthDate(birthDate == null ? new Date() : java.sql.Date.valueOf(LocalDate.parse(birthDate)));
        return this;
    }
}
