# Joget Form Creator API

A Joget DX8 plugin that creates forms programmatically via REST API. Deploy form definitions, generate API endpoints, and create CRUD interfaces through HTTP requests.

## What It Does

This plugin enables external systems to:
- **Create Forms** - Register form definitions in Joget with database table generation
- **Create API Endpoints** - Automatically expose forms via REST API
- **Create CRUD Interfaces** - Generate datalist and userview (menu) for form management

### Companion Tool

Use with [joget-form-generator](https://github.com/aarelaponin/joget-form-generator) (Python) to generate form definition JSON files, then deploy them using this API.

## Requirements

- Joget DX8 Platform
- Java 11+
- Maven 3.6+

## Quick Start

### 1. Build

```bash
mvn clean package
```

Output: `target/form-creator-api-8.1-SNAPSHOT.jar`

### 2. Deploy

Upload the JAR via Joget UI: **Settings → Manage Plugins → Upload Plugin**

### 3. Use

```bash
curl -X POST http://localhost:8080/jw/api/formcreator/forms \
  -H "api_id: YOUR_API_ID" \
  -H "api_key: YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "formId": "contact_form",
    "formName": "Contact Form",
    "tableName": "contact",
    "formDefinition": "{\"className\":\"org.joget.apps.form.model.Form\",\"properties\":{\"id\":\"contact_form\",\"name\":\"Contact Form\",\"tableName\":\"contact\"},\"elements\":[...]}",
    "createApiEndpoint": true,
    "createCrud": true
  }'
```

**Response:**
```json
{
  "status": "success",
  "formId": "contact_form",
  "apiId": "API-12345678-90ab-cdef-1234-567890abcdef",
  "datalistId": "list_contact_form",
  "userviewId": "v",
  "message": "Form created successfully with API endpoint and CRUD interface"
}
```

## API Reference

### Create Form

**Endpoint:** `POST /jw/api/formcreator/forms`

**Headers:**
```
Content-Type: application/json
api_id: <your_api_id>
api_key: <your_api_key>
```

**Request Body:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `formId` | string | Yes | Unique form identifier |
| `formName` | string | Yes | Display name |
| `tableName` | string | Yes | Database table name (without `app_fd_` prefix) |
| `formDefinition` | string | Yes | Form JSON definition |
| `targetAppId` | string | No | Target application ID |
| `targetAppVersion` | string | No | Target application version |
| `createApiEndpoint` | boolean | No | Create REST API for the form (default: false) |
| `apiName` | string | No | API endpoint name |
| `createCrud` | boolean | No | Create datalist + userview (default: false) |

**Response Codes:**

| Code | Description |
|------|-------------|
| 200 | Success |
| 400 | Validation error (missing/invalid parameters) |
| 404 | Target application not found |
| 500 | Server error |

### Multipart Upload

Also supports `multipart/form-data` for file uploads:

```bash
curl -X POST http://localhost:8080/jw/api/formcreator/forms \
  -H "api_id: YOUR_API_ID" \
  -H "api_key: YOUR_API_KEY" \
  -F "formId=contact_form" \
  -F "formName=Contact Form" \
  -F "tableName=contact" \
  -F "formDefinitionFile=@my-form.json" \
  -F "createApiEndpoint=true" \
  -F "createCrud=true"
```

## Architecture

```
HTTP POST /jw/api/formcreator/forms
         │
         ▼
┌─────────────────────────────────┐
│  FormCreatorServiceProvider     │  ← API Plugin entry point
└─────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────┐
│  FormCreationService            │  ← Orchestrator
│  ├── Validate request           │
│  ├── Bootstrap check            │
│  └── Create components          │
└─────────────────────────────────┘
         │
         ├──────────────────────────────────────┐
         ▼                                      ▼
┌─────────────────────┐              ┌─────────────────────┐
│ FormDatabaseService │              │   ApiBuilderService │
│ - Register form     │              │   - Create API      │
│ - Create table      │              │     endpoint        │
│ - Invalidate cache  │              └─────────────────────┘
└─────────────────────┘
         │
         ▼
┌─────────────────────┐              ┌─────────────────────┐
│    CrudService      │──────────────│  JsonProcessing     │
│ - DatalistService   │              │  Service            │
│ - UserviewService   │              └─────────────────────┘
└─────────────────────┘
```

## Project Structure

```
src/main/java/global/govstack/formcreator/
├── lib/
│   └── FormCreatorServiceProvider.java   # API entry point
├── service/
│   ├── FormCreationService.java          # Main orchestrator
│   ├── FormDatabaseService.java          # Form registration
│   ├── ApiBuilderService.java            # API endpoint creation
│   ├── CrudService.java                  # CRUD orchestration
│   ├── DatalistService.java              # Datalist creation
│   ├── UserviewService.java              # Userview/menu creation
│   ├── JsonProcessingService.java        # JSON generation
│   └── FormCreatorBootstrapService.java  # Self-bootstrap
├── model/
│   ├── FormCreationRequest.java
│   ├── FormCreationResponse.java
│   └── ...
├── exception/
│   ├── ValidationException.java
│   ├── FormCreationException.java
│   └── ApiProcessingException.java
├── util/
│   ├── RequestParserUtil.java
│   ├── MultipartRequestParser.java
│   ├── UserContextUtil.java
│   └── ErrorResponseUtil.java
└── constants/
    └── ApiConstants.java
```

## Troubleshooting

### Debug Logging

```bash
tail -f logs/joget.log | grep -i formcreator
```

### Common Issues

| Issue | Solution |
|-------|----------|
| "formId is required" | Ensure all required fields are in request |
| "Invalid JSON format" | Validate request body JSON syntax |
| "Target application not found" | Check targetAppId/targetAppVersion |
| 500 error | Check Joget logs for details |

## Examples

See the `examples/` directory:
- `simple-form-request.json` - Minimal form creation
- `complete-form-request.json` - Full form with API and CRUD
- `test-api.sh` - Test script

## License

Part of the GovStack initiative: https://www.govstack.global

---

**Version:** 8.1-SNAPSHOT
**Package:** `global.govstack.formcreator`
