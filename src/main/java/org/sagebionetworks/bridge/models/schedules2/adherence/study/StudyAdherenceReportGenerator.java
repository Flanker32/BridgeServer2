package org.sagebionetworks.bridge.models.schedules2.adherence.study;

import static java.util.stream.Collectors.toList;
import static org.sagebionetworks.bridge.BridgeUtils.isLocalDateInRange;
import static org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceUtils.calculateAdherencePercentage;
import static org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceUtils.calculateProgress;
import static org.sagebionetworks.bridge.models.schedules2.adherence.SessionCompletionState.UNKNOWN;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import org.joda.time.DateTime;
import org.joda.time.Days;
import org.joda.time.LocalDate;
import org.sagebionetworks.bridge.models.DateRange;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceState;
import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceUtils;
import org.sagebionetworks.bridge.models.schedules2.adherence.ParticipantStudyProgress;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStream;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamAdherenceReport;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamAdherenceReportGenerator;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamDay;
import org.sagebionetworks.bridge.models.schedules2.adherence.eventstream.EventStreamWindow;
import org.sagebionetworks.bridge.models.schedules2.adherence.weekly.NextActivity;
import org.sagebionetworks.bridge.models.schedules2.adherence.weekly.WeeklyAdherenceReportRow;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class StudyAdherenceReportGenerator {

    public static final StudyAdherenceReportGenerator INSTANCE = new StudyAdherenceReportGenerator();
    
    static final Comparator<String> STRING_COMPARATOR = Comparator.nullsLast((r1, r2) -> r1.compareToIgnoreCase(r2));

    /** Sort by label ignoring case, but study bursts come first. */
    static final Comparator<WeeklyAdherenceReportRow> ROW_COMPARATOR = (r1, r2) -> {
        int sb = STRING_COMPARATOR.compare(r1.getStudyBurstId(), r2.getStudyBurstId());
        return (sb != 0) ? sb : STRING_COMPARATOR.compare(r1.getLabel(), r2.getLabel());
    };

    protected LocalDate getDate(AdherenceState state, String eventId) {
        DateTime timestamp = state.getEventTimestampById(eventId);
        return (timestamp != null) ? timestamp.toLocalDate() : null;
    }

    public StudyAdherenceReport generate(AdherenceState state, Schedule2 schedule) {
        
        EventStreamAdherenceReport eventReport = EventStreamAdherenceReportGenerator.INSTANCE.generate(state, schedule);
        
        // Get the earliest date present in the report. The report starts from this date so week and day 
        // calculations are correct.
        LocalDate earliestDate = getDate(state, eventReport.getEarliestEventId());
        // Get the start date of the report. If the schedule was not set up correctly, this can be after
        // the earliest date. The timeline is calculated the same, but we’ll show weeks before the start
        // of the study as “Week 0”, “Week -1”, and so forth.
        LocalDate studyStartDate = getDate(state, state.getStudyStartEventId());

        EventStream studyStream = new EventStream();
        Set<String> unsetEventIds = new HashSet<>();
        Set<String> unscheduledSessions = new HashSet<>();
        Map<String, DateTime> eventTimestamps = new HashMap<>();
        LocalDate localToday = state.getNow().toLocalDate();
        
        // Remap all the event streams to one event stream one one timeline from the earliest date
        for (EventStream stream : eventReport.getStreams()) {
            stream.visitDays((day, i) -> {
                if (day.getStartDate() != null) {
                    Integer numDays = Days.daysBetween(earliestDate, day.getStartDate()).getDays();
                    studyStream.addEntry(numDays, day);
                    
                    String eventId = day.getStartEventId();
                    eventTimestamps.put(eventId, state.getEventTimestampById(eventId));
                } else {
                    // This event has no timestamp and so this session is not scheduled for the user.
                    // Record to report back.
                    unsetEventIds.add(day.getStartEventId());
                    unscheduledSessions.add(day.getSessionName());
                }
            });
        }
        
        // Break this study stream down into weeks. TreeMap sorts the weeks by week number. This report is still
        // “sparse” (no weeks are present that have no activities). 
        Map<Integer, StudyReportWeek> weekMap = new TreeMap<>();
        for (Map.Entry<Integer, List<EventStreamDay>> entry : studyStream.getByDayEntries().entrySet()) {
            int week = entry.getKey() / 7;
            int dayOfWeek = entry.getKey() % 7;
            
            StudyReportWeek oneWeek = weekMap.get(week);
            if (oneWeek == null) {
                oneWeek = new StudyReportWeek();
                oneWeek.setStartDate(earliestDate.plusDays(week*7));
                oneWeek.setWeekInStudy(week+1); // humans are 1-indexed
                
                weekMap.put(week, oneWeek);
            }
            // Note that we are not updating the week value of the day from the week-in-stream to
            // the week-in-study. It is nulled out as part of report generation, except for NextActivity,
            // so we only set it there.
            List<EventStreamDay> days = oneWeek.getByDayEntries().get(dayOfWeek);
            days.addAll(entry.getValue());
        };

        // Some study-wide calculations:
        
        // Calculate the date range, but make sure it includes the study start date
        // so the user can see if the participant is acting well before the study start 
        // date or well after, etc.
        DateRange dateRange = null;
        if (eventReport.getDateRangeOfAllStreams() != null) {
            DateRange streamDates = eventReport.getDateRangeOfAllStreams();
            LocalDate startDate = streamDates.getStartDate();
            LocalDate endDate = streamDates.getEndDate();
            if (studyStartDate != null) {
                if (studyStartDate.isBefore(startDate)) {
                    startDate = studyStartDate;
                } else if (studyStartDate.isAfter(endDate)) {
                    endDate = studyStartDate;
                }
            }
            if (startDate.isEqual(endDate) || startDate.isBefore(endDate)) {
                dateRange = new DateRange(startDate, endDate);
            }
        }
        
        // Study-wide progress and adherence. Don't calculate adherence if there's no schedule.
        ParticipantStudyProgress progression = calculateProgress(state, ImmutableList.of(studyStream));
        Integer adherence = null;
        if (ParticipantStudyProgress.UNSTARTED != progression) {
            adherence = calculateAdherencePercentage(ImmutableList.of(studyStream));
        }
        // If the earliest date is before the study start date, we're going to offset the weekInStudy 
        // field to show there are scheduled activities occurring earlier in the study design. Up to 7
        // days difference, earliestDate and studyStartDate are in the same week (so the offset is, 
        // correctly, still 0). Above seven days, earliestDate and studyStartDate will start to be in 
        // different weeks.
        int weekOffset = 0;
        if (earliestDate != null && studyStartDate != null && earliestDate.isBefore(studyStartDate)) {
            int days = Days.daysBetween(earliestDate, studyStartDate).getDays();
            weekOffset = (days/7);
        }

        List<StudyReportWeek> weeks = Lists.newArrayList(weekMap.values());
        StudyReportWeek currentWeek = null;
        
        // Calculate rows and labels, determine the current week, and calculate adherence for each week
        for (StudyReportWeek oneWeek : weeks) {
            oneWeek.setWeekInStudy(oneWeek.getWeekInStudy()-weekOffset);
            LocalDate firstDayOfWeek = oneWeek.getStartDate();
            LocalDate lastDayOfWeek = oneWeek.getStartDate().plusDays(6);

            calculateRowsAndLabels(oneWeek, localToday);
            if (isLocalDateInRange(firstDayOfWeek, lastDayOfWeek, localToday)) {
                currentWeek = oneWeek;
            }
            // Leave adherence null for future weeks. localToday is only in range {firstDayOfWeek-} when
            // the week’s firstDayOfWeek is in the past.
            if (isLocalDateInRange(firstDayOfWeek, null, localToday)) {
                int weekAdh = AdherenceUtils.calculateAdherencePercentage(oneWeek.getByDayEntries());
                oneWeek.setAdherencePercent(weekAdh);
            }
        }
        NextActivity nextActivity = null;
        if (currentWeek == null) {
            nextActivity = getNextActivity(weeks, localToday);
        }
        
        StudyReportWeek weekReport = createWeekReport(progression, weeks, currentWeek, earliestDate, localToday);
        
        // Delete unnecessary fields, set today for all day entries
        weeks.forEach(week -> clearUnusedFields(week, localToday));
        
        StudyAdherenceReport report = new StudyAdherenceReport();
        report.setDateRange(dateRange);
        report.setWeeks(weeks);
        report.setWeekReport(weekReport);
        report.setNextActivity(nextActivity);
        report.setProgression(progression);
        report.setAdherencePercent(adherence);
        report.setEventTimestamps(eventTimestamps);
        report.setUnsetEventIds(unsetEventIds);
        report.setUnscheduledSessions(unscheduledSessions);
        return report;
    }

    protected void calculateRowsAndLabels(StudyReportWeek oneWeek, LocalDate localToday) {
        Set<String> labels = new LinkedHashSet<>();
        Set<WeeklyAdherenceReportRow> rows = new LinkedHashSet<>();
        for (List<EventStreamDay> days : oneWeek.getByDayEntries().values()) {
            for (EventStreamDay oneDay : days) {
                // The main searches to support are:
                // <Study Burst>
                // <Study Burst ID> 1
                // <Study Burst ID> 1:Week 1
                // <Session Name>
                // <Session Name>:Week 1
                // Week 1
                // if this is a placeholder day, just skip it. We run into these
                // when we process the selected weekly report, which has already been
                // padded.
                if (oneDay.getTimeWindows().isEmpty()) {
                    continue;
                }
                String searchableLabel = (oneDay.getStudyBurstId() != null) ?
                        ":%s:%s %s:Week %s:%s:".formatted(oneDay.getStudyBurstId(), oneDay.getStudyBurstId(), oneDay.getStudyBurstNum(), oneWeek.getWeekInStudy(), oneDay.getSessionName()) :
                        ":%s:Week %s:".formatted(oneDay.getSessionName(), oneWeek.getWeekInStudy());
                String displayLabel = (oneDay.getStudyBurstId() != null) ?
                        "%s %s / Week %s / %s".formatted(oneDay.getStudyBurstId(), oneDay.getStudyBurstNum(), oneWeek.getWeekInStudy(), oneDay.getSessionName()) :
                        "%s / Week %s".formatted(oneDay.getSessionName(), oneWeek.getWeekInStudy());
                labels.add(searchableLabel);
                
                WeeklyAdherenceReportRow row = new WeeklyAdherenceReportRow(); 
                row.setLabel(displayLabel);
                row.setSearchableLabel(searchableLabel);
                row.setSessionGuid(oneDay.getSessionGuid());
                row.setStartEventId(oneDay.getStartEventId());
                row.setSessionName(oneDay.getSessionName());
                row.setSessionSymbol(oneDay.getSessionSymbol());
                row.setStudyBurstId(oneDay.getStudyBurstId());
                row.setStudyBurstNum(oneDay.getStudyBurstNum());
                row.setWeekInStudy(oneWeek.getWeekInStudy());
                rows.add(row);
            }
        }

        // Sort the rows so they display in an order that makes sense, and then pad the days to fit the
        // number and order of the rows.
        List<WeeklyAdherenceReportRow> rowList = Lists.newArrayList(rows);
        rowList.sort(ROW_COMPARATOR);
        
        for (int i=0; i < 7; i++) {
            List<EventStreamDay> paddedDays = new ArrayList<>();
            
            for (WeeklyAdherenceReportRow row : rowList) {
                List<EventStreamDay> days = oneWeek.getByDayEntries().get(i);
                
                EventStreamDay oneDay = findOrCreateDay(days, row.getSessionGuid(), row.getStartEventId());
                // Only update this if it's a new day, if the date is set this is the second
                // pass through the method to clean up the weekly report; leave it.
                if (oneDay.getStartDate() == null) {
                    LocalDate thisDate = oneWeek.getStartDate().plusDays(i);
                    oneDay.setStartDate(thisDate);
                }
                paddedDays.add(oneDay);
            }
            oneWeek.getByDayEntries().put(i, paddedDays);
        }
        oneWeek.getSearchableLabels().addAll(labels);
        // Instead of adding carry-over rows to the existing rows, we are recalculating all of them
        // in this method and replacing the existing rows. This avoids duplicate rows due to carry-
        // overs in the second pass that are identical except for the week when they started.
        oneWeek.getRows().clear();
        oneWeek.getRows().addAll(rowList);
    }
    
    private EventStreamDay findOrCreateDay(List<EventStreamDay> days, String sessionGuid, String eventId) {
        Optional<EventStreamDay> oneDay = days.stream()
               // On a second pass to create rows and pad days for the weekly report, skip padding days
               .filter(day -> day.getSessionGuid() != null/* && day.getStartDate() != null*/)
               // If a session is triggered twice by two different events, it can appear twice in rows.
               // If the events have nearly the same timestamp, the GUIDs and the labels would be exactly
               // the same. For this reason we carry over the event ID as this identifies two unique streams
               // and these must be tracked separately, even though they look the same in the report
               .filter(day -> day.getSessionGuid().equals(sessionGuid) && day.getStartEventId().equals(eventId))
               .findFirst();
        return oneDay.orElse(new EventStreamDay());
    }
    
    /**
     * Starting with the week after the week that contains “today“ look for the first day with a non-empty
     * time window, and use that activity to populate NextActivity information.
     */
    private NextActivity getNextActivity(List<StudyReportWeek> weeks, LocalDate localToday) {
        for (StudyReportWeek oneWeek : weeks) {
            if (oneWeek.getStartDate().isAfter(localToday)) {
                for (List<EventStreamDay> days : oneWeek.getByDayEntries().values()) {
                    for (EventStreamDay oneDay : days) {
                        if (!oneDay.getTimeWindows().isEmpty()) {
                            // This has yet to be changed from day-in-stream to day-in-study, so do it here.
                            oneDay.setWeek(oneWeek.getWeekInStudy());
                            return NextActivity.create(oneDay);
                        }
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * Create a week report. This report will exist even if currentWeek is null (it will measure weeks from
     * the studyStartDate if need be), and it includes information about active "carry-over" activities from 
     * prior weeks. Note that currentWeek needs some of the same processing, like day padding, that all the 
     * weeks of the weekly report get, so there is some duplication here. Initially it was just a reference
     * to one of those weeks, but there are some differences.
     */
    private StudyReportWeek createWeekReport(ParticipantStudyProgress progression, List<StudyReportWeek> weeks,
            StudyReportWeek currentWeek, LocalDate studyStartDate, LocalDate localToday) {

        StudyReportWeek weekReport;
        if (currentWeek != null) {
            weekReport = currentWeek.copy();
        } else if (studyStartDate != null) {
            weekReport = new StudyReportWeek();
            int dayCount = Days.daysBetween(studyStartDate, localToday).getDays();
            int week = dayCount/7;
            weekReport.setWeekInStudy(week+1);
            weekReport.setStartDate(studyStartDate.plusDays(week*7));
        } else { 
            weekReport = new StudyReportWeek();
            weekReport.setWeekInStudy(1);
            weekReport.setStartDate(localToday);
        }
        
        // Find and carry over *all* activities from prior weeks that are not done and that are not expired.
        
        List<EventStreamDay> carryOvers = new ArrayList<>();
        
        int initialDayZeroEntries = weekReport.getByDayEntries().get(0).size();
        for (StudyReportWeek oneWeek : weeks) {
            if (isLocalDateInRange(weekReport.getStartDate(), null, oneWeek.getStartDate())) {
                break;
            }
            oneWeek.visitDays((day, i) -> {
                boolean match = day.getTimeWindows().stream()
                        .anyMatch(window -> UNKNOWN.contains(window.getState()));
                if (match) {
                    EventStreamDay dayCopy = day.copy();
                    dayCopy.setTimeWindows(day.getTimeWindows().stream()
                        .filter(win -> UNKNOWN.contains(win.getState()))
                        .collect(toList()));
                    weekReport.getByDayEntries().get(0).add(dayCopy);
                    carryOvers.add(dayCopy);
                }
            });
        }
        if (weekReport.getByDayEntries().get(0).size() > initialDayZeroEntries) {
            calculateRowsAndLabels(weekReport, localToday);    
        }
        
        // recalculate this, again if there's no schedule, don't calculate anything.
        Integer adhPercent = null;
        if (ParticipantStudyProgress.UNSTARTED != progression) {
            adhPercent = calculateAdherencePercentage(weekReport.getByDayEntries());
        }
        weekReport.setAdherencePercent(adhPercent);
        clearUnusedFields(weekReport,  localToday);
        // reset this because we want it to be marked for display as today if it's
        // in the today column, even though its startDate is prior to that day
        for (EventStreamDay carryOver : carryOvers) {
            carryOver.setToday(weekReport.getStartDate().isEqual(localToday));
        }
        return weekReport;
    }
    
    /**
     * @param oneWeek
     * @param localToday
     */
    private void clearUnusedFields(StudyReportWeek oneWeek, LocalDate localToday) { 
        oneWeek.visitDays((day, i) -> {
            day.setStudyBurstId(null);
            day.setStudyBurstNum(null);
            day.setSessionName(null);
            day.setWeek(null);
            day.setToday(localToday.isEqual(day.getStartDate()));    
            day.setStartDay(null);
            for (EventStreamWindow window : day.getTimeWindows()) {
                window.setEndDay(null);
                // This cannot be removed, or the window will be removed from persisted collection 
                // window.setTimeWindowGuid(null);
            }
        });
    }
}
