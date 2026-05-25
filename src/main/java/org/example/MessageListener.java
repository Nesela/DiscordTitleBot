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
    private HashMap<String, Integer> userPoints;
    private HashMap<String, String> userTitles;
    private HashMap<String, Integer> userDebt;
    private HashMap<String, Long> debtDeadline;

    private HashMap<String, LocalDate> lastCheckInDates = new HashMap<>();

    // 2. 생성자를 만들어 여기서 데이터를 로드합니다.
    public MessageListener() {
        this.userPoints = DataManaGer.loadPoints();
        this.userTitles = DataManaGer.loadTitles();
        this.userDebt = DataManaGer.loadDebts();
        this.debtDeadline = DataManaGer.loadDeadlines();

        // 만약 데이터가 하나도 없어서 null이 리턴된다면 빈 맵으로 초기화 (데이터 보호!)
        if (this.userPoints == null) this.userPoints = new HashMap<>();
        if (this.userTitles == null) this.userTitles = new HashMap<>();
        if (this.userDebt == null) this.userDebt = new HashMap<>();
        if (this.debtDeadline == null) this.debtDeadline = new HashMap<>();
    }

    // 가격표 변수는 그대로 두셔도 됩니다.
    private int publicTitlePrice = 100;
    private int customTitlePrice = 150;

    //유통기한 날짜생성
    private String getExpirationDate(int days) {
        return LocalDate.now().plusDays(days).format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    //만료 여부 확인
    private boolean isExpiresd(String dateString) {
        LocalDate expiry = LocalDate.parse(dateString, DateTimeFormatter.BASIC_ISO_DATE);
        return LocalDate.now().isAfter(expiry);
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
            event.getChannel().sendMessage("💰 **[" + chatName + "]** 님의 현재 잔고: **" + actualBalance + " P**" + status).queue();
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
            int myPoint = userPoints.getOrDefault(userId, 0); // ID로 조회
            String customTitle = message.substring(6).trim();

            if (customTitle.length() > 4) {
                event.getChannel().sendMessage("수제작 칭호는 **최대 4글자**까지만 가능합니다!").queue();
                return;
            }

            if (myPoint < customTitlePrice) {
                event.getChannel().sendMessage("포인트가 부족합니다!").queue();
                return;
            }

            String currentData = userTitles.getOrDefault(userId, "");
            if (currentData.contains(customTitle)) {
                event.getChannel().sendMessage("이미 보유하고 있는 칭호입니다!").queue();
                return;
            }

            String newEntry = customTitle + "|" + getExpirationDate(14);
            userTitles.put(userId, currentData.isEmpty() ? newEntry : currentData + "," + newEntry);
            userPoints.put(userId, myPoint - customTitlePrice);

            DataManaGer.savePoints(userPoints, event.getGuild());
            DataManaGer.saveTitles(userTitles);

            if (!event.getMember().isOwner()) {
                event.getMember().modifyNickname("[" + customTitle + "] " + pureName).queue();
            }
            event.getChannel().sendMessage(" **[" + pureName + "]**님의 장인 정신이 깃든 수제작 칭호가 완성되었습니다.").queue();
        }

// 칭호교환
        if (message.startsWith("!칭호교환 ")) {
            userId = event.getAuthor().getId();
            String newTitle = message.substring(6).trim();
            String currentData = userTitles.getOrDefault(userId, ""); // ID로 조회

            if (!currentData.contains(newTitle)) {
                event.getChannel().sendMessage("보유하지 않은 칭호입니다!").queue();
                return;
            }

            String[] entries = currentData.split(",");
            boolean isFound = false;
            for (String entry : entries) {
                String[] parts = entry.split("\\|");
                if (parts[0].equals(newTitle)) {
                    if (isExpiresd(parts[1])) {
                        event.getChannel().sendMessage(" [" + newTitle + "] 칭호는 이미 기간이 만료되었습니다.").queue();
                        return;
                    }
                    isFound = true;
                    break;
                }
            }

            if (!isFound) return;

            if (!event.getMember().isOwner()) {
                event.getMember().modifyNickname("[" + newTitle + "] " + pureName).queue();
            }
            event.getChannel().sendMessage(" 칭호를 **[" + newTitle + "]**(으)로 변경했습니다!").queue();
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
            int myPoint = userPoints.getOrDefault(userId, 0);
            int myDebt = userDebt.getOrDefault(userId, 0);
            int actualBalance = myPoint - myDebt;

            if (bet > actualBalance) {
                event.getChannel().sendMessage(" **[" + pureName + "]**님, 포인트가 부족합니다! (현재 실제 잔고: " + actualBalance + " P)").queue();
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

                sb.append(String.format("%d등: **%s** - %d P\n", i + 1, name, actualBalance));
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
                String currentName = event.getMember().getEffectiveName();

                // 1. 포인트 및 빚 업데이트
                userPoints.put(userId, userPoints.getOrDefault(userId, 0) + amount);
                userDebt.put(userId, userDebt.getOrDefault(userId, 0) + amount);
                // 3일 뒤 마감 시간(밀리초) 저장
                debtDeadline.put(userId, System.currentTimeMillis() + (3L * 24 * 60 * 60 * 1000));

                // 2. 칭호 및 닉네임 처리
                String newNickname = "[빚쟁이] " + currentName.replaceAll("\\[.*?\\]", "").trim();
                event.getMember().modifyNickname(newNickname).queue();
                userTitles.put(userId, "빚쟁이");

                // 3. 데이터 저장
                DataManaGer.savePoints(userPoints, event.getGuild());
                DataManaGer.saveDebts(userDebt);
                DataManaGer.saveDeadlines(debtDeadline);
                DataManaGer.saveTitles(userTitles);

                event.getChannel().sendMessage("💰 **" + amount + " P** 대출 완료! **[빚쟁이]**가 되셨습니다. 3일 안에 상환하세요!").queue();
            } catch (NumberFormatException e) {
                event.getChannel().sendMessage("❌ 금액을 숫자로 입력해주세요!").queue();
            } catch (Exception e) {
                event.getChannel().sendMessage("❌ 대출 오류: " + e.getMessage()).queue();
            }
        }

        if (message.equals("!상환")) {
            userId = event.getAuthor().getId();

            if (!userDebt.containsKey(userId)) {
                event.getChannel().sendMessage("❌ 상환할 빚이 없습니다!").queue();
                return;
            }

            int debt = userDebt.get(userId);
            int myPoint = userPoints.getOrDefault(userId, 0);

            if (myPoint < debt) {
                event.getChannel().sendMessage("❌ 포인트가 부족합니다. (필요: " + debt + " P, 현재: " + myPoint + " P)").queue();
                return;
            }

            // 1. 데이터 처리
            userPoints.put(userId, myPoint - debt);
            userDebt.remove(userId);
            debtDeadline.remove(userId);
            userTitles.remove(userId);

            // 2. 닉네임 복구 (순수 닉네임으로)
            String cleanName = event.getMember().getEffectiveName().replaceAll("\\[.*?\\]", "").trim();
            if (!event.getMember().isOwner()) {
                event.getMember().modifyNickname(cleanName).queue();
            }

            // 3. [중요] 시트 저장 (이 순서가 중요합니다)
            DataManaGer.savePoints(userPoints, event.getGuild());
            DataManaGer.saveDebts(userDebt);
            DataManaGer.saveDeadlines(debtDeadline);
            DataManaGer.saveTitles(userTitles);

            event.getChannel().sendMessage("✅ 빚 " + debt + " P를 상환하여 [빚쟁이] 칭호를 제거했습니다!").queue();
        }
        if (message.startsWith("!시트비우기 ")) {
            // [수정] 방장(Owner) OR 관리자(Administrator) 권한 체크
            boolean isStaff = event.getMember().isOwner() || event.getMember().hasPermission(Permission.ADMINISTRATOR);

            if (!isStaff) {
                event.getChannel().sendMessage("❌ 서버 운영진(방장/관리자)만 사용할 수 있습니다.").queue();
                return;
            }

            // 2. 명령어 확인용 (오타 방지)
            String target = message.substring(7).trim();
            if (!target.equals("확인")) {
                event.getChannel().sendMessage("⚠️ 정말 모든 데이터를 삭제하시겠습니까?\n맞다면 `!시트비우기 확인`을 입력하세요.").queue();
                return;
            }

            // 3. 모든 데이터 초기화
            userPoints.clear();
            userTitles.clear();
            userDebt.clear();
            debtDeadline.clear();

            // 4. 시트 비우기 (중요: 시트의 모든 영역을 깨끗하게 비웁니다)
            try {
                GoogleSheetService.clearValues("시트1!A2:H100");
                event.getChannel().sendMessage("✅ 운영진 명령으로 모든 데이터가 초기화되었습니다!").queue();
            } catch (Exception e) {
                event.getChannel().sendMessage("⚠️ 시트 초기화 중 오류가 발생했으나 메모리는 비웠습니다: " + e.getMessage()).queue();
            }
        }
    }

}