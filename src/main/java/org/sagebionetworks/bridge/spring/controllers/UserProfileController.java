package org.sagebionetworks.bridge.spring.controllers;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.util.Map;
import java.util.Set;

import jakarta.annotation.Resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.cache.CacheKey;
import org.sagebionetworks.bridge.cache.ViewCache;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.JsonUtils;
import org.sagebionetworks.bridge.models.CriteriaContext;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.accounts.UserSessionInfo;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.services.ParticipantService;

@CrossOrigin
@RestController
public class UserProfileController extends BaseController {
    
    private static final String FIRST_NAME_FIELD = "firstName";
    private static final String LAST_NAME_FIELD = "lastName";
    private static final String EMAIL_FIELD = "email";
    private static final String USERNAME_FIELD = "username";
    private static final String TYPE_FIELD = "type";
    private static final String TYPE_VALUE = "UserProfile";
    private static final Set<String> DATA_GROUPS_SET = ImmutableSet.of("dataGroups");

    private ParticipantService participantService;
    
    private ViewCache viewCache;

    @Autowired
    final void setParticipantService(ParticipantService participantService) {
        this.participantService = participantService;
    }
    @Resource(name = "genericViewCache")
    final void setViewCache(ViewCache viewCache) {
        this.viewCache = viewCache;
    }

    @Deprecated
    @GetMapping(path={"/v3/users/self", "/api/v1/profile"}, produces={APPLICATION_JSON_VALUE})
    public String getUserProfile() {
        UserSession session = getAuthenticatedSession();
        App app = appService.getApp(session.getAppId());
        String userId = session.getId();
        
        CacheKey cacheKey = viewCache.getCacheKey(ObjectNode.class, userId, app.getIdentifier());
        String json = viewCache.getView(cacheKey, new Supplier<ObjectNode>() {
            @Override public ObjectNode get() {
                StudyParticipant participant = participantService.getParticipant(app, userId, false);
                ObjectNode node = JsonNodeFactory.instance.objectNode();
                if (participant.getFirstName() != null) {
                    node.put(FIRST_NAME_FIELD, participant.getFirstName());    
                }
                if (participant.getLastName() != null) {
                    node.put(LAST_NAME_FIELD, participant.getLastName());    
                }
                node.put(EMAIL_FIELD, participant.getEmail());
                node.put(USERNAME_FIELD, participant.getEmail());
                node.put(TYPE_FIELD, TYPE_VALUE);
                for (Map.Entry<String,String> entry : participant.getAttributes().entrySet()) {
                    node.put(entry.getKey(), entry.getValue());    
                }
                return node;
            }
        });
        return json;
    }

    @Deprecated
    @PostMapping({"/v3/users/self", "/api/v1/profile"})
    public JsonNode updateUserProfile() {
        UserSession session = getAuthenticatedSession();
        App app = appService.getApp(session.getAppId());
        String userId = session.getId();
        
        JsonNode node = parseJson(JsonNode.class);
        Map<String,String> attributes = Maps.newHashMap();
        for (String attrKey : app.getUserProfileAttributes()) {
            if (node.has(attrKey)) {
                attributes.put(attrKey, node.get(attrKey).asText());
            }
        }
        
        StudyParticipant participant = participantService.getParticipant(app, userId, false);
        
        StudyParticipant updated = new StudyParticipant.Builder().copyOf(participant)
                .withFirstName(JsonUtils.asText(node, "firstName"))
                .withLastName(JsonUtils.asText(node, "lastName"))
                .withAttributes(attributes)
                .withId(userId).build();
        participantService.updateParticipant(app, updated);
        
        StudyParticipant updatedParticipant = participantService.getParticipant(app, userId, true);
        CriteriaContext context = getCriteriaContext(session);
        
        sessionUpdateService.updateParticipant(session, context, updatedParticipant);
        
        CacheKey cacheKey = viewCache.getCacheKey(ObjectNode.class, userId, app.getIdentifier());
        viewCache.removeView(cacheKey);
        
        return UserSessionInfo.toJSON(session);
    }

    @Deprecated
    @GetMapping("/v3/users/self/dataGroups")
    public JsonNode getDataGroups() {
        UserSession session = getAuthenticatedSession();
        
        AccountId accountId = AccountId.forHealthCode(session.getAppId(), session.getHealthCode());
        Account account = accountService.getAccount(accountId)
                .orElseThrow(() -> new EntityNotFoundException(Account.class));        
        
        Set<String> dataGroups = account.getDataGroups();
        
        ArrayNode array = JsonNodeFactory.instance.arrayNode();
        dataGroups.stream().forEach(array::add);

        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.set("dataGroups", array);
        node.put(TYPE_FIELD, "DataGroups");

        return node;
    }
    
    @Deprecated
    @PostMapping("/v3/users/self/dataGroups")
    public JsonNode updateDataGroups() {
        UserSession session = getAuthenticatedSession();
        App app = appService.getApp(session.getAppId());
        
        StudyParticipant participant = participantService.getParticipant(app, session.getId(), false);
        
        StudyParticipant dataGroups = parseJson(StudyParticipant.class);
        
        StudyParticipant updated = new StudyParticipant.Builder().copyOf(participant)
                .copyFieldsOf(dataGroups, DATA_GROUPS_SET)
                .withId(session.getId()).build();
        
        participantService.updateParticipant(app, updated);
        
        RequestContext reqContext = RequestContext.get();
        
        CriteriaContext context = new CriteriaContext.Builder()
                .withLanguages(session.getParticipant().getLanguages())
                .withClientInfo(reqContext.getCallerClientInfo())
                .withHealthCode(session.getHealthCode())
                .withUserId(session.getId())
                .withUserDataGroups(updated.getDataGroups())
                .withUserStudyIds(updated.getStudyIds())
                .withAppId(session.getAppId())
                .build();
        
        sessionUpdateService.updateDataGroups(session, context);
        
        return UserSessionInfo.toJSON(session);
    }
}
