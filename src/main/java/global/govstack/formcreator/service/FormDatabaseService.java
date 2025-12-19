package global.govstack.formcreator.service;

import global.govstack.formcreator.constants.ApiConstants;
import global.govstack.formcreator.model.InternalFormCreationResult;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.model.FormRowSet;
import org.joget.apps.form.service.FormService;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.commons.util.LogUtil;

import java.sql.*;
import java.util.*;

/**
 * Service class for handling direct database operations related to form definitions.
 * This service manages form registration, table discovery, and schema creation.
 */
public class FormDatabaseService {

    private static final String CLASS_NAME = FormDatabaseService.class.getName();

    /**
     * Register a form directly to the database, bypassing standard Joget APIs.
     * This method handles both INSERT and UPDATE operations, and manages cache invalidation.
     *
     * @param appService The application service
     * @param appDef The application definition
     * @param formId The form ID
     * @param formName The form name
     * @param tableName The database table name for form data
     * @param jsonContent The form definition JSON
     * @param formObject The form object (currently unused but kept for future compatibility)
     * @return true if registration was successful, false otherwise
     */
    public boolean registerFormDirectToDatabase(AppService appService, AppDefinition appDef, String formId,
                                                String formName, String tableName, String jsonContent, Form formObject) {
        try {
            LogUtil.info(CLASS_NAME, "Attempting direct database form registration");

            // Get DataSource from AppUtil
            Object dataSource = null;
            try {
                dataSource = AppUtil.getApplicationContext().getBean("setupDataSource");
                LogUtil.info(CLASS_NAME, "Retrieved setupDataSource: " + (dataSource != null));
            } catch (Exception e) {
                LogUtil.info(CLASS_NAME, "Could not get setupDataSource: " + e.getMessage());
            }

            if (dataSource == null) {
                try {
                    dataSource = AppUtil.getApplicationContext().getBean("dataSource");
                    LogUtil.info(CLASS_NAME, "Retrieved dataSource: " + (dataSource != null));
                } catch (Exception e) {
                    LogUtil.info(CLASS_NAME, "Could not get dataSource: " + e.getMessage());
                }
            }

            if (dataSource != null) {
                Connection conn = null;
                try {
                    // Get connection from DataSource
                    java.lang.reflect.Method getConnectionMethod = dataSource.getClass().getMethod("getConnection");
                    conn = (Connection) getConnectionMethod.invoke(dataSource);

                    LogUtil.info(CLASS_NAME, "Successfully got database connection");

                    // First, discover the correct table name by examining the database schema
                    String formTableName = discoverFormDefinitionTableName(conn);
                    if (formTableName == null) {
                        LogUtil.info(CLASS_NAME, "Could not find form definition table in database");
                        conn.close();
                        return false;
                    }

                    LogUtil.info(CLASS_NAME, "Using form definition table: " + formTableName);

                    // Get the actual column structure for this table
                    String[] tableColumns = getFormDefinitionColumns(conn, formTableName);
                    LogUtil.info(CLASS_NAME, "Table columns: " + Arrays.toString(tableColumns));

                    // Find the correct column names
                    String idColumn = findColumn(tableColumns, new String[]{"id", "formId", "form_id"});
                    String appIdColumn = findColumn(tableColumns, new String[]{"appId", "app_id"});
                    String versionColumn = findColumn(tableColumns, new String[]{"appVersion", "version", "app_version"});

                    LogUtil.info(CLASS_NAME, "Using columns - ID: " + idColumn + ", AppId: " + appIdColumn + ", Version: " + versionColumn);

                    // Check if form definition already exists using correct column names
                    boolean exists = false;
                    if (idColumn != null && appIdColumn != null && versionColumn != null) {
                        String checkSql = "SELECT COUNT(*) FROM " + formTableName + " WHERE " + idColumn + " = ? AND " + appIdColumn + " = ? AND " + versionColumn + " = ?";
                        PreparedStatement checkStmt = conn.prepareStatement(checkSql);
                        checkStmt.setString(1, formId);
                        checkStmt.setString(2, appDef.getAppId());
                        checkStmt.setString(3, appDef.getVersion().toString());

                        ResultSet rs = checkStmt.executeQuery();
                        if (rs.next()) {
                            exists = rs.getInt(1) > 0;
                        }
                        rs.close();
                        checkStmt.close();
                    } else {
                        LogUtil.info(CLASS_NAME, "Cannot determine key columns, skipping existence check");
                    }

                    LogUtil.info(CLASS_NAME, "Form exists check: " + exists);

                    if (exists && idColumn != null && appIdColumn != null && versionColumn != null) {
                        // Update existing form
                        String nameColumn = findColumn(tableColumns, new String[]{"name", "formName", "form_name"});
                        String tableColumn = findColumn(tableColumns, new String[]{"tableName", "table_name"});
                        String jsonColumn = findColumn(tableColumns, new String[]{"json", "definition", "form_json"});
                        String modifiedColumn = findColumn(tableColumns, new String[]{"dateModified", "modified", "date_modified"});

                        List<String> updateColumns = new ArrayList<>();
                        List<String> updateValues = new ArrayList<>();

                        if (nameColumn != null) {
                            updateColumns.add(nameColumn + " = ?");
                            updateValues.add(formName != null ? formName : formId);
                        }
                        if (tableColumn != null) {
                            updateColumns.add(tableColumn + " = ?");
                            updateValues.add(tableName != null ? tableName : formId);
                        }
                        if (jsonColumn != null) {
                            updateColumns.add(jsonColumn + " = ?");
                            updateValues.add(jsonContent);
                        }
                        if (modifiedColumn != null) {
                            updateColumns.add(modifiedColumn + " = ?");
                            updateValues.add(null); // Will be set as timestamp
                        }

                        if (!updateColumns.isEmpty()) {
                            String updateSql = "UPDATE " + formTableName + " SET " +
                                             String.join(", ", updateColumns) +
                                             " WHERE " + idColumn + " = ? AND " + appIdColumn + " = ? AND " + versionColumn + " = ?";
                            PreparedStatement updateStmt = conn.prepareStatement(updateSql);

                            int paramIndex = 1;
                            for (String value : updateValues) {
                                if (value != null) {
                                    updateStmt.setString(paramIndex++, value);
                                } else {
                                    updateStmt.setTimestamp(paramIndex++, new Timestamp(System.currentTimeMillis()));
                                }
                            }
                            updateStmt.setString(paramIndex++, formId);
                            updateStmt.setString(paramIndex++, appDef.getAppId());
                            updateStmt.setString(paramIndex++, appDef.getVersion().toString());

                            int updatedRows = updateStmt.executeUpdate();
                            updateStmt.close();

                            LogUtil.info(CLASS_NAME, "Updated " + updatedRows + " form definition rows");

                            // Force form cache invalidation after update
                            invalidateFormCaches(appService, appDef, formId);
                        }

                    } else {
                        // Build dynamic INSERT statement based on available columns
                        String insertSql = buildInsertStatement(formTableName, tableColumns);
                        PreparedStatement insertStmt = conn.prepareStatement(insertSql);

                        // Set parameters based on available columns
                        setInsertParameters(insertStmt, tableColumns, formId, formName, tableName, jsonContent, appDef);

                        int insertedRows = insertStmt.executeUpdate();
                        insertStmt.close();

                        LogUtil.info(CLASS_NAME, "Inserted " + insertedRows + " new form definition rows using columns: " + Arrays.toString(tableColumns));

                        // Force form cache invalidation after insert
                        invalidateFormCaches(appService, appDef, formId);
                    }

                    // Commit the transaction
                    conn.commit();

                    // CRITICAL: Flush Hibernate cache after JDBC insert
                    LogUtil.info(CLASS_NAME, "Synchronizing Hibernate cache after JDBC insert...");

                    // Method 1: Clear JPA/Hibernate L2 cache (entity-level)
                    try {
                        Object emFactory = AppUtil.getApplicationContext().getBean("entityManagerFactory");
                        if (emFactory != null) {
                            java.lang.reflect.Method getCacheMethod = emFactory.getClass().getMethod("getCache");
                            Object cache = getCacheMethod.invoke(emFactory);
                            java.lang.reflect.Method evictAllMethod = cache.getClass().getMethod("evictAll");
                            evictAllMethod.invoke(cache);
                            LogUtil.info(CLASS_NAME, "Flushed Hibernate L2 cache after JDBC insert");
                        }
                    } catch (Exception cacheEx) {
                        LogUtil.warn(CLASS_NAME, "L2 cache flush failed (continuing anyway): " + cacheEx.getMessage());
                    }

                    // Method 2: Clear FormDefinitionDao session cache (query-level)
                    try {
                        Object formDefDao = AppUtil.getApplicationContext().getBean(ApiConstants.BeanNames.FORM_DEFINITION_DAO);
                        if (formDefDao != null) {
                            java.lang.reflect.Method[] methods = formDefDao.getClass().getMethods();
                            for (java.lang.reflect.Method method : methods) {
                                if (method.getName().equals("evict") || method.getName().equals("clear")) {
                                    try {
                                        if (method.getParameterCount() == 0) {
                                            method.invoke(formDefDao);
                                            LogUtil.info(CLASS_NAME, "Cleared FormDefinitionDao cache via " + method.getName());
                                            break;
                                        }
                                    } catch (Exception e) {
                                        // Try next method
                                    }
                                }
                            }
                        }
                    } catch (Exception daoEx) {
                        LogUtil.warn(CLASS_NAME, "FormDefinitionDao cache clear failed (continuing): " + daoEx.getMessage());
                    }

                    // Method 3: Clear AppService caches (application-level)
                    try {
                        java.lang.reflect.Method[] methods = appService.getClass().getMethods();
                        for (java.lang.reflect.Method method : methods) {
                            String methodName = method.getName().toLowerCase();
                            if (methodName.contains("clear") && methodName.contains("cache")) {
                                try {
                                    if (method.getParameterCount() == 0) {
                                        method.invoke(appService);
                                        LogUtil.info(CLASS_NAME, "Called AppService cache clear: " + method.getName());
                                    } else if (method.getParameterCount() == 1) {
                                        method.invoke(appService, appDef.getAppId());
                                        LogUtil.info(CLASS_NAME, "Called AppService cache clear with appId: " + method.getName());
                                    }
                                } catch (Exception cacheEx) {
                                    // Continue to next method
                                }
                            }
                        }
                    } catch (Exception cacheEx) {
                        LogUtil.warn(CLASS_NAME, "AppService cache clear failed: " + cacheEx.getMessage());
                    }

                    LogUtil.info(CLASS_NAME, "Cache synchronization complete - Hibernate will now see JDBC changes");

                    // Force immediate table creation using official Joget pattern
                    forceTableCreation(formId, tableName, appDef, appService);

                    LogUtil.info(CLASS_NAME, "SUCCESS: Database registration completed with table creation");
                    return true;

                } catch (SQLException sqlEx) {
                    LogUtil.error(CLASS_NAME, sqlEx, "SQL error during form registration: " + sqlEx.getMessage());
                } finally {
                    if (conn != null) {
                        try {
                            conn.close();
                        } catch (SQLException closeEx) {
                            LogUtil.warn(CLASS_NAME, "Error closing connection: " + closeEx.getMessage());
                        }
                    }
                }
            } else {
                LogUtil.info(CLASS_NAME, "No DataSource available for direct database registration");
            }

            return false;

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Database direct registration failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Register a form directly to the database, returning a Result object for explicit error handling.
     */
    public InternalFormCreationResult registerFormDirectToDatabaseWithResult(AppService appService, AppDefinition appDef,
                                                                     String formId, String formName, String tableName,
                                                                     String jsonContent, Form formObject) {
        try {
            LogUtil.info(CLASS_NAME, "Starting form registration with Result pattern for: " + formId);

            boolean success = registerFormDirectToDatabase(appService, appDef, formId, formName, tableName, jsonContent, formObject);

            if (success) {
                LogUtil.info(CLASS_NAME, "Form registration succeeded: " + formId);
                return InternalFormCreationResult.success(formId);
            } else {
                LogUtil.error(CLASS_NAME, null, "Form registration failed for: " + formId);
                return InternalFormCreationResult.error(
                    InternalFormCreationResult.ErrorType.DATABASE_ERROR,
                    "Form registration returned false - check logs for details"
                );
            }

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Exception during form registration: " + e.getMessage());

            String errorType;
            String errorMessage = e.getMessage();

            if (e instanceof SQLException) {
                errorType = InternalFormCreationResult.ErrorType.DATABASE_ERROR;
            } else if (e instanceof java.io.IOException) {
                errorType = InternalFormCreationResult.ErrorType.FILESYSTEM_ERROR;
            } else if (errorMessage != null && errorMessage.toLowerCase().contains("json")) {
                errorType = InternalFormCreationResult.ErrorType.JSON_INVALID;
            } else if (errorMessage != null && errorMessage.toLowerCase().contains("cache")) {
                errorType = InternalFormCreationResult.ErrorType.CACHE_SYNC_ERROR;
            } else if (errorMessage != null && errorMessage.toLowerCase().contains("table")) {
                errorType = InternalFormCreationResult.ErrorType.TABLE_CREATION_ERROR;
            } else {
                errorType = InternalFormCreationResult.ErrorType.VALIDATION_ERROR;
            }

            return InternalFormCreationResult.error(errorType, errorMessage != null ? errorMessage : "Unknown error occurred");
        }
    }

    /**
     * Discover the correct form definition table name in the database
     */
    public String discoverFormDefinitionTableName(Connection conn) {
        try {
            LogUtil.info(CLASS_NAME, "Discovering form definition table name");

            String[] possibleTableNames = {
                "app_fd_form",
                ApiConstants.TableNames.APP_FORM,
                "formdefinition",
                "form_definition",
                "wf_form_definition",
                "app_form_definition",
                "dir_form"
            };

            DatabaseMetaData metaData = conn.getMetaData();

            for (String tableName : possibleTableNames) {
                try {
                    ResultSet tables = metaData.getTables(null, null, tableName, new String[]{"TABLE"});
                    if (tables.next()) {
                        tables.close();
                        LogUtil.info(CLASS_NAME, "Found exact match for form table: " + tableName);
                        return tableName;
                    }
                    tables.close();
                } catch (Exception e) {
                    // Continue to next table name
                }
            }

            // If no exact match, search for tables containing "form"
            try {
                ResultSet allTables = metaData.getTables(null, null, "%", new String[]{"TABLE"});
                while (allTables.next()) {
                    String tableName = allTables.getString("TABLE_NAME");
                    if (tableName.toLowerCase().contains("form") &&
                        (tableName.toLowerCase().contains("def") || tableName.toLowerCase().contains("app"))) {

                        if (hasFormDefinitionColumns(conn, tableName)) {
                            allTables.close();
                            LogUtil.info(CLASS_NAME, "Found form definition table by pattern: " + tableName);
                            return tableName;
                        }
                    }
                }
                allTables.close();
            } catch (Exception e) {
                LogUtil.info(CLASS_NAME, "Error searching for form tables: " + e.getMessage());
            }

            LogUtil.info(CLASS_NAME, "No form definition table found");
            return null;

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error discovering form definition table: " + e.getMessage());
            return null;
        }
    }

    /**
     * Check if a table has columns that suggest it's a form definition table
     */
    public boolean hasFormDefinitionColumns(Connection conn, String tableName) {
        try {
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet columns = metaData.getColumns(null, null, tableName, null);

            boolean hasId = false;
            boolean hasJson = false;
            boolean hasAppId = false;

            while (columns.next()) {
                String columnName = columns.getString("COLUMN_NAME").toLowerCase();
                if (columnName.equals(ApiConstants.PropertyKeys.ID) || columnName.equals("formid")) {
                    hasId = true;
                } else if (columnName.equals(ApiConstants.ColumnNames.JSON)) {
                    hasJson = true;
                } else if (columnName.equals(ApiConstants.ColumnNames.APP_ID.toLowerCase()) || columnName.equals("app_id")) {
                    hasAppId = true;
                }
            }
            columns.close();

            return hasId && (hasJson || hasAppId);

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get the column names for the form definition table
     */
    public String[] getFormDefinitionColumns(Connection conn, String tableName) {
        try {
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet columns = metaData.getColumns(null, null, tableName, null);

            List<String> columnList = new ArrayList<>();
            while (columns.next()) {
                String columnName = columns.getString("COLUMN_NAME");
                columnList.add(columnName.toLowerCase());
            }
            columns.close();

            LogUtil.info(CLASS_NAME, "Form table " + tableName + " has columns: " + columnList);
            return columnList.toArray(new String[0]);

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error getting table columns: " + e.getMessage());
            return new String[]{ApiConstants.PropertyKeys.ID, ApiConstants.PropertyKeys.NAME, ApiConstants.ColumnNames.JSON,
                               ApiConstants.ColumnNames.APP_ID, ApiConstants.ColumnNames.APP_VERSION,
                               ApiConstants.ColumnNames.DATE_CREATED, ApiConstants.ColumnNames.DATE_MODIFIED};
        }
    }

    /**
     * Find a column by checking multiple possible names
     */
    public String findColumn(String[] availableColumns, String[] possibleNames) {
        for (String possibleName : possibleNames) {
            for (String availableColumn : availableColumns) {
                if (availableColumn.equalsIgnoreCase(possibleName)) {
                    return availableColumn;
                }
            }
        }
        return null;
    }

    /**
     * Build dynamic INSERT statement based on available columns
     */
    public String buildInsertStatement(String tableName, String[] columns) {
        List<String> validColumns = new ArrayList<>();
        List<String> placeholders = new ArrayList<>();

        for (String column : columns) {
            String lcColumn = column.toLowerCase();
            if (lcColumn.equals("id") || lcColumn.equals("formid") ||
                lcColumn.equals("name") || lcColumn.equals("tablename") ||
                lcColumn.equals("json") || lcColumn.equals("appid") ||
                lcColumn.equals("appversion") || lcColumn.equals("datecreated") ||
                lcColumn.equals("datemodified")) {
                validColumns.add(column);
                placeholders.add("?");
            }
        }

        String sql = "INSERT INTO " + tableName + " (" +
                    String.join(", ", validColumns) + ") VALUES (" +
                    String.join(", ", placeholders) + ")";

        LogUtil.info(CLASS_NAME, "Built INSERT statement: " + sql);
        return sql;
    }

    /**
     * Set parameters for the INSERT statement based on available columns
     */
    public void setInsertParameters(PreparedStatement stmt, String[] columns,
                                   String formId, String formName, String tableName,
                                   String jsonContent, AppDefinition appDef) throws SQLException {
        int paramIndex = 1;
        Timestamp now = new Timestamp(System.currentTimeMillis());

        for (String column : columns) {
            String lcColumn = column.toLowerCase();
            if (lcColumn.equals("id") || lcColumn.equals("formid")) {
                stmt.setString(paramIndex++, formId);
            } else if (lcColumn.equals("name")) {
                stmt.setString(paramIndex++, formName != null ? formName : formId);
            } else if (lcColumn.equals("tablename")) {
                stmt.setString(paramIndex++, tableName != null ? tableName : formId);
            } else if (lcColumn.equals("json")) {
                stmt.setString(paramIndex++, jsonContent);
            } else if (lcColumn.equals("appid")) {
                stmt.setString(paramIndex++, appDef.getAppId());
            } else if (lcColumn.equals("appversion")) {
                stmt.setString(paramIndex++, appDef.getVersion().toString());
            } else if (lcColumn.equals("datecreated")) {
                stmt.setTimestamp(paramIndex++, now);
            } else if (lcColumn.equals("datemodified")) {
                stmt.setTimestamp(paramIndex++, now);
            }
        }

        LogUtil.info(CLASS_NAME, "Set " + (paramIndex - 1) + " parameters for form insertion");
    }

    /**
     * Force immediate table creation for a form using the official Joget pattern
     */
    public void forceTableCreation(String formId, String tableName, AppDefinition appDef, AppService appService) {
        try {
            LogUtil.info(CLASS_NAME, "Forcing table creation for form: " + formId + " (table: " + tableName + ")");

            FormDataDao formDataDao = (FormDataDao) AppUtil.getApplicationContext().getBean(ApiConstants.BeanNames.FORM_DATA_DAO);

            if (formDataDao != null) {
                // Clear form table cache to ensure fresh schema detection
                try {
                    java.lang.reflect.Method clearCacheMethod = formDataDao.getClass().getMethod("clearFormTableCache", String.class);
                    clearCacheMethod.invoke(formDataDao, formId);
                    LogUtil.info(CLASS_NAME, "Cleared form table cache for: " + formId);
                } catch (Exception cacheEx) {
                    LogUtil.info(CLASS_NAME, "Could not clear cache (non-critical): " + cacheEx.getMessage());
                }

                // Execute dummy load to trigger table creation
                String dummyKey = "xyz123";
                try {
                    formDataDao.loadWithoutTransaction(formId, tableName, dummyKey);
                    LogUtil.info(CLASS_NAME, "Table creation triggered via loadWithoutTransaction");
                } catch (Exception loadEx) {
                    String errorMsg = loadEx.getMessage();
                    if (errorMsg != null && errorMsg.toLowerCase().contains("table") &&
                        errorMsg.toLowerCase().contains("doesn't exist")) {
                        LogUtil.warn(CLASS_NAME, "Table not created by loadWithoutTransaction: " + errorMsg);
                    } else {
                        LogUtil.error(CLASS_NAME, loadEx, "Unexpected error during table creation: " + errorMsg);
                    }
                }

                // Verify table was actually created in database
                try {
                    String actualTable = appService.getFormTableName(appDef, formId);
                    boolean verifiedViaAppService = (actualTable != null && !actualTable.isEmpty());

                    boolean verifiedViaDatabase = false;
                    try {
                        Object dataSource = AppUtil.getApplicationContext().getBean("setupDataSource");
                        java.lang.reflect.Method getConnMethod = dataSource.getClass().getMethod("getConnection");
                        Connection verifyConn = (Connection) getConnMethod.invoke(dataSource);

                        String expectedTableName = "app_fd_" + tableName;
                        DatabaseMetaData metaData = verifyConn.getMetaData();
                        ResultSet tables = metaData.getTables(null, null, expectedTableName, new String[]{"TABLE"});
                        verifiedViaDatabase = tables.next();
                        tables.close();
                        verifyConn.close();

                        if (verifiedViaDatabase) {
                            LogUtil.info(CLASS_NAME, "SUCCESS: Table exists in database: " + expectedTableName);
                        } else {
                            LogUtil.error(CLASS_NAME, null, "FAILURE: Table does NOT exist in database: " + expectedTableName);
                        }
                    } catch (Exception dbEx) {
                        LogUtil.warn(CLASS_NAME, "Database verification failed: " + dbEx.getMessage());
                    }

                    if (verifiedViaAppService && verifiedViaDatabase) {
                        LogUtil.info(CLASS_NAME, "SUCCESS: Table verified via both AppService and direct DB check");
                    } else if (!verifiedViaDatabase) {
                        LogUtil.error(CLASS_NAME, null, "CRITICAL: Table creation FAILED - table does not exist in database");
                        return;
                    }

                } catch (Exception verifyEx) {
                    LogUtil.error(CLASS_NAME, verifyEx, "Table verification error: " + verifyEx.getMessage());
                }
            } else {
                LogUtil.error(CLASS_NAME, null, "FormDataDao bean not available - cannot force table creation");
            }

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Table creation failed: " + e.getMessage());
        }
    }

    /**
     * Invalidate all form-related caches to ensure immediate visibility of changes
     */
    private void invalidateFormCaches(AppService appService, AppDefinition appDef, String formId) {
        try {
            LogUtil.info(CLASS_NAME, "Invalidating form caches for immediate visibility");

            // 1. Clear AppService caches
            java.lang.reflect.Method[] appServiceMethods = appService.getClass().getMethods();
            for (java.lang.reflect.Method method : appServiceMethods) {
                String methodName = method.getName().toLowerCase();
                if ((methodName.contains("clear") || methodName.contains("flush") ||
                     methodName.contains("refresh") || methodName.contains("reload")) &&
                    (methodName.contains("cache") || methodName.contains("form"))) {

                    try {
                        if (method.getParameterCount() == 0) {
                            method.invoke(appService);
                            LogUtil.info(CLASS_NAME, "Called cache method: " + method.getName());
                        } else if (method.getParameterCount() == 1) {
                            method.invoke(appService, appDef.getAppId());
                            LogUtil.info(CLASS_NAME, "Called cache method with appId: " + method.getName());
                        } else if (method.getParameterCount() == 2) {
                            method.invoke(appService, appDef.getAppId(), appDef.getVersion().toString());
                            LogUtil.info(CLASS_NAME, "Called cache method with app/version: " + method.getName());
                        }
                    } catch (Exception e) {
                        LogUtil.info(CLASS_NAME, "Cache method failed: " + method.getName() + " - " + e.getMessage());
                    }
                }
            }

            // 2. Clear FormService caches
            try {
                FormService formService = (FormService) AppUtil.getApplicationContext().getBean(ApiConstants.BeanNames.FORM_SERVICE);
                if (formService != null) {
                    java.lang.reflect.Method[] formServiceMethods = formService.getClass().getMethods();
                    for (java.lang.reflect.Method method : formServiceMethods) {
                        String methodName = method.getName().toLowerCase();
                        if ((methodName.contains("clear") || methodName.contains("flush") ||
                             methodName.contains("refresh")) && methodName.contains("cache")) {

                            try {
                                if (method.getParameterCount() == 0) {
                                    method.invoke(formService);
                                    LogUtil.info(CLASS_NAME, "Called FormService cache method: " + method.getName());
                                }
                            } catch (Exception e) {
                                LogUtil.info(CLASS_NAME, "FormService cache method failed: " + method.getName());
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LogUtil.info(CLASS_NAME, "Could not access FormService for cache clearing: " + e.getMessage());
            }

            // 3. Force table structure refresh by querying the form
            try {
                String tableNameResult = appService.getFormTableName(appDef, formId);
                LogUtil.info(CLASS_NAME, "Form table name after cache clear: " + tableNameResult);

                FormRowSet testRowSet = appService.loadFormData(appDef.getAppId(), appDef.getVersion().toString(), formId, "");
                LogUtil.info(CLASS_NAME, "Form data load test successful, triggering structure refresh");

            } catch (Exception e) {
                LogUtil.info(CLASS_NAME, "Form structure refresh trigger failed: " + e.getMessage());
            }

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Cache invalidation failed: " + e.getMessage());
        }
    }
}
