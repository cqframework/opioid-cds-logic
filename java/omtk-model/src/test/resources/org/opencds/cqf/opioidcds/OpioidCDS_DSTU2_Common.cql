library OpioidCDSCommonDSTU2 version '0.1.0'

using FHIR version '1.0.2'

include OMTKLogic version '0.0.1' called OMTKLogic

codesystem "SNOMED": 'http://snomed.info/sct'

valueset "Benzodiazepines": 'http://hl7.org/fhir/ig/opioid-cds/ValueSet/benzodiazepines'
valueset "End of Life Conditions": 'http://hl7.org/fhir/ig/opioid-cds/ValueSet/end-of-life-conditions'
// Harvested from VSAC - OID: 2.16.840.1.113762.1.4.1108.15
valueset "Hospice Disposition": 'http://hl7.org/fhir/ig/opioid-cds/ValueSet/hospice-disposition'
valueset "Hospice Finding Codes": 'http://hl7.org/fhir/ig/opioid-cds/ValueSet/hospice-finding'
valueset "Hospice Procedure Codes": 'http://hl7.org/fhir/ig/opioid-cds/ValueSet/hospice-procedure'
valueset "Illicit Drug Screening": 'http://hl7.org/fhir/ig/opioid-cds/ValueSet/illicit-drug-urine-screening'
// Harvested from VSAC - OID: 2.16.840.1.113883.3.526.3.1259
valueset "Limited Life Expectancy Conditions": 'http://hl7.org/fhir/ig/opioid-cds/ValueSet/limited-life-expectancy-conditions'
valueset "Long Acting Opioids": 'http://hl7.org/fhir/ig/opioid-cds/ValueSet/long-acting-opioids'
// Harvested from VSAC - OID: 2.16.840.1.113762.1.4.1116.351
valueset "Metastatic Cancer": 'http://hl7.org/fhir/ig/opioid-cds/ValueSet/metastatic-cancer'
valueset "Naloxone": 'http://hl7.org/fhir/ig/opioid-cds/ValueSet/naloxone'
valueset "Risk Assessment": 'http://hl7.org/fhir/ig/opioid-cds/ValueSet/opioid-abuse-assessment'
valueset "Opioid Drug Screening": 'http://hl7.org/fhir/ig/opioid-cds/ValueSet/opioid-urine-screening'
valueset "Ambulatory Abuse Potential Opioids": 'http://hl7.org/fhir/ig/opioid-cds/ValueSet/opioids-abused-in-ambulatory-care'
valueset "End Of Life Opioids": 'http://hl7.org/fhir/ig/opioid-cds/ValueSet/opioids-indicating-end-of-life'
valueset "Substance Abuse": 'http://hl7.org/fhir/ig/opioid-cds/ValueSet/substance-abuse'

// Valuesets that are not currently used
valueset "Opioids": 'http://hl7.org/fhir/ig/opioid-cds/ValueSet/opioids'
valueset "Codeine Cough Meds": 'http://hl7.org/fhir/ig/opioid-cds/ValueSet/opioids-codeine-cough-meds'
valueset "Short Acting Opioids": 'http://hl7.org/fhir/ig/opioid-cds/ValueSet/short-acting-opioids'

code "Referral to Hospice": '306205009' from "SNOMED"

// TODO: Capture process decisions for long-term opioid use
define IsForChronicPain: true

define "Active Ambulatory Opioid Rx":
  [MedicationOrder: "Ambulatory Abuse Potential Opioids"] Rx
    where Rx.status.value = 'active'

define "Active Ambulatory Benzodiazepine Rx":
  [MedicationOrder: "Benzodiazepines"] Rx
    where Rx.status.value = 'active'

define "Active Ambulatory Naloxone Rx":
  [MedicationOrder: "Naloxone"] Rx
    where Rx.status.value = 'active'

define "Ambulatory Opioid Rx":
  [MedicationOrder: "Ambulatory Abuse Potential Opioids"] Rx

