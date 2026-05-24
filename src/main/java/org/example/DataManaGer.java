package org.example;

import java.util.*;
import java.util.stream.Collectors;

public class DataManaGer {

    // 포인트 불러오기
    public static HashMap<String, Integer> loadPoints() {
        HashMap<String, Integer> map = new HashMap<>();
        try {
            List<List<Object>> values = GoogleSheetService.getValues("시트1!A2:B100");
            if (values != null) {
                for (List<Object> row : values) {
                    if (row.size() >= 2) {
                        map.put(row.get(0).toString(), Integer.parseInt(row.get(1).toString()));
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return map;
    }

    // 포인트 저장하기 (자바 17 호환 방식)
    public static void savePoints(HashMap<String, Integer> points) {
        List<List<Object>> values = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : points.entrySet()) {
            values.add(Arrays.asList(entry.getKey(), entry.getValue()));
        }
        try {
            GoogleSheetService.updateValues("시트1!A2:B", values);
        } catch (Exception e) { e.printStackTrace(); }
    }

    // 칭호 불러오기
    public static HashMap<String, String> loadTitles() {
        HashMap<String, String> map = new HashMap<>();
        try {
            List<List<Object>> values = GoogleSheetService.getValues("시트1!C2:D100");
            if (values != null) {
                for (List<Object> row : values) {
                    if (row.size() >= 2) {
                        map.put(row.get(0).toString(), row.get(1).toString());
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return map;
    }

    // 칭호 저장하기 (자바 17 호환 방식)
    public static void saveTitles(HashMap<String, String> titles) {
        List<List<Object>> values = new ArrayList<>();
        for (Map.Entry<String, String> entry : titles.entrySet()) {
            values.add(Arrays.asList(entry.getKey(), entry.getValue()));
        }
        try {
            GoogleSheetService.updateValues("시트1!C2:D", values);
        } catch (Exception e) { e.printStackTrace(); }
    }
}