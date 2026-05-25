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

    public static void savePoints(HashMap<String, Integer> points, net.dv8tion.jda.api.entities.Guild guild) {
        if (points == null || points.isEmpty()) {
            System.out.println("⚠️ 데이터가 비어있어 저장을 건너뜁니다.");
            return;
        }

        try {
            // 1. ID와 포인트가 위치한 A열, B열만 정확히 초기화
            GoogleSheetService.clearValues("시트1!A2:B100");

            // 2. 데이터 준비 (A열: ID, B열: 포인트)
            List<List<Object>> values = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : points.entrySet()) {
                String userId = entry.getKey();
                int point = entry.getValue();

                // 데이터 일관성을 위해 [ID, 포인트] 순서로 저장
                values.add(Arrays.asList(userId, point));
            }

            // 3. 업데이트 (A열부터 B열까지 기록)
            GoogleSheetService.updateValues("시트1!A2:B", values);

        } catch (Exception e) {
            e.printStackTrace();
        }
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
        if (titles == null) return;
        List<List<Object>> values = new ArrayList<>();
        for (Map.Entry<String, String> entry : titles.entrySet()) {
            values.add(Arrays.asList(entry.getKey(), entry.getValue()));
        }
        try {
            // [추가] 칭호 영역 초기화
            GoogleSheetService.clearValues("시트1!C2:D100");
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
        if (debts == null) return;
        List<List<Object>> values = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : debts.entrySet()) {
            values.add(Arrays.asList(entry.getKey(), entry.getValue()));
        }
        try {
            // [추가] 빚 영역 초기화
            GoogleSheetService.clearValues("시트1!E2:F100");
            GoogleSheetService.updateValues("시트1!E2:F", values);
        } catch (Exception e) { e.printStackTrace(); }
    }

    // 만기일은 날짜이므로 toString()으로 저장하고 파싱해서 불러옵니다
    public static HashMap<String, Long> loadDeadlines() {
        HashMap<String, Long> map = new HashMap<>();
        try {
            List<List<Object>> values = GoogleSheetService.getValues("시트1!G2:H100");
            if (values != null) {
                for (List<Object> row : values) {
                    if (row.size() >= 2) {
                        map.put(row.get(0).toString(), Long.parseLong(row.get(1).toString()));
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return map;
    }

    public static void saveDeadlines(HashMap<String, Long> deadlines) {
        if (deadlines == null) return;
        List<List<Object>> values = new ArrayList<>();
        for (Map.Entry<String, Long> entry : deadlines.entrySet()) {
            values.add(Arrays.asList(entry.getKey(), entry.getValue().toString()));
        }
        try {
            GoogleSheetService.clearValues("시트1!G2:H100");
            GoogleSheetService.updateValues("시트1!G2:H", values);
        } catch (Exception e) { e.printStackTrace(); }
    }
}