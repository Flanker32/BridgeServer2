package org.sagebionetworks.bridge.dynamodb;

import com.fasterxml.jackson.databind.JsonNode;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class DynamoParticipantFileTest {
    private static final BridgeObjectMapper MAPPER = BridgeObjectMapper.get();

    @Test
    public void canSerialize() throws Exception {
        DynamoParticipantFile pFile = new DynamoParticipantFile("userId", "fileId", TestConstants.TIMESTAMP);

        String json = MAPPER.writeValueAsString(pFile);
        JsonNode node = MAPPER.readTree(json);

        assertEquals(node.get("userId").textValue(), "userId");
        assertEquals(node.get("fileId").textValue(), "fileId");
        assertEquals(node.get("createdOn").textValue(), TestConstants.TIMESTAMP.toString());
        assertEquals(node.size(), 4);
    }
}
