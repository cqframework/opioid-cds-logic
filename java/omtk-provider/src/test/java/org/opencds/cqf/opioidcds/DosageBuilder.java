package org.opencds.cqf.opioidcds;

import org.hl7.fhir.dstu3.model.BooleanType;
import org.hl7.fhir.dstu3.model.Dosage;
import org.hl7.fhir.dstu3.model.SimpleQuantity;
import org.hl7.fhir.dstu3.model.Timing;
import org.hl7.fhir.exceptions.FHIRException;

public class DosageBuilder extends BaseBuilder<Dosage> {

    public DosageBuilder() {
        super(new Dosage());
    }

    /*
    *
    * Supported Attributes:
    *   timing
    *   asNeededBoolean
    *
    * */

    public DosageBuilder buildTiming(int frequency, double period, String periodUnit) {
        try {
            complexProperty.setTiming(
                    new Timing().setRepeat(
                            new Timing.TimingRepeatComponent()
                                    .setFrequency(frequency)
                                    .setPeriod(period)
                                    .setPeriodUnit(Timing.UnitsOfTime.fromCode(periodUnit))
                    )
            );
        } catch (FHIRException e) {
            throw new RuntimeException("Invalid periodUnit: " + periodUnit + "\nMessage: " + e.getMessage());
        }
        return this;
    }

    public DosageBuilder buildAsNeeded(boolean asNeeded) {
        complexProperty.setAsNeeded(new BooleanType(asNeeded));
        return this;
    }

    public DosageBuilder buildDose(double value, String unit) {
        complexProperty.setDose(new SimpleQuantity().setValue(value).setUnit(unit));
        return this;
    }
}
