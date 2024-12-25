package org.sagebionetworks.bridge.services;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import jakarta.annotation.Resource;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.sns.AmazonSNS;
import com.amazonaws.services.sns.model.CreateTopicResult;
import com.amazonaws.services.sns.model.PublishResult;
import com.amazonaws.services.sns.model.SubscribeRequest;
import com.amazonaws.services.sns.model.SubscribeResult;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.model.SendMessageResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharSink;
import com.google.common.io.FileWriteMode;
import com.google.common.io.Files;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.shiro.codec.Hex;
import org.sagebionetworks.client.SynapseClient;
import org.sagebionetworks.client.exceptions.SynapseException;
import org.sagebionetworks.repo.model.Folder;
import org.sagebionetworks.repo.model.ObjectType;
import org.sagebionetworks.repo.model.Project;
import org.sagebionetworks.repo.model.Team;
import org.sagebionetworks.repo.model.dao.WikiPageKey;
import org.sagebionetworks.repo.model.dao.WikiPageKeyHelper;
import org.sagebionetworks.repo.model.annotation.v2.AnnotationsValue;
import org.sagebionetworks.repo.model.annotation.v2.AnnotationsValueType;
import org.sagebionetworks.repo.model.file.S3FileHandle;
import org.sagebionetworks.repo.model.project.ExternalS3StorageLocationSetting;
import org.sagebionetworks.repo.model.table.ColumnModel;
import org.sagebionetworks.repo.model.table.ColumnType;
import org.sagebionetworks.repo.model.table.EntityView;
import org.sagebionetworks.repo.model.v2.wiki.V2WikiPage;
import org.sagebionetworks.repo.model.table.MaterializedView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.BridgeUtils;
import org.sagebionetworks.bridge.RequestContext;
import org.sagebionetworks.bridge.SecureTokenGenerator;
import org.sagebionetworks.bridge.config.BridgeConfig;
import org.sagebionetworks.bridge.exceptions.BadRequestException;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.exceptions.BridgeSynapseException;
import org.sagebionetworks.bridge.exceptions.EntityNotFoundException;
import org.sagebionetworks.bridge.json.BridgeObjectMapper;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.RequestInfo;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.AccountId;
import org.sagebionetworks.bridge.models.accounts.ParticipantVersion;
import org.sagebionetworks.bridge.models.accounts.SharingScope;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.apps.App;
import org.sagebionetworks.bridge.models.apps.Exporter3Configuration;
import org.sagebionetworks.bridge.models.exporter.ExportToAppNotification;
import org.sagebionetworks.bridge.models.exporter.ExportToStudyNotification;
import org.sagebionetworks.bridge.models.exporter.ExportedRecordInfo;
import org.sagebionetworks.bridge.models.exporter.ExporterCreateStudyNotification;
import org.sagebionetworks.bridge.models.exporter.ExporterSubscriptionRequest;
import org.sagebionetworks.bridge.models.exporter.ExporterSubscriptionResult;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordEx3;
import org.sagebionetworks.bridge.models.schedules2.Schedule2;
import org.sagebionetworks.bridge.models.schedules2.timelines.Timeline;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.upload.Upload;
import org.sagebionetworks.bridge.models.worker.Exporter3Request;
import org.sagebionetworks.bridge.models.worker.WorkerRequest;
import org.sagebionetworks.bridge.s3.S3Helper;
import org.sagebionetworks.bridge.synapse.SynapseHelper;
import org.sagebionetworks.bridge.validators.ExportToAppNotificationValidator;
import org.sagebionetworks.bridge.validators.ExporterSubscriptionRequestValidator;
import org.sagebionetworks.bridge.validators.Validate;

/**
 * <p>
 * Service that supports Exporter 3.0. Includes methods to initialize Exporter resources and to complete and export
 * uploads for 3.0.
 * </p>
 * <p>
 * We specifically want to have code separation between Exporter 2.0 and Exporter 3.0, to allow us to make updates to
 * Exporter 3.0 without getting bogged down by legacy code.
 * </p>
 */
@Component
public class Exporter3Service {
    private static final Logger LOG = LoggerFactory.getLogger(Exporter3Service.class);

    // Package-scoped to be available in unit tests.
    static final String CONFIG_KEY_ADMIN_SYNAPSE_ID = "admin.synapse.id";
    static final String CONFIG_KEY_BUCKET_SUFFIX = "bucket.suffix";
    static final String CONFIG_KEY_DOWNSTREAM_ETL_SYNAPSE_ID = "downstream.etl.synapse.id";
    static final String CONFIG_KEY_EXPORTER_SYNAPSE_ID = "exporter.synapse.id";
    static final String CONFIG_KEY_EXPORTER_SYNAPSE_USER = "exporter.synapse.user";
    static final String CONFIG_KEY_RAW_HEALTH_DATA_BUCKET = "health.data.bucket.raw";
    static final String CONFIG_KEY_SYNAPSE_TRACKING_VIEW = "synapse.tracking.view";
    static final String CONFIG_KEY_TEAM_BRIDGE_ADMIN = "team.bridge.admin";
    static final String CONFIG_KEY_TEAM_BRIDGE_STAFF = "team.bridge.staff";
    static final String FOLDER_NAME_BRIDGE_RAW_DATA = "Bridge Raw Data";
    static final String TABLE_NAME_PARTICIPANT_VERSIONS = "Participant Versions";
    static final String TABLE_NAME_PARTICIPANT_VERSIONS_DEMOGRAPHICS = "Participant Versions Demographics";
    static final String TOPIC_PREFIX_CREATE_STUDY = "Bridge-Create-Study-Notification";
    static final String TOPIC_PREFIX_EXPORT = "Bridge-Export-Notification";
    static final String VIEW_NAME_PARTICIPANT_VERSIONS_DEMOGRAPHICS = "Participant Versions Demographics Joined View";
    static final String VIEW_DEFINING_SQL = "SELECT%n" +
            "    participants.healthCode AS healthCode,%n" +
            "    participants.participantVersion AS participantVersion,%n" +
            "    participants.createdOn AS createdOn,%n" +
            "    participants.modifiedOn AS modifiedOn,%n" +
            "    participants.dataGroups AS dataGroups,%n" +
            "    participants.languages AS languages,%n" +
            "    participants.sharingScope AS sharingScope,%n" +
            "    participants.studyMemberships AS studyMemberships,%n" +
            "    participants.clientTimeZone AS clientTimeZone,%n" +
            "    demographics.studyId AS studyId,%n" +
            "    demographics.demographicCategoryName AS demographicCategoryName,%n" +
            "    demographics.demographicValue AS demographicValue,%n" +
            "    demographics.demographicUnits AS demographicUnits,%n" +
            "    demographics.demographicInvalidity AS demographicInvalidity%n" +
            "FROM %s as participants JOIN %s as demographics%n" +
            "    ON participants.healthCode = demographics.healthCode AND participants.participantVersion = demographics.participantVersion";
    static final String WORKER_NAME_EXPORTER_3 = "Exporter3Worker";

