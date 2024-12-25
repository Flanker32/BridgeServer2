package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.ORG_ADMIN;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.TestConstants.USER_STUDY_IDS;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.sagebionetworks.bridge.models.reports.ReportType.PARTICIPANT;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.fail;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoApp;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.exceptions.UnauthorizedException;
import org.sagebionetworks.bridge.models.DateRangeResourceList;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ReportTypeResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.ConsentStatus;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.reports.ReportData;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;
import org.sagebionetworks.bridge.models.reports.ReportIndex;
import org.sagebionetworks.bridge.models.reports.ReportType;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.ReportService;
import org.sagebionetworks.bridge.services.AppService;

public class ParticipantReportControllerTest extends Mockito {
    
    public static final String NEXT_PAGE_OFFSET_KEY = "nextPageOffsetKey";
    private static final String REPORT_ID = "foo";
    private static final String OTHER_PARTICIPANT_HEALTH_CODE = "ABC";
    private static final String OTHER_PARTICIPANT_ID = "userId";
    private static final AccountId OTHER_ACCOUNT_ID = AccountId.forId(TEST_APP_ID, OTHER_PARTICIPANT_ID);
    private static final String HEALTH_CODE = "healthCode";
    private static final LocalDate START_DATE = LocalDate.parse("2015-01-02");
    private static final LocalDate END_DATE = LocalDate.parse("2015-02-02");
    private static final DateTime START_TIME = DateTime.parse("2015-01-02T08:32:50.000-07:00");
    private static final DateTime END_TIME = DateTime.parse("2015-02-02T15:00:32.123-07:00");
    private static final String OFFSET_KEY = "offsetKey";
    private static final String PAGE_SIZE = "20";
    private static final int PAGE_SIZE_INT = 20;

    @Mock
    ReportService mockReportService;
    
    @Mock
    AppService mockAppService;
    
    @Mock
    AccountService mockAccountService;
    
    @Mock
    Account mockAccount;
    
    @Mock
    Account mockOtherAccount;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Captor
    ArgumentCaptor<ReportData> reportDataCaptor;
    
    @Captor
    ArgumentCaptor<ReportIndex> reportDataIndex;
    
    @Captor
    ArgumentCaptor<ReportDataKey> reportDataKeyCaptor;
    
    @InjectMocks
    @Spy
    ParticipantReportController controller;
    
    UserSession session;
    
