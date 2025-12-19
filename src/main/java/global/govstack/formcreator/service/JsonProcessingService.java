package global.govstack.formcreator.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.joget.apps.form.model.Form;
import org.joget.apps.form.service.FormService;
import org.joget.commons.util.LogUtil;

import java.util.*;

/**
 * Service for JSON processing operations including parsing, validation, and generation
 */
public class JsonProcessingService {

    private static final String CLASS_NAME = JsonProcessingService.class.getName();

    /**
     * Validate if a string is valid JSON
     */
    public boolean isValidJson(String content) {
        try {
            // Simple JSON validation - try to parse it
            content = content.trim();
            return (content.startsWith("{") && content.endsWith("}")) ||
                   (content.startsWith("[") && content.endsWith("]"));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Parse and validate form JSON using Joget's FormService
     */
    public Form parseAndValidateFormJson(String jsonContent, FormService formService) {
        try {
            LogUtil.info(CLASS_NAME, "Parsing form JSON content");

            // Remove any BOM or whitespace issues
            jsonContent = jsonContent.trim();
            if (jsonContent.startsWith("\uFEFF")) {
                jsonContent = jsonContent.substring(1);
            }

            // Use Joget's FormService to create a Form object from JSON
            Form form = (Form) formService.createElementFromJson(jsonContent);

            if (form != null) {
                LogUtil.info(CLASS_NAME, "JSON parsed successfully into Form object");
                LogUtil.info(CLASS_NAME, "Form properties: ID=" + form.getPropertyString("id") +
                           ", Name=" + form.getPropertyString("name") +
                           ", Table=" + form.getPropertyString("tableName"));
                return form;
            } else {
                LogUtil.info(CLASS_NAME, "ERROR: FormService.createElementFromJson returned null");
            }

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error parsing form JSON: " + e.getMessage());
        }

        return null;
    }

    /**
     * Generate API definition JSON for a form
     */
    public String generateApiDefinitionJson(String formId, String apiName, String apiUuid) {
        try {
            // Generate unique element ID
            String elementId = UUID.randomUUID().toString().toUpperCase();

            // Build JSON structure
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("    \"elements\": [{\n");
            json.append("        \"className\": \"org.joget.api.lib.AppFormAPI\",\n");
            json.append("        \"properties\": {\n");
            json.append("            \"formDefId\": \"").append(formId).append("\",\n");
            json.append("            \"ignorePermission\": \"\",\n");
            json.append("            \"id\": \"").append(elementId).append("\",\n");
            json.append("            \"label\": \"\",\n");
            json.append("            \"ENABLED_PATHS\": \"post:/;get:/{recordId};put:/;delete:/{recordId};post:/saveOrUpdate;post:/updateWithFiles;post:/addWithFiles;get:/list\"\n");
            json.append("        }\n");
            json.append("    }],\n");
            json.append("    \"properties\": {\n");
            json.append("        \"name\": \"").append(apiName).append("\",\n");
            json.append("        \"description\": \"Auto-generated API for form: ").append(formId).append("\",\n");
            json.append("        \"id\": \"API-").append(apiUuid).append("\"\n");
            json.append("    }\n");
            json.append("}\n");

            return json.toString();

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error generating API JSON: " + e.getMessage());
            return null;
        }
    }

    /**
     * Generate datalist definition JSON for a form
     */
    public String generateDatalistDefinitionJson(String formId, String datalistName, String datalistId, String formJson) {
        try {
            // Extract form fields to generate columns
            // Note: This extracts only user-defined fields (max 6), excluding system columns
            List<Map<String, String>> columns = extractFormFieldsForDatalist(formJson);

            // Build JSON structure
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("    \"useSession\": \"false\",\n");
            json.append("    \"showPageSizeSelector\": \"true\",\n");
            json.append("    \"rowActions\": [],\n");
            json.append("    \"columns\": [\n");

            // Add columns (if empty, Joget will use default columns)
            for (int i = 0; i < columns.size(); i++) {
                Map<String, String> field = columns.get(i);
                json.append("        {\n");
                json.append("            \"name\": \"").append(field.get("name")).append("\",\n");
                json.append("            \"id\": \"column_").append(i).append("\",\n");
                json.append("            \"label\": \"").append(field.get("label")).append("\"\n");
                json.append("        }");
                if (i < columns.size() - 1) {
                    json.append(",");
                }
                json.append("\n");
            }

            json.append("    ],\n");
            json.append("    \"pageSize\": 0,\n");
            json.append("    \"orderBy\": \"\",\n");
            json.append("    \"filters\": [],\n");
            json.append("    \"pageSizeSelectorOptions\": \"10,20,30,40,50,100\",\n");
            json.append("    \"buttonPosition\": \"bothLeft\",\n");
            json.append("    \"checkboxPosition\": \"left\",\n");
            json.append("    \"name\": \"").append(datalistName).append("\",\n");
            json.append("    \"id\": \"").append(datalistId).append("\",\n");
            json.append("    \"binder\": {\n");
            json.append("        \"className\": \"org.joget.plugin.enterprise.AdvancedFormRowDataListBinder\",\n");
            json.append("        \"properties\": {\"formDefId\": \"").append(formId).append("\"}\n");
            json.append("    },\n");
            json.append("    \"actions\": [],\n");
            json.append("    \"order\": \"\"\n");
            json.append("}\n");

            return json.toString();

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error generating datalist JSON: " + e.getMessage());
            return null;
        }
    }

    /**
     * Generate userview definition JSON with CRUD menu for a form
     */
    public String generateUserviewDefinitionJson(String formId, String datalistId, String userviewName, String userviewId) {
        try {
            // Generate unique IDs
            String categoryId = "category-" + UUID.randomUUID().toString();
            String menuId = UUID.randomUUID().toString();
            String welcomePageId = UUID.randomUUID().toString();
            String homeCategory = "category-" + UUID.randomUUID().toString();

            // Build JSON structure
            StringBuilder json = new StringBuilder();
            json.append("{\n");
            json.append("    \"className\": \"org.joget.apps.userview.model.Userview\",\n");
            json.append("    \"categories\": [\n");

            // Home category with welcome page
            json.append("        {\n");
            json.append("            \"className\": \"org.joget.apps.userview.model.UserviewCategory\",\n");
            json.append("            \"menus\": [{\n");
            json.append("                \"className\": \"org.joget.apps.userview.lib.HtmlPage\",\n");
            json.append("                \"properties\": {\n");
            json.append("                    \"id\": \"").append(welcomePageId).append("\",\n");
            json.append("                    \"label\": \"Welcome\",\n");
            json.append("                    \"customId\": \"welcome\",\n");
            json.append("                    \"content\": \"<h3>Welcome to ").append(escapeJson(userviewName)).append("</h3>\"\n");
            json.append("                }\n");
            json.append("            }],\n");
            json.append("            \"properties\": {\n");
            json.append("                \"id\": \"").append(homeCategory).append("\",\n");
            json.append("                \"label\": \"<i class='fa fa-home'></i> Home\"\n");
            json.append("            }\n");
            json.append("        },\n");

            // CRUD category
            json.append("        {\n");
            json.append("            \"className\": \"org.joget.apps.userview.model.UserviewCategory\",\n");
            json.append("            \"menus\": [{\n");
            json.append("                \"className\": \"org.joget.plugin.enterprise.CrudMenu\",\n");
            json.append("                \"properties\": {\n");
            json.append("                    \"datalistId\": \"").append(datalistId).append("\",\n");
            json.append("                    \"addFormId\": \"").append(formId).append("\",\n");
            json.append("                    \"editFormId\": \"").append(formId).append("\",\n");
            json.append("                    \"id\": \"").append(menuId).append("\",\n");
            json.append("                    \"customId\": \"").append(formId).append("_crud\",\n");
            json.append("                    \"label\": \"").append(escapeJson(userviewName)).append("\",\n");
            json.append("                    \"list-showDeleteButton\": \"yes\",\n");
            json.append("                    \"add-afterSaved\": \"list\",\n");
            json.append("                    \"edit-afterSaved\": \"list\",\n");
            json.append("                    \"buttonPosition\": \"bothLeft\",\n");
            json.append("                    \"checkboxPosition\": \"left\",\n");
            json.append("                    \"selectionType\": \"multiple\",\n");
            json.append("                    \"rowCount\": \"true\"\n");
            json.append("                }\n");
            json.append("            }],\n");
            json.append("            \"properties\": {\n");
            json.append("                \"id\": \"").append(categoryId).append("\",\n");
            json.append("                \"label\": \"<i class='fa fa-list'></i> Manage\"\n");
            json.append("            }\n");
            json.append("        }\n");
            json.append("    ],\n");
            json.append("    \"properties\": {\n");
            json.append("        \"id\": \"").append(userviewId).append("\",\n");
            json.append("        \"name\": \"").append(escapeJson(userviewName)).append("\",\n");
            json.append("        \"description\": \"Auto-generated userview for ").append(escapeJson(formId)).append("\",\n");
            json.append("        \"welcomeMessage\": \"#date.EEE, d MMM yyyy#\",\n");
            json.append("        \"logoutText\": \"Logout\",\n");
            json.append("        \"footerMessage\": \"Powered by Joget\"\n");
            json.append("    },\n");
            json.append("    \"setting\": {\n");
            json.append("        \"properties\": {\n");
            json.append("            \"userviewId\": \"").append(userviewId).append("\",\n");
            json.append("            \"userviewName\": \"").append(escapeJson(userviewName)).append("\",\n");
            json.append("            \"theme\": {\n");
            json.append("                \"className\": \"org.joget.apps.userview.lib.DefaultTheme\",\n");
            json.append("                \"properties\": {}\n");
            json.append("            },\n");
            json.append("            \"permission\": {\n");
            json.append("                \"className\": \"org.joget.apps.userview.lib.LoggedInUserPermission\",\n");
            json.append("                \"properties\": {}\n");
            json.append("            }\n");
            json.append("        }\n");
            json.append("    }\n");
            json.append("}\n");

            return json.toString();

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error generating userview JSON: " + e.getMessage());
            return null;
        }
    }

    /**
     * Generate JSON for a single category with CRUD menu
     */
    public String generateCategoryJson(String formId, String datalistId, String formLabel) {
        String categoryId = "category-" + UUID.randomUUID().toString();
        String menuId = UUID.randomUUID().toString();
        String customId = formId + "_crud";

        StringBuilder json = new StringBuilder();
        json.append("        {\n");
        json.append("            \"className\": \"org.joget.apps.userview.model.UserviewCategory\",\n");
        json.append("            \"menus\": [{\n");
        json.append("                \"className\": \"org.joget.plugin.enterprise.CrudMenu\",\n");
        json.append("                \"properties\": {\n");
        json.append("                    \"list-customFooter\": \"\",\n");
        json.append("                    \"add-afterSavedRedirectUrl\": \"\",\n");
        json.append("                    \"editFormId\": \"").append(formId).append("\",\n");
        json.append("                    \"cacheAllLinks\": \"\",\n");
        json.append("                    \"edit-saveButtonLabel\": \"\",\n");
        json.append("                    \"list-showDeleteButton\": \"yes\",\n");
        json.append("                    \"list-newButtonLabel\": \"\",\n");
        json.append("                    \"add-afterSavedRedirectParamName\": \"\",\n");
        json.append("                    \"list-deleteSubformData\": \"\",\n");
        json.append("                    \"enableOffline\": \"\",\n");
        json.append("                    \"selectionType\": \"multiple\",\n");
        json.append("                    \"addFormId\": \"").append(formId).append("\",\n");
        json.append("                    \"id\": \"").append(menuId).append("\",\n");
        json.append("                    \"iconIncluded\": false,\n");
        json.append("                    \"add-messageShowAfterComplete\": \"\",\n");
        json.append("                    \"edit-readonlyLabel\": \"\",\n");
        json.append("                    \"list-editLinkLabel\": \"\",\n");
        json.append("                    \"add-cancelButtonLabel\": \"\",\n");
        json.append("                    \"list-deleteFiles\": \"\",\n");
        json.append("                    \"add-customHeader\": \"\",\n");
        json.append("                    \"edit-readonly\": \"\",\n");
        json.append("                    \"datalistId\": \"").append(datalistId).append("\",\n");
        json.append("                    \"edit-nextButtonLabel\": \"\",\n");
        json.append("                    \"list-confirmation\": \"\",\n");
        json.append("                    \"userviewCacheDuration\": \"\",\n");
        json.append("                    \"add-afterSaved\": \"list\",\n");
        json.append("                    \"add-afterSavedRedirectParamvalue\": \"\",\n");
        json.append("                    \"list-customHeader\": \"\",\n");
        json.append("                    \"edit-customHeader\": \"\",\n");
        json.append("                    \"edit-afterSavedRedirectParamName\": \"\",\n");
        json.append("                    \"list-abortRelatedRunningProcesses\": \"\",\n");
        json.append("                    \"edit-afterSavedRedirectUrl\": \"\",\n");
        json.append("                    \"edit-prevButtonLabel\": \"\",\n");
        json.append("                    \"customId\": \"").append(customId).append("\",\n");
        json.append("                    \"edit-afterSaved\": \"list\",\n");
        json.append("                    \"list-deleteButtonLabel\": \"\",\n");
        json.append("                    \"checkboxPosition\": \"left\",\n");
        json.append("                    \"add-customFooter\": \"\",\n");
        json.append("                    \"list-deleteGridData\": \"\",\n");
        json.append("                    \"edit-allowRecordTraveling\": \"\",\n");
        json.append("                    \"rowCount\": \"true\",\n");
        json.append("                    \"edit-afterSavedRedirectParamvalue\": \"\",\n");
        json.append("                    \"edit-customFooter\": \"\",\n");
        json.append("                    \"keyName\": \"\",\n");
        json.append("                    \"label\": \"").append(escapeJson(formLabel)).append("\",\n");
        json.append("                    \"list-newLinkTarget\": \"\",\n");
        json.append("                    \"edit-lastButtonLabel\": \"\",\n");
        json.append("                    \"buttonPosition\": \"bothLeft\",\n");
        json.append("                    \"edit-firstButtonLabel\": \"\",\n");
        json.append("                    \"add-saveButtonLabel\": \"\",\n");
        json.append("                    \"edit-messageShowAfterComplete\": \"\",\n");
        json.append("                    \"cacheListAction\": \"\",\n");
        json.append("                    \"userviewCacheScope\": \"\",\n");
        json.append("                    \"edit-moreActions\": [],\n");
        json.append("                    \"list-moreActions\": [],\n");
        json.append("                    \"edit-backButtonLabel\": \"\",\n");
        json.append("                    \"list-editLinkTarget\": \"\"\n");
        json.append("                }\n");
        json.append("            }],\n");
        json.append("            \"properties\": {\n");
        json.append("                \"id\": \"").append(categoryId).append("\",\n");
        json.append("                \"label\": \"<i class='fa fa-tasks'></i> ").append(escapeJson(formLabel)).append("\"\n");
        json.append("            }\n");
        json.append("        }");

        return json.toString();
    }

    /**
     * Find matching closing bracket for an opening bracket
     */
    public int findMatchingBracket(String json, int openingBracketPos) {
        int depth = 0;
        boolean inString = false;
        boolean escapeNext = false;

        for (int i = openingBracketPos; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escapeNext) {
                escapeNext = false;
                continue;
            }

            if (c == '\\') {
                escapeNext = true;
                continue;
            }

            if (c == '"') {
                inString = !inString;
                continue;
            }

            if (!inString) {
                if (c == '[') {
                    depth++;
                } else if (c == ']') {
                    depth--;
                    if (depth == 0) {
                        return i;
                    }
                }
            }
        }

        return -1; // Not found
    }

    /**
     * Escape JSON special characters
     */
    public String escapeJson(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }

    /**
     * Extract form fields from form JSON to generate datalist columns.
     * Uses proper JSON parsing (Gson) instead of regex for robust handling of nested objects.
     */
    public List<Map<String, String>> extractFormFieldsForDatalist(String formJson) {
        List<Map<String, String>> fields = new ArrayList<>();

        try {
            // System columns to exclude from datalist (Joget internal fields)
            Set<String> systemColumns = new HashSet<>(java.util.Arrays.asList(
                "id", "dateCreated", "dateModified",
                "createdBy", "createdByName",
                "modifiedBy", "modifiedByName"
            ));

            // Maximum number of columns to show in datalist
            final int MAX_COLUMNS = 6;

            // Parse JSON using Gson (architecturally correct: JSON parser for JSON, not regex)
            JsonParser parser = new JsonParser();
            JsonElement root = parser.parse(formJson);

            // Recursively traverse the JSON tree to find form fields
            extractFieldsFromElement(root, fields, systemColumns, MAX_COLUMNS);

            // Log results
            if (fields.isEmpty()) {
                LogUtil.warn(CLASS_NAME, "No user-defined fields extracted from form JSON");
                LogUtil.warn(CLASS_NAME, "Datalist will be created with no columns - Joget will use default columns");
            } else {
                LogUtil.info(CLASS_NAME, "Successfully extracted " + fields.size() + " user-defined fields for datalist columns");
            }

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error extracting fields from form JSON: " + e.getMessage());
            LogUtil.warn(CLASS_NAME, "Datalist will be created with no columns - Joget will use default columns");
        }

        return fields;
    }

    /**
     * Recursively traverse JSON tree to find form field elements.
     */
    private void extractFieldsFromElement(JsonElement element,
                                         List<Map<String, String>> fields,
                                         Set<String> systemColumns,
                                         int maxColumns) {

        // Stop when we have enough columns
        if (fields.size() >= maxColumns) {
            return;
        }

        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();

            // Check if this is a form field element
            if (obj.has("className") && obj.has("properties")) {
                String className = obj.get("className").getAsString();

                // Is it a form field? (not Section, Column, Form, etc.)
                if (className.contains("org.joget.apps.form.lib.")) {
                    JsonObject props = obj.getAsJsonObject("properties");

                    if (props.has("id")) {
                        String fieldId = props.get("id").getAsString();

                        // Filter out system columns and layout elements
                        if (fieldId != null && !fieldId.isEmpty() &&
                            !fieldId.startsWith("section") &&
                            !fieldId.startsWith("column") &&
                            !systemColumns.contains(fieldId)) {

                            String fieldLabel = props.has("label") ?
                                props.get("label").getAsString() : fieldId;

                            Map<String, String> field = new HashMap<>();
                            field.put("name", fieldId);
                            field.put("label", fieldLabel);
                            fields.add(field);

                            LogUtil.info(CLASS_NAME, "Extracted field " + fields.size() +
                                ": id=" + fieldId + ", label=" + fieldLabel);
                        }
                    }
                }
            }

            // Recursively process all properties in this object
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                extractFieldsFromElement(entry.getValue(), fields, systemColumns, maxColumns);
            }

        } else if (element.isJsonArray()) {
            // Recursively process array elements
            JsonArray array = element.getAsJsonArray();
            for (JsonElement item : array) {
                extractFieldsFromElement(item, fields, systemColumns, maxColumns);
            }
        }
        // Primitives (strings, numbers, etc.) are ignored
    }

    /**
     * Extract a JSON value for a given key
     */
    public String extractJsonValue(String jsonBlock, String key) {
        try {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "\"" + key + "\":\\s*\"([^\"]*)\""
            );
            java.util.regex.Matcher matcher = pattern.matcher(jsonBlock);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }
}
