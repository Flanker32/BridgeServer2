package org.sagebionetworks.bridge.services;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.Properties;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.services.email.MimeTypeEmail;
import org.sagebionetworks.bridge.services.email.MimeTypeEmailProvider;

import com.amazonaws.services.simpleemail.model.MessageRejectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailService;
import com.amazonaws.services.simpleemail.model.RawMessage;
import com.amazonaws.services.simpleemail.model.SendRawEmailRequest;
import com.amazonaws.services.simpleemail.model.SendRawEmailResult;
import com.google.common.base.Charsets;

@Component("sendEmailViaAmazonService")
public class SendMailViaAmazonService implements SendMailService {

    private static final Logger logger = LoggerFactory.getLogger(SendMailViaAmazonService.class);
    public static final String UNVERIFIED_EMAIL_ERROR = "Bridge cannot send email until you verify Amazon SES can send using your app's support email address";

    private AmazonSimpleEmailService emailClient;
    private EmailVerificationService emailVerificationService;

    @Autowired
    final void setEmailClient(AmazonSimpleEmailService emailClient) {
        this.emailClient = emailClient;
    }
    @Autowired
    final void setEmailVerificationService(EmailVerificationService emailVerificationService) {
        this.emailVerificationService = emailVerificationService;
    }
    
    @Override
    public void sendEmail(MimeTypeEmailProvider provider) {
        String senderEmail = provider.getPlainSenderEmail();
        if (!emailVerificationService.isVerified(senderEmail)) {
            throw new BridgeServiceException(UNVERIFIED_EMAIL_ERROR);
        }

        try {
            String fullSenderEmail = provider.getMimeTypeEmail().getSenderAddress();
            MimeTypeEmail email = provider.getMimeTypeEmail();
            for (String recipient: email.getRecipientAddresses()) {
                sendEmail(fullSenderEmail, recipient, email, provider.getApp().getIdentifier());
            }
        } catch (MessageRejectedException ex) {
            // This happens if the sender email is not verified in SES. In general, it's not useful to app users to
            // receive a 500 Internal Error when this happens. Plus, if this exception gets thrown, the user session
            // won't be updated properly, and really weird things happen. The best course of option is to log an error
            // and swallow the exception.
            logger.error("SES rejected email: " + ex.getMessage(), ex);
        } catch(MessagingException | AmazonServiceException | IOException e) {
            throw new BridgeServiceException(e);
        }
    }

    private void sendEmail(String senderEmail, String recipient, MimeTypeEmail email, String appId)
            throws AmazonClientException, MessagingException, IOException {
        
        Session mailSession = Session.getInstance(new Properties(), null);
        MimeMessage mimeMessage = new MimeMessage(mailSession);
        mimeMessage.setFrom(new InternetAddress(senderEmail));
        mimeMessage.setSubject(email.getSubject(), Charsets.UTF_8.name());
        mimeMessage.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient));

        MimeMultipart mimeMultipart = new MimeMultipart();
        for (MimeBodyPart part : email.getMessageParts()) {
            if (part != null) {
                mimeMultipart.addBodyPart(part);    
            }
        }

        // Convert MimeMessage to raw text to send to SES.
        mimeMessage.setContent(mimeMultipart);
        ByteArrayOutputStream byteOutputStream = new ByteArrayOutputStream();
        mimeMessage.writeTo(byteOutputStream);
        RawMessage sesRawMessage = new RawMessage(ByteBuffer.wrap(byteOutputStream.toByteArray()));

        SendRawEmailRequest req = new SendRawEmailRequest(sesRawMessage);
        req.setSource(senderEmail);
        req.setDestinations(Collections.singleton(recipient));
        SendRawEmailResult result = emailClient.sendRawEmail(req);

        logger.info("Sent email to SES with messageID " + result.getMessageId() + " with type " +
                        email.getType() + " for app " + appId + " and request " + RequestContext.get().getId());
    }
    
}
