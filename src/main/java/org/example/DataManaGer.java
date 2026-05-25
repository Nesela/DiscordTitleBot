package org.example;

import java.time.LocalDate;
import java.util.*;

public class DataManaGer {

    // --- 포인트 로직 ---
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

    public static void savePoints(HashMap<String, Integer> points) {
        List<List<Object>> values = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : points.entrySet()) {
            values.add(Arrays.asList(entry.getKey(), entry.getValue()));
        }
        try {
            GoogleSheetService.updateValues("시트1!A2:B", values);
        } catch (Exception e) { e.printStackTrace(); }
    }

    // --- 칭호 로직 ---
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

    public static void saveTitles(HashMap<String, String> titles) {
        List<List<Object>> values = new ArrayList<>();
        for (Map.Entry<String, String> entry : titles.entrySet()) {
            values.add(Arrays.asList(entry.getKey(), entry.getValue()));
        }
        try {
            GoogleSheetService.updateValues("시트1!C2:D", values);
        } catch (Exception e) { e.printStackTrace(); }
    }

    // --- 빚 및 만기일 로직 (시트 E:F에 저장하도록 세팅) ---
    public static HashMap<String, Integer> loadDebts() {
        HashMap<String, Integer> map = new HashMap<>();
        try {
            List<List<Object>> values = GoogleSheetService.getValues("시트1!E2:F100");
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

    public static void saveDebts(HashMap<String, Integer> debts) {
        List<List<Object>> values = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : debts.entrySet()) {
            values.add(Arrays.asList(entry.getKey(), entry.getValue()));
        }
        try {
            GoogleSheetService.updateValues("시트1!E2:F", values);
        } catch (Exception e) { e.printStackTrace(); }
    }

    // 만기일은 날짜이므로 toString()으로 저장하고 파싱해서 불러옵니다
    public static HashMap<String, LocalDate> loadDeadlines() {
        HashMap<String, LocalDate> map = new HashMap<>();
        try {
            List<List<Object>> values = GoogleSheetService.getValues("시트1!G2:H100");
            if (values != null) {
                for (List<Object> row : values) {
                    if (row.size() >= 2) {
                        map.put(row.get(0).toString(), LocalDate.parse(row.get(1).toString()));
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return map;
    }

    public static void saveDeadlines(HashMap<String, LocalDate> deadlines) {
        List<List<Object>> values = new ArrayList<>();
        for (Map.Entry<String, LocalDate> entry : deadlines.entrySet()) {
            values.add(Arrays.asList(entry.getKey(), entry.toString()));
        }
        try {
            GoogleSheetService.updateValues("시트1!G2:H", values);
        } catch (Exception e) { e.printStackTrace(); }
    }
}