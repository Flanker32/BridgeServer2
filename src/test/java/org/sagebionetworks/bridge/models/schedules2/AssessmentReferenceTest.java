package org.sagebionetworks.bridge.models.schedules2;

import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.LABELS;
import static org.testng.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.ImmutableList;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.assessments.ColorScheme;
import org.sagebionetworks.bridge.models.assessments.ImageResource;

public class AssessmentReferenceTest {
    
    @Test
    public void canSerialize() throws Exception {
        ColorScheme scheme = new ColorScheme("#111111", "#222222", "#333333", "#444444");
        
        AssessmentReference ref = new AssessmentReference();
        ref.setGuid(GUID);
        ref.setAppId("shared");
        ref.setTitle("Title");
        ref.setIdentifier("identifier");
        ref.setRevision(5);
        ref.setMinutesToComplete(10);
        ref.setLabels(LABELS);
        ref.setColorScheme(scheme);
        ImageResource imageResource = new ImageResource();
        imageResource.setName("default");
        imageResource.setModule("sage_survey");
        imageResource.setLabels(LABELS);
        ref.setImageResource(imageResource);

        JsonNode node = BridgeObjectMapper.get().valueToTree(ref);
        assertEquals(node.get("guid").textValue(), GUID);
        assertEquals(node.get("appId").textValue(), "shared");
        assertEquals(node.get("title").textValue(), "Title");
        assertEquals(node.get("identifier").textValue(), "identifier");
        assertEquals(node.get("revision").intValue(), 5);
        assertEquals(node.get("minutesToComplete").intValue(), 10);
        
        assertEquals(node.get("colorScheme").get("background").textValue(), "#111111");
        assertEquals(node.get("colorScheme").get("foreground").textValue(), "#222222");
        assertEquals(node.get("colorScheme").get("activated").textValue(), "#333333");
        assertEquals(node.get("colorScheme").get("inactivated").textValue(), "#444444");
        assertEquals(node.get("colorScheme").get("type").textValue(), "ColorScheme");
        assertEquals(node.get("type").textValue(), "AssessmentReference");

        assertEquals(node.get("imageResource").get("name").textValue(), "default");
        assertEquals(node.get("imageResource").get("module").textValue(), "sage_survey");
        assertEquals(node.get("imageResource").get("labels").get(0).get("lang").textValue(), LABELS.getFirst().getLang());
        assertEquals(node.get("imageResource").get("labels").get(0).get("value").textValue(), LABELS.getFirst().getValue());
        assertEquals(node.get("imageResource").get("labels").get(1).get("lang").textValue(), LABELS.get(1).getLang());
        assertEquals(node.get("imageResource").get("labels").get(1).get("value").textValue(), LABELS.get(1).getValue());
        assertEquals(node.get("imageResource").get("type").textValue(), "ImageResource");

        ArrayNode arrayNode = (ArrayNode)node.get("labels");
        assertEquals(arrayNode.get(0).get("lang").textValue(), "en");
        assertEquals(arrayNode.get(0).get("value").textValue(), "English");
        
        assertEquals(arrayNode.get(1).get("lang").textValue(), "fr");
        assertEquals(arrayNode.get(1).get("value").textValue(), "French");
        
        AssessmentReference deser = BridgeObjectMapper.get()
                .readValue(node.toString(), AssessmentReference.class);
        assertEquals(deser.getGuid(), GUID);
        assertEquals(deser.getAppId(), "shared");
        assertEquals(deser.getTitle(), "Title");
        assertEquals(deser.getMinutesToComplete(), Integer.valueOf(10));
        assertEquals(deser.getColorScheme(), scheme);
        assertEquals(deser.getLabels().getFirst().getValue(), LABELS.getFirst().getValue());
        assertEquals(deser.getLabels().get(1).getValue(), LABELS.get(1).getValue());
        assertEquals(deser.getImageResource().getName(), "default");
        assertEquals(deser.getImageResource().getModule(), "sage_survey");
        assertEquals(deser.getImageResource().getLabels().getFirst().getLang(), LABELS.getFirst().getLang());
        assertEquals(deser.getImageResource().getLabels().getFirst().getValue(), LABELS.getFirst().getValue());
        assertEquals(deser.getImageResource().getLabels().get(1).getLang(), LABELS.get(1).getLang());
        assertEquals(deser.getImageResource().getLabels().get(1).getValue(), LABELS.get(1).getValue());
    }
    
    @Test
    public void nullLabelsReturnsEmptyList() {
        AssessmentReference ref = new AssessmentReference();
        assertEquals(ref.getLabels(), ImmutableList.of());
    }
}
