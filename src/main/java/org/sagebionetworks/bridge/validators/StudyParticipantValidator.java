package org.sagebionetworks.bridge.validators;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.sagebionetworks.bridge.BridgeUtils.isValidTimeZoneID;
import static org.sagebionetworks.bridge.validators.Validate.CANNOT_BE_BLANK;
import static org.sagebionetworks.bridge.validators.Validate.INVALID_EMAIL_ERROR;
import static org.sagebionetworks.bridge.validators.Validate.INVALID_PHONE_ERROR;
import static org.sagebionetworks.bridge.validators.Validate.OWASP_REGEXP_VALID_EMAIL;
import static org.sagebionetworks.bridge.validators.Validate.INVALID_TIME_ZONE;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.TEXT_SIZE;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateJsonLength;
import static org.sagebionetworks.bridge.validators.ValidatorUtils.validateStringLength;

import java.util.Map;
import java.util.Set;

import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.models.accounts.Phone;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.apps.PasswordPolicy;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.StudyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StudyParticipantValidator implements Validator {
    private static final Logger LOG = LoggerFactory.getLogger(StudyParticipantValidator.class);

    private final StudyService studyService;
    private final App app;
    private final boolean isNew;
    
    public StudyParticipantValidator(StudyService studyService, App app, boolean isNew) {
        this.studyService = studyService;
        this.app = app;
        this.isNew = isNew;
    }
    
    @Override
    public boolean supports(Class<?> clazz) {
        return StudyParticipant.class.isAssignableFrom(clazz);
    }

    @Override
    public void validate(Object object, Errors errors) {
        StudyParticipant participant = (StudyParticipant)object;
        
        // This is an intermediary step to outright preventing these values from 
        // being supplied via our participant APIs (such accounts should be managed
        // through the /v1/accounts APIs for administrative accounts).
        if (participant.getOrgMembership() != null) {
            LOG.warn("Study participant created with an org membership by caller "
                    + RequestContext.get().getCallerUserId());
            errors.rejectValue("orgMembership", "is prohibited for study participants");
        }
        if (!participant.getRoles().isEmpty()) {
            LOG.warn("Study participant created with roles by caller "
                    + RequestContext.get().getCallerUserId());
            errors.rejectValue("roles", "are prohibited for study participants");
        }
        if (participant.getSynapseUserId() != null) {
            LOG.warn("Study participant created with an synapseUserId by caller "
                    + RequestContext.get().getCallerUserId());
            errors.rejectValue("synapseUserId", "is prohibited for study participants");
        }
        if (isNew) {
            if (!ValidatorUtils.participantHasValidIdentifier(participant)) {
                errors.reject("email, phone, or externalId is required");
            }
            // If provided, phone must be valid
            Phone phone = participant.getPhone();
            if (phone != null && !Phone.isValid(phone)) {
                errors.rejectValue("phone", INVALID_PHONE_ERROR);
            }
            // If provided, email must be valid. Commons email validator v1.7 causes our test to 
            // fail because the word "test" appears in the user name, for reasons I could not 
            // deduce from their code. So we have switched to using OWASP regular expression to 
            // match valid email addresses.
            String email = participant.getEmail();
            if (email != null && !email.matches(OWASP_REGEXP_VALID_EMAIL)) {
                errors.rejectValue("email", INVALID_EMAIL_ERROR);
            }
            // External ID is required for non-administrative accounts when it is required on sign-up.
            if (app.isExternalIdRequiredOnSignup() && participant.getExternalIds().isEmpty()) {
                errors.rejectValue("externalId", "is required");
            }
            // Password is optional, but validation is applied if supplied, any time it is 
            // supplied (such as in the password reset workflow).
            String password = participant.getPassword();
            if (password != null) {
                PasswordPolicy passwordPolicy = app.getPasswordPolicy();
                ValidatorUtils.password(errors, passwordPolicy, password);
            }
            
            // External IDs can be updated during creation or on update. If it's already assigned to another user, 
            // the database constraints will prevent this record's persistence.
            if (isNotBlank(participant.getExternalId()) && participant.getExternalIds().isEmpty()) {
                errors.rejectValue("externalId", "must now be supplied in the externalIds property that maps a study ID to the new external ID");    
            }
            if (participant.getExternalIds() != null) {
                for (Map.Entry<String, String> entry : participant.getExternalIds().entrySet()) {
                    String studyId = entry.getKey();
                    String externalId = entry.getValue();
                    Study study = studyService.getStudy(app.getIdentifier(), studyId, false);
                    if (study == null) {
                        errors.rejectValue("externalIds["+studyId+"]", "is not a study");
                    }
                    if (isBlank(externalId)) {
                        errors.rejectValue("externalIds["+studyId+"].externalId", CANNOT_BE_BLANK);
                    }
                    validateStringLength(errors, 255, externalId, "externalIds["+studyId+"].externalId");
                }
            }
        } else {
            if (isBlank(participant.getId())) {
                errors.rejectValue("id", "is required");
            }
        }
        for (String dataGroup : participant.getDataGroups()) {
            if (!app.getDataGroups().contains(dataGroup)) {
                errors.rejectValue("dataGroups", messageForSet(app.getDataGroups(), dataGroup));
            }
        }
        for (String attributeName : participant.getAttributes().keySet()) {
            if (!app.getUserProfileAttributes().contains(attributeName)) {
                errors.rejectValue("attributes", messageForSet(app.getUserProfileAttributes(), attributeName));
            } else {
                String attributeValue = participant.getAttributes().get(attributeName);
                validateStringLength(errors, 255, attributeValue,"attributes["+attributeName+"]");
            }
        }
        if (!isValidTimeZoneID(participant.getClientTimeZone(), false)) {
            errors.rejectValue("clientTimeZone", INVALID_TIME_ZONE);
        }
        validateStringLength(errors, 255, participant.getEmail(), "email");
        validateStringLength(errors, 255, participant.getFirstName(), "firstName");
        validateStringLength(errors, 255, participant.getLastName(), "lastName");
        validateStringLength(errors, TEXT_SIZE, participant.getNote(), "note");
        validateJsonLength(errors, TEXT_SIZE, participant.getClientData(), "clientData");
    }

    private String messageForSet(Set<String> set, String fieldName) {
        return "'%s' is not defined for app (use %s)".formatted(
                fieldName, BridgeUtils.COMMA_SPACE_JOINER.join(set));
    }
}
