package org.sagebionetworks.bridge.models;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.hibernate.ClientInfoConverter;
import org.sagebionetworks.bridge.hibernate.DateTimeToLongAttributeConverter;
import org.sagebionetworks.bridge.hibernate.DateTimeZoneAttributeConverter;
import org.sagebionetworks.bridge.hibernate.StringListConverter;
import org.sagebionetworks.bridge.hibernate.StringSetConverter;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.json.DateTimeSerializer;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;

/**
 * Information about the criteria and access times of requests from a specific user. Useful for 
 * support and troubleshooting, and potentially useful to show the filtering that is occurring on 
 * the server to researchers in the researcher UI.
 */
@Entity
@Table(name = "RequestInfos")
@JsonDeserialize(builder = RequestInfo.Builder.class)
@JsonFilter("filter")
public final class RequestInfo {
    public static final ObjectWriter REQUEST_INFO_WRITER = new BridgeObjectMapper().writer(
            new SimpleFilterProvider().addFilter("filter",
                    SimpleBeanPropertyFilter.serializeAllExcept("appId")));
    
    // By putting Hibernate annotations on the fields, we do not need to provide setter methods and
    // can continue using our Builder pattern.
    @Id
    private String userId;
    @Convert(converter = ClientInfoConverter.class)
    private ClientInfo clientInfo;
    private String userAgent;
    @Convert(converter = StringListConverter.class)
    private List<String> languages;
    @Convert(converter = StringSetConverter.class)
    private Set<String> userDataGroups;
    @Convert(converter = StringSetConverter.class)
    @Column(name = "userSubstudyIds")
    private Set<String> userStudyIds;
    @JsonSerialize(using=DateTimeSerializer.class)
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime activitiesAccessedOn;
    @JsonSerialize(using=DateTimeSerializer.class)
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime signedInOn;
    @JsonSerialize(using=DateTimeSerializer.class)
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime uploadedOn;
    @Convert(converter = DateTimeZoneAttributeConverter.class)
    private DateTimeZone timeZone;
    @Column(name = "studyIdentifier")
    private String appId;
    @JsonSerialize(using=DateTimeSerializer.class)
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime timelineAccessedOn;

    // Hibernate needs a default constructor; the easiest thing to do is to remove the 
    // existing constructor and allow the builder to set private fields directly.
    
    public String getUserId() {
        return userId;
    }

    public ClientInfo getClientInfo() {
        return clientInfo;
    }
    
    public String getUserAgent() {
        return userAgent;
    }

    public List<String> getLanguages() {
        return languages;
    }

    public Set<String> getUserDataGroups() {
        return userDataGroups;
    }

    public Set<String> getUserStudyIds() {
        return userStudyIds;
    }
    
    public DateTime getActivitiesAccessedOn() {
        return (activitiesAccessedOn == null) ? null : activitiesAccessedOn.withZone(timeZone);
    }

    public DateTime getTimelineAccessedOn() {
        return (timelineAccessedOn == null) ? null : timelineAccessedOn.withZone(timeZone);
    }
    
    public DateTime getSignedInOn() {
        return (signedInOn == null) ? null : signedInOn.withZone(timeZone);
    }
    
    public DateTime getUploadedOn() {
        return (uploadedOn == null) ? null : uploadedOn.withZone(timeZone);
    }
    
    public DateTimeZone getTimeZone() {
        return timeZone;
    }
    
    public String getAppId() {
        return appId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(getTimelineAccessedOn(), getActivitiesAccessedOn(), clientInfo, userAgent, languages,
                getSignedInOn(), userDataGroups, userStudyIds, userId, timeZone, uploadedOn, appId);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        RequestInfo other = (RequestInfo) obj;
        return Objects.equals(getTimelineAccessedOn(), other.getTimelineAccessedOn()) &&
               Objects.equals(getActivitiesAccessedOn(), other.getActivitiesAccessedOn()) &&
               Objects.equals(clientInfo, other.clientInfo) &&
               Objects.equals(userAgent, other.userAgent) &&
               Objects.equals(languages, other.languages) &&
               Objects.equals(getSignedInOn(), other.getSignedInOn()) && 
               Objects.equals(getUploadedOn(), other.getUploadedOn()) &&
               Objects.equals(userDataGroups, other.userDataGroups) && 
               Objects.equals(userStudyIds, other.userStudyIds) && 
               Objects.equals(userId, other.userId) && 
               Objects.equals(timeZone, other.timeZone) && 
               Objects.equals(appId, other.appId);
    }
    
