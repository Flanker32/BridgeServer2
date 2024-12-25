package org.sagebionetworks.bridge.dynamodb;

import static org.sagebionetworks.bridge.TestUtils.getNotificationRegistration;
import static org.sagebionetworks.bridge.TestUtils.getNotificationTopic;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.notifications.NotificationProtocol;
import org.sagebionetworks.bridge.models.notifications.NotificationRegistration;
import org.sagebionetworks.bridge.models.notifications.NotificationTopic;
import org.sagebionetworks.bridge.models.notifications.TopicSubscription;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.SubscribeRequest;
import com.amazonaws.services.sns.model.SubscribeResult;

public class DynamoTopicSubscriptionDaoTest {
    private static final String ENDPOINT_ARN = "endpoint-arn";
    private static final AmazonServiceException EXCEPTION = new AmazonServiceException("Somethin' bad happened [test]");
    private static final String PHONE = "+14255550123";
    private static final String TOPIC_ARN = "topic-arn";

    private DynamoTopicSubscriptionDao subDao;
    
    @Mock
    private DynamoDBMapper mockMapper;
    
    @Mock
    private AmazonSNS mockSnsClient;
    
    @Mock
    private SubscribeResult mockSubscribeResult;
    
    @Mock
    private DynamoTopicSubscription mockSubscription;
    
    @Captor
    private ArgumentCaptor<SubscribeRequest> subscribeRequestCaptor;
    
    @Captor
    private ArgumentCaptor<DynamoTopicSubscription> subscriptionCaptor;
    
    @Captor
    private ArgumentCaptor<String> unsubscribeStringCaptor;
    
    @BeforeMethod
    public void before() {
        MockitoAnnotations.initMocks(this);
        subDao = new DynamoTopicSubscriptionDao();
        subDao.setTopicSubscriptionMapper(mockMapper);
        subDao.setSnsClient(mockSnsClient);
    }
    
    @Test
    public void successfulSubscribePushNotification() {
        // Mock SNS.
        doReturn("subscriptionARN").when(mockSubscribeResult).getSubscriptionArn();
        doReturn(mockSubscribeResult).when(mockSnsClient).subscribe(any());

        // Make registration and topic.
        NotificationRegistration registration = getNotificationRegistration();
        registration.setProtocol(NotificationProtocol.APPLICATION);
        registration.setEndpoint(ENDPOINT_ARN);

        NotificationTopic topic = getNotificationTopic();
        topic.setTopicARN(TOPIC_ARN);

        // Execute and validate.
        TopicSubscription sub = subDao.subscribe(registration, topic);

        verify(mockSnsClient).subscribe(subscribeRequestCaptor.capture());
        
        SubscribeRequest request = subscribeRequestCaptor.getValue();
        assertEquals(request.getTopicArn(), TOPIC_ARN);
        assertEquals(request.getProtocol(), "application");
        assertEquals(request.getEndpoint(), ENDPOINT_ARN);
        
        verify(mockMapper).save(subscriptionCaptor.capture());
        
        TopicSubscription saved = subscriptionCaptor.getValue();
        assertEquals(saved.getRegistrationGuid(), "registrationGuid");
        assertEquals(saved.getSubscriptionARN(), "subscriptionARN");
        assertEquals(saved.getTopicGuid(), "topicGuid");
        
        assertEquals(sub.getRegistrationGuid(), "registrationGuid");
        assertEquals(sub.getSubscriptionARN(), "subscriptionARN");
        assertEquals(sub.getTopicGuid(), "topicGuid");
    }

    @Test
    public void successfulSubscribeSmsNotification() {
        // Mock SNS.
        doReturn("subscriptionARN").when(mockSubscribeResult).getSubscriptionArn();
        doReturn(mockSubscribeResult).when(mockSnsClient).subscribe(any());

        // Make registration and topic.
        NotificationRegistration registration = getNotificationRegistration();
        registration.setProtocol(NotificationProtocol.SMS);
        registration.setEndpoint(PHONE);

        NotificationTopic topic = getNotificationTopic();
        topic.setTopicARN(TOPIC_ARN);

        // Execute and validate. Only validate SNS subscribe request. Everything else is tested elsewhere.
        subDao.subscribe(registration, topic);

        verify(mockSnsClient).subscribe(subscribeRequestCaptor.capture());
        SubscribeRequest request = subscribeRequestCaptor.getValue();
        assertEquals(request.getTopicArn(), TOPIC_ARN);
        assertEquals(request.getProtocol(), "sms");
        assertEquals(request.getEndpoint(), PHONE);
    }

