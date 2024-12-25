package org.sagebionetworks.bridge.models.assessments;

import static jakarta.persistence.CascadeType.MERGE;
import static jakarta.persistence.CascadeType.PERSIST;
import static jakarta.persistence.FetchType.EAGER;
import static org.sagebionetworks.bridge.models.TagUtils.toTagSet;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.hibernate.DateTimeToLongAttributeConverter;
import org.sagebionetworks.bridge.hibernate.JsonNodeAttributeConverter;
import org.sagebionetworks.bridge.hibernate.LabelListConverter;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.hibernate.ColorSchemeConverter;
import org.sagebionetworks.bridge.hibernate.CustomizationFieldsConverter;
import org.sagebionetworks.bridge.models.Label;
import org.sagebionetworks.bridge.models.Tag;
import org.sagebionetworks.bridge.models.assessments.config.PropertyInfo;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Persistence object for a record about an assessment (task, survey, measure) in 
 * the Bridge system.
 */
@Entity
// This annotation is necessary so a constraint violation exception involving an 
// assessment displays the correct message without exposing the Hibernate implementation.
@BridgeTypeName("Assessment")
@Table(name = "Assessments")
public class HibernateAssessment {
    
    public static HibernateAssessment create(String appId, Assessment dto) {
        HibernateAssessment assessment = new HibernateAssessment();
        assessment.setAppId(appId);
        assessment.setGuid(dto.getGuid());
        assessment.setAppId(appId);
        assessment.setIdentifier(dto.getIdentifier());
        assessment.setRevision(dto.getRevision());
        assessment.setTitle(dto.getTitle());
        assessment.setSummary(dto.getSummary());
        assessment.setValidationStatus(dto.getValidationStatus());
        assessment.setNormingStatus(dto.getNormingStatus());
        assessment.setMinutesToComplete(dto.getMinutesToComplete());
        assessment.setOsName(dto.getOsName());
        assessment.setOriginGuid(dto.getOriginGuid());
        assessment.setOwnerId(dto.getOwnerId());
        assessment.setTags(toTagSet(dto.getTags()));
        assessment.setCustomizationFields(dto.getCustomizationFields());
        assessment.setColorScheme(dto.getColorScheme());
        assessment.getLabels().addAll(dto.getLabels());
        assessment.setCreatedOn(dto.getCreatedOn());
        assessment.setModifiedOn(dto.getModifiedOn());
        assessment.setDeleted(dto.isDeleted());
        assessment.setVersion(dto.getVersion());
        assessment.setImageResource(dto.getImageResource());
        assessment.setFrameworkIdentifier(dto.getFrameworkIdentifier());
        assessment.setJsonSchemaUrl(dto.getJsonSchemaUrl());
        assessment.setCategory(dto.getCategory());
        assessment.setMinAge(dto.getMinAge());
        assessment.setMaxAge(dto.getMaxAge());
        assessment.setAdditionalMetadata(dto.getAdditionalMetadata());
        assessment.setPhase(dto.getPhase());
        return assessment;
    }

    @Id
    private String guid;
    private String appId;
    private String identifier;
    private int revision = 1;
    private String title;
    private String summary;
    private String validationStatus;
    private String normingStatus;
    private Integer minutesToComplete;
    // same constants used in OperatingSystem
    private String osName;
    
    // For imported assessments, they retain a link to the assessment they
    // were copied from. The origin assessment will be in the shared 
    // assessment library. Every revision has a unique GUID to make it 
    // easier to query for these.
    private String originGuid;
    
    // In local apps this is the ID of an organization. In the shared context, 
    // this is an appId, ":", and an organization ID (e.g. "appId:orgId").
    private String ownerId;
    
    @ManyToMany(cascade = { MERGE, PERSIST }, fetch = EAGER)
    @JoinTable(name = "AssessmentTags",
        joinColumns = { @JoinColumn(name = "assessmentGuid") }, 
        inverseJoinColumns = { @JoinColumn(name = "tagValue")}
    )
    private Set<Tag> tags;
    
    @Convert(converter = CustomizationFieldsConverter.class)
    private Map<String,Set<PropertyInfo>> customizationFields;
    
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime createdOn;
    
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime modifiedOn;
    
    @Convert(converter = ColorSchemeConverter.class)
    private ColorScheme colorScheme;
    
    @Column(columnDefinition = "text", name = "labels", nullable = true)
    @Convert(converter = LabelListConverter.class)
    private List<Label> labels;
    
    private boolean deleted;
    
