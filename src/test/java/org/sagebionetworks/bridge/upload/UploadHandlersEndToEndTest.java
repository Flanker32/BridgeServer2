package org.sagebionetworks.bridge.upload;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.codec.digest.DigestUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.LocalDate;
import org.mockito.ArgumentCaptor;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.dao.UploadDao;
import org.sagebionetworks.bridge.dynamodb.DynamoApp;
import org.sagebionetworks.bridge.dynamodb.DynamoSurvey;
import org.sagebionetworks.bridge.file.InMemoryFileHelper;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.services.ParticipantService;
import org.sagebionetworks.bridge.time.DateUtils;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.studies.Enrollment;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.upload.UploadFieldDefinition;
import org.sagebionetworks.bridge.models.upload.UploadFieldType;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.models.upload.UploadSchemaType;
import org.sagebionetworks.bridge.models.upload.UploadStatus;
import org.sagebionetworks.bridge.s3.S3Helper;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.HealthDataService;
import org.sagebionetworks.bridge.services.AppService;
import org.sagebionetworks.bridge.services.SurveyService;
import org.sagebionetworks.bridge.services.UploadArchiveService;
import org.sagebionetworks.bridge.services.UploadSchemaService;

public class UploadHandlersEndToEndTest {
    private static final String APP_VERSION = "version 1.0.0, build 1";
    private static final Set<String> DATA_GROUP_SET = ImmutableSet.of("parkinson", "test_user");
    private static final String EXTERNAL_ID = "external-id";
    private static final String HEALTH_CODE = "health-code";
    private static final byte[] METADATA_JSON_CONTENT = "{\"meta-file-key\":\"meta-file-value\"}".getBytes();
    private static final String PHONE_INFO = "Unit Test Hardware";
    private static final String UPLOAD_FILENAME = "upload-filename";
    private static final String UPLOAD_ID = "upload-id";
    private static final DateTime STUDY_START_TIME = DateTime.parse("2015-03-28T15:44:26.804-0700");

    private static final String CREATED_ON_STRING = "2015-04-02T03:26:59.456-07:00";
    private static final long CREATED_ON_MILLIS = DateUtils.convertToMillisFromEpoch(CREATED_ON_STRING);
    private static final String CREATED_ON_TIME_ZONE = "-0700";

    private static final DateTime MOCK_NOW = DateTime.parse("2016-06-03T11:33:55.777-0700");
    private static final long MOCK_NOW_MILLIS = MOCK_NOW.getMillis();
    private static final LocalDate MOCK_TODAY = MOCK_NOW.toLocalDate();

    private static final String RAW_ZIP_FILENAME = UPLOAD_ID + "-raw.zip";

    private static final DynamoApp APP = new DynamoApp();
    static {
        APP.setStrictUploadValidationEnabled(true);
    }

    private static final String SCHEMA_ID = "non-survey-schema";
    private static final String SCHEMA_NAME = "Non-Survey Schema";
    private static final int SCHEMA_REV = 3;

    private static final String SURVEY_CREATED_ON_STRING = "2016-06-03T16:01:02.003-0700";
    private static final long SURVEY_CREATED_ON_MILLIS = DateUtils.convertToMillisFromEpoch(SURVEY_CREATED_ON_STRING);
    private static final String SURVEY_GUID = "survey-guid";
    private static final String SURVEY_ID = "survey-id";
    private static final String SURVEY_SCHEMA_NAME = "My Survey";
    private static final int SURVEY_SCHEMA_REV = 2;

    private InMemoryFileHelper inMemoryFileHelper;
    private HealthDataService mockHealthDataService;
    private UploadDao mockUploadDao;
    private S3Helper mockS3UploadHelper;
    private ArgumentCaptor<ObjectMetadata> metadataCaptor;
    private byte[] rawFile;
    private HealthDataRecord savedRecord;
    private Upload upload;
    private Map<String, byte[]> uploadedFileContentMap;

    @BeforeClass
    public static void mockDateTime() {
        DateTimeUtils.setCurrentMillisFixed(MOCK_NOW_MILLIS);
    }

