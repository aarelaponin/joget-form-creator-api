package global.govstack.formcreator.lib;

import global.govstack.formcreator.constants.ApiConstants;
import global.govstack.formcreator.exception.ApiProcessingException;
import global.govstack.formcreator.model.FormCreationRequest;
import global.govstack.formcreator.service.FormCreationService;
import global.govstack.formcreator.util.ErrorResponseUtil;
import global.govstack.formcreator.util.MultipartRequestParser;
import global.govstack.formcreator.util.RequestParserUtil;
import global.govstack.formcreator.util.UserContextUtil;
import org.joget.api.annotations.Operation;
import org.joget.api.annotations.Param;
import org.joget.api.annotations.Response;
import org.joget.api.annotations.Responses;
import org.joget.api.model.ApiPluginAbstract;
import org.joget.api.model.ApiResponse;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.property.model.PropertyEditable;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.json.JSONObject;

import javax.servlet.http.HttpServletRequest;

/**
 * Form Creator Service Provider - API Plugin for creating Joget forms via REST API.
 *
 * This plugin provides endpoints for:
 * - Creating forms from JSON definitions
 * - Creating API endpoints for forms
 * - Creating CRUD interfaces (datalist + userview)
 *
 * Based on the architecture pattern from the processing-server plugin.
 */
public class FormCreatorServiceProvider extends ApiPluginAbstract implements PropertyEditable {

    private static final String CLASS_NAME = "global.govstack.formcreator.lib.FormCreatorServiceProvider";

    @Override
    public String getName() {
        return "formcreator-api";
    }

    @Override
    public String getVersion() {
        return "8.1-SNAPSHOT";
    }

    @Override
    public String getDescription() {
        return "Form Creator API - Create Joget forms, API endpoints, and CRUD interfaces via REST API";
    }

    @Override
    public String getTag() {
        return "formcreator";
    }

    @Override
    public String getIcon() {
        return "<i class=\"fa fa-file-code-o\"></i>";
    }

    @Override
    public String getLabel() {
        return "Form Creator API";
    }

    @Override
    public String getClassName() {
        return CLASS_NAME;
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(
            getClass().getName(),
            "/properties/FormCreatorServiceProvider.json",
            null,
            true,
            null
        );
    }

    /**
     * Create a new form from JSON definition or multipart file upload
     *
     * Endpoint: POST /jw/api/formcreator/forms
     *
     * Supports two content types:
     * 1. application/json - JSON body with formDefinition as string
     * 2. multipart/form-data - Form fields + file upload
     *
     * @param appId Target application ID (optional, uses current app if not specified)
     * @param appVersion Target application version (optional, uses latest if not specified)
     * @param request HttpServletRequest for accessing multipart data
     * @param requestBody JSON request body (for non-multipart requests)
     * @return ApiResponse with form creation result
     */
    @Operation(
        path = "/formcreator/forms",
        type = Operation.MethodType.POST,
        summary = "Create a new form from JSON definition or file upload",
        description = "Creates a Joget form based on provided JSON definition and metadata. " +
                      "Supports both JSON (application/json) and file upload (multipart/form-data). " +
                      "Optionally creates API endpoint and CRUD interface. " +
                      "Requires formId, formName, tableName, and formDefinition (or formDefinitionFile)."
    )
    @Responses({
        @Response(responseCode = 200, description = "Form created successfully"),
        @Response(responseCode = 400, description = "Invalid request - validation failed"),
        @Response(responseCode = 500, description = "Server error during form creation")
    })
    public ApiResponse createForm(
        @Param(value = "appId", required = false) String appId,
        @Param(value = "appVersion", required = false) String appVersion,
        @Param(value = "request", required = false) HttpServletRequest request,
        @Param(value = "body", required = false) String requestBody
    ) {
        LogUtil.info(CLASS_NAME, "=== Form Creation Request Received ===");
        LogUtil.info(CLASS_NAME, "Target App ID: " + (appId != null ? appId : "current"));
        LogUtil.info(CLASS_NAME, "Target App Version: " + (appVersion != null ? appVersion : "latest"));

        // Detect request type
        if (request != null && MultipartRequestParser.isMultipartRequest(request)) {
            LogUtil.info(CLASS_NAME, "Detected multipart/form-data request");
            return processMultipartRequest(appId, appVersion, request);
        } else {
            LogUtil.info(CLASS_NAME, "Detected application/json request");
            return processJsonRequest(appId, appVersion, requestBody);
        }
    }

    /**
     * Process JSON request (application/json)
     *
     * @param appId Target application ID
     * @param appVersion Target application version
     * @param requestBody JSON request body
     * @return ApiResponse with status code and response body
     */
    private ApiResponse processJsonRequest(String appId, String appVersion, String requestBody) {
        WorkflowUserManager workflowUserManager = getWorkflowUserManager();

        return UserContextUtil.executeAsSystemUser(workflowUserManager, () -> {
            try {
                // Log request details
                LogUtil.debug(CLASS_NAME, "Request body length: " +
                            (requestBody != null ? requestBody.length() : 0));

                // Parse JSON request
                FormCreationRequest request = RequestParserUtil.parseJsonRequest(requestBody);

                // Get FormCreationService
                FormCreationService creationService = new FormCreationService();

                // Process the request
                JSONObject response = creationService.processFormCreationRequest(appId, appVersion, request);

                LogUtil.info(CLASS_NAME, "=== Form Creation Successful ===");
                LogUtil.info(CLASS_NAME, "Response: " + response.toString());

                return new ApiResponse(ApiConstants.HttpStatus.OK, response.toString());

            } catch (ApiProcessingException e) {
                // Handle known processing exceptions with specific status codes
                return handleError(e.getStatusCode(), e.getErrorType(), e);

            } catch (Exception e) {
                // Handle unexpected exceptions
                return handleError(
                    ApiConstants.HttpStatus.INTERNAL_SERVER_ERROR,
                    ApiConstants.ErrorTypes.INTERNAL_SERVER_ERROR,
                    e
                );
            }
        });
    }

