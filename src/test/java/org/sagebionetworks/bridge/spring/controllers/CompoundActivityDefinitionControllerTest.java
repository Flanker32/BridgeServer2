package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.TestUtils.assertCreate;
import static org.sagebionetworks.bridge.TestUtils.assertCrossOrigin;
import static org.sagebionetworks.bridge.TestUtils.assertDelete;
import static org.sagebionetworks.bridge.TestUtils.assertGet;
import static org.sagebionetworks.bridge.TestUtils.assertPost;
import static org.sagebionetworks.bridge.TestUtils.mockRequestBody;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableList;

import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.schedules.CompoundActivityDefinition;
import org.sagebionetworks.bridge.services.CompoundActivityDefinitionService;
import org.sagebionetworks.bridge.services.AppService;

public class CompoundActivityDefinitionControllerTest extends Mockito {
    private static final String TASK_ID = "test-task";

    private CompoundActivityDefinitionController controller;
    private CompoundActivityDefinitionService defService;
    private AppService appService;
    private HttpServletRequest mockRequest;
    private HttpServletResponse mockResponse;

    @BeforeMethod
    public void setup() {
        // mock session
        UserSession mockSession = new UserSession();
        mockSession.setAppId(TEST_APP_ID);

        // mock app service
        appService = mock(AppService.class);

        // mock def service
        defService = mock(CompoundActivityDefinitionService.class);

        // spy controller
        controller = spy(new CompoundActivityDefinitionController());
        doReturn(mockSession).when(controller).getAuthenticatedSession(any());
        controller.setCompoundActivityDefService(defService);
        controller.setAppService(appService);
        
        mockRequest = mock(HttpServletRequest.class);
        mockResponse = mock(HttpServletResponse.class);
        doReturn(mockRequest).when(controller).request();
        doReturn(mockResponse).when(controller).response();
    }

    @Test
    public void verifyAnnotations() throws Exception {
        assertCrossOrigin(CompoundActivityDefinitionController.class);
        assertCreate(CompoundActivityDefinitionController.class, "createCompoundActivityDefinition");
        assertDelete(CompoundActivityDefinitionController.class, "deleteCompoundActivityDefinition");
        assertGet(CompoundActivityDefinitionController.class, "getAllCompoundActivityDefinitionsInApp");
        assertGet(CompoundActivityDefinitionController.class, "getCompoundActivityDefinition");
        assertPost(CompoundActivityDefinitionController.class, "updateCompoundActivityDefinition");
    }
    
    @Test
    public void create() throws Exception{
        // make input def - Mark it with task ID for tracing. No other params matter.
        CompoundActivityDefinition controllerInput = CompoundActivityDefinition.create();
        controllerInput.setTaskId(TASK_ID);

        // Set it as the mock JSON input.
        mockRequestBody(mockRequest, controllerInput);

        // mock service - Service output should have both task ID and app ID so we can test that app ID is filtered
        // out
        CompoundActivityDefinition serviceOutput = CompoundActivityDefinition.create();
        serviceOutput.setAppId(TEST_APP_ID);
        serviceOutput.setTaskId(TASK_ID);

        ArgumentCaptor<CompoundActivityDefinition> serviceInputCaptor = ArgumentCaptor.forClass(
                CompoundActivityDefinition.class);
        when(defService.createCompoundActivityDefinition(eq(TEST_APP_ID), serviceInputCaptor.capture()))
                .thenReturn(serviceOutput);

        // execute and validate
        String result = controller.createCompoundActivityDefinition();
        CompoundActivityDefinition controllerOutput = getDefFromResult(result);
        assertEquals(controllerOutput.getTaskId(), TASK_ID);
        assertNull(controllerOutput.getAppId());

        // validate service input
        CompoundActivityDefinition serviceInput = serviceInputCaptor.getValue();
        assertEquals(TASK_ID, serviceInput.getTaskId());
        
        verify(controller).getAuthenticatedSession(Roles.DEVELOPER);
        verifyZeroInteractions(appService);
    }

