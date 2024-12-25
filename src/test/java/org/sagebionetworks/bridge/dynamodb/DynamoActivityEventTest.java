package org.sagebionetworks.bridge.dynamodb;

import static org.sagebionetworks.bridge.TestUtils.assertDatesWithTimeZoneEqual;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.ACTIVITIES_RETRIEVED;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.CUSTOM;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.ENROLLMENT;
import static org.sagebionetworks.bridge.models.activities.ActivityEventType.FINISHED;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.FUTURE_ONLY;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.IMMUTABLE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import com.fasterxml.jackson.databind.JsonNode;

import org.joda.time.DateTime;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.dynamodb.DynamoActivityEvent.Builder;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.activities.ActivityEvent;

public class DynamoActivityEventTest {

    @Test
    public void canConstructSimpleEventId() {
        DateTime now = DateTime.now();
        Builder builder = new DynamoActivityEvent.Builder();
        ActivityEvent event = builder.withObjectType(ENROLLMENT).withHealthCode("BBB").withTimestamp(now).build();
        
        assertEquals(event.getHealthCode(), "BBB");
        assertEquals(new DateTime(event.getTimestamp()), now);
        assertEquals(event.getEventId(), "enrollment");
    }
    
    @Test
    public void canConstructEventNoAction() {
        DateTime now = DateTime.now();
        Builder builder = new DynamoActivityEvent.Builder();
        ActivityEvent event = builder.withObjectType(ENROLLMENT).withHealthCode("BBB").withTimestamp(now).build();
        
        assertEquals(event.getHealthCode(), "BBB");
        assertEquals(new DateTime(event.getTimestamp()), now);
        assertEquals(event.getEventId(), "enrollment");
    }
    
    @Test
    public void simpleActivityEventIdIsCorrect() {
        DateTime now = DateTime.now();
        ActivityEvent event = new DynamoActivityEvent.Builder().withHealthCode("BBB")
                .withObjectType(ENROLLMENT).withTimestamp(now).build();
        
        assertEquals(event.getHealthCode(), "BBB");
        assertEquals(new DateTime(event.getTimestamp()), now);
        assertEquals(event.getEventId(), "enrollment");
    }
    
    @Test
    public void activitiesRetrievedEvent() {
        DateTime now = DateTime.now();
        ActivityEvent event = new DynamoActivityEvent.Builder().withObjectType(ACTIVITIES_RETRIEVED)
                .withHealthCode("BBB").withTimestamp(now).build();
        
        assertEquals(event.getHealthCode(), "BBB");
        assertEquals(new DateTime(event.getTimestamp()), now);
        assertEquals(event.getEventId(), "activities_retrieved");
    }
    
    @Test
    public void testBuilder() {
        DateTime timestamp = new DateTime(123L);
        ActivityEvent event = new DynamoActivityEvent.Builder()
                .withHealthCode("healthCode")
                .withTimestamp(timestamp)
                .withObjectType(CUSTOM)
                .withObjectId("objectId")
                .withEventType(FINISHED)
                .withUpdateType(IMMUTABLE)
                .withAnswerValue("true").build();
        
        assertEquals(event.getHealthCode(), "healthCode");
        assertEquals(event.getTimestamp(), timestamp);
        assertEquals(event.getEventId(), "custom:objectId:finished");
        assertEquals(event.getAnswerValue(), "true");
        assertEquals(event.getUpdateType(), IMMUTABLE);

        event = new DynamoActivityEvent.Builder()
                .withHealthCode("healthCode")
                .withTimestamp(timestamp)
                .withObjectType(CUSTOM)
                .withObjectId("objectId")
                .withEventType(FINISHED)
                .withAnswerValue("true").build();
        
        assertEquals(event.getHealthCode(), "healthCode");
    }

