package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sagebionetworks.bridge.BridgeConstants.SHARED_APP_ID;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolder;
import org.sagebionetworks.bridge.models.GuidCreatedOnVersionHolderImpl;
import org.sagebionetworks.bridge.models.sharedmodules.SharedModuleImportStatus;
import org.sagebionetworks.bridge.models.sharedmodules.SharedModuleMetadata;
import org.sagebionetworks.bridge.models.sharedmodules.SharedModuleType;
import org.sagebionetworks.bridge.models.surveys.Survey;
import org.sagebionetworks.bridge.models.upload.UploadSchema;

/**
 * Service for importing shared modules into the local app, and recursively importing schemas and surveys as well.
 */
@Component
public class SharedModuleService {
    private static final Logger LOG = LoggerFactory.getLogger(SharedModuleService.class);

    private SharedModuleMetadataService moduleMetadataService;
    private UploadSchemaService schemaService;
    private SurveyService surveyService;

    /** Module metadata service, so we can know what module we need to import. Configured by Spring. */
    @Autowired
    public final void setModuleMetadataService(SharedModuleMetadataService moduleMetadataService) {
        this.moduleMetadataService = moduleMetadataService;
    }

    /**
     * Schema service, used to get the schema from shared and write the schema to the local app, where applicable.
     * Configured by Spring.
     */
    @Autowired
    public final void setSchemaService(UploadSchemaService schemaService) {
        this.schemaService = schemaService;
    }

    /** Survey service, also used to copy from shared to local. Configured by Spring */
    @Autowired
    public final void setSurveyService(SurveyService surveyService) {
        this.surveyService = surveyService;
    }

    /** Imports a specific module version into the specified app. */
    public SharedModuleImportStatus importModuleByIdAndVersion(String appId, String moduleId,
            int moduleVersion) {
        // appId is provided by the controller. Validate the rest of the args.
        if (StringUtils.isBlank(moduleId)) {
            throw new BadRequestException("module ID must be specified");
        }
        if (moduleVersion <= 0) {
            throw new BadRequestException("moduleVersion must be positive");
        }

        // Get metadata and import.
        SharedModuleMetadata metadata = moduleMetadataService.getMetadataByIdAndVersion(moduleId, moduleVersion);
        return importModule(appId, metadata);
    }

    /** Imports the latest published version of a module into the specified app. */
    public SharedModuleImportStatus importModuleByIdLatestPublishedVersion(String appId, String moduleId) {
        // appId is provided by the controller. Validate the rest of the args.
        if (StringUtils.isBlank(moduleId)) {
            throw new BadRequestException("module ID must be specified");
        }

        // Query for most recent published version.
        List<SharedModuleMetadata> metadataList = moduleMetadataService.queryMetadataById(moduleId, true, true, null,
                null, null, false);
        if (metadataList.isEmpty()) {
            throw new EntityNotFoundException(SharedModuleMetadata.class);
        }
        if (metadataList.size() > 1) {
            // Error, because this represents a coding error that we should fix.
            LOG.error("Module " + moduleId + " has more than one most recent publisher version.");
        }
        return importModule(appId, metadataList.getFirst());
    }

    // Helper method to import a module given the module metadata object.
    private SharedModuleImportStatus importModule(String appId, SharedModuleMetadata metadata) {
        // Callers will have passed in a non-null metadata object. This preconditions check is just to check against
        // bad coding.
        checkNotNull(metadata, "metadata must be specified");
        String moduleId = metadata.getId();
        int moduleVersion = metadata.getVersion();

        SharedModuleType moduleType = metadata.getModuleType();
        if (moduleType == SharedModuleType.SCHEMA) {
            // Copy schema from shared to local.
            String schemaId = metadata.getSchemaId();
            int schemaRev = metadata.getSchemaRevision();
            UploadSchema schema = schemaService.getUploadSchemaByIdAndRev(SHARED_APP_ID, schemaId, schemaRev);

            // annotate with module ID and version
            schema.setModuleId(moduleId);
            schema.setModuleVersion(moduleVersion);

            schemaService.createSchemaRevisionV4(appId, schema);

            // Schema ID and rev are the same in the shared app and in the local app.
            return new SharedModuleImportStatus(schemaId, schemaRev);
        } else if (moduleType == SharedModuleType.SURVEY) {
            // Copy survey from shared to local.
            String sharedSurveyGuid = metadata.getSurveyGuid();
            long sharedSurveyCreatedOn = metadata.getSurveyCreatedOn();
            GuidCreatedOnVersionHolder sharedSurveyKey = new GuidCreatedOnVersionHolderImpl(sharedSurveyGuid,
                    sharedSurveyCreatedOn);
            Survey sharedSurvey = surveyService.getSurvey(SHARED_APP_ID, sharedSurveyKey, true, true);

            // annotate survey with module ID and version
            sharedSurvey.setModuleId(moduleId);
            sharedSurvey.setModuleVersion(moduleVersion);

            // Survey keys don't include appId. Instead, we need to set the appId directly in the survey object.
            sharedSurvey.setAppId(appId);
            Survey localSurvey = surveyService.createSurvey(sharedSurvey);
            GuidCreatedOnVersionHolder localSurveyKey = new GuidCreatedOnVersionHolderImpl(localSurvey.getGuid(),
                    localSurvey.getCreatedOn());

            // Publish the survey, so that (a) the survey is ready for immediate use and (b) if someone changes the
            // survey, we preserve the "official" survey version from the shared library.
            //
            // Cut a new schema rev, because if somehow this conflicts with an existing survey, we want to have a clean
            // version so it doesn't get munged with an "unofficial" version.
            surveyService.publishSurvey(appId, localSurveyKey, true);

            // Survey GUID and createdOn are changed when we create the survey in a new app. Return the new ones.
            return new SharedModuleImportStatus(localSurveyKey.getGuid(), localSurveyKey.getCreatedOn());
        } else {
            // If we ever hit this code block, something has gone terribly terribly wrong.
            throw new IllegalStateException("Unexpected error: Somehow, module is neither a schema nor survey.");
        }
    }
}