    @BeforeMethod
    public void before() throws Exception {
        // Reset all member vars, because JUnit doesn't.
        inMemoryFileHelper = new InMemoryFileHelper();
        mockHealthDataService = mock(HealthDataService.class);
        mockUploadDao = mock(UploadDao.class);
        mockS3UploadHelper = mock(S3Helper.class);
        savedRecord = null;
        uploadedFileContentMap = new HashMap<>();
        metadataCaptor = ArgumentCaptor.forClass(ObjectMetadata.class);

        ObjectNode metadataFromRequest = (ObjectNode) BridgeObjectMapper.get().readTree(
                "{\"meta-request-key\":\"meta-request-value\"}");

        upload = Upload.create();
        upload.setFilename(UPLOAD_FILENAME);
        upload.setHealthCode(HEALTH_CODE);
        upload.setMetadata(metadataFromRequest);
        upload.setUploadId(UPLOAD_ID);

        // Mock HealthDataService.createOrUpdateRecord()
        when(mockHealthDataService.createOrUpdateRecord(any(HealthDataRecord.class))).thenAnswer(
                invocation -> {
                    // save record
                    savedRecord = invocation.getArgument(0);
                    return savedRecord.getId();
                });

        when(mockHealthDataService.getRecordById(any())).thenAnswer(invocation -> {
            String recordId = invocation.getArgument(0);
            if (savedRecord == null || !savedRecord.getId().equals(recordId)) {
                return null;
            } else {
                return savedRecord;
            }
        });

        // Mock S3 upload helper. We need to save the file contents, since we delete all files at the end of execution.
        doAnswer(invocation -> {
            String s3Key = invocation.getArgument(1);
            File uploadedFile = invocation.getArgument(2);
            byte[] uploadedFileContent = inMemoryFileHelper.getBytes(uploadedFile);
            uploadedFileContentMap.put(s3Key, uploadedFileContent);

            // Required return
            return null;
        }).when(mockS3UploadHelper).writeFileToS3(any(), any(), any(), any());

        doAnswer(invocation -> {
            String s3Key = invocation.getArgument(1);
            byte[] uploadedFileContent = invocation.getArgument(2);
            uploadedFileContentMap.put(s3Key, uploadedFileContent);

            // Required return
            return null;
        }).when(mockS3UploadHelper).writeBytesToS3(any(), any(), any(), any());
    }

    @AfterClass
    public static void resetDateTime() {
        DateTimeUtils.setCurrentMillisSystem();
    }