    @Test
    public void serialize() throws Exception {
        // Start with JSON.
        String jsonText = """
                {
                   "healthCode":"test-health-code",
                   "eventId":"test-event",
                   "updateType":"mutable",
                   "answerValue":"dummy answer",
                   "timestamp":"2018-08-20T16:15:19.913Z"
                }\
                """;

        // Convert to POJO.
        ActivityEvent activityEvent = BridgeObjectMapper.get().readValue(jsonText, ActivityEvent.class);
        assertNull(activityEvent.getHealthCode());
        assertEquals(activityEvent.getEventId(), "test-event");
        assertEquals(activityEvent.getAnswerValue(), "dummy answer");
        assertDatesWithTimeZoneEqual(activityEvent.getTimestamp(),
                DateTime.parse("2018-08-20T16:15:19.913Z"));
        assertNull(activityEvent.getUpdateType());

        // Convert back to JSON.
        JsonNode activityNode = BridgeObjectMapper.get().valueToTree(activityEvent);
        assertNull(activityNode.get("healthCode"));
        assertEquals(activityNode.get("eventId").textValue(), "test-event");
        assertEquals(activityNode.get("answerValue").textValue(), "dummy answer");
        assertEquals(activityNode.get("timestamp").textValue(), "2018-08-20T16:15:19.913Z");
        assertEquals(activityNode.get("type").textValue(), "ActivityEvent");

        // Test that activity event does not include health code or updateType
        String filteredJsonText = BridgeObjectMapper.get().writeValueAsString(activityEvent);
        JsonNode filteredActivityNode = BridgeObjectMapper.get().readTree(filteredJsonText);
        assertNull(filteredActivityNode.get("healthCode"));
        assertNull(filteredActivityNode.get("updateType"));
        assertEquals(filteredActivityNode.get("eventId").textValue(), "test-event");
        assertEquals(filteredActivityNode.get("answerValue").textValue(), "dummy answer");
        assertEquals(filteredActivityNode.get("timestamp").textValue(), "2018-08-20T16:15:19.913Z");
        assertEquals(filteredActivityNode.get("type").textValue(), "ActivityEvent");
        
        // without a studyId
        jsonText = """
                {
                   "healthCode":"test-health-code",
                   "eventId":"test-event",
                   "answerValue":"dummy answer",
                   "timestamp":"2018-08-20T16:15:19.913Z"
                }\
                """;
        
        activityEvent = BridgeObjectMapper.get().readValue(jsonText, ActivityEvent.class);
        assertNull(activityEvent.getHealthCode());
        assertEquals(activityEvent.getEventId(), "test-event");
        assertEquals(activityEvent.getAnswerValue(), "dummy answer");
        assertEquals(activityEvent.getTimestamp(), DateTime.parse("2018-08-20T16:15:19.913Z"));

        // Convert back to JSON.
        activityNode = BridgeObjectMapper.get().valueToTree(activityEvent);
        assertNull(activityNode.get("healthCode"));
        assertEquals(activityNode.get("eventId").textValue(), "test-event");
        assertEquals(activityNode.get("answerValue").textValue(), "dummy answer");
        assertEquals(activityNode.get("timestamp").textValue(), "2018-08-20T16:15:19.913Z");
        assertEquals(activityNode.get("type").textValue(), "ActivityEvent");
        
        filteredJsonText = BridgeObjectMapper.get().writeValueAsString(activityEvent);
        filteredActivityNode = BridgeObjectMapper.get().readTree(filteredJsonText);
        assertNull(filteredActivityNode.get("healthCode"));
    }
    
    @Test
    public void updateTypePickedUpFromObjecType() {
        DynamoActivityEvent event = new DynamoActivityEvent.Builder()
                .withObjectType(ACTIVITIES_RETRIEVED).build();
        
        assertEquals(event.getUpdateType(), IMMUTABLE);
    }

    @Test
    public void updateTypeForCustomIsImmutable() {
        DynamoActivityEvent event = new DynamoActivityEvent.Builder().withObjectType(CUSTOM).build();
        assertEquals(event.getUpdateType(), IMMUTABLE);
    }

    @Test(expectedExceptions = IllegalStateException.class, 
            expectedExceptionsMessageRegExp = "No update type configured.*")
    public void updateTypeCannotBeNull() {
        new DynamoActivityEvent.Builder().build();
    }
    
    public void updateTypeCanBeOverridden() { 
        DynamoActivityEvent event = new DynamoActivityEvent.Builder()
                .withObjectType(CUSTOM)
                .withUpdateType(FUTURE_ONLY).build();
        
        assertEquals(event.getUpdateType(), FUTURE_ONLY);
    }
}
