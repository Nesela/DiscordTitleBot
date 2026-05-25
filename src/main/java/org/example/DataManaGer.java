package org.example;

import java.util.*;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

public class DataManaGer {

    // 로직을 단순화하여, 데이터를 가져올 때 null 체크를 확실하게 합니다.
    public static HashMap<String, Integer> loadPoints() {
        HashMap<String, Integer> map = new HashMap<>();
        try {
            List<List<Object>> values = GoogleSheetService.getValues("시트1!A2:C100");
            if (values != null) {
                for (List<Object> row : values) {
                    // size 체크를 3 이상으로 확실하게!
                    if (row.size() >= 3 && row.get(0) != null && row.get(2) != null) {
                        map.put(row.get(0).toString(), Integer.parseInt(row.get(2).toString()));
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("포인트 로드 중 에러: " + e.getMessage());
        }
        return map;
    }

    public static void savePoints(HashMap<String, Integer> points, Guild guild) {
        if (points == null || points.isEmpty()) return;
        List<List<Object>> values = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : points.entrySet()) {
            Member member = guild.getMemberById(entry.getKey());
            String nickname = (member != null) ? member.getEffectiveName() : "알수없음";
            values.add(Arrays.asList(entry.getKey(), nickname, entry.getValue()));
        }
        try {
            GoogleSheetService.updateValues("시트1!A2:C", values);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 칭호 로드
    public static HashMap<String, String> loadTitles() {
        HashMap<String, String> map = new HashMap<>();
        try {
            List<List<Object>> values = GoogleSheetService.getValues("시트1!D2:E100");
            if (values != null) {
                for (List<Object> row : values) {
                    if (row.size() >= 2 && row.get(0) != null) {
                        map.put(row.get(0).toString(), row.get(1) != null ? row.get(1).toString() : "");
                    }
                }
            }
        } catch (Exception e) { System.out.println("칭호 로드 에러: " + e.getMessage()); }
        return map;
    }

    // 칭호 저장
    public static void saveTitles(HashMap<String, String> titles) {
        if (titles == null || titles.isEmpty()) return;
        List<List<Object>> values = new ArrayList<>();
        for (Map.Entry<String, String> entry : titles.entrySet()) {
            // [핵심] 대괄호가 포함된 칭호를 텍스트로 명확히 처리
            String title = entry.getValue();
            if (!title.startsWith("[")) title = "[" + title + "]"; // 대괄호 강제 포함

            // 시트에서 수식으로 오해하지 않게 ' (작은따옴표)를 앞에 붙여 저장
            values.add(Arrays.asList(entry.getKey(), "'" + title));
        }

        try {
            // 기존 데이터를 싹 지우고 새로 쓰도록 호출 (영역을 확실하게)
            GoogleSheetService.updateValues("시트1!D2:E", values);
        } catch (Exception e) {
            System.out.println("칭호 저장 실패: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // 빚 로드
    public static HashMap<String, Integer> loadDebts() {
        HashMap<String, Integer> map = new HashMap<>();
        try {
            List<List<Object>> values = GoogleSheetService.getValues("시트1!F2:G100");
            if (values != null) {
                for (List<Object> row : values) {
                    if (row.size() >= 2 && row.get(0) != null && row.get(1) != null) {
                        map.put(row.get(0).toString(), Integer.parseInt(row.get(1).toString()));
                    }
                }
            }
        } catch (Exception e) { System.out.println("빚 로드 에러: " + e.getMessage()); }
        return map;
    }

    // 빚 저장
    public static void saveDebts(HashMap<String, Integer> debts) {
        if (debts == null || debts.isEmpty()) return;
        List<List<Object>> values = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : debts.entrySet()) values.add(Arrays.asList(entry.getKey(), entry.getValue()));
        try {
            GoogleSheetService.updateValues("시트1!F2:G", values);
        } catch (Exception e) { e.printStackTrace(); }
    }

    // --- 만기일 (H:ID, I:만기일) ---
    public static void saveDeadlines(HashMap<String, Long> deadlines) {
        if (deadlines == null || deadlines.isEmpty()) return;
        List<List<Object>> values = new ArrayList<>();
        for (Map.Entry<String, Long> entry : deadlines.entrySet()) {
            // [ID, 만기일] 순으로 저장
            values.add(Arrays.asList(entry.getKey(), entry.getValue()));
        }
        try {
            GoogleSheetService.updateValues("시트1!H2:I", values);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    // --- 만기일 (H:ID, I:만기일) 로드 ---
    public static HashMap<String, Long> loadDeadlines() {
        HashMap<String, Long> map = new HashMap<>();
        try {
            List<List<Object>> values = GoogleSheetService.getValues("시트1!H2:I100");
            if (values != null) {
                for (List<Object> row : values) {
                    if (row.size() >= 2 && row.get(0) != null && row.get(1) != null) {
                        map.put(row.get(0).toString(), Long.parseLong(row.get(1).toString()));
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("만기일 로드 에러: " + e.getMessage());
        }
        return map;
    }

}

