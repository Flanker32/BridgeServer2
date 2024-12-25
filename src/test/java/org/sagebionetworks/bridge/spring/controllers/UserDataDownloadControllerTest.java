package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestUtils.assertAccept;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.sagebionetworks.bridge.spring.controllers.UserDataDownloadController.ACCEPTED_MSG;
import static org.testng.Assert.assertEquals;

import jakarta.servlet.http.HttpServletRequest;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.DateRange;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.services.UserDataDownloadService;

public class UserDataDownloadControllerTest extends Mockito {
    private static final String START_DATE = "2015-08-15";
    private static final String END_DATE = "2015-08-19";
    private static final String USER_ID = "test-user-id";
    private static final String EMAIL = "email@email.com";

    @Mock
    UserSession mockSession;
    
    @Mock
    UserDataDownloadService mockService;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @InjectMocks
    @Spy
    UserDataDownloadController controller;
    
    @Captor
    ArgumentCaptor<DateRange> dateRangeCaptor;
    
    @BeforeMethod
    public void before() throws Exception {
        MockitoAnnotations.initMocks(this);
        
        doReturn(mockSession).when(controller).getAuthenticatedAndConsentedSession();
        doReturn(TEST_APP_ID).when(mockSession).getAppId();
        doReturn(mockRequest).when(controller).request();
    }
    
    @Test
    public void verifyAnnotations() throws Exception { 
        assertCrossOrigin(UserDataDownloadController.class);
        assertAccept(UserDataDownloadController.class, "requestUserData");
    }
    
    
    @Test
    public void testWithEmail() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder().withId(USER_ID).withEmail(EMAIL)
                .withEmailVerified(Boolean.TRUE).build();
        doReturn(participant).when(mockSession).getParticipant();
        mockWithJson();

        // execute and validate
        StatusMessage result = controller.requestUserData();
        assertEquals(result, ACCEPTED_MSG);

        verify(mockService).requestUserData(eq(TEST_APP_ID), eq(USER_ID), dateRangeCaptor.capture());
        
        // validate args sent to mock service
        DateRange dateRange = dateRangeCaptor.getValue();
        assertEquals("2015-08-15", dateRange.getStartDate().toString());
        assertEquals("2015-08-19", dateRange.getEndDate().toString());
    }

    @Test
    public void testWithPhone() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder().withId(USER_ID)
                .withPhone(TestConstants.PHONE).withPhoneVerified(Boolean.TRUE).build();
        doReturn(participant).when(mockSession).getParticipant();
        mockWithJson();

        // execute and validate
        StatusMessage result = controller.requestUserData();
        assertEquals(result, ACCEPTED_MSG);

        verify(mockService).requestUserData(eq(TEST_APP_ID), eq(USER_ID), dateRangeCaptor.capture());
        
        // validate args sent to mock service
        DateRange dateRange = dateRangeCaptor.getValue();
        assertEquals("2015-08-15", dateRange.getStartDate().toString());
        assertEquals("2015-08-19", dateRange.getEndDate().toString());
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void throwExceptionIfAccountHasNoEmail() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder().withId(USER_ID).build();
        doReturn(participant).when(mockSession).getParticipant();
        mockWithJson();

        controller.requestUserData();
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void throwExceptionIfAccountEmailUnverified() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder().withId(USER_ID)
                .withEmail(EMAIL).withEmailVerified(Boolean.FALSE).build();
        doReturn(participant).when(mockSession).getParticipant();
        mockWithJson();

        controller.requestUserData();
    }

    @Test(expectedExceptions = BadRequestException.class)
    public void throwExceptionIfAccountHasNoPhone() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder().withId(USER_ID)
                .withPhoneVerified(Boolean.TRUE).build();
        doReturn(participant).when(mockSession).getParticipant();
        mockWithJson();

        controller.requestUserData();
    }
    
    @Test(expectedExceptions = BadRequestException.class)
    public void throwExceptionIfAccountPhoneUnverified() throws Exception {
        StudyParticipant participant = new StudyParticipant.Builder().withId(USER_ID)
                .withPhone(TestConstants.PHONE).withPhoneVerified(Boolean.FALSE).build();
        doReturn(participant).when(mockSession).getParticipant();
        mockWithJson();

        controller.requestUserData();
    }
    
    private void mockWithJson() throws Exception {
        // This isn't in the before(), because we don't want to mock the JSON body for the query params test.
        String dateRangeJsonText = "{\n" +
                "   \"startDate\":\"" + START_DATE + "\",\n" +
                "   \"endDate\":\"" + END_DATE + "\"\n" +
                "}";
        mockRequestBody(mockRequest, dateRangeJsonText);
    }

}
