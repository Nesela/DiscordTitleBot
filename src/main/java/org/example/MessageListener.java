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
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        String message = event.getMessage().getContentRaw();
        String currentNickname = event.getMember().getEffectiveName();
        String pureName = currentNickname.replaceAll("\\[.*?\\]", "").trim();
        String nickname = pureName; //

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

        //별명변겅으로 인한 칭호 검사
        if (!isAdmin) {
            if (currentNickname.contains("[") && currentNickname.contains("]")) {

                String tagInNickname = currentNickname.substring(currentNickname.indexOf("[") + 1, currentNickname.indexOf("]"));

                if (realTitle == null || !realTitle.contains(tagInNickname)) {
                    String targetNickname;
                    String alertMessage;

                    if (realTitle != null && !realTitle.isEmpty()) {
                        // 가방에 있는것중 첫번째 칭호로 복구
                        String firstTitle = realTitle.split(",")[0].split("\\|")[0];
                        targetNickname = "[" + firstTitle + "] " + pureName;
                        alertMessage = " **[" + pureName + "]**님, 구매하지 않은 칭호를 도용하셨습니다! 보유 중인 진짜 칭호 **[" + firstTitle + "]**로 변경됩니다.";
                    } else {
                        //칭호가 없을경우 원래별명으로 변경
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
                    "1. `!랭킹` : 현재 보유 포인트 랭킹을 확인합니다.\n\n" +
                    "1. `!선물` : 포인트의 선물이 가능합니다.\n\n" +
                    "1. `!홀짝 [홀/짝] [금액]` : 포인트의 2배를 노리는 도박 게임!\n\n" +
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

            event.getChannel().sendMessage(" **[" + chatName + "]** 님의 자산 " + myPoint + " P 보유 중입니다.").queue();
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

            if (!choice.equals("홀") && !choice.equals("짝")) {
                event.getChannel().sendMessage(" `홀` 또는 `짝` 중에서 선택해 주세요!").queue();
                return;
            }

            int myPoint = userPoints.getOrDefault(nickname, 0);
            if (bet > myPoint) {
                event.getChannel().sendMessage(" **[" + nickname + "]**님, 포인트가 부족합니다!").queue();
                return;
            }

            // 진짜 홀짝 결정: 0은 짝, 1은 홀
            int result = (int) (Math.random() * 2);
            String resultStr = (result == 0) ? "짝" : "홀";
            boolean isWin = (choice.equals(resultStr));

            if (isWin) {
                // 이겼을 때: 기존 자산에 배팅액만큼 더해줌 (결과적으로 배팅액 2배를 가져감)
                userPoints.put(nickname, myPoint + bet);

                event.getChannel().sendMessage(" \uD83C\uDF89 **[정답!]** 결과: **[" + resultStr + "]**\n" +
                        "배팅액: " + bet + " P\n" +
                        "보상액: " + (bet * 2) + " P (원금 + 수익)\n" +
                        "현재 자산: **" + (myPoint + bet) + " P**").queue();
            } else {
                // 졌을 때: 기존 자산에서 배팅액만큼 뺌
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

            // 1. Map을 List로 변환하여 점수 순으로 정렬
            java.util.List<java.util.Map.Entry<String, Integer>> ranking = new java.util.ArrayList<>(userPoints.entrySet());
            ranking.sort((o1, o2) -> o2.getValue().compareTo(o1.getValue())); // 높은 순 정렬

            // 2. 상위 10명만 뽑아서 메시지 작성
            StringBuilder sb = new StringBuilder();
            sb.append("\uD83C\uDFC6 **[종겜방 포인트 부자 랭킹 (Top 10)]**\n\n");

            for (int i = 0; i < Math.min(ranking.size(), 10); i++) {
                java.util.Map.Entry<String, Integer> entry = ranking.get(i);
                sb.append(String.format("%d등: **%s** - %d P\n", i + 1, entry.getKey(), entry.getValue()));
            }

            event.getChannel().sendMessage(sb.toString()).queue();
            return;
        }
        if (message.startsWith("!선물 ")) {
            // 1. 명령어 형식 체크 (공백 기준으로 자르기)
            String content = message.substring(4).trim(); // "!선물 " 제외
            int lastSpaceIndex = content.lastIndexOf(" ");

            if (lastSpaceIndex == -1) {
                event.getChannel().sendMessage("사용법: `!선물 [받을사람] [금액]`").queue();
                return;
            }

            String senderName = event.getMember().getEffectiveName(); // 보내는 사람
            String receiverName = content.substring(0, lastSpaceIndex); // 받을 사람 (띄어쓰기 포함)
            String amountStr = content.substring(lastSpaceIndex + 1); // 금액

            try {
                int amount = Integer.parseInt(amountStr);
                int senderPoints = userPoints.getOrDefault(senderName, 0);

                // 2. 예외 처리
                if (amount <= 0) {
                    event.getChannel().sendMessage("금액은 1 이상이어야 합니다.").queue();
                    return;
                }
                if (senderName.equals(receiverName)) {
                    event.getChannel().sendMessage("자기 자신에게는 선물할 수 없습니다!").queue();
                    return;
                }
                if (senderPoints < amount) {
                    event.getChannel().sendMessage("포인트가 부족합니다! (현재 보유: " + senderPoints + " P)").queue();
                    return;
                }
                if (!userPoints.containsKey(receiverName)) {
                    event.getChannel().sendMessage("서버에 존재하지 않는 유저입니다.").queue();
                    return;
                }

                // 3. 포인트 이동 및 저장
                userPoints.put(senderName, senderPoints - amount);
                userPoints.put(receiverName, userPoints.getOrDefault(receiverName, 0) + amount);

                DataManaGer.savePoints(userPoints); // 파일 저장

                event.getChannel().sendMessage("🎁 **" + senderName + "**님이 **" + receiverName + "**님에게 **" + amount + " P**를 선물했습니다!").queue();

            } catch (NumberFormatException e) {
                event.getChannel().sendMessage("금액은 **숫자**로만 입력해주세요.").queue();
            }
        }

    }
}
