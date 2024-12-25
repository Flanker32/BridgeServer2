package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.TestUtils.mockEditAccount;
import static org.testng.Assert.assertEquals;

import java.util.Map;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.google.common.collect.Maps;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.services.AccountService;
import org.sagebionetworks.bridge.services.AppService;

public class EmailControllerTest extends Mockito {

    private static final String EMAIL = "email";
    private static final String DATA_BRACKET_EMAIL = "data[email]";
    private static final String UNSUBSCRIBE_TOKEN = "unsubscribeToken";
    private static final String TOKEN = "token";
    private static final String STUDY = "study";
    private static final String APP_ID = "appId";
    private static final String EMAIL_ADDRESS = "bridge-testing@sagebase.org";

    @Mock
    AppService mockAppService;

    @Mock
    AccountService mockAccountService;
    
    @Mock
    Account mockAccount;
    
    @Mock
    BridgeConfig mockConfig;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Mock
    HttpServletResponse mockResponse;
    
    @Spy
    @InjectMocks
    EmailController controller;

    App app;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        when(mockConfig.getEmailUnsubscribeToken()).thenReturn(UNSUBSCRIBE_TOKEN);
        when(mockAccountService.getAccountId(TEST_APP_ID, "email:"+EMAIL_ADDRESS))
            .thenReturn(Optional.of(TEST_USER_ID));
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
        mockEditAccount(mockAccountService, mockAccount);
        
        app = App.create();
        app.setIdentifier(TEST_APP_ID);
        
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
    }

    private void mockContext(String... values) {
        Map<String, String[]> paramMap = Maps.newHashMap();
        for (int i = 0; i <= values.length - 2; i += 2) {
            paramMap.put(values[i], new String[] { values[i + 1] });
        }
        for (Map.Entry<String, String[]> param : paramMap.entrySet()) {
            when(mockRequest.getParameter(param.getKey())).thenReturn(param.getValue()[0]);
        }
    }

    @Test
    public void testWithStudy() throws Exception {
        mockContext(DATA_BRACKET_EMAIL, EMAIL_ADDRESS, STUDY, TEST_APP_ID, TOKEN, UNSUBSCRIBE_TOKEN);

        controller.unsubscribeFromEmail();

        verify(mockAccount).setNotifyByEmail(false);
    }

    @Test
    public void testWithAppId() throws Exception {
        mockContext(DATA_BRACKET_EMAIL, EMAIL_ADDRESS, APP_ID, TEST_APP_ID, TOKEN, UNSUBSCRIBE_TOKEN);

        controller.unsubscribeFromEmail();

        verify(mockAccount).setNotifyByEmail(false);
    }
    
    @Test
    public void testWithEmail() throws Exception {
        mockContext(EMAIL, EMAIL_ADDRESS, STUDY, TEST_APP_ID, TOKEN, UNSUBSCRIBE_TOKEN);

        controller.unsubscribeFromEmail();

        verify(mockAccount).setNotifyByEmail(false);
    }
    
    @Test
    public void noStudyThrowsException() throws Exception {
        mockContext(DATA_BRACKET_EMAIL, EMAIL_ADDRESS, TOKEN, UNSUBSCRIBE_TOKEN);

        String result = controller.unsubscribeFromEmail();
        assertEquals(result, "App ID not found.");
    }

    @Test
    public void noEmailThrowsException() throws Exception {
        mockContext(STUDY, TEST_APP_ID, TOKEN, UNSUBSCRIBE_TOKEN);

        String result = controller.unsubscribeFromEmail();
        assertEquals(result, "Email not found.");
    }

    // noAccountThrowsException is now handled by the call to editAccount

    @Test
    public void missingTokenThrowsException() throws Exception {
        mockContext(DATA_BRACKET_EMAIL, EMAIL_ADDRESS, STUDY, TEST_APP_ID);

        String result = controller.unsubscribeFromEmail();
        assertEquals(result, "No authentication token provided.");
    }
}