    private void test(UploadSchema schema, Survey survey, Map<String, String> fileMap, String nonZippedFileContent)
            throws Exception {
        // Set up zip service.
        UploadArchiveService unzipService = new UploadArchiveService();
        if (upload.isZipped()) {
            // fileMap is in strings. Convert to bytes so we can use the Zipper.
            Map<String, byte[]> fileBytesMap = new HashMap<>();
            for (Map.Entry<String, String> oneFile : fileMap.entrySet()) {
                String filename = oneFile.getKey();
                String fileString = oneFile.getValue();
                fileBytesMap.put(filename, fileString.getBytes(Charsets.UTF_8));
            }

            // Add metadata.json to the fileBytesMap.
            fileBytesMap.put("metadata.json", METADATA_JSON_CONTENT);

            // For zipping, we use the real service.
            unzipService.setMaxNumZipEntries(1000000);
            unzipService.setMaxZipEntrySize(1000000);
            rawFile = unzipService.zip(fileBytesMap);
        } else {
            // If it's not zipped, use the nonZippedFileContent as the raw file.
            rawFile = nonZippedFileContent.getBytes(Charsets.UTF_8);
        }

        // Set up UploadFileHelper
        DigestUtils mockMd5DigestUtils = mock(DigestUtils.class);
        when(mockMd5DigestUtils.digest(any(File.class))).thenReturn(TestConstants.MOCK_MD5);
        when(mockMd5DigestUtils.digest(any(byte[].class))).thenReturn(TestConstants.MOCK_MD5);

        UploadFileHelper uploadFileHelper = new UploadFileHelper();
        uploadFileHelper.setFileHelper(inMemoryFileHelper);
        uploadFileHelper.setMd5DigestUtils(mockMd5DigestUtils);
        uploadFileHelper.setS3Helper(mockS3UploadHelper);

        // set up S3DownloadHandler - mock S3 Helper
        // "S3" returns file unencrypted for simplicity of testing
        S3Helper mockS3DownloadHelper = mock(S3Helper.class);
        doAnswer(invocation -> {
            File destFile = invocation.getArgument(2);
            inMemoryFileHelper.writeBytes(destFile, rawFile);

            // Required return
            return null;
        }).when(mockS3DownloadHelper).downloadS3File(eq(TestConstants.UPLOAD_BUCKET), eq(UPLOAD_ID), any());

        S3DownloadHandler s3DownloadHandler = new S3DownloadHandler();
        s3DownloadHandler.setFileHelper(inMemoryFileHelper);
        s3DownloadHandler.setS3Helper(mockS3DownloadHelper);

        // set up DecryptHandler - For ease of tests, this will just return the input verbatim.
        UploadArchiveService mockUploadArchiveService = mock(UploadArchiveService.class);
        doAnswer(invocation -> {
            File encryptedFile = invocation.getArgument(1);
            byte[] content = inMemoryFileHelper.getBytes(encryptedFile);

            File decryptedFile = invocation.getArgument(2);
            inMemoryFileHelper.writeBytes(decryptedFile, content);
            return null;
        }).when(mockUploadArchiveService).decrypt(eq(TEST_APP_ID), any(File.class), any(File.class));

        DecryptHandler decryptHandler = new DecryptHandler();
        decryptHandler.setFileHelper(inMemoryFileHelper);
        decryptHandler.setUploadArchiveService(mockUploadArchiveService);

        // Set up UnzipHandler
        UnzipHandler unzipHandler = new UnzipHandler();
        unzipHandler.setFileHelper(inMemoryFileHelper);
        unzipHandler.setUploadArchiveService(unzipService);

        // Set up InitRecordHandler
        InitRecordHandler initRecordHandler = new InitRecordHandler();
        initRecordHandler.setFileHelper(inMemoryFileHelper);

        // mock schema service
        UploadSchemaService mockUploadSchemaService = mock(UploadSchemaService.class);
        if (schema != null) {
            when(mockUploadSchemaService.getUploadSchemaByIdAndRev(TEST_APP_ID, schema.getSchemaId(),
                    schema.getRevision())).thenReturn(schema);
            when(mockUploadSchemaService.getUploadSchemaByIdAndRevNoThrow(TEST_APP_ID,
                    schema.getSchemaId(), schema.getRevision())).thenReturn(schema);
        }

        // mock survey service
        SurveyService mockSurveyService = mock(SurveyService.class);
        if (survey != null) {
            when(mockSurveyService.getSurvey(TEST_APP_ID,
                    new GuidCreatedOnVersionHolderImpl(survey.getGuid(), survey.getCreatedOn()), false, true))
                            .thenReturn(survey);
        }

        // set up IosSchemaValidationHandler
        IosSchemaValidationHandler2 iosSchemaValidationHandler = new IosSchemaValidationHandler2();
        iosSchemaValidationHandler.setFileHelper(inMemoryFileHelper);
        iosSchemaValidationHandler.setUploadFileHelper(uploadFileHelper);
        iosSchemaValidationHandler.setUploadSchemaService(mockUploadSchemaService);
        iosSchemaValidationHandler.setSurveyService(mockSurveyService);

        // set up GenericUploadFormatHandler
        GenericUploadFormatHandler genericUploadFormatHandler = new GenericUploadFormatHandler();
        genericUploadFormatHandler.setFileHelper(inMemoryFileHelper);
        genericUploadFormatHandler.setUploadFileHelper(uploadFileHelper);
        genericUploadFormatHandler.setUploadSchemaService(mockUploadSchemaService);
        genericUploadFormatHandler.setSurveyService(mockSurveyService);

        // set up UploadFormatHandler
        UploadFormatHandler uploadFormatHandler = new UploadFormatHandler();
        uploadFormatHandler.setV1LegacyHandler(iosSchemaValidationHandler);
        uploadFormatHandler.setV2GenericHandler(genericUploadFormatHandler);

        // set up StrictValidationHandler
        StrictValidationHandler strictValidationHandler = new StrictValidationHandler();
        strictValidationHandler.setUploadSchemaService(mockUploadSchemaService);

        AppService mockAppService = mock(AppService.class);
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(APP);
        strictValidationHandler.setAppService(mockAppService);

        Enrollment enrollment = Enrollment.create(TEST_APP_ID, "test-study", "userId", EXTERNAL_ID);

        // set up TranscribeConsentHandler
        Account account = Account.create();
        account.setDataGroups(ImmutableSet.of("parkinson","test_user"));
        account.setSharingScope(SharingScope.SPONSORS_AND_PARTNERS);
        account.setEnrollments(ImmutableSet.of(enrollment));

        AccountService mockAccountService = mock(AccountService.class);
        when(mockAccountService.getAccount(any())).thenReturn(Optional.of(account));

        ParticipantService mockParticipantService = mock(ParticipantService.class);
        when(mockParticipantService.getStudyStartTime(any())).thenReturn(STUDY_START_TIME);

        TranscribeConsentHandler transcribeConsentHandler = new TranscribeConsentHandler();
        transcribeConsentHandler.setAccountService(mockAccountService);
        transcribeConsentHandler.setParticipantService(mockParticipantService);

        // Set up UploadRawZipHandler.
        UploadRawZipHandler uploadRawZipHandler = new UploadRawZipHandler();
        uploadRawZipHandler.setUploadFileHelper(uploadFileHelper);

        // set up UploadArtifactsHandler
        UploadArtifactsHandler uploadArtifactsHandler = new UploadArtifactsHandler();
        uploadArtifactsHandler.setHealthDataService(mockHealthDataService);

        // set up task factory
        List<UploadValidationHandler> handlerList = ImmutableList.of(s3DownloadHandler, decryptHandler, unzipHandler,
                initRecordHandler, uploadFormatHandler, strictValidationHandler, transcribeConsentHandler,
                uploadRawZipHandler, uploadArtifactsHandler);

        UploadValidationTaskFactory taskFactory = new UploadValidationTaskFactory();
        taskFactory.setFileHelper(inMemoryFileHelper);
        taskFactory.setHandlerList(handlerList);
        taskFactory.setUploadDao(mockUploadDao);
        taskFactory.setHealthDataService(mockHealthDataService);

        // create task, execute
        UploadValidationTask task = taskFactory.newTask(TEST_APP_ID, upload);
        task.run();
    }

