package global.govstack.formcreator.service;

import global.govstack.formcreator.constants.ApiConstants;
import global.govstack.formcreator.model.ApiCreationResult;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.model.BuilderDefinition;
import org.joget.apps.app.dao.BuilderDefinitionDao;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;

import java.io.File;
import java.io.FileWriter;
import java.util.Date;
import java.util.UUID;

/**
 * Service class for creating API endpoints using Joget's Builder API.
 * This service handles both file system and database operations for API definitions.
 */
public class ApiBuilderService {

    private static final String CLASS_NAME = ApiBuilderService.class.getName();
    private final JsonProcessingService jsonProcessingService;

    /**
     * Constructor with JsonProcessingService dependency injection
     *
     * @param jsonProcessingService Service for JSON generation and processing
     */
    public ApiBuilderService(JsonProcessingService jsonProcessingService) {
        this.jsonProcessingService = jsonProcessingService;
    }

    /**
     * Creates an API endpoint for a form using Joget's dual storage pattern.
     * This method creates both the file system and database entries required for the API to be visible in Joget UI.
     *
     * @param formId The form ID to create the API endpoint for
     * @param apiName The display name for the API endpoint
     * @param appDef The application definition containing the target app
     * @return ApiCreationResult indicating success or failure with the API ID
     */
    public ApiCreationResult createApiEndpoint(String formId, String apiName, AppDefinition appDef) {
        String apiId = null;
        try {
            LogUtil.info(CLASS_NAME, "Creating API endpoint for form: " + formId);

            // Generate UUID for API
            String apiUuid = UUID.randomUUID().toString();
            apiId = ApiConstants.IdPrefixes.API + apiUuid;

            // Generate API definition JSON
            String apiJson = jsonProcessingService.generateApiDefinitionJson(formId, apiName, apiUuid);

            if (apiJson == null) {
                LogUtil.error(CLASS_NAME, null, "Failed to generate API JSON for form: " + formId);
                return ApiCreationResult.error("API JSON generation failed - generateApiDefinitionJson returned null");
            }

            // Step 1: Write API definition file to file system
            String jogetDir = System.getProperty("user.dir");
            String apiDir = jogetDir + ApiConstants.Paths.APP_SRC + "/" + appDef.getAppId() + "/" +
                           appDef.getAppId() + "_" + appDef.getVersion() + ApiConstants.Paths.BUILDER_DIR + ApiConstants.Paths.API_DIR;

            // Create directory if it doesn't exist
            File apiDirectory = new File(apiDir);
            if (!apiDirectory.exists()) {
                boolean created = apiDirectory.mkdirs();
                if (!created) {
                    LogUtil.error(CLASS_NAME, null, "Failed to create API directory: " + apiDir);
                    return ApiCreationResult.error("Failed to create API directory: " + apiDir);
                }
                LogUtil.info(CLASS_NAME, "API directory created at: " + apiDir);
            }

            // Write API definition file
            String apiFilePath = apiDir + "/" + apiId + ApiConstants.Paths.JSON_EXTENSION;
            try (FileWriter fileWriter = new FileWriter(apiFilePath)) {
                fileWriter.write(apiJson);
            }
            LogUtil.info(CLASS_NAME, "API file created at: " + apiFilePath);

            // Step 2: Save API definition to database using BuilderDefinitionDao
            BuilderDefinitionDao builderDefDao = (BuilderDefinitionDao)
                AppUtil.getApplicationContext().getBean(ApiConstants.BeanNames.BUILDER_DEFINITION_DAO);

            if (builderDefDao == null) {
                LogUtil.error(CLASS_NAME, null, "BuilderDefinitionDao not available - cannot save API to database");
                return ApiCreationResult.error("BuilderDefinitionDao not available - API file created but not in database");
            }

            BuilderDefinition builderDef = new BuilderDefinition();
            builderDef.setAppId(appDef.getAppId());
            builderDef.setAppVersion(appDef.getVersion());
            builderDef.setId(apiId);
            builderDef.setName(apiName);
            builderDef.setType(ApiConstants.BuilderTypes.API);
            builderDef.setJson(apiJson);
            builderDef.setDateCreated(new Date());
            builderDef.setDateModified(new Date());
            builderDef.setAppDefinition(appDef);

            builderDefDao.add(builderDef);
            LogUtil.info(CLASS_NAME, "API definition saved to database with ID: " + apiId);

            LogUtil.info(CLASS_NAME, "SUCCESS: API endpoint created: " + apiId);
            LogUtil.info(CLASS_NAME, "API endpoint URL: /jw/api/" + appDef.getAppId() + "/" + appDef.getVersion() + "/form/" + formId);

            return ApiCreationResult.success(apiId);

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error creating API endpoint: " + e.getMessage());
            return ApiCreationResult.error("Exception during API creation: " + e.getMessage());
        }
    }
}
