library OpioidCDS_DSTU2_REC_11 version '0.1.0'

using FHIR version '1.0.2'

include FHIRHelpers version '1.0.2' called FHIRHelpers
include OpioidCDSCommonDSTU2 version '0.1.0' called Common

/*
**
**  Recommendation #11
**    Avoid prescribing opioid pain medication and benzodiazepines concurrently whenever possible
**
*/

/*
  Trigger context:
    Primary care/ambulatory care
  Trigger event:
    Prescription of benzodiazepine or opioid with ambulatory care abuse potential
  Assumptions:
    Count only ambulatory prescriptions
  Inclusion criteria:
    On both opioid with ambulatory care abuse potential and benzodiazepine
  Notification:
    Avoid prescribing opioid pain mediation and benzodiazepine concurrently whenever possible.
    Ref: CDC Recommendation #11.
  EHR expectations:
    Have availability to snooze if benefits outweigh risks
*/

parameter ContextPrescriptions List<MedicationOrder>

context Patient

define "Validate Trigger Event For Benzodiazepine":
  exists(
    ContextPrescriptions triggerScript
      where triggerScript.medicationCodeableConcept in Common."Benzodiazepines"
  )

define "Validate Trigger Event For Ambulatory Care Abuse Potential":
  exists(
    ContextPrescriptions triggerScript
    where triggerScript.medicationCodeableConcept in Common."Ambulatory Abuse Potential Opioids"
  )

define "Inclusion Criteria":
  AgeInYears() >= 18
    and ("Validate Trigger Event For Benzodiazepine"
      or "Validate Trigger Event For Ambulatory Care Abuse Potential")
    and not "Exclusion Criteria"
    and "On Benzodiazepine"
    and "Opioid with Ambulatory Care Abuse Potential"

define "Exclusion Criteria":
  Common."End of Life Assessment"
    or Common."Is Context Prescription End of Life Opioid?"(ContextPrescriptions)

define "On Benzodiazepine":
  "Validate Trigger Event For Benzodiazepine"
  or exists( Common."Active Ambulatory Benzodiazepine Rx" )

define "Opioid with Ambulatory Care Abuse Potential":
  "Validate Trigger Event For Ambulatory Care Abuse Potential"
  or exists( Common."Active Ambulatory Opioid Rx" )

define "Get Indicator":
  if "Inclusion Criteria"
    then 'warning'
  else null

define "Get Summary":
  if "Inclusion Criteria"
    then 'Avoid prescribing opioid pain medication and benzodiazepine concurrently whenever possible.'
  else null

define "Get Detail":
  if "Inclusion Criteria"
    then
      if "Validate Trigger Event For Benzodiazepine"
        then 'The benzodiazepine prescription request is concurrent with an active opioid prescription'
      else 'The opioid prescription request is concurrent with an active benzodiazepine prescription'
  else null
