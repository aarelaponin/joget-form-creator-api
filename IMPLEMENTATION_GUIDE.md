# Form Creator API - Implementation Guide

This guide provides step-by-step instructions for completing the implementation by integrating the existing form-creator plugin's services.

## Current Status

✅ **Completed:**
- Core API plugin infrastructure
- Request/Response models
- Validation and error handling
- User context management
- Utility classes

🔨 **Pending:**
- Integration with form creation services
- Integration with API builder services
- Integration with CRUD creation services

## Integration Steps

### Step 1: Copy Service Classes from Original Plugin

Copy the following service files from `/Users/aarelaponin/IdeaProjects/gs-plugins/form-creator/`:

```bash
cd /Users/aarelaponin/IdeaProjects/gs-plugins/form-creator-api

# Create service directory if needed
mkdir -p src/main/java/global/govstack/formcreator/service

# Copy service classes
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

# Copy model classes (Result objects)
mkdir -p src/main/java/global/govstack/formcreator/model

cp ../form-creator/src/main/java/com/fiscaladmin/gam/formcreator/model/FormCreationResult.java \
   src/main/java/global/govstack/formcreator/model/

cp ../form-creator/src/main/java/com/fiscaladmin/gam/formcreator/model/ApiCreationResult.java \
   src/main/java/global/govstack/formcreator/model/

cp ../form-creator/src/main/java/com/fiscaladmin/gam/formcreator/model/CrudCreationResult.java \
   src/main/java/global/govstack/formcreator/model/
```

### Step 2: Update Package Declarations

In all copied files, update the package declaration:

**Find:**
```java
package com.fiscaladmin.gam.formcreator.service;
```

**Replace with:**
```java
package global.govstack.formcreator.service;
```

**Find:**
```java
package com.fiscaladmin.gam.formcreator.model;
```

**Replace with:**
```java
package global.govstack.formcreator.model;
```

### Step 3: Update Constant References

In all copied files, update constant references:

**Find:**
```java
import com.fiscaladmin.gam.formcreator.constants.Constants;
```

**Replace with:**
```java
import global.govstack.formcreator.constants.ApiConstants;
```

**Then update all usages:**
- `Constants.BeanNames.XXX` → `ApiConstants.BeanNames.XXX`
- `Constants.PropertyKeys.XXX` → `ApiConstants.PropertyKeys.XXX`
- `Constants.Paths.XXX` → `ApiConstants.Paths.XXX`
- etc.

### Step 4: Integrate Form Creation Logic

In `src/main/java/global/govstack/formcreator/service/FormCreationService.java`:

**Find the TODO at line ~280:**
```java
// TODO: Call FormDatabaseService.registerFormDirectToDatabaseWithResult()
// This should be integrated from the existing form-creator plugin
```

**Replace with:**
```java
// Initialize FormDatabaseService
FormDatabaseService formDatabaseService = new FormDatabaseService();

// Register form to database
FormCreationResult formResult = formDatabaseService.registerFormDirectToDatabaseWithResult(
    appService,
    appDef,
    request.getFormId(),
    request.getFormName(),
    request.getTableName(),
    request.getFormDefinitionJson(),
    formObject
);

// Check if form creation succeeded
if (!formResult.isSuccess()) {
    throw new FormCreationException(
        "Failed to create form: " + formResult.getErrorMessage()
    );
}

LogUtil.info(CLASS_NAME, "Form registered successfully: " + request.getFormId());

// Try FormDeploymentService if direct registration fails
if (!formResult.isSuccess()) {
    FormDeploymentService formDeploymentService = new FormDeploymentService();
    boolean deployed = formDeploymentService.registerFormViaAppService(
        appService,
        appDef,
        request.getFormId(),
        request.getFormName(),
        request.getTableName(),
        request.getFormDefinitionJson(),
        formObject
    );

    if (!deployed) {
        throw new FormCreationException("Form deployment failed");
    }
}

return true;
```

### Step 5: Integrate API Endpoint Creation

In `src/main/java/global/govstack/formcreator/service/FormCreationService.java`:

**Find the TODO at line ~320:**
```java
// TODO: Call ApiBuilderService.createApiEndpoint()
```

**Replace with:**
```java
// Initialize ApiBuilderService
JsonProcessingService jsonProcessingService = new JsonProcessingService();
ApiBuilderService apiBuilderService = new ApiBuilderService(jsonProcessingService);

// Determine API name
String apiName = request.getApiName();
if (apiName == null || apiName.trim().isEmpty()) {
    apiName = request.getFormName() + ApiConstants.Defaults.DEFAULT_API_NAME_SUFFIX;
}

// Create API endpoint
ApiCreationResult apiResult = apiBuilderService.createApiEndpoint(
    request.getFormId(),
    apiName,
    appDef
);

// Check result
if (apiResult.isSuccess()) {
    LogUtil.info(CLASS_NAME, "API endpoint created: " + apiResult.getApiId());
    return apiResult.getApiId();
} else {
    LogUtil.warn(CLASS_NAME, "API endpoint creation failed: " + apiResult.getErrorMessage());
    return null;
}
```

### Step 6: Integrate CRUD Creation

