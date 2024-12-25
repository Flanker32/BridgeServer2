package org.sagebionetworks.bridge.models.studies;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.joda.time.DateTime;
import org.sagebionetworks.bridge.hibernate.DateTimeToLongAttributeConverter;
import org.sagebionetworks.bridge.hibernate.JsonNodeAttributeConverter;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.BridgeTypeName;
import org.sagebionetworks.bridge.models.BridgeEntity;
import org.sagebionetworks.bridge.models.accounts.AccountRef;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;

@Entity
@Table(name = "Alerts")
@BridgeTypeName("Alert")
public class Alert implements BridgeEntity {
    @Id
    private String id;
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime createdOn;
    @JsonIgnore
    private String studyId;
    @JsonIgnore
    private String appId;
    @JsonIgnore
    private String userId;
    @Transient
    private AccountRef participant;
    @Enumerated(EnumType.STRING)
    private AlertCategory category;
    @Convert(converter = JsonNodeAttributeConverter.class)
    private JsonNode data;
    // whether the alert has been marked as read (viewed)
    // "read" is reserved in SQL
    @Column(name = "isRead")
    private boolean read;

    public enum AlertCategory {
        NEW_ENROLLMENT,
        TIMELINE_ACCESSED,
        LOW_ADHERENCE,
        UPCOMING_STUDY_BURST,
        STUDY_BURST_CHANGE
    }

    public Alert() {
    }

    public Alert(String id, DateTime createdOn, String studyId, String appId, String userId, AccountRef participant,
            AlertCategory category, JsonNode data, boolean read) {
        this.id = id;
        this.createdOn = createdOn;
        this.studyId = studyId;
        this.appId = appId;
        this.userId = userId;
        this.participant = participant;
        this.category = category;
        this.data = data;
        this.read = read;
    }

    public static Alert newEnrollment(String studyId, String appId, String userId) {
        return new Alert(null, null, studyId, appId, userId, null, AlertCategory.NEW_ENROLLMENT,
                BridgeObjectMapper.get().nullNode(), false);
    }

    public static Alert timelineAccessed(String studyId, String appId, String userId) {
        return new Alert(null, null, studyId, appId, userId, null, AlertCategory.TIMELINE_ACCESSED,
                BridgeObjectMapper.get().nullNode(), false);
    }

    public static Alert lowAdherence(String studyId, String appId, String userId, double adherenceThreshold) {
        return new Alert(null, null, studyId, appId, userId, null, AlertCategory.LOW_ADHERENCE,
                BridgeObjectMapper.get().valueToTree(new LowAdherenceAlertData(adherenceThreshold)), false);
    }

    public static Alert studyBurstChange(String studyId, String appId, String userId) {
        return new Alert(null, null, studyId, appId, userId, null, AlertCategory.STUDY_BURST_CHANGE,
                BridgeObjectMapper.get().nullNode(), false);
    }

    public static class LowAdherenceAlertData {
        double adherenceThreshold;

        public LowAdherenceAlertData(double adherenceThreshold) {
            this.adherenceThreshold = adherenceThreshold;
        }

        public double getAdherenceThreshold() {
            return adherenceThreshold;
        }

        public void setAdherenceThreshold(double adherenceThreshold) {
            this.adherenceThreshold = adherenceThreshold;
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public DateTime getCreatedOn() {
        return createdOn;
    }

    public void setCreatedOn(DateTime createdOn) {
        this.createdOn = createdOn;
    }

    public String getStudyId() {
        return studyId;
    }

    public void setStudyId(String studyId) {
        this.studyId = studyId;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public AccountRef getParticipant() {
        return participant;
    }

    public void setParticipant(AccountRef participant) {
        this.participant = participant;
    }

    public AlertCategory getCategory() {
        return category;
    }

    public void setCategory(AlertCategory category) {
        this.category = category;
    }

    public JsonNode getData() {
        return data;
    }

    public void setData(JsonNode data) {
        this.data = data;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    @Override
    public String toString() {
        return "Alert [id=" + id + ", createdOn=" + createdOn + ", studyId=" + studyId + ", appId=" + appId
                + ", userId=" + userId + ", category=" + category + ", data=" + data + "]";
    }
}
