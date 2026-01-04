package com.piplineData.loadService.layer2.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service để render SQL template với runtime parameters
 * Hỗ trợ cú pháp {{param_name}}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateRenderService {

    private static final Pattern PARAM_PATTERN = Pattern.compile("\\{\\{\\s*(\\w+)\\s*\\}\\}");

    /**
     * Render SQL template với params
     * 
     * @param template SQL template với {{param_name}}
     * @param params Map of parameter values
     * @return Rendered SQL
     */
    public String render(String template, Map<String, Object> params) {
        if (template == null || template.trim().isEmpty()) {
            return template;
        }

        if (params == null) {
            params = new HashMap<>();
        }

        log.debug("Rendering template with {} parameters", params.size());
        
        StringBuffer result = new StringBuffer();
        Matcher matcher = PARAM_PATTERN.matcher(template);

        while (matcher.find()) {
            String paramName = matcher.group(1);
            Object value = params.get(paramName);

            if (value == null) {
                log.warn("Parameter '{}' not found in params, keeping placeholder", paramName);
                matcher.appendReplacement(result, Matcher.quoteReplacement("{{" + paramName + "}}"));
            } else {
                String replacement = formatValue(value);
                matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
            }
        }
        matcher.appendTail(result);

        String rendered = result.toString();
        log.debug("Template rendered successfully");
        return rendered;
    }

    /**
     * Format value based on type for SQL
     */
    private String formatValue(Object value) {
        if (value == null) {
            return "NULL";
        }

        if (value instanceof String) {
            // Escape single quotes in strings
            String strValue = ((String) value).replace("'", "''");
            return "'" + strValue + "'";
        }

        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }

        // Default: convert to string and quote
        String strValue = value.toString().replace("'", "''");
        return "'" + strValue + "'";
    }

    /**
     * Extract all parameter names from template
     */
    public java.util.List<String> extractParamNames(String template) {
        java.util.List<String> paramNames = new java.util.ArrayList<>();
        
        if (template == null || template.trim().isEmpty()) {
            return paramNames;
        }

        Matcher matcher = PARAM_PATTERN.matcher(template);
        while (matcher.find()) {
            paramNames.add(matcher.group(1));
        }

        return paramNames;
    }

    /**
     * Validate that all required params are provided
     */
    public boolean validateParams(String template, Map<String, Object> params) {
        java.util.List<String> requiredParams = extractParamNames(template);
        
        if (params == null) {
            return requiredParams.isEmpty();
        }

        for (String paramName : requiredParams) {
            if (!params.containsKey(paramName)) {
                log.error("Missing required parameter: {}", paramName);
                return false;
            }
        }

        return true;
    }
}
