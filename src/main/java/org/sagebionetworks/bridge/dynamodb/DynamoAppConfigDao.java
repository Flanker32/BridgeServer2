package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;

import jakarta.annotation.Resource;

import org.sagebionetworks.bridge.dao.AppConfigDao;
import org.sagebionetworks.bridge.dao.CriteriaDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.Criteria;
import org.sagebionetworks.bridge.models.appconfig.AppConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.google.common.collect.Lists;

@Component
public class DynamoAppConfigDao implements AppConfigDao {

    private DynamoDBMapper mapper;
    private CriteriaDao criteriaDao;
    
    @Resource(name = "appConfigDdbMapper")
    final void setMapper(DynamoDBMapper mapper) {
        this.mapper = mapper;
    }
    
    @Autowired
    final void setCriteriaDao(CriteriaDao criteriaDao) {
        this.criteriaDao = criteriaDao;
    }
    
    DynamoAppConfig loadAppConfig(String appId, String guid) {
        checkNotNull(appId);
        checkNotNull(guid);
        
        DynamoAppConfig key = new DynamoAppConfig();
        key.setAppId(appId);
        key.setGuid(guid);
        
        DynamoAppConfig config = mapper.load(key);
        if (config != null) {
            loadCriteria(config);
        }
        return config;
    }
    
    public List<AppConfig> getAppConfigs(String appId, boolean includeDeleted) {
        checkNotNull(appId);
        
        DynamoAppConfig key = new DynamoAppConfig();
        key.setAppId(appId);

        DynamoDBQueryExpression<DynamoAppConfig> query = new DynamoDBQueryExpression<DynamoAppConfig>()
                .withHashKeyValues(key);
        if (!includeDeleted) {
            query.withQueryFilterEntry("deleted", new Condition()
                .withComparisonOperator(ComparisonOperator.NE)
                .withAttributeValueList(new AttributeValue().withN("1")));
        }
        PaginatedQueryList<DynamoAppConfig> results = mapper.query(DynamoAppConfig.class, query);
        
        List<AppConfig> list = Lists.newArrayListWithCapacity(results.size());
        for (DynamoAppConfig appConfig : results) {
            loadCriteria(appConfig);
            list.add(appConfig);
        }
        return list;
    }
    
    public AppConfig getAppConfig(String appId, String guid) {
        DynamoAppConfig appConfig = loadAppConfig(appId, guid);
        if (appConfig == null) {
            throw new EntityNotFoundException(AppConfig.class);
        }
        return appConfig;
    }
    
    public AppConfig createAppConfig(AppConfig appConfig) {
        checkNotNull(appConfig);
        
        appConfig.setDeleted(false);
        Criteria criteria = persistCriteria(appConfig);
        appConfig.setCriteria(criteria);
        
        mapper.save(appConfig);
        
        return appConfig;
    }
    
    public AppConfig updateAppConfig(AppConfig appConfig) {
        checkNotNull(appConfig);
        
        DynamoAppConfig saved = loadAppConfig(appConfig.getAppId(), appConfig.getGuid());
        // If it doesn't exist, or it's deleted and not being undeleted, then return 404
        if (saved == null || (appConfig.isDeleted() && saved.isDeleted())) {
            throw new EntityNotFoundException(AppConfig.class);
        }
        Criteria criteria = persistCriteria(appConfig);
        appConfig.setCriteria(criteria);
        
        mapper.save(appConfig);
        return appConfig;
    }
    
    public void deleteAppConfig(String appId, String guid) {
        checkNotNull(appId);
        checkNotNull(guid);
        
        AppConfig appConfig = loadAppConfig(appId, guid);
        if (appConfig == null || appConfig.isDeleted()) {
            throw new EntityNotFoundException(AppConfig.class);
        }
        appConfig.setDeleted(true);
        mapper.save(appConfig);
    }
    
    public void deleteAppConfigPermanently(String appId, String guid) {
        checkNotNull(appId);
        checkNotNull(guid);
        
        DynamoAppConfig appConfig = loadAppConfig(appId, guid);
        if (appConfig == null) {
            throw new EntityNotFoundException(AppConfig.class);
        }
        mapper.delete(appConfig);
        criteriaDao.deleteCriteria(appConfig.getCriteria().getKey());
    }
    
    private String getKey(AppConfig appConfig) {
        return "appconfig:" + appConfig.getGuid();
    }
    
    private Criteria persistCriteria(AppConfig config) {
        Criteria criteria = config.getCriteria();
        criteria.setKey(getKey(config));
        return criteriaDao.createOrUpdateCriteria(criteria);
    }

    private void loadCriteria(AppConfig config) {
        Criteria criteria = criteriaDao.getCriteria(getKey(config));
        if (criteria == null) {
            criteria = Criteria.create();
        }
        criteria.setKey(getKey(config));
        config.setCriteria(criteria);
    }
}
