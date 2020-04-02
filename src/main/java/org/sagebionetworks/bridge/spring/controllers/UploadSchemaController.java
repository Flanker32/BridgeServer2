package org.sagebionetworks.bridge.spring.controllers;

import static org.sagebionetworks.bridge.Roles.ADMIN;
import static org.sagebionetworks.bridge.Roles.DEVELOPER;
import static org.sagebionetworks.bridge.Roles.RESEARCHER;
import static org.sagebionetworks.bridge.Roles.WORKER;
import static org.sagebionetworks.bridge.models.upload.UploadSchema.PUBLIC_SCHEMA_WRITER;
import static org.springframework.http.MediaType.APPLICATION_JSON_UTF8_VALUE;

import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.sagebionetworks.bridge.models.ResourceList;
import org.sagebionetworks.bridge.models.StatusMessage;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.upload.UploadSchema;
import org.sagebionetworks.bridge.services.UploadSchemaService;

@CrossOrigin
@RestController
public class UploadSchemaController extends BaseController {
    static final StatusMessage DELETED_REVISION_MSG = new StatusMessage("Schema revision has been deleted.");
    static final StatusMessage DELETED_MSG = new StatusMessage("Schemas have been deleted.");
    private UploadSchemaService uploadSchemaService;

    /** Service handler for Upload Schema APIs. This is configured by Spring. */
    @Autowired
    final void setUploadSchemaService(UploadSchemaService uploadSchemaService) {
        this.uploadSchemaService = uploadSchemaService;
    }

    /**
     * Service handler for creating a new schema revision, using V4 API semantics. See
     * {@link org.sagebionetworks.bridge.dao.UploadSchemaDao#createSchemaRevisionV4}
     *
     * @return Play result, with the created schema
     */
    @PostMapping(path="/v4/uploadschemas", produces={APPLICATION_JSON_UTF8_VALUE})
    @ResponseStatus(HttpStatus.CREATED)
    public String createSchemaRevisionV4() throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        String studyId = session.getStudyIdentifier();

