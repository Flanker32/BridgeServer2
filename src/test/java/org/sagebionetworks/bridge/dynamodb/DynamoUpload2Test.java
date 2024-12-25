package org.sagebionetworks.bridge.dynamodb;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.upload.UploadCompletionClient;
import org.sagebionetworks.bridge.models.upload.UploadStatus;

public class DynamoUpload2Test {
    private static final String CLIENT_INFO = "dummy client info";

    /**
     * We will be returning this object through the API in a later update to the server. For now, 
     * we just want to know we are persisting an object that can return the correct JSON. We 
     * never read this object in <i>from</i> JSON.
     */
    @Test
    public void canSerialize() throws Exception {
        DateTime requestedOn = DateTime.now().withZone(DateTimeZone.UTC);
        DateTime completedOn = DateTime.now().withZone(DateTimeZone.UTC);
        ObjectNode metadata = (ObjectNode) BridgeObjectMapper.get().readTree("{\"key\":\"value\"}");
        
        DynamoUpload2 upload = new DynamoUpload2();
        upload.setClientInfo(CLIENT_INFO);
        upload.setCompletedBy(UploadCompletionClient.S3_WORKER);
        upload.setRequestedOn(requestedOn.getMillis());
        upload.setCompletedOn(completedOn.getMillis());
        upload.setContentLength(10000L);
        upload.setContentMd5("abc");
        upload.setContentType("application/json");
        upload.setDuplicateUploadId("original-upload-id");
        upload.setEncrypted(false);
        upload.setFilename("filename.zip");
        upload.setHealthCode("healthCode");
        upload.setMetadata(metadata);
        upload.setRecordId("ABC");
        upload.setStatus(UploadStatus.SUCCEEDED);
        upload.setAppId(TEST_APP_ID);
        upload.setUploadDate(LocalDate.parse("2016-10-10"));
        upload.setUploadId("DEF");
        upload.setUserAgent(TestConstants.UA);
        upload.setValidationMessageList(Lists.newArrayList("message 1", "message 2"));
        upload.setVersion(2L);
        upload.setZipped(false);
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(upload);
        assertEquals(node.get("clientInfo").textValue(), CLIENT_INFO);
        assertEquals(node.get("completedBy").textValue(), "s3_worker");
        assertEquals(node.get("requestedOn").textValue(), requestedOn.toString());
        assertEquals(node.get("completedOn").textValue(), completedOn.toString());
        assertEquals(node.get("contentLength").longValue(), 10000L);
        assertEquals(node.get("duplicateUploadId").textValue(), "original-upload-id");
        assertFalse(node.get("encrypted").booleanValue());
        assertEquals(node.get("metadata"), metadata);
        assertEquals(node.get("status").textValue(), "succeeded");
        assertEquals(node.get("uploadDate").textValue(), "2016-10-10");
        assertEquals(node.get("recordId").textValue(), "ABC");
        assertEquals(node.get("uploadId").textValue(), "DEF");
        assertEquals(node.get("contentMd5").textValue(), "abc");
        assertEquals(node.get("contentType").textValue(), "application/json");
        assertEquals(node.get("filename").textValue(), "filename.zip");
        assertEquals(node.get("appId").textValue(), TEST_APP_ID);
        assertEquals(node.get("studyId").textValue(), TEST_APP_ID);
        assertEquals(node.get("userAgent").textValue(), TestConstants.UA);
        assertEquals(node.get("version").longValue(), 2L);
        assertEquals(node.get("healthCode").textValue(), "healthCode");
        assertFalse(node.get("zipped").booleanValue());
        
        ArrayNode messages = (ArrayNode)node.get("validationMessageList");
        assertEquals(messages.get(0).textValue(), "message 1");
        assertEquals(messages.get(1).textValue(), "message 2");
    }

