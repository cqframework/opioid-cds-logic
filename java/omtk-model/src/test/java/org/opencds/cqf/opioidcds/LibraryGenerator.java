package org.opencds.cqf.opioidcds;

import org.cqframework.cql.cql2elm.CqlTranslator;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

public abstract class LibraryGenerator<T> {
    String version = "0.1.0";
    boolean experimental = false;
    String typeSystem = "http://hl7.org/fhir/library-type";
    String typeCode = "logic-library";
    String typeDisplay = "Logic Library";
    Date date = new Date();
    String publisher = "Centers for Disease Control and Prevention (CDC)";
    String useContextCodeSystem = "http://hl7.org/fhir/usage-context-type";
    String useContextCodeCode = "focus";
    String useContextCodeDisplay = "Clinical Focus";
    String useContextValueSystem = "http://snomed.info/sct";
    String useContextMedReqValueCode = "182888003";
    String useContextMedReqValueDisplay = "Medication requested (situation)";
    String useContextChronPainValueCode = "82423001";
    String useContextChronPainValueDisplay = "Chronic pain (finding)";
    String jurisdictionSystem = "urn:iso:std:iso:3166";
    String jurisdictionCode = "US";
    String jurisdictionDisplay = "United States of America";
    String topic = "Opioid Prescribing";
    List<String> contributorNames = Arrays.asList("Kensaku Kawamoto, MD, PhD, MHS", "Bryn Rhodes", "Floyd Eisenberg, MD, MPH", "Robert McClure, MD, MPH");
    String copyright = "© CDC 2016+.";
    String relatedArtifactGuidlinesDisplay = "CDC guideline for prescribing opioids for chronic pain";
    String relatedArtifactGuidlinesUrl = "https://guidelines.gov/summaries/summary/50153/cdc-guideline-for-prescribing-opioids-for-chronic-pain---united-states-2016#420";
    String relatedArtifactMmeDisplay = "MME Conversion Tables";
    String relatedArtifactMmeUrl = "https://www.cdc.gov/drugoverdose/pdf/calculating_total_daily_dose-a.pdf";
    String omtkReference = "Library/omtk-logic";
    String stu3CommonReference = "Library/opioidcds-common";
    String stu4CommonReference = "Library/opioidcds-common-stu4";
    String contentType = "application/elm+xml";

    String stu3CommonIdentifier = "OpioidCDS_STU3_Common";
    String stu4CommonIdentifier = "OpioidCDS_STU4_Common";
    String stu3CommonTitle = "Opioid CDS Common Logic (for FHIR STU3)";
    String stu4CommonTitle = "Opioid CDS Common Logic (for FHIR STU4)";
    String commonDescription = "Common Opioid Decision Support Logic for use in implementing CDC Opioid Prescribing Guidelines.";
    String commonPurpose = "This library contains common logic across recommendations including MME calculations, conversions, and looking up codes in valuesets.";
    String commonUsage = "This library is used for decision support for opioid guideline recommendations when applying PlanDefinitions.";

    String stu3RecFourIdentifier = "OpioidCDS_STU3_REC_04";
    String stu4RecFourIdentifier = "OpioidCDS_STU4_REC_04";
    String stu3RecFourTitle = "Opioid CDS Logic (for FHIR STU3) for recommendation #4";
    String stu4RecFourTitle = "Opioid CDS Logic (for FHIR STU4) for recommendation #4";
    String recFourDescription = "Opioid decision support logic for prescribing extended-release/long-acting (ER/LA) opioids when starting a patient on opioids.";
    String recFourPurpose = "The purpose of this library is to determine the appropriateness of extended-release opioids with ambulatory abuse potential for the patient.";
    String recFourUsage = "This library is used to notify the prescriber/user that immediate-release opioids are recommended when starting a patient on opioids.";

