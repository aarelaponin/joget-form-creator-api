# Form Creator API Plugin

**Version:** 8.1-SNAPSHOT
**Package:** `global.govstack.formcreator`
**Type:** Joget API Plugin

A Joget DX8 API plugin for creating forms programmatically via REST API. This plugin enables external systems to create Joget forms, API endpoints, and CRUD interfaces through HTTP requests.

## Overview

This plugin solves the limitation where Joget's **Post Processing Tools** are not invoked when forms are submitted via API. By implementing as an **API Plugin**, it provides programmatic access to form creation capabilities.

### Key Features

- ✅ **RESTful API** - Create forms via HTTP POST requests
- ✅ **JSON Support** - Accept form definitions as JSON in request body
- ✅ **File Upload Support** - Accept form definitions as uploaded JSON files (planned)
- ✅ **Optional API Endpoint Creation** - Automatically create API endpoints for forms
- ✅ **Optional CRUD Creation** - Automatically create datalist and userview interfaces
- ✅ **Target Application Support** - Create forms in specific applications
- ✅ **Comprehensive Error Handling** - Proper HTTP status codes and error messages
- ✅ **Authentication** - API key-based authentication

## Requirements

- Joget DX8 Platform
- Java 11 or higher
- Maven 3.6+

## Quick Start

### 1. Build the Plugin

```bash
mvn clean package
```

The compiled plugin will be at `target/form-creator-api-8.1-SNAPSHOT.jar`

### 2. Deploy to Joget

**Option A: Hot Deploy (Recommended)**
1. Upload the JAR file through Joget's Manage Plugins interface (Settings → Manage Plugins)
2. The plugin will be hot-deployed without server restart

**Option B: Manual Deploy**
1. Copy `target/form-creator-api-8.1-SNAPSHOT.jar` to Joget's `wflow/app_plugins/`
2. Restart Joget server

### 3. Test the API

```bash
curl -X POST http://localhost:8080/jw/api/formcreator/forms \
  -H "api_id: YOUR_API_ID" \
  -H "api_key: YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "formId": "test_form",
    "formName": "Test Form",
    "tableName": "app_fd_test",
    "formDefinition": "{\"id\":\"test_form\",\"name\":\"Test Form\",\"tableName\":\"app_fd_test\",\"elements\":[{\"className\":\"org.joget.apps.form.lib.TextField\",\"properties\":{\"id\":\"field1\",\"label\":\"Field 1\"}}]}",
    "createApiEndpoint": true,
    "createCrud": true
  }'
```

Expected response:
```json
{
  "status": "success",
  "formId": "test_form",
  "apiId": "API-12345678-90ab-cdef-1234-567890abcdef",
  "datalistId": "list_test_form",
  "userviewId": "v",
  "message": "Form created successfully with API endpoint and CRUD interface",
  "timestamp": "2025-11-23T10:30:00Z"
}
```

## API Reference

### Create Form Endpoint

**Endpoint:** `POST /jw/api/formcreator/forms`

**Authentication:** API Key (api_id and api_key headers)

**Request Headers:**
```
Content-Type: application/json
api_id: <your_api_id>
api_key: <your_api_key>
```

**Request Body:**
```json
{
  "formId": "contact_form",              // Required: Unique form identifier
  "formName": "Contact Form",            // Required: Display name
  "tableName": "app_fd_contact",         // Required: Database table name
  "formDefinition": "{...}",             // Required: Form JSON definition
  "targetAppId": "myapp",                // Optional: Target application ID
  "targetAppVersion": "1",               // Optional: Target app version
  "createApiEndpoint": true,             // Optional: Create API endpoint
  "apiName": "Contact API",              // Optional: API endpoint name
  "createCrud": true                     // Optional: Create CRUD interface
}
```

**Request Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| formId | string | Yes | Unique identifier for the form |
| formName | string | Yes | Display name for the form |
| tableName | string | Yes | Database table name for form data |
| formDefinition | string | Yes | Form definition as JSON string |
| targetAppId | string | No | Target application ID (uses current if not specified) |
| targetAppVersion | string | No | Target application version (uses latest if not specified) |
| createApiEndpoint | boolean | No | Create API endpoint for the form (default: false) |
| apiName | string | No | Name for the API endpoint (defaults to formName + " API") |
| createCrud | boolean | No | Create CRUD interface (default: false) |

**Success Response (200 OK):**
```json
{
  "status": "success",
  "formId": "contact_form",
  "apiId": "API-a1b2c3d4-e5f6-7g8h-9i0j-k1l2m3n4o5p6",
  "datalistId": "list_contact_form",
  "userviewId": "v",
  "message": "Form created successfully with API endpoint and CRUD interface",
  "timestamp": "2025-11-23T10:30:00Z"
}
```

**Error Response (400 Bad Request):**
```json
{
  "status": "error",
  "errorType": "Validation Error",
  "errorMessage": "formId is required",
  "timestamp": "2025-11-23T10:30:00Z"
}
```

**Error Response (500 Internal Server Error):**
```json
{
  "status": "error",
  "errorType": "Form Creation Error",
  "errorMessage": "Failed to register form in database",
  "timestamp": "2025-11-23T10:30:00Z"
}
```

