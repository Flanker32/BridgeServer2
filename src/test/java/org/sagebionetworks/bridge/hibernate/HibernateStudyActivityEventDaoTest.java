package org.sagebionetworks.bridge.hibernate;

import static org.joda.time.DateTimeZone.UTC;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_STUDY_ID;
import static org.sagebionetworks.bridge.TestConstants.TEST_USER_ID;
import static org.sagebionetworks.bridge.hibernate.HibernateStudyActivityEventDao.DELETE_SQL;
import static org.sagebionetworks.bridge.hibernate.HibernateStudyActivityEventDao.EVENT_ID_FIELD;
import static org.sagebionetworks.bridge.hibernate.HibernateStudyActivityEventDao.GET_RECENT_SQL;
import static org.sagebionetworks.bridge.hibernate.HibernateStudyActivityEventDao.HISTORY_SQL;
import static org.sagebionetworks.bridge.hibernate.HibernateStudyActivityEventDao.STUDY_ID_FIELD;
import static org.sagebionetworks.bridge.hibernate.HibernateStudyActivityEventDao.USER_ID_FIELD;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;
import static org.sagebionetworks.bridge.models.ResourceList.PAGE_SIZE;
import static org.sagebionetworks.bridge.models.activities.ActivityEventObjectType.CUSTOM;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.IMMUTABLE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;

import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableList;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;

public class HibernateStudyActivityEventDaoTest extends Mockito {

    @Mock
    HibernateHelper mockHelper;
    
    @InjectMocks
    HibernateStudyActivityEventDao dao;
    
    @Captor
    ArgumentCaptor<Map<String,Object>> paramsCaptor;
    
    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
    }
    
    @Test
    public void deleteCustomEvent() {
        StudyActivityEvent event = new StudyActivityEvent.Builder()
            .withUserId(TEST_USER_ID)
            .withStudyId(TEST_STUDY_ID)
            .withObjectType(CUSTOM)
            .withObjectId("event1").build();
        
        dao.deleteEvent(event);
        
        verify(mockHelper).nativeQueryUpdate(eq(DELETE_SQL), paramsCaptor.capture());
        Map<String,Object> params = paramsCaptor.getValue();
        assertEquals(params.get(USER_ID_FIELD), TEST_USER_ID);
        assertEquals(params.get(STUDY_ID_FIELD), TEST_STUDY_ID);
        assertEquals(params.get(EVENT_ID_FIELD), "custom:event1");
    }
    
    @Test
    public void publishEvent() {
        StudyActivityEvent event = new StudyActivityEvent.Builder().build();
        
        dao.publishEvent(event);
        
        verify(mockHelper).saveOrUpdate(event);
    }
    
    @Test
    public void getRecentStudyActivityEvents() { 
        List<Object[]> list = ImmutableList.of(new Object[12], new Object[12]);
        when(mockHelper.nativeQuery(any(), any())).thenReturn(list);
        
        List<StudyActivityEvent> retValue = dao.getRecentStudyActivityEvents(
                TEST_USER_ID, TEST_STUDY_ID);
        assertSame(retValue.size(), 2);
        
        verify(mockHelper).nativeQuery(eq(GET_RECENT_SQL), paramsCaptor.capture());
        Map<String,Object> params = paramsCaptor.getValue();
        assertEquals(params.get(USER_ID_FIELD), TEST_USER_ID);
        assertEquals(params.get(STUDY_ID_FIELD), TEST_STUDY_ID);
    }
    
    @Test
    public void getRecentStudyActivityEvent() throws Exception {
        StudyActivityEvent event1 = new StudyActivityEvent.Builder()
                .withEventId("custom:event1")
                .withTimestamp(CREATED_ON)
                .withCreatedOn(MODIFIED_ON)
                .withRecordCount(1)
                .build();
        StudyActivityEvent event2 = new StudyActivityEvent.Builder()
                .withEventId("custom:event2")
                .withTimestamp(CREATED_ON)
                .withCreatedOn(MODIFIED_ON)
                .withRecordCount(2)
                .build();
        
        List<Object[]> results = ImmutableList.of(StudyActivityEvent.recordify(event1),
                StudyActivityEvent.recordify(event2));
        when(mockHelper.nativeQuery(any(), any())).thenReturn(results);
        
        StudyActivityEvent retValue = dao.getRecentStudyActivityEvent(
                TEST_USER_ID, TEST_STUDY_ID, "custom:event2");
        assertEquals(retValue.getEventId(), "custom:event2");
        assertEquals(retValue.getTimestamp().withZone(UTC), CREATED_ON);
        assertEquals(retValue.getCreatedOn().withZone(UTC), MODIFIED_ON);
        assertEquals(retValue.getRecordCount(), Integer.valueOf(2));
        assertEquals(retValue.getUpdateType(), IMMUTABLE);
        
        verify(mockHelper).nativeQuery(eq(GET_RECENT_SQL), paramsCaptor.capture());
        Map<String,Object> params = paramsCaptor.getValue();
        assertEquals(params.get(USER_ID_FIELD), TEST_USER_ID);
        assertEquals(params.get(STUDY_ID_FIELD), TEST_STUDY_ID);
    }
    
    @Test
    public void getStudyActivityEventHistory() {
        StudyActivityEvent event = new StudyActivityEvent.Builder().build();
        List<StudyActivityEvent> list = ImmutableList.of(event, event);
        
        when(mockHelper.nativeQueryGet(eq("SELECT * " + HISTORY_SQL), any(), 
                eq(150), eq(50), eq(StudyActivityEvent.class))).thenReturn(list);
        when(mockHelper.nativeQueryCount(eq("SELECT count(*) " + HISTORY_SQL), any())).thenReturn(200);
        
        PagedResourceList<StudyActivityEvent> retValue = dao.getStudyActivityEventHistory(
                TEST_USER_ID, TEST_STUDY_ID, "custom:event1", 150, 50);
        
        assertEquals(retValue.getTotal(), Integer.valueOf(200));
        assertEquals(retValue.getItems().size(), 2);
        assertEquals(retValue.getRequestParams().get(OFFSET_BY), Integer.valueOf(150));
        assertEquals(retValue.getRequestParams().get(PAGE_SIZE), Integer.valueOf(50));
        
        verify(mockHelper).nativeQueryGet(eq("SELECT * " + HISTORY_SQL), paramsCaptor.capture(), 
                eq(150), eq(50), eq(StudyActivityEvent.class));
        verify(mockHelper).nativeQueryCount(eq("SELECT count(*) " + HISTORY_SQL), paramsCaptor.capture());
        
        Map<String,Object> params = paramsCaptor.getAllValues().getFirst();
        assertEquals(params.get(USER_ID_FIELD), TEST_USER_ID);
        assertEquals(params.get(STUDY_ID_FIELD), TEST_STUDY_ID);
        assertEquals(params.get(EVENT_ID_FIELD), "custom:event1");

        params = paramsCaptor.getAllValues().get(1);
        assertEquals(params.get(USER_ID_FIELD), TEST_USER_ID);
        assertEquals(params.get(STUDY_ID_FIELD), TEST_STUDY_ID);
        assertEquals(params.get(EVENT_ID_FIELD), "custom:event1");
    }
}