    private static void validateCommonRecordProps(HealthDataRecord record) {
        // The following fields are not used in this test:
        //   data, schemaId, and schemaRevision fields are specific to individual tests.
        //   id - This one is created by the DAO, so it's not present in the passed in record.
        //   synapseExporterStatus - This isn't used in upload validation.
        //   version - That's internal.
        assertEquals(record.getAppVersion(), APP_VERSION);
        assertEquals(record.getCreatedOn().longValue(), CREATED_ON_MILLIS);
        assertEquals(record.getCreatedOnTimeZone(), CREATED_ON_TIME_ZONE);
        assertEquals(record.getHealthCode(), HEALTH_CODE);
        assertEquals(record.getPhoneInfo(), PHONE_INFO);
        assertEquals(record.getAppId(), TEST_APP_ID);
        assertEquals(record.getUploadDate(), MOCK_TODAY);
        assertEquals(record.getUploadId(), UPLOAD_ID);
        assertEquals(record.getUploadedOn().longValue(), MOCK_NOW_MILLIS);
        assertEquals(record.getUserSharingScope(), SharingScope.SPONSORS_AND_PARTNERS);
        assertEquals(record.getUserExternalId(), EXTERNAL_ID);
        assertEquals(record.getUserDataGroups(), DATA_GROUP_SET);

        // Metadata needs to exist for backwards compatibility, and it should have appVersion and phoneInfo. Beyond
        // that, it doesn't really matter
        JsonNode metadataNode = record.getMetadata();
        assertEquals(metadataNode.get(UploadUtil.FIELD_APP_VERSION).textValue(), APP_VERSION);
        assertEquals(metadataNode.get(UploadUtil.FIELD_PHONE_INFO).textValue(), PHONE_INFO);

        // Validate user metadata.
        JsonNode userMetadataNode = record.getUserMetadata();
        assertEquals(userMetadataNode.size(), 2);
        assertEquals(userMetadataNode.get("meta-file-key").textValue(), "meta-file-value");
        assertEquals(userMetadataNode.get("meta-request-key").textValue(), "meta-request-value");
    }

    private void testSurvey(Map<String, String> fileMap) throws Exception {
        // set up schema
        // To test backwards compatibility, survey schema should include both the old style fields and the new
        // "answers" field.
        List<UploadFieldDefinition> fieldDefList = ImmutableList.of(
                UploadUtil.ANSWERS_FIELD_DEF,
                new UploadFieldDefinition.Builder().withName("AAA").withType(UploadFieldType.SINGLE_CHOICE)
                        .build(),
                new UploadFieldDefinition.Builder().withName("BBB").withType(UploadFieldType.MULTI_CHOICE)
                        .withMultiChoiceAnswerList("fencing", "football", "running", "swimming", "3").build(),
                new UploadFieldDefinition.Builder().withName("delicious").withType(UploadFieldType.MULTI_CHOICE)
                        .withMultiChoiceAnswerList("Yes", "No").withAllowOtherChoices(true).build());

        UploadSchema schema = UploadSchema.create();
        schema.setFieldDefinitions(fieldDefList);
        schema.setName(SURVEY_SCHEMA_NAME);
        schema.setRevision(SURVEY_SCHEMA_REV);
        schema.setSchemaId(SURVEY_ID);
        schema.setSchemaType(UploadSchemaType.IOS_SURVEY);
        schema.setAppId(TEST_APP_ID);
        schema.setSurveyGuid(SURVEY_GUID);
        schema.setSurveyCreatedOn(SURVEY_CREATED_ON_MILLIS);

        // set up survey (all we need is reference to the schema)
        DynamoSurvey survey = new DynamoSurvey();
        survey.setCreatedOn(SURVEY_CREATED_ON_MILLIS);
        survey.setGuid(SURVEY_GUID);
        survey.setIdentifier(SURVEY_ID);
        survey.setSchemaRevision(SURVEY_SCHEMA_REV);

        // execute
        test(schema, survey, fileMap, null);

        // verify created record
        ArgumentCaptor<HealthDataRecord> recordCaptor = ArgumentCaptor.forClass(HealthDataRecord.class);
        verify(mockHealthDataService).createOrUpdateRecord(recordCaptor.capture());

        HealthDataRecord record = recordCaptor.getValue();
        validateCommonRecordProps(record);
        assertEquals(record.getSchemaId(), SURVEY_ID);
        assertEquals(record.getSchemaRevision().intValue(), SURVEY_SCHEMA_REV);

        JsonNode dataNode = record.getData();
        assertEquals(dataNode.size(), 4);
        assertEquals(dataNode.get("AAA").textValue(), "Yes");

        JsonNode bbbChoiceAnswersNode = dataNode.get("BBB");
        assertEquals(bbbChoiceAnswersNode.size(), 3);
        assertEquals(bbbChoiceAnswersNode.get(0).textValue(), "fencing");
        assertEquals(bbbChoiceAnswersNode.get(1).textValue(), "running");
        assertEquals(bbbChoiceAnswersNode.get(2).textValue(), "3");

        JsonNode deliciousNode = dataNode.get("delicious");
        assertEquals(deliciousNode.size(), 2);
        assertEquals(deliciousNode.get(0).textValue(), "Yes");
        assertEquals(deliciousNode.get(1).textValue(), "Maybe");

        // Answers node has all the same fields as dataNode, except without its own answers field. Note that the
        // answers field doesn't go through canonicalization, since it's treated as an attachment instead of individual
        // fields.
        String answersAttachmentId = dataNode.get(UploadUtil.FIELD_ANSWERS).textValue();
        byte[] answersUploadedContent = uploadedFileContentMap.get(answersAttachmentId);
        JsonNode answersNode = BridgeObjectMapper.get().readTree(answersUploadedContent);
        assertEquals(answersNode.size(), 3);

        JsonNode aaaChoiceAnswersNode = answersNode.get("AAA");
        assertEquals(aaaChoiceAnswersNode.size(), 1);
        assertEquals(aaaChoiceAnswersNode.get(0).textValue(), "Yes");

        bbbChoiceAnswersNode = answersNode.get("BBB");
        assertEquals(bbbChoiceAnswersNode.size(), 3);
        assertEquals(bbbChoiceAnswersNode.get(0).textValue(), "fencing");
        assertEquals(bbbChoiceAnswersNode.get(1).textValue(), "running");
        assertEquals(bbbChoiceAnswersNode.get(2).intValue(), 3);

        deliciousNode = answersNode.get("delicious");
        assertEquals(deliciousNode.size(), 2);
        assertEquals(deliciousNode.get(0).textValue(), "Yes");
        assertEquals(deliciousNode.get(1).textValue(), "Maybe");

        // We upload the unencrypted zipped file back to S3.
        validateRawDataAttachment(RAW_ZIP_FILENAME);

        // verify upload dao write validation status
        verify(mockUploadDao).writeValidationStatus(upload, UploadStatus.SUCCEEDED, ImmutableList.of(), UPLOAD_ID);
    }