    static final List<ColumnModel> PARTICIPANT_VERSION_COLUMN_MODELS;
    static {
        ImmutableList.Builder<ColumnModel> listBuilder = ImmutableList.builder();

        // Original healthcodes are GUIDs. These are 36 characters long.
        ColumnModel healthCodeColumn = new ColumnModel();
        healthCodeColumn.setName("healthCode");
        healthCodeColumn.setColumnType(ColumnType.STRING);
        healthCodeColumn.setMaximumSize(36L);
        listBuilder.add(healthCodeColumn);

        ColumnModel participantVersionColumn = new ColumnModel();
        participantVersionColumn.setName("participantVersion");
        participantVersionColumn.setColumnType(ColumnType.INTEGER);
        listBuilder.add(participantVersionColumn);

        ColumnModel createdOnColumn = new ColumnModel();
        createdOnColumn.setName("createdOn");
        createdOnColumn.setColumnType(ColumnType.DATE);
        listBuilder.add(createdOnColumn);

        ColumnModel modifiedOnColumn = new ColumnModel();
        modifiedOnColumn.setName("modifiedOn");
        modifiedOnColumn.setColumnType(ColumnType.DATE);
        listBuilder.add(modifiedOnColumn);

        // We have pre-existing validation that checks that the total length of all data groups is less than
        // 250 characters. This validation predates the use of string lists in Synapse. Additionally, some apps have
        // 30+ data groups, and some apps have data groups with 20+ characters. Synapse table row width is valuable, so
        // instead of reserving potentially 600+ characters that will be sparsely used, we will keep to the
        // 250 character limit.
        ColumnModel dataGroupsColumn = new ColumnModel();
        dataGroupsColumn.setName("dataGroups");
        dataGroupsColumn.setColumnType(ColumnType.STRING);
        dataGroupsColumn.setMaximumSize(250L);
        listBuilder.add(dataGroupsColumn);

        // These are 5-character language codes (eg "en-US", "es-ES"). Let's generously allow participants to export up to
        // 10 languages, which is 50 characters at most, and should be more than enough for nearly all participants.
        ColumnModel languagesColumn = new ColumnModel();
        languagesColumn.setName("languages");
        languagesColumn.setColumnType(ColumnType.STRING_LIST);
        languagesColumn.setMaximumSize(5L);
        languagesColumn.setMaximumListLength(10L);
        listBuilder.add(languagesColumn);

        // The longest sharing scope is ALL_QUALIFIED_RESEARCHERS, which is 25 characters long.
        ColumnModel sharingScopeColumn = new ColumnModel();
        sharingScopeColumn.setName("sharingScope");
        sharingScopeColumn.setColumnType(ColumnType.STRING);
        sharingScopeColumn.setMaximumSize(25L);
        listBuilder.add(sharingScopeColumn);

        // Synapse doesn't have maps as a type in their Table model. For now, we serialize it as a string. In 2.0, we
        // set a max string length of 250 characters, and that seems to be fine.
        ColumnModel studyMembershipsColumn = new ColumnModel();
        studyMembershipsColumn.setName("studyMemberships");
        studyMembershipsColumn.setColumnType(ColumnType.STRING);
        studyMembershipsColumn.setMaximumSize(250L);
        listBuilder.add(studyMembershipsColumn);

        // The longest time zone I could find was 28 characters long. Let's add a bit of a buffer and say 40
        // characters. As long as Llanfair PG, Wales doesn't have its own time zone, this should be fine.
        ColumnModel timeZoneColumn = new ColumnModel();
        timeZoneColumn.setName("clientTimeZone");
        timeZoneColumn.setColumnType(ColumnType.STRING);
        timeZoneColumn.setMaximumSize(40L);
        listBuilder.add(timeZoneColumn);

        PARTICIPANT_VERSION_COLUMN_MODELS = listBuilder.build();
    }
    static final List<ColumnModel> PARTICIPANT_VERSION_DEMOGRAPHICS_COLUMN_MODELS;
    static {
        ImmutableList.Builder<ColumnModel> listBuilder = ImmutableList.builder();

        ColumnModel healthCodeColumn = new ColumnModel();
        healthCodeColumn.setName("healthCode");
        healthCodeColumn.setColumnType(ColumnType.STRING);
        healthCodeColumn.setMaximumSize(36L);
        listBuilder.add(healthCodeColumn);

        ColumnModel participantVersionColumn = new ColumnModel();
        participantVersionColumn.setName("participantVersion");
        participantVersionColumn.setColumnType(ColumnType.INTEGER);
        listBuilder.add(participantVersionColumn);

        ColumnModel studyIdColumn = new ColumnModel();
        studyIdColumn.setName("studyId");
        studyIdColumn.setColumnType(ColumnType.STRING);
        healthCodeColumn.setMaximumSize(255L);
        listBuilder.add(studyIdColumn);

        ColumnModel categoryNameColumn = new ColumnModel();
        categoryNameColumn.setName("demographicCategoryName");
        categoryNameColumn.setColumnType(ColumnType.STRING);
        healthCodeColumn.setMaximumSize(255L);
        listBuilder.add(categoryNameColumn);

        ColumnModel demographicValueColumn = new ColumnModel();
        demographicValueColumn.setName("demographicValue");
        demographicValueColumn.setColumnType(ColumnType.LARGETEXT);
        listBuilder.add(demographicValueColumn);

        ColumnModel demographicUnitsColumn = new ColumnModel();
        demographicUnitsColumn.setName("demographicUnits");
        demographicUnitsColumn.setColumnType(ColumnType.STRING);
        healthCodeColumn.setMaximumSize(512L);
        listBuilder.add(demographicUnitsColumn);

        ColumnModel demographicInvalidityColumn = new ColumnModel();
        demographicInvalidityColumn.setName("demographicInvalidity");
        demographicInvalidityColumn.setColumnType(ColumnType.STRING);
        healthCodeColumn.setMaximumSize(512L);
        listBuilder.add(demographicInvalidityColumn);

        PARTICIPANT_VERSION_DEMOGRAPHICS_COLUMN_MODELS = listBuilder.build();
    }