    @Test
    public void testEncryptedAndZipped() {
        // Defaults to true.
        DynamoUpload2 upload = new DynamoUpload2();
        assertTrue(upload.isEncrypted());
        assertTrue(upload.isZipped());

        // Can set to false.
        upload.setEncrypted(false);
        upload.setZipped(false);
        assertFalse(upload.isEncrypted());
        assertFalse(upload.isZipped());

        // Can set to true.
        upload.setEncrypted(true);
        upload.setZipped(true);
        assertTrue(upload.isEncrypted());
        assertTrue(upload.isZipped());
    }

    @Test
    public void testGetSetValidationMessageList() {
        DynamoUpload2 upload2 = new DynamoUpload2();

        // initial get gives empty list
        List<String> initialList = upload2.getValidationMessageList();
        assertTrue(initialList.isEmpty());

        // set and validate
        upload2.setValidationMessageList(ImmutableList.of("first message"));
        List<String> list2 = upload2.getValidationMessageList();
        assertTrue(initialList.isEmpty());

        assertEquals(list2.size(), 1);
        assertEquals(list2.getFirst(), "first message");

        // set should overwrite
        upload2.setValidationMessageList(ImmutableList.of("second message"));
        List<String> list3 = upload2.getValidationMessageList();
        assertTrue(initialList.isEmpty());

        assertEquals(list2.size(), 1);
        assertEquals(list2.getFirst(), "first message");

        assertEquals(list3.size(), 1);
        assertEquals(list3.getFirst(), "second message");
    }

    @Test
    public void testGetAppendValidationMessageList() {
        DynamoUpload2 upload2 = new DynamoUpload2();

        // initial get gives empty list
        List<String> initialList = upload2.getValidationMessageList();
        assertTrue(initialList.isEmpty());

        // append and validate
        upload2.appendValidationMessages(ImmutableList.of("first message"));
        List<String> list2 = upload2.getValidationMessageList();
        assertTrue(initialList.isEmpty());

        assertEquals(list2.size(), 1);
        assertEquals(list2.getFirst(), "first message");

        // append again
        upload2.appendValidationMessages(ImmutableList.of("second message"));
        List<String> list3 = upload2.getValidationMessageList();
        assertTrue(initialList.isEmpty());

        assertEquals(list2.size(), 1);
        assertEquals(list2.getFirst(), "first message");

        assertEquals(list3.size(), 2);
        assertEquals(list3.getFirst(), "first message");
        assertEquals(list3.get(1), "second message");
    }

    @Test
    public void testGetSetAppendValidationMessageList() {
        DynamoUpload2 upload2 = new DynamoUpload2();

        // initial get gives empty list
        List<String> initialList = upload2.getValidationMessageList();
        assertTrue(initialList.isEmpty());

        // set on an empty list
        upload2.setValidationMessageList(ImmutableList.of("first message"));
        List<String> list2 = upload2.getValidationMessageList();
        assertTrue(initialList.isEmpty());

        assertEquals(list2.size(), 1);
        assertEquals(list2.getFirst(), "first message");

        // append on a set
        upload2.appendValidationMessages(ImmutableList.of("second message"));
        List<String> list3 = upload2.getValidationMessageList();
        assertTrue(initialList.isEmpty());

        assertEquals(list2.size(), 1);
        assertEquals(list2.getFirst(), "first message");

        assertEquals(list3.size(), 2);
        assertEquals(list3.getFirst(), "first message");
        assertEquals(list3.get(1), "second message");

        // set should overwrite the append
        upload2.setValidationMessageList(ImmutableList.of("third message"));
        List<String> list4 = upload2.getValidationMessageList();
        assertTrue(initialList.isEmpty());

        assertEquals(list2.size(), 1);
        assertEquals(list2.getFirst(), "first message");

        assertEquals(list3.size(), 2);
        assertEquals(list3.getFirst(), "first message");
        assertEquals(list3.get(1), "second message");

        assertEquals(list4.size(), 1);
        assertEquals(list4.getFirst(), "third message");
    }
    
    @Test
    public void emptyTimestampsAreNotSerialized() throws Exception {
        DynamoUpload2 upload = new DynamoUpload2();
        
        JsonNode node = BridgeObjectMapper.get().valueToTree(upload);
        assertNull(node.get("requestedOn"));
        assertNull(node.get("completedOn"));
    }
}
