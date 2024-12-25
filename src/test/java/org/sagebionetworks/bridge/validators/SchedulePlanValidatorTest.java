package org.sagebionetworks.bridge.validators;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import java.util.Set;

import org.springframework.validation.Errors;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoSchedulePlan;
import org.sagebionetworks.bridge.exceptions.InvalidEntityException;
import org.sagebionetworks.bridge.models.schedules.ABTestScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.Schedule;
import org.sagebionetworks.bridge.models.schedules.SchedulePlan;
import org.sagebionetworks.bridge.models.schedules.ScheduleStrategy;
import org.sagebionetworks.bridge.models.schedules.ScheduleType;
import org.sagebionetworks.bridge.models.schedules.SimpleScheduleStrategy;

import com.google.common.collect.Sets;

public class SchedulePlanValidatorTest {

    private static final Set<String> TASK_IDENTIFIERS = Sets.newHashSet("tapTest");
    
    private SchedulePlanValidator validator;

    @BeforeMethod
    public void before() throws Exception {
        validator = new SchedulePlanValidator(TestConstants.USER_DATA_GROUPS, TestConstants.USER_STUDY_IDS,
                TASK_IDENTIFIERS);
    }

    @Test
    public void enumeratedValuesArePassedToScheduleStrategyValidator() {
        SchedulePlan plan = new DynamoSchedulePlan();
        ScheduleStrategy strategy = mock(ScheduleStrategy.class);
        plan.setStrategy(strategy);
        
        Errors errors = mock(Errors.class);
        
        validator.validate((Object)plan, errors);
        
        verify(strategy).validate(TestConstants.USER_DATA_GROUPS, TestConstants.USER_STUDY_IDS, TASK_IDENTIFIERS,
                errors);
    }
    
    @Test
    public void validSchedulePlan() {
        SchedulePlan plan = getValidSimpleStrategy();
        Validate.entityThrowingException(validator, plan);

        plan = getValidABTestStrategy();
        Validate.entityThrowingException(validator, plan);
    }
    
    @Test
    public void appIdRequired() {
        SchedulePlan plan = getValidSimpleStrategy();
        plan.setAppId(null);
        assertMessage(plan, "appId cannot be missing, null, or blank", "appId");
    }

    @Test
    public void labelRequired() {
        SchedulePlan plan = getValidSimpleStrategy();
        plan.setLabel(null);
        assertMessage(plan, "label cannot be missing, null, or blank", "label");
    }

    @Test
    public void strategyRequired() {
        SchedulePlan plan = getValidSimpleStrategy();
        plan.setStrategy(null);
        assertMessage(plan, "strategy is required", "strategy");
    }

    @Test
    public void simpleStrategyScheduleRequired() {
        SchedulePlan plan = getValidSimpleStrategy();
        ((SimpleScheduleStrategy)plan.getStrategy()).setSchedule(null);
        assertMessage(plan, "strategy.schedule is required", "strategy.schedule");
    }
    
    @Test
    public void abTestStrategyScheduleAddsUpTo100() {
        SchedulePlan plan = getValidABTestStrategy();
        ((ABTestScheduleStrategy)plan.getStrategy()).getScheduleGroups().getFirst().setPercentage(10);
        
        assertMessage(plan, "strategy.scheduleGroups groups must add up to 100%", "strategy.scheduleGroups");
    }
    
    @Test
    public void abTestStrategyScheduleRequiresSchedule() {
        SchedulePlan plan = getValidABTestStrategy();
        ((ABTestScheduleStrategy)plan.getStrategy()).getScheduleGroups().getFirst().setSchedule(null);
        
        assertMessage(plan, "strategy.scheduleGroups[0].schedule is required", "strategy.scheduleGroups[0].schedule");
    }

    private void assertMessage(SchedulePlan plan, String message, String fieldName) {
        try {
            Validate.entityThrowingException(validator, plan);
            fail("Should have thrown exception");
        } catch (InvalidEntityException e) {
            assertEquals(e.getErrors().get(fieldName).getFirst(), message);
        }
    }

    private Schedule getSchedule() {
        Schedule schedule = new Schedule();
        schedule.setScheduleType(ScheduleType.ONCE);
        schedule.setLabel("a label");
        schedule.addActivity(TestUtils.getActivity3());
        return schedule;
    }

    private SchedulePlan getValidSimpleStrategy() {
        SimpleScheduleStrategy strategy = new SimpleScheduleStrategy();
        strategy.setSchedule(getSchedule());

        // This is valid, then you can manipulate it to invalidate it.
        SchedulePlan plan = new DynamoSchedulePlan();
        plan.setLabel("a label");
        plan.setAppId(TEST_APP_ID);
        plan.setStrategy(strategy);
        return plan;
    }

    private SchedulePlan getValidABTestStrategy() {
        ABTestScheduleStrategy strategy = new ABTestScheduleStrategy();
        strategy.addGroup(70, getSchedule());
        strategy.addGroup(30, getSchedule());

        // This is valid, then you can manipulate it to invalidate it.
        SchedulePlan plan = new DynamoSchedulePlan();
        plan.setLabel("a label");
        plan.setAppId(TEST_APP_ID);
        plan.setStrategy(strategy);
        return plan;
    }
}