### Error Codes

| HTTP Code | Error Type | Description |
|-----------|------------|-------------|
| 200 | Success | Form created successfully |
| 400 | Validation Error | Required parameters missing or invalid |
| 400 | Invalid JSON | Form definition JSON is malformed |
| 404 | App Not Found | Target application does not exist |
| 500 | Form Creation Error | Error creating form definition |
| 500 | Internal Server Error | Unexpected error during processing |

## Project Structure

```
form-creator-api/
├── pom.xml                                          # Maven build configuration
├── README.md                                        # This file
├── FORM_CREATOR_API_ARCHITECTURE.md                 # Architecture specification
└── src/main/
    ├── java/global/govstack/formcreator/
    │   ├── Activator.java                           # OSGi bundle activator
    │   ├── constants/
    │   │   └── ApiConstants.java                    # All constants
    │   ├── exception/
    │   │   ├── ApiProcessingException.java          # Base API exception
    │   │   ├── ValidationException.java             # Validation errors
    │   │   └── FormCreationException.java           # Form creation errors
    │   ├── lib/
    │   │   └── FormCreatorServiceProvider.java      # Main API plugin
    │   ├── model/
    │   │   ├── FormCreationRequest.java             # Request model
    │   │   └── FormCreationResponse.java            # Response model
    │   ├── service/
    │   │   └── FormCreationService.java             # Business logic
    │   └── util/
    │       ├── ErrorResponseUtil.java               # Error response utilities
    │       ├── UserContextUtil.java                 # User context management
    │       └── RequestParserUtil.java               # Request parsing
    └── resources/
        └── properties/
            └── FormCreatorServiceProvider.json      # Plugin configuration
```

## Development Status

### Completed Components ✅

- [x] Maven project structure
- [x] OSGi bundle activator
- [x] API plugin infrastructure
- [x] Request/Response models
- [x] Exception hierarchy
- [x] Utility classes
- [x] Constants and configuration
- [x] Request validation
- [x] Error handling
- [x] Logging framework

### Pending Integration 🔨

The following components need to be integrated from the existing `form-creator` plugin:

1. **Form Creation Logic**
   - Integrate `FormDatabaseService.registerFormDirectToDatabaseWithResult()`
   - Integrate `FormDeploymentService.registerFormViaAppService()`
   - Location in current plugin: `/Users/aarelaponin/IdeaProjects/gs-plugins/form-creator/src/main/java/com/fiscaladmin/gam/formcreator/service/`

2. **API Endpoint Creation**
   - Integrate `ApiBuilderService.createApiEndpoint()`
   - Location in current plugin: `/Users/aarelaponin/IdeaProjects/gs-plugins/form-creator/src/main/java/com/fiscaladmin/gam/formcreator/service/ApiBuilderService.java`

3. **CRUD Creation**
   - Integrate `CrudService.createCrud()`
   - Integrate `DatalistService` and `UserviewService`
   - Location in current plugin: `/Users/aarelaponin/IdeaProjects/gs-plugins/form-creator/src/main/java/com/fiscaladmin/gam/formcreator/service/`

### Integration Points

Look for `TODO` comments in the code:
- `FormCreationService.java` line ~280: `createFormDefinition()` method
- `FormCreationService.java` line ~320: `createApiEndpoint()` method
- `FormCreationService.java` line ~340: `createCrudInterface()` method

## Integration Steps

### Step 1: Copy Service Classes

Copy the following service classes from the original `form-creator` plugin:

```bash
# From form-creator plugin to form-creator-api plugin
cp ../form-creator/src/main/java/com/fiscaladmin/gam/formcreator/service/FormDatabaseService.java \
   src/main/java/global/govstack/formcreator/service/

cp ../form-creator/src/main/java/com/fiscaladmin/gam/formcreator/service/FormDeploymentService.java \
   src/main/java/global/govstack/formcreator/service/

cp ../form-creator/src/main/java/com/fiscaladmin/gam/formcreator/service/ApiBuilderService.java \
   src/main/java/global/govstack/formcreator/service/

cp ../form-creator/src/main/java/com/fiscaladmin/gam/formcreator/service/CrudService.java \
   src/main/java/global/govstack/formcreator/service/

cp ../form-creator/src/main/java/com/fiscaladmin/gam/formcreator/service/DatalistService.java \
   src/main/java/global/govstack/formcreator/service/

cp ../form-creator/src/main/java/com/fiscaladmin/gam/formcreator/service/UserviewService.java \
   src/main/java/global/govstack/formcreator/service/

cp ../form-creator/src/main/java/com/fiscaladmin/gam/formcreator/service/JsonProcessingService.java \
   src/main/java/global/govstack/formcreator/service/
```

### Step 2: Update Package Names

Update the package declarations in copied files from:
```java
package com.fiscaladmin.gam.formcreator.service;
```

To:
```java
package global.govstack.formcreator.service;
```

### Step 3: Update Constant References

Update constant references from:
```java
import com.fiscaladmin.gam.formcreator.constants.Constants;
Constants.FieldNames.FORM_ID
```