    // Config attributes.
    private Long bridgeAdminTeamId;
    private Long bridgeStaffTeamId;
    private Long adminSynapseId;
    private Long downstreamEtlSynapseId;
    private Long exporterSynapseId;
    private String exporterSynapseUser;
    private String rawHealthDataBucket;
    private String snsTopicSuffix;
    private String synapseTrackingViewId;

    private AccountService accountService;
    private AppService appService;
    private BridgeConfig config;
    private HealthDataEx3Service healthDataEx3Service;
    private ParticipantVersionService participantVersionService;
    private RequestInfoService requestInfoService;
    private S3Helper s3Helper;
    private AmazonSNS snsClient;
    private AmazonSQS sqsClient;
    private StudyService studyService;
    private SynapseHelper synapseHelper;
    private Schedule2Service schedule2Service;
    private SynapseClient synapseClient;
    private FileService fileService;
    private DigestUtils md5DigestUtils;
    private AmazonS3 s3Client;

    @Autowired
    public final void setConfig(BridgeConfig config) {
        this.config = config;

        bridgeAdminTeamId = (long) config.getInt(CONFIG_KEY_TEAM_BRIDGE_ADMIN);
        bridgeStaffTeamId = (long) config.getInt(CONFIG_KEY_TEAM_BRIDGE_STAFF);
        exporterSynapseId = (long) config.getInt(CONFIG_KEY_EXPORTER_SYNAPSE_ID);
        exporterSynapseUser = config.getProperty(CONFIG_KEY_EXPORTER_SYNAPSE_USER);
        rawHealthDataBucket = config.getProperty(CONFIG_KEY_RAW_HEALTH_DATA_BUCKET);
        synapseTrackingViewId = config.getProperty(CONFIG_KEY_SYNAPSE_TRACKING_VIEW);

        // It's called the bucket suffix in config, but it can be used as a general suffix for any resource.
        snsTopicSuffix = config.getProperty(CONFIG_KEY_BUCKET_SUFFIX);

        String adminSynapseIdStr = config.get(CONFIG_KEY_ADMIN_SYNAPSE_ID);
        if (StringUtils.isNotBlank(adminSynapseIdStr)) {
            adminSynapseId = Long.valueOf(adminSynapseIdStr);
        } else {
            adminSynapseId = null;
        }

        String downstreamEtlSynapseIdStr = config.get(CONFIG_KEY_DOWNSTREAM_ETL_SYNAPSE_ID);
        if (StringUtils.isNotBlank(downstreamEtlSynapseIdStr)) {
            downstreamEtlSynapseId = Long.valueOf(downstreamEtlSynapseIdStr);
        } else {
            downstreamEtlSynapseId = null;
        }
    }

    @Autowired
    public final void setAccountService(AccountService accountService) {
        this.accountService = accountService;
    }

    @Autowired
    public final void setAppService(AppService appService) {
        this.appService = appService;
    }

    @Autowired
    public final void setHealthDataEx3Service(HealthDataEx3Service healthDataEx3Service) {
        this.healthDataEx3Service = healthDataEx3Service;
    }

    @Autowired
    public final void setParticipantVersionService(ParticipantVersionService participantVersionService) {
        this.participantVersionService = participantVersionService;
    }

    @Autowired
    public final void setRequestInfoService(RequestInfoService requestInfoService) {
        this.requestInfoService = requestInfoService;
    }

    @Resource(name = "s3Helper")
    public final void setS3Helper(S3Helper s3Helper) {
        this.s3Helper = s3Helper;
    }

    @Autowired
    public final void setSnsClient(AmazonSNS snsClient) {
        this.snsClient = snsClient;
    }

    @Autowired
    final void setSqsClient(AmazonSQS sqsClient) {
        this.sqsClient = sqsClient;
    }

