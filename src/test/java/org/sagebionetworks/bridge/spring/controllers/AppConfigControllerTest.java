package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.TestConstants.HEALTH_CODE;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestConstants.UA;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.cache.ViewCache;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.GuidVersionHolder;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.services.AppConfigService;
import org.sagebionetworks.bridge.services.AppService;

public class AppConfigControllerTest extends Mockito {
    
    private static final String GUID = "guid";
    private static final CacheKey CACHE_KEY = CacheKey.appConfigList(TEST_APP_ID);
    
    @InjectMocks
    @Spy
    private AppConfigController controller;
    
    @Mock
    private HttpServletRequest mockRequest;
    
    @Mock
    private HttpServletResponse mockResponse;
    
    @Mock
    private AppConfigService mockService;
    
    @Mock
    private AppService mockAppService;
    
    @Mock
    private CacheProvider mockCacheProvider;
    
    @Mock
    private ViewCache viewCache;
    
    @Captor
    private ArgumentCaptor<CriteriaContext> contextCaptor;
    
    private App app;
    
    private AppConfig appConfig;
    
    private UserSession session;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        
        // With mock dependencies, the view cache just doesn't work (no cache hits), and tests that aren't
        // specifically verifying caching behavior pass.
        viewCache = new ViewCache();
        viewCache.setCacheProvider(mockCacheProvider);
        viewCache.setObjectMapper(BridgeObjectMapper.get());
        viewCache.setCachePeriod(100);
        controller.setViewCache(viewCache);
        
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
        
        appConfig = AppConfig.create();
        appConfig.setGuid(GUID);
        appConfig.setVersion(1L);
        
        app = App.create();
        app.setIdentifier(TEST_APP_ID);
        
