package org.sagebionetworks.bridge.models.apps;

import static org.sagebionetworks.bridge.TestConstants.TEST_APP_ID;
import static org.testng.Assert.assertEquals;

import java.util.LinkedHashSet;
import java.util.List;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.testng.annotations.Test;

import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.dynamodb.DynamoApp;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.accounts.Account;

public class AppAndUsersTest {
    private static final String TEST_APP_NAME = "test=app-name";
    private static final String TEST_USER_EMAIL = "test+user@email.com";
    private static final String TEST_USER_EMAIL_2 = "test+user+2@email.com";
    private static final String TEST_USER_FIRST_NAME = "test_user_first_name";
    private static final String TEST_USER_LAST_NAME = "test_user_last_name";
    private static final String TEST_USER_PASSWORD = "test_user_password";
    private static final String TEST_ADMIN_ID_1 = "3346407";
    private static final String TEST_ADMIN_ID_2 = "3348228";

    @Test
    public void deserializeWithStudyProperty() throws Exception {
        deserializeCorrectly("study");
    }
    
    @Test
    public void deserializeWithAppProperty() throws Exception {
        deserializeCorrectly("app");
    }
    
    private void deserializeCorrectly(String appFieldName) throws Exception {
        // mock
        String json = "{" +
                "  \"adminIds\": [\"3346407\", \"3348228\"]," +
                "  \"" + appFieldName + "\": {" +
                "    \"identifier\": \""+TEST_APP_ID+"\"," +
                "    \"supportEmail\": \"test+user@email.com\"," +
                "    \"name\": \"test=app-name\"," +
                "    \"active\": \"true\"" +
                "  }," +
                "  \"users\": [" +
                "    {" +
                "      \"firstName\": \"test_user_first_name\"," +
                "      \"lastName\": \"test_user_last_name\"," +
                "      \"email\": \"test+user@email.com\"," +
                "      \"password\": \"test_user_password\"," +
                "      \"roles\": [\"developer\",\"researcher\"]" +
                "    }," +
                "    {" +
                "      \"firstName\": \"test_user_first_name\"," +
                "      \"lastName\": \"test_user_last_name\"," +
                "      \"email\": \"test+user+2@email.com\"," +
                "      \"password\": \"test_user_password\"," +
                "      \"roles\": [\"researcher\"]" +
                "    }" +
                "  ]" +
                "}";

        App app = new DynamoApp();
        app.setActive(true);
        app.setIdentifier(TEST_APP_ID);
        app.setName(TEST_APP_NAME);
        app.setSupportEmail(TEST_USER_EMAIL);

        // make it ordered
        LinkedHashSet<Roles> user1Roles = new LinkedHashSet<>();
        user1Roles.add(Roles.RESEARCHER);
        user1Roles.add(Roles.DEVELOPER);

        Account mockUser1 = Account.create();
        mockUser1.setEmail(TEST_USER_EMAIL);
        mockUser1.setFirstName(TEST_USER_FIRST_NAME);
        mockUser1.setLastName(TEST_USER_LAST_NAME);
        mockUser1.setRoles(ImmutableSet.copyOf(user1Roles));
        mockUser1.setPassword(TEST_USER_PASSWORD);

        Account mockUser2 = Account.create();
        mockUser2.setEmail(TEST_USER_EMAIL_2);
        mockUser2.setFirstName(TEST_USER_FIRST_NAME);
        mockUser2.setLastName(TEST_USER_LAST_NAME);
        mockUser2.setRoles(ImmutableSet.of(Roles.RESEARCHER));
        mockUser2.setPassword(TEST_USER_PASSWORD);

        List<String> adminIds = ImmutableList.of(TEST_ADMIN_ID_1, TEST_ADMIN_ID_2);

        AppAndUsers retAppAndUsers = BridgeObjectMapper.get().readValue(json, AppAndUsers.class);
        List<String> retAdminIds = retAppAndUsers.getAdminIds();
        App retApp = retAppAndUsers.getApp();
        List<Account> userList = retAppAndUsers.getUsers();

        // verify
        assertEquals(retAdminIds, adminIds);
        assertEquals(retApp, app);
        
        Account acct1 = userList.getFirst();
        assertEquals(acct1.getEmail(), mockUser1.getEmail());
        assertEquals(acct1.getFirstName(), TEST_USER_FIRST_NAME);
        assertEquals(acct1.getLastName(), TEST_USER_LAST_NAME);
        assertEquals(acct1.getRoles(), user1Roles);
        assertEquals(acct1.getPassword(), TEST_USER_PASSWORD);

        Account acct2 = userList.get(1);
        assertEquals(acct2.getEmail(), TEST_USER_EMAIL_2);
        assertEquals(acct2.getFirstName(), TEST_USER_FIRST_NAME);
        assertEquals(acct2.getLastName(), TEST_USER_LAST_NAME);
        assertEquals(acct2.getRoles(), ImmutableSet.of(Roles.RESEARCHER));
        assertEquals(acct2.getPassword(), TEST_USER_PASSWORD);
    }
}