    @Version
    private long version;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "name", column = @Column(name = "imageResourceName")),
            @AttributeOverride(name = "module", column = @Column(name = "imageResourceModule")),
            @AttributeOverride(name = "labels", column = @Column(name = "imageResourceLabels"))
    })
    private ImageResource imageResource;

    private String frameworkIdentifier;
    private String jsonSchemaUrl;
    private String category;
    private Integer minAge;
    private Integer maxAge;
    @Convert(converter = JsonNodeAttributeConverter.class)
    private JsonNode additionalMetadata;
    @Enumerated(EnumType.STRING)
    private AssessmentPhase phase;

    public String getGuid() {
        return guid;
    }
    public void setGuid(String guid) {
        this.guid = guid;
    }
    public String getAppId() {
        return appId;
    }
    public void setAppId(String appId) {
        this.appId = appId;
    }
    public String getIdentifier() {
        return identifier;
    }
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }
    public String getTitle() {
        return title;
    }
    public void setTitle(String title) {
        this.title = title;
    }
    public String getSummary() {
        return summary;
    }
    public void setSummary(String summary) {
        this.summary = summary;
    }
    public String getValidationStatus() {
        return validationStatus;
    }
    public void setValidationStatus(String validationStatus) {
        this.validationStatus = validationStatus;
    }
    public String getNormingStatus() {
        return normingStatus;
    }
    public void setNormingStatus(String normingStatus) {
        this.normingStatus = normingStatus;
    }
    public Integer getMinutesToComplete() {
        return this.minutesToComplete;
    }
    public void setMinutesToComplete(Integer minutesToComplete) {
        this.minutesToComplete = minutesToComplete;
    }
    public String getOsName() {
        return osName;
    }
    public void setOsName(String os) {
        this.osName = os;
    }
    public String getOriginGuid() {
        return originGuid;
    }
    public void setOriginGuid(String originGuid) {
        this.originGuid = originGuid;
    }
    public String getOwnerId() {
        return ownerId;
    }
    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }
    public Set<Tag> getTags() {
        return tags;
    }
    public void setTags(Set<Tag> tags) {
        this.tags = tags;
    }
    public Map<String, Set<PropertyInfo>> getCustomizationFields() {
        return customizationFields;
    }
    public void setCustomizationFields(Map<String, Set<PropertyInfo>> customizationFields) {
        this.customizationFields = customizationFields;
    }
    public DateTime getCreatedOn() {
        return createdOn;
    }
    public void setCreatedOn(DateTime createdOn) {
        this.createdOn = createdOn;
    }
    public DateTime getModifiedOn() {
        return modifiedOn;
    }
    public void setModifiedOn(DateTime modifiedOn) {
        this.modifiedOn = modifiedOn;
    }
    public ColorScheme getColorScheme() {
        return colorScheme;
    }
    public void setColorScheme(ColorScheme colorScheme) {
        this.colorScheme = colorScheme;
    }
    public List<Label> getLabels() {
        if (labels == null) {
            labels = new ArrayList<>();
        }
        return labels;
    }
    public void setLabels(List<Label> labels) {
        this.labels = labels;
    }
    public boolean isDeleted() {
        return deleted;
    }
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
    public int getRevision() {
        return revision;
    }
    public void setRevision(int revision) {
        this.revision = revision;
    }
    public long getVersion() {
        return version;
    }
    public void setVersion(long version) {
        this.version = version;
    }
    public ImageResource getImageResource() {
        return imageResource;
    }
    public void setImageResource(ImageResource imageResource) {
        this.imageResource = imageResource;
    }
    public String getFrameworkIdentifier() {
        return frameworkIdentifier;
    }
    public void setFrameworkIdentifier(String frameworkIdentifier) {
        this.frameworkIdentifier = frameworkIdentifier;
    }
    public String getJsonSchemaUrl() {
        return jsonSchemaUrl;
    }
    public void setJsonSchemaUrl(String jsonSchemaUrl) {
        this.jsonSchemaUrl = jsonSchemaUrl;
    }
    public String getCategory() {
        return category;
    }
    public void setCategory(String category) {
        this.category = category;
    }
    public Integer getMinAge() {
        return minAge;
    }
    public void setMinAge(Integer minAge) {
        this.minAge = minAge;
    }
    public Integer getMaxAge() {
        return maxAge;
    }
    public void setMaxAge(Integer maxAge) {
        this.maxAge = maxAge;
    }
    public JsonNode getAdditionalMetadata() {
        return additionalMetadata;
    }
    public void setAdditionalMetadata(JsonNode additionalMetadata) {
        this.additionalMetadata = additionalMetadata;
    }
    public AssessmentPhase getPhase() {
        return phase;
    }
    public void setPhase(AssessmentPhase phase) {
        this.phase = phase;
    }
}

