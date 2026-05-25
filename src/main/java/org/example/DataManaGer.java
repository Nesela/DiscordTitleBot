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
        // 1. 데이터 비어있으면 아예 저장을 안 함 (데이터 보호!)
        if (points == null || points.isEmpty()) {
            System.out.println("⚠️ 데이터가 비어있어 저장을 건너뜁니다.");
            return;
        }

        try {
            // 2. 구글 시트 지우기 (GoogleSheetService 클래스의 메서드 호출)
            GoogleSheetService.clearValues("시트1!A2:C100");

            // 3. 데이터 준비
            List<List<Object>> values = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : points.entrySet()) {
                String userId = entry.getKey();
                int point = entry.getValue();

                // 멤버 닉네임 가져오기
                net.dv8tion.jda.api.entities.Member member = guild.getMemberById(userId);
                String nickname = (member != null) ? member.getEffectiveName() : "알수없음";

                values.add(Arrays.asList(nickname, userId, point));
            }

            // 4. 구글 시트에 업데이트 (GoogleSheetService 클래스의 메서드 호출)
            GoogleSheetService.updateValues("시트1!A2:C", values);

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