To:
```java
import global.govstack.formcreator.constants.ApiConstants;
ApiConstants.RequestFields.FORM_ID
```

### Step 4: Integrate with FormCreationService

Update the TODO sections in `FormCreationService.java`:

```java
// Replace TODO in createFormDefinition()
FormCreationResult formResult = formDatabaseService.registerFormDirectToDatabaseWithResult(
    appService, appDef,
    request.getFormId(),
    request.getFormName(),
    request.getTableName(),
    request.getFormDefinitionJson(),
    formObject
);

if (!formResult.isSuccess()) {
    throw new FormCreationException(formResult.getErrorMessage());
}
```

### Step 5: Build and Test

```bash
# Build the plugin
mvn clean package

# Deploy to Joget
# Upload target/form-creator-api-8.1-SNAPSHOT.jar via Joget UI

# Test the API
curl -X POST http://localhost:8080/jw/api/formcreator/forms \
  -H "api_id: test_api" \
  -H "api_key: test_key" \
  -H "Content-Type: application/json" \
  -d @test-request.json
```

## Testing

### Unit Tests

```bash
mvn test
```

### Integration Tests

```bash
# Test minimal form creation
curl -X POST http://localhost:8080/jw/api/formcreator/forms \
  -H "api_id: test_api" \
  -H "api_key: test_key" \
  -H "Content-Type: application/json" \
  -d '{
    "formId": "simple_form",
    "formName": "Simple Form",
    "tableName": "app_fd_simple",
    "formDefinition": "{\"id\":\"simple_form\",\"name\":\"Simple Form\",\"tableName\":\"app_fd_simple\",\"elements\":[{\"className\":\"org.joget.apps.form.lib.TextField\",\"properties\":{\"id\":\"field1\",\"label\":\"Field 1\"}}]}"
  }'

# Test form with API and CRUD
curl -X POST http://localhost:8080/jw/api/formcreator/forms \
  -H "api_id: test_api" \
  -H "api_key: test_key" \
  -H "Content-Type: application/json" \
  -d '{
    "formId": "contact_form",
    "formName": "Contact Form",
    "tableName": "app_fd_contact",
    "formDefinition": "{...}",
    "createApiEndpoint": true,
    "createCrud": true
  }'

# Verify form in database
mysql -h localhost -u root -p jwdb \
  -e "SELECT * FROM app_form WHERE formId='simple_form';"
```

## Troubleshooting

### Common Issues

| Issue | Solution |
|-------|----------|
| **"formId is required"** | Ensure all required fields are in the request body |
| **"Invalid JSON format"** | Validate the request body JSON syntax |
| **"Form definition JSON is invalid"** | Check the form definition JSON structure |
| **"Target application not found"** | Verify the targetAppId and targetAppVersion |
| **API returns 500** | Check Joget logs for detailed error messages |

### Debug Logging

Enable debug mode in the plugin configuration to see detailed logs:

```bash
# Check Joget logs
tail -f logs/joget.log | grep -E "(FormCreatorServiceProvider|FormCreationService)"
```

Expected logs:
```
INFO - === Form Creation Request Received ===
INFO - Target App ID: myapp
INFO - Processing form creation request
DEBUG - Validating request: FormCreationRequest{formId='test_form'...}
INFO - Request validation passed
INFO - Creating form: test_form
INFO - Form created successfully: test_form
INFO - === Form Creation Successful ===
```

## Architecture

This plugin follows the architecture pattern from the `processing-server` plugin:

```
┌─────────────────────────────────────────────────────────────────┐
│                    API Request (HTTP POST)                       │
│        POST /jw/api/formcreator/forms                            │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│          FormCreatorServiceProvider.createForm()                 │
│              (API Plugin - Entry Point)                          │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│       UserContextUtil.executeAsSystemUser()                      │
│              (Set admin context)                                 │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│       FormCreationService.processRequest()                       │
│  1. Parse and validate request                                   │
│  2. Get target application                                       │
│  3. Create form definition                                       │
│  4. Optionally create API endpoint                               │
│  5. Optionally create CRUD interface                             │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│            Return HTTP Response (200/400/500)                    │
│  {                                                               │
│    "status": "success",                                          │
│    "formId": "test_form",                                        │
│    "apiId": "API-xxx...",                                        │
│    "message": "Form created successfully"                        │
│  }                                                               │
└─────────────────────────────────────────────────────────────────┘
```

## Documentation

- **[FORM_CREATOR_API_ARCHITECTURE.md](FORM_CREATOR_API_ARCHITECTURE.md)** - Complete architecture and design specification
- **[Joget API Plugin Guide](https://dev.joget.org/community/display/DX8/API+Builder)** - Official Joget API documentation

## Version History

- **8.1-SNAPSHOT**: Initial implementation
  - Core API plugin infrastructure
  - Request/response models
  - Validation and error handling
  - Service layer stubs (pending integration)

## License

Part of the GovStack initiative.
https://www.govstack.global

---

**Version**: 8.1-SNAPSHOT
**Package**: `global.govstack.formcreator`
**Last Updated**: November 23, 2025
**Status**: Initial Implementation (Pending Service Integration)
