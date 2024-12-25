package org.sagebionetworks.bridge.hibernate;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.joda.time.DateTime;

import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.templates.Template;
import org.sagebionetworks.bridge.models.templates.TemplateType;

@Entity
@Table(name = "Templates")
public final class HibernateTemplate implements Template {

    private String guid;
    private String appId;
    private TemplateType type;
    private String name;
    private String description;
    private Criteria criteria;
    private DateTime createdOn;
    private DateTime modifiedOn;
    private DateTime publishedCreatedOn;
    private boolean deleted;
    private int version;
    
    @JsonIgnore
    @Override
    @Column(name = "studyId")
    public String getAppId() { 
        return appId;
    }
    @Override
    public void setAppId(String appId) {
        this.appId = appId;
    }
    @Id
    @Override
    public String getGuid() {
        return guid;
    }
    @Override
    public void setGuid(String guid) {
        this.guid = guid;
    }
    @Override
    @Enumerated(EnumType.STRING)
    public TemplateType getTemplateType() {
        return type;
    }
    @Override
    public void setTemplateType(TemplateType type) {
        this.type = type;
    }
    @Override
    public String getName() {
        return name;
    }
    @Override
    public void setName(String name) {
        this.name = name;
    }
    @Override
    public String getDescription() {
        return description;
    }
    @Override
    public void setDescription(String description) {
        this.description = description;
    }
    @Transient
    @Override
    public Criteria getCriteria() {
        return criteria;
    }
    @Override
    public void setCriteria(Criteria criteria) {
        this.criteria = criteria;
    }
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    @Override
    public DateTime getCreatedOn() {
        return createdOn;
    }
    @Override
    public void setCreatedOn(DateTime createdOn) {
        this.createdOn = createdOn;
    }
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    @Override
    public DateTime getModifiedOn() {
        return modifiedOn;
    }
    @Override
    public void setModifiedOn(DateTime modifiedOn) {
        this.modifiedOn = modifiedOn;
    }
    @Convert(converter = DateTimeToLongAttributeConverter.class)
    @Override
    public DateTime getPublishedCreatedOn() {
        return publishedCreatedOn;
    }
    @Override
    public void setPublishedCreatedOn(DateTime publishedCreatedOn) {
        this.publishedCreatedOn = publishedCreatedOn;
    }
    @Override
    public boolean isDeleted() {
        return deleted;
    }
    @Override
    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }
    @Version
    @Override
    public int getVersion() {
        return version;
    }
    @Override
    public void setVersion(int version) {
        this.version = version;
    }
}
