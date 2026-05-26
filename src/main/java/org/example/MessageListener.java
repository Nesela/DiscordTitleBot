package org.example;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import javax.xml.crypto.Data;
import java.util.HashMap;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class MessageListener extends ListenerAdapter {
    // 1. 여기서 바로 로드하지 말고 변수만 선언하세요.
    private HashMap<String, Integer> userPoints = new HashMap<>(); // 선언 시 바로 로드 금지!
    private HashMap<String, String> userTitles = new HashMap<>();
    private HashMap<String, Integer> userDebt = new HashMap<>();
    private HashMap<String, Long> debtDeadline = new HashMap<>();

    private HashMap<String, LocalDate> lastCheckInDates = new HashMap<>();

    // 2. 생성자를 만들어 여기서 데이터를 로드합니다.
    public MessageListener() {
        HashMap<String, Integer> loadedPoints = DataManaGer.loadPoints();
        if (loadedPoints != null) this.userPoints = loadedPoints;

        HashMap<String, String> loadedTitles = DataManaGer.loadTitles();
        if (loadedTitles != null) this.userTitles = loadedTitles;

        HashMap<String, Integer> loadedDebts = DataManaGer.loadDebts();
        if (loadedDebts != null) this.userDebt = loadedDebts;

        HashMap<String, Long> loadedDeadlines = DataManaGer.loadDeadlines();
        if (loadedDeadlines != null) this.debtDeadline = loadedDeadlines;
    }

    // 가격표 변수는 그대로 두셔도 됩니다.
    private int publicTitlePrice = 100;
    private int customTitlePrice = 150;

    //유통기한 날짜생성
    private String getExpirationDate(int days) {
        return LocalDate.now().plusDays(days).format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    //만료 여부 확인
    public boolean isExpiresd(String dateString) {
        if (dateString == null || dateString.isEmpty()) return false;

        try {
            // [핵심] 숫자와 연관 없는 문자(], [, 등)를 전부 제거하고 숫자만 남깁니다.
            String cleanDate = dateString.replaceAll("[^0-9]", "");

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
            LocalDate expiryDate = LocalDate.parse(cleanDate, formatter);
            LocalDate now = LocalDate.now();

            return now.isAfter(expiryDate);
        } catch (Exception e) {
            System.out.println("날짜 파싱 오류 발생: " + dateString);
            return false; // 파싱 실패 시 만료되지 않은 것으로 간주하거나 에러 처리
        }
    }

    private void checkTitlse(MessageReceivedEvent event) {
        // 1. 닉네임이 아닌 '고유 ID'를 키로 사용합니다.
        String userId = event.getAuthor().getId();
        String currentNickname = event.getMember().getEffectiveName();
        String pureName = currentNickname.replaceAll("\\[.*?\\]", "").trim();

        // 2. 데이터 조회도 userId로!
        if (!userTitles.containsKey(userId)) return;

        String data = userTitles.get(userId);
        String[] entries = data.split(",");
        StringBuilder newInventory = new StringBuilder();
        boolean isChanged = false;

        for (String entry : entries) {
            String[] parts = entry.split("\\|");
            if (parts.length < 2) continue;

            String title = parts[0];
            String expirDate = parts[1];

            if (isExpiresd(expirDate)) {
                isChanged = true;
                event.getChannel().sendMessage(" [" + title + "] 칭호 기간이 만료되어 회수되었습니다.").queue();
            } else {
                if (newInventory.length() > 0) newInventory.append(",");
                newInventory.append(entry);
            }
        }

        if (isChanged) {
            // 3. 저장도 userId로!
            if (newInventory.length() == 0) userTitles.remove(userId);
            else userTitles.put(userId, newInventory.toString());

            DataManaGer.saveTitles(userTitles);

            if (!event.getMember().isOwner()) {
                event.getMember().modifyNickname(pureName).queue();
            }
        }

        // 4. 만기일 확인도 userId로!
        long now = System.currentTimeMillis();
        if (debtDeadline.containsKey(userId) && now > debtDeadline.get(userId)) {
            int points = userPoints.getOrDefault(userId, 0);
            int debt = userDebt.get(userId);

            userPoints.put(userId, points - debt);
            userDebt.remove(userId);
            debtDeadline.remove(userId);

            DataManaGer.saveDebts(userDebt);
            DataManaGer.saveDeadlines(debtDeadline);

            event.getMember().modifyNickname("[노예] " + pureName).queue();
            event.getChannel().sendMessage("⛓️ **[" + pureName + "]**님의 대출 만기일이 지나 모든 자산이 압류되었습니다. 현재 잔고: **" + (points - debt) + " P**").queue();

            DataManaGer.savePoints(userPoints, event.getGuild());
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        // 공통 변수 (딱 한 번만 선언하세요!)
        String message = event.getMessage().getContentRaw();
        String userId = event.getAuthor().getId();
        String currentNickname = event.getMember().getEffectiveName();
        String pureName = currentNickname.replaceAll("\\[.*?\\]", "").trim();
        boolean isAdmin = event.getMember().hasPermission(Permission.ADMINISTRATOR);

        if (message.startsWith("!포인트지급 ")) {
            boolean isStaff = event.getMember().isOwner() || event.getMember().hasPermission(Permission.ADMINISTRATOR);
            if (!isStaff) {
                event.getChannel().sendMessage("서버 운영진만 사용할 수 있는 기능입니다!").queue();
                return;
            }

            String content = message.substring("!포인트지급 ".length()).trim();
            int lastSpaceIndex = content.lastIndexOf(" ");
            if (lastSpaceIndex == -1) {
                event.getChannel().sendMessage("사용법: `!포인트지급 [닉네임] [금액]`").queue();
                return;
            }

            String targetName = content.substring(0, lastSpaceIndex);
            String amountStr = content.substring(lastSpaceIndex + 1);

            try {
                int amount = Integer.parseInt(amountStr);

                // [중요] 닉네임으로 유저 찾아서 userId 구하기
                String targetUserId = null;
                for (net.dv8tion.jda.api.entities.Member member : event.getGuild().getMembers()) {
                    if (member.getEffectiveName().contains(targetName)) {
                        targetUserId = member.getId();
                        break;
                    }
                }

                if (targetUserId == null) {
                    event.getChannel().sendMessage("서버에서 해당 유저를 찾을 수 없습니다.").queue();
                    return;
                }

                // userId를 키로 사용하여 저장
                int currentPoints = userPoints.getOrDefault(targetUserId, 0);
                userPoints.put(targetUserId, currentPoints + amount);
                DataManaGer.savePoints(userPoints, event.getGuild());

                event.getChannel().sendMessage(" **[" + targetName + "]**님께 **" + amount + " P** 지급 완료!").queue();

            } catch (NumberFormatException e) {
                event.getChannel().sendMessage(" **[오류]** 금액을 확인해주세요.").queue();
            }
        }
        //유저삭제
        if (message.startsWith("!유저삭제 ")) {
            if (!event.getMember().isOwner() && !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
                event.getChannel().sendMessage("서버 운영진만 사용할 수 있습니다.").queue();
                return;
            }

            String targetQuery = message.substring(6).trim(); // 6글자 "!유저삭제 " 제외
            String targetUserId = null;

            // 닉네임 검색만 수행
            for (net.dv8tion.jda.api.entities.Member member : event.getGuild().getMembers()) {
                if (member.getEffectiveName().contains(targetQuery)) {
                    targetUserId = member.getId();
                    break;
                }
            }

            if (targetUserId == null) {
                event.getChannel().sendMessage("❌ 해당 유저를 찾을 수 없습니다.").queue();
                return;
            }

            // 데이터 삭제
            userPoints.remove(targetUserId);
            userTitles.remove(targetUserId);
            userDebt.remove(targetUserId);
            debtDeadline.remove(targetUserId);

            DataManaGer.savePoints(userPoints, event.getGuild());
            DataManaGer.saveTitles(userTitles);
            DataManaGer.saveDebts(userDebt);
            DataManaGer.saveDeadlines(debtDeadline);

            event.getChannel().sendMessage("✅ **ID: " + targetUserId + "** 유저의 모든 데이터가 삭제되었습니다.").queue();
        }

        if (!isAdmin) {
            checkTitlse(event);
        }

        if (event.getAuthor().isBot()) return;

// 칭호 유무 확인 (ID 사용)
        userId = event.getAuthor().getId();
        String realTitle = userTitles.get(userId);

        String chatName = currentNickname;

        if (realTitle != null && !realTitle.isEmpty()) {
            if (!currentNickname.contains("[") || !currentNickname.contains("]")) {
                String firstTitle = realTitle.split(",")[0].split("\\|")[0];
                chatName = "[" + firstTitle + "] " + pureName;
            }
        }

// 칭호 검사 (도용 방지)
        // 칭호 검사 (도용 방지) 부분 수정
        if (!isAdmin) {
            if (currentNickname.contains("[") && currentNickname.contains("]")) {
                String tagInNickname = currentNickname.substring(currentNickname.indexOf("[") + 1, currentNickname.indexOf("]"));

                // [수정 포인트] 빚쟁이는 예외 처리!
                if (tagInNickname.equals("빚쟁이")) {
                    // 빚쟁이일 때는 칭호 도용 체크를 아예 하지 않고 다음 로직으로 넘어감
                } else {
                    // 그 외 일반 칭호들만 도용 체크 수행
                    if (realTitle == null || !realTitle.contains(tagInNickname)) {
                        String targetNickname = (realTitle != null && !realTitle.isEmpty())
                                ? "[" + realTitle.split(",")[0].split("\\|")[0] + "] " + pureName
                                : pureName;

                        if (!event.getMember().isOwner()) event.getMember().modifyNickname(targetNickname).queue();
                        event.getChannel().sendMessage("⚠️ 칭호 도용 감지! 닉네임이 리셋되었습니다.").queue();
                        return; // 여기서 멈추는 것은 정상
                    }
                }
            }
        }

        //명령어 종류
        if (message.equals("!명령어")) {
            event.getChannel().sendMessage("\uD83C\uDFAE [종겜방 봇 명령어 안내]\n" +
                    "💰 **포인트 & 도박**\n" +
                    "1. `!출첵` : 출석체크를 진행하여 포인트를 획득합니다.\n" +
                    "1. `!포인트` : 내 보유 포인트를 확인합니다.\n" +
                    "1. `!랭킹` : 현재 보유 포인트 랭킹을 확인합니다.\n" +
                    "1. `!선물` : 포인트의 선물이 가능합니다.\n" +
                    "1. `!홀짝 [홀/짝] [금액]` : 포인트의 2배를 노리는 도박 게임!\n\n" +
                    "💸 **대출 & 상환**\n" +
                    "6. `!대출 [금액]` : 최대 100 P까지 대출 (3일 이내 상환 필수!)\n" +
                    "7. `!상환` : 빌린 빚을 상환합니다.\n\n" +
                    "🏷️ **칭호 시스템**\n" +
                    "\n 구매하신 칭호는 구매일로부터 14일 동안 사용하실 수 있습니다\n" +
                    "1. `!칭호교체 칭호이름` : 보유중인 칭호에서 교체가 가능합니다.\n" +
                    "1. `!내칭호` : 내 칭호를 확인합니다.\n" +
                    "2. `!칭호상점` : 칭호 상점을 엽니다.").queue();
        }
        // 출석체크
        if (message.equals("!출첵")) {
            userId = event.getAuthor().getId();
            java.time.LocalDate today = java.time.LocalDate.now();
            java.time.LocalDate lastDate = lastCheckInDates.get(userId);

            if (lastDate != null && lastDate.equals(today)) {
                event.getChannel().sendMessage(" **[" + chatName + "]**님, 출석체크는 **하루에 한 번**만 가능합니다!").queue();
                return;
            }

            int bonus = 0;
            String msg = " **[" + chatName + "]** 님이 출석체크를 완료하여 15포인트가 지급되었습니다.";

            if (!userPoints.containsKey(userId)) { // ID로 확인
                bonus = 100;
                msg = " **[" + chatName + "]** 님, 첫 출첵! 보너스 100포인트 포함 **115포인트**가 지급되었습니다.";
            }

            int currentPoint = userPoints.getOrDefault(userId, 0); // ID로 조회
            userPoints.put(userId, currentPoint + 15 + bonus); // ID로 저장
            lastCheckInDates.put(userId, today);
            DataManaGer.savePoints(userPoints, event.getGuild());

            event.getChannel().sendMessage(msg).queue();
            return;
        }

// 포인트 확인
        if (message.equals("!포인트")) {
            userId = event.getAuthor().getId();
            int myPoint = userPoints.getOrDefault(userId, 0);
            int myDebt = userDebt.getOrDefault(userId, 0);
            int actualBalance = myPoint - myDebt;

            String status = (myDebt > 0) ? " (빚: " + myDebt + " P)" : "";
            event.getChannel().sendMessage("💰 **[" + chatName + "]** 님의 현재 잔고: **" + myPoint + " P**" + status).queue();
        }

// 칭호상점 (그대로 두셔도 됩니다)
        if (message.equals("!칭호상점")) {
            // 여기에 방장님의 원래 내용을 그대로 넣으시면 됩니다!
            // 예시:
            event.getChannel().sendMessage("\uD83C\uDFAE **[종겜방 칭호 상점]**\n\n" +
                    "칭호는 구매일로부터 **14일**간 유지됩니다.\n\n" +
                    "1. **짱짱시루** - 100 P\n" +
                    "2. **고인물** - 100 P\n" +
                    "----------------------------\n" +
                    "🏷️ **수제작 칭호 (최대 4글자)** - 150 P\n\n" +
                    "구매법: `!칭호구매 [이름]` 또는 `!칭호제작 [이름]`").queue();
        }

// 고정값 칭호 구매
        if (message.startsWith("!칭호구매 ")) {
            userId = event.getAuthor().getId();
            int myPoint = userPoints.getOrDefault(userId, 0);
            String choice = message.substring(6).trim();

            if (!choice.equals("짱짱시루") && !choice.equals("고인물")) {
                event.getChannel().sendMessage("상점에 없는 칭호입니다!").queue();
                return;
            }

            if (myPoint < publicTitlePrice) {
                event.getChannel().sendMessage("포인트가 부족합니다.").queue();
                return;
            }

            if (!event.getMember().isOwner()) {
                event.getMember().modifyNickname("[" + choice + "] " + pureName).queue();
            }

            String currentData = userTitles.getOrDefault(userId, "");
            if (currentData.contains(choice)) {
                event.getChannel().sendMessage("이미 보유하고 있는 칭호입니다!").queue();
                return;
            }

            // 칭호 가방 저장 및 포인트 차감 (전부 userId 기준)
            userTitles.put(userId, currentData.isEmpty() ? choice + "|" + getExpirationDate(14) : currentData + "," + choice + "|" + getExpirationDate(14));
            userPoints.put(userId, myPoint - publicTitlePrice);

            DataManaGer.savePoints(userPoints, event.getGuild());
            DataManaGer.saveTitles(userTitles);
            event.getChannel().sendMessage(" **[" + pureName + "]**님이 공용 칭호 **[" + choice + "]**를 구매하셨습니다.").queue();
        }
        // 수제작 칭호
        if (message.startsWith("!칭호제작 ")) {
            userId = event.getAuthor().getId();

            // 1. 여기서 pureName을 딱 한 번만 정의함
            String currentName = event.getMember().getEffectiveName();
            pureName = currentName.replaceAll("\\[.*?\\]", "").trim();

            int myPoint = userPoints.getOrDefault(userId, 0);
            String customTitle = message.substring(6).trim();

            // 1. 글자 수 제한
            if (customTitle.length() > 4) {
                event.getChannel().sendMessage("수제작 칭호는 **최대 4글자**까지만 가능합니다!").queue();
                return;
            }

            // 2. [핵심] 마이너스 방지 로직 (포인트가 가격보다 적으면 아예 차단)
            if (myPoint < customTitlePrice) {
                event.getChannel().sendMessage("❌ 포인트가 부족합니다! (필요: " + customTitlePrice + " P, 보유: " + myPoint + " P)").queue();
                return;
            }

            // 3. 이미 보유한 칭호 체크
            String currentData = userTitles.getOrDefault(userId, "");
            if (currentData.contains(customTitle)) {
                event.getChannel().sendMessage("❌ 이미 보유하고 있는 칭호입니다!").queue();
                return;
            }

            // 4. 데이터 업데이트 (포인트 차감 및 데이터 추가)
            userPoints.put(userId, myPoint - customTitlePrice);
            String newEntry = customTitle + "|" + getExpirationDate(14);
            userTitles.put(userId, currentData.isEmpty() ? newEntry : currentData + "," + newEntry);

            // 5. 시트 저장 (데이터 반영)
            DataManaGer.savePoints(userPoints, event.getGuild());
            DataManaGer.saveTitles(userTitles);

            // 6. 닉네임 변경 (데이터 저장 완료 후 실행)
            try {
                if (!event.getMember().isOwner()) {
                    event.getMember().modifyNickname("[" + customTitle + "] " + pureName).queue();
                }
            } catch (Exception e) {
                System.out.println("닉네임 변경 실패: " + e.getMessage());
            }

            event.getChannel().sendMessage("✅ **" + pureName + "**님의 장인 정신이 깃든 **[" + customTitle + "]** 칭호가 완성되었습니다!").queue();
        }

// 칭호교환
        if (message.startsWith("!칭호교체 ")) {
            userId = event.getAuthor().getId();
            String newTitle = message.substring(6).trim(); // !칭호교체 + 공백 = 6글자라 그대로 쓰시면 됩니다
            String currentData = userTitles.getOrDefault(userId, "");

            if (!currentData.contains(newTitle)) {
                event.getChannel().sendMessage("❌ 보유하지 않은 칭호입니다!").queue();
                return;
            }

            String[] entries = currentData.split(",");
            StringBuilder reordered = new StringBuilder();
            String targetEntry = "";

            for (String entry : entries) {
                if (entry.split("\\|")[0].equals(newTitle)) {
                    targetEntry = entry;
                } else {
                    if (reordered.length() > 0) reordered.append(",");
                    reordered.append(entry);
                }
            }

            String finalData = (reordered.length() == 0) ? targetEntry : targetEntry + "," + reordered.toString();

            userTitles.put(userId, finalData);
            DataManaGer.saveTitles(userTitles);

            String cleanName = event.getMember().getEffectiveName().replaceAll("\\[.*?\\]", "").trim();
            if (!event.getMember().isOwner()) {
                event.getMember().modifyNickname("[" + newTitle + "] " + cleanName).queue();
            }
            event.getChannel().sendMessage("✅ 칭호를 **[" + newTitle + "]**(으)로 교체했습니다!").queue();
        }
        //내칭호 확인
        // 내칭호 확인
        if (message.equals("!내칭호")) {
            userId = event.getAuthor().getId();
            String currentData = userTitles.getOrDefault(userId, "");

            if (currentData.isEmpty()) {
                event.getChannel().sendMessage(" **[" + pureName + "]**님, 아직 보유한 칭호가 없습니다!").queue();
                return;
            }
            StringBuilder sb = new StringBuilder();
            sb.append(" \uD83C\uDFAE **[" + pureName + "]님의 칭호 가방**\n\n");

            String[] entries = currentData.split(",");
            for (String entry : entries) {
                String[] parts = entry.split("\\|");
                if (parts.length < 2) continue;

                String title = parts[0];
                String expiry = parts[1];
                String formattedDate = expiry.substring(0, 4) + "-" + expiry.substring(4, 6) + "-" + expiry.substring(6, 8);

                if (isExpiresd(expiry)) {
                    sb.append(" - ~~[" + title + "] (만료됨)~~ \n");
                } else {
                    sb.append(" - [" + title + "] (유통기한: " + formattedDate + "까지) \n");
                }
            }
            event.getChannel().sendMessage(sb.toString()).queue();
        }

// 도박 홀짝
        if (message.startsWith("!홀짝")) {
            userId = event.getAuthor().getId();
            String[] parts = message.split(" ");
            if (parts.length < 3) {
                event.getChannel().sendMessage(" 사용법: `!홀짝 [홀/짝] [금액]`").queue();
                return;
            }

            String choice = parts[1];
            int bet = 0;
            try {
                bet = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                event.getChannel().sendMessage(" 금액은 숫자로 입력해 주세요!").queue();
                return;
            }

            if (bet <= 0) {
                event.getChannel().sendMessage(" ❌ **금액은 1 P 이상으로만 배팅 가능합니다!**").queue();
                return;
            }

            // [중요] ID 기반으로 포인트와 빚 계산
            int myPoint = userPoints.getOrDefault(userId, 0); // 이것만 있으면 됩니다!

            if (bet > myPoint) {
                event.getChannel().sendMessage(" ❌ **[" + pureName + "]님, 포인트가 부족합니다! (보유: " + myPoint + " P)**").queue();
                return;
            }

            int result = (int) (Math.random() * 2);
            String resultStr = (result == 0) ? "짝" : "홀";
            boolean isWin = (choice.equals(resultStr));

            if (isWin) {
                userPoints.put(userId, myPoint + bet);
                event.getChannel().sendMessage(" \uD83C\uDF89 **[정답!]** 결과: **[" + resultStr + "]**\n" +
                        "배팅액: " + bet + " P\n" +
                        "현재 자산: **" + (myPoint + bet) + " P**").queue();
            } else {
                userPoints.put(userId, myPoint - bet);
                event.getChannel().sendMessage(" \uD83D\uDC80 **[실패!]** 결과: **[" + resultStr + "]**\n" +
                        "배팅액: " + bet + " P\n" +
                        "현재 자산: **" + (myPoint - bet) + " P**").queue();
            }
            DataManaGer.savePoints(userPoints, event.getGuild());
        }
        //랭킹포인트
        if (message.equals("!랭킹")) {
            if (userPoints.isEmpty()) {
                event.getChannel().sendMessage("아직 포인트 데이터가 없습니다!").queue();
                return;
            }

            java.util.List<java.util.Map.Entry<String, Integer>> ranking = new java.util.ArrayList<>(userPoints.entrySet());

            ranking.sort((o1, o2) -> {
                int b1 = o1.getValue() - userDebt.getOrDefault(o1.getKey(), 0);
                int b2 = o2.getValue() - userDebt.getOrDefault(o2.getKey(), 0);
                return Integer.compare(b2, b1);
            });

            StringBuilder sb = new StringBuilder();
            sb.append("\uD83C\uDFC6 **[종겜방 실제 잔고 랭킹 (Top 10)]**\n\n");

            for (int i = 0; i < Math.min(ranking.size(), 10); i++) {
                java.util.Map.Entry<String, Integer> entry = ranking.get(i);
                String targetId = entry.getKey();

                int actualBalance = entry.getValue() - userDebt.getOrDefault(targetId, 0);

                // ID로 멤버를 찾아 닉네임을 표시 (없으면 ID 그대로 표시)
                net.dv8tion.jda.api.entities.Member member = event.getGuild().getMemberById(targetId);
                String name = (member != null) ? member.getEffectiveName() : "알 수 없음";

                sb.append(String.format("%d등: **%s** %d P\n", i + 1, name, actualBalance));
            }
            event.getChannel().sendMessage(sb.toString()).queue();
            return;
        }
        //선물하기
        if (message.startsWith("!선물")) {
            String senderId = event.getAuthor().getId();
            String content = message.substring(3).trim();
            int lastSpaceIndex = content.lastIndexOf(" ");

            if (lastSpaceIndex == -1) {
                event.getChannel().sendMessage("사용법: `!선물 [받을사람] [금액]`").queue();
                return;
            }

            String receiverName = content.substring(0, lastSpaceIndex).trim();
            String amountStr = content.substring(lastSpaceIndex + 1);

            try {
                int amount = Integer.parseInt(amountStr);
                int senderPoints = userPoints.getOrDefault(senderId, 0);
                int myDebt = userDebt.getOrDefault(senderId, 0);

                if (amount <= 0) {
                    event.getChannel().sendMessage("1 이상 입력하세요.").queue();
                    return;
                }
                if (senderPoints - myDebt < amount) {
                    event.getChannel().sendMessage("잔고가 부족합니다.").queue();
                    return;
                }

                // 닉네임으로 수신자 ID 찾기
                String receiverId = null;
                for (net.dv8tion.jda.api.entities.Member m : event.getGuild().getMembers()) {
                    if (m.getEffectiveName().contains(receiverName)) {
                        receiverId = m.getId();
                        break;
                    }
                }

                if (receiverId == null || receiverId.equals(senderId)) {
                    event.getChannel().sendMessage("존재하지 않는 유저이거나 자기 자신입니다.").queue();
                    return;
                }

                userPoints.put(senderId, senderPoints - amount);
                userPoints.put(receiverId, userPoints.getOrDefault(receiverId, 0) + amount);
                DataManaGer.savePoints(userPoints, event.getGuild());

                event.getChannel().sendMessage("🎁 **" + pureName + "**님이 **" + receiverName + "**님에게 **" + amount + " P**를 선물했습니다!").queue();
            } catch (NumberFormatException e) {
                event.getChannel().sendMessage("숫자를 입력해주세요.").queue();
            }
        }
        //대출
        if (message.startsWith("!대출 ")) {
            try {
                int amount = Integer.parseInt(message.substring(4).trim());
                userId = event.getAuthor().getId();

                if (amount > 100) {
                    event.getChannel().sendMessage("❌ 대출 한도를 초과했습니다! (최대 100 P까지 대출 가능)").queue();
                    return; // 여기서 멈춤 (데이터 저장 안 함)
                }

                if (userDebt.containsKey(userId)) {
                    event.getChannel().sendMessage("❌ 이미 상환하지 않은 빚이 있습니다! 상환 후 다시 시도하세요.").queue();
                    return;
                }

                // 1. 데이터 업데이트
                int currentPoints = userPoints.getOrDefault(userId, 0);
                int currentDebt = userDebt.getOrDefault(userId, 0);

                userPoints.put(userId, currentPoints + amount);
                userDebt.put(userId, currentDebt + amount);
                debtDeadline.put(userId, System.currentTimeMillis() + (3L * 24 * 60 * 60 * 1000));
                userTitles.put(userId, "빚쟁이");

                // 2. 무조건 데이터부터 저장 (닉네임 변경 전)
                DataManaGer.savePoints(userPoints, event.getGuild());
                DataManaGer.saveDebts(userDebt);
                DataManaGer.saveDeadlines(debtDeadline);
                DataManaGer.saveTitles(userTitles);

                // 3. 닉네임 변경 (이 부분에서 오류가 나도 포인트는 이미 저장됨)
                try {
                    String currentName = event.getMember().getEffectiveName();
                    String cleanName = currentName.replaceAll("\\[.*?\\]", "").trim();
                    if (!event.getMember().isOwner()) {
                        event.getMember().modifyNickname("[빚쟁이] " + cleanName).queue();
                    }
                } catch (Exception e) {
                    System.out.println("닉네임 변경 중 사소한 오류 발생: " + e.getMessage());
                }

                event.getChannel().sendMessage("💰 **" + amount + " P** 대출 완료! 현재 잔고: **" + (currentPoints + amount) + " P**").queue();

            } catch (NumberFormatException e) {
                event.getChannel().sendMessage("❌ 금액을 숫자로 입력해주세요!").queue();
            }
        }

        //상한
        //상한
        if (message.equals("!상환")) {
            userId = event.getAuthor().getId();

            // 1. 빚이 있는지 확인
            if (!userDebt.containsKey(userId)) {
                event.getChannel().sendMessage("❌ 상환할 빚이 없습니다!").queue();
                return;
            }

            int debt = userDebt.get(userId);
            int myPoint = userPoints.getOrDefault(userId, 0);

            // 2. 포인트 계산 및 차감
            int newPoint = myPoint - debt;
            userPoints.put(userId, newPoint);

            // 3. 칭호 복구를 위해 삭제 전에 기존 칭호 미리 백업
            String originalTitle = userTitles.get(userId);

            // 4. 빚 관련 데이터 삭제
            userDebt.remove(userId);
            debtDeadline.remove(userId);

            // 5. 닉네임 처리
            String cleanName = event.getMember().getEffectiveName().replaceAll("\\[.*?\\]", "").trim();

            // 6. 신분 및 닉네임 변경 (핵심 수정 구간)
            if (!event.getMember().isOwner()) {
                if (newPoint < 0) {
                    // [노예] 상태: 시트에 기록되도록 userTitles에 저장!
                    userTitles.put(userId, "노예");
                    event.getMember().modifyNickname("[노예] " + cleanName).queue();
                } else {
                    // 잔고가 0 이상: [노예] 상태에서 벗어남
                    // 1. 만약 [노예]였던 상태라면 시트에서도 삭제
                    if ("노예".equals(userTitles.get(userId))) {
                        userTitles.remove(userId);
                    }

                    // 2. 원래 칭호 복구 (이전 코드 활용)
                    if (originalTitle != null && !originalTitle.isEmpty()) {
                        event.getMember().modifyNickname("[" + originalTitle + "] " + cleanName).queue();
                    } else {
                        event.getMember().modifyNickname(cleanName).queue();
                    }
                }
            }

            // 7. 시트 저장 (이제 [노예]가 userTitles에 있으므로 시트에 확실히 등록됩니다!)
            DataManaGer.savePoints(userPoints, event.getGuild());
            DataManaGer.saveDebts(userDebt);
            DataManaGer.saveDeadlines(debtDeadline);
            DataManaGer.saveTitles(userTitles);

            // 8. 결과 메시지 출력
            if (newPoint < 0) {
                event.getChannel().sendMessage("⛓️ 빚 " + debt + " P를 상환했지만, 잔고가 마이너스이므로 **[노예]** 신분이 됩니다. 현재 잔고: **" + newPoint + " P**").queue();
            } else {
                event.getChannel().sendMessage("✅ 빚 " + debt + " P를 성공적으로 상환했습니다! 현재 잔고: **" + newPoint + " P**").queue();
            }
        }

}}