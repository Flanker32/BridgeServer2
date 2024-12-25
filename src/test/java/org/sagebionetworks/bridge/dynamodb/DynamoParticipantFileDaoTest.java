package org.sagebionetworks.bridge.dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.PaginatedQueryList;
import com.google.common.collect.ImmutableList;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.sagebionetworks.bridge.TestConstants;
import org.sagebionetworks.bridge.models.ForwardCursorPagedResourceList;
import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.files.ParticipantFile;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

@SuppressWarnings("unchecked")
public class DynamoParticipantFileDaoTest {
    private static final DynamoParticipantFile RESULT;

    private static final List<DynamoParticipantFile> RESULT_LIST;

    private static final ParticipantFile KEY =
            new DynamoParticipantFile("test_user", "test_file");

    static {
        RESULT = new DynamoParticipantFile();
        RESULT.setUserId("test_user");
        RESULT.setFileId("test_file");
        RESULT.setCreatedOn(TestConstants.TIMESTAMP);
        RESULT.setAppId("api");
        RESULT.setMimeType("dummy-type");
        RESULT.setDownloadUrl("fake_url");

        RESULT_LIST = new ArrayList<>(10);
        for (int i = 0; i < 10; i++) {
            DynamoParticipantFile file = new DynamoParticipantFile();
            file.setFileId("file" + i);
            file.setUserId("same_user");
            file.setAppId("api");
            file.setMimeType("image/jpeg");
            file.setCreatedOn(TestConstants.TIMESTAMP);
            RESULT_LIST.add(file);
        }
    }

    @Mock
    DynamoDBMapper mapper;

    @Mock
    PaginatedQueryList<DynamoParticipantFile> resultPage;

    @Captor
    ArgumentCaptor<ParticipantFile> fileCaptor;

    @Captor
    ArgumentCaptor<DynamoDBQueryExpression<ParticipantFile>> expressionCaptor;

    @InjectMocks
    DynamoParticipantFileDao dao;

    @BeforeMethod
    public void beforeMethod() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void getParticipantFiles() {
        when(resultPage.stream()).thenReturn(Stream.of(RESULT));
        when(mapper.query(eq(DynamoParticipantFile.class), any())).thenReturn(resultPage);

        ForwardCursorPagedResourceList<ParticipantFile> result = dao.getParticipantFiles(KEY.getUserId(), null, 5);
        assertNotNull(result);
        List<ParticipantFile> resultList = result.getItems();
        assertEquals(resultList.size(), 1);
        ParticipantFile resultFile = resultList.getFirst();
        assertEquals(resultFile, RESULT);

        verify(mapper).query(any(), expressionCaptor.capture());
        DynamoDBQueryExpression<ParticipantFile> expression = expressionCaptor.getValue();
        assertEquals(expression.getLimit().intValue(), 5);
        assertNull(expression.getExclusiveStartKey());
        assertTrue(expression.isConsistentRead());
        assertEquals(expression.getKeyConditionExpression(), "userId = :val1");
        assertEquals(expression.getExpressionAttributeValues().get(":val1").getS(), KEY.getUserId());
    }

    @Test
    public void getParticipantFilesPageSize() {
        when(resultPage.stream()).thenReturn(RESULT_LIST.stream());
        when(mapper.query(eq(DynamoParticipantFile.class), any())).thenReturn(resultPage);

        ForwardCursorPagedResourceList<ParticipantFile> result =
                dao.getParticipantFiles(KEY.getUserId(), null, 5);
        assertNotNull(result);
        String nextPageOffsetKey = result.getNextPageOffsetKey();
        Map<String, Object> params = result.getRequestParams();
        // "file4" is the end of first 5 items, not "file5"
        assertEquals(nextPageOffsetKey, "file4");
        assertNull(params.get(ResourceList.OFFSET_KEY));
        assertEquals(params.get(ResourceList.PAGE_SIZE), 5);

        verify(mapper).query(any(), expressionCaptor.capture());
        DynamoDBQueryExpression<ParticipantFile> expression = expressionCaptor.getValue();
        assertTrue(expression.isConsistentRead());
        assertEquals(expression.getLimit().intValue(), 5);
        assertNull(expression.getExclusiveStartKey());
        assertTrue(expression.isConsistentRead());
        assertEquals(expression.getKeyConditionExpression(), "userId = :val1");
        assertEquals(expression.getExpressionAttributeValues().get(":val1").getS(), KEY.getUserId());

        // verify everything is correct in this result list.
        List<ParticipantFile> resultList = result.getItems();
        for (int i = 0; i < resultList.size(); i++) {
            ParticipantFile file = resultList.get(i);
            assertEquals(file.getUserId(), "same_user");
            assertEquals(file.getFileId(), "file" + i);
            assertEquals(file.getMimeType(), "image/jpeg");
            assertEquals(file.getAppId(), "api");
        }
    }

