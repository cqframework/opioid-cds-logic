package org.opencds.cqf.opioidcds;

import org.hl7.fhir.dstu3.model.*;
import org.hl7.fhir.exceptions.FHIRException;

import java.time.LocalDate;
import java.time.temporal.TemporalAccessor;
import java.util.Date;

public class MedicationRequestBuilder extends BaseBuilder<MedicationRequest> {

    public MedicationRequestBuilder() {
        super(new MedicationRequest());
    }

    /*
    *
    * Supported Attributes
    *   intent
    *   category
    *   medicationCodeableConcept
    *   subject
    *   authoredOn
    *   dosageInstruction
    *   dispenseRequest
    *       .validityPeriod
    *       .numberOfRepeatsAllowed
    *       .expectedSupplyDuration
    *
    * */

    public MedicationRequestBuilder buildIntent(String intent) {
        try {
            complexProperty.setIntent(MedicationRequest.MedicationRequestIntent.fromCode(intent));
        } catch (FHIRException e) {
            throw new RuntimeException("Invalid intent code: " + intent + "\nMessage: " + e.getMessage());
        }
        return this;
    }

    public MedicationRequestBuilder buildCategory(String category) {
        complexProperty.setCategory(new CodeableConcept().addCoding(new Coding().setCode(category)));
        return this;
    }

    public MedicationRequestBuilder buildMedicationCodeableConcept(String code) {
        complexProperty.setMedication(
                new CodeableConcept().addCoding(
                        new Coding()
                                .setSystem("http://www.nlm.nih.gov/research/umls/rxnorm")
                                .setCode(code)
                )
        );
        return this;
    }

    public MedicationRequestBuilder buildMedicationCodeableConcept(String code, String display) {
        complexProperty.setMedication(
                new CodeableConcept().addCoding(
                        new Coding()
                                .setSystem("http://hl7.org/fhir/ig/opioid-cds/CodeSystem/opioidcds-rxnorm-codes")
                                .setCode(code)
                                .setDisplay(display)
                )
        );
        return this;
    }

    public MedicationRequestBuilder buildMedicationCodeableConcept(CodeableConcept medication) {
        complexProperty.setMedication(medication);
        return this;
    }

    public MedicationRequestBuilder buildSubject(String reference) {
        complexProperty.setSubject(new Reference().setReference(reference));
        return this;
    }

    public MedicationRequestBuilder buildAuthoredOn(String date) {
        complexProperty.setAuthoredOn(date == null ? new Date() : java.sql.Date.valueOf(LocalDate.parse(date)));
        return this;
    }

    public MedicationRequestBuilder buildDosageInstruction(Dosage dosage) {
        complexProperty.addDosageInstruction(dosage);
        return this;
    }

    public MedicationRequestBuilder buildDispenseRequest(String start, String end, int numRepeats, double supplyValue, String supplyUnit) {
        complexProperty.setDispenseRequest(
                new MedicationRequest.MedicationRequestDispenseRequestComponent()
                        .setValidityPeriod(
                                new Period()
                                        .setStart(start == null ? new Date() : java.sql.Date.valueOf(LocalDate.parse(start)))
                                        .setEnd(end == null ? new Date() : java.sql.Date.valueOf(LocalDate.parse(end)))
                        )
                        .setNumberOfRepeatsAllowed(numRepeats)
                        .setExpectedSupplyDuration((Duration) new Duration().setValue(supplyValue).setUnit(supplyUnit))
        );
        return this;
    }
}
