package org.sagebionetworks.bridge.models.files;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.hibernate.DateTimeToLongAttributeConverter;
import org.sagebionetworks.bridge.models.BridgeEntity;

@Entity
@Table(name = "FileMetadata")
public final class FileMetadata implements BridgeEntity {
    @JsonIgnore
    @Column(name = "studyId")
    private String appId;
    private String name;
    @Id
    private String guid;
    private String description;
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime createdOn;
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    private DateTime modifiedOn;
    private boolean deleted;
    @Enumerated(EnumType.STRING)
    private FileDispositionType disposition;
    @Version
    private long version;
    
    public String getAppId() {
        return appId;
    }
    public void setAppId(String appId) {
        this.appId = appId;
    }
    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public String getGuid() {
        return guid;
    }
    public void setGuid(String guid) {
        this.guid = guid;
    }
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    /** Files support logical deletion. Revisions remain accessible on S3 for released clients and configurations. */
    public boolean isDeleted() {
        return deleted;
    }
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
    public long getVersion() {
        return version;
    }
    public void setVersion(long version) {
        this.version = version;
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
    public FileDispositionType getDisposition() {
        return disposition;
    }
    public void setDisposition(FileDispositionType disposition) {
        this.disposition = disposition;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(appId, deleted, description, guid, name, createdOn, modifiedOn, version,
                disposition);
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        FileMetadata other = (FileMetadata) obj;
        return (Objects.equals(appId, other.appId) &&
                Objects.equals(deleted, other.deleted) &&
                Objects.equals(description, other.description) &&
                Objects.equals(guid, other.guid) &&
                Objects.equals(name, other.name) &&
                Objects.equals(createdOn, other.createdOn) &&
                Objects.equals(modifiedOn, other.modifiedOn) &&
                Objects.equals(version, other.version) &&
                Objects.equals(disposition, other.disposition));
    }
}