        session = new UserSession();
        session.setAppId(TEST_APP_ID);
        session.setParticipant(new StudyParticipant.Builder()
                .withDataGroups(TestConstants.USER_DATA_GROUPS)
                .withLanguages(TestConstants.LANGUAGES)
                .withRoles(ImmutableSet.of(DEVELOPER))
                .withHealthCode(HEALTH_CODE)
                .build());
    }
    
    @AfterMethod
    public void afterMethod() {
        RequestContext.set(null);
    }
    
    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(AppConfigController.class);
        assertGet(AppConfigController.class, "getAppConfigByCriteria");
        assertGet(AppConfigController.class, "getAppConfigs");
        assertCreate(AppConfigController.class, "createAppConfig");
        assertGet(AppConfigController.class, "getAppConfig");
        assertPost(AppConfigController.class, "updateAppConfig");
        assertDelete(AppConfigController.class, "deleteAppConfig");
    }
    
    @Test
    public void getAppConfigByCriteria() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerLanguages(ImmutableList.of("en"))
                .withUserAgent(UA).build());
        
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        when(mockService.getAppConfigForUser(contextCaptor.capture(), eq(true))).thenReturn(appConfig);
        
        String string = controller.getAppConfigByCriteria(TEST_APP_ID);
        AppConfig returnedValue = BridgeObjectMapper.get().readValue(string, AppConfig.class);
        assertEquals(returnedValue, appConfig);
        
        verify(mockService).getAppConfigForUser(contextCaptor.capture(), eq(true));
        CriteriaContext capturedContext = contextCaptor.getValue();
        
        assertEquals(capturedContext.getAppId(), TEST_APP_ID);
        assertEquals(capturedContext.getClientInfo().getAppName(), "Asthma");
        assertEquals(capturedContext.getClientInfo().getAppVersion(), Integer.valueOf(26));
        assertEquals(capturedContext.getLanguages(), ImmutableList.of("en"));
        assertEquals(capturedContext.getClientInfo().getOsName(), "iPhone OS");
        RequestContext.set(null);
    }

    @Test
    public void getAppConfigs() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        List<AppConfig> list = ImmutableList.of(AppConfig.create(), AppConfig.create());
        when(mockService.getAppConfigs(TEST_APP_ID, false)).thenReturn(list);
        
        ResourceList<AppConfig> results = controller.getAppConfigs("false");
        assertEquals(2, results.getItems().size());
        assertFalse((Boolean)results.getRequestParams().get("includeDeleted"));
        verify(mockService).getAppConfigs(TEST_APP_ID, false);
    }

    @Test
    public void getAppConfig() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        when(mockService.getAppConfig(TEST_APP_ID, GUID)).thenReturn(appConfig);
        
        AppConfig result = controller.getAppConfig(GUID);

        assertEquals(appConfig.getGuid(), result.getGuid());
        verify(mockService).getAppConfig(TEST_APP_ID, GUID);
    }

    @Test
    public void createAppConfig() throws Exception {
        mockRequestBody(mockRequest, appConfig);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        when(mockService.createAppConfig(eq(TEST_APP_ID), any())).thenReturn(appConfig);
        
        GuidVersionHolder result = controller.createAppConfig();
        
        assertEquals(result.getGuid(), GUID);
        assertEquals(result.getVersion(), Long.valueOf(1));
        verify(mockService).createAppConfig(eq(TEST_APP_ID), any());
    }
    
    @Test
    public void updateAppConfig() throws Exception {
        mockRequestBody(mockRequest, appConfig);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        when(mockService.updateAppConfig(eq(TEST_APP_ID), any())).thenReturn(appConfig);
        
        GuidVersionHolder result = controller.updateAppConfig(GUID);
        
        assertEquals(result.getGuid(), GUID);
        assertEquals(result.getVersion(), Long.valueOf(1));
        verify(mockService).updateAppConfig(eq(TEST_APP_ID), any());
    }
    
    @Test
    public void deleteAppConfigDefault() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);

        StatusMessage message = controller.deleteAppConfig(GUID, null);
        assertEquals(message.getMessage(), "App config deleted.");
        
        verify(mockService).deleteAppConfig(TEST_APP_ID, GUID);
    }

    @Test
    public void deleteAppConfig() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        
        StatusMessage message = controller.deleteAppConfig(GUID, "false");
        assertEquals(message.getMessage(), "App config deleted.");
        
        verify(mockService).deleteAppConfig(TEST_APP_ID, GUID);
    }

    @Test
    public void developerCannotPermanentlyDelete() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        
        StatusMessage message = controller.deleteAppConfig(GUID, "true");
        assertEquals(message.getMessage(), "App config deleted.");
        
        verify(mockService).deleteAppConfig(TEST_APP_ID, GUID);
    }
    
    @Test
    public void adminCanPermanentlyDelete() throws Exception {
        session.setParticipant(new StudyParticipant.Builder()
                .copyOf(session.getParticipant())
                .withRoles(ImmutableSet.of(DEVELOPER, ADMIN))
                .build());
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        
        StatusMessage message = controller.deleteAppConfig(GUID, "true");
        assertEquals(message.getMessage(), "App config deleted.");
        
        verify(mockService).deleteAppConfigPermanently(TEST_APP_ID, GUID);
    }

    @Test
    public void getAppConfigByCriteriaAddsToCache() throws Exception {
        RequestContext.set(new RequestContext.Builder()
                .withCallerLanguages(ImmutableList.of("en"))
                .withUserAgent(UA).build());
        mockRequestBody(mockRequest, appConfig);
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
        when(mockService.getAppConfigForUser(any(), eq(true))).thenReturn(appConfig);
        
        controller.getAppConfigByCriteria(TEST_APP_ID);
        
        verify(mockCacheProvider).addCacheKeyToSet(CACHE_KEY, "26:iPhone OS:en:" + TEST_APP_ID + ":AppConfig:view");
    }

    @Test
    public void createAppConfigDeletesCache() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        when(mockService.createAppConfig(any(), any())).thenReturn(appConfig);
        mockRequestBody(mockRequest, appConfig);
        
        controller.createAppConfig();
        
        verify(mockCacheProvider).removeSetOfCacheKeys(CACHE_KEY);
    }

    @Test
    public void updateAppConfigDeletesCache() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        when(mockService.updateAppConfig(any(), any())).thenReturn(appConfig);
        mockRequestBody(mockRequest, appConfig);
        
        controller.updateAppConfig("guid");
        
        verify(mockCacheProvider).removeSetOfCacheKeys(CACHE_KEY);
    }

    @Test
    public void deleteAppConfigDeletesCache() throws Exception {
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        
        controller.deleteAppConfig("guid", null);
        
        verify(mockCacheProvider).removeSetOfCacheKeys(CACHE_KEY);
    }
}