In `src/main/java/global/govstack/formcreator/service/FormCreationService.java`:

**Find the TODO at line ~340:**
```java
// TODO: Call CrudService.createCrud()
```

**Replace with:**
```java
// Initialize services
JsonProcessingService jsonProcessingService = new JsonProcessingService();
DatalistService datalistService = new DatalistService(jsonProcessingService);
UserviewService userviewService = new UserviewService(jsonProcessingService);
CrudService crudService = new CrudService(datalistService, userviewService);

// Create CRUD interface
CrudCreationResult crudResult = crudService.createCrud(
    request.getFormId(),
    request.getFormName(),
    appDef,
    request.getFormDefinitionJson()
);

// Check result
if (crudResult.isSuccess()) {
    LogUtil.info(CLASS_NAME, "CRUD created: " +
                crudResult.getDatalistId() + ", " + crudResult.getUserviewId());
    return new String[]{crudResult.getDatalistId(), crudResult.getUserviewId()};
} else {
    LogUtil.warn(CLASS_NAME, "CRUD creation failed: " + crudResult.getErrorMessage());
    return null;
}
```

### Step 7: Add Service Dependencies

At the top of `FormCreationService.java`, add instance variables for services:

```java
public class FormCreationService {

    private static final String CLASS_NAME = FormCreationService.class.getName();

    // Service dependencies
    private final JsonProcessingService jsonProcessingService;
    private final FormDatabaseService formDatabaseService;
    private final FormDeploymentService formDeploymentService;
    private final ApiBuilderService apiBuilderService;
    private final CrudService crudService;

    public FormCreationService() {
        this.jsonProcessingService = new JsonProcessingService();
        this.formDatabaseService = new FormDatabaseService();
        this.formDeploymentService = new FormDeploymentService();
        this.apiBuilderService = new ApiBuilderService(jsonProcessingService);

        DatalistService datalistService = new DatalistService(jsonProcessingService);
        UserviewService userviewService = new UserviewService(jsonProcessingService);
        this.crudService = new CrudService(datalistService, userviewService);
    }

    // ... rest of the class
}
```

### Step 8: Build and Test

```bash
# Build the plugin
cd /Users/aarelaponin/IdeaProjects/gs-plugins/form-creator-api
mvn clean package

# Check for compilation errors
# If there are errors, review the package names and imports

# Deploy to Joget
# Upload target/form-creator-api-8.1-SNAPSHOT.jar via Joget Manage Plugins UI
```

### Step 9: Test the Integration

```bash
# Test simple form creation
cd examples
./test-api.sh

# Or test manually
curl -X POST http://localhost:8080/jw/api/formcreator/forms \
  -H "api_id: YOUR_API_ID" \
  -H "api_key: YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d @simple-form-request.json

# Check Joget logs
tail -f ~/joget-enterprise-linux-8.1.6/logs/joget.log | grep FormCreator

# Verify in database
mysql -h localhost -u root -p jwdb \
  -e "SELECT formId, name, tableName FROM app_form WHERE formId='test_form';"
```

## Troubleshooting

### Compilation Errors

**Error:** `Cannot find symbol: class FormDatabaseService`
**Solution:** Ensure you copied all service classes and updated package names correctly.

**Error:** `Cannot find symbol: Constants.BeanNames`
**Solution:** Update constant references from `Constants` to `ApiConstants`.

**Error:** `Package com.fiscaladmin.gam.formcreator does not exist`
**Solution:** Update all package declarations in copied files to `global.govstack.formcreator`.

### Runtime Errors

**Error:** `FormDatabaseService not found`
**Solution:** Verify the service classes are included in the compiled JAR:
```bash
jar tf target/form-creator-api-8.1-SNAPSHOT.jar | grep FormDatabaseService
```

**Error:** `NullPointerException in FormCreationService`
**Solution:** Ensure services are initialized in the constructor.

**Error:** `Form creation failed`
**Solution:** Check Joget logs for detailed error messages:
```bash
tail -f logs/joget.log | grep -E "(FormCreator|FormDatabase|FormDeployment)"
```

## Testing Checklist

- [ ] Plugin compiles without errors
- [ ] Plugin deploys to Joget successfully
- [ ] Simple form creation works
- [ ] Form appears in Joget form list
- [ ] Form table is created in database
- [ ] API endpoint creation works (if requested)
- [ ] CRUD interface creation works (if requested)
- [ ] Validation errors return proper HTTP 400
- [ ] Server errors return proper HTTP 500
- [ ] Error messages are descriptive

## Next Steps

After completing integration:

1. **Add Unit Tests** - Test each service method
2. **Add Integration Tests** - Test end-to-end API calls
3. **Update Documentation** - Document any changes or issues encountered
4. **Performance Testing** - Test with large form definitions
5. **Security Review** - Ensure API key validation works correctly

## Support

If you encounter issues during integration:

1. Check the Joget logs for detailed error messages
2. Review the original form-creator plugin's implementation
3. Compare with the processing-server plugin's pattern
4. Refer to the architecture specification in FORM_CREATOR_API_ARCHITECTURE.md

---

**Last Updated:** November 23, 2025
**Status:** Ready for Integration