    @Test
    public void v1LegacySurvey() throws Exception {
        // set up upload files
        String infoJsonText = "{\n" +
                "   \"files\":[{\n" +
                "       \"filename\":\"AAA.json\",\n" +
                "       \"timestamp\":\"" + CREATED_ON_STRING + "\"\n" +
                "   },{\n" +
                "       \"filename\":\"BBB.json\",\n" +
                "       \"timestamp\":\"" + CREATED_ON_STRING + "\"\n" +
                "   },{\n" +
                "       \"filename\":\"delicious.json\",\n" +
                "       \"timestamp\":\"" + CREATED_ON_STRING + "\"\n" +
                "   }],\n" +
                "   \"surveyGuid\":\"" + SURVEY_GUID + "\",\n" +
                "   \"surveyCreatedOn\":\"" + SURVEY_CREATED_ON_STRING + "\",\n" +
                "   \"appVersion\":\"" + APP_VERSION + "\",\n" +
                "   \"phoneInfo\":\"" + PHONE_INFO + "\"\n" +
                "}";

        String aaaJsonText = """
                {
                   "questionType":0,
                   "choiceAnswers":["Yes"],
                   "startDate":"2015-04-02T03:26:57-07:00",
                   "questionTypeName":"SingleChoice",
                   "item":"AAA",
                   "endDate":"2015-04-02T03:26:59-07:00"
                }\
                """;

        String bbbJsonText = """
                {
                   "questionType":0,
                   "choiceAnswers":["fencing", "running", 3],
                   "startDate":"2015-04-02T03:27:05-07:00",
                   "questionTypeName":"MultipleChoice",
                   "item":"BBB",
                   "endDate":"2015-04-02T03:27:09-07:00"
                }\
                """;

        String deliciousJsonText = """
                {
                   "questionType":0,
                   "choiceAnswers":["Yes", "Maybe"],
                   "startDate":"2015-04-02T03:27:05-07:00",
                   "questionTypeName":"MultipleChoice",
                   "item":"delicious",
                   "endDate":"2015-04-02T03:27:09-07:00"
                }\
                """;

        Map<String, String> fileMap = ImmutableMap.<String, String>builder().put("info.json", infoJsonText)
                .put("AAA.json", aaaJsonText).put("BBB.json", bbbJsonText).put("delicious.json", deliciousJsonText)
                .build();

        // execute
        testSurvey(fileMap);
    }

    @Test
    public void v2GenericSurvey() throws Exception {
        // set up upload files
        String infoJsonText = "{\n" +
                "   \"createdOn\":\"" + CREATED_ON_STRING + "\",\n" +
                "   \"dataFilename\":\"answers.json\",\n" +
                "   \"format\":\"v2_generic\",\n" +
                "   \"surveyGuid\":\"" + SURVEY_GUID + "\",\n" +
                "   \"surveyCreatedOn\":\"" + SURVEY_CREATED_ON_STRING + "\",\n" +
                "   \"appVersion\":\"" + APP_VERSION + "\",\n" +
                "   \"phoneInfo\":\"" + PHONE_INFO + "\"\n" +
                "}";

        String answersJsonText = """
                {
                   "AAA":["Yes"],
                   "BBB":["fencing", "running", 3],
                   "delicious":["Yes", "Maybe"]
                }\
                """;

        Map<String, String> fileMap = ImmutableMap.<String, String>builder().put("info.json", infoJsonText)
                .put("answers.json", answersJsonText).build();

        // execute
        testSurvey(fileMap);
    }