    @Test
    public void subscribeWhenSnsFails() {
        doThrow(EXCEPTION).when(mockSnsClient).subscribe(any(SubscribeRequest.class));
        
        NotificationRegistration registration = getNotificationRegistration();
        NotificationTopic topic = getNotificationTopic();

        try {
            subDao.subscribe(registration, topic);
        } catch(AmazonServiceException e) {
            verify(mockMapper, never()).save(subscriptionCaptor.capture());
        }
    }
    
    @Test
    public void subscribeWhenMapperFails() {
        doReturn("subscriptionARN").when(mockSubscribeResult).getSubscriptionArn();
        doReturn(mockSubscribeResult).when(mockSnsClient).subscribe(any());
        
        doThrow(EXCEPTION).when(mockMapper).save(any());
        
        NotificationRegistration registration = getNotificationRegistration();
        NotificationTopic topic = getNotificationTopic();
        
        try {
            subDao.subscribe(registration, topic);
            fail("expected exception");
        } catch(AmazonServiceException e) {
            // expected exception
        }
        
        verify(mockSnsClient).subscribe(any());
        verify(mockMapper).save(subscriptionCaptor.capture());
        verify(mockSnsClient).unsubscribe(unsubscribeStringCaptor.capture());
        
        TopicSubscription saved = subscriptionCaptor.getValue();
        assertEquals(saved.getRegistrationGuid(), "registrationGuid");
        assertEquals(saved.getSubscriptionARN(), "subscriptionARN");
        assertEquals(saved.getTopicGuid(), "topicGuid");
        
        String subscriptionARN = unsubscribeStringCaptor.getValue();
        assertEquals(subscriptionARN, "subscriptionARN");
    }
    
    @Test
    public void successfulUnsubscribe() {
        doReturn(mockSubscription).when(mockMapper).load(any());
        doReturn("subscriptionARN").when(mockSubscription).getSubscriptionARN();
        doReturn("registrationGuid").when(mockSubscription).getRegistrationGuid();
        doReturn("topicGuid").when(mockSubscription).getTopicGuid();
        
        NotificationRegistration registration = getNotificationRegistration();
        NotificationTopic topic = getNotificationTopic();
        
        subDao.unsubscribe(registration, topic);
        
        verify(mockMapper).load(subscriptionCaptor.capture());
        verify(mockSnsClient).unsubscribe(unsubscribeStringCaptor.capture());
        verify(mockMapper).delete(subscriptionCaptor.capture());
        
        DynamoTopicSubscription loadKey = subscriptionCaptor.getAllValues().getFirst();
        assertEquals(loadKey.getRegistrationGuid(), "registrationGuid");
        assertEquals(loadKey.getTopicGuid(), "topicGuid");
        
        String subscriptionARN = unsubscribeStringCaptor.getValue();
        assertEquals(subscriptionARN, "subscriptionARN");
        
        DynamoTopicSubscription deleteObj = subscriptionCaptor.getAllValues().get(1);
        assertEquals(deleteObj.getRegistrationGuid(), "registrationGuid");
        assertEquals(deleteObj.getTopicGuid(), "topicGuid");
    }
    
