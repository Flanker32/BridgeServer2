package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.BridgeConstants.API_DEFAULT_PAGE_SIZE;
import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.TestConstants.GUID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.sagebionetworks.bridge.models.templates.TemplateType.EMAIL_ACCOUNT_EXISTS;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

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
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.models.GuidVersionHolder;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.templates.Template;
import org.sagebionetworks.bridge.services.AppService;
import org.sagebionetworks.bridge.services.TemplateService;

public class TemplateControllerTest extends Mockito {
    
    @Mock
    TemplateService mockTemplateService;
    
    @Mock
    HttpServletRequest mockRequest;
    
    @Mock
    HttpServletResponse mockResponse;
    
    @Mock
    AppService mockAppService;
    
    @InjectMocks
    @Spy
    TemplateController controller;
    
    @Captor
    ArgumentCaptor<Template> templateCaptor;
    
    UserSession session;
    
    App app;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
        
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
        
        session = new UserSession();
        session.setAppId(TEST_APP_ID);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER);
        doReturn(session).when(controller).getAuthenticatedSession(DEVELOPER, ADMIN);
        
        app = App.create();
        app.setIdentifier(TEST_APP_ID);
        when(mockAppService.getApp(TEST_APP_ID)).thenReturn(app);
    }

    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(TemplateController.class);
        assertGet(TemplateController.class, "getTemplates");
        assertPost(TemplateController.class, "createTemplate");
        assertGet(TemplateController.class, "getTemplate");
        assertPost(TemplateController.class, "updateTemplate");
        assertDelete(TemplateController.class, "deleteTemplate");
    }
    
    @Test
    public void getTemplates() {
        List<? extends Template> items = ImmutableList.of(Template.create(), Template.create());
        PagedResourceList<? extends Template> page = new PagedResourceList<>(items, 2);
        doReturn(page).when(mockTemplateService).getTemplatesForType(TEST_APP_ID, EMAIL_ACCOUNT_EXISTS, 100, 50, true);
        
        PagedResourceList<? extends Template> result = controller
                .getTemplates(EMAIL_ACCOUNT_EXISTS.name().toLowerCase(), "100", "50", "true");
        assertEquals(result.getItems().size(), 2);
        
        verify(mockTemplateService).getTemplatesForType(TEST_APP_ID, EMAIL_ACCOUNT_EXISTS, 100, 50, true);
    }
    
    @Test
    public void getTemplatesDefaults() {
        controller.getTemplates(EMAIL_ACCOUNT_EXISTS.name().toLowerCase(), null, null, null);
        verify(mockTemplateService).getTemplatesForType(TEST_APP_ID, EMAIL_ACCOUNT_EXISTS, 0, API_DEFAULT_PAGE_SIZE, false);
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = "Template type is required")
    public void getTemplatesTypeIsRequired() {
        controller.getTemplates(null, null, null, null);
    }
    
    @Test(expectedExceptions = BadRequestException.class, 
            expectedExceptionsMessageRegExp = "Invalid template type.*")
    public void getTemplatesInvalidType() {
        controller.getTemplates("not-a-type", null, null, null);
    }
    
    @Test
    public void createTemplate() throws Exception {
        Template template = Template.create();
        template.setName("This is a name");
        mockRequestBody(mockRequest, template);
        
        GuidVersionHolder holder = new GuidVersionHolder(GUID, 1L);
        when(mockTemplateService.createTemplate(eq(app), any())).thenReturn(holder);
        
        GuidVersionHolder result = controller.createTemplate();
        assertEquals(result.getGuid(), GUID);
        assertEquals(result.getVersion(), Long.valueOf(1));
        
        verify(mockTemplateService).createTemplate(eq(app), templateCaptor.capture());
        assertEquals(templateCaptor.getValue().getName(), "This is a name");
    }
    
    @Test
    public void getTemplate() {
        Template template = Template.create();
        template.setName("This is a name");
        when(mockTemplateService.getTemplate(TEST_APP_ID, GUID)).thenReturn(template);
        
        Template result = controller.getTemplate(GUID);
        assertSame(result, template);
        
        verify(mockTemplateService).getTemplate(TEST_APP_ID, GUID);
    }

    @Test
    public void updateTemplate() throws Exception {
        Template template = Template.create();
        template.setName("This is a name");
        mockRequestBody(mockRequest, template);
        
        GuidVersionHolder holder = new GuidVersionHolder(GUID, 2L);
        when(mockTemplateService.updateTemplate(eq(TEST_APP_ID), any())).thenReturn(holder);
        
        GuidVersionHolder result = controller.updateTemplate(GUID);
        assertEquals(result.getGuid(), GUID);
        assertEquals(result.getVersion(), Long.valueOf(2));
        
        verify(mockTemplateService).updateTemplate(eq(TEST_APP_ID), templateCaptor.capture());
        assertEquals(templateCaptor.getValue().getName(), "This is a name");
        assertEquals(templateCaptor.getValue().getGuid(), GUID);
    }
    
    @Test
    public void deleteTemplateDefault() throws Exception {
        StatusMessage message = controller.deleteTemplate(GUID, null);
        assertEquals(message.getMessage(), "Template deleted.");
        
        verify(mockTemplateService).deleteTemplate(TEST_APP_ID, GUID);
    }

    @Test
    public void deleteTemplate() throws Exception {
        StatusMessage message = controller.deleteTemplate(GUID, "false");
        assertEquals(message.getMessage(), "Template deleted.");
        
        verify(mockTemplateService).deleteTemplate(TEST_APP_ID, GUID);
    }

    @Test
    public void developerCannotPermanentlyDelete() throws Exception {
        StatusMessage message = controller.deleteTemplate(GUID, "true");
        assertEquals(message.getMessage(), "Template deleted.");
        
        verify(mockTemplateService).deleteTemplate(TEST_APP_ID, GUID);
    }
    
    @Test
    public void adminCanPermanentlyDelete() throws Exception {
        session.setParticipant(new StudyParticipant.Builder().withRoles(ImmutableSet.of(ADMIN)).build());
        StatusMessage message = controller.deleteTemplate(GUID, "true");
        assertEquals(message.getMessage(), "Template deleted.");
        
        verify(mockTemplateService).deleteTemplatePermanently(TEST_APP_ID, GUID);
    }
}
