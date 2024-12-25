package org.sagebionetworks.bridge.models.schedules2.timelines;

import static org.sagebionetworks.bridge.TestConstants.CREATED_ON;
import static org.sagebionetworks.bridge.TestConstants.MODIFIED_ON;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.List;

import com.google.common.collect.ImmutableList;

import org.joda.time.DateTime;
import org.mockito.Mockito;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.schedules2.adherence.AdherenceRecord;

public class SessionStateTest extends Mockito {
    private DateTime TS1 = DateTime.now();
    private DateTime TS2 = DateTime.now().plusHours(1);
    private DateTime TS3 = DateTime.now().plusHours(2);
    private DateTime TS4 = DateTime.now().plusHours(3);
    private DateTime TS5 = DateTime.now().plusHours(4);
    private DateTime TS6 = DateTime.now().plusHours(5);
    private DateTime TS7 = DateTime.now().plusHours(6);
    private DateTime TS8 = DateTime.now().plusHours(7);
    
    @Test
    public void testUnstarted() { 
        SessionState state = new SessionState(4);
        assertTrue(state.isUnstarted());
        
        state = new SessionState(4);
        state.add(unstartedRec());
        state.add(unstartedRec());
        assertTrue(state.isUnstarted());
        state.add(unstartedRec());
        state.add(unstartedRec());
        assertTrue(state.isUnstarted());
    }

    @Test
    public void testDeclined() { 
        SessionState state = new SessionState(2);
        state.add(finishedRec());
        assertFalse(state.isDeclined());
        
        state = new SessionState(2);
        state.add(unstartedDeclinedRec());
        state.add(unstartedDeclinedRec());
        assertTrue(state.isDeclined());
    }
    
    @Test
    public void testFinished() { 
        SessionState state = new SessionState(4);
        state.add(finishedRec());
        state.add(finishedRec());
        state.add(finishedRec());
        state.add(finishedRec());
        assertTrue(state.isFinished());
        
        state = new SessionState(4);
        state.add(finishedRec());
        state.add(finishedRec());
        state.add(unstartedRec());
        state.add(unstartedRec());
        assertFalse(state.isFinished());
    }
    
    @Test
    public void testStarted() { 
        SessionState state = new SessionState(4);
        state.add(finishedRec());
        state.add(finishedRec());
        state.add(unstartedRec());
        state.add(unstartedRec());
        assertFalse(state.isUnstarted());
        
        state = new SessionState(4);
        state.add(unstartedRec());
        state.add(unstartedRec());
        assertTrue(state.isUnstarted());
    }
    
    @Test
    public void selectsTimestamps() {
        List<AdherenceRecord> recs = getRecords();
        
        SessionState state = new SessionState(4);
        state.add(recs.getFirst());
        state.add(recs.get(1));
        state.add(recs.get(2));
        state.add(recs.get(3));
        
        assertEquals(state.earliest, TS1);
        assertEquals(state.latest, TS8);
        
        AdherenceRecord sessionRecord = new AdherenceRecord();
        
        boolean retValue = state.updateSessionRecord(sessionRecord);
        assertTrue(retValue);
        assertEquals(sessionRecord.getStartedOn(), TS1);
        assertEquals(sessionRecord.getFinishedOn(), TS8);
    }
    
    @Test
    public void unstartsSessionRecord() { 
        SessionState state = new SessionState(4);
        
        AdherenceRecord sessionRecord = new AdherenceRecord();
        sessionRecord.setStartedOn(CREATED_ON);
        sessionRecord.setFinishedOn(MODIFIED_ON);
        
        boolean retValue = state.updateSessionRecord(sessionRecord);
        assertTrue(retValue);
        assertNull(sessionRecord.getStartedOn());
        assertNull(sessionRecord.getFinishedOn());
    }
    
    @Test
    public void finishSessionRecord() {
        List<AdherenceRecord> recs = getRecords();
        
        SessionState state = new SessionState(4);
        state.add(recs.getFirst());
        state.add(recs.get(1));
        state.add(recs.get(2));
        state.add(recs.get(3));
        
        AdherenceRecord sessionRecord = new AdherenceRecord();
        
        boolean retValue = state.updateSessionRecord(sessionRecord);
        assertTrue(retValue);
        assertEquals(sessionRecord.getStartedOn(), TS1);
        assertEquals(sessionRecord.getFinishedOn(), TS8);
    }
    