    String stu3RecFiveIdentifier = "OpioidCDS_STU3_REC_05";
    String stu4RecFiveIdentifier = "OpioidCDS_STU4_REC_05";
    String stu3recFiveTitle = "Opioid CDS Logic (for FHIR STU3) for recommendation #5";
    String stu4recFiveTitle = "Opioid CDS Logic (for FHIR STU4) for recommendation #5";
    String recFiveDescription = "Opioid Decision Support Logic for use in implementing CDC Opioid Prescribing Guidelines.";
    String recFivePurpose = "This library works in concert with the OMTK logic library to provide decision support for Morphine Milligram Equivalence calculations and dynamic value resolution.";
    String recFiveUsage = "This library is to notify the prescriber/user whether the current prescription exceeds the recommended MME.";

    String stu3RecSevenIdentifier = "OpioidCDS_STU3_REC_07";
    String stu4RecSevenIdentifier = "OpioidCDS_STU4_REC_07";
    String stu3RecSevenTitle = "Opioid CDS Logic (for FHIR STU3) for recommendation #7";
    String stu4RecSevenTitle = "Opioid CDS Logic (for FHIR STU4) for recommendation #7";
    String recSevenDescription = "Opioid decision support logic to evaluate benefits and harms with patients within 1 to 4 weeks of starting opioid therapy and harms of continued therapy with patients every 3 months or more frequently.";
    String recSevenPurpose = "The purpose of this library is to determine whether the patient has been evaluated for benefits and harms within 1 to 4 weeks of starting opioid therapy and every 3 months or more subsequently.";
    String recSevenUsage = "This library is used to notify the prescriber/user whether an evaluation for benefits and harms associated with opioid therapy is recommended for the patient.";

    String stu3RecEightIdentifier = "OpioidCDS_STU3_REC_04";
    String stu4RecEightIdentifier = "OpioidCDS_STU4_REC_04";
    String stu3RecEightTitle = "Opioid CDS Logic (for FHIR STU3) for recommendation #4";
    String stu4RecEightTitle = "Opioid CDS Logic (for FHIR STU4) for recommendation #4";
    String recEightDescription = "Opioid decision support logic to consider offering Naloxone when factors that increase risk for opioid overdose are present.";
    String recEightPurpose = "The purpose of this library is to determine whether increased risks for opioid overdose are present.";
    String recEightUsage = "This library is used to recommend the prescriber/user to consider offering Naloxone when increased risks for opioid overdose are present.";

    String stu3RecTenIdentifier = "OpioidCDS_STU3_REC_10";
    String stu4RecTenIdentifier = "OpioidCDS_STU4_REC_10";
    String stu3RecTenTitle = "Opioid CDS Logic (for FHIR STU3) for recommendation #10";
    String stu4RecTenTitle = "Opioid CDS Logic (for FHIR STU4) for recommendation #10";
    String recTenDescription = "Opioid decision support logic to evaluate whether the patient has had a urine screening in the past 12 months and provide analysis.";
    String recTenPurpose = "The purpose of this library is to determine whether the patient has had a urine screening in the past 12 months. Is so, then check the results for missing opioids that are prescribed, present opioids that aren't prescribed or present illicit drugs.";
    String recTenUsage = "This library is used to notify the prescriber/user whether the patient has had a urine screening in the past 12 months and to provide analysis if true.";

    String stu3RecElevenIdentifier = "OpioidCDS_STU3_REC_11";
    String stu4RecElevenIdentifier = "OpioidCDS_STU4_REC_11";
    String stu3RecElevenTitle = "Opioid CDS Logic (for FHIR STU3) for recommendation #11";
    String stu4RecElevenTitle = "Opioid CDS Logic (for FHIR STU4) for recommendation #11";
    String recElevenDescription = "Opioid decision support logic to avoid prescribing opioid pain medication and benzodiazepines concurrently whenever possible.";
    String recElevenPurpose = "The purpose of this library is to determine whether opioid pain medication and benzodiazepines have been prescribed concurrently.";
    String recElevenUsage = "This library is used to notify the prescriber/user to avoid prescribing opioid pain medication and benzodiazepines concurrently.";

    public abstract T generate(CqlTranslator translator, String id);
}
