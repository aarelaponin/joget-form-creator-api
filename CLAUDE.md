# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Joget DX8 API plugin for creating forms programmatically via REST API. It creates an OSGi bundle that integrates with the Joget platform to expose form creation capabilities through HTTP endpoints.

**Package:** `global.govstack.formcreator`
**Version:** 8.1-SNAPSHOT

## Build Commands

```bash
# Build the plugin (produces target/form-creator-api-8.1-SNAPSHOT.jar)
mvn clean package

# Run tests
mvn test

# Build without tests
mvn clean package -DskipTests
```

## Deployment

Upload the built JAR through Joget's Manage Plugins interface (Settings > Manage Plugins) for hot deployment, or copy to `wflow/app_plugins/` and restart Joget.

## Architecture

This is a Joget API Plugin (extends `ApiPluginAbstract`) following the OSGi bundle pattern:

- **Entry Point:** `FormCreatorServiceProvider` - Handles HTTP requests at `/jw/api/formcreator/forms`
- **Business Logic:** `FormCreationService` - Orchestrates form, API endpoint, and CRUD creation
- **Models:** `FormCreationRequest`/`FormCreationResponse` - Request/response DTOs
- **Exceptions:** Custom exception hierarchy (`ApiProcessingException`, `ValidationException`, `FormCreationException`)
- **Constants:** `ApiConstants` - Central location for all constants, bean names, error messages

Request flow:
1. `FormCreatorServiceProvider.createForm()` receives HTTP POST
2. `UserContextUtil.executeAsSystemUser()` sets admin context
3. `RequestParserUtil` parses JSON or multipart request into `FormCreationRequest`
4. `FormCreationService.processFormCreationRequest()` validates and creates form

## Key Implementation Notes

- Supports both `application/json` and `multipart/form-data` content types
- Uses Joget's `AppService`, `FormService` beans for form operations
- Form definition is expected as a JSON string representing Joget form structure
- Optional flags `createApiEndpoint` and `createCrud` trigger additional component creation

## Current Development Status

The plugin infrastructure is complete. Form creation services are stubbed with TODO markers at:
- `FormCreationService.java:280` - Form definition creation
- `FormCreationService.java:342` - API endpoint creation
- `FormCreationService.java:370` - CRUD interface creation

These need integration with services from the sibling `form-creator` plugin located at `../form-creator/`.

## Testing

```bash
# Run test script against local Joget
cd examples && ./test-api.sh

# Manual curl test
curl -X POST http://localhost:8080/jw/api/formcreator/forms \
  -H "api_id: YOUR_API_ID" \
  -H "api_key: YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d @examples/simple-form-request.json
```