    private void testNonSurvey(String infoJsonText) throws Exception {
        // set up schema
        List<UploadFieldDefinition> fieldDefList = ImmutableList.of(
                new UploadFieldDefinition.Builder().withName("CCC.txt").withType(UploadFieldType.ATTACHMENT_BLOB)
                        .build(),
                new UploadFieldDefinition.Builder().withName("DDD.csv").withType(UploadFieldType.ATTACHMENT_CSV)
                        .build(),
                new UploadFieldDefinition.Builder().withName("EEE.json")
                        .withType(UploadFieldType.ATTACHMENT_JSON_BLOB).build(),
                new UploadFieldDefinition.Builder().withName("FFF.json")
                        .withType(UploadFieldType.ATTACHMENT_JSON_TABLE).build(),
                new UploadFieldDefinition.Builder().withName("GGG.txt").withType(UploadFieldType.ATTACHMENT_V2)
                        .build(),
                new UploadFieldDefinition.Builder().withName("record.json.HHH")
                        .withType(UploadFieldType.ATTACHMENT_V2).build(),
                new UploadFieldDefinition.Builder().withName("record.json.III").withType(UploadFieldType.BOOLEAN)
                        .build(),
                new UploadFieldDefinition.Builder().withName("record.json.JJJ")
                        .withType(UploadFieldType.CALENDAR_DATE).build(),
                new UploadFieldDefinition.Builder().withName("record.json.LLL")
                        .withType(UploadFieldType.DURATION_V2).build(),
                new UploadFieldDefinition.Builder().withName("record.json.MMM").withType(UploadFieldType.FLOAT)
                        .build(),
                new UploadFieldDefinition.Builder().withName("record.json.NNN")
                        .withType(UploadFieldType.INLINE_JSON_BLOB).build(),
                new UploadFieldDefinition.Builder().withName("record.json.OOO").withType(UploadFieldType.INT)
                        .build(),
                new UploadFieldDefinition.Builder().withName("record.json.PPP").withType(UploadFieldType.STRING)
                        .build(),
                new UploadFieldDefinition.Builder().withName("record.json.QQQ").withType(UploadFieldType.TIME_V2)
                        .build(),
                new UploadFieldDefinition.Builder().withName("record.json.arrr")
                        .withType(UploadFieldType.TIMESTAMP).build(),
                new UploadFieldDefinition.Builder().withName("empty_attachment")
                        .withType(UploadFieldType.ATTACHMENT_V2).withRequired(false).build());

        UploadSchema schema = UploadSchema.create();
        schema.setFieldDefinitions(fieldDefList);
        schema.setName(SCHEMA_NAME);
        schema.setRevision(SCHEMA_REV);
        schema.setSchemaId(SCHEMA_ID);
        schema.setSchemaType(UploadSchemaType.IOS_DATA);
        schema.setAppId(TEST_APP_ID);

        // set up upload files
        String cccTxtContent = "Blob file";

        String dddCsvContent = "foo,bar\nbaz,qux";

        String eeeJsonContent = "{\"key\":\"value\"}";

        String fffJsonContent = "[{\"name\":\"Dwayne\"},{\"name\":\"Eggplant\"}]";

        String gggTxtContent = "Arbitrary attachment";

        // Note that a lot of these have the wrong type, but are convertible to the correct type. This is to test that
        // values can be canonicalized.
        String recordJsonContent = """
                {
                   "HHH":["attachment", "inside", "file"],
                   "III":1,
                   "JJJ":"2016-06-03T17:03-0700",
                   "LLL":"PT1H",
                   "MMM":"3.14",
                   "NNN":["inline", "json"],
                   "OOO":"2.718",
                   "PPP":1337,
                   "QQQ":"2016-06-03T19:21:35.378-0700",
                   "arrr":"2016-06-03T18:12:34.567+0900"
                }\
                """;

        Map<String, String> fileMap = ImmutableMap.<String, String>builder().put("info.json", infoJsonText)
                .put("CCC.txt", cccTxtContent).put("DDD.csv", dddCsvContent).put("EEE.json", eeeJsonContent)
                .put("FFF.json", fffJsonContent).put("GGG.txt", gggTxtContent).put("record.json", recordJsonContent)
                .put("empty_attachment", "").build();

        // execute
        test(schema, null, fileMap, null);

        // verify created record
        ArgumentCaptor<HealthDataRecord> recordCaptor = ArgumentCaptor.forClass(HealthDataRecord.class);
        verify(mockHealthDataService).createOrUpdateRecord(recordCaptor.capture());

        HealthDataRecord record = recordCaptor.getValue();
        validateCommonRecordProps(record);
        assertEquals(record.getSchemaId(), SCHEMA_ID);
        assertEquals(record.getSchemaRevision().intValue(), SCHEMA_REV);

        JsonNode dataNode = record.getData();
        assertEquals(dataNode.size(), 15);
        assertTrue(dataNode.get("record.json.III").booleanValue());
        assertEquals(dataNode.get("record.json.JJJ").textValue(), "2016-06-03");
        assertEquals(dataNode.get("record.json.LLL").textValue(), "PT1H");
        assertEquals(dataNode.get("record.json.MMM").doubleValue(), 3.14, /*delta*/ 0.001);
        assertEquals(dataNode.get("record.json.OOO").intValue(), 2);
        assertEquals(dataNode.get("record.json.PPP").textValue(), "1337");
        assertEquals(dataNode.get("record.json.QQQ").textValue(),"19:21:35.378");
        assertEquals(DateTime.parse(dataNode.get("record.json.arrr").textValue()),
                DateTime.parse("2016-06-03T18:12:34.567+0900"));

        JsonNode nnnNode = dataNode.get("record.json.NNN");
        assertEquals(nnnNode.size(), 2);
        assertEquals(nnnNode.get(0).textValue(), "inline");
        assertEquals(nnnNode.get(1).textValue(), "json");

        // validate attachment content in S3
        String cccTxtAttachmentId = dataNode.get("CCC.txt").textValue();
        validateTextAttachment(cccTxtContent, cccTxtAttachmentId);

        String dddCsvAttachmentId = dataNode.get("DDD.csv").textValue();
        validateTextAttachment(dddCsvContent, dddCsvAttachmentId);

        String gggTxtAttachmentId = dataNode.get("GGG.txt").textValue();
        validateTextAttachment(gggTxtContent, gggTxtAttachmentId);

        String eeeJsonAttachmentId = dataNode.get("EEE.json").textValue();
        byte[] eeeJsonUploadedContent = uploadedFileContentMap.get(eeeJsonAttachmentId);
        JsonNode eeeJsonNode = BridgeObjectMapper.get().readTree(eeeJsonUploadedContent);
        assertEquals(eeeJsonNode.size(), 1);
        assertEquals(eeeJsonNode.get("key").textValue(), "value");

        String fffJsonAttachmentId = dataNode.get("FFF.json").textValue();
        byte[] fffJsonUploadedContent = uploadedFileContentMap.get(fffJsonAttachmentId);
        JsonNode fffJsonNode = BridgeObjectMapper.get().readTree(fffJsonUploadedContent);
        assertEquals(fffJsonNode.size(), 2);

        assertEquals(fffJsonNode.get(0).size(), 1);
        assertEquals(fffJsonNode.get(0).get("name").textValue(), "Dwayne");

        assertEquals(fffJsonNode.get(1).size(), 1);
        assertEquals(fffJsonNode.get(1).get("name").textValue(), "Eggplant");

        String hhhAttachmentId = dataNode.get("record.json.HHH").textValue();
        
        ArgumentCaptor<byte[]> attachmentContentCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(mockS3UploadHelper).writeBytesToS3(eq(TestConstants.ATTACHMENT_BUCKET), eq(hhhAttachmentId),
                attachmentContentCaptor.capture(), metadataCaptor.capture());
        JsonNode hhhNode = BridgeObjectMapper.get().readTree(attachmentContentCaptor.getValue());        
        assertEquals(hhhNode.size(), 3);
        assertEquals(hhhNode.get(0).textValue(), "attachment");
        assertEquals(hhhNode.get(1).textValue(), "inside");
        assertEquals(hhhNode.get(2).textValue(), "file");
        
        assertEquals(metadataCaptor.getValue().getSSEAlgorithm(), ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);

        // We upload the unencrypted zipped file back to S3.
        validateRawDataAttachment(RAW_ZIP_FILENAME);

        // verify upload dao write validation status
        verify(mockUploadDao).writeValidationStatus(upload, UploadStatus.SUCCEEDED, ImmutableList.of(), UPLOAD_ID);
    }

