package org.sagebionetworks.bridge.services.email;

import static org.testng.Assert.assertEquals;

import java.net.URLEncoder;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.config.BridgeConfigFactory;
import org.sagebionetworks.bridge.dynamodb.DynamoApp;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.apps.MimeType;
import org.sagebionetworks.bridge.models.templates.TemplateRevision;

public class EmailSignInEmailProviderTest {

    private static final String RECIPIENT_EMAIL = "recipient@recipient.com";
    private static final String SUBJECT_TEMPLATE = "${appName} sign in link";
    private static final String BODY_TEMPLATE = "Click here to sign in: <a href=\"" +
            "https://${host}/mobile/startSession.html?email=${email}&appId=${appId}&token=${token}\""+
            ">https://${host}/mobile/startSession.html?email=${email}&appId=${appId}&token=${token}</a>";

    @Test
    public void testProvider() throws Exception {
        App app = new DynamoApp();
        app.setName("App name");
        app.setIdentifier("foo");
        app.setSupportEmail("support@email.com");
        
        TemplateRevision revision = TemplateRevision.create();
        revision.setSubject(SUBJECT_TEMPLATE);
        revision.setDocumentContent(BODY_TEMPLATE);
        revision.setMimeType(MimeType.HTML);
        
        // Verifying in particular that all instances of a template variable are replaced
        // in the template.
        BasicEmailProvider provider = new BasicEmailProvider.Builder()
                .withApp(app)
                .withTemplateRevision(revision)
                .withRecipientEmail(RECIPIENT_EMAIL)
                .withToken("email", BridgeUtils.encodeURIComponent(RECIPIENT_EMAIL))
                .withToken("token", "ABC").build();
        
        String url = "https://%s/mobile/startSession.html?email=%s&appId=foo&token=ABC".formatted(
                BridgeConfigFactory.getConfig().getHostnameWithPostfix("ws"),
                URLEncoder.encode(RECIPIENT_EMAIL, "UTF-8"));
        
        String finalBody = "Click here to sign in: <a href=\"%s\">%s</a>".formatted(url, url);
        
        MimeTypeEmail email = provider.getMimeTypeEmail();
        assertEquals(email.getSenderAddress(), "\"App name\" <support@email.com>");
        assertEquals(email.getRecipientAddresses().getFirst(), RECIPIENT_EMAIL);
        assertEquals(email.getSubject(), "App name sign in link");
        assertEquals(email.getMessageParts().getFirst().getContent(), finalBody);
    }
    
}
