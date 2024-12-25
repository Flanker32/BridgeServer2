package org.sagebionetworks.bridge.dynamodb;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;
import java.util.Set;

import jakarta.annotation.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.dao.ReportIndexDao;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.ReportTypeResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.reports.ReportDataKey;
import org.sagebionetworks.bridge.models.reports.ReportIndex;
import org.sagebionetworks.bridge.models.reports.ReportType;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException;
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;
import com.google.common.collect.ImmutableMap;

@Component
public class DynamoReportIndexDao implements ReportIndexDao {
    private static final Logger LOG = LoggerFactory.getLogger(DynamoReportIndexDao.class);

    protected static final DynamoDBSaveExpression DOES_NOT_EXIST_EXPRESSION = new DynamoDBSaveExpression()
            .withExpected(new ImmutableMap.Builder<String,ExpectedAttributeValue>()
                    .put("key", new ExpectedAttributeValue(false))
                    .put("identifier", new ExpectedAttributeValue(false)).build());
    
    private DynamoDBMapper mapper;

    @Resource(name = "reportIndexMapper")
    final void setReportIndexMapper(DynamoDBMapper reportIndexMapper) {
        this.mapper = reportIndexMapper;
    }
    
    @Override
    public ReportIndex getIndex(ReportDataKey key) {
        checkNotNull(key);
        
        DynamoReportIndex hashKey = new DynamoReportIndex();
        hashKey.setKey(key.getIndexKeyString());
        hashKey.setIdentifier(key.getIdentifier());
        
        return mapper.load(hashKey);
    }

    @Override
    public void addIndex(ReportDataKey key, Set<String> studies) {
        checkNotNull(key);
        
        DynamoReportIndex index = new DynamoReportIndex();
        index.setKey(key.getIndexKeyString());
        index.setIdentifier(key.getIdentifier());
        index.setStudyIds(studies);

        // Optimization: Reads are significantly cheaper than writes. Check to see if the index already exists. If it
        // does, don't bother writing it.
        DynamoReportIndex loadedIndex = mapper.load(index);
        if (loadedIndex != null) {
            return;
        }

        try {
            mapper.save(index, DOES_NOT_EXIST_EXPRESSION);    
        } catch(ConditionalCheckFailedException e) {
            // Read-before-write is not atomic. There's a possible race condition where two machines are creating the
            // index at the same time. It's rare, but possible that one of these machines may have also updated the
            // metadata before we save. So we still need this SaveExpression to prevent clobbering the index metadata.
            LOG.warn("Race condition creating index for " + key.toString() + ": " + e.getMessage(), e);
        }
    }
    
    @Override
    public void removeIndex(ReportDataKey key) {
        checkNotNull(key);
        
        DynamoReportIndex hashKey = new DynamoReportIndex();
        hashKey.setKey(key.getIndexKeyString());
        hashKey.setIdentifier(key.getIdentifier());
        
        DynamoReportIndex index = mapper.load(hashKey);
        if (index != null) {
            mapper.delete(index);
        }
    }

    @Override
    public void updateIndex(ReportIndex index) {
        checkNotNull(index);
        
        Map<String,ExpectedAttributeValue> map = new ImmutableMap.Builder<String,ExpectedAttributeValue>()
                .put("key", new ExpectedAttributeValue(new AttributeValue(index.getKey())))
                .put("identifier", new ExpectedAttributeValue(new AttributeValue(index.getIdentifier()))).build();
        
        DynamoDBSaveExpression doesExistExpression = new DynamoDBSaveExpression().withExpected(map);
        
        try {
            mapper.save(index, doesExistExpression);
        } catch(ConditionalCheckFailedException e) {
            throw new EntityNotFoundException(ReportIndex.class);
        }
    }
    
    @Override
    public ReportTypeResourceList<? extends ReportIndex> getIndices(String appId, ReportType reportType) {
        checkNotNull(appId);
        checkNotNull(reportType);
        
        // Don't use ReportDataKey because it enforces presence of healthCode for ReportType.PARTICIPANT.
        String key = "%s:%s".formatted(appId, reportType.name());
        
        DynamoReportIndex hashKey = new DynamoReportIndex();
        hashKey.setKey(key);
        
        DynamoDBQueryExpression<DynamoReportIndex> query =
                new DynamoDBQueryExpression<DynamoReportIndex>().withHashKeyValues(hashKey);

        return new ReportTypeResourceList<>(mapper.query(DynamoReportIndex.class, query))
                .withRequestParam(ResourceList.REPORT_TYPE, reportType);
    }

}
