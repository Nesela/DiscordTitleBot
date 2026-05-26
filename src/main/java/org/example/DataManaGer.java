package org.example;

import java.util.*;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

public class DataManaGer {
    // 1. 메모리에 닉네임을 기억할 Map 추가
    public static HashMap<String, String> nicknameCache = new HashMap<>();

    public static HashMap<String, Integer> loadPoints() {
        HashMap<String, Integer> map = new HashMap<>();
        // nicknameCache.clear(); // [주의] 무조건 다 지우지 마세요!

        try {
            List<List<Object>> values = GoogleSheetService.getValues("시트1!A2:C100");
            if (values != null) {
                for (List<Object> row : values) {
                    if (row.size() >= 3 && row.get(0) != null) {
                        String userId = row.get(0).toString();
                        String nickname = (row.get(1) != null) ? row.get(1).toString() : "알수없음";
                        int point = Integer.parseInt(row.get(2).toString());

                        map.put(userId, point);

                        // [수정 포인트] "알수없음"이 아닐 때만 캐시에 넣습니다.
                        // 이렇게 하면 봇이 처음에 이름을 모를 땐 캐시가 비어있게 되고,
                        // 나중에 봇이 서버에서 멤버 정보를 가져올 때 자연스럽게 진짜 이름이 채워집니다.
                        if (!nickname.equals("알수없음")) {
                            nicknameCache.put(userId, nickname);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return map;
    }

    public static void savePoints(HashMap<String, Integer> points, net.dv8tion.jda.api.entities.Guild guild) {
        System.out.println("[디버그] 봇이 인식 중인 전체 멤버 수: " + guild.getMembers().size());

        List<List<Object>> values = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : points.entrySet()) {
            String userId = entry.getKey();
            net.dv8tion.jda.api.entities.Member member = guild.getMemberById(userId);

            String displayName = (member != null) ? member.getEffectiveName() : nicknameCache.getOrDefault(userId, "알수없음");

            // "알수없음" 이거나 "불러오기실패"인 경우 저장을 건너뜁니다 (기존 데이터 유지)
            if (displayName.equals("알수없음") || displayName.contains("이름불러오기")) {
                continue;
            }

            values.add(Arrays.asList(userId, displayName, entry.getValue()));
        }

        // [핵심] try-catch로 감싸야 빨간줄이 사라집니다!
        try {
            if (!values.isEmpty()) {
                GoogleSheetService.updateValues("시트1!A2:C", values);
            }
        } catch (Exception e) {
            System.err.println("구글 시트 저장 중 오류 발생: " + e.getMessage());
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
                    if (row.size() >= 2) map.put(row.get(0).toString(), row.get(1).toString());
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return map;
    }

    // 칭호 저장
    public static void saveTitles(HashMap<String, String> titles) {
        if (titles == null) return;
        List<List<Object>> values = new ArrayList<>();
        for (Map.Entry<String, String> entry : titles.entrySet()) {
            // [강력] 모든 대괄호, 따옴표를 다 지워버림
            String rawTitle = entry.getValue().replaceAll("[\\[\\]\"']", "");
            values.add(Arrays.asList(entry.getKey(), rawTitle));
        }
        try {
            GoogleSheetService.updateValues("시트1!D2:E", values);
        } catch (Exception e) { e.printStackTrace(); }
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

