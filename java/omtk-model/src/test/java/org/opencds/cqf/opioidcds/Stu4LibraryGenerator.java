package org.opencds.cqf.opioidcds;

import org.cqframework.cql.cql2elm.CqlTranslator;
import org.hl7.elm.r1.Retrieve;
import org.hl7.elm.r1.ValueSetRef;
import org.hl7.fhir.r4.model.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Stu4LibraryGenerator extends LibraryGenerator<Library> {

    private RelatedArtifact commonLibraryRef =
            new RelatedArtifact()
                    .setType(RelatedArtifact.RelatedArtifactType.DEPENDSON)
                    .setResource(new Reference(stu4CommonReference));

    private RelatedArtifact mmeDailyDoseRef =
            new RelatedArtifact()
                    .setType(RelatedArtifact.RelatedArtifactType.DOCUMENTATION)
                    .setDisplay(relatedArtifactMmeDisplay)
                    .setUrl(relatedArtifactMmeUrl);

    private DataRequirement prefetchRxRequirement =
            (DataRequirement) new DataRequirement()
                    .setType("MedicationRequest")
                    .addCodeFilter(
                            new DataRequirement.DataRequirementCodeFilterComponent()
                                    .setPath("status")
                                    .addCode(new Coding().setCode("active"))
                    )
                    .addCodeFilter(
                            new DataRequirement.DataRequirementCodeFilterComponent()
                                    .setPath("category")
                                    .addCode(
                                            new Coding()
                                                    .setDisplay("http://hl7.org/fhir/medication-request-category")
                                                    .setCode("outpatient")
                                    )
                    )
                    .setId("medications");

    private Identifier getIdentifier(String value) {
        return new Identifier()
                .setUse(Identifier.IdentifierUse.OFFICIAL)
                .setValue(value);
    }

    private Library getTemplate() {
        Library library = new Library()
                .setVersion(version)
                .setStatus(Enumerations.PublicationStatus.ACTIVE)
                .setExperimental(experimental)
                .setType(
                        new CodeableConcept().addCoding(
                                new Coding()
                                        .setSystem(typeSystem)
                                        .setCode(typeCode)
                                        .setDisplay(typeDisplay)
                        )
                )
                .setDate(date)
                .setPublisher(publisher)
                .addUseContext(
                        new UsageContext().setCode(
                                new Coding()
                                        .setSystem(useContextCodeSystem)
                                        .setCode(useContextCodeCode)
                                        .setDisplay(useContextCodeDisplay)
                        ).setValue(
                                new CodeableConcept().addCoding(
                                        new Coding()
                                                .setSystem(useContextValueSystem)
                                                .setCode(useContextMedReqValueCode)
                                                .setDisplay(useContextMedReqValueDisplay)
                                )
                        )
                )
                .addUseContext(
                        new UsageContext().setCode(
                                new Coding()
                                        .setSystem(useContextCodeSystem)
                                        .setCode(useContextCodeCode)
                                        .setDisplay(useContextCodeDisplay)
                        ).setValue(
                                new CodeableConcept().addCoding(
                                        new Coding()
                                                .setSystem(useContextValueSystem)
                                                .setCode(useContextChronPainValueCode)
                                                .setDisplay(useContextChronPainValueDisplay)
                                )
                        )
                )
                .addJurisdiction(
                        new CodeableConcept().addCoding(
                                new Coding()
                                        .setSystem(jurisdictionSystem)
                                        .setCode(jurisdictionCode)
                                        .setDisplay(jurisdictionDisplay)
                        )
                )
                .addTopic(new CodeableConcept().setText(topic))
                .setCopyright(copyright)
                .addRelatedArtifact(
                        new RelatedArtifact()
                                .setType(RelatedArtifact.RelatedArtifactType.DOCUMENTATION)
                                .setDisplay(relatedArtifactGuidlinesDisplay)
                                .setUrl(relatedArtifactGuidlinesUrl)
                );

        for (String author : contributorNames) {
            library.addContributor(
                    new Contributor()
                            .setType(Contributor.ContributorType.AUTHOR)
                            .setName(author)
            );
        }

        return library;
    }

    @Override
    public Library generate(CqlTranslator translator, String id) {
        Library library = getTemplate();

        List<DataRequirement> dataReqList = getDataRequirements(translator);
        if (!dataReqList.isEmpty()) {
            library.setDataRequirement(dataReqList);
        }

        library.addContent(
                new Attachment()
                        .setContentType(contentType)
                        .setData(translator.toXml().getBytes())
        );

        switch (id) {
            case "opioidcds-common-stu4":
                library.setId(id);
                library
                        .addIdentifier(getIdentifier(stu4CommonIdentifier))
                        .setTitle(stu4CommonTitle)
                        .setDescription(commonDescription)
                        .setPurpose(commonPurpose)
                        .setUsage(commonUsage)
                        .addRelatedArtifact(
                                new RelatedArtifact()
                                        .setType(RelatedArtifact.RelatedArtifactType.DEPENDSON)
                                        .setResource(new Reference(omtkReference))
                        )
                        .addRelatedArtifact(mmeDailyDoseRef);
                break;
            case "opioidcds-recommendation-04-stu4":
                library.setId(id);
                library
                        .addIdentifier(getIdentifier(stu4RecFourIdentifier))
                        .setTitle(stu4RecFourTitle)
                        .setDescription(recFourDescription)
                        .setPurpose(recFourPurpose)
                        .setUsage(recFourUsage)
                        .addRelatedArtifact(commonLibraryRef);
                break;

            case "opioidcds-recommendation-05-stu4":
                library.setId(id);
                library
                        .addIdentifier(getIdentifier(stu4RecFiveIdentifier))
                        .setTitle(stu4recFiveTitle)
                        .setDescription(recFiveDescription)
                        .setPurpose(recFivePurpose)
                        .setUsage(recFiveUsage)
                        .addRelatedArtifact(commonLibraryRef)
                        .addRelatedArtifact(mmeDailyDoseRef)
                        .setDataRequirement(Collections.singletonList(prefetchRxRequirement));
                break;

            case "opioidcds-recommendation-07-stu4":
                library.setId(id);
                library
                        .addIdentifier(getIdentifier(stu4RecSevenIdentifier))
                        .setTitle(stu4RecSevenTitle)
                        .setDescription(recSevenDescription)
                        .setPurpose(recSevenPurpose)
                        .setUsage(recSevenUsage)
                        .addRelatedArtifact(commonLibraryRef);
                break;

            case "opioidcds-recommendation-08-stu4":
                library.setId(id);
                library
                        .addIdentifier(getIdentifier(stu4RecEightIdentifier))
                        .setTitle(stu4RecEightTitle)
                        .setDescription(recEightDescription)
                        .setPurpose(recEightPurpose)
                        .setUsage(recEightUsage)
                        .addRelatedArtifact(commonLibraryRef)
                        .addRelatedArtifact(mmeDailyDoseRef);
                break;

            case "opioidcds-recommendation-10-stu4":
                library.setId(id);
                library
                        .addIdentifier(getIdentifier(stu4RecTenIdentifier))
                        .setTitle(stu4RecTenTitle)
                        .setDescription(recTenDescription)
                        .setPurpose(recTenPurpose)
                        .setUsage(recTenUsage)
                        .addRelatedArtifact(commonLibraryRef);
                break;

            case "opioidcds-recommendation-11-stu4":
                library.setId(id);
                library
                        .addIdentifier(getIdentifier(stu4RecElevenIdentifier))
                        .setTitle(stu4RecElevenTitle)
                        .setDescription(recElevenDescription)
                        .setPurpose(recElevenPurpose)
                        .setUsage(recElevenUsage)
                        .addRelatedArtifact(commonLibraryRef);
                break;
        }

        return library;
    }

    public List<DataRequirement> getDataRequirements(CqlTranslator translator) {
        List<DataRequirement> dataReqList = new ArrayList<>();
        // Add context
        for (Retrieve retrieve : translator.toRetrieves()) {
            DataRequirement dataReq = new DataRequirement();
            dataReq.setType(retrieve.getDataType().getLocalPart());
            if (retrieve.getCodeProperty() != null) {
                DataRequirement.DataRequirementCodeFilterComponent codeFilter = new DataRequirement.DataRequirementCodeFilterComponent();
                codeFilter.setPath(retrieve.getCodeProperty());
                if (retrieve.getCodes() instanceof ValueSetRef) {
                    Type valueSetName = new UriType(((ValueSetRef) retrieve.getCodes()).getName());
                    codeFilter.setValueSet(valueSetName);
                }
                dataReq.setCodeFilter(Collections.singletonList(codeFilter));
            }
            // TODO - Date filters - we want to populate this with a $data-requirements request as there isn't a good way through elm analysis
            dataReqList.add(dataReq);
        }
        return dataReqList;
    }
}