    @Test
    public void inProcessSessionRecord() {
        List<AdherenceRecord> recs = getRecords();
        
        recs.get(2).setFinishedOn(null);
        
        SessionState state = new SessionState(4);
        state.add(recs.getFirst());
        state.add(recs.get(1));
        state.add(recs.get(2));
        // not the 4th record
        
        AdherenceRecord sessionRecord = new AdherenceRecord();
        sessionRecord.setFinishedOn(MODIFIED_ON);
        
        boolean retValue = state.updateSessionRecord(sessionRecord);
        assertTrue(retValue);
        assertEquals(sessionRecord.getStartedOn(), TS2); // TS1 was in record #4
        assertNull(sessionRecord.getFinishedOn());
    }
    
    @Test
    public void unstartsSessionRecordNotChanged() { 
        SessionState state = new SessionState(4);
        state.add(unstartedRec());
        
        AdherenceRecord sessionRecord = new AdherenceRecord();
        
        boolean retValue = state.updateSessionRecord(sessionRecord);
        assertFalse(retValue);
        assertNull(sessionRecord.getStartedOn());
        assertNull(sessionRecord.getFinishedOn());
    }

    @Test
    public void finishSessionRecordIsChanged() {
        List<AdherenceRecord> recs = getRecords();
        recs.getFirst().setDeclined(true);
        recs.get(1).setDeclined(true);
        recs.get(2).setDeclined(true);
        recs.get(3).setDeclined(true);
        
        SessionState state = new SessionState(4);
        for (AdherenceRecord rec : recs) {
            state.add(rec);    
        }
        
        AdherenceRecord sessionRecord = new AdherenceRecord();
        sessionRecord.setStartedOn(CREATED_ON);
        sessionRecord.setFinishedOn(MODIFIED_ON);
        
        boolean retValue = state.updateSessionRecord(sessionRecord);
        assertTrue(retValue);
        assertEquals(sessionRecord.getStartedOn(), TS1);
        assertEquals(sessionRecord.getFinishedOn(), TS8);
        assertTrue(sessionRecord.isDeclined());
    }
    
    @Test
    public void inProcessSessionRecordIsChanged() {
        List<AdherenceRecord> recs = getRecords();
        
        SessionState state = new SessionState(4);
        for (AdherenceRecord oneRec : recs) {
            oneRec.setFinishedOn(null);
            state.add(oneRec);
        }
        
        AdherenceRecord sessionRecord = new AdherenceRecord();
        sessionRecord.setStartedOn(CREATED_ON);
        
        boolean retValue = state.updateSessionRecord(sessionRecord);
        assertTrue(retValue);
        assertEquals(sessionRecord.getStartedOn(), TS1);
        assertNull(sessionRecord.getFinishedOn());
    }
    
    private List<AdherenceRecord> getRecords() { 
        AdherenceRecord rec1 = unstartedRec();
        rec1.setStartedOn(TS2);
        rec1.setFinishedOn(TS3);
        AdherenceRecord rec2 = unstartedRec();
        rec2.setStartedOn(TS7);
        rec2.setFinishedOn(TS8);
        AdherenceRecord rec3 = unstartedRec();
        rec3.setStartedOn(TS4);
        rec3.setFinishedOn(TS6);
        AdherenceRecord rec4 = unstartedRec();
        rec4.setStartedOn(TS1);
        rec4.setFinishedOn(TS5);
        return ImmutableList.of(rec1, rec2, rec3, rec4);
    }

    private AdherenceRecord unstartedRec() {
        AdherenceRecord ar = new AdherenceRecord();
        return ar;
    }

    private AdherenceRecord unstartedDeclinedRec() {
        AdherenceRecord ar = new AdherenceRecord();
        ar.setDeclined(true);
        return ar;
    }
    
    private AdherenceRecord finishedRec() {
        AdherenceRecord ar = new AdherenceRecord();
        ar.setStartedOn(CREATED_ON);
        ar.setFinishedOn(MODIFIED_ON);
        return ar;
    }
}
