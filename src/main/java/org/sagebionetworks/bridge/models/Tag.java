package org.sagebionetworks.bridge.models;

import java.util.Objects;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.NaturalId;

/**
 * Generic tag implementation for tagged entities. Each field that implements a set of 
 * tags should set a different category on the tag so they can be stored in the same 
 * table. 
 */
@Entity
@Table(name = "Tags")
public final class Tag {
    @Id
    @NaturalId
    private String value;
    
    public Tag() {}
    
    public Tag(String value) {
        this.value = value;
    }
    public String getValue() {
        return value;
    }
    public void setValue(String value) {
        this.value = value;
    }
    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        Tag other = (Tag) obj;
        return Objects.equals(value, other.value);
    }
}
