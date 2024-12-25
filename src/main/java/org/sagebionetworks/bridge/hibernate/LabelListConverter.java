package org.sagebionetworks.bridge.hibernate;

import java.util.List;

import jakarta.persistence.Converter;

import com.fasterxml.jackson.core.type.TypeReference;

import org.sagebionetworks.bridge.models.Label;

@Converter
public class LabelListConverter extends BaseJsonAttributeConverter<List<Label>> {

    private static final TypeReference<List<Label>> TYPE_REF = new TypeReference<List<Label>>() {};

    @Override
    public String convertToDatabaseColumn(List<Label> list) {
        return super.serialize(list);
    }
    @Override
    public List<Label> convertToEntityAttribute(String string) {
        return super.deserialize(string, TYPE_REF);
    }
}
