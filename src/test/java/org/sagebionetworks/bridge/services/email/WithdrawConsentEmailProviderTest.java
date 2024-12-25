package org.sagebionetworks.bridge.services.email;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.List;

import jakarta.mail.internet.MimeBodyPart;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.Withdrawal;
import org.sagebionetworks.bridge.models.apps.App;

public class WithdrawConsentEmailProviderTest {

    private static final long UNIX_TIMESTAMP = 1446073435148L;
    private static final Withdrawal WITHDRAWAL = new Withdrawal("<p>Because, reasons.</p>");
    
    private WithdrawConsentEmailProvider provider;
    private App app;
    private Account account;
    
    @BeforeMethod
    public void before() {
        app = mock(App.class);
        
        account = Account.create();
        account.setEmail("d@d.com");
        
        provider = new WithdrawConsentEmailProvider(app, account, WITHDRAWAL, UNIX_TIMESTAMP);
    }
    
    @Test
    public void canGenerateMinimalEmail() throws Exception {
        when(app.getConsentNotificationEmail()).thenReturn("a@a.com");
        when(app.isConsentNotificationEmailVerified()).thenReturn(true);
        when(app.getName()).thenReturn("App Name");
        when(app.getSupportEmail()).thenReturn("c@c.com");

        provider = new WithdrawConsentEmailProvider(app, account, new Withdrawal(null), UNIX_TIMESTAMP);
        MimeTypeEmail email = provider.getMimeTypeEmail();
        assertEquals(email.getType(), EmailType.WITHDRAW_CONSENT);
        
        List<String> recipients = email.getRecipientAddresses();
        assertEquals(recipients.size(), 1);
        assertEquals(recipients.getFirst(), "a@a.com");
        
        String sender = email.getSenderAddress();
        assertEquals(sender, "\"App Name\" <c@c.com>");
        
        assertEquals(email.getSubject(), "Notification of consent withdrawal for App Name");
        
        MimeBodyPart body = email.getMessageParts().getFirst();
        assertEquals(body.getContent(),
                "<p>User   &lt;d@d.com&gt; withdrew from the study on October 28, 2015. </p><p>Reason:</p><p><i>No reason given.</i></p>");
    }

    @Test
    public void canGenerateMaximalEmail() throws Exception {
        when(app.getConsentNotificationEmail()).thenReturn("a@a.com, b@b.com");
        when(app.isConsentNotificationEmailVerified()).thenReturn(true);
        when(app.getName()).thenReturn("App Name");
        when(app.getSupportEmail()).thenReturn("c@c.com");
        account.setFirstName("<b>Jack</b>");
        account.setLastName("<i>Aubrey</i>");

        provider = new WithdrawConsentEmailProvider(app, account, WITHDRAWAL, UNIX_TIMESTAMP);
        MimeTypeEmail email = provider.getMimeTypeEmail();
        assertEquals(email.getType(), EmailType.WITHDRAW_CONSENT);

        List<String> recipients = email.getRecipientAddresses();
        assertEquals(recipients.size(), 2);
        assertEquals(recipients.getFirst(), "a@a.com");
        assertEquals(recipients.get(1), "b@b.com");
        
        String sender = email.getSenderAddress();
        assertEquals(sender, "\"App Name\" <c@c.com>");
        
        assertEquals(email.getSubject(), "Notification of consent withdrawal for App Name");
        
        MimeBodyPart body = email.getMessageParts().getFirst();
        assertEquals(body.getContent(),
                "<p>User Jack Aubrey &lt;d@d.com&gt; withdrew from the study on October 28, 2015. </p><p>Reason:</p><p>Because, reasons.</p>");
    }
    
    @Test
    public void unverifiedStudyConsentEmailGeneratesNoRecipients() {
        app.setConsentNotificationEmailVerified(false);
        provider = new WithdrawConsentEmailProvider(app, account, WITHDRAWAL, UNIX_TIMESTAMP);
        
        assertTrue(provider.getRecipients().isEmpty());
    }
    
    @Test
    public void nullStudyConsentEmailGeneratesNoRecipients() {
        // email shouldn't be verified if it is null, but regardless, there should still be no recipients
        app.setConsentNotificationEmailVerified(true); 
        app.setConsentNotificationEmail(null);
        provider = new WithdrawConsentEmailProvider(app, account, WITHDRAWAL, UNIX_TIMESTAMP);
        
        assertTrue(provider.getRecipients().isEmpty());
    }
}