define function "Is Context Prescription End of Life Opioid?" (ContextPrescriptions List<MedicationOrder>):
  exists (
    ContextPrescriptions Rx
      where ToCode(Rx.medicationCodeableConcept.coding[0]) in "End Of Life Opioids"
  )

define "End of Life Assessment":
  // 1. Conditions indicating end of life or with limited life expectancy
  exists (
    (
      [Condition: code in "End of Life Conditions"] C
        where C.clinicalStatus.value in { 'active', 'relapse' }
    )
    union
    (
      [Condition: code in "Limited Life Expectancy Conditions"] C
        where C.clinicalStatus.value in { 'active', 'relapse' }
    )
  )
  // 2. Admitted/referred/discharged to hospice care
  or exists (
    (
      [ReferralRequest: type in "Referral to Hospice"] RR
        where RR.status.value in { 'accepted', 'active', 'completed', 'requested' }
    )
    union
    (
      [Procedure: code in "Hospice Procedure Codes"] P
        where P.status.value in { 'in-progress', 'completed' }
    )
    union
    (
      // todo - code not listed as a valid search param for DSTU2 - verify and change if needed
      [ProcedureRequest: code in "Hospice Procedure Codes"] E
        where E.status.value not in { 'suspended', 'aborted', 'rejected', 'draft' }
    )
    union
    (
      [Observation: code in "Hospice Finding Codes"] O
        where O.status.value not in { 'unknown', 'entered-in-error', 'cancelled' }
    )
    union
    (
      [Encounter] E
        where
          (
            if E.hospitalization.dischargeDisposition.coding[0] is null
              then false
            else ToCode(E.hospitalization.dischargeDisposition.coding[0]) in "Hospice Disposition"
          )
          and E.status.value in { 'planned', 'arrived', 'in-progress', 'finished', 'onleave' }
    )
  )
  // 3. Medications indicating end of life
  or exists (
    (
      [MedicationAdministration: code in "End Of Life Opioids"] MA
        where MA.status.value in { 'in-progress', 'on-hold', 'completed' }
    )
    union
    (
      [MedicationDispense: code in "End Of Life Opioids"] MD
        where MD.status.value in { 'in-progress', 'on-hold', 'completed' }
    )
    union
    (
      [MedicationOrder: code in "End Of Life Opioids"] MR
        where MR.status.value in { 'active', 'on-hold', 'completed' }
    )
    union
    (
      [MedicationStatement: code in "End Of Life Opioids"] MS
        where MS.status.value in { 'active', 'completed', 'intended' }
    )
  )

define function Prescriptions(Orders List<MedicationOrder>):
  Orders O
    let
      rxNormCode: ToCode(O.medicationCodeableConcept.coding[0]),
      medicationName: OMTKLogic.GetMedicationName(rxNormCode),
      // NOTE: Assuming a single dosage instruction element
      dosageInstruction: O.dosageInstruction[0],
      repeat: dosageInstruction.timing.repeat,
      frequency: Coalesce(repeat.frequencyMax.value, repeat.frequency.value),
      period: System.Quantity { value: repeat.period.value, unit: repeat.periodUnits.value },
      doseDescription:
        Coalesce(
          // There should be a conversion from FHIR.SimpleQuantity to System.Quantity
          if dosageInstruction.doseRange is not null
            then ToString(ToQuantity(dosageInstruction.doseRange.low))
                          + '-' + ToString(ToQuantity(dosageInstruction.doseRange.high))
                          + dosageInstruction.doseRange.high.unit.value
          else ToString(ToQuantity(dosageInstruction.doseQuantity))
        ),
      frequencyDescription:
        ToString(dosageInstruction.timing.repeat.frequency.value) +
          Coalesce(
            '-' + ToString(dosageInstruction.timing.repeat.frequencyMax.value),
            ''
          )
    return {
      rxNormCode: rxNormCode,
      isDraft: O.status.value = 'draft',
      // NOTE: Assuming asNeeded is expressed as a boolean
      isPRN: dosageInstruction.asNeededBoolean.value,
      prescription:
        if dosageInstruction.text is not null then
          medicationName + ' ' + dosageInstruction.text.value
        else
          // TODO: Shouldn't need the .value here on asNeededBoolean
          medicationName + ' ' + doseDescription + ' q' + frequencyDescription + (if dosageInstruction.asNeededBoolean.value then ' PRN' else ''),
      // TODO: Shouldn't need the ToQuantity here...
      dose: if dosageInstruction.doseRange is not null
              then ToQuantity(dosageInstruction.doseRange.high)
            else ToQuantity(dosageInstruction.doseQuantity),
      dosesPerDay: Coalesce(OMTKLogic.ToDaily(frequency, period), 1.0)
    }