    @Override
    public String toString() {
        return "RequestInfo [userId=" + userId + ", userAgent=" + userAgent + ", languages=" + languages
                + ", userDataGroups=" + userDataGroups + ", userStudyIds=" + userStudyIds 
                + ", activitiesAccessedOn=" + getActivitiesAccessedOn() + ", signedInOn=" + getSignedInOn() 
                + ", uploadedOn=" + getUploadedOn() + ", timeZone=" + timeZone + ", appId=" 
                + appId + ", timelineAccessedOn=" + getTimelineAccessedOn() + "]";
    }

    public static class Builder {
        private String userId;
        private ClientInfo clientInfo;
        private String userAgent;
        private List<String> languages;
        private Set<String> userDataGroups;
        private Set<String> userStudyIds;
        private DateTime activitiesAccessedOn;
        private DateTime timelineAccessedOn;
        private DateTime signedInOn;
        private DateTime uploadedOn;
        private DateTimeZone timeZone = DateTimeZone.UTC;
        private String appId;

        public Builder copyOf(RequestInfo requestInfo) {
            if (requestInfo != null) {
                withUserId(requestInfo.getUserId());
                withClientInfo(requestInfo.getClientInfo());
                withUserAgent(requestInfo.getUserAgent());
                withLanguages(requestInfo.getLanguages());
                withUserDataGroups(requestInfo.getUserDataGroups());
                withUserStudyIds(requestInfo.getUserStudyIds());
                withActivitiesAccessedOn(requestInfo.getActivitiesAccessedOn());
                withTimelineAccessedOn(requestInfo.getTimelineAccessedOn());
                withSignedInOn(requestInfo.getSignedInOn());
                withUploadedOn(requestInfo.getUploadedOn());
                withTimeZone(requestInfo.getTimeZone());
                withAppId(requestInfo.getAppId());
            }
            return this;
        }
        public Builder withUserId(String userId) {
            if (userId != null) {
                this.userId = userId;
            }
            return this;
        }
        public Builder withClientInfo(ClientInfo clientInfo) {
            if (clientInfo != null) {
                this.clientInfo = clientInfo;
            }
            return this;
        }
        public Builder withUserAgent(String userAgent) {
            if (userAgent != null) {
                if (userAgent.length() > BridgeConstants.MAX_USER_AGENT_LENGTH) {
                    userAgent = userAgent.substring(0, BridgeConstants.MAX_USER_AGENT_LENGTH);
                }
                this.userAgent = userAgent;
            }
            return this;
        }
        public Builder withLanguages(List<String> languages) {
            if (languages != null) {
                this.languages = languages;
            }
            return this;
        }
        public Builder withUserDataGroups(Set<String> userDataGroups) {
            if (userDataGroups != null) {
                this.userDataGroups = userDataGroups;
            }
            return this;
        }
        @JsonAlias("userSubstudyIds")
        public Builder withUserStudyIds(Set<String> userStudyIds) {
            if (userStudyIds != null) {
                this.userStudyIds = userStudyIds;
            }
            return this;
        }
        public Builder withActivitiesAccessedOn(DateTime activitiesAccessedOn) {
            if (activitiesAccessedOn != null) {
                this.activitiesAccessedOn = activitiesAccessedOn;
            }
            return this;
        }
        public Builder withTimelineAccessedOn(DateTime timelineAccessedOn) {
            if (timelineAccessedOn != null) {
                this.timelineAccessedOn = timelineAccessedOn;
            }
            return this;
        }
        public Builder withSignedInOn(DateTime signedInOn) {
            if (signedInOn != null) {
                this.signedInOn = signedInOn;
            }
            return this;
        }
        public Builder withUploadedOn(DateTime uploadedOn) {
            if (uploadedOn != null) {
                this.uploadedOn = uploadedOn;
            }
            return this;
        }
        public Builder withTimeZone(DateTimeZone timeZone) {
            if (timeZone != null) {
                this.timeZone = timeZone;
            }
            return this;
        }
        public Builder withAppId(String appId) {
            if (appId != null) {
                this.appId = appId;    
            }
            return this;
        }
        
        public RequestInfo build() {
            RequestInfo info = new RequestInfo();
            info.userId = userId;
            info.clientInfo = clientInfo;
            info.userAgent = userAgent;
            info.languages = languages;
            info.userDataGroups = userDataGroups;
            info.userStudyIds = userStudyIds;
            info.activitiesAccessedOn = activitiesAccessedOn;
            info.timelineAccessedOn = timelineAccessedOn;
            info.signedInOn = signedInOn;
            info.uploadedOn = uploadedOn;
            info.timeZone = timeZone;
            info.appId = appId;
            return info;
        }
    }
    
}