        UploadSchema uploadSchema = parseJson(UploadSchema.class);
        UploadSchema createdSchema = uploadSchemaService.createSchemaRevisionV4(studyId, uploadSchema);
        return PUBLIC_SCHEMA_WRITER.writeValueAsString(createdSchema);
    }

    /**
     * Play controller for POST /researcher/v1/uploadSchema/:schemaId. This API creates an upload schema, using the
     * study for the current service endpoint and the schema of the specified schema. If the schema already exists,
     * this method updates it instead.
     *
     * @return Play result, with the created or updated schema in JSON format
     */
    @PostMapping(path="/v3/uploadschemas", produces={APPLICATION_JSON_UTF8_VALUE})
    public String createOrUpdateUploadSchema() throws JsonProcessingException, IOException {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        String studyId = session.getStudyIdentifier();
        
        UploadSchema uploadSchema = parseJson(UploadSchema.class);
        UploadSchema createdSchema = uploadSchemaService.createOrUpdateUploadSchema(studyId, uploadSchema);
        return PUBLIC_SCHEMA_WRITER.writeValueAsString(createdSchema);
    }

    @DeleteMapping("/v3/uploadschemas/{schemaId}")
    public StatusMessage deleteAllRevisionsOfUploadSchema(@PathVariable String schemaId,
            @RequestParam(defaultValue = "false") boolean physical) {
        UserSession session = getAuthenticatedSession(DEVELOPER, ADMIN);
        
        if (physical && session.isInRole(ADMIN)) {
            uploadSchemaService.deleteUploadSchemaByIdPermanently(session.getStudyIdentifier(), schemaId);
        } else {
            uploadSchemaService.deleteUploadSchemaById(session.getStudyIdentifier(), schemaId);    
        }
        return DELETED_MSG;
    }
    
    @DeleteMapping("/v3/uploadschemas/{schemaId}/revisions/{revision}")
    public StatusMessage deleteSchemaRevision(@PathVariable String schemaId, @PathVariable int revision,
            @RequestParam(defaultValue = "false") boolean physical) {
        UserSession session = getAuthenticatedSession(DEVELOPER, ADMIN);
        
        if (physical && session.isInRole(ADMIN)) {
            uploadSchemaService.deleteUploadSchemaByIdAndRevisionPermanently(session.getStudyIdentifier(), schemaId, revision);
        } else {
            uploadSchemaService.deleteUploadSchemaByIdAndRevision(session.getStudyIdentifier(), schemaId, revision);
        }
        return DELETED_REVISION_MSG;
    }

    /**
     * Play controller for GET /researcher/v1/uploadSchema/byId/:schemaId. This API fetches the upload schema with the
     * specified ID. If there is more than one revision of the schema, this fetches the latest revision. If the schema
     * doesn't exist, this API throws a 404 exception.
     *
     * @param schemaId
     *         schema ID to fetch
     * @return Play result with the fetched schema in JSON format
     */
    @GetMapping(path="/v3/uploadschemas/{schemaId}/recent", produces={APPLICATION_JSON_UTF8_VALUE})
    public String getUploadSchema(@PathVariable String schemaId) throws JsonProcessingException, IOException {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        String studyId = session.getStudyIdentifier();
        
        UploadSchema uploadSchema = uploadSchemaService.getUploadSchema(studyId, schemaId);
        return PUBLIC_SCHEMA_WRITER.writeValueAsString(uploadSchema);
    }
    
    /**
     * Play controller for GET /v3/uploadschemas/:schemaId. Returns all revisions of this upload schema. If the 
     * schema doesn't exist, this API throws a 404 exception.
     * @param schemaId
     *         schema ID to fetch
     * @param includeDeleted
     *         "true" if logically deleted items should be included in results, they are excluded otherwise
     * @return Play result with an array of all revisions of the fetched schema in JSON format
     */
    @GetMapping(path="/v3/uploadschemas/{schemaId}", produces={APPLICATION_JSON_UTF8_VALUE})
    public String getUploadSchemaAllRevisions(@PathVariable String schemaId,
            @RequestParam(defaultValue = "false") boolean includeDeleted) throws JsonProcessingException, IOException {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        String studyId = session.getStudyIdentifier();
        
        List<UploadSchema> uploadSchemas = uploadSchemaService.getUploadSchemaAllRevisions(studyId, schemaId,
                Boolean.valueOf(includeDeleted));
        ResourceList<UploadSchema> uploadSchemaResourceList = new ResourceList<>(uploadSchemas);
        return PUBLIC_SCHEMA_WRITER.writeValueAsString(uploadSchemaResourceList);
    }

    /**
     * Fetches the upload schema for the specified study, schema ID, and revision. If no schema is found, this API
     * throws a 404 exception.
     *
     * @param schemaId
     *         schema ID to fetch
     * @param rev
     *         revision number of the schema to fetch, must be positive
     * @return Play result with the fetched schema in JSON format
     */
    @GetMapping(path="/v3/uploadschemas/{schemaId}/revisions/{revision}", produces={APPLICATION_JSON_UTF8_VALUE})
    public String getUploadSchemaByIdAndRev(@PathVariable String schemaId, @PathVariable int revision)
            throws JsonProcessingException, IOException {
        UserSession session = getAuthenticatedSession(DEVELOPER, WORKER);
        String studyId = session.getStudyIdentifier();

        UploadSchema uploadSchema = uploadSchemaService.getUploadSchemaByIdAndRev(studyId, schemaId, revision);
        return PUBLIC_SCHEMA_WRITER.writeValueAsString(uploadSchema);
    }

    /**
     * Cross-study worker API to get the upload schema for the specified study, schema ID, and revision.
     *
     * @param studyId
     *         study the schema lives in
     * @param schemaId
     *         schema to fetch
     * @param revision
     *         schema revision to fetch
     * @return the requested schema revision
     */
    @GetMapping("/v3/studies/{studyId}/uploadschemas/{schemaId}/revisions/{revision}")
    public UploadSchema getUploadSchemaByStudyAndSchemaAndRev(@PathVariable String studyId,
            @PathVariable String schemaId, @PathVariable int revision) {
        getAuthenticatedSession(WORKER);
        return uploadSchemaService.getUploadSchemaByIdAndRev(studyId, schemaId, revision);
    }

    /**
     * Play controller for GET /v3/uploadschemas. This API fetches the most recent revision of all upload 
     * schemas for the current study. 
     * 
     * @return Play result with list of schemas for this study
     */
    @GetMapping(path="/v3/uploadschemas", produces={APPLICATION_JSON_UTF8_VALUE})
    public String getUploadSchemasForStudy(@RequestParam(defaultValue = "false") boolean includeDeleted)
            throws Exception {
        UserSession session = getAuthenticatedSession(DEVELOPER, RESEARCHER);
        String studyId = session.getStudyIdentifier();

        List<UploadSchema> schemaList = uploadSchemaService.getUploadSchemasForStudy(studyId, Boolean.valueOf(includeDeleted));
        ResourceList<UploadSchema> schemaResourceList = new ResourceList<>(schemaList);
        return PUBLIC_SCHEMA_WRITER.writeValueAsString(schemaResourceList);
    }

    /**
     * Service handler for updating a new schema revision, using V4 API semantics. See
     * {@link org.sagebionetworks.bridge.dao.UploadSchemaDao#updateSchemaRevisionV4}
     *
     * @param schemaId
     *         schema ID to update
     * @param revision
     *         schema revision to update
     * @return Play result, with the updated schema
     */
    @PostMapping(path="/v4/uploadschemas/{schemaId}/revisions/{revision}", produces={APPLICATION_JSON_UTF8_VALUE})
    public String updateSchemaRevisionV4(@PathVariable String schemaId, @PathVariable int revision)
            throws JsonProcessingException, IOException {
        UserSession session = getAuthenticatedSession(DEVELOPER);
        String studyId = session.getStudyIdentifier();

        UploadSchema uploadSchema = parseJson(UploadSchema.class);
        UploadSchema updatedSchema = uploadSchemaService.updateSchemaRevisionV4(studyId, schemaId, revision,
                uploadSchema);
        return PUBLIC_SCHEMA_WRITER.writeValueAsString(updatedSchema);
    }
}