    @Test
    public void delete() throws Exception {
        // execute and validate
        StatusMessage result = controller.deleteCompoundActivityDefinition(TASK_ID);
        assertEquals(result.getMessage(), "Compound activity definition has been deleted.");

        // verify call through to the service
        verify(defService).deleteCompoundActivityDefinition(TEST_APP_ID, TASK_ID);
        verify(controller).getAuthenticatedSession(Roles.DEVELOPER);
        verifyZeroInteractions(appService);
    }

    @Test
    public void list() throws Exception {
        // mock service
        CompoundActivityDefinition serviceOutput = CompoundActivityDefinition.create();
        serviceOutput.setAppId(TEST_APP_ID);
        serviceOutput.setTaskId(TASK_ID);

        when(defService.getAllCompoundActivityDefinitionsInApp(TEST_APP_ID)).thenReturn(
                ImmutableList.of(serviceOutput));

        // execute and validate
        String result = controller.getAllCompoundActivityDefinitionsInApp();

        ResourceList<CompoundActivityDefinition> controllerOutputResourceList = BridgeObjectMapper.get().readValue(
                result, new TypeReference<ResourceList<CompoundActivityDefinition>>() {});
        List<CompoundActivityDefinition> controllerOutputList = controllerOutputResourceList.getItems();
        assertEquals(controllerOutputList.size(), 1);

        CompoundActivityDefinition controllerOutput = controllerOutputList.getFirst();
        assertEquals(controllerOutput.getTaskId(), TASK_ID);
        assertNull(controllerOutput.getAppId());
        verify(controller).getAuthenticatedSession(Roles.DEVELOPER);
        verifyZeroInteractions(appService);
    }

    @Test
    public void get() throws Exception {
        // mock service
        CompoundActivityDefinition serviceOutput = CompoundActivityDefinition.create();
        serviceOutput.setAppId(TEST_APP_ID);
        serviceOutput.setTaskId(TASK_ID);

        when(defService.getCompoundActivityDefinition(TEST_APP_ID, TASK_ID)).thenReturn(serviceOutput);

        // execute and validate
        String result = controller.getCompoundActivityDefinition(TASK_ID);
        CompoundActivityDefinition controllerOutput = getDefFromResult(result);
        assertEquals(controllerOutput.getTaskId(), TASK_ID);
        assertNull(controllerOutput.getAppId());
        verify(controller).getAuthenticatedSession(Roles.DEVELOPER);
        verifyZeroInteractions(appService);
    }

    @Test
    public void update() throws Exception {
        // make input def
        CompoundActivityDefinition controllerInput = CompoundActivityDefinition.create();
        controllerInput.setTaskId(TASK_ID);

        // Set it as the mock JSON input.
        mockRequestBody(mockRequest, controllerInput);

        // mock service
        CompoundActivityDefinition serviceOutput = CompoundActivityDefinition.create();
        serviceOutput.setAppId(TEST_APP_ID);
        serviceOutput.setTaskId(TASK_ID);

        ArgumentCaptor<CompoundActivityDefinition> serviceInputCaptor = ArgumentCaptor.forClass(
                CompoundActivityDefinition.class);
        when(defService.updateCompoundActivityDefinition(eq(TEST_APP_ID), eq(TASK_ID),
                serviceInputCaptor.capture())).thenReturn(serviceOutput);

        // execute and validate
        String result = controller.updateCompoundActivityDefinition(TASK_ID);
        CompoundActivityDefinition controllerOutput = getDefFromResult(result);
        assertEquals(controllerOutput.getTaskId(), TASK_ID);
        assertNull(controllerOutput.getAppId());

        // validate service input
        CompoundActivityDefinition serviceInput = serviceInputCaptor.getValue();
        assertEquals(serviceInput.getTaskId(), TASK_ID);
        verify(controller).getAuthenticatedSession(Roles.DEVELOPER);
        verifyZeroInteractions(appService);
    }

    private static CompoundActivityDefinition getDefFromResult(String jsonText) throws Exception {
        return BridgeObjectMapper.get().readValue(jsonText, CompoundActivityDefinition.class);
    }
}
