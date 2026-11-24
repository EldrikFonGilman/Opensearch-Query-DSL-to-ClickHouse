package com.example.querytranslator.config;

import com.example.querytranslator.schema.FieldType;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties("query-translator")
public class QueryTranslatorProperties {
    private Map<String, TableProperties> tables = new LinkedHashMap<>();
    private Limits limits = new Limits();

    public Map<String, TableProperties> getTables() { return tables; }
    public void setTables(Map<String, TableProperties> tables) { this.tables = tables; }
    public Limits getLimits() { return limits; }
    public void setLimits(Limits limits) { this.limits = limits; }

    public static class TableProperties {
        private Map<String, FieldType> fields = new LinkedHashMap<>();
        public Map<String, FieldType> getFields() { return fields; }
        public void setFields(Map<String, FieldType> fields) { this.fields = fields; }
    }

    public static class Limits {
        private int requestSizeBytes = 1_048_576;
        private int nestingDepth = 10;
        private int termsValues = 1_000;
        private int wildcardLength = 1_024;

        public int getRequestSizeBytes() { return requestSizeBytes; }
        public void setRequestSizeBytes(int value) { requestSizeBytes = value; }
        public int getNestingDepth() { return nestingDepth; }
        public void setNestingDepth(int value) { nestingDepth = value; }
        public int getTermsValues() { return termsValues; }
        public void setTermsValues(int value) { termsValues = value; }
        public int getWildcardLength() { return wildcardLength; }
        public void setWildcardLength(int value) { wildcardLength = value; }
    }
}
