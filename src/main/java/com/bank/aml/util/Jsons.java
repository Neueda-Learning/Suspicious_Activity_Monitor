package com.bank.aml.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class Jsons {
    public static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private Jsons() {}

    public static ObjectNode obj() { return MAPPER.createObjectNode(); }
    public static ArrayNode arr() { return MAPPER.createArrayNode(); }
    public static JsonNode emptyObj() { return obj(); }
    public static JsonNode emptyArr() { return arr(); }

    public static JsonNode toTree(Object value) {
        return MAPPER.valueToTree(value);
    }

    public static String pretty(Object value) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}