    @BeforeMethod
    public void before() throws Exception {
        MockitoAnnotations.initMocks(this);
        
        DynamoApp app = new DynamoApp();
        app.setIdentifier(TEST_APP_ID);
        
        StudyParticipant participant = new StudyParticipant.Builder().withHealthCode(HEALTH_CODE)
                .withId(TEST_USER_ID).withRoles(Sets.newHashSet(DEVELOPER)).build();

        ConsentStatus status = new ConsentStatus.Builder().withName("Name").withGuid(SubpopulationGuid.create("GUID"))
                .withConsented(true).withRequired(true).withSignedMostRecentConsent(true).build();
        Map<SubpopulationGuid,ConsentStatus> statuses = Maps.newHashMap();
        statuses.put(SubpopulationGuid.create(status.getSubpopulationGuid()), status);
        
        session = new UserSession(participant);
        session.setAppId(TEST_APP_ID);
        session.setAuthenticated(true);
        session.setConsentStatuses(statuses);
        
        doReturn(app).when(mockAppService).getApp(TEST_APP_ID);
        doReturn(OTHER_PARTICIPANT_ID).when(mockOtherAccount).getId();
        doReturn(OTHER_PARTICIPANT_HEALTH_CODE).when(mockOtherAccount).getHealthCode();
        doReturn(Optional.of(mockOtherAccount)).when(mockAccountService).getAccount(OTHER_ACCOUNT_ID);
        doReturn(HEALTH_CODE).when(mockAccount).getHealthCode();
        doReturn(TEST_USER_ID).when(mockAccount).getId();
        doReturn(session).when(controller).getSessionIfItExists();
        doReturn(session).when(controller).getAuthenticatedSession();
        doReturn(session).when(controller).getAuthenticatedSession(WORKER);
        
        ReportIndex index = ReportIndex.create();
        index.setIdentifier("fofo");
        ReportTypeResourceList<? extends ReportIndex> list = new ReportTypeResourceList<>(
                Lists.newArrayList(index)).withRequestParam(ResourceList.REPORT_TYPE, ReportType.STUDY);
        
        index = ReportIndex.create();
        index.setIdentifier("fofo");
        list = new ReportTypeResourceList<>(Lists.newArrayList(index))
                .withRequestParam(ResourceList.REPORT_TYPE, ReportType.PARTICIPANT);
        doReturn(list).when(mockReportService).getReportIndices(TEST_APP_ID, ReportType.PARTICIPANT);
        
        doReturn(mockRequest).when(controller).request();
    }
    
    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(ParticipantReportController.class);
        assertGet(ParticipantReportController.class, "getParticipantReportForSelf");
        assertGet(ParticipantReportController.class, "getParticipantReportForSelfV4");
        assertCreate(ParticipantReportController.class, "saveParticipantReportForSelf");
        assertGet(ParticipantReportController.class, "listParticipantReportIndices");
        assertGet(ParticipantReportController.class, "getParticipantReportIndex");
        assertGet(ParticipantReportController.class, "getParticipantReport");
        assertGet(ParticipantReportController.class, "getParticipantReportForWorker");
        assertGet(ParticipantReportController.class, "getParticipantReportV4");
        assertGet(ParticipantReportController.class, "getParticipantReportForWorkerV4");
        assertCreate(ParticipantReportController.class, "saveParticipantReport");
        assertCreate(ParticipantReportController.class, "saveParticipantReportForWorker");
        assertDelete(ParticipantReportController.class, "deleteParticipantReport");
        assertDelete(ParticipantReportController.class, "deleteParticipantReportRecord");
        assertDelete(ParticipantReportController.class, "deleteParticipantReportIndex");
    }
    
    @Test
    public void getParticipantReportDataForSelf() throws Exception {
        doReturn(makeResults(START_DATE, END_DATE)).when(mockReportService).getParticipantReport(session.getAppId(),
                session.getId(), REPORT_ID, session.getHealthCode(), START_DATE, END_DATE);
        
        DateRangeResourceList<? extends ReportData> result = controller.getParticipantReportForSelf(REPORT_ID, START_DATE.toString(), END_DATE.toString());
        assertResultContent(START_DATE, END_DATE, result);
    }
    
    @Test
    public void getParticipantReportDataForSelfV4() throws Exception {
        doReturn(makePagedResults(START_TIME, END_TIME, OFFSET_KEY, PAGE_SIZE_INT)).when(mockReportService)
                .getParticipantReportV4(session.getAppId(), session.getId(), REPORT_ID, session.getHealthCode(), START_TIME, END_TIME,
                        OFFSET_KEY, Integer.parseInt(PAGE_SIZE));
        
        ForwardCursorPagedResourceList<ReportData> result = controller.getParticipantReportForSelfV4(REPORT_ID, START_TIME.toString(), END_TIME.toString(),
                OFFSET_KEY, PAGE_SIZE);
        
        assertReportDataPage(START_TIME, END_TIME, OFFSET_KEY, PAGE_SIZE_INT, result);
    }
    
    @Test
    public void saveParticipantDataForSelf() throws Exception {
        String json = TestUtils.createJson("{'date':'2015-02-12','data':{'field1':'Last','field2':'Name'}}");
        mockRequestBody(mockRequest, json);
        session.setAppId(json);
        StatusMessage result = controller.saveParticipantReportForSelf(REPORT_ID);
        assertEquals(result.getMessage(), "Report data saved.");
        
        verify(mockReportService).saveParticipantReport(eq(session.getAppId()), eq(TEST_USER_ID), eq(REPORT_ID),
                eq(HEALTH_CODE), reportDataCaptor.capture());
        
        ReportData reportData = reportDataCaptor.getValue();
        assertEquals("2015-02-12", reportData.getDate());
        assertEquals("Last", reportData.getData().get("field1").asText());
        assertEquals("Name", reportData.getData().get("field2").asText());
        assertNull(reportData.getKey());
    }
    
    @Test
    public void getParticipantReportDataNoDatesForSelf() throws Exception {
        doReturn(makeResults(START_DATE, END_DATE)).when(mockReportService).getParticipantReport(session.getAppId(),
                session.getId(), REPORT_ID, session.getHealthCode(), null, null);
        
        DateRangeResourceList<? extends ReportData> result = controller.getParticipantReportForSelf(REPORT_ID, null, null);

        assertResultContent(START_DATE, END_DATE, result);
    }

    @Test
    public void getParticipantReportDataV4() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder().withHealthCode(HEALTH_CODE)
                .withRoles(Sets.newHashSet(RESEARCHER)).build();
        session.setParticipant(participant);
        
        doReturn(Optional.of(mockAccount)).when(mockAccountService).getAccount(OTHER_ACCOUNT_ID);
        
        doReturn(makePagedResults(START_TIME, END_TIME, OFFSET_KEY, PAGE_SIZE_INT)).when(mockReportService)
                .getParticipantReportV4(session.getAppId(), TEST_USER_ID, REPORT_ID, HEALTH_CODE, START_TIME, END_TIME,
                        OFFSET_KEY, Integer.parseInt(PAGE_SIZE));
        
        ForwardCursorPagedResourceList<ReportData> result = controller.getParticipantReportV4(OTHER_PARTICIPANT_ID,
                REPORT_ID, START_TIME.toString(), END_TIME.toString(), OFFSET_KEY, PAGE_SIZE);
        
        assertReportDataPage(START_TIME, END_TIME, OFFSET_KEY, PAGE_SIZE_INT, result);
    }

    @Test
    public void getParticipantReportForWorkerV4_DefaultParams() throws Exception {
        // Mock dependencies
        when(mockAccountService.getAccount(any())).thenReturn(Optional.of(mockAccount));

        ForwardCursorPagedResourceList<ReportData> expectedPage = makePagedResults(START_TIME, END_TIME, null,
                API_DEFAULT_PAGE_SIZE);
        when(mockReportService.getParticipantReportV4(TEST_APP_ID, TEST_USER_ID, REPORT_ID, HEALTH_CODE, null,
                null, null, API_DEFAULT_PAGE_SIZE)).thenReturn(expectedPage);

        // Execute and validate.
        ForwardCursorPagedResourceList<ReportData> result = controller.getParticipantReportForWorkerV4(
                TEST_APP_ID, OTHER_PARTICIPANT_ID, REPORT_ID, null, null, null, null);

        assertReportDataPage(START_TIME, END_TIME, null, API_DEFAULT_PAGE_SIZE, result);

        // Verify dependent service call.
        verify(mockReportService).getParticipantReportV4(TEST_APP_ID, TEST_USER_ID, REPORT_ID, HEALTH_CODE, null,
                null, null, API_DEFAULT_PAGE_SIZE);
    }

    @Test
    public void getParticipantReportForWorkerV4_OptionalParams() throws Exception {
        // Mock dependencies
        when(mockAccountService.getAccount(any())).thenReturn(Optional.of(mockAccount));

        ForwardCursorPagedResourceList<ReportData> expectedPage = makePagedResults(START_TIME, END_TIME, OFFSET_KEY,
                PAGE_SIZE_INT);
        when(mockReportService.getParticipantReportV4(TEST_APP_ID, TEST_USER_ID, REPORT_ID, HEALTH_CODE, START_TIME, END_TIME,
                OFFSET_KEY, PAGE_SIZE_INT)).thenReturn(expectedPage);

        // Execute and validate.
        ForwardCursorPagedResourceList<ReportData> result = controller.getParticipantReportForWorkerV4(
                TEST_APP_ID, OTHER_PARTICIPANT_ID, REPORT_ID, START_TIME.toString(), END_TIME.toString(),
                OFFSET_KEY, PAGE_SIZE);

        assertReportDataPage(START_TIME, END_TIME, OFFSET_KEY, PAGE_SIZE_INT, result);

        // Verify dependent service call.
        verify(mockReportService).getParticipantReportV4(TEST_APP_ID, TEST_USER_ID, REPORT_ID, HEALTH_CODE, START_TIME,
                END_TIME, OFFSET_KEY, PAGE_SIZE_INT);
    }

    @Test
    public void getParticipantReportDataAsResearcher() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());
        
        doReturn(Optional.of(mockAccount)).when(mockAccountService).getAccount(OTHER_ACCOUNT_ID);
        
        doReturn(makeResults(START_DATE, END_DATE)).when(mockReportService).getParticipantReport(session.getAppId(),
                TEST_USER_ID, REPORT_ID, HEALTH_CODE, START_DATE, END_DATE);
        
        DateRangeResourceList<? extends ReportData> result = controller.getParticipantReport(OTHER_PARTICIPANT_ID,
                REPORT_ID, START_DATE.toString(), END_DATE.toString());
        assertResultContent(START_DATE, END_DATE, result);
    }

    @Test
    public void getParticipantReportDataAsDeveloperForSelf() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(DEVELOPER))
                .withCallerUserId(OTHER_PARTICIPANT_ID).build());
        
        StudyParticipant participant = new StudyParticipant.Builder().withHealthCode(HEALTH_CODE)
                .withId(OTHER_PARTICIPANT_ID).withRoles(Sets.newHashSet(DEVELOPER)).build();
        session.setParticipant(participant);

        when(mockAccount.getId()).thenReturn(OTHER_PARTICIPANT_ID);
        doReturn(Optional.of(mockAccount)).when(mockAccountService).getAccount(OTHER_ACCOUNT_ID);
        
        doReturn(makeResults(START_DATE, END_DATE)).when(mockReportService).getParticipantReport(session.getAppId(),
                TEST_USER_ID, REPORT_ID, HEALTH_CODE, START_DATE, END_DATE);
        
        DateRangeResourceList<? extends ReportData> result = controller.getParticipantReport(OTHER_PARTICIPANT_ID,
                REPORT_ID, START_DATE.toString(), END_DATE.toString());
        assertResultContent(START_DATE, END_DATE, result);
    }
    
    @Test
    public void getParticipantReportDataV4AsDeveloperForSelf() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder().withHealthCode(HEALTH_CODE)
                .withId(OTHER_PARTICIPANT_ID).withRoles(Sets.newHashSet(DEVELOPER)).build();
        session.setParticipant(participant);
        
        doReturn(Optional.of(mockAccount)).when(mockAccountService).getAccount(OTHER_ACCOUNT_ID);
        
        doReturn(makePagedResults(START_TIME, END_TIME, OFFSET_KEY, PAGE_SIZE_INT)).when(mockReportService)
                .getParticipantReportV4(session.getAppId(), TEST_USER_ID, REPORT_ID, HEALTH_CODE, START_TIME, END_TIME,
                        OFFSET_KEY, Integer.parseInt(PAGE_SIZE));
        
        ForwardCursorPagedResourceList<ReportData> result = controller.getParticipantReportV4(OTHER_PARTICIPANT_ID,
                REPORT_ID, START_TIME.toString(), END_TIME.toString(), OFFSET_KEY, PAGE_SIZE);
        
        assertReportDataPage(START_TIME, END_TIME, OFFSET_KEY, PAGE_SIZE_INT, result);
        
    }
    
    @Test
    public void getParticipantReportForWorker_DefaultParams() throws Exception {
        // Mock dependencies
        when(mockAccountService.getAccount(any())).thenReturn(Optional.of(mockAccount));

        DateRangeResourceList<ReportData> expectedPage = makeResults(START_DATE, END_DATE);
        doReturn(expectedPage).when(mockReportService).getParticipantReport(TEST_APP_ID, TEST_USER_ID, REPORT_ID,
                HEALTH_CODE, null, null);

        // Execute and validate.
        DateRangeResourceList<? extends ReportData> result = controller
                .getParticipantReportForWorker(TEST_APP_ID, OTHER_PARTICIPANT_ID, REPORT_ID, null, null);
        assertResultContent(START_DATE, END_DATE, result);

        // Verify dependent service call.
        verify(mockReportService).getParticipantReport(TEST_APP_ID, TEST_USER_ID, REPORT_ID, HEALTH_CODE, null, null);
    }

    @Test
    public void getParticipantReportForWorker_OptionalParams() throws Exception {
        // Mock dependencies
        when(mockAccountService.getAccount(any())).thenReturn(Optional.of(mockAccount));

        DateRangeResourceList<ReportData> expectedPage = makeResults(START_DATE, END_DATE);
        doReturn(expectedPage).when(mockReportService).getParticipantReport(TEST_APP_ID, TEST_USER_ID, REPORT_ID,
                HEALTH_CODE, START_DATE, END_DATE);

        // Execute and validate.
        DateRangeResourceList<? extends ReportData> result = controller.getParticipantReportForWorker(
                TEST_APP_ID, OTHER_PARTICIPANT_ID, REPORT_ID, START_DATE.toString(), END_DATE.toString());
        assertResultContent(START_DATE, END_DATE, result);

        // Verify dependent service call.
        verify(mockReportService).getParticipantReport(TEST_APP_ID, TEST_USER_ID, REPORT_ID, HEALTH_CODE, START_DATE, END_DATE);
    }

    @Test
    public void saveParticipantReportData() throws Exception {
        String json = TestUtils.createJson("{'date':'2015-02-12','data':{'field1':'Last','field2':'Name'}}");
        mockRequestBody(mockRequest, json);
        when(mockAccountService.getAccount(any())).thenReturn(Optional.of(mockOtherAccount));

        StatusMessage result = controller.saveParticipantReport(OTHER_PARTICIPANT_ID, REPORT_ID);
        assertEquals(result.getMessage(), "Report data saved.");

        verify(mockReportService).saveParticipantReport(eq(TEST_APP_ID), eq(TEST_USER_ID), eq(REPORT_ID),
                eq(OTHER_PARTICIPANT_HEALTH_CODE), reportDataCaptor.capture());
        ReportData reportData = reportDataCaptor.getValue();
        assertEquals(reportData.getDate().toString(), LocalDate.parse("2015-02-12").toString());
        assertNull(reportData.getKey());
        assertEquals(reportData.getData().get("field1").asText(), "Last");
        assertEquals(reportData.getData().get("field2").asText(), "Name");
    }
    
    @Test(expectedExceptions = InvalidEntityException.class, 
            expectedExceptionsMessageRegExp = ".*Error parsing JSON in request body, fields:.*")
    public void saveParticipantReportForWorkerBadJson() throws Exception {
        mockRequestBody(mockRequest, "\"+1234567890\"");
        when(mockAccountService.getAccount(any())).thenReturn(Optional.of(mockAccount));

        controller.saveParticipantReport(TEST_USER_ID, REPORT_ID);
    }
    
    // This should be legal
    @Test
    public void saveParticipantEmptyReportData() throws Exception {
        String json = TestUtils.createJson("{'date':'2015-02-12','data':{}}");
        mockRequestBody(mockRequest, json);
        when(mockAccountService.getAccount(any())).thenReturn(Optional.of(mockAccount));

        StatusMessage result = controller.saveParticipantReport(OTHER_PARTICIPANT_ID, REPORT_ID);
        assertEquals(result.getMessage(), "Report data saved.");
    }
    
    @Test
    public void saveParticipantReportForWorker() throws Exception {
        String json = TestUtils.createJson("{'healthCode': '"+OTHER_PARTICIPANT_HEALTH_CODE+
                "', 'date':'2015-02-12','data':['A','B','C']}");
        mockRequestBody(mockRequest, json);
        when(mockAccountService.getAccountId(TEST_APP_ID, "healthCode:"+OTHER_PARTICIPANT_HEALTH_CODE))
            .thenReturn(Optional.of(TEST_USER_ID));

        StatusMessage result = controller.saveParticipantReportForWorker(REPORT_ID);
        assertEquals(result.getMessage(), "Report data saved.");
        
        verify(mockReportService).saveParticipantReport(eq(TEST_APP_ID), eq(TEST_USER_ID), eq(REPORT_ID),
                eq(OTHER_PARTICIPANT_HEALTH_CODE), reportDataCaptor.capture());
        ReportData reportData = reportDataCaptor.getValue();
        assertEquals(reportData.getDate().toString(), LocalDate.parse("2015-02-12").toString());
        assertNull(reportData.getKey());
        assertEquals(reportData.getData().get(0).asText(), "A");
        assertEquals(reportData.getData().get(1).asText(), "B");
        assertEquals(reportData.getData().get(2).asText(), "C");
    }
    
    @Test
    public void saveParticipantReportForWorkerRequiresHealthCode() throws Exception {
        String json = TestUtils.createJson("{'date':'2015-02-12','data':['A','B','C']}");
        mockRequestBody(mockRequest, json);
        try {
            controller.saveParticipantReportForWorker(REPORT_ID);
            fail("Should have thrown exception");
        } catch(BadRequestException e) {
            assertEquals(e.getMessage(), "A health code is required to save report data.");
            verifyNoMoreInteractions(mockReportService);
        }
    }
    
    @Test
    public void getParticipantReportIndices() throws Exception {
        ReportTypeResourceList<? extends ReportIndex> results = controller.listParticipantReportIndices();
        
        assertEquals(results.getItems().size(), 1);
        assertEquals(results.getRequestParams().get("reportType"), ReportType.PARTICIPANT);
        assertEquals(results.getItems().getFirst().getIdentifier(), "fofo");
        
        verify(mockReportService).getReportIndices(TEST_APP_ID, ReportType.PARTICIPANT);
    }
    
    @Test
    public void getParticipantReportIndex() throws Exception {
        ReportIndex index = ReportIndex.create();
        index.setIdentifier(REPORT_ID);
        index.setPublic(true);
        index.setStudyIds(USER_STUDY_IDS);
        
        when(mockReportService.getReportIndex(any())).thenReturn(index);
        
        ReportIndex result = controller.getParticipantReportIndex(REPORT_ID);
        
        assertEquals(result.getIdentifier(), REPORT_ID);
        assertEquals(result.getStudyIds(), USER_STUDY_IDS);
        
        verify(mockReportService).getReportIndex(reportDataKeyCaptor.capture());
        ReportDataKey key = reportDataKeyCaptor.getValue();
        assertEquals(key.getAppId(), TEST_APP_ID);
        assertEquals(key.getIdentifier(), REPORT_ID);
        assertEquals(key.getReportType(), PARTICIPANT);
    }
    
    @Test
    public void deleteParticipantReportData() throws Exception {
        when(mockAccountService.getAccount(any())).thenReturn(Optional.of(mockOtherAccount));
        
        StatusMessage result = controller.deleteParticipantReport(OTHER_PARTICIPANT_ID, REPORT_ID);
        assertEquals(result.getMessage(), "Report deleted.");
        
        verify(mockReportService).deleteParticipantReport(session.getAppId(), OTHER_PARTICIPANT_ID, REPORT_ID,
                OTHER_PARTICIPANT_HEALTH_CODE);
    }
    
    @Test
    public void deleteParticipantReportDataRecord() throws Exception {
        when(mockAccountService.getAccount(any())).thenReturn(Optional.of(mockOtherAccount));
        
        StatusMessage result = controller.deleteParticipantReportRecord(OTHER_PARTICIPANT_ID, REPORT_ID, "2014-05-10");
        assertEquals(result.getMessage(), "Report record deleted.");
        
        verify(mockReportService).deleteParticipantReportRecord(session.getAppId(), null, REPORT_ID,
                "2014-05-10", OTHER_PARTICIPANT_HEALTH_CODE);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void deleteParticipantRecordDataRecordDeveloper() {
        StudyParticipant regularUser = new StudyParticipant.Builder().copyOf(session.getParticipant())
            .withRoles(Sets.newHashSet(ORG_ADMIN)).build();
        session.setParticipant(regularUser);
        
        controller.deleteParticipantReportRecord(REPORT_ID, "bar", "2014-05-10");
    }
    
    @Test
    public void adminCanDeleteParticipantIndex() throws Exception {
        // Mock getAuthenticatedSession().
        doReturn(session).when(controller).getAuthenticatedSession(ADMIN);

        // Execute and validate.
        StatusMessage result = controller.deleteParticipantReportIndex(REPORT_ID);
        assertEquals(result.getMessage(), "Report index deleted.");
        
        verify(mockReportService).deleteParticipantReportIndex(TEST_APP_ID, TEST_USER_ID, REPORT_ID);
    }
    
    @Test(expectedExceptions = UnauthorizedException.class)
    public void nonAdminCannotDeleteParticipantIndex() {
        // Mock getAuthenticatedSession().
        doThrow(UnauthorizedException.class).when(controller).getAuthenticatedSession(ADMIN);

        // Execute and validate.
        controller.deleteParticipantReportIndex(REPORT_ID);
    }
        
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp=".*Account not found.*")
    public void getParticipantReportAccountNotFound() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());

        when(mockAccountService.getAccount(any()))
            .thenThrow(new EntityNotFoundException(Account.class));
        
        controller.getParticipantReport(TEST_USER_ID, REPORT_ID, null, null);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp=".*Account not found.*")
    public void getParticipantReportForWorkerAccountNotFound() {
        doReturn(session).when(controller).getAuthenticatedSession(WORKER);

        when(mockAccountService.getAccount(any()))
            .thenThrow(new EntityNotFoundException(Account.class));
        
        controller.getParticipantReportForWorker(TEST_APP_ID, TEST_USER_ID, REPORT_ID, null, null);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp=".*Account not found.*")
    public void saveParticipantReportAccountNotFound() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        
        when(mockAccountService.getAccount(any()))
            .thenThrow(new EntityNotFoundException(Account.class));

        controller.saveParticipantReport(TEST_USER_ID, REPORT_ID);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp=".*Account not found.*")
    public void deleteParticipantReportAccountNotFound() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, WORKER);
        
        when(mockAccountService.getAccount(any()))
            .thenThrow(new EntityNotFoundException(Account.class));

        controller.deleteParticipantReport(TEST_USER_ID, REPORT_ID);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp=".*Account not found.*")
    public void deleteParticipantReportRecordAccountNotFound() {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, WORKER);
        
        when(mockAccountService.getAccount(any()))
            .thenThrow(new EntityNotFoundException(Account.class));

        controller.deleteParticipantReportRecord(TEST_USER_ID, REPORT_ID, null);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp=".*Account not found.*")
    public void getParticipantReportV4AccountNotFound() {
        RequestContext.set(new RequestContext.Builder()
                .withCallerRoles(ImmutableSet.of(RESEARCHER)).build());
        
        doReturn(session).when(controller).getAdministrativeSession();
        
        when(mockAccountService.getAccount(any()))
            .thenThrow(new EntityNotFoundException(Account.class));
        
        controller.getParticipantReportV4(TEST_USER_ID, REPORT_ID, null, null, null, null);
    }
    
    @Test(expectedExceptions = EntityNotFoundException.class, 
            expectedExceptionsMessageRegExp=".*Account not found.*")
    public void getParticipantReportForWorkerV4AccountNotFound() {
        doReturn(session).when(controller).getAuthenticatedSession(WORKER);
        
        when(mockAccountService.getAccount(any()))
            .thenThrow(new EntityNotFoundException(Account.class));

        controller.getParticipantReportForWorkerV4(TEST_APP_ID, TEST_USER_ID, REPORT_ID, null, null, null, null);
    }
    
    private void assertResultContent(LocalDate expectedStartDate, LocalDate expectedEndDate,
            DateRangeResourceList<? extends ReportData> result) throws Exception {

        assertEquals(result.getRequestParams().get("startDate").toString(), "2015-01-02");
        assertEquals(result.getRequestParams().get("endDate").toString(), "2015-02-02");
        assertEquals(result.getItems().size(), 2);
        
        ReportData data1 = result.getItems().getFirst();
        assertEquals(data1.getDate(), "2015-02-10");
        
        JsonNode child1Data = data1.getData();
        assertEquals("First", child1Data.get("field1").asText());
        assertEquals("Name", child1Data.get("field2").asText());

        ReportData data2 = result.getItems().get(1);
        assertEquals(data2.getDate(), "2015-02-12");
        
        JsonNode child2Data = data2.getData();
        assertEquals("Last", child2Data.get("field1").asText());
        assertEquals("Name", child2Data.get("field2").asText());
    }
    
    private ForwardCursorPagedResourceList<ReportData> makePagedResults(DateTime startTime, DateTime endTime,
            String offsetKey, int pageSize) {
        List<ReportData> list = Lists.newArrayList();
        list.add(createReport(DateTime.parse("2015-02-10T00:00:00.000Z"), "First", "Name"));
        list.add(createReport(DateTime.parse("2015-02-12T00:00:00.000Z"), "Last", "Name"));
        return new ForwardCursorPagedResourceList<>(list, NEXT_PAGE_OFFSET_KEY)
            .withRequestParam(ResourceList.OFFSET_KEY, offsetKey)
            .withRequestParam(ResourceList.PAGE_SIZE, pageSize)
            .withRequestParam(ResourceList.START_TIME, startTime)
            .withRequestParam(ResourceList.END_TIME, endTime);
    }

    private static void assertReportDataPage(DateTime expectedStartTime, DateTime expectedEndTime,
            String expectedOffsetKey, int expectedPageSize, ForwardCursorPagedResourceList<ReportData> page) {
        // Verify metadata.
        assertEquals(NEXT_PAGE_OFFSET_KEY, page.getNextPageOffsetKey());
        assertEquals(expectedStartTime.toString(), page.getRequestParams().get(ResourceList.START_TIME));
        assertEquals(expectedEndTime.toString(), page.getRequestParams().get(ResourceList.END_TIME));
        assertEquals(expectedOffsetKey, page.getRequestParams().get(ResourceList.OFFSET_KEY));
        assertEquals(expectedPageSize, page.getRequestParams().get(ResourceList.PAGE_SIZE));

        // Verify items. Note that key doesn't show up, because it's tagged with @JsonIgnore,
        List<ReportData> list = page.getItems();
        assertEquals(2, list.size());

        assertEquals("2015-02-10T00:00:00.000Z", list.getFirst().getDateTime().toString());
        assertEquals(2, list.getFirst().getData().size());
        assertEquals("First", list.getFirst().getData().get("field1").textValue());
        assertEquals("Name", list.getFirst().getData().get("field2").textValue());

        assertEquals("2015-02-12T00:00:00.000Z", list.get(1).getDateTime().toString());
        assertEquals(2, list.get(1).getData().size());
        assertEquals("Last", list.get(1).getData().get("field1").textValue());
        assertEquals("Name", list.get(1).getData().get("field2").textValue());
    }

    private DateRangeResourceList<ReportData> makeResults(LocalDate startDate, LocalDate endDate){
        List<ReportData> list = Lists.newArrayList();
        list.add(createReport(LocalDate.parse("2015-02-10"), "First", "Name"));
        list.add(createReport(LocalDate.parse("2015-02-12"), "Last", "Name"));
        
        return new DateRangeResourceList<ReportData>(list)
                .withRequestParam("startDate", startDate)
                .withRequestParam("endDate", endDate);
    }
    
    private ReportData createReport(LocalDate date, String fieldValue1, String fieldValue2) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("field1", fieldValue1);
        node.put("field2", fieldValue2);
        ReportData report = ReportData.create();
        report.setKey("foo:" + TEST_APP_ID);
        report.setLocalDate(date);
        report.setData(node);
        return report;
    }
    
    private ReportData createReport(DateTime date, String fieldValue1, String fieldValue2) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("field1", fieldValue1);
        node.put("field2", fieldValue2);
        ReportData report = ReportData.create();
        report.setKey("foo:" + TEST_APP_ID);
        report.setDateTime(date);
        report.setData(node);
        return report;
    }
}
