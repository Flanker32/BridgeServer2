package org.sagebionetworks.bridge.hibernate;

import java.util.Optional;

import jakarta.annotation.Resource;

import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.AssessmentConfigDao;
import org.sagebionetworks.bridge.models.assessments.Assessment;
import org.sagebionetworks.bridge.models.assessments.HibernateAssessment;
import org.sagebionetworks.bridge.models.assessments.config.AssessmentConfig;
import org.sagebionetworks.bridge.models.assessments.config.HibernateAssessmentConfig;

@Component
public class HibernateAssessmentConfigDao implements AssessmentConfigDao {
    
    private HibernateHelper hibernateHelper;
    
    @Resource(name = "basicHibernateHelper")
    final void setHibernateHelper(HibernateHelper hibernateHelper) {
        this.hibernateHelper = hibernateHelper;
    }

    @Override
    public Optional<AssessmentConfig> getAssessmentConfig(String guid) {
        HibernateAssessmentConfig config = hibernateHelper.getById(HibernateAssessmentConfig.class, guid);
        if (config == null) {
            return Optional.empty();
        }
        return Optional.of(AssessmentConfig.create(config));
    }

    @Override
    public AssessmentConfig updateAssessmentConfig(String appId, Assessment assessment, String guid, AssessmentConfig config) {
        HibernateAssessment hibAssessment = HibernateAssessment.create(appId, assessment);
        HibernateAssessmentConfig hibConfig = HibernateAssessmentConfig.create(guid, config);
        HibernateAssessmentConfig retValue = hibernateHelper.executeWithExceptionHandling(hibConfig, (session) -> {
            session.merge(hibAssessment);
            session.merge(hibConfig);
            // If we don't reload this value, the version we return is not updated. We've
            // verified through manual testing with client applications. It appears
            // the merge method does not do this like the save method.
            return session.get(HibernateAssessmentConfig.class, hibConfig.getGuid());
        });
        return AssessmentConfig.create(retValue);
    }

    @Override
    public AssessmentConfig customizeAssessmentConfig(String guid, AssessmentConfig config) {
        HibernateAssessmentConfig hibConfig = HibernateAssessmentConfig.create(guid, config);
        HibernateAssessmentConfig retValue = hibernateHelper.executeWithExceptionHandling(hibConfig, (session) -> {
            session.merge(hibConfig);
            // If we don't reload this value, the version we return is not updated. We've
            // verified through manual testing with client applications. It appears
            // the merge method does not do this like the save method.
            return session.get(HibernateAssessmentConfig.class, hibConfig.getGuid());
        });
        return AssessmentConfig.create(retValue);
    }
    
    @Override
    public void deleteAssessmentConfig(String guid, AssessmentConfig config) {
        HibernateAssessmentConfig hibConfig = HibernateAssessmentConfig.create(guid, config);
        hibernateHelper.executeWithExceptionHandling(hibConfig, (session) -> {
            session.remove(hibConfig);
            return null;
        });
    }

}