    @Test
    public void v1LegacyNonSurvey() throws Exception {
        // make info.json
        String infoJsonText = "{\n" +
                "   \"files\":[{\n" +
                "       \"filename\":\"CCC.txt\",\n" +
                "       \"timestamp\":\"" + CREATED_ON_STRING + "\"\n" +
                "   },{\n" +
                "       \"filename\":\"DDD.csv\",\n" +
                "       \"timestamp\":\"" + CREATED_ON_STRING + "\"\n" +
                "   },{\n" +
                "       \"filename\":\"EEE.json\",\n" +
                "       \"timestamp\":\"" + CREATED_ON_STRING + "\"\n" +
                "   },{\n" +
                "       \"filename\":\"FFF.json\",\n" +
                "       \"timestamp\":\"" + CREATED_ON_STRING + "\"\n" +
                "   },{\n" +
                "       \"filename\":\"GGG.txt\",\n" +
                "       \"timestamp\":\"" + CREATED_ON_STRING + "\"\n" +
                "   },{\n" +
                "       \"filename\":\"record.json\",\n" +
                "       \"timestamp\":\"" + CREATED_ON_STRING + "\"\n" +
                "   },{\n" +
                "       \"filename\":\"empty_attachment\",\n" +
                "       \"timestamp\":\"" + CREATED_ON_STRING + "\"\n" +
                "   }],\n" +
                "   \"item\":\"" + SCHEMA_ID + "\",\n" +
                "   \"schemaRevision\":" + SCHEMA_REV + ",\n" +
                "   \"appVersion\":\"" + APP_VERSION + "\",\n" +
                "   \"phoneInfo\":\"" + PHONE_INFO + "\"\n" +
                "}";

        // execute
        testNonSurvey(infoJsonText);
    }

