package com.dpw.specshield.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
public class JsonUtils {
    private JsonUtils() {
        // Private constructor to prevent instantiation
    }
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        OBJECT_MAPPER.enable(SerializationFeature.INDENT_OUTPUT);
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        OBJECT_MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

        StreamReadConstraints streamReadConstraints = StreamReadConstraints.builder()
                .maxStringLength(104857600)     // 100MB for string fields
                .build();

        OBJECT_MAPPER.getFactory().setStreamReadConstraints(streamReadConstraints);
    }
    
    public static Map<String, Object> toMap(Object source) throws Exception {
        return OBJECT_MAPPER.convertValue(source, new TypeReference<Map<String, Object>>() {});
    }
    
    
    // Convert Object to JSON String
    public static String toJsonString(Object object) {
        try {
            return OBJECT_MAPPER.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error converting object to JSON", e);
        }
    }

    // Convert JSON String to Object
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (IOException e) {
            throw new RuntimeException("Error converting JSON to object", e);
        }
    }

    // Convert JSON String to JsonNode
    public static JsonNode toJsonNodeFromString(String json) {
        try {
            return OBJECT_MAPPER.readTree(json);
        } catch (IOException e) {
            throw new RuntimeException("Error parsing JSON string to JsonNode", e);
        }
    }

    // Convert JSON String to JsonNode
    public static JsonNode toJsonNode(Object json) {
        return OBJECT_MAPPER.convertValue(json,JsonNode.class);
    }

    // Convert JsonNode to Object
    public static <T> T fromJsonNode(JsonNode jsonNode, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.treeToValue(jsonNode, clazz);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error converting JsonNode to object", e);
        }
    }

    // Convert Object to Pretty JSON String
    public static String toPrettyJson(Object object) {
        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error converting object to pretty JSON", e);
        }
    }

    // Convert JSON String to List of Objects
    public static <T> List<T> fromJsonToList(String json, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(json, OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (IOException e) {
            throw new RuntimeException("Error converting JSON to List", e);
        }
    }

    // Convert JSON String to Map
    public static <K, V> Map<K, V> fromJsonToMap(String json, Class<K> keyClass, Class<V> valueClass) {
        try {
            return OBJECT_MAPPER.readValue(json, OBJECT_MAPPER.getTypeFactory().constructMapType(Map.class, keyClass, valueClass));
        } catch (IOException e) {
            throw new RuntimeException("Error converting JSON to Map", e);
        }
    }

    public static <T> T convertValue(Object fromValue, Class<T> toValueType) {
        return OBJECT_MAPPER.convertValue(fromValue, toValueType);
    }

    public static <T> T convertValue(Object fromValue, TypeReference<T> toValueTypeRef) {
        return OBJECT_MAPPER.convertValue(fromValue, toValueTypeRef);
    }


    public static <T> T readJsonFile(String fileName, TypeReference<T> typeReference) {
        try {
            return OBJECT_MAPPER.readValue(
                    new ClassPathResource(fileName).getInputStream(),
                    typeReference
            );
        }
        catch (Exception e) {
            log.error("Error reading json file");
            throw new RuntimeException("Error reading json file");
        }
    }

    public static <T> T fromJsonNode(JsonNode node, TypeReference<T> typeReference) {
        try {
            return new ObjectMapper().readValue(new ObjectMapper().treeAsTokens(node), typeReference);
        } catch (IOException e) {
            throw new RuntimeException("Deserialization failed", e);
        }
    }

    public static <T> T merge(T valueToUpdate, Object overrides) {
        try {
            return OBJECT_MAPPER.updateValue(valueToUpdate, overrides);
        } catch (JsonMappingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Usually used as first param: saved data; second param: update request
     */
    public static JsonNode deepMerge(JsonNode mainNode, JsonNode updateNode) {
        return mergeInternal(mainNode, updateNode, false);
    }

    public static JsonNode patchUpdateDeepMerge(JsonNode mainNode, JsonNode updateNode) {
        return mergeInternal(mainNode, updateNode, true);
    }

    private static JsonNode mergeInternal(JsonNode mainNode, JsonNode updateNode, boolean addMissingFields) {
        if (!(mainNode instanceof ObjectNode) || !(updateNode instanceof ObjectNode)) {
            return mainNode;
        }

        ObjectNode mainObject = (ObjectNode) mainNode;
        Iterator<String> fieldNames = updateNode.fieldNames();

        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            JsonNode updatedValue = updateNode.get(fieldName);

            if (mainObject.has(fieldName)) {
                JsonNode currentValue = mainObject.get(fieldName);
                if (currentValue.isObject() && updatedValue.isObject()) {
                    mergeInternal(currentValue, updatedValue, addMissingFields);
                } else {
                    mainObject.set(fieldName, updatedValue);
                }
            } else if (addMissingFields) {
                mainObject.set(fieldName, updatedValue);
            }
        }

        return mainObject;
    }
}

