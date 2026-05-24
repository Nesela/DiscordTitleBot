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
    private int customTitlePrice = 300;

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
        String message = event.getMessage().getContentRaw();

        if (event.getAuthor().isBot()) {
            return;
        }

        // 디코방에 표시된 이름
        String currentNickname = event.getMember().getEffectiveName();
        // [칭호]를 지운 이름
        String pureName = currentNickname.replaceAll("\\[.*?\\]", "").trim();
        String nickName = pureName;

        //채팅 시 칭호 유통기한 검사 및 회수
        checkTitlse(event);

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
        if (currentNickname.contains("[") && currentNickname.contains("]")) {
            //관리자 통과
//            if (!event.getMember().isOwner() && !event.getMember().hasPermission(Permission.ADMINISTRATOR))

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
        String nickname = pureName;


        //명령어 종류
        if (message.equals("!명령어")) {
            event.getChannel().sendMessage("\uD83C\uDFAE [종겜방 봇 명령어 안내]\n" +
                    "💰 **포인트 & 도박**\n" +
                    "1. `!출첵` : 출석체크를 진행하여 포인트를 획득합니다.\n" +
                    "1. `!포인트` : 내 보유 포인트를 확인합니다.\n" +
                    "1. `!홀짝 [홀/짝] [금액]` : 포인트의 2배를 노리는 도박 게임!\n\n" +
                    "🏷️ **칭호 시스템**\n" +
                    " 칭호의 경우 14일의 유통기한이 존재합니다.\n" +
                    "1. `!칭호교체 칭호이름` : 보유중인 칭호에서 교체가 가능합니다.\n" +
                    "1. `!내칭호` : 내 칭호를 확인합니다.\n" +
                    "2. `!칭호상점` : 칭호 상점을 엽니다.").queue();
        }
        //출석체크
        if (message.equals("!출첵")) {
            //오늘날짜oogleSheetService.java
            java.time.LocalDate today = java.time.LocalDate.now();

            //유저 마지막 출석날짜
            java.time.LocalDate lastDate = lastCheckInDates.get(nickname);

            if (lastDate != null && lastDate.equals(today)) {
                event.getChannel().sendMessage(" **[" + chatName + "]**님, 출석체크는 **하루에 한 번**만 가능합니다! 내일 다시 와주세요.").queue();
                return;
            }

            int currentPoint = userPoints.getOrDefault(nickname, 0);
            int newPoint = currentPoint + 10;

            userPoints.put(nickname, newPoint);

            lastCheckInDates.put(nickname, today);

            DataManaGer.savePoints(userPoints);

            event.getChannel().sendMessage(" **[" + chatName + "]** 님이 출석체크를 완료하여 10포인트가 지급되었습니다.").queue();
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
            String currentData =userTitles.getOrDefault(nickname, "");

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
        if (message.startsWith("!포인트지급")) {
            // 1. 관리자 체크
            if (!event.getMember().isOwner()) {
                event.getChannel().sendMessage("서버 주인만 사용할 수 있는 기능입니다!").queue();
                return;
            }

            // 2. 명령어 형식 확인 (예: !포인트지급 @유저 1000)
            String[] parts = message.split(" ");
            if (parts.length < 3) {
                event.getChannel().sendMessage("사용법: `!포인트지급 [이름] [금액]`").queue();
                return;
            }

            String targetName = parts[1]; // 대상 이름
            int amount = Integer.parseInt(parts[2]); // 지급할 금액

            // 3. 포인트 지급 로직
            int currentPoints = userPoints.getOrDefault(targetName, 0);
            userPoints.put(targetName, currentPoints + amount);
            DataManaGer.savePoints(userPoints);

            event.getChannel().sendMessage(" **[" + targetName + "]**님께 **" + amount + " P** 지급 완료!").queue();
        }
    }
}