    /**
     * Process multipart request (multipart/form-data)
     *
     * @param appId Target application ID
     * @param appVersion Target application version
     * @param httpRequest HttpServletRequest
     * @return ApiResponse with status code and response body
     */
    private ApiResponse processMultipartRequest(String appId, String appVersion, HttpServletRequest httpRequest) {
        WorkflowUserManager workflowUserManager = getWorkflowUserManager();

        return UserContextUtil.executeAsSystemUser(workflowUserManager, () -> {
            try {
                // Parse multipart request
                LogUtil.info(CLASS_NAME, ">>> Parsing multipart request...");
                LogUtil.info(CLASS_NAME, "Content-Type: " + httpRequest.getContentType());
                LogUtil.info(CLASS_NAME, "Content-Length: " + httpRequest.getContentLength());

                MultipartRequestParser.MultipartData multipartData =
                    MultipartRequestParser.parseMultipartRequest(httpRequest);

                // Convert to FormCreationRequest
                FormCreationRequest request = RequestParserUtil.parseMultipartRequest(multipartData);

                LogUtil.info(CLASS_NAME, ">>> Multipart request parsed successfully!");
                LogUtil.info(CLASS_NAME, ">>> Request Details:");
                LogUtil.info(CLASS_NAME, "  - formId: " + request.getFormId());
                LogUtil.info(CLASS_NAME, "  - formName: " + request.getFormName());
                LogUtil.info(CLASS_NAME, "  - tableName: " + request.getTableName());
                LogUtil.info(CLASS_NAME, "  - targetAppId: " + request.getTargetAppId());
                LogUtil.info(CLASS_NAME, "  - targetAppVersion: " + request.getTargetAppVersion());
                LogUtil.info(CLASS_NAME, "  - createApiEndpoint: " + request.isCreateApiEndpoint());
                LogUtil.info(CLASS_NAME, "  - createCrud: " + request.isCreateCrud());

                if (request.getFormDefinitionFile() != null) {
                    LogUtil.info(CLASS_NAME, ">>> File Upload Detected:");
                    LogUtil.info(CLASS_NAME, "  - fileName: " + request.getFormDefinitionFileName());
                    LogUtil.info(CLASS_NAME, "  - fileSize: " + request.getFormDefinitionFile().length + " bytes");
                } else {
                    LogUtil.info(CLASS_NAME, ">>> No file uploaded (using inline JSON)");
                }

                // Get FormCreationService and process the request
                FormCreationService creationService = new FormCreationService();
                JSONObject response = creationService.processFormCreationRequest(appId, appVersion, request);

                LogUtil.info(CLASS_NAME, "=== Form Creation Successful ===");
                LogUtil.info(CLASS_NAME, "Response: " + response.toString());

                return new ApiResponse(ApiConstants.HttpStatus.OK, response.toString());

            } catch (ApiProcessingException e) {
                // Handle known processing exceptions with specific status codes
                return handleError(e.getStatusCode(), e.getErrorType(), e);

            } catch (Exception e) {
                // Handle unexpected exceptions
                LogUtil.error(CLASS_NAME, e, ">>> ERROR in processMultipartRequest");
                return handleError(
                    ApiConstants.HttpStatus.INTERNAL_SERVER_ERROR,
                    ApiConstants.ErrorTypes.INTERNAL_SERVER_ERROR,
                    e
                );
            }
        });
    }

    /**
     * Get the workflow user manager from application context
     *
     * @return WorkflowUserManager instance
     */
    protected WorkflowUserManager getWorkflowUserManager() {
        return (WorkflowUserManager) AppUtil.getApplicationContext()
            .getBean(ApiConstants.BeanNames.WORKFLOW_USER_MANAGER);
    }

    /**
     * Create a standardized error response
     *
     * @param statusCode HTTP status code
     * @param errorType Error type description
     * @param e The exception
     * @return ApiResponse with error details
     */
    protected ApiResponse handleError(int statusCode, String errorType, Exception e) {
        String errorMessage = e.getMessage();
        String logMessage = errorType + ": " + errorMessage;

        // Log based on severity
        logError(statusCode, e, logMessage);

        // For unexpected errors, don't expose internal details
        if (!(e instanceof ApiProcessingException)) {
            errorMessage = "An unexpected error occurred during form creation";
        }

        LogUtil.info(CLASS_NAME, "=== Form Creation Failed ===");
        LogUtil.info(CLASS_NAME, "Status Code: " + statusCode);
        LogUtil.info(CLASS_NAME, "Error Type: " + errorType);
        LogUtil.info(CLASS_NAME, "Error Message: " + errorMessage);

        return new ApiResponse(
            statusCode,
            ErrorResponseUtil.createErrorResponse(errorType, errorMessage)
        );
    }

    /**
     * Log messages based on status code severity
     *
     * Server errors (5xx) are logged as errors with stack traces.
     * Client errors (4xx) are logged as warnings.
     *
     * @param statusCode The HTTP status code
     * @param e The exception
     * @param message The message to log
     */
    protected void logError(int statusCode, Exception e, String message) {
        if (statusCode >= 500) {
            LogUtil.error(getClassName(), e, message);
        } else {
            LogUtil.warn(getClassName(), message);
        }
    }
}
