package org.sagebionetworks.bridge.services.email;

import java.util.Set;

import jakarta.mail.MessagingException;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.models.apps.App;

import com.google.common.collect.Iterables;

public abstract class MimeTypeEmailProvider {

    private App app;
    
    protected MimeTypeEmailProvider(App app) {
        this.app = app;
    }
    
    /**
     * Get the sender email address without any further formatting. For example, if the provider formats the sender
     * email as <code>"App Name" &lt;email@email.com&gtl</code>, This method returns only <code>email@email.com</code>.
     */
    public String getPlainSenderEmail() {
        Set<String> senderEmails = BridgeUtils.commaListToOrderedSet(getApp().getSupportEmail());
        return Iterables.getFirst(senderEmails, null);
    }
    
    /**
     * Get the sender email address as formatted for an email. For example, if the provider's email address is
     * <code>email@email.com</code>, this method would return something like
     * <code>"App Name" &lt;email@email.com&gtl</code>.
     */
    public String getFormattedSenderEmail() {
        String senderEmail = getPlainSenderEmail();
        return "%s <%s>".formatted(getApp().getName(), senderEmail);
    }
    public App getApp() {
        return app;
    }
    
    public abstract MimeTypeEmail getMimeTypeEmail() throws MessagingException;
    
}