define function MME(prescriptions List<MedicationOrder>):
  (Prescriptions(prescriptions)) P
    let mme: SingletonFrom(OMTKLogic.CalculateMMEs({ { rxNormCode: P.rxNormCode, doseQuantity: P.dose, dosesPerDay: P.dosesPerDay } }))
    return {
      rxNormCode: P.rxNormCode,
      isDraft: P.isDraft,
      isPRN: P.isPRN,
      prescription: P.prescription,
      dailyDose: mme.dailyDoseDescription,
      conversionFactor: mme.conversionFactor,
      mme: mme.mme
    }
    // sort by if isDraft then 0 else 1, rxNormCode.code

define function TotalMME(prescriptions List<MedicationOrder>):
  System.Quantity {
    value: Sum((MME(prescriptions)) M return M.mme.value),
    unit: 'mg/d'
  }

define function ProbableDaysInRange(Orders List<MedicationOrder>, daysPast Integer, numDaysInDaysPast Integer):
  Orders orders
    let
      frequency: orders.dosageInstruction[0].timing.repeat.frequency.value,
      period: orders.dosageInstruction[0].timing.repeat.period.value,
      periodDays: GetPeriodDays(orders.dosageInstruction[0].timing.repeat.periodUnits.value),
      dosesPerDay:
        if (frequency / (period * periodDays)) >= 1.0
        then 1.0
        else frequency / (period * periodDays),
      repeat: orders.dispenseRequest.numberOfRepeatsAllowed.value,
      supplyDuration: GetDurationInDays(orders.dispenseRequest.expectedSupplyDuration),
      validityPeriod: days between orders.dispenseRequest.validityPeriod."start".value and Today(),
      endDifference:
        if orders.dispenseRequest.validityPeriod."end".value < Today()
        then days between orders.dispenseRequest.validityPeriod."end".value and Today()
        else 0
    return
      if (repeat * supplyDuration) < numDaysInDaysPast then false
      else
        (dosesPerDay * ((repeat * supplyDuration) / validityPeriod) * (daysPast - endDifference)) >= numDaysInDaysPast

define function GetPeriodDays(value System.String):
  case
    when value = 'a' then 365.0
    when value = 'mo' then 30.0
    when value = 'h' then 1.0/24.0
    when value = 'min' then 1.0/24.0*60.0
    else 1.0
  end

define function GetDurationInDays(value FHIR.Duration):
  case
    when StartsWith(value.unit.value, 'a') then value.value.value * 365.0
    when StartsWith(value.unit.value, 'mo') then value.value.value * 30.0
    else value.value.value
  end

define function GetIngredient(rxNormCode Code):
  OMTKLogic.GetIngredients(rxNormCode).ingredientName

define function GetMedicationNames(medications List<MedicationOrder>):
  medications M
    return OMTKLogic.GetIngredients(ToCode(M.medicationCodeableConcept.coding[0])).rxNormCode.display

/*
*  Conversion Functions
*/

define function ToCode(coding FHIR.Coding):
  System.Code {
    code: coding.code.value,
    system: coding.system.value,
    version: coding.version.value,
    display: coding.display.value
  }

define function ToQuantity(quantity FHIR.Quantity):
  System.Quantity { value: quantity.value.value, unit: quantity.unit.value }