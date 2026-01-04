package com.piplineData.loadService.service;

import com.google.common.collect.MapDifference;
import com.google.common.collect.Maps;

import com.piplineData.loadService.dto.MapField;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DataComparator {

    /**
     * So sánh dữ liệu từ Source và Destination sử dụng MapDifference
     * @param sourceData dữ liệu từ Source DB
     * @param destData dữ liệu từ Destination DB
     * @param keyFields danh sách các field làm key để so sánh
     * @return MapDifference object
     */
    public MapDifference<String, Map<String, Object>> compareData(
            List<Map<String, Object>> sourceData,
            List<Map<String, Object>> destData,
            List<String> keyFields) {


        // Chuyển đổi List thành Map với composite key
        Map<String, Map<String, Object>> sourceMap = convertToMap(sourceData, keyFields);
        Map<String, Map<String, Object>> destMap = convertToMap(destData, keyFields);

        // Sử dụng Guava MapDifference để so sánh
        MapDifference<String, Map<String, Object>> difference = Maps.difference(sourceMap, destMap);


        return difference;
    }

    /**
     * Chuyển List<Map> thành Map với composite key
     * Composite key được tạo từ các keyFields
     */
    private Map<String, Map<String, Object>> convertToMap(
            List<Map<String, Object>> dataList,
            List<String> keyFields) {

        Map<String, Map<String, Object>> resultMap = new HashMap<>();

        for (Map<String, Object> record : dataList) {
            String compositeKey = buildCompositeKey(record, keyFields);
            resultMap.put(compositeKey, record);
        }

        return resultMap;
    }

    /**
     * Tạo composite key từ các keyFields
     * Ví dụ: orderid=123|createtime=2025-01-01
     */
    private String buildCompositeKey(Map<String, Object> record, List<String> keyFields) {
        return keyFields.stream()
                .map(field -> {
                    Object value = record.get(field);
                    return field + "=" + (value != null ? value.toString() : "null");
                })
                .collect(Collectors.joining("|"));
    }

    /**
     * Parse composite key thành Map
     */
    public Map<String, Object> parseCompositeKey(String compositeKey) {
        Map<String, Object> keyMap = new HashMap<>();
        String[] parts = compositeKey.split("\\|");

        for (String part : parts) {
            String[] keyValue = part.split("=", 2);
            if (keyValue.length == 2) {
                keyMap.put(keyValue[0], keyValue[1]);
            }
        }

        return keyMap;
    }

    /**
     * Lấy danh sách key fields từ MapField configuration
     */
    public List<String> extractKeyFields(List<MapField> mapFields) {
        return mapFields.stream()
                .filter(mf -> "key".equalsIgnoreCase(mf.getBuild()))
                .map(MapField::getTo)
                .collect(Collectors.toList());
    }

    /**
     * Lấy danh sách value fields từ MapField configuration
     */
    public List<String> extractValueFields(List<MapField> mapFields) {
        return mapFields.stream()
                .filter(mf -> "value".equalsIgnoreCase(mf.getBuild()))
                .map(MapField::getTo)
                .collect(Collectors.toList());
    }
}