    @Autowired
    final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }

    @Resource(name="exporterSynapseClient")
    public final void setSynapseClient(SynapseClient synapseClient) {
        this.synapseClient = synapseClient;
    }

    @Resource(name="exporterSynapseHelper")
    public final void setSynapseHelper(SynapseHelper synapseHelper) {
        this.synapseHelper = synapseHelper;
    }

    @Autowired
    final void setSchedule2Service(Schedule2Service schedule2Service) {
        this.schedule2Service = schedule2Service;
    }

    @Autowired
    public final void setFileService(FileService fileService) {
        this.fileService = fileService;
    }

    @Resource(name = "s3Client")
    public final void setS3Client(AmazonS3 s3Client) {
        this.s3Client = s3Client;
    }

    @Resource(name = "md5DigestUtils")
    public final void setMd5DigestUtils(DigestUtils md5DigestUtils) {
        this.md5DigestUtils = md5DigestUtils;
    }

    /**
     * Initializes configs and Synapse resources for Exporter 3.0. Note that if any config already exists, this API
     * will simply ignore them. This allows for two notable scenarios
     * (a) Advanced users can use existing projects or data access teams for Exporter 3.0.
     * (b) If in the future, we add something new (like a notification queue, or a default view), we can re-run this
     * API to create the new stuff without affecting the old stuff.
     */
    public Exporter3Configuration initExporter3(String appId) throws BridgeSynapseException, IOException,
            SynapseException {
        boolean isAppModified = false;
        App app = appService.getApp(appId);

        // Init the Exporter3Config object.
        Exporter3Configuration ex3Config = app.getExporter3Configuration();
        if (ex3Config == null) {
            ex3Config = new Exporter3Configuration();
            app.setExporter3Configuration(ex3Config);
            isAppModified = true;
        }

        boolean isConfigModified = initExporter3Internal(app.getName(), appId, Optional.empty(), ex3Config);
        if (isConfigModified) {
            isAppModified = true;
        }

        // Finally, enable Exporter 3.0 if it's not already enabled.
        if (!app.isExporter3Enabled()) {
            app.setExporter3Enabled(true);
            isAppModified = true;
        }

        // Update app if necessary. We mark this as an admin update, because the only field that changed is
        // exporter3config and exporter3enabled.
        if (isAppModified) {
            appService.updateApp(app, true);
        }

        return ex3Config;
    }

    /**
     * Initializes configs and Synapse resources for Exporter 3.0 for a study. This follows the same rules as
     * initializing for apps.
     */
    public Exporter3Configuration initExporter3ForStudy(String appId, String studyId) throws BridgeSynapseException,
            IOException, SynapseException {
        boolean isFirstInitialization = false;
        boolean isStudyModified = false;
        Study study = studyService.getStudy(appId, studyId, true);

        // Init the Exporter3Config object.
        Exporter3Configuration ex3Config = study.getExporter3Configuration();
        if (ex3Config == null) {
            ex3Config = new Exporter3Configuration();
            study.setExporter3Configuration(ex3Config);
            isFirstInitialization = true;
            isStudyModified = true;
        }

        boolean isConfigModified = initExporter3Internal(study.getName(), appId, Optional.of(studyId), ex3Config);
        if (isConfigModified) {
            isStudyModified = true;
        }

        // Finally, enable Exporter 3.0 if it's not already enabled.
        if (!study.isExporter3Enabled()) {
            study.setExporter3Enabled(true);
            isStudyModified = true;
        }

        // Update study if necessary. We mark this as an admin update, because the only field that changed is
        // exporter3config and exporter3enabled.
        if (isStudyModified) {
            studyService.updateStudy(appId, study);
        }

        if (isFirstInitialization) {
            // Send notification if the study was freshly initialized.
            App app = appService.getApp(appId);
            Exporter3Configuration appEx3Config = app.getExporter3Configuration();
            if (appEx3Config != null && appEx3Config.getCreateStudyNotificationTopicArn() != null) {
                String createStudyNotificationTopicArn = appEx3Config.getCreateStudyNotificationTopicArn();

                // Create notification.
                ExporterCreateStudyNotification notification = new ExporterCreateStudyNotification();
                notification.setAppId(appId);
                notification.setParentProjectId(ex3Config.getProjectId());
                notification.setRawFolderId(ex3Config.getRawDataFolderId());
                notification.setStudyId(studyId);

                sendNotification(appId, studyId, "create study", createStudyNotificationTopicArn, notification);
            }
        }
        return ex3Config;
    }

    // Package-scoped for unit tests.
    // Parent name is the name of the app or study this is being initialized for.
    // This modifies the passed in Exporter3Config. Returns true if ex3Config was modified.
    boolean initExporter3Internal(String parentName, String appId, Optional<String> studyId, Exporter3Configuration ex3Config)
            throws BridgeSynapseException, IOException, SynapseException {
        boolean isModified = false;

        // Name in Synapse are globally unique, so we add a random token to the name to ensure it
        // doesn't conflict with an existing name. Also, Synapse names can only contain a certain
        // subset of characters. We've verified this name is acceptable for this transformation.
        String synapseName = BridgeUtils.toSynapseFriendlyName(parentName);
        String nameScopingToken = getNameScopingToken();

        // Create data access team.
        Long dataAccessTeamId = ex3Config.getDataAccessTeamId();
        if (dataAccessTeamId == null) {
            Team team = new Team();
            team.setName(synapseName + " Access Team " + nameScopingToken);
            team = synapseHelper.createTeamWithRetry(team);
            dataAccessTeamId = Long.parseLong(team.getId());
            LOG.info("Created Synapse team " + dataAccessTeamId);

            // As a temporary measure, add the admin ID as a manager of this team. This will allow us to manage
            // permissions manually until we have a solution for https://sagebionetworks.jira.com/browse/BRIDGE-3154
            if (adminSynapseId != null) {
                synapseHelper.inviteToTeam(dataAccessTeamId, adminSynapseId, true);
            }

            ex3Config.setDataAccessTeamId(dataAccessTeamId);
            isModified = true;
        }

        Set<Long> dataAdminIds = ImmutableSet.of(exporterSynapseId, bridgeAdminTeamId);

        Set<Long> dataReadOnlyIds = new HashSet<>();
        dataReadOnlyIds.add(bridgeStaffTeamId);
        dataReadOnlyIds.add(dataAccessTeamId);

        Set<Long> projectAdminIds = new HashSet<>();
        projectAdminIds.add(exporterSynapseId);
        projectAdminIds.add(bridgeAdminTeamId);

        Set<Long> projectReadOnlyIds = ImmutableSet.of(bridgeStaffTeamId, dataAccessTeamId);

        // Note: At the moment, we are not able to create additional accounts in Synapse Dev. Some environments might
        // not have these accounts set up yet, so we need to be able to handle these configs not present.
        if (adminSynapseId != null) {
            projectAdminIds.add(adminSynapseId);
        }
        if (downstreamEtlSynapseId != null) {
            dataReadOnlyIds.add(downstreamEtlSynapseId);
            projectAdminIds.add(downstreamEtlSynapseId);
        }

        // Create project.
        String projectId = ex3Config.getProjectId();
        if (projectId == null) {
            Project project = new Project();
            project.setName(synapseName + " Project " + nameScopingToken);
            project = synapseHelper.createEntityWithRetry(project);
            projectId = project.getId();
            LOG.info("Created Synapse project " + projectId);

            // Create ACLs for project.
            synapseHelper.createAclWithRetry(projectId, projectAdminIds, projectReadOnlyIds);

            ex3Config.setProjectId(projectId);
            isModified = true;

            // We also need to add this project to the tracking view.
            if (StringUtils.isNotBlank(synapseTrackingViewId)) {
                try {
                    EntityView view = synapseHelper.getEntityWithRetry(synapseTrackingViewId, EntityView.class);
                    if (view != null) {
                        // For whatever reason, view.getScopes() doesn't include the "syn" prefix.
                        view.getScopeIds().add(projectId.substring(3));
                        synapseHelper.updateEntityWithRetry(view);
                    }
                } catch (SynapseException ex) {
                    LOG.error("Error adding new project " + projectId + " to tracking view " + synapseTrackingViewId +
                            ": " + ex.getMessage(), ex);
                }
            }

            // Add study and app id annotations.
            Map<String, AnnotationsValue> annotations = new HashMap<>();
            annotations.put("appId",
                    new AnnotationsValue().setType(AnnotationsValueType.STRING).setValue(ImmutableList.of(appId)));
            if (studyId.isPresent()) {
                annotations.put("studyId", new AnnotationsValue().setType(AnnotationsValueType.STRING)
                        .setValue(ImmutableList.of(studyId.get())));
            }
            synapseHelper.addAnnotationsToEntity(projectId, annotations);
        }

        // Create Participant Version Table.
        String participantVersionTableId = ex3Config.getParticipantVersionTableId();
        if (participantVersionTableId == null) {
            participantVersionTableId = synapseHelper.createTableWithColumnsAndAcls(PARTICIPANT_VERSION_COLUMN_MODELS,
                    dataReadOnlyIds, dataAdminIds, projectId, TABLE_NAME_PARTICIPANT_VERSIONS);
            LOG.info("Created Synapse participant versions table " + participantVersionTableId);

            ex3Config.setParticipantVersionTableId(participantVersionTableId);
            isModified = true;
        }
        // Create Participant Version Demographics Table.
        String participantVersionDemographicsTableId = ex3Config.getParticipantVersionDemographicsTableId();
        if (participantVersionDemographicsTableId == null) {
            participantVersionDemographicsTableId = synapseHelper.createTableWithColumnsAndAcls(
                    PARTICIPANT_VERSION_DEMOGRAPHICS_COLUMN_MODELS, dataReadOnlyIds, dataAdminIds, projectId,
                    TABLE_NAME_PARTICIPANT_VERSIONS_DEMOGRAPHICS);
            LOG.info(
                    "Created Synapse participant versions demographics table " + participantVersionDemographicsTableId);

            ex3Config.setParticipantVersionDemographicsTableId(participantVersionDemographicsTableId);
            isModified = true;
        }

        // Create joined materialized view with participant versions and demographics
        String participantVersionDemographicsViewId = ex3Config.getParticipantVersionDemographicsViewId();
        if (participantVersionDemographicsViewId == null) {
            String sql = VIEW_DEFINING_SQL.formatted(participantVersionTableId, participantVersionDemographicsTableId);
            MaterializedView view = new MaterializedView().setName(VIEW_NAME_PARTICIPANT_VERSIONS_DEMOGRAPHICS)
                    .setParentId(projectId).setDefiningSQL(sql);
            MaterializedView createdView = synapseHelper.createEntityWithRetry(view);
            synapseHelper.createAclWithRetry(createdView.getId(), dataAdminIds, dataReadOnlyIds);
            LOG.info("Created Synapse participant versions demographics materialized view " + createdView.getId());

            ex3Config.setParticipantVersionDemographicsViewId(createdView.getId());
            isModified = true;
        }

        // Create Folder for raw data.
        String rawDataFolderId = ex3Config.getRawDataFolderId();
        if (rawDataFolderId == null) {
            Folder folder = new Folder();
            folder.setName(FOLDER_NAME_BRIDGE_RAW_DATA);
            folder.setParentId(projectId);
            folder = synapseHelper.createEntityWithRetry(folder);
            rawDataFolderId = folder.getId();
            LOG.info("Created Synapse folder " + rawDataFolderId);

            // Create ACLs for folder. This is a separate ACL because we don't want to allow people to modify the
            // raw data.
            synapseHelper.createAclWithRetry(rawDataFolderId, dataAdminIds, dataReadOnlyIds);

            ex3Config.setRawDataFolderId(rawDataFolderId);
            isModified = true;
        }

        // Create storage location.
        Long storageLocationId = ex3Config.getStorageLocationId();
        if (storageLocationId == null) {
            String baseKey = appId;
            if (studyId.isPresent()) {
                baseKey += "/" + studyId.get();
            }
            // Create owner.txt so that we can create the storage location.
            s3Helper.writeLinesToS3(rawHealthDataBucket, baseKey + "/owner.txt",
                    ImmutableList.of(exporterSynapseUser));

            // Create storage location.
            ExternalS3StorageLocationSetting storageLocation = new ExternalS3StorageLocationSetting();
            storageLocation.setBaseKey(baseKey);
            storageLocation.setBucket(rawHealthDataBucket);
            storageLocation.setStsEnabled(true);
            storageLocation = synapseHelper.createStorageLocationForEntity(rawDataFolderId, storageLocation);
            storageLocationId = storageLocation.getStorageLocationId();
            LOG.info("Created Synapse storage location " + storageLocationId);

            ex3Config.setStorageLocationId(storageLocationId);
            isModified = true;
        }

        // Enable upload tables (CSV generation) if it's disabled.
        if (!ex3Config.isUploadTableEnabled()) {
            ex3Config.setUploadTableEnabled(true);
            isModified = true;
        }

        return isModified;
    }

    // Package-scoped for unit tests.
    String getNameScopingToken() {
        return SecureTokenGenerator.NAME_SCOPE_INSTANCE.nextToken();
    }

    /**
     * This is called by the Worker when a health data record is exported to Synapse. This sends notifications to all
     * subscribers for both the app-wide Synapse project and all study-specific Synapse projects that the record was
     * exported to.
     */
    public void sendExportNotifications(ExportToAppNotification exportToAppNotification) {
        Validate.entityThrowingException(ExportToAppNotificationValidator.INSTANCE, exportToAppNotification);
        String appId = exportToAppNotification.getAppId();
        String recordId = exportToAppNotification.getRecordId();
        final String type = "export record " + recordId;

        App app = appService.getApp(appId);
        if (!app.isExporter3Enabled()) {
            throw new BadRequestException("App does not have Exporter 3.0 enabled");
        }

        // Send app-wide notification.
        Exporter3Configuration ex3Config = app.getExporter3Configuration();
        if (ex3Config != null && ex3Config.getExportNotificationTopicArn() != null) {
            String appNotificationTopicArn = ex3Config.getExportNotificationTopicArn();
            try {
                sendNotification(appId, null, type, appNotificationTopicArn, exportToAppNotification);
            } catch (RuntimeException ex) {
                LOG.error("Error notifying export for app-wide project, app=" + appId + ", record=" + recordId, ex);
            }
        } else {
            // This could happen if the study is configured for export, but not the app. Log at info level, so we can
            // trace with our logs.
            LOG.info("Export not enabled for app-wide project, app=" + appId + ", record=" + recordId);
        }

        // Send study-specific notifications. Note that getStudyRecords() is never null.
        Map<String, ExportedRecordInfo> studyRecordMap = exportToAppNotification.getStudyRecords();
        for (Map.Entry<String, ExportedRecordInfo> entry : studyRecordMap.entrySet()) {
            String studyId = entry.getKey();

            try {
                Study study = studyService.getStudy(appId, studyId, false);
                if (study == null || !study.isExporter3Enabled() || study.getExporter3Configuration() == null) {
                    // This is very unusual. Log and move on.
                    LOG.error("Export for non-existent or non-configured study, app=" + appId + ", study=" +
                            studyId + ", record=" + recordId);
                    continue;
                }
                String studyNotificationTopicArn = study.getExporter3Configuration()
                        .getExportNotificationTopicArn();
                if (studyNotificationTopicArn == null) {
                    // This is normal. Skip.
                    continue;
                }

                ExportedRecordInfo recordInfo = entry.getValue();
                ExportToStudyNotification exportToStudyNotification = new ExportToStudyNotification();
                exportToStudyNotification.setAppId(appId);
                exportToStudyNotification.setStudyId(studyId);
                exportToStudyNotification.setRecordId(recordId);
                exportToStudyNotification.setParentProjectId(recordInfo.getParentProjectId());
                exportToStudyNotification.setRawFolderId(recordInfo.getRawFolderId());
                exportToStudyNotification.setFileEntityId(recordInfo.getFileEntityId());
                exportToStudyNotification.setS3Bucket(recordInfo.getS3Bucket());
                exportToStudyNotification.setS3Key(recordInfo.getS3Key());

                sendNotification(appId, studyId, type, studyNotificationTopicArn, exportToStudyNotification);
            } catch (RuntimeException ex) {
                LOG.error("Error notifying export for study-specific project, app=" + appId + ", study=" +
                        studyId + ", record=" + recordId, ex);
            }
        }
    }

    // Helper method for sending SNS notifications. appId, studyId, and type are used for logging. topicArn is the SNS
    // topic to send the notification to. Object is the notification to send, which will be converted to JSON and sent
    // in JSON format.
    private void sendNotification(String appId, String studyId, String type, String topicArn, Object notification) {
        // Convert notification to JSON and publish.
        String notificationJson;
        try {
            notificationJson = BridgeObjectMapper.get().writeValueAsString(notification);
            PublishResult publishResult = snsClient.publish(topicArn, notificationJson);
            LOG.info("Sent notification for app=" + appId + ", study=" + studyId + ", type=" + type + ", message ID " +
                    publishResult.getMessageId());
        } catch (JsonProcessingException ex) {
            // This should never happen, but catch it and log.
            LOG.error("Error creating notification for app=" + appId + ", study=" + studyId + ", type=" + type, ex);
        }
    }

    /** Subscribe to be notified when a study is initialized for Exporter 3.0 in the given app. */
    public ExporterSubscriptionResult subscribeToCreateStudyNotifications(String appId,
            ExporterSubscriptionRequest subscriptionRequest) {
        return subscribeToNotificationsForApp(appId, TOPIC_PREFIX_CREATE_STUDY, subscriptionRequest,
                Exporter3Configuration::getCreateStudyNotificationTopicArn,
                Exporter3Configuration::setCreateStudyNotificationTopicArn);
    }

    /** Subscribe to be notified when health data is exported to any Synapse projects in the app. */
    public ExporterSubscriptionResult subscribeToExportNotificationsForApp(String appId,
            ExporterSubscriptionRequest subscriptionRequest) {
        return subscribeToNotificationsForApp(appId, TOPIC_PREFIX_EXPORT, subscriptionRequest,
                Exporter3Configuration::getExportNotificationTopicArn,
                Exporter3Configuration::setExportNotificationTopicArn);
    }

    private ExporterSubscriptionResult subscribeToNotificationsForApp(String appId, String topicPrefix,
            ExporterSubscriptionRequest subscriptionRequest, Function<Exporter3Configuration, String> getter,
            BiConsumer<Exporter3Configuration, String> setter) {
        Validate.entityThrowingException(ExporterSubscriptionRequestValidator.INSTANCE, subscriptionRequest);

        boolean isAppModified = false;
        App app = appService.getApp(appId);

        // Init the Exporter3Config object.
        Exporter3Configuration ex3Config = app.getExporter3Configuration();
        if (ex3Config == null) {
            ex3Config = new Exporter3Configuration();
            app.setExporter3Configuration(ex3Config);
            isAppModified = true;
        }

        // Has the SNS topic been created for this app?
        String topicArn = getter.apply(ex3Config);
        if (topicArn == null) {
            String topicName = topicPrefix + '-' + appId + '-' + snsTopicSuffix;
            CreateTopicResult createTopicResult = snsClient.createTopic(topicName);
            topicArn = createTopicResult.getTopicArn();
            LOG.info("Created SNS topic name=" + topicName + ", arn=" + topicArn);

            setter.accept(ex3Config, topicArn);
            isAppModified = true;
        }

        // Update app if necessary. We mark this as an admin update, because the only field that changed is
        // exporter3config.
        if (isAppModified) {
            appService.updateApp(app, true);
        }

        // Subscribe to the topic.
        SubscribeRequest snsSubscribeRequest = new SubscribeRequest();
        snsSubscribeRequest.setAttributes(subscriptionRequest.getAttributes());
        snsSubscribeRequest.setEndpoint(subscriptionRequest.getEndpoint());
        snsSubscribeRequest.setProtocol(subscriptionRequest.getProtocol());
        snsSubscribeRequest.setReturnSubscriptionArn(true);
        snsSubscribeRequest.setTopicArn(topicArn);
        SubscribeResult snsSubscribeResult = snsClient.subscribe(snsSubscribeRequest);

        ExporterSubscriptionResult subscriptionResult = new ExporterSubscriptionResult();
        subscriptionResult.setSubscriptionArn(snsSubscribeResult.getSubscriptionArn());
        return subscriptionResult;
    }

    /** Subscribe to be notified when health data is exported to the study-specific Synapse project. */
    public ExporterSubscriptionResult subscribeToExportNotificationsForStudy(String appId, String studyId,
            ExporterSubscriptionRequest subscriptionRequest) {
        return subscribeToNotificationsForStudy(appId, studyId, TOPIC_PREFIX_EXPORT, subscriptionRequest,
                Exporter3Configuration::getExportNotificationTopicArn,
                Exporter3Configuration::setExportNotificationTopicArn);
    }

    @SuppressWarnings("SameParameterValue")
    private ExporterSubscriptionResult subscribeToNotificationsForStudy(String appId, String studyId,
            String topicPrefix, ExporterSubscriptionRequest subscriptionRequest,
            Function<Exporter3Configuration, String> getter, BiConsumer<Exporter3Configuration, String> setter) {
        Validate.entityThrowingException(ExporterSubscriptionRequestValidator.INSTANCE, subscriptionRequest);

        boolean isStudyModified = false;
        Study study = studyService.getStudy(appId, studyId, true);
        Exporter3Configuration ex3Config = study.getExporter3Configuration();
        if (ex3Config == null) {
            ex3Config = new Exporter3Configuration();
            study.setExporter3Configuration(ex3Config);
            isStudyModified = true;
        }

        // Has the SNS topic been created for this app?
        String topicArn = getter.apply(ex3Config);
        if (topicArn == null) {
            String topicName = topicPrefix + '-' + appId + '-' + studyId + '-' + snsTopicSuffix;
            CreateTopicResult createTopicResult = snsClient.createTopic(topicName);
            topicArn = createTopicResult.getTopicArn();
            LOG.info("Created SNS topic name=" + topicName + ", arn=" + topicArn);

            setter.accept(ex3Config, topicArn);
            isStudyModified = true;
        }

        // Update app if necessary. We mark this as an admin update, because the only field that changed is
        // exporter3config.
        if (isStudyModified) {
            studyService.updateStudy(appId, study);
        }

        // Subscribe to the topic.
        SubscribeRequest snsSubscribeRequest = new SubscribeRequest();
        snsSubscribeRequest.setAttributes(subscriptionRequest.getAttributes());
        snsSubscribeRequest.setEndpoint(subscriptionRequest.getEndpoint());
        snsSubscribeRequest.setProtocol(subscriptionRequest.getProtocol());
        snsSubscribeRequest.setReturnSubscriptionArn(true);
        snsSubscribeRequest.setTopicArn(topicArn);
        SubscribeResult snsSubscribeResult = snsClient.subscribe(snsSubscribeRequest);

        ExporterSubscriptionResult subscriptionResult = new ExporterSubscriptionResult();
        subscriptionResult.setSubscriptionArn(snsSubscribeResult.getSubscriptionArn());
        return subscriptionResult;
    }

    /** Complete an upload for Exporter 3.0, and also export that upload. */
    public void completeUpload(App app, Upload upload) throws JsonProcessingException {
        String appId = app.getIdentifier();
        String healthCode = upload.getHealthCode();

        // Create record.
        HealthDataRecordEx3 record = HealthDataRecordEx3.createFromUpload(upload);

        // Mark record with sharing scope.
        Account account = accountService.getAccount(AccountId.forHealthCode(appId,
                healthCode)).orElseThrow(() -> {
            // This should never happen. If it does, log a warning and throw.
            LOG.warn("Account disappeared in the middle of upload, healthCode=" + healthCode + ", appId="
                    + appId + ", uploadId=" + upload.getUploadId());
            return new EntityNotFoundException(StudyParticipant.class);
        });
        SharingScope sharingScope = account.getSharingScope();
        record.setSharingScope(sharingScope);

        // Handle Client Info and User Agent.
        String clientInfoJsonText;
        String userAgent;
        if (upload.getUserAgent() != null) {
            clientInfoJsonText = upload.getClientInfo();
            userAgent = upload.getUserAgent();
        } else {
            ClientInfo clientInfo;
            RequestContext requestContext = RequestContext.get();
            String calledUserId = requestContext.getCallerUserId();
            String uploaderUserId = account.getId();
            if (Objects.equals(calledUserId, uploaderUserId)) {
                // The caller is the uploader. Get the Client Info from the RequestContext.
                clientInfo = requestContext.getCallerClientInfo();
                userAgent = requestContext.getUserAgent();
            } else {
                // The caller is _not_ the uploader. Get the Client Info from the RequestInfoService.
                RequestInfo requestInfo = requestInfoService.getRequestInfo(uploaderUserId);
                if (requestInfo != null && requestInfo.getClientInfo() != null) {
                    clientInfo = requestInfo.getClientInfo();
                    userAgent = requestInfo.getUserAgent();
                } else {
                    clientInfo = ClientInfo.UNKNOWN_CLIENT;
                    userAgent = null;
                }
            }

            clientInfoJsonText = BridgeObjectMapper.get().writerWithDefaultPrettyPrinter()
                    .writeValueAsString(clientInfo);
        }
        record.setClientInfo(clientInfoJsonText);
        record.setUserAgent(userAgent);

        // Also mark with the latest participant version.
        Optional<ParticipantVersion> participantVersion = participantVersionService
                .getLatestParticipantVersionForHealthCode(appId, healthCode);
        if (participantVersion.isPresent()) {
            record.setParticipantVersion(participantVersion.get().getParticipantVersion());
        }

        // If the record already exists (for example, this is a redrive), we need to set the version attribute properly
        // so we overwrite the old record properly.
        Optional<HealthDataRecordEx3> oldRecord = healthDataEx3Service.getRecord(upload.getUploadId(), false);
        if (oldRecord.isPresent()) {
            record.setVersion(oldRecord.get().getVersion());
        }

        // Save record.
        record = healthDataEx3Service.createOrUpdateRecord(record);

        if (sharingScope != SharingScope.NO_SHARING) {
            exportUpload(appId, record.getId());
        }
    }

    // This is separate because we might need a separate redrive process in the future.
    private void exportUpload(String appId, String recordId) {
        // Create request.
        Exporter3Request exporter3Request = new Exporter3Request();
        exporter3Request.setAppId(appId);
        exporter3Request.setRecordId(recordId);

        WorkerRequest workerRequest = new WorkerRequest();
        workerRequest.setService(WORKER_NAME_EXPORTER_3);
        workerRequest.setBody(exporter3Request);

        // Convert request to JSON.
        ObjectMapper objectMapper = BridgeObjectMapper.get();
        String requestJson;
        try {
            requestJson = objectMapper.writeValueAsString(workerRequest);
        } catch (JsonProcessingException ex) {
            // This should never happen, but catch and re-throw for code hygiene.
            throw new BridgeServiceException("Error creating export request for app " + appId + " record " + recordId,
                    ex);
        }

        // Note: SqsInitializer runs after Spring, so we need to grab the queue URL dynamically.
        String workerQueueUrl = config.getProperty(BridgeConstants.CONFIG_KEY_WORKER_SQS_URL);

        // Sent to SQS.
        SendMessageResult sqsResult = sqsClient.sendMessage(workerQueueUrl, requestJson);
        LOG.info("Sent export request for app " + appId + " record " + recordId + "; received message ID=" +
                sqsResult.getMessageId());
    }

    // Export timeline from Bridge to Synapse (Some researchers only have access to Synapse, not Bridge,
    // so they need access to the Timeline information.).
    public Exporter3Configuration exportTimelineForStudy(String appId, String studyId) throws BridgeSynapseException,
            SynapseException, IOException{
        // Get Timeline to export for the study.
        Study study = studyService.getStudy(appId, studyId, true);
        if (study.getScheduleGuid() == null) {
            throw new EntityNotFoundException(Schedule2.class);
        }
        Timeline timeline = schedule2Service.getTimelineForSchedule(appId,
                study.getScheduleGuid());

        // Check Synapse
        synapseHelper.checkSynapseWritableOrThrow();

        // If Exporter3 is not enabled for study, initiate Exporter3 for Study;
        if (!study.isExporter3Enabled()) {
            initExporter3ForStudy(appId, studyId);
            study = studyService.getStudy(appId, studyId, true);
        }
        Exporter3Configuration exporter3Config = study.getExporter3Configuration();

        // Export the study's Timeline to Synapse as a wiki page in JSON format.
        // If we have an IOException, the wiki page will almost certainly fail to upload. Instead of catching the exception,
        // we just let the exception get thrown.
        JsonNode node = BridgeObjectMapper.get().valueToTree(timeline);
        File outputFile = File.createTempFile("timelineFor" + studyId, ".txt");
        CharSink charSink = Files.asCharSink(outputFile, Charsets.UTF_8, FileWriteMode.APPEND);
        charSink.write(node.toString());

        // Create a PutObjectRequest with the source file and destination information
        String s3Key = appId + "/" + studyId;
        PutObjectRequest request = new PutObjectRequest(rawHealthDataBucket, s3Key, outputFile);

        // Upload the file to Amazon S3
        s3Client.putObject(request);
        LOG.info("File uploaded successfully to S3.");

        // Create Synapse S3 file handle.
        S3FileHandle markdown = new S3FileHandle();
        markdown.setBucketName(rawHealthDataBucket);
        markdown.setContentType("text/plain");
        markdown.setFileName("timelineFor" + studyId + ".txt");
        markdown.setKey(s3Key);
        markdown.setStorageLocationId(exporter3Config.getStorageLocationId());
        // Synapse needs a hexadecimal MD5.
        byte[] md5 = md5DigestUtils.digest(outputFile);
        markdown.setContentMd5(Hex.encodeToString(md5));
        markdown = synapseHelper.createS3FileHandleWithRetry(markdown);

        // If first time exporting the timeline for the study, create a new wiki page in Synapse;
        // If wiki page already exists, update the existing wiki page.
        if (exporter3Config.getWikiPageId() == null) {
            V2WikiPage wiki = new V2WikiPage();
            wiki.setTitle("Exported Timeline for " + studyId);
            wiki.setMarkdownFileHandleId(markdown.getId());
            wiki = synapseClient.createV2WikiPage(exporter3Config.getProjectId(), ObjectType.ENTITY, wiki);
            exporter3Config.setWikiPageId(wiki.getId());
            studyService.updateStudy(appId, study);
        } else {
            WikiPageKey key = WikiPageKeyHelper.createWikiPageKey(exporter3Config.getProjectId(), ObjectType.ENTITY, exporter3Config.getWikiPageId());
            V2WikiPage getWiki = synapseClient.getV2WikiPage(key);
            getWiki.setMarkdownFileHandleId(markdown.getId());
            synapseClient.updateV2WikiPage(exporter3Config.getProjectId(), ObjectType.ENTITY, getWiki);
        }
        return exporter3Config;
    }
}
