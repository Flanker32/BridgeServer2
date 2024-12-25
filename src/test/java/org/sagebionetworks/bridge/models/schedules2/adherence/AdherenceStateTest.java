package org.sagebionetworks.bridge.models.schedules2.adherence;

import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.TEST_CLIENT_TIME_ZONE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.fail;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.mockito.Mockito;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStream;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamDay;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamWindow;
import org.sagebionetworks.bridge.models.schedules2.timelines.TimelineMetadata;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class AdherenceStateTest extends Mockito {
    
    private static final DateTimeZone TEST_TIME_ZONE = DateTimeZone.forID(TEST_CLIENT_TIME_ZONE);
    private static final DateTime NOW = CREATED_ON.plusDays(10).withZone(DateTimeZone.forID("America/Chicago"));
    private static final DateTime EVENT_TS1 = CREATED_ON;
    private static final DateTime EVENT_TS2 = CREATED_ON.plusDays(5);

    TimelineMetadata meta1;
    TimelineMetadata meta2;
    List<TimelineMetadata> metadata;
    
    AdherenceRecord rec1;
    AdherenceRecord rec2;
    
    AdherenceState state;

    @BeforeMethod
    public void beforeMethod() { 
        meta1 = new TimelineMetadata();
        meta1.setSessionStartEventId("event1");
        meta1.setSessionInstanceGuid("instanceGuid1");
        meta1.setSessionGuid("guid1");
        meta1.setSessionInstanceStartDay(1);
        meta1.setSessionName("session1");
        meta1.setSessionSymbol("1");
        
        meta2 = new TimelineMetadata();
        meta2.setSessionStartEventId("event2");
        meta2.setSessionInstanceGuid("instanceGuid2");
        meta2.setSessionGuid("guid2");
        meta2.setSessionInstanceStartDay(2);
        meta2.setSessionName("session2");
        meta2.setSessionSymbol("2");
        meta2.setStudyBurstId("burst2");
        meta2.setStudyBurstNum(2);
        metadata = ImmutableList.of(meta1, meta2);
        
        StudyActivityEvent e1 = new StudyActivityEvent.Builder()
                .withEventId("event1")
                .withTimestamp(EVENT_TS1)
                .build();
        
        StudyActivityEvent e2 = new StudyActivityEvent.Builder()
                .withEventId("event2")
                .withTimestamp(EVENT_TS2)
                .build();
        
        List<StudyActivityEvent> events = ImmutableList.of(e1, e2);
        
        rec1 = new AdherenceRecord();
        rec1.setInstanceGuid("ar1");
        rec1.setEventTimestamp(EVENT_TS1);
        
        rec2 = new AdherenceRecord();
        rec2.setInstanceGuid("ar2");
        rec2.setEventTimestamp(EVENT_TS2);
        
        List<AdherenceRecord> adherenceRecords = ImmutableList.of(rec1, rec2);
        
        state = new AdherenceState.Builder()
            .withMetadata(metadata)
            .withEvents(events)
            .withAdherenceRecords(adherenceRecords)
            .withNow(NOW)
            .withClientTimeZone(TEST_CLIENT_TIME_ZONE)
            .withStudyStartEventId("event1")
            .build();
    }
    
    @Test
    public void testConstruction_nulls() {
        AdherenceState emptyState = new AdherenceState.Builder().withNow(NOW).build();
        assertEquals(emptyState.getMetadata(), ImmutableList.of());
        assertNull(emptyState.getAdherenceRecordByGuid("event1"));
        assertNull(emptyState.getDaysSinceEventById("event1"));    
        assertNull(emptyState.getEventTimestampById("event1"));    
        assertNotNull(emptyState.getEventStreamById("event1"));
        assertNotNull(emptyState.getEventStreamDayByKey(meta1));    
    }
    
    @Test
    public void testConstruction() {
        assertSame(state.getMetadata(), metadata);
        assertEquals(state.getNow(), NOW.withZone(TEST_TIME_ZONE));
        assertEquals(state.getClientTimeZone(), TEST_CLIENT_TIME_ZONE);
        assertEquals(state.getTimeZone(), TEST_TIME_ZONE);
        
        EventStream stream1 = state.getEventStreamById("event1");
        assertEquals(stream1.getStartEventId(), "event1");
        assertEquals(stream1.getEventTimestamp(), EVENT_TS1.withZone(TEST_TIME_ZONE));
        // We're not creating a new one each time
        assertSame(state.getEventStreamById("event1"), state.getEventStreamById("event1"));
        
        EventStream stream2 = state.getEventStreamById("event2");
        assertEquals(stream2.getStartEventId(), "event2");
        assertEquals(stream2.getEventTimestamp(), EVENT_TS2.withZone(TEST_TIME_ZONE));
        
        assertSame(state.getMetadata().getFirst(), meta1);
        assertSame(state.getMetadata().get(1), meta2);
        
        EventStreamDay day1 = state.getEventStreamDayByKey(meta1);
        // We're not creating a new one each time
        assertSame(state.getEventStreamDayByKey(meta1), state.getEventStreamDayByKey(meta1));
        assertEquals(day1.getSessionGuid(), "guid1");
        assertEquals(day1.getSessionName(), "session1");
        assertEquals(day1.getSessionSymbol(), "1");
        assertEquals(day1.getStartEventId(), "event1");
        assertNull(day1.getStudyBurstId());
        assertNull(day1.getStudyBurstNum());
        
        EventStreamDay day2 = state.getEventStreamDayByKey(meta2);
        assertEquals(day2.getSessionGuid(), "guid2");
        assertEquals(day2.getSessionName(), "session2");
        assertEquals(day2.getSessionSymbol(), "2");
        assertEquals(day2.getStartEventId(), "event2");
        assertEquals(day2.getStudyBurstId(), "burst2");
        assertEquals(day2.getStudyBurstNum(), Integer.valueOf(2));
        
        assertSame(state.getAdherenceRecordByGuid("ar1"), rec1);
        assertSame(state.getAdherenceRecordByGuid("ar2"), rec2);

        assertEquals(state.getDaysSinceEventById("event1"), Integer.valueOf(10));
        assertEquals(state.getDaysSinceEventById("event2"), Integer.valueOf(5));

        assertEquals(state.getEventTimestampById("event1"), EVENT_TS1.withZone(TEST_TIME_ZONE));
        assertEquals(state.getEventTimestampById("event2"), EVENT_TS2.withZone(TEST_TIME_ZONE));
        
        // Nothing to do == in compliance, ignore this person this week
        assertEquals(state.calculateAdherencePercentage(), 100);
        assertEquals(state.getStudyStartEventId(), "event1");
        assertEquals(state.getStreamEventIds(), ImmutableList.of("event1", "event2"));
    }
    
    @Test
    public void getSessionStateCount() {
        // We do have to manually set the states for some made-up windows in order
        // to verify this getter works...
        EventStreamDay day1 = state.getEventStreamDayByKey(meta1);
        EventStreamWindow win1a = new EventStreamWindow();
        win1a.setSessionInstanceGuid(meta1.getSessionInstanceGuid());
        win1a.setTimeWindowGuid("win1a");
        win1a.setState(SessionCompletionState.ABANDONED);
        day1.addTimeWindow(win1a);
        
        EventStreamWindow win1b = new EventStreamWindow();
        win1b.setSessionInstanceGuid(meta1.getSessionInstanceGuid());
        win1b.setTimeWindowGuid("win1b");
        win1b.setState(SessionCompletionState.EXPIRED);
        day1.addTimeWindow(win1b);
        
        EventStreamDay day2 = state.getEventStreamDayByKey(meta2);
        EventStreamWindow win2a = new EventStreamWindow();
        win2a.setSessionInstanceGuid(meta2.getSessionInstanceGuid());
        win2a.setTimeWindowGuid("win2a");
        win2a.setState(SessionCompletionState.UNSTARTED);
        day2.addTimeWindow(win2a);
        
        EventStreamWindow win2b = new EventStreamWindow();
        win2b.setSessionInstanceGuid(meta2.getSessionInstanceGuid());
        win2b.setTimeWindowGuid("win2b");
        win2b.setState(SessionCompletionState.COMPLETED);
        day2.addTimeWindow(win2b);

        assertEquals(state.calculateAdherencePercentage(), 25);
    }
    
    @Test
    public void timeZone_fromNow() {
        state = new AdherenceState.Builder()
                .withMetadata(metadata)
                .withNow(NOW)
                // no time zone
                .build();
        // a time zone from the "now" value would be an offset, but same difference
        assertEquals(state.getTimeZone(), DateTimeZone.forID("America/Chicago"));    
    }
    
    @Test
    public void timeZone_fromClientTimeZone() {
        assertEquals(state.getTimeZone(), TEST_TIME_ZONE);    
    }
    
    @Test
    public void toBuilder () {
        AdherenceState copy = state.toBuilder().build();
        
        assertSame(copy.getMetadata(), state.getMetadata());
        assertSame(copy.getNow(), state.getNow());
        assertSame(copy.getClientTimeZone(), state.getClientTimeZone());
        assertSame(copy.getEvents(), state.getEvents());
        assertSame(copy.getAdherenceRecords(), state.getAdherenceRecords());
        assertSame(copy.getStudyStartEventId(), "event1");
    }
    
    // BRIDGE-3331 testing bug where persistent windows led to a duplicate key error
    // when collecting adherence records with duplicate instance GUIDs into guid into a map.
    // Persistent time windows are ignored in adherence, so we can safely bypass the exception.
    @Test
    public void persistentWindowDuplicateKey() {
        StudyActivityEvent e1 = new StudyActivityEvent.Builder()
                .withEventId("event1")
                .withTimestamp(EVENT_TS1)
                .build();

        StudyActivityEvent e2 = new StudyActivityEvent.Builder()
                .withEventId("event2")
                .withTimestamp(EVENT_TS2)
                .build();
    
        List<StudyActivityEvent> events = ImmutableList.of(e1, e2);
    
        AdherenceRecord record1 = new AdherenceRecord();
        record1.setInstanceGuid("ar1");
        record1.setEventTimestamp(EVENT_TS1);
    
        AdherenceRecord record2 = new AdherenceRecord();
        record2.setInstanceGuid("ar1");
        record2.setEventTimestamp(EVENT_TS2);
    
        List<AdherenceRecord> adherenceRecords = ImmutableList.of(record1, record2);
    
        // If there bug is present, there will be an exception thrown during the state build.
        try {
            AdherenceState adherenceState = new AdherenceState.Builder()
                    .withMetadata(metadata)
                    .withEvents(events)
                    .withAdherenceRecords(adherenceRecords)
                    .withNow(NOW)
                    .withClientTimeZone(TEST_CLIENT_TIME_ZONE)
                    .withStudyStartEventId("event1")
                    .build();
        } catch (IllegalStateException e) {
            fail("AdherenceState failed to build due to duplicate adherence record instanceGuids.");
        }
    }
}
