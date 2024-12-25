package org.sagebionetworks.bridge.hibernate;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.sagebionetworks.bridge.models.ResourceList.OFFSET_BY;
import static org.sagebionetworks.bridge.models.ResourceList.PAGE_SIZE;

import java.util.List;
import java.util.Map;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.StudyActivityEventDao;
import org.sagebionetworks.bridge.models.PagedResourceList;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;

@Component
public class HibernateStudyActivityEventDao implements StudyActivityEventDao {
    
    static final String EVENT_ID_FIELD = "eventId";
    static final String STUDY_ID_FIELD = "studyId";
    static final String USER_ID_FIELD = "userId";

    static final String DELETE_SQL = "DELETE FROM StudyActivityEvents " +
            "WHERE userId = :userId AND studyId = :studyId AND eventId = :eventId";
    
    // Is there a better way to get this than two subselects? Apparently not in MySQL
    static final String GET_RECENT_SQL = "SELECT *, (SELECT count(*) as total FROM " +
            "StudyActivityEvents WHERE eventId = sae.eventId AND studyId = :studyId " +
            "AND userId = :userId GROUP BY eventId) FROM StudyActivityEvents AS sae " +
            "WHERE userId = :userId AND studyId = :studyId AND eventTimestamp = (SELECT " +
            "eventTimestamp FROM StudyActivityEvents WHERE userId = :userId AND studyId = " +
            ":studyId AND eventId = sae.eventId ORDER BY createdOn DESC LIMIT 1) " +
            "ORDER BY eventId";

    static final String HISTORY_SQL = "FROM StudyActivityEvents WHERE " +
            "userId = :userId AND studyId = :studyId AND eventId = :eventId " +
            "ORDER BY createdOn DESC";
            
    private HibernateHelper helper;
    
    @Resource(name = "mysqlHibernateHelper")
    final void setHibernateHelper(HibernateHelper helper) {
        this.helper = helper;
    }

    @Override
    public void deleteEvent(StudyActivityEvent event) {
        checkNotNull(event);
        
        QueryBuilder query = new QueryBuilder();
        query.append(DELETE_SQL, USER_ID_FIELD, event.getUserId(), 
                STUDY_ID_FIELD, event.getStudyId(),
                EVENT_ID_FIELD, event.getEventId());
        helper.nativeQueryUpdate(query.getQuery(), query.getParameters());
    }

    @Override
    public void publishEvent(StudyActivityEvent event) {
        checkNotNull(event);
        
        helper.saveOrUpdate(event);
    }

    @Override
    public List<StudyActivityEvent> getRecentStudyActivityEvents(String userId, String studyId) {
        checkNotNull(userId);
        checkNotNull(studyId);
        
        QueryBuilder builder = new QueryBuilder();
        builder.append(GET_RECENT_SQL, USER_ID_FIELD, userId, STUDY_ID_FIELD, studyId);
        
        List<Object[]> results = helper.nativeQuery(builder.getQuery(), builder.getParameters());
        return results.stream().map(StudyActivityEvent::create).collect(toList());
    }
    
    @Override
    public StudyActivityEvent getRecentStudyActivityEvent(String userId, String studyId, String eventId) {
        checkNotNull(userId);
        checkNotNull(studyId);
        checkNotNull(eventId);
        
        Map<String, StudyActivityEvent> map = getRecentStudyActivityEvents(userId, studyId)
                .stream().collect(toMap(StudyActivityEvent::getEventId, e -> e));
        return map.get(eventId);
    }

    @Override
    public PagedResourceList<StudyActivityEvent> getStudyActivityEventHistory(String userId, 
            String studyId, String eventId, Integer offsetBy, Integer pageSize) {
        checkNotNull(userId);
        checkNotNull(studyId);
        checkNotNull(eventId);
        
        QueryBuilder builder = new QueryBuilder();
        builder.append(HISTORY_SQL, USER_ID_FIELD, userId, STUDY_ID_FIELD, studyId, EVENT_ID_FIELD, eventId);

        List<StudyActivityEvent> records = helper.nativeQueryGet("SELECT * " + builder.getQuery(), 
                builder.getParameters(), offsetBy, pageSize, StudyActivityEvent.class);
        
        int count = helper.nativeQueryCount("SELECT count(*) " + builder.getQuery(), builder.getParameters());
        
        return new PagedResourceList<>(records, count, true)
                .withRequestParam(OFFSET_BY, offsetBy)
                .withRequestParam(PAGE_SIZE, pageSize);
    }

}
