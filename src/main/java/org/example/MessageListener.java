package org.example;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import javax.xml.crypto.Data;
import java.util.HashMap;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class MessageListener extends ListenerAdapter {
    //포인트 저장소
    private HashMap<String, Integer> userPoints = DataManaGer.loadPoints();
    //칭호 저장소
    private HashMap<String, String> userTitles = DataManaGer.loadTitles();
    //마지막 출석체크 날짜
    private HashMap<String, java.time.LocalDate> lastCheckInDates = new HashMap<>();

    private HashMap<String, Integer> userDebt = DataManaGer.loadDebts();
    private HashMap<String, LocalDate> debtDeadline = DataManaGer.loadDeadlines();
    //가격표 변수 기본값
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
        String currentNickname = event.getMember().getEffectiveName();
        String pureName = currentNickname.replaceAll("\\[.*?\\]", "").trim();
        String nickname = pureName; // 이게 가방 키로 쓰입니다.

        if (!userTitles.containsKey(nickname)) return;

        String data = userTitles.get(nickname);
        String[] entries = data.split(",");
        StringBuilder newInventory = new StringBuilder();
        boolean isChanged = false;

        for (String entry : entries) {
            //데이터 안전장치
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
            if (newInventory.length() == 0) userTitles.remove(nickname);
            else userTitles.put(nickname, newInventory.toString());

            DataManaGer.saveTitles(userTitles);

            if (!event.getMember().isOwner()) {
                event.getMember().modifyNickname(pureName).queue();
            }
        }
        // 만기일 확인 및 강제 마이너스 처리
        if (debtDeadline.containsKey(nickname) && LocalDate.now().isAfter(debtDeadline.get(nickname))) {
            int points = userPoints.getOrDefault(nickname, 0);
            int debt = userDebt.get(nickname);

            // 1. 포인트에서 빚을 뺌 (결과가 마이너스가 됨)
            userPoints.put(nickname, points - debt);

            // 2. 빚/만기일 기록 삭제
            userDebt.remove(nickname);
            debtDeadline.remove(nickname);

            DataManaGer.saveDebts(userDebt);
            DataManaGer.saveDeadlines(debtDeadline);

            // 3. 칭호 변경
            event.getMember().modifyNickname("[노예] " + pureName).queue();
            event.getChannel().sendMessage("⛓️ **[" + nickname + "]**님의 대출 만기일이 지나 모든 자산이 압류되었습니다. 현재 잔고: **" + (points - debt) + " P**").queue();

            DataManaGer.savePoints(userPoints);
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        String message = event.getMessage().getContentRaw();
        String currentNickname = event.getMember().getEffectiveName();
        String pureName = currentNickname.replaceAll("\\[.*?\\]", "").trim();
        String nickname = pureName;

        boolean isAdmin = event.getMember() != null && event.getMember().hasPermission(Permission.ADMINISTRATOR);

        if (message.startsWith("!포인트지급 ")) { // 한 칸 띄우고 시작
            // 관리자 권한 확인
            boolean isStaff = event.getMember().isOwner() || event.getMember().hasPermission(Permission.ADMINISTRATOR);
            if (!isStaff) {
                event.getChannel().sendMessage("서버 운영진만 사용할 수 있는 기능입니다!").queue();
                return;
            }

            // 명령어에서 명령어 부분을 제외하고 추출
            String content = message.substring("!포인트지급 ".length()).trim();

            // 마지막 공백을 찾아 이름과 금액을 분리
            int lastSpaceIndex = content.lastIndexOf(" ");
            if (lastSpaceIndex == -1) {
                event.getChannel().sendMessage("사용법: `!포인트지급 [닉네임] [금액]`").queue();
                return;
            }

            String targetName = content.substring(0, lastSpaceIndex); // 중간에 띄어쓰기가 있어도 다 합쳐짐
            String amountStr = content.substring(lastSpaceIndex + 1); // 마지막 단어는 금액

            try {
                int amount = Integer.parseInt(amountStr);

                // 포인트 지급 로직
                int currentPoints = userPoints.getOrDefault(targetName, 0);
                userPoints.put(targetName, currentPoints + amount);
                DataManaGer.savePoints(userPoints);

                event.getChannel().sendMessage(" **[" + targetName + "]**님께 **" + amount + " P** 지급 완료!").queue();

            } catch (NumberFormatException e) {
                event.getChannel().sendMessage(" **[오류]** 마지막에 입력한 **" + amountStr + "**은(는) 숫자가 아닙니다! 금액을 확인해주세요.").queue();
            }
        }

        if (message.startsWith("!유저삭제 ")) {
            // 관리자/서버주인 권한 확인
            if (!event.getMember().isOwner() && !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
                event.getChannel().sendMessage("서버 운영진만 사용할 수 있습니다.").queue();
                return;
            }

            String targetName = message.substring(6).trim(); // "!유저삭제 " 제외하고 이름 추출

            // 1. 포인트 삭제
            if (userPoints.containsKey(targetName)) {
                userPoints.remove(targetName);
                DataManaGer.savePoints(userPoints); // 포인트 파일 업데이트
            }

            // 2. 칭호 삭제
            if (userTitles.containsKey(targetName)) {
                userTitles.remove(targetName);
                DataManaGer.saveTitles(userTitles); // 칭호 파일 업데이트
            }

            event.getChannel().sendMessage("✅ **" + targetName + "**님의 모든 데이터(포인트/칭호)가 삭제되었습니다.").queue();
        }

        if (!isAdmin) {
            checkTitlse(event);
            // [중복 닉네임 로직 생략: 기존 코드의 도용 방지 로직을 여기에 배치하세요]
        }

        if (event.getAuthor().isBot()) {
            return;
        }

        // 칭호 유무 확인
        String realTitle = userTitles.get(pureName);

        String chatName = currentNickname;

        if (realTitle != null && !realTitle.isEmpty()) {
            if (!currentNickname.contains("[") || !currentNickname.contains("]")) {
                String firstTitle = realTitle.split(",")[0].split("\\|")[0];
                chatName = "[" + firstTitle + "] " + pureName;
            }
        }

        //별명변경으로 인한 칭호 검사
        if (!isAdmin) {
            if (currentNickname.contains("[") && currentNickname.contains("]")) {

                String tagInNickname = currentNickname.substring(currentNickname.indexOf("[") + 1, currentNickname.indexOf("]"));

                // [수정] 빚쟁이 태그는 검거 대상에서 제외합니다!
                if (tagInNickname.equals("빚쟁이")) {
                    return;
                }

                if (realTitle == null || !realTitle.contains(tagInNickname)) {
                    // ... (기존 검거 로직 동일)
                    String targetNickname;
                    String alertMessage;

                    if (realTitle != null && !realTitle.isEmpty()) {
                        String firstTitle = realTitle.split(",")[0].split("\\|")[0];
                        targetNickname = "[" + firstTitle + "] " + pureName;
                        alertMessage = " **[" + pureName + "]**님, 구매하지 않은 칭호를 도용하셨습니다! 보유 중인 진짜 칭호 **[" + firstTitle + "]**로 변경됩니다.";
                    } else {
                        targetNickname = pureName;
                        alertMessage = " **[" + pureName + "]**님, 구매하지 않은 칭호를 무단 도용하여 닉네임이 강제 리셋되었습니다! 칭호는 상점에서 구매해 주세요.";
                    }

                    if (!event.getMember().isOwner()) {
                        event.getMember().modifyNickname(targetNickname).queue();
                    }
                    event.getChannel().sendMessage(alertMessage).queue();
                    return;
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
        //출석체크
        if (message.equals("!출첵")) {
            java.time.LocalDate today = java.time.LocalDate.now();
            java.time.LocalDate lastDate = lastCheckInDates.get(nickname);

            if (lastDate != null && lastDate.equals(today)) {
                event.getChannel().sendMessage(" **[" + chatName + "]**님, 출석체크는 **하루에 한 번**만 가능합니다! 내일 다시 와주세요.").queue();
                return;
            }

            // 1. 신규/기존 확인 후 포인트 계산
            int bonus = 0;
            String msg = " **[" + chatName + "]** 님이 출석체크를 완료하여 15포인트가 지급되었습니다.";

            if (!userPoints.containsKey(nickname)) {
                bonus = 100;
                msg = " **[" + chatName + "]** 님, 첫 출첵! 보너스 100포인트 포함 **115포인트**가 지급되었습니다.";
            }

            int currentPoint = userPoints.getOrDefault(nickname, 0);
            int newPoint = currentPoint + 15 + bonus; // 15(기본) + 보너스(신규일 때 105)

            userPoints.put(nickname, newPoint);
            lastCheckInDates.put(nickname, today);
            DataManaGer.savePoints(userPoints);

            event.getChannel().sendMessage(msg).queue();
            return; // 로직 끝

        }
        //포인트 확인
        if (message.equals("!포인트")) {
            int myPoint = userPoints.getOrDefault(nickname, 0);
            int myDebt = userDebt.getOrDefault(nickname, 0);
            int actualBalance = myPoint - myDebt; // 빚을 제외한 진짜 잔고

            String status = (myDebt > 0) ? " (빚: " + myDebt + " P)" : "";
            event.getChannel().sendMessage("💰 **[" + chatName + "]** 님의 현재 잔고: **" + actualBalance + " P**" + status).queue();
        }
        //칭호 종류 가격 확인
        if (message.equals("!칭호상점")) {
            event.getChannel().sendMessage(" **[칭호 상점]**\n\n" +
                    " **[1. 공용 칭호 상점]** - 가격: `" + publicTitlePrice + " P`\n" +
                    " `1. !칭호구매 짱짱시루` \n `2. !칭호구매 고인물`\n\n" +
                    " **[2. 나만의 수제작 칭호]** - 가격: `" + customTitlePrice + " P`\n" +
                    " `!칭호제작 원하는글자`").queue();
        }
        //고정값 칭호
        if (message.startsWith("!칭호구매 ")) {
            int myPoint = userPoints.getOrDefault(nickname, 0);
            String choice = message.substring(6).trim();

            if (!choice.equals("짱짱시루") && !choice.equals("고인물")) {
                event.getChannel().sendMessage(" **[" + nickname + "]**님, 상점에 없는 칭호입니다! `!칭호상점`을 확인해 주세요. ").queue();

                return;
            }

            if (myPoint < publicTitlePrice) {
                event.getChannel().sendMessage(" **[" + nickname + "]**님, 포인트가 부족합니다. ").queue();
                return;
            }

            if (!event.getMember().isOwner()) {
                event.getMember().modifyNickname("[" + choice + "] " + pureName).queue();
            }

            event.getChannel().sendMessage(" **[" + nickname + "]**님이 공용 칭호 **[" + choice + "]**를 구매하셨습니다.").queue();

            String currentData = userTitles.getOrDefault(nickname, "");
            String newEntry = choice + "|" + getExpirationDate(14);

            //칭호 중복구매 체크
            if (currentData.contains(choice)) {
                event.getChannel().sendMessage("이미 보유하고 있는 칭호입니다!").queue();
                return;
            }

            //칭호 가방에넣기
            if (currentData.isEmpty()) {
                userTitles.put(nickname, newEntry);
            } else {
                userTitles.put(nickname, currentData + "," + newEntry);
            }

            //포인트 차감 및 저장
            userPoints.put(nickname, myPoint - publicTitlePrice);
            DataManaGer.savePoints(userPoints);
            DataManaGer.saveTitles(userTitles);
        }
        //수제작 칭호
        if (message.startsWith("!칭호제작 ")) {
            int myPoint = userPoints.getOrDefault(nickname, 0);
            String customTitle = message.substring(6).trim();

            //글자수, 중복 체크
            if (customTitle.length() > 4) {
                event.getChannel().sendMessage(" **[" + nickname + "]**님, 수제작 칭호는 **최대 4글자**까지만 가능합니다! (입력한 글자 수: " + customTitle.length() + "자)").queue();
                return; // 💡 여기서 튕겨내야 아래 포인트 차감 코드로 안 내려갑니다.
            }

            if (myPoint < customTitlePrice) {
                event.getChannel().sendMessage(" **[" + nickname + "]**님, 포인트가 부족합니다! (수제작비: " + customTitlePrice + " P / 현재 자산: " + myPoint + " P)").queue();
                return;
            }

            //칭호 중복구매 체크
            String currentData = userTitles.getOrDefault(nickname, "");
            if (currentData.contains(customTitle)) {
                event.getChannel().sendMessage("이미 보유하고 있는 칭호입니다!").queue();
                return;
            }

            String newEntry = customTitle + "|" + getExpirationDate(14);
            if (currentData.isEmpty()) {
                userTitles.put(nickname, newEntry);
            } else {
                userTitles.put(nickname, currentData + "," + newEntry);
            }

            //변수저장
            userPoints.put(nickname, myPoint - customTitlePrice);
            DataManaGer.savePoints(userPoints);
            DataManaGer.saveTitles(userTitles);

            if (!event.getMember().isOwner()) {
                event.getMember().modifyNickname("[" + customTitle + "] " + pureName).queue();
            }
            event.getChannel().sendMessage(" **[" + nickname + "]**님의 장인 정신이 깃든 수제작 칭호가 완성되었습니다.").queue();
        }

        if (message.startsWith("!칭호교환 ")) {
            String newTitle = message.substring(6).trim();
            String currentData = userTitles.getOrDefault(nickname, "");

            // 칭호 보유 확인
            if (!currentData.contains(newTitle)) {
                event.getChannel().sendMessage(" **[" + nickname + "]**님, 보유하지 않은 칭호입니다!").queue();
                return;
            }
            // 유통기한 체크
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

            if (!isFound) {
                event.getChannel().sendMessage("보유중이신 칭호가 아닙니다.").queue();
                return;
            }

            if (!event.getMember().isOwner()) {
                event.getMember().modifyNickname("[" + newTitle + "] " + pureName).queue();
            }

            event.getChannel().sendMessage(" 칭호를 **[" + newTitle + "]**(으)로 변경했습니다!").queue();
        }
        //내칭호 확인
        if (message.equals("!내칭호")) {
            String currentData = userTitles.getOrDefault(nickname, "");

            if (currentData.isEmpty()) {
                event.getChannel().sendMessage(" **[" + nickname + "]**님, 아직 보유한 칭호가 없습니다! 상점에서 구매해 보세요.").queue();
                return;
            }
            StringBuilder sb = new StringBuilder();
            sb.append(" \uD83C\uDFAE **[" + nickname + "]님의 칭호 가방**\n\n");

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
        //도박 홀짝
        if (message.startsWith("!홀짝")) {
            String[] parts = message.split(" ");
            if (parts.length < 3) {
                event.getChannel().sendMessage(" 사용법: `!홀짝 [홀/짝] [금액]`").queue();
                return;
            }

            String choice = parts[1]; // 홀 또는 짝
            int bet = 0;
            try {
                bet = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                event.getChannel().sendMessage(" 금액은 숫자로 입력해 주세요!").queue();
                return;
            }

            // [추가된 핵심 보안 로직]
            if (bet <= 0) {
                event.getChannel().sendMessage(" ❌ **금액은 1 P 이상으로만 배팅 가능합니다!**").queue();
                return;
            }

            if (!choice.equals("홀") && !choice.equals("짝")) {
                event.getChannel().sendMessage(" `홀` 또는 `짝` 중에서 선택해 주세요!").queue();
                return;
            }

            // [수정된 부분] 빚을 제외한 실제 잔고 계산
            int myPoint = userPoints.getOrDefault(nickname, 0);
            int myDebt = userDebt.getOrDefault(nickname, 0);
            int actualBalance = myPoint - myDebt;

            if (bet > actualBalance) {
                event.getChannel().sendMessage(" **[" + nickname + "]**님, 포인트가 부족합니다! (현재 실제 잔고: " + actualBalance + " P)").queue();
                return;
            }

            // 진짜 홀짝 결정: 0은 짝, 1은 홀
            int result = (int) (Math.random() * 2);
            String resultStr = (result == 0) ? "짝" : "홀";
            boolean isWin = (choice.equals(resultStr));

            if (isWin) {
                // 이겼을 때
                userPoints.put(nickname, myPoint + bet);
                event.getChannel().sendMessage(" \uD83C\uDF89 **[정답!]** 결과: **[" + resultStr + "]**\n" +
                        "배팅액: " + bet + " P\n" +
                        "현재 자산: **" + (myPoint + bet) + " P**").queue();
            } else {
                // 졌을 때
                userPoints.put(nickname, myPoint - bet);
                event.getChannel().sendMessage(" \uD83D\uDC80 **[실패!]** 결과: **[" + resultStr + "]**\n" +
                        "배팅액: " + bet + " P (손실)\n" +
                        "현재 자산: **" + (myPoint - bet) + " P**").queue();
            }

            DataManaGer.savePoints(userPoints);
        }
        //랭킹포인트
        if (message.equals("!랭킹")) {
            if (userPoints.isEmpty()) {
                event.getChannel().sendMessage("아직 포인트 데이터가 없습니다!").queue();
                return;
            }

            // 1. Map을 List로 변환
            java.util.List<java.util.Map.Entry<String, Integer>> ranking = new java.util.ArrayList<>(userPoints.entrySet());

            // 2. [수정] 실제 잔고(포인트 - 빚)가 높은 순으로 정렬
            ranking.sort((o1, o2) -> {
                int balance1 = o1.getValue() - userDebt.getOrDefault(o1.getKey(), 0);
                int balance2 = o2.getValue() - userDebt.getOrDefault(o2.getKey(), 0);
                return Integer.compare(balance2, balance1); // 내림차순
            });

            // 3. 상위 10명 표시
            StringBuilder sb = new StringBuilder();
            sb.append("\uD83C\uDFC6 **[종겜방 실제 잔고 랭킹 (Top 10)]**\n\n");

            for (int i = 0; i < Math.min(ranking.size(), 10); i++) {
                java.util.Map.Entry<String, Integer> entry = ranking.get(i);
                String name = entry.getKey();
                int actualBalance = entry.getValue() - userDebt.getOrDefault(name, 0);

                sb.append(String.format("%d등: **%s** - %d P\n", i + 1, name, actualBalance));
            }

            event.getChannel().sendMessage(sb.toString()).queue();
            return;
        }
        //선물하기
        //선물하기
        if (message.startsWith("!선물")) {

            if (message.trim().equals("!선물")) {
                event.getChannel().sendMessage("사용법: `!선물 [받을사람] [금액]`").queue();
                return;
            }

            String content = message.substring(3).trim();
            int lastSpaceIndex = content.lastIndexOf(" ");

            if (lastSpaceIndex == -1) {
                event.getChannel().sendMessage("사용법: `!선물 [받을사람] [금액]`").queue();
                return;
            }

            String senderName = pureName;
            String receiverName = content.substring(0, lastSpaceIndex).trim();
            String amountStr = content.substring(lastSpaceIndex + 1);

            try {
                int amount = Integer.parseInt(amountStr);

                // [수정된 부분] 빚을 제외한 실제 잔고 계산
                int senderPoints = userPoints.getOrDefault(senderName, 0);
                int myDebt = userDebt.getOrDefault(senderName, 0);
                int actualBalance = senderPoints - myDebt;

                // 예외 처리
                if (amount <= 0) {
                    event.getChannel().sendMessage("금액은 1 이상이어야 합니다.").queue();
                    return;
                }
                if (senderName.equals(receiverName)) {
                    event.getChannel().sendMessage("자기 자신에게는 선물할 수 없습니다!").queue();
                    return;
                }

                // [수정된 부분] 보유 포인트 대신 실제 잔고와 비교
                if (actualBalance < amount) {
                    event.getChannel().sendMessage("포인트가 부족합니다! (현재 실제 잔고: " + actualBalance + " P, 빚: " + myDebt + " P)").queue();
                    return;
                }

                // 유저 찾기 로직
                boolean userFound = false;
                for (String key : userPoints.keySet()) {
                    if (key.replaceAll("\\s+", "").equalsIgnoreCase(receiverName.replaceAll("\\s+", ""))) {
                        receiverName = key;
                        userFound = true;
                        break;
                    }
                }

                if (!userFound) {
                    event.getChannel().sendMessage("서버에 존재하지 않는 유저입니다.").queue();
                    return;
                }

                // 포인트 이동
                userPoints.put(senderName, senderPoints - amount);
                userPoints.put(receiverName, userPoints.getOrDefault(receiverName, 0) + amount);

                DataManaGer.savePoints(userPoints);

                event.getChannel().sendMessage("🎁 **" + senderName + "**님이 **" + receiverName + "**님에게 **" + amount + " P**를 선물했습니다!").queue();

            } catch (NumberFormatException e) {
                event.getChannel().sendMessage("금액은 **숫자**로만 입력해주세요.").queue();
            }
        }
        //대출
        if (message.startsWith("!대출")) { // !대출 100 처럼 뒤에 공백이 있을 때
            // [추가] 명령어만 입력하고 뒤에 아무것도 없을 때 처리
            if (message.trim().equals("!대출")) {
                event.getChannel().sendMessage("사용법: `!대출 [금액]`을 입력해주세요. (최대 100 P)").queue();
                return;
            }

            if (currentNickname.contains("[빚쟁이]")) {
                event.getChannel().sendMessage("이미 대출 중이라 [빚쟁이] 상태입니다!").queue();
                return;
            }

            String amountStr = message.substring(4).trim();
            int loanAmount;
            try {
                loanAmount = Integer.parseInt(amountStr);
            } catch (NumberFormatException e) {
                event.getChannel().sendMessage("금액은 숫자로만 입력해주세요!").queue();
                return;
            }

            // [기존 로직들...]
            if (loanAmount <= 0) {
                event.getChannel().sendMessage("❌ 1보다 큰 금액만 대출할 수 있습니다.").queue();
                return;
            }
            if (loanAmount > 100) {
                event.getChannel().sendMessage("⚠️ 대출은 최대 100 P까지 가능합니다!").queue();
                return;
            }

            if (userDebt.containsKey(nickname)) {
                event.getChannel().sendMessage("❌ 이미 대출 중입니다! 상환 후 이용해주세요.").queue();
                return;
            }

            // ... 이하 동일 (포인트 지급 및 저장 로직)
            int currentPoints = userPoints.getOrDefault(nickname, 0);
            int newTotal = currentPoints + loanAmount;

            userPoints.put(nickname, newTotal);
            userDebt.put(nickname, loanAmount);
            debtDeadline.put(nickname, LocalDate.now().plusDays(3));

            DataManaGer.savePoints(userPoints);
            DataManaGer.saveDebts(userDebt);
            DataManaGer.saveDeadlines(debtDeadline);

            event.getMember().modifyNickname("[빚쟁이] " + pureName).queue();
            event.getChannel().sendMessage("💰 **" + loanAmount + " P**가 대출되었습니다!").queue();
        }
        //상환하기
        if (message.equals("!상환")) {
            int myDebt = userDebt.getOrDefault(nickname, 0);
            int myPoints = userPoints.getOrDefault(nickname, 0);

            if (myDebt == 0) {
                event.getChannel().sendMessage("갚을 빚이 없습니다!").queue();
                return;
            }

            if (myPoints < myDebt) {
                event.getChannel().sendMessage("포인트가 부족합니다! 빚: " + myDebt + " P").queue();
                return;
            }

            // 갚기 완료
            userPoints.put(nickname, myPoints - myDebt);
            userDebt.remove(nickname);
            debtDeadline.remove(nickname);

            // [저장 코드 추가]
            DataManaGer.savePoints(userPoints);
            DataManaGer.saveDebts(userDebt);
            DataManaGer.saveDeadlines(debtDeadline);

            String currentTitles = userTitles.getOrDefault(nickname, "");
            String targetNickname;

            if (!currentTitles.isEmpty()) {
                // 가방에서 첫 번째 칭호를 가져와 복구
                String firstTitle = currentTitles.split(",")[0].split("\\|")[0];
                targetNickname = "[" + firstTitle + "] " + pureName;
            } else {
                // 보유한 칭호가 없으면 그냥 원래 이름으로
                targetNickname = pureName;
            }

            // 3. 닉네임 변경 및 완료 메시지
            if (!event.getMember().isOwner()) {
                event.getMember().modifyNickname(targetNickname).queue();
            }
            event.getChannel().sendMessage("✅ 빚을 모두 갚았습니다! 자유의 몸이 되셨군요. 칭호를 **" + targetNickname + "**(으)로 복구했습니다.").queue();
        }

    }
}
