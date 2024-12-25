package org.sagebionetworks.bridge.hibernate;

import java.io.IOException;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import jakarta.persistence.PersistenceException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Converter
public class JsonNodeAttributeConverter implements AttributeConverter<JsonNode,String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    @Override
    public String convertToDatabaseColumn(JsonNode node) {
        return (node == null) ? null : node.toString();
    }

    @Override
    public JsonNode convertToEntityAttribute(String string) {
        try {
            return (string == null) ? null : MAPPER.readTree(string);
        } catch (IOException e) {
            throw new PersistenceException(e);
        }
    }

}