    @Test
    public void v2GenericNonSurvey() throws Exception {
        // make info.json
        String infoJsonText = "{\n" +
                "   \"createdOn\":\"" + CREATED_ON_STRING + "\",\n" +
                "   \"format\":\"v2_generic\",\n" +
                "   \"item\":\"" + SCHEMA_ID + "\",\n" +
                "   \"schemaRevision\":" + SCHEMA_REV + ",\n" +
                "   \"appVersion\":\"" + APP_VERSION + "\",\n" +
                "   \"phoneInfo\":\"" + PHONE_INFO + "\"\n" +
                "}";

        // execute
        testNonSurvey(infoJsonText);
    }

    @Test
    public void schemaless() throws Exception {
        // Make info.json.
        String infoJsonText = "{\n" +
                "   \"createdOn\":\"" + CREATED_ON_STRING + "\",\n" +
                "   \"format\":\"v2_generic\",\n" +
                "   \"appVersion\":\"" + APP_VERSION + "\",\n" +
                "   \"phoneInfo\":\"" + PHONE_INFO + "\"\n" +
                "}";

        // Create a dummy file, which is ignored because we don't have a schema.
        Map<String, String> fileMap = ImmutableMap.<String, String>builder().put("info.json", infoJsonText)
                .put("dummy", "dummy content").build();

        // Execute.
        test(null, null, fileMap, null);

        // Verify created record.
        ArgumentCaptor<HealthDataRecord> recordCaptor = ArgumentCaptor.forClass(HealthDataRecord.class);
        verify(mockHealthDataService).createOrUpdateRecord(recordCaptor.capture());

        HealthDataRecord record = recordCaptor.getValue();
        validateCommonRecordProps(record);
        assertNull(record.getSchemaId());
        assertNull(record.getSchemaRevision());

        // Data map is empty. No schema means no data parsed.
        JsonNode dataNode = record.getData();
        assertEquals(dataNode.size(), 0);

        // Only 1 attachment, and that's raw data.
        assertEquals(uploadedFileContentMap.size(), 1);
        validateRawDataAttachment(RAW_ZIP_FILENAME);

        // verify upload dao write validation status
        verify(mockUploadDao).writeValidationStatus(upload, UploadStatus.SUCCEEDED, ImmutableList.of(), UPLOAD_ID);
    }

    @Test
    public void nonZipped() throws Exception {
        // Execute.
        upload.setZipped(false);
        String rawFileContent = "dummy content";
        test(null, null, null, rawFileContent);

        // Verify created record.
        ArgumentCaptor<HealthDataRecord> recordCaptor = ArgumentCaptor.forClass(HealthDataRecord.class);
        verify(mockHealthDataService).createOrUpdateRecord(recordCaptor.capture());

        // These attributes are still parsed in.
        HealthDataRecord record = recordCaptor.getValue();
        assertEquals(record.getCreatedOn().longValue(), MOCK_NOW_MILLIS);
        assertEquals(record.getHealthCode(), HEALTH_CODE);
        assertEquals(record.getAppId(), TEST_APP_ID);
        assertEquals(record.getUploadDate(), MOCK_TODAY);
        assertEquals(record.getUploadId(), UPLOAD_ID);
        assertEquals(record.getUploadedOn().longValue(), MOCK_NOW_MILLIS);
        assertEquals(record.getUserSharingScope(), SharingScope.SPONSORS_AND_PARTNERS);
        assertEquals(record.getUserExternalId(), EXTERNAL_ID);
        assertEquals(record.getUserDataGroups(), DATA_GROUP_SET);

        // These attributes are not.
        assertNull(record.getAppVersion());
        assertNull(record.getCreatedOnTimeZone());
        assertNull(record.getPhoneInfo());
        assertNull(record.getSchemaId());
        assertNull(record.getSchemaRevision());

        // User metadata from request only.
        JsonNode userMetadata = record.getUserMetadata();
        assertEquals(userMetadata.size(), 1);
        assertEquals(userMetadata.get("meta-request-key").textValue(), "meta-request-value");

        // Data and metadata both exist and are empty.
        assertTrue(record.getData().isObject());
        assertEquals(record.getData().size(), 0);

        assertTrue(record.getMetadata().isObject());
        assertEquals(record.getMetadata().size(), 0);

        // Only 1 attachment, and that's raw data.
        assertEquals(uploadedFileContentMap.size(), 1);
        validateRawDataAttachment(UPLOAD_ID + "-" + UPLOAD_FILENAME);

        // verify upload dao write validation status
        verify(mockUploadDao).writeValidationStatus(upload, UploadStatus.SUCCEEDED, ImmutableList.of(), UPLOAD_ID);
    }

    private void validateTextAttachment(String expected, String attachmentId) {
        byte[] uploadedFileContent = uploadedFileContentMap.get(attachmentId);
        assertEquals(new String(uploadedFileContent, Charsets.UTF_8), expected);
    }

    private void validateRawDataAttachment(String expectedFilename) {
        verify(mockS3UploadHelper).writeFileToS3(eq(TestConstants.ATTACHMENT_BUCKET), eq(expectedFilename),
                any(), metadataCaptor.capture());
        byte[] rawDataBytes = uploadedFileContentMap.get(expectedFilename);
        assertEquals(rawDataBytes, rawFile);
        assertEquals(metadataCaptor.getValue().getSSEAlgorithm(), ObjectMetadata.AES_256_SERVER_SIDE_ENCRYPTION);
    }
}
