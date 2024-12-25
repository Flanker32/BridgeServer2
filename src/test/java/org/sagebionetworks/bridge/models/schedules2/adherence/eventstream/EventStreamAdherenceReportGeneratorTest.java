package org.sagebionetworks.bridge.models.schedules2.adherence.eventstream;

import static java.util.stream.Collectors.toList;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.SCHEDULE_GUID;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.models.schedules2.PerformanceOrder.SEQUENTIAL;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.ABANDONED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.COMPLETED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.DECLINED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.EXPIRED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.NOT_APPLICABLE;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.NOT_YET_AVAILABLE;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.STARTED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.UNSTARTED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamAdherenceReportGenerator.INSTANCE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.joda.time.Period;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.Session;
import org.sagebionetworks.bridge.models.schedules2.TimeWindow;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceState;
import org.sagebionetworks.bridge.models.schedules2.adherence.ParticipantStudyProgress;
import org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState;
import org.sagebionetworks.bridge.models.schedules2.timelines.TimelineMetadata;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class EventStreamAdherenceReportGeneratorTest {
    
    private static final DateTime NOW = DateTime.parse("2021-10-15T08:13:47.345-07:00");
    private static final DateTime STARTED_ON = CREATED_ON;
    private static final DateTime FINISHED_ON = MODIFIED_ON;
    private static final TimelineMetadata META1 = createMeta("sessionInstanceGuid", "sessionInstanceGuid",
            "sessionGuid", "timeWindowGuid", 13, 15, "sessionStartEventId", "studyBurstId", 1, "sessionSymbol",
            "sessionName", false);
    private static final TimelineMetadata META_PERSISTENT = createMeta("JOQg4yz0lrif7V3HYYzACw",
            "JOQg4yz0lrif7V3HYYzACw", "Bw7z_QMiGeuQDVSk_Ndo-Gp-", "yxSek2gkA5tHFQRTXafsqrzX", 0, 0, "created_on", null,
            null, null, "Session #3 - window is persistent", true);
    private static final TimelineMetadata META2_A = createMeta("fTnAnasmcs-UndPt60iX8w", "fTnAnasmcs-UndPt60iX8w",
            "u90_okqrmPgKptcc9E8lORwC", "ksuWqp17x3i9zjQBh0FHSDS2", 4, 4, "study_burst:Main Sequence:01",
            "Main Sequence", 1, "*", "Session #1", false);
    private static final TimelineMetadata META2_B = createMeta("gnescr0HRz5T2JEjc0Ad6Q", "gnescr0HRz5T2JEjc0Ad6Q",
            "u90_okqrmPgKptcc9E8lORwC", "ksuWqp17x3i9zjQBh0FHSDS2", 2, 2, "study_burst:Main Sequence:01",
            "Main Sequence", 1, "*", "Session #1", false);
    private static final Schedule2 SCHEDULE = createSchedule();
    
    @Test
    public void calculatesReport() throws Exception {
        AdherenceRecord adherenceRecord = createRecord(STARTED_ON, FINISHED_ON, "sessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));

        AdherenceState state = createState(NOW, META1, event, adherenceRecord);
        EventStreamAdherenceReport report = INSTANCE.generate(state, SCHEDULE);

        assertEquals(report.getTimestamp(), NOW.withZone(DateTimeZone.forID("America/Chicago")));
        assertEquals(report.getAdherencePercent(), 100);
        assertEquals(report.getStreams().size(), 1);
        assertEquals(report.getDayRangeOfAllStreams().getMin(), 13);
        assertEquals(report.getDayRangeOfAllStreams().getMax(), 15);
        assertEquals(report.getDateRangeOfAllStreams().getStartDate().toString(), "2021-10-14");
        assertEquals(report.getDateRangeOfAllStreams().getEndDate().toString(), "2021-10-16");
        assertEquals(report.getClientTimeZone(), "America/Chicago");
        assertEquals(report.getEarliestEventId(), "sessionStartEventId");
    }
    
    @Test
    public void generate_metadataCopiedToReport() throws Exception {
        // We will focus on calculation of the completion states in the report, but here let's 
        // verify the metadata is being copied over into the report.
        AdherenceState state = createState(NOW, META1, null, null);

        EventStreamAdherenceReport report = INSTANCE.generate(state, SCHEDULE);
        
        assertEquals(report.getTimestamp(), NOW.withZone(DateTimeZone.forID("America/Chicago")));
        assertEquals(report.getAdherencePercent(), 100);
        assertEquals(report.getStreams().size(), 1);
        
        EventStream stream = report.getStreams().getFirst();
        assertEquals(stream.getStartEventId(), "sessionStartEventId");
        assertEquals(stream.getStudyBurstId(), "studyBurstId");
        assertEquals(stream.getStudyBurstNum(), Integer.valueOf(1));
        assertEquals(stream.getByDayEntries().size(), 1);
        assertEquals(stream.getByDayEntries().get(Integer.valueOf(13)).size(), 1); 
        
        EventStreamDay day = stream.getByDayEntries().get(Integer.valueOf(13)).getFirst();
        assertEquals(day.getSessionGuid(), "sessionGuid");
        assertEquals(day.getSessionName(), "sessionName");
        assertEquals(day.getStartDay(), (Integer)13);
        
        EventStreamWindow window = day.getTimeWindows().getFirst();
        assertEquals(window.getSessionInstanceGuid(), "sessionInstanceGuid");
        assertEquals(window.getTimeWindowGuid(), "timeWindowGuid");
        assertEquals(window.getEndDay(), (Integer)15);
        // Start and end dates are not set without the starting event
        assertNull(window.getStartDate());
        assertNull(window.getEndDate());
        assertEquals(window.getStartTime(), LocalTime.parse("01:00"));
        assertEquals(window.getEndTime(), LocalTime.parse("02:00"));
        assertEquals(window.getState(), NOT_APPLICABLE);
    }
    
    @Test
    public void constructorUsesParticipantTimeZone() { 
        StudyActivityEvent event = new StudyActivityEvent.Builder()
                .withEventId("enrollment")
                .withTimestamp(MODIFIED_ON).build();
        
        AdherenceState.Builder builder = new AdherenceState.Builder();
        builder.withClientTimeZone("America/Denver");
        builder.withMetadata(ImmutableList.of());
        builder.withEvents(ImmutableList.of(event));
        builder.withAdherenceRecords(ImmutableList.of());
        builder.withNow(NOW);
        
        AdherenceState state = builder.build();
        assertEquals(state.getTimeZone().toString(), "America/Denver");
    }
    
    @Test
    public void constructorUsesNowTimestampTimeZone() { 
        StudyActivityEvent event = new StudyActivityEvent.Builder()
                .withEventId("enrollment")
                .withTimestamp(MODIFIED_ON).build();
        
        AdherenceState.Builder builder = new AdherenceState.Builder();
        builder.withMetadata(ImmutableList.of());
        builder.withEvents(ImmutableList.of(event));
        builder.withAdherenceRecords(ImmutableList.of());
        builder.withNow(NOW);
        
        AdherenceState state = builder.build();
        assertEquals(state.getTimeZone().toString(), "-07:00");
    }
    
    @Test
    public void stateCalculation_notApplicable() throws Exception {
        // We will focus on calculation of the completion states in the report, but here let's 
        // verify the metadata is being copied over into the report.
        AdherenceState state = createState(NOW, META1, null, null);

        EventStreamAdherenceReport report = INSTANCE.generate(state, SCHEDULE);
        assertEquals(getReportStates(report), ImmutableList.of(NOT_APPLICABLE));
    }
    
    @Test
    public void stateCalculation_notApplicableWithAdherence() throws Exception {
        // This is a pathological case...the client shouldn't have generated an adherence record
        // for an assessment that the participant shouldn't do. 
        AdherenceRecord adherenceRecord = createRecord(STARTED_ON, FINISHED_ON, "sessionInstanceGuid", false);
        
        AdherenceState state = createState(NOW, META1, null, adherenceRecord);

        EventStreamAdherenceReport report = INSTANCE.generate(state, SCHEDULE);
        assertEquals(getReportStates(report), ImmutableList.of(NOT_APPLICABLE));
    }
    
    @Test
    public void stateCalculation_notYetAvailable() throws Exception {
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));

        AdherenceState state = createState(NOW.minusDays(10), META1, event, null);

        EventStreamAdherenceReport report = INSTANCE.generate(state, SCHEDULE);
        assertEquals(getReportStates(report), ImmutableList.of(NOT_YET_AVAILABLE));
    }
    
    @Test
    public void stateCalculation_notYetAvailableWithAdherenceRecord() throws Exception {
        AdherenceRecord adherenceRecord = createRecord(null, null, "sessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));

        AdherenceState state = createState(NOW.minusDays(10), META1, event, adherenceRecord);

        EventStreamAdherenceReport report = INSTANCE.generate(state, SCHEDULE);
        assertEquals(getReportStates(report), ImmutableList.of(NOT_YET_AVAILABLE));
    }
    
    @Test
    public void stateCalculation_unstarted() throws Exception {
        AdherenceRecord adherenceRecord = createRecord(null, null, "sessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));

        AdherenceState state = createState(NOW, META1, event, adherenceRecord);

        EventStreamAdherenceReport report = INSTANCE.generate(state, SCHEDULE);
        assertEquals(getReportStates(report), ImmutableList.of(UNSTARTED));
    }
    
    @Test
    public void stateCalculation_started() throws Exception {
        AdherenceRecord adherenceRecord = createRecord(STARTED_ON, null, "sessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));

        AdherenceState state = createState(NOW, META1, event, adherenceRecord);

        EventStreamAdherenceReport report = INSTANCE.generate(state, SCHEDULE);
        assertEquals(getReportStates(report), ImmutableList.of(STARTED));
    }
    
    @Test
    public void stateCalculation_completed() throws Exception {
        AdherenceRecord adherenceRecord = createRecord(STARTED_ON, FINISHED_ON, "sessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));
        
        AdherenceState state = createState(NOW, META1, event, adherenceRecord);

        EventStreamAdherenceReport report = INSTANCE.generate(state, SCHEDULE);
        assertEquals(getReportStates(report), ImmutableList.of(COMPLETED));
    }
    
    @Test
    public void stateCalculation_abandoned() throws Exception {
        AdherenceRecord adherenceRecord = createRecord(STARTED_ON, null, "sessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));

        AdherenceState state = createState(NOW.plusDays(2), META1, event, adherenceRecord);

        EventStreamAdherenceReport report = INSTANCE.generate(state, SCHEDULE);
        assertEquals(getReportStates(report), ImmutableList.of(ABANDONED));
    }
    
    @Test
    public void stateCalculation_expired() throws Exception {
        AdherenceRecord adherenceRecord = createRecord(null, null, "sessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));

        AdherenceState state = createState(NOW.plusDays(2), META1, event, adherenceRecord);

        EventStreamAdherenceReport report = INSTANCE.generate(state, SCHEDULE);
        assertEquals(getReportStates(report), ImmutableList.of(EXPIRED));
    }

    @Test
    public void stateCalculation_expiredNoRecord() throws Exception {
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));

        AdherenceState state = createState(NOW.plusDays(2), META1, event, null);

        EventStreamAdherenceReport report = INSTANCE.generate(state, SCHEDULE);
        assertEquals(getReportStates(report), ImmutableList.of(EXPIRED));
    }
    
    @Test
    public void stateCalculation_declined() throws Exception {
        AdherenceRecord adherenceRecord = createRecord(null, null, "sessionInstanceGuid", true);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));

        AdherenceState state = createState(NOW.plusDays(2), META1, event, adherenceRecord);

        EventStreamAdherenceReport report = INSTANCE.generate(state, SCHEDULE);
        assertEquals(getReportStates(report), ImmutableList.of(DECLINED));
    }
    
    @Test
    public void stateCalculation_ignoresIrrelevantAdherenceRecord() throws Exception {
        AdherenceRecord adherenceRecord = createRecord(STARTED_ON, FINISHED_ON, "otherSessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));

        AdherenceState state = createState(NOW, META1, event, adherenceRecord);

        EventStreamAdherenceReport report = INSTANCE.generate(state, SCHEDULE);
        assertEquals(getReportStates(report), ImmutableList.of(UNSTARTED));
    }
    
    @Test
    public void stateCalculation_ignoresIrrelevantEvents() throws Exception {
        AdherenceRecord adherenceRecord = createRecord(STARTED_ON, FINISHED_ON, "sessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("otherStartEventId", NOW.minusDays(14));

        AdherenceState state = createState(NOW, META1, event, adherenceRecord);

        EventStreamAdherenceReport report = INSTANCE.generate(state, SCHEDULE);
        assertEquals(getReportStates(report), ImmutableList.of(NOT_APPLICABLE));
    }
    
    @Test
    public void stateCalculation_pastChangesState() throws Exception {
        AdherenceRecord adherenceRecord = createRecord(null, null, "sessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW);

        AdherenceState state = createState(NOW.minusDays(21), META1, event, adherenceRecord);

        EventStreamAdherenceReport report = INSTANCE.generate(state, SCHEDULE);
        assertEquals(getReportStates(report), ImmutableList.of(NOT_YET_AVAILABLE));
    }

    @Test
    public void stateCalculation_futureChangesState() throws Exception {
        AdherenceRecord adherenceRecord = createRecord(null, null, "sessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW);

        AdherenceState state = createState(NOW.plusDays(28), META1, event, adherenceRecord);

        EventStreamAdherenceReport report = INSTANCE.generate(state, SCHEDULE);
        assertEquals(getReportStates(report), ImmutableList.of(EXPIRED));
    }
    
    @Test
    public void persistentSessionWindowsAreIgnored() {
        AdherenceRecord adherenceRecord = createRecord(STARTED_ON, FINISHED_ON, "JOQg4yz0lrif7V3HYYzACw", false);
        StudyActivityEvent event = createEvent("created_on", NOW);

        AdherenceState state = createState(NOW, META_PERSISTENT, event, adherenceRecord);
        assertTrue(INSTANCE.generate(state, SCHEDULE).getStreams().isEmpty());
    }
    
    @Test
    public void groupsUnderEventId() throws Exception {
        AdherenceRecord adherenceRecord = createRecord(STARTED_ON, FINISHED_ON, "gnescr0HRz5T2JEjc0Ad6Q", false);        
        StudyActivityEvent event = createEvent("study_burst:Main Sequence:01", NOW.minusDays(3));

        AdherenceState state = createState(NOW, META2_A, META2_B, event, adherenceRecord, false);

        EventStreamAdherenceReport report = INSTANCE.generate(state, SCHEDULE);
        assertNotNull(report.getStreams().getFirst().getByDayEntries().get(2));
        assertNotNull(report.getStreams().getFirst().getByDayEntries().get(4));
        assertEquals(getReportStates(report), ImmutableList.of(COMPLETED, NOT_YET_AVAILABLE));
    }
    
    @Test
    public void progression_noSchedule() { 
        AdherenceState state = new AdherenceState.Builder().withNow(NOW).build();
        
        EventStreamAdherenceReport report = INSTANCE.generate(state, SCHEDULE);
        assertEquals(report.getAdherencePercent(), 100);
        assertEquals(report.getProgression(), ParticipantStudyProgress.UNSTARTED);
        assertTrue(report.getStreams().isEmpty());
    }
    
    @Test
    public void progression_done() { 
        AdherenceRecord adherenceRecord = createRecord(STARTED_ON, FINISHED_ON, "sessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));
        AdherenceState state = createState(NOW, META1, event, adherenceRecord, true);
        
        EventStreamAdherenceReport report = INSTANCE.generate(state, SCHEDULE);
        assertEquals(report.getProgression(), ParticipantStudyProgress.DONE);        
    }
    
    @Test
    public void progression_unstarted() {
        AdherenceRecord adherenceRecord = createRecord(null, null, "sessionInstanceGuid", false);
        AdherenceState state = createState(NOW, META1, null, adherenceRecord, true);
        
        EventStreamAdherenceReport report = INSTANCE.generate(state, SCHEDULE);
        assertEquals(report.getProgression(), ParticipantStudyProgress.UNSTARTED);
    }
    
    @Test
    public void progression_inProgress() {
        AdherenceRecord adherenceRecord = createRecord(null, null, "sessionInstanceGuid", false);
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));
        AdherenceState state = createState(NOW, META1, event, adherenceRecord, true);
        
        EventStreamAdherenceReport report = INSTANCE.generate(state, SCHEDULE);
        assertEquals(report.getProgression(), ParticipantStudyProgress.IN_PROGRESS);
    }
    
    @Test
    public void populatesWindowTimes() throws Exception {
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));
        
        AdherenceState state = createState(NOW, META1, event, null);
        EventStreamAdherenceReport report = INSTANCE.generate(state, SCHEDULE);
        
        assertEquals(report.getTimestamp(), NOW.withZone(DateTimeZone.forID("America/Chicago")));
        assertEquals(report.getAdherencePercent(), 0);
        assertEquals(report.getStreams().size(), 1);
    
        EventStream stream = report.getStreams().getFirst();
        assertEquals(stream.getStartEventId(), "sessionStartEventId");
        assertEquals(stream.getByDayEntries().size(), 1);
        assertEquals(stream.getByDayEntries().get(13).size(), 1);
    
        EventStreamDay day = stream.getByDayEntries().get(13).getFirst();
        assertEquals(day.getStartDay(), (Integer)13);
    
        EventStreamWindow window = day.getTimeWindows().getFirst();
        assertEquals(window.getSessionInstanceGuid(), "sessionInstanceGuid");
        assertEquals(window.getTimeWindowGuid(), "timeWindowGuid");
        assertEquals(window.getEndDay(), (Integer)15);
        assertEquals(window.getStartDate(), LocalDate.parse("2021-10-14"));
        assertEquals(window.getEndDate(), LocalDate.parse("2021-10-16"));
        assertEquals(window.getStartTime(), LocalTime.parse("01:00"));
        assertEquals(window.getEndTime(), LocalTime.parse("02:00"));
        assertEquals(window.getState(), UNSTARTED);
    }
    
    @Test
    public void windowTimesHandleVaryingDurations_sameDayLaterEndTime() throws Exception {
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));
        TimelineMetadata metadata = createMeta("sessionInstanceGuid", "sessionInstanceGuid",
                "sessionGuid", "timeWindowGuid", 14, 14, "sessionStartEventId", "studyBurstId", 1, "sessionSymbol",
                "sessionName", false);
        Schedule2 schedule = createSchedule("PT12H");
    
        AdherenceState state = createState(NOW, metadata, event, null);
        EventStreamAdherenceReport report = INSTANCE.generate(state, schedule);
    
        assertEquals(report.getTimestamp(), NOW.withZone(DateTimeZone.forID("America/Chicago")));
        assertEquals(report.getAdherencePercent(), 0);
        assertEquals(report.getStreams().size(), 1);
    
        EventStream stream = report.getStreams().getFirst();
        assertEquals(stream.getStartEventId(), "sessionStartEventId");
        assertEquals(stream.getByDayEntries().size(), 1);
        assertEquals(stream.getByDayEntries().get(14).size(), 1);
    
        EventStreamDay day = stream.getByDayEntries().get(14).getFirst();
        assertEquals(day.getStartDay(), (Integer)14);
    
        EventStreamWindow window = day.getTimeWindows().getFirst();
        assertEquals(window.getSessionInstanceGuid(), "sessionInstanceGuid");
        assertEquals(window.getTimeWindowGuid(), "timeWindowGuid");
        assertEquals(window.getEndDay(), (Integer)14);
        assertEquals(window.getStartDate(), LocalDate.parse("2021-10-15"));
        assertEquals(window.getEndDate(), LocalDate.parse("2021-10-15"));
        assertEquals(window.getStartTime(), LocalTime.parse("01:00"));
        assertEquals(window.getEndTime(), LocalTime.parse("13:00"));
        assertEquals(window.getState(), UNSTARTED);
    }
    
    @Test
    public void windowTimesHandleVaryingDurations_nextDayEarlierEndTime() throws Exception {
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));
        TimelineMetadata metadata = createMeta("sessionInstanceGuid", "sessionInstanceGuid",
                "sessionGuid", "timeWindowGuid", 13, 14, "sessionStartEventId", "studyBurstId", 1, "sessionSymbol",
                "sessionName", false);
        Schedule2 schedule = createSchedule("PT23H");
        
        AdherenceState state = createState(NOW, metadata, event, null);
        EventStreamAdherenceReport report = INSTANCE.generate(state, schedule);
        
        assertEquals(report.getTimestamp(), NOW.withZone(DateTimeZone.forID("America/Chicago")));
        assertEquals(report.getAdherencePercent(), 0);
        assertEquals(report.getStreams().size(), 1);
        
        EventStream stream = report.getStreams().getFirst();
        assertEquals(stream.getStartEventId(), "sessionStartEventId");
        assertEquals(stream.getByDayEntries().size(), 1);
        assertEquals(stream.getByDayEntries().get(13).size(), 1);
        
        EventStreamDay day = stream.getByDayEntries().get(13).getFirst();
        assertEquals(day.getStartDay(), (Integer)13);
        
        EventStreamWindow window = day.getTimeWindows().getFirst();
        assertEquals(window.getSessionInstanceGuid(), "sessionInstanceGuid");
        assertEquals(window.getTimeWindowGuid(), "timeWindowGuid");
        assertEquals(window.getEndDay(), (Integer)14);
        assertEquals(window.getStartDate(), LocalDate.parse("2021-10-14"));
        assertEquals(window.getEndDate(), LocalDate.parse("2021-10-15"));
        assertEquals(window.getStartTime(), LocalTime.parse("01:00"));
        assertEquals(window.getEndTime(), LocalTime.parse("00:00"));
        assertEquals(window.getState(), UNSTARTED);
    }
    
    @Test
    public void endTimeNullWithoutDuration() throws Exception {
        StudyActivityEvent event = createEvent("sessionStartEventId", NOW.minusDays(14));
        
        Schedule2 schedule = createSchedule();
        schedule.getSessions().getFirst().getTimeWindows().getFirst().setExpiration(null);
        
        AdherenceState state = createState(NOW, META1, event, null);
        EventStreamAdherenceReport report = INSTANCE.generate(state, schedule);
        
        EventStream stream = report.getStreams().getFirst();
        EventStreamDay day = stream.getByDayEntries().get(Integer.valueOf(13)).getFirst();
        
        EventStreamWindow window = day.getTimeWindows().getFirst();
        assertEquals(window.getSessionInstanceGuid(), "sessionInstanceGuid");
        assertEquals(window.getTimeWindowGuid(), "timeWindowGuid");
        assertEquals(window.getEndDay(), (Integer)15);
        assertEquals(window.getStartDate(), LocalDate.parse("2021-10-14"));
        assertEquals(window.getEndDate(), LocalDate.parse("2021-10-16"));
        assertEquals(window.getStartTime(), LocalTime.parse("01:00"));
        assertNull(window.getEndTime());
        assertEquals(window.getState(), UNSTARTED);
    }
    
    private AdherenceState createState(DateTime now, TimelineMetadata meta,
            StudyActivityEvent event, AdherenceRecord adherenceRecord) {
        return createState(now, meta, event, adherenceRecord, false);
    }
    
    private AdherenceState createState(DateTime now, TimelineMetadata meta,
            StudyActivityEvent event, AdherenceRecord adherenceRecord, boolean showActive) {
        return createState(now, meta, null, event, adherenceRecord, showActive);
    }
    
    private AdherenceState createState(DateTime now, TimelineMetadata meta1,
            TimelineMetadata meta2, StudyActivityEvent event, AdherenceRecord adherenceRecord, boolean showActive) {
        AdherenceState.Builder builder = new AdherenceState.Builder();
        List<TimelineMetadata> metas = new ArrayList<>();
        if (meta1 != null) {
            metas.add(meta1);       
        }
        if (meta2 != null) {
            metas.add(meta2);       
        }
        builder.withMetadata(metas);
        if (event != null) {
            builder.withEvents(ImmutableList.of(event));
        }
        if (adherenceRecord != null) {
            builder.withAdherenceRecords(ImmutableList.of(adherenceRecord));
        }
        builder.withNow(now);
        builder.withClientTimeZone("America/Chicago");
        return builder.build();
    }
    
    private static TimelineMetadata createMeta(String guid, String sessionInstanceGuid, String sessionGuid,
            String timeWindowGuid, Integer sessionInstanceStartDay, Integer sessionInstanceEndDay,
            String sessionStartEventId, String studyBurstId, Integer studyBurstNum, String sessionSymbol,
            String sessionName, boolean timeWindowPersistent) {
        TimelineMetadata meta = new TimelineMetadata();
        meta.setGuid(guid);
        meta.setSessionInstanceGuid(sessionInstanceGuid);
        meta.setSessionGuid(sessionGuid);
        meta.setTimeWindowGuid(timeWindowGuid);
        meta.setSessionInstanceStartDay(sessionInstanceStartDay);
        meta.setSessionInstanceEndDay(sessionInstanceEndDay);
        meta.setSessionStartEventId(sessionStartEventId);
        meta.setStudyBurstId(studyBurstId);
        meta.setStudyBurstNum(studyBurstNum);
        meta.setSessionSymbol(sessionSymbol);
        meta.setSessionName(sessionName);
        meta.setTimeWindowPersistent(timeWindowPersistent);
        return meta;
    }

    private StudyActivityEvent createEvent(String eventId, DateTime timestamp) {
        return new StudyActivityEvent.Builder().withEventId(eventId).withTimestamp(timestamp).build();
    }
    
    private AdherenceRecord createRecord(DateTime startedOn, DateTime finishedOn, String instanceGuid, boolean declined) {
        AdherenceRecord rec = new AdherenceRecord();
        rec.setStartedOn(startedOn);
        rec.setFinishedOn(finishedOn);
        rec.setInstanceGuid(instanceGuid);
        rec.setDeclined(declined);
        return rec;
    }
    
    private List<SessionCompletionState> getReportStates(EventStreamAdherenceReport report) {
        return report.getStreams().stream()
            .flatMap(stream -> stream.getByDayEntries().values().stream())
            .flatMap(days -> days.stream())
            .flatMap(day -> day.getTimeWindows().stream())
            .map(EventStreamWindow::getState)
            .collect(toList());
    }
    
    private static Schedule2 createSchedule() {
        return createSchedule("P2DT1H");
    }
    
    private static Schedule2 createSchedule(String expiration) {
        // TimeWindow 1A
        TimeWindow win1 = new TimeWindow();
        win1.setGuid("timeWindowGuid");
        win1.setStartTime(LocalTime.parse("01:00"));
        win1.setExpiration(Period.parse(expiration));
    
        // Session 1
        Session s1 = new Session();
        s1.setDelay(Period.parse("P14D"));
        s1.setStartEventIds(ImmutableList.of("sessionStartEventId"));
        s1.setTimeWindows(ImmutableList.of(win1));
        s1.setGuid("sessionGuid");
        s1.setName("sessionName");
        s1.setPerformanceOrder(SEQUENTIAL);
    
        // TimeWindow 2A
        TimeWindow win2 = new TimeWindow();
        win2.setGuid("ksuWqp17x3i9zjQBh0FHSDS2");
        win2.setStartTime(LocalTime.parse("00:00"));
        win2.setExpiration(Period.parse("P2D"));
    
        // Session 2
        Session s2 = new Session();
        s2.setDelay(Period.parse("P14D"));
        s2.setStartEventIds(ImmutableList.of("study_burst:Main Sequence:01"));
        s2.setTimeWindows(ImmutableList.of(win2));
        s2.setGuid("u90_okqrmPgKptcc9E8lORwC");
        s2.setName("Session #1");
        s2.setPerformanceOrder(SEQUENTIAL);
    
        // Schedule
        Schedule2 schedule = new Schedule2();
        schedule.setSessions(ImmutableList.of(s1, s2));
        schedule.setAppId(TEST_APP_ID);
        schedule.setGuid(SCHEDULE_GUID);
        schedule.setName("Test Schedule");
        schedule.setOwnerId("sage-bionetworks");
        schedule.setDuration(Period.parse("P4W"));
        schedule.setCreatedOn(CREATED_ON);
        schedule.setModifiedOn(MODIFIED_ON);
    
        return schedule;
    }
}
