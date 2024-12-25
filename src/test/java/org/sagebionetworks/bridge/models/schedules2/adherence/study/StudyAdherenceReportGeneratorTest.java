package org.sagebionetworks.bridge.models.schedules2.adherence.study;

import static org.sagebionetworks.bridge.TestConstants.ASSESSMENT_1_GUID;
import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.sagebionetworks.bridge.TestConstants.SCHEDULE_GUID;
import static org.sagebionetworks.bridge.TestConstants.SESSION_GUID_1;
import static org.sagebionetworks.bridge.TestConstants.SESSION_GUID_2;
import static org.sagebionetworks.bridge.TestConstants.SESSION_GUID_3;
import static org.sagebionetworks.bridge.TestConstants.SESSION_GUID_4;
import static org.sagebionetworks.bridge.TestConstants.SESSION_WINDOW_GUID_2;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.IMMUTABLE;
import static org.sagebionetworks.bridge.models.activities.ActivityEventUpdateType.MUTABLE;
import static org.sagebionetworks.bridge.models.schedules2.PerformanceOrder.SEQUENTIAL;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.COMPLETED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.EXPIRED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.NOT_YET_AVAILABLE;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.UNSTARTED;
import static org.sagebionetworks.bridge.models.schedules2.adherence.study.StudyAdherenceReportGenerator.INSTANCE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.joda.time.Period;
import org.mockito.Mockito;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.activities.ActivityEventObjectType;
import org.sagebionetworks.bridge.models.activities.StudyActivityEvent;
import org.sagebionetworks.bridge.models.schedules2.AssessmentReference;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.Session;
import org.sagebionetworks.bridge.models.schedules2.StudyBurst;
import org.sagebionetworks.bridge.models.schedules2.TimeWindow;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceState;
import org.sagebionetworks.bridge.models.schedules2.adherence.ParticipantStudyProgress;
import org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamDay;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamWindow;
import org.sagebionetworks.bridge.models.schedules2.adherence.weekly.WeeklyAdherenceReportRow;
import org.sagebionetworks.bridge.models.schedules2.timelines.Scheduler;
import org.sagebionetworks.bridge.models.schedules2.timelines.Timeline;
import org.sagebionetworks.bridge.models.schedules2.timelines.TimelineMetadata;
import org.testng.annotations.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class StudyAdherenceReportGeneratorTest extends Mockito {
    
    private static final DateTime STUDY_BURST_3_TS = DateTime.parse("2022-03-22T16:23:15.999-08:00");
    private static final DateTime STUDY_BURST_2_TS = DateTime.parse("2022-03-15T16:23:15.999-08:00");
    private static final DateTime STUDY_BURST_1_TS = DateTime.parse("2022-03-08T16:23:15.999-08:00");
    private static final DateTime EVENT_2_TS = DateTime.parse("2022-03-10T16:23:15.999-08:00");
    private static final DateTime TIMELINE_RETRIEVED_TS = DateTime.parse("2022-03-01T16:23:15.999-08:00");
    private static final DateTime NOW = DateTime.parse("2022-03-15T01:00:00.000-08:00");

    public static StudyAdherenceReport createReport() throws Exception {
        AdherenceState state = createAdherenceState().build();
        Schedule2 schedule = createSchedule();
        return INSTANCE.generate(state, schedule);
    }
    
    public static List<AdherenceRecord> createAdherenceRecords() {
        // initial survey
        AdherenceRecord rec1 = new AdherenceRecord();
        rec1.setInstanceGuid("pqVRM8cV-buumqQvUGwRsQ");
        rec1.setStartedOn(DateTime.now());
        rec1.setFinishedOn(DateTime.now());
        
        // baseline tapping test
        AdherenceRecord rec2 = new AdherenceRecord();
        rec2.setInstanceGuid("xyvAcmEYAVAzCMfGhf187g");
        rec2.setStartedOn(DateTime.now());
        rec2.setFinishedOn(DateTime.now());
        
        // study burst 1 tapping test: g0-ktzutxhFU9bWYXJfLJQ
        AdherenceRecord rec3 = new AdherenceRecord();
        rec3.setInstanceGuid("freUhgN8OBMQOuUJBY_b4Q");
        rec3.setDeclined(true);
        
        // study burst 2 tapping test: ZWFzGqjQucjHS2YM6wLmSQ
        AdherenceRecord rec4 = new AdherenceRecord();
        rec4.setInstanceGuid("B01W5ru8Cjr8DAbODKcMKA");
        rec4.setStartedOn(DateTime.now());
        
        return ImmutableList.of(rec1, rec2, rec3, rec4);
    }
    
    public static List<TimelineMetadata> createTimelineMetadata() throws Exception {
        Timeline timeline = Scheduler.INSTANCE.calculateTimeline(createSchedule());
        return timeline.getMetadata();
    }
    
    public static List<StudyActivityEvent> createEvents() {
        StudyActivityEvent e1 = new StudyActivityEvent.Builder()
                .withEventId("timeline_retrieved")
                .withTimestamp(TIMELINE_RETRIEVED_TS)
                .withObjectType(ActivityEventObjectType.TIMELINE_RETRIEVED)
                .build();
        StudyActivityEvent e2 = new StudyActivityEvent.Builder()
                .withEventId("custom:event2")
                .withTimestamp(EVENT_2_TS)
                .withObjectType(ActivityEventObjectType.CUSTOM)
                .build();
        
        // don't forget the study burst events! They are not calculated at the time
        // the schedule is calculated!
        StudyActivityEvent e3 = new StudyActivityEvent.Builder()
                .withEventId("study_burst:Study Burst:01")
                .withTimestamp(STUDY_BURST_1_TS)
                .withObjectType(ActivityEventObjectType.STUDY_BURST)
                .build();
        StudyActivityEvent e4 = new StudyActivityEvent.Builder()
                .withEventId("study_burst:Study Burst:02")
                .withTimestamp(STUDY_BURST_2_TS)
                .withObjectType(ActivityEventObjectType.STUDY_BURST)
                .build();
        StudyActivityEvent e5 = new StudyActivityEvent.Builder()
                .withEventId("study_burst:Study Burst:03")
                .withTimestamp(STUDY_BURST_3_TS)
                .withObjectType(ActivityEventObjectType.STUDY_BURST)
                .build();
        return ImmutableList.of(e1, e2, e3, e4, e5);
    }
    
    public static AdherenceState.Builder createAdherenceState() throws Exception {
        return new AdherenceState.Builder()
                .withStudyStartEventId("timeline_retrieved")
                .withMetadata(createTimelineMetadata())
                .withEvents(createEvents())
                .withNow(NOW);
    }
    
    /**
     * This is not a super-complicated schedule, but I have mapped out the exact dates when
     * everything should occur for a timeline_retrieved event of 3/1, so I can verify the 
     * dates and such are correct after the generator executes.
     */
    public static Schedule2 createSchedule() {
        // survey
        AssessmentReference ref1 = new AssessmentReference();
        ref1.setGuid("survey");
        ref1.setAppId(TEST_APP_ID);
        ref1.setIdentifier("survey");
        
        // tapping test
        AssessmentReference ref2 = new AssessmentReference();
        ref2.setGuid("tappingTest");
        ref2.setAppId(TEST_APP_ID);
        ref2.setIdentifier("tappingTest");
        
        // final survey
        AssessmentReference ref3 = new AssessmentReference();
        ref3.setGuid("finalSurvey");
        ref3.setAppId(TEST_APP_ID);
        ref3.setIdentifier("finalSurvey");
        
        Schedule2 schedule = new Schedule2();
        
        // Initial survey
        TimeWindow win1 = new TimeWindow();
        win1.setGuid("win1");
        win1.setStartTime(LocalTime.parse("00:00"));
        win1.setExpiration(Period.parse("P1D"));
        
        Session s1 = new Session();
        s1.setAssessments(ImmutableList.of(ref1));
        s1.setDelay(Period.parse("P1D"));
        s1.setStartEventIds(ImmutableList.of("timeline_retrieved"));
        s1.setTimeWindows(ImmutableList.of(win1));
        s1.setGuid("initialSurveyGuid");
        s1.setName("Initial Survey");
        s1.setPerformanceOrder(SEQUENTIAL);
        
        // Baseline tapping test
        TimeWindow win2 = new TimeWindow();
        win2.setGuid("win2");
        win2.setStartTime(LocalTime.parse("00:00"));
        win2.setExpiration(Period.parse("P1D"));
        
        Session s2 = new Session();
        s2.setAssessments(ImmutableList.of(ref2));
        s2.setDelay(Period.parse("P2D"));
        s2.setStartEventIds(ImmutableList.of("timeline_retrieved"));
        s2.setTimeWindows(ImmutableList.of(win2));
        s2.setGuid("baselineGuid");
        s2.setName("Baseline Tapping Test");
        s2.setPerformanceOrder(SEQUENTIAL);
        
        // Study Burst
        StudyBurst burst = new StudyBurst();
        burst.setIdentifier("Study Burst");
        burst.setOriginEventId("timeline_retrieved");
        burst.setUpdateType(IMMUTABLE);
        burst.setDelay(Period.parse("P1W"));
        burst.setOccurrences(3);
        burst.setInterval(Period.parse("P1W"));

        TimeWindow win3 = new TimeWindow();
        win3.setGuid("win3");
        win3.setStartTime(LocalTime.parse("00:00"));
        win3.setExpiration(Period.parse("P1D"));

        Session s3 = new Session();
        s3.setAssessments(ImmutableList.of(ref2));
        s3.setStudyBurstIds(ImmutableList.of("Study Burst"));
        s3.setTimeWindows(ImmutableList.of(win3));
        s3.setGuid("burstTappingGuid");
        s3.setName("Study Burst Tapping Test");
        s3.setPerformanceOrder(SEQUENTIAL);
        
        // Final survey
        TimeWindow win4 = new TimeWindow();
        win4.setGuid("win4");
        win4.setStartTime(LocalTime.parse("00:00"));
        win4.setExpiration(Period.parse("P3D"));
        
        Session s4 = new Session();
        s4.setAssessments(ImmutableList.of(ref3));
        s4.setDelay(Period.parse("P24D"));
        s4.setStartEventIds(ImmutableList.of("timeline_retrieved"));
        s4.setTimeWindows(ImmutableList.of(win4));
        s4.setGuid("finalSurveyGuid");
        s4.setName("Final Survey");
        s4.setPerformanceOrder(SEQUENTIAL);
        
        // Supplemental survey that does not fire for our test user
        TimeWindow win5 = new TimeWindow();
        win5.setGuid("win5");
        win5.setStartTime(LocalTime.parse("00:00"));
        win5.setExpiration(Period.parse("PT12H"));

        Session s5 = new Session(); 
        s5.setAssessments(ImmutableList.of(ref1));
        s5.setStartEventIds(ImmutableList.of("custom:event1"));
        s5.setTimeWindows(ImmutableList.of(win5));
        s5.setGuid("session5");
        s5.setName("Supplemental Survey");
        s5.setPerformanceOrder(SEQUENTIAL);

        schedule.setSessions(ImmutableList.of(s1, s2, s3, s4, s5));
        schedule.setAppId(TEST_APP_ID);
        schedule.setGuid(SCHEDULE_GUID);
        schedule.setName("Test Schedule");
        schedule.setOwnerId("sage-bionetworks");
        schedule.setDuration(Period.parse("P4W"));
        schedule.setStudyBursts(ImmutableList.of(burst));
        schedule.setCreatedOn(CREATED_ON);
        schedule.setModifiedOn(MODIFIED_ON);
        
        return schedule;
    }
    
    @Test
    public void generate() throws Exception {
        DateTimeZone zone = DateTimeZone.forID("America/Chicago");
        
        AdherenceState.Builder builder = createAdherenceState();
        builder.withAdherenceRecords(createAdherenceRecords());
        builder.withClientTimeZone("America/Chicago");
        Schedule2 schedule = createSchedule();
        StudyAdherenceReport report = INSTANCE.generate(builder.build(), schedule);
        
        // testAccount is set in the service, not the generator
        assertEquals(report.getAdherencePercent(), Integer.valueOf(50));
        assertEquals(report.getProgression(), ParticipantStudyProgress.IN_PROGRESS);
        assertEquals(report.getDateRange().getStartDate(), LocalDate.parse("2022-03-01"));
        assertEquals(report.getDateRange().getEndDate(), LocalDate.parse("2022-03-27"));
        
        assertEquals(report.getUnsetEventIds(), ImmutableSet.of("custom:event1"));
        assertEquals(report.getUnscheduledSessions(), ImmutableSet.of("Supplemental Survey"));
        assertEquals(report.getEventTimestamps().get("timeline_retrieved"), TIMELINE_RETRIEVED_TS.withZone(zone));
        assertEquals(report.getEventTimestamps().get("study_burst:Study Burst:01"), STUDY_BURST_1_TS.withZone(zone));
        assertEquals(report.getEventTimestamps().get("study_burst:Study Burst:02"), STUDY_BURST_2_TS.withZone(zone));
        assertEquals(report.getEventTimestamps().get("study_burst:Study Burst:03"), STUDY_BURST_3_TS.withZone(zone));
        // Not used in the test schedule
        assertNull(report.getEventTimestamps().get("custom:event2"));

        assertEquals(report.getWeeks().size(), 4);
        StudyReportWeek week1 = report.getWeeks().getFirst();
        assertEquals(week1.getStartDate(), LocalDate.parse("2022-03-01"));
        assertEquals(week1.getWeekInStudy(), 1);
        assertEquals(week1.getAdherencePercent(), Integer.valueOf(100));
        assertEquals(week1.getRows().size(), 2);
        
        WeeklyAdherenceReportRow week1Row1 = week1.getRows().getFirst();
        assertEquals(week1Row1.getLabel(), "Baseline Tapping Test / Week 1");
        assertEquals(week1Row1.getSearchableLabel(), ":Baseline Tapping Test:Week 1:");
        assertEquals(week1Row1.getSessionGuid(), "baselineGuid");
        assertEquals(week1Row1.getStartEventId(), "timeline_retrieved");
        assertEquals(week1Row1.getSessionName(), "Baseline Tapping Test");
        assertEquals(week1Row1.getWeekInStudy(), Integer.valueOf(1));
        
        WeeklyAdherenceReportRow week1Row2 = week1.getRows().get(1);
        assertEquals(week1Row2.getLabel(), "Initial Survey / Week 1");
        assertEquals(week1Row2.getSearchableLabel(), ":Initial Survey:Week 1:");
        assertEquals(week1Row2.getSessionGuid(), "initialSurveyGuid");
        assertEquals(week1Row2.getStartEventId(), "timeline_retrieved");
        assertEquals(week1Row2.getSessionName(), "Initial Survey");
        assertEquals(week1Row2.getWeekInStudy(), Integer.valueOf(1));
        
        List<EventStreamDay> week1Day0 = week1.getByDayEntries().get(0);
        assertEquals(week1Day0.size(), 2);
        assertDay(week1Day0.getFirst(), "2022-03-01", 0, false);
        assertDay(week1Day0.get(1), "2022-03-01", 0, false);
        
        List<EventStreamDay> week1Day1 = week1.getByDayEntries().get(1);
        assertEquals(week1Day1.size(), 2);
        assertDay(week1Day1.getFirst(), "2022-03-02", 0, false);
        assertDay(week1Day1.get(1), "2022-03-02", 1, false);
        
        assertEquals(week1Day1.get(1).getSessionGuid(), "initialSurveyGuid");
        assertEquals(week1Day1.get(1).getStartEventId(), "timeline_retrieved");
        assertTimeWindow(week1Day1.get(1).getTimeWindows().getFirst(), "pqVRM8cV-buumqQvUGwRsQ", 
                "win1", COMPLETED, "2022-03-02");
        
        List<EventStreamDay> week1Day2 = week1.getByDayEntries().get(2);
        assertEquals(week1Day2.size(), 2);
        assertDay(week1Day2.getFirst(), "2022-03-03", 1, false);
        assertDay(week1Day2.get(1), "2022-03-03", 0, false);
        
        assertEquals(week1Day2.getFirst().getSessionGuid(), "baselineGuid");
        assertEquals(week1Day2.getFirst().getStartEventId(), "timeline_retrieved");
        assertEquals(week1Day2.getFirst().getStartDate(), LocalDate.parse("2022-03-03"));
        assertTimeWindow(week1Day2.getFirst().getTimeWindows().getFirst(), "xyvAcmEYAVAzCMfGhf187g", 
                "win2", COMPLETED, "2022-03-03");
        
        List<EventStreamDay> week1Day3 = week1.getByDayEntries().get(3);
        assertEquals(week1Day3.size(), 2);
        assertDay(week1Day3.getFirst(), "2022-03-04", 0, false);
        assertDay(week1Day3.get(1), "2022-03-04", 0, false);
        
        List<EventStreamDay> week1Day4 = week1.getByDayEntries().get(4);
        assertEquals(week1Day4.size(), 2);
        assertDay(week1Day4.getFirst(), "2022-03-05", 0, false);
        assertDay(week1Day4.get(1), "2022-03-05", 0, false);
        
        List<EventStreamDay> week1Day5 = week1.getByDayEntries().get(5);
        assertEquals(week1Day5.size(), 2);
        assertDay(week1Day5.getFirst(), "2022-03-06", 0, false);
        assertDay(week1Day5.get(1), "2022-03-06", 0, false);
        
        List<EventStreamDay> week1Day6 = week1.getByDayEntries().get(6);
        assertEquals(week1Day6.size(), 2);
        assertDay(week1Day6.getFirst(), "2022-03-07", 0, false);
        assertDay(week1Day6.get(1), "2022-03-07", 0, false);
        
        StudyReportWeek week2 = report.getWeeks().get(1);
        assertEquals(week2.getStartDate(), LocalDate.parse("2022-03-08"));
        assertEquals(week2.getWeekInStudy(), 2);
        assertEquals(week2.getAdherencePercent(), Integer.valueOf(0));
        assertEquals(week2.getRows().size(), 1);
        
        WeeklyAdherenceReportRow week2Row1 = week2.getRows().getFirst();
        assertEquals(week2Row1.getLabel(), "Study Burst 1 / Week 2 / Study Burst Tapping Test");
        assertEquals(week2Row1.getSearchableLabel(), 
                ":Study Burst:Study Burst 1:Week 2:Study Burst Tapping Test:");
        assertEquals(week2Row1.getSessionGuid(), "burstTappingGuid");
        assertEquals(week2Row1.getStartEventId(), "study_burst:Study Burst:01");
        assertEquals(week2Row1.getSessionName(), "Study Burst Tapping Test");
        assertEquals(week2Row1.getWeekInStudy(), Integer.valueOf(2));
        assertEquals(week2Row1.getStudyBurstId(), "Study Burst");
        assertEquals(week2Row1.getStudyBurstNum(), Integer.valueOf(1));
        
        List<EventStreamDay> week2Day1 = week2.getByDayEntries().get(0);
        assertEquals(week2Day1.size(), 1);
        assertDay(week2Day1.getFirst(), "2022-03-08", 1, false);
        assertEquals(week2Day1.getFirst().getSessionGuid(), "burstTappingGuid");
        assertEquals(week2Day1.getFirst().getStartEventId(), "study_burst:Study Burst:01");
        assertEquals(week2Day1.getFirst().getTimeWindows().size(), 1);
        assertTimeWindow(week2Day1.getFirst().getTimeWindows().getFirst(), "freUhgN8OBMQOuUJBY_b4Q", "win3",
                SessionCompletionState.DECLINED, "2022-03-08");
        
        List<EventStreamDay> week2Day2 = week2.getByDayEntries().get(1);
        assertEquals(week2Day2.size(), 1);
        assertEquals(week2Day2.getFirst().getStartDate(), LocalDate.parse("2022-03-09"));
        assertTrue(week2Day2.getFirst().getTimeWindows().isEmpty());
        
        List<EventStreamDay> week2Day3 = week2.getByDayEntries().get(2);
        assertEquals(week2Day3.size(), 1);
        assertEquals(week2Day3.getFirst().getStartDate(), LocalDate.parse("2022-03-10"));
        assertTrue(week2Day3.getFirst().getTimeWindows().isEmpty());
        
        List<EventStreamDay> week2Day4 = week2.getByDayEntries().get(3);
        assertEquals(week2Day4.size(), 1);
        assertEquals(week2Day4.getFirst().getStartDate(), LocalDate.parse("2022-03-11"));
        assertTrue(week2Day4.getFirst().getTimeWindows().isEmpty());
        
        List<EventStreamDay> week2Day5 = week2.getByDayEntries().get(4);
        assertEquals(week2Day5.size(), 1);
        assertEquals(week2Day5.getFirst().getStartDate(), LocalDate.parse("2022-03-12"));
        assertTrue(week2Day5.getFirst().getTimeWindows().isEmpty());
        
        List<EventStreamDay> week2Day6 = week2.getByDayEntries().get(5);
        assertEquals(week2Day6.size(), 1);
        assertEquals(week2Day6.getFirst().getStartDate(), LocalDate.parse("2022-03-13"));
        assertTrue(week2Day6.getFirst().getTimeWindows().isEmpty());

        List<EventStreamDay> week2Day7 = week2.getByDayEntries().get(6);
        assertEquals(week2Day7.size(), 1);
        assertEquals(week2Day7.getFirst().getStartDate(), LocalDate.parse("2022-03-14"));
        assertTrue(week2Day7.getFirst().getTimeWindows().isEmpty());

        StudyReportWeek week3 = report.getWeeks().get(2);
        assertEquals(week3.getStartDate(), LocalDate.parse("2022-03-15"));
        assertEquals(week3.getWeekInStudy(), 3);
        assertEquals(week3.getAdherencePercent(), Integer.valueOf(0));
        
        WeeklyAdherenceReportRow week3Row1 = week3.getRows().getFirst();
        assertEquals(week3Row1.getLabel(), "Study Burst 2 / Week 3 / Study Burst Tapping Test");
        assertEquals(week3Row1.getSearchableLabel(), 
                ":Study Burst:Study Burst 2:Week 3:Study Burst Tapping Test:");
        assertEquals(week3Row1.getSessionGuid(), "burstTappingGuid");
        assertEquals(week3Row1.getStartEventId(), "study_burst:Study Burst:02");
        assertEquals(week3Row1.getSessionName(), "Study Burst Tapping Test");
        assertEquals(week3Row1.getWeekInStudy(), Integer.valueOf(3));
        assertEquals(week3Row1.getStudyBurstId(), "Study Burst");
        assertEquals(week3Row1.getStudyBurstNum(), Integer.valueOf(2));
        
        List<EventStreamDay> week3Day1 = week3.getByDayEntries().get(0);
        assertTrue(week3Day1.getFirst().isToday());
        assertEquals(week3Day1.getFirst().getSessionGuid(), "burstTappingGuid");
        assertEquals(week3Day1.getFirst().getStartEventId(), "study_burst:Study Burst:02");
        assertEquals(week3Day1.getFirst().getStartDate(), LocalDate.parse("2022-03-15"));
        assertEquals(week3Day1.getFirst().getTimeWindows().size(), 1);
        assertTimeWindow(week3Day1.getFirst().getTimeWindows().getFirst(), "B01W5ru8Cjr8DAbODKcMKA", "win3",
                SessionCompletionState.STARTED, "2022-03-15");
        
        List<EventStreamDay> week3Day2 = week3.getByDayEntries().get(1);
        assertEquals(week3Day2.size(), 1);
        assertEquals(week3Day2.getFirst().getStartDate(), LocalDate.parse("2022-03-16"));
        assertTrue(week3Day2.getFirst().getTimeWindows().isEmpty());
        
        List<EventStreamDay> week3Day3 = week3.getByDayEntries().get(2);
        assertEquals(week3Day3.size(), 1);
        assertEquals(week3Day3.getFirst().getStartDate(), LocalDate.parse("2022-03-17"));
        assertTrue(week3Day3.getFirst().getTimeWindows().isEmpty());
        
        List<EventStreamDay> week3Day4 = week3.getByDayEntries().get(3);
        assertEquals(week3Day4.size(), 1);
        assertEquals(week3Day4.getFirst().getStartDate(), LocalDate.parse("2022-03-18"));
        assertTrue(week3Day4.getFirst().getTimeWindows().isEmpty());
        
        List<EventStreamDay> week3Day5 = week3.getByDayEntries().get(4);
        assertEquals(week3Day5.size(), 1);
        assertEquals(week3Day5.getFirst().getStartDate(), LocalDate.parse("2022-03-19"));
        assertTrue(week3Day5.getFirst().getTimeWindows().isEmpty());
        
        List<EventStreamDay> week3Day6 = week3.getByDayEntries().get(5);
        assertEquals(week3Day6.size(), 1);
        assertEquals(week3Day6.getFirst().getStartDate(), LocalDate.parse("2022-03-20"));
        assertTrue(week3Day6.getFirst().getTimeWindows().isEmpty());
        
        List<EventStreamDay> week3Day7 = week3.getByDayEntries().get(6);
        assertEquals(week3Day7.size(), 1);
        assertEquals(week3Day7.getFirst().getStartDate(), LocalDate.parse("2022-03-21"));
        assertTrue(week3Day7.getFirst().getTimeWindows().isEmpty());
        
        StudyReportWeek week4 = report.getWeeks().get(3);
        assertEquals(week4.getStartDate(), LocalDate.parse("2022-03-22"));
        assertEquals(week4.getWeekInStudy(), 4);
        
        WeeklyAdherenceReportRow week4Row1 = week4.getRows().getFirst();
        assertEquals(week4Row1.getLabel(), "Study Burst 3 / Week 4 / Study Burst Tapping Test");
        assertEquals(week4Row1.getSearchableLabel(), 
                ":Study Burst:Study Burst 3:Week 4:Study Burst Tapping Test:");
        assertEquals(week4Row1.getSessionGuid(), "burstTappingGuid");
        assertEquals(week4Row1.getStartEventId(), "study_burst:Study Burst:03");
        assertEquals(week4Row1.getSessionName(), "Study Burst Tapping Test");
        assertEquals(week4Row1.getWeekInStudy(), Integer.valueOf(4));
        assertEquals(week4Row1.getStudyBurstId(), "Study Burst");
        assertEquals(week4Row1.getStudyBurstNum(), Integer.valueOf(3));
        
        WeeklyAdherenceReportRow week4Row2 = week4.getRows().get(1);
        assertEquals(week4Row2.getLabel(), "Final Survey / Week 4");
        assertEquals(week4Row2.getSearchableLabel(), ":Final Survey:Week 4:");
        assertEquals(week4Row2.getSessionGuid(), "finalSurveyGuid");
        assertEquals(week4Row2.getStartEventId(), "timeline_retrieved");
        assertEquals(week4Row2.getSessionName(), "Final Survey");
        assertEquals(week4Row2.getWeekInStudy(), Integer.valueOf(4));
        assertNull(week4Row2.getStudyBurstId());
        assertNull(week4Row2.getStudyBurstNum());
        
        List<EventStreamDay> week4Day1 = week4.getByDayEntries().get(0);
        assertEquals(week4Day1.size(), 2);
        assertFalse(week4Day1.getFirst().isToday());
        assertEquals(week4Day1.getFirst().getSessionGuid(), "burstTappingGuid");
        assertEquals(week4Day1.getFirst().getStartEventId(), "study_burst:Study Burst:03");
        assertEquals(week4Day1.getFirst().getStartDate(), LocalDate.parse("2022-03-22"));
        assertTimeWindow(week4Day1.getFirst().getTimeWindows().getFirst(), "sdOEXR4pJ-EyQQF8YmjwFw", "win3",
            SessionCompletionState.NOT_YET_AVAILABLE, "2022-03-22");
        assertTrue(week4Day1.get(1).getTimeWindows().isEmpty());
        
        List<EventStreamDay> week4Day2 = week4.getByDayEntries().get(1);
        assertEquals(week4Day2.size(), 2);
        assertTrue(week4Day2.getFirst().getTimeWindows().isEmpty());
        assertTrue(week4Day2.get(1).getTimeWindows().isEmpty());
        
        List<EventStreamDay> week4Day3 = week4.getByDayEntries().get(2);
        assertEquals(week4Day3.size(), 2);
        assertTrue(week4Day3.getFirst().getTimeWindows().isEmpty());
        assertTrue(week4Day3.get(1).getTimeWindows().isEmpty());
        
        List<EventStreamDay> week4Day4 = week4.getByDayEntries().get(3);
        assertEquals(week4Day4.size(), 2);
        assertFalse(week4Day4.get(1).isToday());
        assertEquals(week4Day4.get(1).getSessionGuid(), "finalSurveyGuid");
        assertEquals(week4Day4.get(1).getStartEventId(), "timeline_retrieved");
        assertEquals(week4Day4.get(1).getStartDate(), LocalDate.parse("2022-03-25"));
        assertTimeWindow(week4Day4.get(1).getTimeWindows().getFirst(), "7aD28QS4xd0GLZELfIh0-Q", "win4",
                SessionCompletionState.NOT_YET_AVAILABLE, "2022-03-27");
        
        List<EventStreamDay> week4Day5 = week4.getByDayEntries().get(4);
        assertEquals(week4Day5.size(), 2);
        assertTrue(week4Day5.getFirst().getTimeWindows().isEmpty());
        assertTrue(week4Day5.get(1).getTimeWindows().isEmpty());

        List<EventStreamDay> week4Day6 = week4.getByDayEntries().get(5);
        assertEquals(week4Day6.size(), 2);
        assertTrue(week4Day6.getFirst().getTimeWindows().isEmpty());
        assertTrue(week4Day6.get(1).getTimeWindows().isEmpty());
        
        List<EventStreamDay> week4Day7 = week4.getByDayEntries().get(6);
        assertEquals(week4Day7.size(), 2);
        assertTrue(week4Day7.getFirst().getTimeWindows().isEmpty());
        assertTrue(week4Day7.get(1).getTimeWindows().isEmpty());        
    }
    
    @Test
    public void generate_noClientTimeZone() throws Exception {
        AdherenceState.Builder builder = createAdherenceState();
        builder.withAdherenceRecords(createAdherenceRecords());
        Schedule2 schedule = createSchedule();
        StudyAdherenceReport report = INSTANCE.generate(builder.build(), schedule);
        
        assertEquals(report.getEventTimestamps().get("timeline_retrieved"), TIMELINE_RETRIEVED_TS);
        assertEquals(report.getEventTimestamps().get("study_burst:Study Burst:01"), STUDY_BURST_1_TS);
        assertEquals(report.getEventTimestamps().get("study_burst:Study Burst:02"), STUDY_BURST_2_TS);
        assertEquals(report.getEventTimestamps().get("study_burst:Study Burst:03"), STUDY_BURST_3_TS);
    }
    
    @Test
    public void generate_noAdherenceRecords() throws Exception {
        AdherenceState.Builder builder = createAdherenceState();
        builder.withClientTimeZone("America/Chicago");
        Schedule2 schedule = createSchedule();
        StudyAdherenceReport report = INSTANCE.generate(builder.build(), schedule);
        
        assertEquals(report.getAdherencePercent(), Integer.valueOf(0));
        StudyReportWeek week1 = report.getWeeks().getFirst();
        assertEquals(week1.getAdherencePercent(), Integer.valueOf(0));
        assertEquals(week1.getRows().size(), 2);

        List<EventStreamDay> week1Day1 = week1.getByDayEntries().get(1);
        assertEquals(week1Day1.get(1).getTimeWindows().getFirst().getState(), EXPIRED);
        
        List<EventStreamDay> week1Day2 = week1.getByDayEntries().get(2);
        assertEquals(week1Day2.getFirst().getTimeWindows().getFirst().getState(), EXPIRED);
        
        StudyReportWeek week2 = report.getWeeks().get(1);
        List<EventStreamDay> week2Day1 = week2.getByDayEntries().get(0);
        assertEquals(week2Day1.getFirst().getTimeWindows().getFirst().getState(), EXPIRED);
        
        StudyReportWeek week3 = report.getWeeks().get(2);
        List<EventStreamDay> week3Day1 = week3.getByDayEntries().get(0);
        assertEquals(week3Day1.getFirst().getTimeWindows().getFirst().getState(), UNSTARTED);
        
        StudyReportWeek week4 = report.getWeeks().get(3);
        List<EventStreamDay> week4Day1 = week4.getByDayEntries().get(0);
        assertEquals(week4Day1.getFirst().getTimeWindows().getFirst().getState(), NOT_YET_AVAILABLE);
        
        List<EventStreamDay> week4Day4 = week4.getByDayEntries().get(3);
        assertEquals(week4Day4.get(1).getTimeWindows().getFirst().getState(), NOT_YET_AVAILABLE);
    }
    
    @Test
    public void generate_noEvents() throws Exception {
        AdherenceState.Builder builder = createAdherenceState();
        builder.withAdherenceRecords(createAdherenceRecords());
        builder.withClientTimeZone("America/Chicago");
        builder.withEvents(ImmutableList.of());
        Schedule2 schedule = createSchedule();
        
        StudyAdherenceReport report = INSTANCE.generate(builder.build(), schedule);
        
        // testAccount is set in the service, not the generator
        assertNull(report.getAdherencePercent());
        assertEquals(report.getProgression(), ParticipantStudyProgress.UNSTARTED);
        assertNull(report.getDateRange());
        
        assertEquals(report.getUnsetEventIds(), ImmutableSet.of("custom:event1", "timeline_retrieved", 
                "study_burst:Study Burst:01", "study_burst:Study Burst:02", "study_burst:Study Burst:03"));
        assertEquals(report.getUnscheduledSessions(), ImmutableSet.of("Supplemental Survey", "Initial Survey",
                "Baseline Tapping Test", "Study Burst Tapping Test", "Final Survey"));
        assertTrue(report.getEventTimestamps().isEmpty());

        assertEquals(report.getWeeks().size(), 0);
        
        // Verify this too
        assertNull(report.getWeekReport().getAdherencePercent());
    }
    
    @Test
    public void generate_noSchedule() throws Exception {
        AdherenceState.Builder builder = createAdherenceState();
        builder.withMetadata(ImmutableList.of());
        builder.withAdherenceRecords(createAdherenceRecords());
        builder.withClientTimeZone("America/Chicago");
        Schedule2 schedule = createSchedule();
        
        StudyAdherenceReport report = INSTANCE.generate(builder.build(), schedule);
        assertEquals(report.getProgression(), ParticipantStudyProgress.UNSTARTED);
        assertTrue(report.getWeeks().isEmpty());
        assertTrue(report.getUnsetEventIds().isEmpty());
        assertTrue(report.getUnscheduledSessions().isEmpty());
        assertTrue(report.getEventTimestamps().isEmpty());
        
        StudyReportWeek week = report.getWeekReport();
        assertEquals(week.getWeekInStudy(), 1);
        assertTrue(week.getRows().isEmpty());
        assertTrue(week.getByDayEntries().get(0).isEmpty());
        assertTrue(week.getByDayEntries().get(1).isEmpty());
        assertTrue(week.getByDayEntries().get(2).isEmpty());
        assertTrue(week.getByDayEntries().get(3).isEmpty());
        assertTrue(week.getByDayEntries().get(4).isEmpty());
        assertTrue(week.getByDayEntries().get(5).isEmpty());
        assertTrue(week.getByDayEntries().get(6).isEmpty());
    }
    
    @Test
    public void generate_noStudyStartEvent() throws Exception {
        AdherenceState.Builder builder = createAdherenceState();
        // remove timeline_retrieved
        builder.withEvents(createEvents().subList(1,  createEvents().size()));
        builder.withAdherenceRecords(createAdherenceRecords());
        builder.withClientTimeZone("America/Chicago");
        Schedule2 schedule = createSchedule();
        StudyAdherenceReport report = INSTANCE.generate(builder.build(), schedule);
        
        // testAccount is set in the service, not the generator
        assertEquals(report.getAdherencePercent(), Integer.valueOf(0));
        assertEquals(report.getProgression(), ParticipantStudyProgress.IN_PROGRESS);
        assertEquals(report.getDateRange().getStartDate(), LocalDate.parse("2022-03-08"));
        assertEquals(report.getDateRange().getEndDate(), LocalDate.parse("2022-03-22"));
        
        assertEquals(report.getUnsetEventIds(), ImmutableSet.of("custom:event1", "timeline_retrieved"));
        assertEquals(report.getUnscheduledSessions(),
                ImmutableSet.of("Supplemental Survey", "Initial Survey", "Baseline Tapping Test", "Final Survey"));
        
        assertEquals(report.getWeeks().size(), 3);
        
        // It's the study bursts, just test a few fields to verify this.
        StudyReportWeek week1 = report.getWeeks().getFirst();
        assertEquals(week1.getRows().getFirst().getLabel(), 
                "Study Burst 1 / Week 1 / Study Burst Tapping Test");
        
        StudyReportWeek week2 = report.getWeeks().get(1);
        assertEquals(week2.getRows().getFirst().getLabel(), 
                "Study Burst 2 / Week 2 / Study Burst Tapping Test");
        
        StudyReportWeek week3 = report.getWeeks().get(2);
        assertEquals(week3.getRows().getFirst().getLabel(), 
                "Study Burst 3 / Week 3 / Study Burst Tapping Test");
    }
    
    @Test
    public void findOrCreateDay_sessionTriggeredByTwoDifferentEventsCreatesOneRow() throws Exception {
        DateTimeZone zone = DateTimeZone.forID("America/Chicago");
        
        // Adjusted the schedule so that it triggers the first session by two events (custom:event2). We want
        // these events to have the same timestamps so we can verify they are not duplicated. We're going to 
        // lose one event ID if everything currently works correctly.
        StudyActivityEvent e1 = new StudyActivityEvent.Builder()
                .withEventId("timeline_retrieved")
                .withTimestamp(TIMELINE_RETRIEVED_TS)
                .withObjectType(ActivityEventObjectType.TIMELINE_RETRIEVED)
                .build();
        StudyActivityEvent e2 = new StudyActivityEvent.Builder()
                .withEventId("custom:event2")
                .withTimestamp(TIMELINE_RETRIEVED_TS)
                .withObjectType(ActivityEventObjectType.CUSTOM)
                .build();
        StudyActivityEvent e3 = new StudyActivityEvent.Builder()
                .withEventId("study_burst:Study Burst:01")
                .withTimestamp(STUDY_BURST_1_TS)
                .withObjectType(ActivityEventObjectType.STUDY_BURST)
                .build();
        StudyActivityEvent e4 = new StudyActivityEvent.Builder()
                .withEventId("study_burst:Study Burst:02")
                .withTimestamp(STUDY_BURST_2_TS)
                .withObjectType(ActivityEventObjectType.STUDY_BURST)
                .build();
        StudyActivityEvent e5 = new StudyActivityEvent.Builder()
                .withEventId("study_burst:Study Burst:03")
                .withTimestamp(STUDY_BURST_3_TS)
                .withObjectType(ActivityEventObjectType.STUDY_BURST)
                .build();
        List<StudyActivityEvent> events =  ImmutableList.of(e1, e2, e3, e4, e5);
        
        Schedule2 schedule = createSchedule();
        schedule.getSessions().getFirst().setStartEventIds(ImmutableList.of("timeline_retrieved", "custom:event2"));

        List<TimelineMetadata> metadata = Scheduler.INSTANCE.calculateTimeline(schedule).getMetadata();

        AdherenceState.Builder builder = createAdherenceState();
        builder.withAdherenceRecords(createAdherenceRecords());
        builder.withClientTimeZone("America/Chicago");
        builder.withMetadata(metadata);
        builder.withEvents(events);
        
        StudyAdherenceReport report = INSTANCE.generate(builder.build(), schedule);
        
        // testAccount is set in the service, not the generator
        assertEquals(report.getAdherencePercent(), Integer.valueOf(40));
        assertEquals(report.getProgression(), ParticipantStudyProgress.IN_PROGRESS);
        assertEquals(report.getDateRange().getStartDate(), LocalDate.parse("2022-03-01"));
        assertEquals(report.getDateRange().getEndDate(), LocalDate.parse("2022-03-27"));
        
        assertEquals(report.getUnsetEventIds(), ImmutableSet.of("custom:event1"));
        assertEquals(report.getUnscheduledSessions(), ImmutableSet.of("Supplemental Survey"));
        assertEquals(report.getEventTimestamps().get("timeline_retrieved"), TIMELINE_RETRIEVED_TS.withZone(zone));
        assertEquals(report.getEventTimestamps().get("study_burst:Study Burst:01"), STUDY_BURST_1_TS.withZone(zone));
        assertEquals(report.getEventTimestamps().get("study_burst:Study Burst:02"), STUDY_BURST_2_TS.withZone(zone));
        assertEquals(report.getEventTimestamps().get("study_burst:Study Burst:03"), STUDY_BURST_3_TS.withZone(zone));
        assertEquals(report.getEventTimestamps().get("custom:event2"), DateTime.parse("2022-03-01T18:23:15.999-06:00").withZone(zone));

        assertEquals(report.getWeeks().size(), 4);
        StudyReportWeek week1 = report.getWeeks().getFirst();
        assertEquals(week1.getStartDate(), LocalDate.parse("2022-03-01"));
        assertEquals(week1.getWeekInStudy(), 1);
        assertEquals(week1.getAdherencePercent(), Integer.valueOf(66));
        assertEquals(week1.getRows().size(), 3);
        
        WeeklyAdherenceReportRow week1Row1 = week1.getRows().getFirst();
        assertEquals(week1Row1.getLabel(), "Baseline Tapping Test / Week 1");
        assertEquals(week1Row1.getSearchableLabel(), ":Baseline Tapping Test:Week 1:");
        assertEquals(week1Row1.getSessionGuid(), "baselineGuid");
        assertEquals(week1Row1.getStartEventId(), "timeline_retrieved");
        assertEquals(week1Row1.getSessionName(), "Baseline Tapping Test");
        assertEquals(week1Row1.getWeekInStudy(), Integer.valueOf(1));
        
        WeeklyAdherenceReportRow week1Row2 = week1.getRows().get(1);
        assertEquals(week1Row2.getLabel(), "Initial Survey / Week 1");
        assertEquals(week1Row2.getSearchableLabel(), ":Initial Survey:Week 1:");
        assertEquals(week1Row2.getSessionGuid(), "initialSurveyGuid");
        assertEquals(week1Row2.getStartEventId(), "custom:event2");
        assertEquals(week1Row2.getSessionName(), "Initial Survey");
        assertEquals(week1Row2.getWeekInStudy(), Integer.valueOf(1));
        
        WeeklyAdherenceReportRow week1Row3 = week1.getRows().get(2);
        assertEquals(week1Row3.getLabel(), "Initial Survey / Week 1");
        assertEquals(week1Row3.getSearchableLabel(), ":Initial Survey:Week 1:");
        assertEquals(week1Row3.getSessionGuid(), "initialSurveyGuid");
        assertEquals(week1Row3.getStartEventId(), "timeline_retrieved");
        assertEquals(week1Row3.getSessionName(), "Initial Survey");
        assertEquals(week1Row3.getWeekInStudy(), Integer.valueOf(1));
        
        List<EventStreamDay> week1Day0 = week1.getByDayEntries().get(0);
        assertEquals(week1Day0.size(), 3);
        assertDay(week1Day0.getFirst(), "2022-03-01", 0, false);
        assertDay(week1Day0.get(1), "2022-03-01", 0, false);
        assertDay(week1Day0.get(2), "2022-03-01", 0, false);
        
        List<EventStreamDay> week1Day1 = week1.getByDayEntries().get(1);
        assertEquals(week1Day1.size(), 3);
        assertDay(week1Day1.getFirst(), "2022-03-02", 0, false);
        assertDay(week1Day1.get(1), "2022-03-02", 1, false);
        assertDay(week1Day1.get(2), "2022-03-02", 1, false);
        
        assertEquals(week1Day1.get(1).getSessionGuid(), "initialSurveyGuid");
        assertEquals(week1Day1.get(1).getStartEventId(), "custom:event2");
        assertTimeWindow(week1Day1.get(1).getTimeWindows().getFirst(), "E_ZDacDPqL-vBKGnB6u6Iw", 
                "win1", EXPIRED, "2022-03-02");
        
        assertEquals(week1Day1.get(2).getSessionGuid(), "initialSurveyGuid");
        assertEquals(week1Day1.get(2).getStartEventId(), "timeline_retrieved");
        assertTimeWindow(week1Day1.get(2).getTimeWindows().getFirst(), "pqVRM8cV-buumqQvUGwRsQ", 
                "win1", COMPLETED, "2022-03-02");
        
        List<EventStreamDay> week1Day2 = week1.getByDayEntries().get(2);
        assertEquals(week1Day2.size(), 3);
        assertDay(week1Day2.getFirst(), "2022-03-03", 1, false);
        assertDay(week1Day2.get(1), "2022-03-03", 0, false);
        assertDay(week1Day2.get(2), "2022-03-03", 0, false);
        
        assertEquals(week1Day2.getFirst().getSessionGuid(), "baselineGuid");
        assertEquals(week1Day2.getFirst().getStartEventId(), "timeline_retrieved");
        assertEquals(week1Day2.getFirst().getStartDate(), LocalDate.parse("2022-03-03"));
        assertTimeWindow(week1Day2.getFirst().getTimeWindows().getFirst(), "xyvAcmEYAVAzCMfGhf187g", 
                "win2", COMPLETED, "2022-03-03");
        
        List<EventStreamDay> week1Day3 = week1.getByDayEntries().get(3);
        assertEquals(week1Day3.size(), 3);
        assertDay(week1Day3.getFirst(), "2022-03-04", 0, false);
        assertDay(week1Day3.get(1), "2022-03-04", 0, false);
        assertDay(week1Day3.get(2), "2022-03-04", 0, false);
        
        List<EventStreamDay> week1Day4 = week1.getByDayEntries().get(4);
        assertEquals(week1Day4.size(), 3);
        assertDay(week1Day4.getFirst(), "2022-03-05", 0, false);
        assertDay(week1Day4.get(1), "2022-03-05", 0, false);
        assertDay(week1Day4.get(2), "2022-03-05", 0, false);
        
        List<EventStreamDay> week1Day5 = week1.getByDayEntries().get(5);
        assertEquals(week1Day5.size(), 3);
        assertDay(week1Day5.getFirst(), "2022-03-06", 0, false);
        assertDay(week1Day5.get(1), "2022-03-06", 0, false);
        assertDay(week1Day5.get(2), "2022-03-06", 0, false);
        
        List<EventStreamDay> week1Day6 = week1.getByDayEntries().get(6);
        assertEquals(week1Day6.size(), 3);
        assertDay(week1Day6.getFirst(), "2022-03-07", 0, false);
        assertDay(week1Day6.get(1), "2022-03-07", 0, false);
        assertDay(week1Day6.get(2), "2022-03-07", 0, false);
    }        
    
    @Test
    public void getNextActivity() throws Exception { 
        List<StudyActivityEvent> events = createEvents();
        
        StudyActivityEvent event = new StudyActivityEvent.Builder()
                .withEventId("custom:event1")
                .withTimestamp(DateTime.parse("2022-05-01T16:23:15.999-08:00"))
                .withObjectType(ActivityEventObjectType.CUSTOM)
                .build();
        events = BridgeUtils.addToList(events, event);
        
        AdherenceState state = createAdherenceState()
                .withNow(DateTime.parse("2022-04-15T00:00:00.000-08:00"))
                .withEvents(events).build();
        Schedule2 schedule = createSchedule();
    
        StudyAdherenceReport report = INSTANCE.generate(state, schedule);
        
        assertEquals(report.getNextActivity().getSessionGuid(), "session5");
        assertEquals(report.getNextActivity().getSessionName(), "Supplemental Survey");
        assertEquals(report.getNextActivity().getWeekInStudy(), Integer.valueOf(9));
        assertEquals(report.getNextActivity().getStartDate().toString(), "2022-05-01");
        
        // This is the day before (and in the same week) as the next activity, so nextActivity
        // should be null in this case.
        state = createAdherenceState().withNow(DateTime.parse("2022-04-30T00:00:00.000-08:00"))
                .withEvents(events).build();
        report = INSTANCE.generate(state, schedule);
        assertNull(report.getNextActivity());
    }    
    
    @Test
    public void createWeekReport_carryOverDay() throws Exception {
        // This date is in week 7, and there is no week 7. So we create that week
        Schedule2 schedule = createSchedule();
        schedule.setDuration(Period.parse("P8W"));
        schedule.getSessions().getFirst().getTimeWindows().getFirst().setExpiration(null);
        List<TimelineMetadata> meta = Scheduler.INSTANCE.calculateTimeline(schedule).getMetadata();
        
        AdherenceState.Builder builder = createAdherenceState();
        builder.withMetadata(meta);
        builder.withNow(DateTime.parse("2022-04-15T12:00:00.000-08:00"));
        StudyAdherenceReport report = INSTANCE.generate(builder.build(), schedule);
        
        StudyReportWeek weekReport = report.getWeekReport();
        
        assertEquals(weekReport.getWeekInStudy(), 7);
        assertEquals(weekReport.getStartDate(), LocalDate.parse("2022-04-12"));
        assertEquals(weekReport.getAdherencePercent(), Integer.valueOf(0));
        assertEquals(weekReport.getRows().size(), 1);
        
        WeeklyAdherenceReportRow row = weekReport.getRows().getFirst();
        assertEquals(row.getLabel(), "Initial Survey / Week 7"); // Notice: week 7, not week 1
        assertEquals(row.getSearchableLabel(), ":Initial Survey:Week 7:"); // Notice: week 7, not week 1
        assertEquals(row.getSessionGuid(), "initialSurveyGuid");
        assertEquals(row.getStartEventId(), "timeline_retrieved");
        assertEquals(row.getSessionName(), "Initial Survey");
        assertEquals(row.getWeekInStudy(), Integer.valueOf(7));
        
        EventStreamDay carryOverDay = weekReport.getByDayEntries().get(0).getFirst();
        assertEquals(carryOverDay.getSessionGuid(), "initialSurveyGuid");
        assertEquals(carryOverDay.getStartEventId(), "timeline_retrieved");
        // note, this wasn't changed to the day 0 date, and it shouldn't be
        assertEquals(carryOverDay.getStartDate(), LocalDate.parse("2022-03-02"));
        assertFalse(carryOverDay.isToday());
        
        assertEquals(carryOverDay.getTimeWindows().size(), 1);
        assertEquals(carryOverDay.getTimeWindows().getFirst().getSessionInstanceGuid(), "pqVRM8cV-buumqQvUGwRsQ");
        assertEquals(carryOverDay.getTimeWindows().getFirst().getTimeWindowGuid(), "win1");
        assertEquals(carryOverDay.getTimeWindows().getFirst().getState(), SessionCompletionState.UNSTARTED);
        assertEquals(carryOverDay.getTimeWindows().getFirst().getEndDate(), LocalDate.parse("2022-04-25"));
    }    
    
    @Test
    public void createWeekReport_carryOverDayKeepsOriginalDayAndTodayFlag() throws Exception {
        Schedule2 schedule = createSchedule();
        schedule.setDuration(Period.parse("P8W"));
        schedule.getSessions().getFirst().getTimeWindows().getFirst().setExpiration(null);
        List<TimelineMetadata> meta = Scheduler.INSTANCE.calculateTimeline(schedule).getMetadata();
        
        AdherenceState.Builder builder = createAdherenceState();
        builder.withMetadata(meta);
        builder.withNow(DateTime.parse("2022-04-12T12:00:00.000-08:00"));
        
        StudyAdherenceReport report = INSTANCE.generate(builder.build(), schedule);
        
        StudyReportWeek weekReport = report.getWeekReport();
        
        EventStreamDay carryOverDay = weekReport.getByDayEntries().get(0).getFirst();
        assertEquals(carryOverDay.getSessionGuid(), "initialSurveyGuid");
        assertEquals(carryOverDay.getStartEventId(), "timeline_retrieved");
        // note, this wasn't changed to the day 0 date, and it shouldn't be, but
        // we do set the today flag for display purposes
        assertEquals(carryOverDay.getStartDate(), LocalDate.parse("2022-03-02"));
        assertTrue(carryOverDay.isToday());
        
        assertEquals(carryOverDay.getTimeWindows().size(), 1);
        assertEquals(carryOverDay.getTimeWindows().getFirst().getSessionInstanceGuid(), "pqVRM8cV-buumqQvUGwRsQ");
        assertEquals(carryOverDay.getTimeWindows().getFirst().getTimeWindowGuid(), "win1");
        assertEquals(carryOverDay.getTimeWindows().getFirst().getState(), SessionCompletionState.UNSTARTED);
        assertEquals(carryOverDay.getTimeWindows().getFirst().getEndDate(), LocalDate.parse("2022-04-25"));
    }
    
    private void assertDay(EventStreamDay day, String date, int windowEntries, boolean isToday) {
        assertEquals(day.getStartDate(), LocalDate.parse(date));
        assertEquals(day.getTimeWindows().size(), windowEntries);
        assertEquals(day.isToday(), isToday);
    }
    
    private void assertTimeWindow(EventStreamWindow window, String sessionInstanceGuid, String timeWindowGuid,
            SessionCompletionState state, String endDate) {
        assertEquals(window.getSessionInstanceGuid(), sessionInstanceGuid);
        assertEquals(window.getTimeWindowGuid(), timeWindowGuid);
        assertEquals(window.getState(), state);
        assertEquals(window.getEndDate(), LocalDate.parse(endDate));
    }
    
    @Test
    public void rowsSortedByBurstIdAndThenLabel() throws Exception {
        StudyAdherenceReport report = createReport();
        
        List<StudyReportWeek> weeks = ImmutableList.copyOf(report.getWeeks());
        List<WeeklyAdherenceReportRow> rows = weeks.getFirst().getRows();
        
        assertEquals(rows.getFirst().getLabel(), "Baseline Tapping Test / Week 1");
        assertEquals(rows.getFirst().getSessionName(), "Baseline Tapping Test");
        assertEquals(rows.get(1).getLabel(), "Initial Survey / Week 1");
        assertEquals(rows.get(1).getSessionName(), "Initial Survey");
        
        // elements are in the correct order:
        assertEquals(weeks.getFirst().getByDayEntries().get(1).get(1).getSessionGuid(), "initialSurveyGuid");
        assertEquals(weeks.getFirst().getByDayEntries().get(2).getFirst().getSessionGuid(), "baselineGuid");
        
        // burst comes first here despite alphabetizing
        rows = weeks.get(3).getRows();
        assertEquals(rows.getFirst().getLabel(), "Study Burst 3 / Week 4 / Study Burst Tapping Test");
        assertEquals(rows.getFirst().getSessionName(), "Study Burst Tapping Test");
        assertEquals(rows.get(1).getLabel(), "Final Survey / Week 4");
        assertEquals(rows.get(1).getSessionName(), "Final Survey");
        
        assertEquals(weeks.get(3).getByDayEntries().get(0).getFirst().getSessionGuid(), "burstTappingGuid");
        assertEquals(weeks.get(3).getByDayEntries().get(3).get(1).getSessionGuid(), "finalSurveyGuid");
    }
    
    @Test
    public void todaySet() throws Exception {
        AdherenceState state = createAdherenceState().build();
        Schedule2 schedule = createSchedule();
        StudyAdherenceReport report = INSTANCE.generate(state, schedule);
        
        for (StudyReportWeek week : report.getWeeks()) {
            for (List<EventStreamDay> days : week.getByDayEntries().values()) {
                for (EventStreamDay oneDay : days) {
                    if (oneDay.isToday()) {
                        assertEquals(oneDay.getStartDate().toString(), "2022-03-15");
                    } else if (oneDay.getStartDate() != null) {
                        assertNotEquals(oneDay.getStartDate().toString(), "2022-03-15");
                    }
                }
            }
        }
    }
    
    @Test
    public void timestampAfterSchedule() throws Exception {
        AdherenceState state = createAdherenceState()
                .withNow(DateTime.parse("2022-05-01T00:00:00.000-08:00")).build();
        Schedule2 schedule = createSchedule();
        StudyAdherenceReport report = INSTANCE.generate(state, schedule);
        for (StudyReportWeek week : report.getWeeks()) {
            for (List<EventStreamDay> days : week.getByDayEntries().values()) {
                for (EventStreamDay oneDay : days) {
                    for (EventStreamWindow win : oneDay.getTimeWindows()) {
                        assertEquals(win.getState(), EXPIRED);
                    }
                }
            }
        }
    }
    
    @Test
    public void timestampBeforeSchedule() throws Exception {
        AdherenceState state = createAdherenceState()
                .withNow(DateTime.parse("2020-01-01T00:00:00.000-08:00")).build();
        Schedule2 schedule = createSchedule();
        StudyAdherenceReport report = INSTANCE.generate(state, schedule);
        
        for (StudyReportWeek week : report.getWeeks()) {
            for (List<EventStreamDay> days : week.getByDayEntries().values()) {
                for (EventStreamDay oneDay : days) {
                    for (EventStreamWindow win : oneDay.getTimeWindows()) {
                        assertEquals(win.getState(), NOT_YET_AVAILABLE);
                    }
                }
            }
        }
    }
    
    @Test
    public void timestampOnFallowWeek() throws Exception {
        // To make this work we have to create a fallow week, by delaying the
        // study burst for 4 weeks.
        Schedule2 schedule = createSchedule();
        schedule.getStudyBursts().getFirst().setDelay(Period.parse("P4W"));
        
        StudyActivityEvent e1 = new StudyActivityEvent.Builder()
                .withEventId("timeline_retrieved")
                .withTimestamp(DateTime.parse("2022-03-01T16:23:15.999-08:00"))
                .build();
        StudyActivityEvent e2 = new StudyActivityEvent.Builder()
                .withEventId("custom:event2")
                .withTimestamp(DateTime.parse("2022-03-10T16:23:15.999-08:00"))
                .build();
        
        // don't forget the study burst events! They are not calculated at the time
        // the schedule is calculated!
        StudyActivityEvent e3 = new StudyActivityEvent.Builder()
                .withEventId("study_burst:Study Burst:01")
                .withTimestamp(DateTime.parse("2022-03-29T16:23:15.999-08:00"))
                .build();
        StudyActivityEvent e4 = new StudyActivityEvent.Builder()
                .withEventId("study_burst:Study Burst:02")
                .withTimestamp(DateTime.parse("2022-04-05T16:23:15.999-08:00"))
                .build();
        StudyActivityEvent e5 = new StudyActivityEvent.Builder()
                .withEventId("study_burst:Study Burst:03")
                .withTimestamp(DateTime.parse("2022-04-12T16:23:15.999-08:00"))
                .build();
        
        Timeline timeline = Scheduler.INSTANCE.calculateTimeline(schedule);
        AdherenceState state = createAdherenceState()
                .withMetadata(timeline.getMetadata())
                .withEvents(ImmutableList.of(e1, e2, e3, e4, e5))
                .build();
        
        // this now has a gap and the 15th (now) falls into it
        StudyAdherenceReport report = INSTANCE.generate(state, schedule);
        
        List<StudyReportWeek> weeks = ImmutableList.copyOf(report.getWeeks());
        assertEquals(weeks.getFirst().getWeekInStudy(), 1);
        assertEquals(weeks.getFirst().getStartDate().toString(), "2022-03-01");
        assertEquals(weeks.get(1).getWeekInStudy(), 4);
        assertEquals(weeks.get(1).getStartDate().toString(), "2022-03-22");
        
        // Nothing is "now" because that's in the gap.
        for (StudyReportWeek week : report.getWeeks()) {
            for (List<EventStreamDay> days : week.getByDayEntries().values()) {
                for (EventStreamDay oneDay : days) {
                    assertFalse(oneDay.isToday());
                }
            }
        }
        assertEquals(report.getNextActivity().getSessionGuid(), "finalSurveyGuid");
        assertEquals(report.getNextActivity().getSessionName(), "Final Survey");
        assertEquals(report.getNextActivity().getWeekInStudy(), Integer.valueOf(4));
        assertEquals(report.getNextActivity().getStartDate().toString(), "2022-03-25");
    }
    
    @Test
    public void createWeekReport_withCurrentWeek() throws Exception {
        StudyAdherenceReport report = createReport();
        
        StudyReportWeek currentWeek = report.getWeeks().get(2);
        StudyReportWeek weekReport = report.getWeekReport();
        
        // This particular week report has not been adjusted for activities in prior weeks,
        // so comparison is simple
        assertEquals(weekReport.getWeekInStudy(), 3);
        assertEquals(weekReport.getStartDate(), LocalDate.parse("2022-03-15"));
        assertEquals(weekReport.getAdherencePercent(), Integer.valueOf(0));
        assertEquals(weekReport.getSearchableLabels(), 
                ImmutableSet.of(":Study Burst:Study Burst 2:Week 3:Study Burst Tapping Test:"));
        assertEquals(weekReport.getRows(), currentWeek.getRows());
        
        assertTrue(weekReport.getByDayEntries().get(1).getFirst().getTimeWindows().isEmpty());
        assertTrue(weekReport.getByDayEntries().get(2).getFirst().getTimeWindows().isEmpty());
        assertTrue(weekReport.getByDayEntries().get(3).getFirst().getTimeWindows().isEmpty());
        assertTrue(weekReport.getByDayEntries().get(4).getFirst().getTimeWindows().isEmpty());
        assertTrue(weekReport.getByDayEntries().get(5).getFirst().getTimeWindows().isEmpty());
        assertTrue(weekReport.getByDayEntries().get(6).getFirst().getTimeWindows().isEmpty());
        assertEquals(weekReport.getByDayEntries().get(0).getFirst().getSessionGuid(), "burstTappingGuid");
    }
    
    @Test
    public void createWeekReport_studyStartDate() throws Exception {
        // This test is basically identical to createWeekReport_withCurrentWeek
        // studyStartDate is selected in that test as well.
        StudyAdherenceReport report = createReport();
        
        StudyReportWeek currentWeek = report.getWeeks().get(2);
        StudyReportWeek weekReport = report.getWeekReport();
        assertEquals(weekReport.getWeekInStudy(), currentWeek.getWeekInStudy());
        assertEquals(weekReport.getStartDate(), currentWeek.getStartDate());
        assertEquals(weekReport.getAdherencePercent(), currentWeek.getAdherencePercent());
        assertEquals(weekReport.getSearchableLabels(), currentWeek.getSearchableLabels());
        assertEquals(weekReport.getRows(), currentWeek.getRows());
    }
    
    @Test
    public void createWeekReport_noCurrentWeekNoStudyStartDate() throws Exception {
        Schedule2 schedule = createSchedule();
        schedule.setDuration(Period.parse("P8W"));
        schedule.getSessions().getFirst().getTimeWindows().getFirst().setExpiration(null);
        List<TimelineMetadata> meta = Scheduler.INSTANCE.calculateTimeline(schedule).getMetadata();
        
        AdherenceState.Builder builder = createAdherenceState();
        builder.withStudyStartEventId(null);
        builder.withEvents(ImmutableList.of()); // no “earliest event” to fall back on 
        builder.withMetadata(meta);
        builder.withNow(DateTime.parse("2022-04-15T12:00:00.000-08:00"));
        
        StudyAdherenceReport report = INSTANCE.generate(builder.build(), schedule);
        
        // Lacking any relevant events, we just show "Week 1" with any relevant tasks. This should
        // be reported as "unstarted" which is usually shown differently in UIs instead of this week one 
        // information. We might choose to do something differently here.
        StudyReportWeek weekReport = report.getWeekReport();
        assertEquals(weekReport.getWeekInStudy(), 1);
        assertEquals(weekReport.getStartDate(), LocalDate.parse("2022-04-15"));
        assertTrue(weekReport.getByDayEntries().get(0).isEmpty());
        assertTrue(weekReport.getByDayEntries().get(1).isEmpty());
        assertTrue(weekReport.getByDayEntries().get(2).isEmpty());
        assertTrue(weekReport.getByDayEntries().get(3).isEmpty());
        assertTrue(weekReport.getByDayEntries().get(4).isEmpty());
        assertTrue(weekReport.getByDayEntries().get(5).isEmpty());
        assertTrue(weekReport.getByDayEntries().get(6).isEmpty());
        assertTrue(weekReport.getRows().isEmpty());
    }

    @Test
    public void createWeekReport_noCarryOverBecauseWindowsAreCompleted() throws Exception {
        Schedule2 schedule = createSchedule();
        schedule.setDuration(Period.parse("P8W"));
        schedule.getSessions().getFirst().getTimeWindows().getFirst().setExpiration(null);
        List<TimelineMetadata> meta = Scheduler.INSTANCE.calculateTimeline(schedule).getMetadata();
        
        AdherenceState.Builder builder = createAdherenceState();
        builder.withMetadata(meta);
        builder.withAdherenceRecords(createAdherenceRecords());
        builder.withNow(DateTime.parse("2022-04-15T12:00:00.000-08:00"));
        
        StudyAdherenceReport report = INSTANCE.generate(builder.build(), schedule);
        
        StudyReportWeek weekReport = report.getWeekReport();
        assertTrue(weekReport.getByDayEntries().get(0).isEmpty());
        assertTrue(weekReport.getByDayEntries().get(1).isEmpty());
        assertTrue(weekReport.getByDayEntries().get(2).isEmpty());
        assertTrue(weekReport.getByDayEntries().get(3).isEmpty());
        assertTrue(weekReport.getByDayEntries().get(4).isEmpty());
        assertTrue(weekReport.getByDayEntries().get(5).isEmpty());
        assertTrue(weekReport.getByDayEntries().get(6).isEmpty());
        assertTrue(weekReport.getRows().isEmpty());
    }
    
    @Test
    public void createWeekReport_carryOver() throws Exception {
        // This date is in week 7, and there is no week 7. So we create that week
        
        Schedule2 schedule = createSchedule();
        schedule.setDuration(Period.parse("P8W"));
        schedule.getSessions().getFirst().getTimeWindows().getFirst().setExpiration(null);
        List<TimelineMetadata> meta = Scheduler.INSTANCE.calculateTimeline(schedule).getMetadata();
        
        AdherenceState.Builder builder = createAdherenceState();
        builder.withMetadata(meta);
        builder.withNow(DateTime.parse("2022-04-15T12:00:00.000-08:00"));
        
        StudyAdherenceReport report = INSTANCE.generate(builder.build(), schedule);
        
        StudyReportWeek weekReport = report.getWeekReport();
        
        assertEquals(weekReport.getWeekInStudy(), 7);
        assertEquals(weekReport.getStartDate(), LocalDate.parse("2022-04-12"));
        assertEquals(weekReport.getAdherencePercent(), Integer.valueOf(0));
        assertEquals(weekReport.getRows().size(), 1);
        
        WeeklyAdherenceReportRow row = weekReport.getRows().getFirst();
        assertEquals(row.getLabel(), "Initial Survey / Week 7"); // Notice: week 7, not week 1
        assertEquals(row.getSearchableLabel(), ":Initial Survey:Week 7:"); // Notice: week 7, not week 1
        assertEquals(row.getSessionGuid(), "initialSurveyGuid");
        assertEquals(row.getStartEventId(), "timeline_retrieved");
        assertEquals(row.getSessionName(), "Initial Survey");
        assertEquals(row.getWeekInStudy(), Integer.valueOf(7));
        
        assertTrue(weekReport.getByDayEntries().get(1).getFirst().getTimeWindows().isEmpty());
        assertTrue(weekReport.getByDayEntries().get(2).getFirst().getTimeWindows().isEmpty());
        assertTrue(weekReport.getByDayEntries().get(3).getFirst().getTimeWindows().isEmpty());
        assertTrue(weekReport.getByDayEntries().get(4).getFirst().getTimeWindows().isEmpty());
        assertTrue(weekReport.getByDayEntries().get(5).getFirst().getTimeWindows().isEmpty());
        assertTrue(weekReport.getByDayEntries().get(6).getFirst().getTimeWindows().isEmpty());
        
        EventStreamDay carryOverDay = weekReport.getByDayEntries().get(0).getFirst();
        assertEquals(carryOverDay.getSessionGuid(), "initialSurveyGuid");
        assertEquals(carryOverDay.getStartEventId(), "timeline_retrieved");
        // note, this wasn't changed to the day 0 date, and it shouldn't be
        assertEquals(carryOverDay.getStartDate(), LocalDate.parse("2022-03-02"));
        
        assertEquals(carryOverDay.getTimeWindows().size(), 1);
        assertEquals(carryOverDay.getTimeWindows().getFirst().getSessionInstanceGuid(), "pqVRM8cV-buumqQvUGwRsQ");
        assertEquals(carryOverDay.getTimeWindows().getFirst().getTimeWindowGuid(), "win1");
        assertEquals(carryOverDay.getTimeWindows().getFirst().getState(), SessionCompletionState.UNSTARTED);
        assertEquals(carryOverDay.getTimeWindows().getFirst().getEndDate(), LocalDate.parse("2022-04-25"));
    }
    
    @Test
    public void createWeekReport_adherencePercentCorrectWithCarryOver() throws Exception {
        // Set this up in the future with all the adherence records, but remove the 
        // initial survey adherence record (you have to do this so it is carried forward).
        // This should change the adherence percent accordingly.
        List<AdherenceRecord> records = createAdherenceRecords();

        Schedule2 schedule = createSchedule();
        schedule.setDuration(Period.parse("P8W"));
        schedule.getSessions().getFirst().getTimeWindows().getFirst().setExpiration(null);
        List<TimelineMetadata> meta = Scheduler.INSTANCE.calculateTimeline(schedule).getMetadata();
        
        AdherenceState.Builder builder = createAdherenceState();
        builder.withMetadata(meta);
        // Remove initial survey
        builder.withAdherenceRecords(records.subList(1, records.size()));
        
        StudyAdherenceReport report = INSTANCE.generate(builder.build(), schedule);
        // If the initial survey is not done and not carried over (we remove the AR for it),
        // adherence is 25%.
        assertEquals(report.getAdherencePercent(), Integer.valueOf(25));
    }
    
    @Test
    public void createWeekReport_adherencePercentCorrectWithoutCarryOver() throws Exception {
        // Set this up in the future with all the adherence records, but remove the 
        // initial survey adherence record (you have to do this so it is carried forward).
        // This should change the adherence percent accordingly.
        List<AdherenceRecord> records = createAdherenceRecords();

        Schedule2 schedule = createSchedule();
        schedule.setDuration(Period.parse("P8W"));
        schedule.getSessions().getFirst().getTimeWindows().getFirst().setExpiration(null);
        List<TimelineMetadata> meta = Scheduler.INSTANCE.calculateTimeline(schedule).getMetadata();
        
        AdherenceState.Builder builder = createAdherenceState();
        builder.withMetadata(meta);
        // Remove initial survey
        builder.withAdherenceRecords(records);
        
        StudyAdherenceReport report = INSTANCE.generate(builder.build(), schedule);
        // If the initial survey is declared as done by including the adherence recored for it,
        // adherence is 50%.
        assertEquals(report.getAdherencePercent(), Integer.valueOf(50));
    }
    
    @Test
    public void createWeekReport_unusedFieldsAreCleared() throws Exception {
        LocalDate today = LocalDate.parse("2022-03-15");
        
        StudyAdherenceReport report = createReport();
        for (StudyReportWeek oneWeek : report.getWeeks()) {
            oneWeek.visitDays((day, i) -> {
                assertNull(day.getStudyBurstId());
                assertNull(day.getStudyBurstNum());
                assertNull(day.getSessionName());
                assertNull(day.getWeek());
                assertEquals(day.isToday(), today.isEqual(day.getStartDate()));
                assertNull(day.getStartDay());
                day.getTimeWindows().forEach(win -> assertNull(win.getEndDay()));
            });
        }
        StudyReportWeek oneWeek = report.getWeekReport();
        oneWeek.visitDays((day, i) -> {
            assertNull(day.getStudyBurstId());
            assertNull(day.getStudyBurstNum());
            assertNull(day.getSessionName());
            assertNull(day.getWeek());
            assertEquals(day.isToday(), today.isEqual(day.getStartDate()));
            assertNull(day.getStartDay());
            day.getTimeWindows().forEach(win -> assertNull(win.getEndDay()));
        });
    }
    
    @Test
    public void dayAndDateRangesAdjustedToStudyStartTimestamp() throws Exception {
        StudyAdherenceReport report = createReport();
        
        // The timeline_retrieved start date is 3/1. We use 3/1 and not 3/2, the 
        // first date there is an activity.
        assertEquals(report.getDateRange().getStartDate().toString(), "2022-03-01");
        // the final survey lands on the 24th, and lasts for three days:
        assertEquals(report.getDateRange().getEndDate().toString(), "2022-03-27");
    }
    
    @Test
    public void adherenceNotCalculatedForFutureWeeks() throws Exception {
        AdherenceState state = new AdherenceState.Builder()
                .withStudyStartEventId("timeline_retrieved")
                .withMetadata(createTimelineMetadata())
                .withEvents(createEvents())
                .withNow(DateTime.parse("2022-03-7T01:00:00.000-08:00")).build();
        Schedule2 schedule = createSchedule();
        StudyAdherenceReport report = INSTANCE.generate(state, schedule);
        
        List<StudyReportWeek> weeks = ImmutableList.copyOf(report.getWeeks());
        assertEquals(weeks.getFirst().getAdherencePercent(), Integer.valueOf(0));
        assertNull(weeks.get(1).getAdherencePercent());
        assertNull(weeks.get(2).getAdherencePercent());
        assertNull(weeks.get(3).getAdherencePercent());
    }
    
    @Test
    public void dateRangeErrorDefaultsToStreamDateRange() {
        AssessmentReference ref1 = new AssessmentReference();
        ref1.setGuid("survey");
        ref1.setAppId(TEST_APP_ID);
        ref1.setIdentifier("survey");
        
        Schedule2 schedule = new Schedule2();
        
        TimeWindow win1 = new TimeWindow();
        win1.setGuid("win1");
        win1.setStartTime(LocalTime.parse("00:00"));
        win1.setExpiration(Period.parse("P2D"));
        
        Session s1 = new Session();
        s1.setGuid("s1");
        s1.setAssessments(ImmutableList.of(ref1));
        s1.setStartEventIds(ImmutableList.of("custom:event1"));
        s1.setTimeWindows(ImmutableList.of(win1));
        s1.setGuid("initialSurveyGuid");
        s1.setName("Initial Survey");
        s1.setPerformanceOrder(SEQUENTIAL);
        
        schedule.setSessions(ImmutableList.of(s1));
        schedule.setAppId(TEST_APP_ID);
        schedule.setGuid(SCHEDULE_GUID);
        schedule.setName("Test Schedule");
        schedule.setOwnerId("sage-bionetworks");
        schedule.setDuration(Period.parse("P4W"));
        schedule.setCreatedOn(CREATED_ON);
        schedule.setModifiedOn(MODIFIED_ON);
        
        Timeline timeline = Scheduler.INSTANCE.calculateTimeline(schedule);
        List<TimelineMetadata> metadata = timeline.getMetadata();
        
        StudyActivityEvent e1 = new StudyActivityEvent.Builder()
                .withEventId("custom:event1")
                .withTimestamp(DateTime.parse("2022-02-08T12:23:15.999-08:00"))
                .withObjectType(ActivityEventObjectType.CUSTOM)
                .build();
        StudyActivityEvent e2 = new StudyActivityEvent.Builder()
                .withEventId("custom:event2")
                .withTimestamp(DateTime.parse("2022-03-08T12:23:15.999-08:00"))
                .withObjectType(ActivityEventObjectType.CUSTOM)
                .build();
        List<StudyActivityEvent> events = ImmutableList.of(e1, e2);
        
        AdherenceState state = new AdherenceState.Builder()
                .withStudyStartEventId("custom:event2")
                .withMetadata(metadata)
                .withEvents(events)
                .withClientTimeZone("America/Los_Angeles")
                .withNow(NOW).build();

        // This used to throw an exception but now returns the full date range of the dates.
        StudyAdherenceReport report = INSTANCE.generate(state, schedule);
        assertEquals(report.getDateRange().getStartDate(), LocalDate.parse("2022-02-08"));
        assertEquals(report.getDateRange().getEndDate(), LocalDate.parse("2022-03-08"));
    }
    
    // BRIDGE-3287: rows were being duplicated in the weekly report with carry-overs
    @Test
    public void rowsAreNotDuplicated() {
        AssessmentReference ref = new AssessmentReference();
        ref.setIdentifier("id");
        ref.setGuid(ASSESSMENT_1_GUID);
        ref.setRevision(1);
        
        TimeWindow win1 = new TimeWindow();
        win1.setGuid("win1Guid");
        win1.setStartTime(LocalTime.parse("08:00"));

        Session session1 = new Session();
        session1.setName("session1");
        session1.setGuid("session1");
        session1.setAssessments(ImmutableList.of(ref));
        session1.setTimeWindows(ImmutableList.of(win1));
        session1.setStartEventIds(ImmutableList.of("timeline_retrieved"));
        
        TimeWindow win2 = new TimeWindow();
        win2.setGuid("win2Guid");
        win2.setStartTime(LocalTime.parse("08:00"));

        Session session2 = new Session();
        session2.setName("session2");
        session2.setGuid("session2");
        session2.setAssessments(ImmutableList.of(ref));
        session2.setTimeWindows(ImmutableList.of(win2));
        session2.setStartEventIds(ImmutableList.of("timeline_retrieved"));
        
        TimeWindow win3 = new TimeWindow();
        win3.setGuid("win3Guid");
        win3.setStartTime(LocalTime.parse("08:00"));
        win3.setExpiration(Period.parse("PT3H"));

        Session session3 = new Session();
        session3.setName("session3");
        session3.setGuid("session3");
        session3.setInterval(Period.parse("P1W"));
        session3.setAssessments(ImmutableList.of(ref));
        session3.setTimeWindows(ImmutableList.of(win3));
        session3.setStartEventIds(ImmutableList.of("timeline_retrieved"));
        
        Schedule2 schedule = new Schedule2();
        schedule.setGuid("scheduleGuid");
        schedule.setDuration(Period.parse("P3W"));
        schedule.setSessions(ImmutableList.of(session1, session2, session3));
        
        Timeline timeline = Scheduler.INSTANCE.calculateTimeline(schedule);
        List<TimelineMetadata> metadata = timeline.getMetadata();
        
        // Events
        StudyActivityEvent event = new StudyActivityEvent.Builder()
                .withEventId("timeline_retrieved")
                .withTimestamp(DateTime.parse("2022-04-02T04:10:38.593Z")).build();
        
        DateTime now = DateTime.parse("2022-04-20T18:27:30.367Z");
        
        AdherenceState state = new AdherenceState.Builder()
                .withEvents(ImmutableList.of(event))
                .withMetadata(metadata)
                .withNow(now).build();
        
        StudyAdherenceReport report = StudyAdherenceReportGenerator.INSTANCE.generate(state, schedule);
        
        StudyReportWeek weeklyReport = report.getWeekReport();
        
        List<String> rowLabels = weeklyReport.getRows().stream()
                .map(row -> row.getSearchableLabel()).collect(Collectors.toList());
        assertEquals(rowLabels, ImmutableList.of(":session1:Week 3:", ":session2:Week 3:", ":session3:Week 3:"));
    }
    
    // BRIDGE-3288: As reported
    @Test
    public void negativeDaysDoNotBreakSchedule() throws JsonProcessingException {
        AssessmentReference ref1 = new AssessmentReference();
        ref1.setGuid("survey");
        ref1.setAppId(TEST_APP_ID);
        ref1.setIdentifier("survey");
        
        TimeWindow win1 = new TimeWindow();
        win1.setGuid(TestConstants.SESSION_WINDOW_GUID_1);
        win1.setStartTime(LocalTime.parse("08:00"));
        win1.setExpiration(Period.parse("PT24H"));
        
        Session session1 = new Session();
        session1.setGuid(SESSION_GUID_1);
        session1.setName("session1");
        session1.setOccurrences(1);
        session1.setAssessments(ImmutableList.of(ref1));
        session1.setTimeWindows(ImmutableList.of(win1));
        session1.setStartEventIds(ImmutableList.of("custom:First Clinic visit"));
        
        TimeWindow win2 = new TimeWindow();
        win2.setGuid(SESSION_WINDOW_GUID_2);
        win2.setStartTime(LocalTime.parse("08:30"));
        win2.setExpiration(Period.parse("PT2H"));
        
        TimeWindow win3 = new TimeWindow();
        win3.setGuid(TestConstants.SESSION_WINDOW_GUID_3);
        win3.setStartTime(LocalTime.parse("20:30"));
        win3.setExpiration(Period.parse("PT2H"));

        Session session2 = new Session();
        session2.setGuid(SESSION_GUID_2);
        session2.setName("session2");
        session2.setAssessments(ImmutableList.of(ref1));
        session2.setDelay(Period.parse("P2D"));
        session2.setOccurrences(14);
        session2.setInterval(Period.parse("P1D"));
        session2.setAssessments(ImmutableList.of(ref1));
        session2.setTimeWindows(ImmutableList.of(win2, win3));
        session2.setStudyBurstIds(ImmutableList.of("custom_RemoteActivities_burst"));

        TimeWindow win4 = new TimeWindow();
        win4.setGuid(SESSION_GUID_4);
        win4.setStartTime(LocalTime.parse("08:00"));
        win4.setExpiration(Period.parse("P20D"));
        
        Session session3 = new Session();
        session3.setGuid(SESSION_GUID_3);
        session3.setName("session3");
        session3.setDelay(Period.parse("P4W"));
        session3.setOccurrences(10);
        session3.setInterval(Period.parse("P4W"));
        session3.setAssessments(ImmutableList.of(ref1));
        session3.setTimeWindows(ImmutableList.of(win4));
        session3.setStartEventIds(ImmutableList.of("custom:First Clinic visit"));
        
        StudyBurst burst = new StudyBurst();
        burst.setIdentifier("custom_RemoteActivities_burst");
        burst.setOriginEventId("custom:RemoteActivities");
        burst.setInterval(Period.parse("P2W"));
        burst.setOccurrences(3);
        burst.setUpdateType(MUTABLE);
        
        Schedule2 schedule = new Schedule2();
        schedule.setGuid(SCHEDULE_GUID);
        schedule.setDuration(Period.parse("P2W"));
        schedule.setStudyBursts(ImmutableList.of(burst));
        schedule.setSessions(ImmutableList.of(session1, session2, session3));
        
        Timeline timeline = Scheduler.INSTANCE.calculateTimeline(schedule);
        
        StudyActivityEvent event0 = new StudyActivityEvent.Builder()
                .withEventId("custom:First Clinic visit")
                .withTimestamp(DateTime.parse("2022-04-05T19:05:43.000Z")).build();
        StudyActivityEvent event1 = new StudyActivityEvent.Builder()
                .withEventId("custom:RemoteActivities")
                .withTimestamp(DateTime.parse("2022-04-01T19:05:46.000Z")).build();
        StudyActivityEvent event2 = new StudyActivityEvent.Builder()
                .withEventId("study_burst:custom_RemoteActivities_burst:01")
                .withTimestamp(DateTime.parse("2022-04-01T19:05:46.000Z")).build();
        StudyActivityEvent event3 = new StudyActivityEvent.Builder()
                .withEventId("study_burst:custom_RemoteActivities_burst:02")
                .withTimestamp(DateTime.parse("2022-04-15T19:05:46.000Z")).build();
        StudyActivityEvent event4 = new StudyActivityEvent.Builder()
                .withEventId("study_burst:custom_RemoteActivities_burst:03")
                .withTimestamp(DateTime.parse("2022-04-29T19:05:46.000Z")).build();
        
        AdherenceState state = new AdherenceState.Builder()
                .withMetadata(timeline.getMetadata())
                .withEvents(ImmutableList.of(event0, event1, event2, event3, event4))
                .withNow(DateTime.parse("2022-04-08T12:53:31.385-08:00"))
                .withStudyStartEventId("custom:First Clinic visit")
                .build();
            
        StudyAdherenceReport report = StudyAdherenceReportGenerator.INSTANCE.generate(state, schedule);
        StudyReportWeek weeklyReport = report.getWeekReport();
        
        assertStartDate(weeklyReport, 0, LocalDate.parse("2022-04-08"));
        assertStartDate(weeklyReport, 1, LocalDate.parse("2022-04-09"));
        assertStartDate(weeklyReport, 2, LocalDate.parse("2022-04-10"));
        assertStartDate(weeklyReport, 3, LocalDate.parse("2022-04-11"));
        assertStartDate(weeklyReport, 4, LocalDate.parse("2022-04-12"));
        assertStartDate(weeklyReport, 5, LocalDate.parse("2022-04-13"));
        assertStartDate(weeklyReport, 6, LocalDate.parse("2022-04-14"));
    }
    
    // BRIDGE-3288
    @Test
    public void negativeDaysDoNotBreakScheduleSimplified() throws JsonProcessingException {
        // This schedule won't pass validation, but I'm concerned there are “legal” ways
        // to break the schedule when there are overlaps and negative weeks.
        AssessmentReference ref1 = new AssessmentReference();
        ref1.setGuid("survey");
        ref1.setAppId(TEST_APP_ID);
        ref1.setIdentifier("survey");
        
        TimeWindow win1 = new TimeWindow();
        win1.setGuid(TestConstants.SESSION_WINDOW_GUID_1);
        win1.setStartTime(LocalTime.parse("08:00"));
        
        Session session1 = new Session();
        session1.setGuid(SESSION_GUID_1);
        session1.setName("session1");
        session1.setAssessments(ImmutableList.of(ref1));
        session1.setInterval(Period.parse("P1W"));
        session1.setTimeWindows(ImmutableList.of(win1));
        session1.setStartEventIds(ImmutableList.of("custom:event1"));
        
        TimeWindow win2 = new TimeWindow();
        win2.setGuid(SESSION_WINDOW_GUID_2);
        win2.setStartTime(LocalTime.parse("08:30"));
        
        Session session2 = new Session();
        session2.setGuid(SESSION_GUID_2);
        session2.setName("session2");
        session2.setAssessments(ImmutableList.of(ref1));
        session2.setInterval(Period.parse("P1W"));
        session2.setTimeWindows(ImmutableList.of(win2));
        session2.setStartEventIds(ImmutableList.of("custom:event2"));

        Schedule2 schedule = new Schedule2();
        schedule.setGuid(SCHEDULE_GUID);
        schedule.setDuration(Period.parse("P2W"));
        schedule.setSessions(ImmutableList.of(session1, session2));
        
        Timeline timeline = Scheduler.INSTANCE.calculateTimeline(schedule);
        
        StudyActivityEvent event1 = new StudyActivityEvent.Builder()
                .withEventId("custom:event1")
                .withTimestamp(DateTime.parse("2022-04-05T12:00:00.000Z")).build();
        StudyActivityEvent event2 = new StudyActivityEvent.Builder()
                .withEventId("custom:event2")
                .withTimestamp(DateTime.parse("2022-04-20T12:00:00.000Z")).build();
        
        AdherenceState state = new AdherenceState.Builder()
                .withMetadata(timeline.getMetadata())
                .withEvents(ImmutableList.of(event1, event2))
                .withNow(DateTime.parse("2022-04-24T12:00:00.000Z"))
                .withStudyStartEventId("custom:event2")
                .build();
            
        StudyAdherenceReport report = StudyAdherenceReportGenerator.INSTANCE.generate(state, schedule);
        
        StudyReportWeek week = report.getWeeks().getFirst();
        assertEquals(week.getWeekInStudy(), -1);
        assertStartDate(week, 0, LocalDate.parse("2022-04-05"));
        assertStartDate(week, 1, LocalDate.parse("2022-04-06"));
        assertStartDate(week, 2, LocalDate.parse("2022-04-07"));
        assertStartDate(week, 3, LocalDate.parse("2022-04-08"));
        assertStartDate(week, 4, LocalDate.parse("2022-04-09"));
        assertStartDate(week, 5, LocalDate.parse("2022-04-10"));
        assertStartDate(week, 6, LocalDate.parse("2022-04-11"));
        
        week = report.getWeeks().get(1);
        assertEquals(week.getWeekInStudy(), 0);
        assertStartDate(week, 0, LocalDate.parse("2022-04-12"));
        assertStartDate(week, 1, LocalDate.parse("2022-04-13"));
        assertStartDate(week, 2, LocalDate.parse("2022-04-14"));
        assertStartDate(week, 3, LocalDate.parse("2022-04-15"));
        assertStartDate(week, 4, LocalDate.parse("2022-04-16"));
        assertStartDate(week, 5, LocalDate.parse("2022-04-17"));
        assertStartDate(week, 6, LocalDate.parse("2022-04-18"));

        week = report.getWeeks().get(2);
        assertEquals(week.getWeekInStudy(), 1);
        assertStartDate(week, 0, LocalDate.parse("2022-04-19"));
        assertStartDate(week, 1, LocalDate.parse("2022-04-20"));
        assertStartDate(week, 2, LocalDate.parse("2022-04-21"));
        assertStartDate(week, 3, LocalDate.parse("2022-04-22"));
        assertStartDate(week, 4, LocalDate.parse("2022-04-23"));
        assertStartDate(week, 5, LocalDate.parse("2022-04-24"));
        assertStartDate(week, 6, LocalDate.parse("2022-04-25"));

        week = report.getWeeks().get(3);
        assertEquals(week.getWeekInStudy(), 2);
        assertStartDate(week, 0, LocalDate.parse("2022-04-26"));
        assertStartDate(week, 1, LocalDate.parse("2022-04-27"));
        assertStartDate(week, 2, LocalDate.parse("2022-04-28"));
        assertStartDate(week, 3, LocalDate.parse("2022-04-29"));
        assertStartDate(week, 4, LocalDate.parse("2022-04-30"));
        assertStartDate(week, 5, LocalDate.parse("2022-05-01"));
        assertStartDate(week, 6, LocalDate.parse("2022-05-02"));
    }
    
    // BRIDGE-3288
    @Test
    public void negativeDaysDoNotBreakSchedule_oneDay() throws JsonProcessingException {
        // This schedule won't pass validation, but I'm concerned there are “legal” ways
        // to break the schedule when there are overlaps and negative weeks.
        AssessmentReference ref1 = new AssessmentReference();
        ref1.setGuid("survey");
        ref1.setAppId(TEST_APP_ID);
        ref1.setIdentifier("survey");
        
        TimeWindow win1 = new TimeWindow();
        win1.setGuid(TestConstants.SESSION_WINDOW_GUID_1);
        win1.setStartTime(LocalTime.parse("08:00"));
        
        Session session1 = new Session();
        session1.setGuid(SESSION_GUID_1);
        session1.setName("session1");
        session1.setAssessments(ImmutableList.of(ref1));
        session1.setInterval(Period.parse("P1W"));
        session1.setTimeWindows(ImmutableList.of(win1));
        session1.setStartEventIds(ImmutableList.of("custom:event1"));
        
        TimeWindow win2 = new TimeWindow();
        win2.setGuid(SESSION_WINDOW_GUID_2);
        win2.setStartTime(LocalTime.parse("08:30"));
        
        Session session2 = new Session();
        session2.setGuid(SESSION_GUID_2);
        session2.setName("session2");
        session2.setAssessments(ImmutableList.of(ref1));
        session2.setInterval(Period.parse("P1W"));
        session2.setTimeWindows(ImmutableList.of(win2));
        session2.setStartEventIds(ImmutableList.of("custom:event2"));

        Schedule2 schedule = new Schedule2();
        schedule.setGuid(SCHEDULE_GUID);
        schedule.setDuration(Period.parse("P2W"));
        schedule.setSessions(ImmutableList.of(session1, session2));
        
        Timeline timeline = Scheduler.INSTANCE.calculateTimeline(schedule);
        
        StudyActivityEvent event1 = new StudyActivityEvent.Builder()
                .withEventId("custom:event1")
                .withTimestamp(DateTime.parse("2022-04-19T12:00:00.000Z")).build();
        StudyActivityEvent event2 = new StudyActivityEvent.Builder()
                .withEventId("custom:event2")
                .withTimestamp(DateTime.parse("2022-04-27T12:00:00.000Z")).build();
        
        AdherenceState state = new AdherenceState.Builder()
                .withMetadata(timeline.getMetadata())
                .withEvents(ImmutableList.of(event1, event2))
                .withNow(DateTime.parse("2022-04-24T12:00:00.000Z"))
                .withStudyStartEventId("custom:event2")
                .build();
            
        StudyAdherenceReport report = StudyAdherenceReportGenerator.INSTANCE.generate(state, schedule);
        
        StudyReportWeek week = report.getWeeks().getFirst();
        assertEquals(week.getWeekInStudy(), 0);
        assertStartDate(week, 0, LocalDate.parse("2022-04-19"));
        assertStartDate(week, 6, LocalDate.parse("2022-04-25"));
        
        week = report.getWeeks().get(1);
        assertEquals(week.getWeekInStudy(), 1);
        assertStartDate(week, 0, LocalDate.parse("2022-04-26"));
        assertStartDate(week, 6, LocalDate.parse("2022-05-02"));

        week = report.getWeeks().get(2);
        assertEquals(week.getWeekInStudy(), 2);
        assertStartDate(week, 0, LocalDate.parse("2022-05-03"));
        assertStartDate(week, 6, LocalDate.parse("2022-05-09"));
    }
    
    private void assertStartDate(StudyReportWeek weeklyReport, int dayOfWeek, LocalDate startDate) {
        for (EventStreamDay day : weeklyReport.getByDayEntries().get(dayOfWeek)) {
            assertEquals(day.getStartDate(), startDate);
        }
    }
}