    @Test
    public void getParticipantFilesOffsetKey() {
        when(mapper.query(eq(DynamoParticipantFile.class), any())).thenAnswer(
                i -> setUpQueryResult(i.getArgument(1)));

        ForwardCursorPagedResourceList<ParticipantFile> result =
                dao.getParticipantFiles(KEY.getUserId(), "file3", 5);
        assertNotNull(result);
        String nextPageOffsetKey = result.getNextPageOffsetKey();
        Map<String, Object> params = result.getRequestParams();

        assertEquals(nextPageOffsetKey, "file8");
        assertEquals(params.get(ResourceList.OFFSET_KEY), "file3");
        assertEquals(params.get(ResourceList.PAGE_SIZE), 5);

        List<ParticipantFile> resultList = result.getItems();
        assertEquals(resultList.size(), 5);
        for (int i = 0; i < resultList.size(); i++) {
            ParticipantFile file = resultList.get(i);
            assertEquals(file.getUserId(), "same_user");
            assertEquals(file.getFileId(), "file" + (i+4));
            assertEquals(file.getMimeType(), "image/jpeg");
            assertEquals(file.getAppId(), "api");
        }

        verify(mapper).query(any(), expressionCaptor.capture());
        DynamoDBQueryExpression<ParticipantFile> expression = expressionCaptor.getValue();
        assertTrue(expression.isConsistentRead());
        assertEquals(expression.getLimit().intValue(), 5);
        assertEquals(expression.getExpressionAttributeValues().get(":val2").getS(),
                "file3");
        assertTrue(expression.isConsistentRead());
        assertEquals(expression.getKeyConditionExpression(), "userId = :val1 and fileId > :val2");
        assertEquals(expression.getExpressionAttributeValues().get(":val1").getS(), KEY.getUserId());
    }

    private PaginatedQueryList<DynamoParticipantFile> setUpQueryResult(DynamoDBQueryExpression<ParticipantFile> exp) {
        String exclusiveStartKey = exp.getExpressionAttributeValues().get(":val2").getS();
        int indexOfStart = -1;
        for (int i = 0; i < RESULT_LIST.size(); i++) {
            if (exclusiveStartKey.equals(RESULT_LIST.get(i).getFileId())) {
                indexOfStart = i;
            }
        }
        List<DynamoParticipantFile> prunedList = RESULT_LIST.subList(
                indexOfStart+1, indexOfStart+exp.getLimit()+1);
        when(resultPage.stream()).thenReturn(prunedList.stream());
        return resultPage;
    }

    @Test
    public void getAllFilesForParticipant() {
        // Set up mock.
        when(resultPage.stream()).thenReturn(Stream.of(RESULT));
        when(mapper.query(eq(DynamoParticipantFile.class), any())).thenReturn(resultPage);

        // Execute and verify.
        List<ParticipantFile> resultList = dao.getAllFilesForParticipant(KEY.getUserId());
        assertNotNull(resultList);
        assertEquals(resultList.size(), 1);
        ParticipantFile resultFile = resultList.getFirst();
        assertSame(resultFile, RESULT);

        // Verify query.
        ArgumentCaptor<DynamoDBQueryExpression<DynamoParticipantFile>> queryCaptor = ArgumentCaptor.forClass(
                DynamoDBQueryExpression.class);
        verify(mapper).query(eq(DynamoParticipantFile.class), queryCaptor.capture());
        DynamoDBQueryExpression<DynamoParticipantFile> expression = queryCaptor.getValue();
        DynamoParticipantFile hashKey = expression.getHashKeyValues();
        assertEquals(hashKey.getUserId(), KEY.getUserId());
        assertNull(hashKey.getFileId());
    }

    @Test
    public void getParticipantFile() {
        when(mapper.load(any())).thenReturn(RESULT);

        Optional<ParticipantFile> result = dao.getParticipantFile(KEY.getUserId(), KEY.getFileId());
        assertTrue(result.isPresent());
        ParticipantFile fileResult = result.get();
        assertEquals(fileResult.getUserId(), RESULT.getUserId());
        assertEquals(fileResult.getFileId(), RESULT.getFileId());
        assertEquals(fileResult.getMimeType(), RESULT.getMimeType());
        assertEquals(fileResult.getCreatedOn(), RESULT.getCreatedOn());
        assertEquals(fileResult.getDownloadUrl(), RESULT.getDownloadUrl());

        verify(mapper).load(fileCaptor.capture());
        ParticipantFile loadedFile = fileCaptor.getValue();
        assertEquals(loadedFile.getUserId(), KEY.getUserId());
        assertEquals(loadedFile.getFileId(), KEY.getFileId());
    }

    @Test
    public void getParticipantFileNoSuchFile() {
        when(mapper.load(any())).thenReturn(null);

        Optional<ParticipantFile> result = dao.getParticipantFile(KEY.getUserId(), KEY.getFileId());
        assertFalse(result.isPresent());
    }

    @Test
    public void uploadParticipantFile() {
        dao.uploadParticipantFile(KEY);
        verify(mapper).save(KEY);
    }

    @Test
    public void deleteParticipantFile() {
        when(mapper.load(any())).thenReturn(RESULT);
        dao.deleteParticipantFile(KEY.getUserId(), KEY.getFileId());
        verify(mapper).delete(fileCaptor.capture());
        ParticipantFile deletedFile = fileCaptor.getValue();
        assertEquals(deletedFile.getFileId(), KEY.getFileId());
        assertEquals(deletedFile.getUserId(), KEY.getUserId());
    }

    @Test
    public void deleteParticipantFileNoSuchFile() {
        when(mapper.load(any())).thenReturn(null);
        dao.deleteParticipantFile(KEY.getUserId(), KEY.getFileId());
        verify(mapper, never()).delete(any());
    }

    @Test
    public void batchDeleteParticipantFiles() {
        // Copy RESULT_LIST to a new list because of Java type wonkiness.
        List<ParticipantFile> fileList = ImmutableList.copyOf(RESULT_LIST);
        dao.batchDeleteParticipantFiles(fileList);
        verify(mapper).batchDelete(same((fileList)));
    }
}