    @Test
    public void unsubscribeWhenSnsFails() {
        doReturn(mockSubscription).when(mockMapper).load(any());
        doReturn("subscriptionARN").when(mockSubscription).getSubscriptionARN();
        
        doThrow(EXCEPTION).when(mockSnsClient).unsubscribe(any(String.class));
        
        NotificationRegistration registration = getNotificationRegistration();
        NotificationTopic topic = getNotificationTopic();
        
        // Do not delete DDB record if the unsubscribe call fails for any reason.
        try {
            subDao.unsubscribe(registration, topic);
            fail("Should have thrown exception");
        } catch(AmazonServiceException e) {
            // expected exception
        }
        
        verify(mockMapper).load(subscriptionCaptor.capture());
        verify(mockSnsClient).unsubscribe(unsubscribeStringCaptor.capture());
        verify(mockMapper, never()).delete(subscriptionCaptor.capture());
        
        DynamoTopicSubscription loadKey = subscriptionCaptor.getAllValues().getFirst();
        assertEquals(loadKey.getRegistrationGuid(), "registrationGuid");
        assertEquals(loadKey.getTopicGuid(), "topicGuid");
        
        String subscriptionARN = unsubscribeStringCaptor.getValue();
        assertEquals(subscriptionARN, "subscriptionARN");
    }
    
    @Test
    public void unsubscribeWhenMapperFails() {
        doReturn(mockSubscription).when(mockMapper).load(any());
        doReturn("subscriptionARN").when(mockSubscription).getSubscriptionARN();
        doReturn("registrationGuid").when(mockSubscription).getRegistrationGuid();
        
        doThrow(EXCEPTION).when(mockMapper).delete(any());
        
        NotificationRegistration registration = getNotificationRegistration();
        NotificationTopic topic = getNotificationTopic();
        
        try {
            subDao.unsubscribe(registration, topic);
            fail("expected exception");
        } catch(AmazonServiceException e) {
            // expected exception
        }
        
        verify(mockMapper).load(subscriptionCaptor.capture());
        verify(mockSnsClient).unsubscribe(unsubscribeStringCaptor.capture());
        verify(mockMapper).delete(subscriptionCaptor.capture());
        
        DynamoTopicSubscription loadKey = subscriptionCaptor.getAllValues().getFirst();
        assertEquals(loadKey.getRegistrationGuid(), "registrationGuid");
        assertEquals(loadKey.getTopicGuid(), "topicGuid");
        
        String subscriptionARN = unsubscribeStringCaptor.getValue();
        assertEquals(subscriptionARN, "subscriptionARN");
        
        DynamoTopicSubscription deleteObj = subscriptionCaptor.getAllValues().get(1);
        assertEquals(deleteObj.getRegistrationGuid(), "registrationGuid");
        
        // This leaves no SNS topic subscription, but a record in DDB. This is okay and we will
        // attempt to clean this up from the service any time the user updates their subscriptions.
    }
    
    @Test
    public void deleteWorks() {
        TopicSubscription subscription = TopicSubscription.create();
        subscription.setSubscriptionARN("subscriptionARN");
        
        subDao.removeOrphanedSubscription(subscription);
        
        verify(mockSnsClient).unsubscribe(unsubscribeStringCaptor.capture());
        String subscriptionARN = unsubscribeStringCaptor.getValue();
        assertEquals(subscriptionARN, "subscriptionARN");
        
        verify(mockMapper).delete(subscriptionCaptor.capture());
        DynamoTopicSubscription capturedSub = subscriptionCaptor.getValue();
        assertEquals(capturedSub, subscription);
    }
    
    @Test
    public void deleteTriesBothStoresIfSnsFails() {
        TopicSubscription subscription = TopicSubscription.create();
        subscription.setSubscriptionARN("subscriptionARN");
        
        doThrow(EXCEPTION).when(mockSnsClient).unsubscribe(anyString());
        
        try {
            subDao.removeOrphanedSubscription(subscription);
            fail("expected exception");
        } catch(AmazonServiceException e) {
            // expected exception
        }
        
        verify(mockSnsClient).unsubscribe(unsubscribeStringCaptor.capture());
        String subscriptionARN = unsubscribeStringCaptor.getValue();
        assertEquals(subscriptionARN, "subscriptionARN");
        
        verify(mockMapper).delete(subscriptionCaptor.capture());
        DynamoTopicSubscription capturedSub = subscriptionCaptor.getValue();
        assertEquals(capturedSub, subscription);
    }
}
