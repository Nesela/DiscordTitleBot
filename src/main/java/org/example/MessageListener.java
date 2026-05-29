package org.example;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.events.guild.member.update.GuildMemberUpdateNicknameEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;


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
    private java.util.Random random = new java.util.Random();
    private HashMap<String, Long> workCooldowns = new HashMap<>();
    private HashMap<String, Integer> pickaxeLevels = new HashMap<>(); // 유저별 곡괭이 레벨
    private HashMap<String, Integer> protectionTickets = new HashMap<>(); // 유저별 보유한 강보권 수

    private HashMap<String, LocalDate> lastCheckInDates = new HashMap<>();
    // 가격표 변수는 그대로 두셔도 됩니다.
    private int publicTitlePrice = 100;
    private int customTitlePrice = 150;

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

        HashMap<String, Integer> loadedLevels = DataManaGer.loadPickaxeLevels();
        if (loadedLevels != null) this.pickaxeLevels = loadedLevels;

        // 보호권 불러오기
        HashMap<String, Integer> loadedTickets = DataManaGer.loadProtectionTickets();
        if (loadedTickets != null) this.protectionTickets = loadedTickets;
    }

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
        String message = event.getMessage().getContentRaw();
        String userId = event.getAuthor().getId();

        // 1. 멤버 객체를 변수에 담아둡니다.
        net.dv8tion.jda.api.entities.Member member = event.getMember();

        // 2. 멤버가 있을 때만 캐시를 갱신합니다.
        if (member != null) {
            DataManaGer.nicknameCache.put(event.getAuthor().getId(), member.getEffectiveName());
        }

        // 3. 변수 선언 시 member가 null인지 체크(삼항 연산자)하여 봇이 죽지 않게 합니다.


        // member가 있으면 닉네임, 없으면 유저 이름(getName) 사용
        String currentNickname = (member != null) ? member.getEffectiveName() : event.getAuthor().getName();
        String pureName = currentNickname.replaceAll("\\[.*?\\]", "").trim();

        boolean isAdmin = (member != null) && member.hasPermission(Permission.ADMINISTRATOR);


        if (message.startsWith("!포인트지급 ")) {
            boolean isStaff = event.getMember().isOwner() || event.getMember().hasPermission(Permission.ADMINISTRATOR);
            if (!isStaff) {
                event.getChannel().sendMessage("서버 운영진만 사용할 수 있는 기능입니다!").queue();
                return;
            }

            String content = message.substring("!포인트지급 ".length()).trim();
            int lastSpaceIndex = content.lastIndexOf(" ");
            if (lastSpaceIndex == -1) {
                event.getChannel().sendMessage("사용법: `!포인트지급 [닉네임 or @멘션] [금액]`").queue();
                return;
            }

            String targetQuery = content.substring(0, lastSpaceIndex).trim(); // 닉네임 or 멘션
            String amountStr = content.substring(lastSpaceIndex + 1);

            try {
                int amount = Integer.parseInt(amountStr);
                String targetUserId = null;

                // 1. @멘션이 있다면 바로 ID 추출
                if (!event.getMessage().getMentions().getMembers().isEmpty()) {
                    targetUserId = event.getMessage().getMentions().getMembers().get(0).getId();
                } else {
                    // 2. 멘션이 없으면 닉네임 검색 (태그 제거 후 비교)
                    // 변수명을 member 대신 m으로 변경했습니다
                    for (net.dv8tion.jda.api.entities.Member m : event.getGuild().getMembers()) {
                        // 안쪽 코드도 m. 으로 수정!
                        String cleanName = m.getEffectiveName().replaceAll("\\[.*?\\]", "").trim();

                        if (cleanName.equalsIgnoreCase(targetQuery)) {
                            targetUserId = m.getId();
                            break;
                        }
                    }
                }

                if (targetUserId == null) {
                    event.getChannel().sendMessage("❌ 서버에서 해당 유저를 찾을 수 없습니다. (정확한 닉네임을 입력하거나 @멘션을 사용하세요)").queue();
                    return;
                }

                // 포인트 지급
                int currentPoints = userPoints.getOrDefault(targetUserId, 0);
                userPoints.put(targetUserId, currentPoints + amount);
                DataManaGer.savePoints(userPoints, event.getGuild());

                event.getChannel().sendMessage("✅ **ID: " + targetUserId + "**님께 **" + amount + " P** 지급 완료! (현재 잔고: " + (currentPoints + amount) + " P)").queue();

            } catch (NumberFormatException e) {
                event.getChannel().sendMessage("❌ 금액을 숫자로 입력해주세요.").queue();
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
            for (net.dv8tion.jda.api.entities.Member m : event.getGuild().getMembers()) {
                // 안쪽에서 쓰는 변수도 똑같이 'm'으로 바꿔줘야 합니다
                if (m.getEffectiveName().contains(targetQuery)) {
                    targetUserId = m.getId();
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
                    "⛏️ **채굴 & 강화**\n" +
                    "6. `!채굴` : 광석을 캐서 포인트를 벌거나 강보권을 획득합니다.\n" +
                    "7. `!강화` : 포인트를 소모해 곡괭이 레벨을 올립니다. (실패 시 레벨 하락)\n" +
                    "8. `!내정보` : 곡괭이 레벨, 보유중인 강보권 을 확인합니다.\n\n" +
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

        if (message.equals("!내정보")) {
            userId = event.getAuthor().getId();
            int level = pickaxeLevels.getOrDefault(userId, 1);
            int tickets = protectionTickets.getOrDefault(userId, 0);

            String infoMsg = "👤 **" + event.getMember().getEffectiveName() + "님의 장비 정보**\n" +
                    "-----------------------------\n" +
                    "⛏️ 곡괭이 레벨: **" + level + " 레벨**\n" +
                    "🛡️ 보유 강보권: **" + tickets + " 장**\n" +
                    "-----------------------------\n" +
                    "※ 잔액 확인은 `!포인트` 명령어를 사용하세요!";

            event.getChannel().sendMessage(infoMsg).queue();
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
            DataManaGer.loadPoints();
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

                // [수정] 변수명을 member -> targetMember로 바꾸면 이름 충돌이 해결됩니다!
                net.dv8tion.jda.api.entities.Member targetMember = event.getGuild().getMemberById(targetId);

                String name = "알 수 없음";

                if (targetMember != null) {
                    name = targetMember.getEffectiveName();
                } else if (DataManaGer.nicknameCache.containsKey(targetId)) {
                    name = DataManaGer.nicknameCache.get(targetId);
                }

                sb.append(String.format("%d등: **%s** %d P\n", i + 1, name, actualBalance));
            }
            event.getChannel().sendMessage(sb.toString()).queue();
            return;
        }
        //선물하기
        if (message.startsWith("!선물")) {
            DataManaGer.loadPoints();
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

                if (receiverId == null) {
                    for (String id : DataManaGer.nicknameCache.keySet()) {
                        if (DataManaGer.nicknameCache.get(id).contains(receiverName)) {
                            receiverId = id;
                            break;
                        }
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
                    return;
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

                // [수정] 칭호 추가 로직을 여기서 실행 (중복 안 되게!)
                String currentData = userTitles.getOrDefault(userId, "");
                if (!currentData.contains("빚쟁이")) {
                    String debtEntry = "빚쟁이|" + getExpirationDate(3);
                    userTitles.put(userId, currentData.isEmpty() ? debtEntry : currentData + "," + debtEntry);
                }

                // 2. 저장
                DataManaGer.savePoints(userPoints, event.getGuild());
                DataManaGer.saveDebts(userDebt);
                DataManaGer.saveDeadlines(debtDeadline);
                DataManaGer.saveTitles(userTitles);

                // 3. 닉네임 변경
                String cleanName = event.getMember().getEffectiveName().replaceAll("\\[.*?\\]", "").trim();
                if (!event.getMember().isOwner()) {
                    event.getMember().modifyNickname("[빚쟁이] " + cleanName).queue();
                }

                event.getChannel().sendMessage("💰 **" + amount + " P** 대출 완료!").queue();

            } catch (NumberFormatException e) {
                event.getChannel().sendMessage("❌ 금액을 숫자로 입력해주세요!").queue();
            }
        }

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
            int newPoint = myPoint - debt; // 새로운 잔고 계산

            // 2. 데이터 업데이트 (포인트, 빚, 마감일 삭제)
            userPoints.put(userId, newPoint);
            userDebt.remove(userId);
            debtDeadline.remove(userId);

            // 3. 기존 칭호에서 '빚쟁이'와 '노예' 제거
            String currentData = userTitles.getOrDefault(userId, "");
            StringBuilder sb = new StringBuilder();

            if (!currentData.isEmpty()) {
                for (String entry : currentData.split(",")) {
                    // '빚쟁이'나 '노예'가 아닌 것들만 남김
                    if (!entry.startsWith("빚쟁이|") && !entry.startsWith("노예|")) {
                        if (sb.length() > 0) sb.append(",");
                        sb.append(entry);
                    }
                }
            }

            // 4. 만약 잔고가 마이너스면 '노예' 추가
            if (newPoint < 0) {
                String slaveEntry = "노예|" + getExpirationDate(999);
                if (sb.length() > 0) sb.append(",");
                sb.append(slaveEntry);
            }

            // 5. 칭호 저장
            String finalTitles = sb.toString();
            if (finalTitles.isEmpty()) userTitles.remove(userId);
            else userTitles.put(userId, finalTitles);

            // 6. 모든 데이터 저장 (딱 한 번만 실행!)
            DataManaGer.savePoints(userPoints, event.getGuild());
            DataManaGer.saveDebts(userDebt);
            DataManaGer.saveDeadlines(debtDeadline);
            DataManaGer.saveTitles(userTitles);

            // 7. 닉네임 변경 (노예면 [노예], 아니면 남은 칭호 중 첫 번째)
            String cleanName = event.getMember().getEffectiveName().replaceAll("\\[.*?\\]", "").trim();
            String newNickname = cleanName;

            if (newPoint < 0) {
                newNickname = "[노예] " + cleanName;
            } else if (!finalTitles.isEmpty()) {
                String firstTitle = finalTitles.split(",")[0].split("\\|")[0];
                newNickname = "[" + firstTitle + "] " + cleanName;
            }

            if (!event.getMember().isOwner()) {
                event.getMember().modifyNickname(newNickname).queue();
            }

            // 8. 결과 메시지
            if (newPoint < 0) {
                event.getChannel().sendMessage("⛓️ 빚 " + debt + " P를 상환했지만, 잔고가 마이너스이므로 **[노예]** 신분이 됩니다. 현재 잔고: **" + newPoint + " P**").queue();
            } else {
                event.getChannel().sendMessage("✅ 빚 " + debt + " P를 성공적으로 상환했습니다! **[빚쟁이]** 칭호가 해제되었습니다.").queue();
            }
        }


        if (message.equals("!채굴")) {
            userId = event.getAuthor().getId();
            long now = System.currentTimeMillis();
            long cooldown = 3 * 60 * 1000; // 3분

            // 쿨타임 체크
            if (workCooldowns.containsKey(userId) && (now - workCooldowns.get(userId) < cooldown)) {
                long remaining = (cooldown - (now - workCooldowns.get(userId))) / 1000;
                event.getChannel().sendMessage("⏳ **" + remaining + "초** 뒤에 다시 채굴할 수 있습니다!").queue();
                return;
            }

            int level = pickaxeLevels.getOrDefault(userId, 1); // 없으면 1레벨
            int bonus = (level - 1) * 2; // 1레벨당 +5포인트 보너스 (밸런스 조절 가능)
            // [핵심] 랜덤 보상 시스템
            int chance = random.nextInt(100); // 0~99
            int earn = 0;
            String resultMsg = "";

            int diamondChance = 5 + (level / 2); // 레벨당 다이아 확률 0.5%씩 증가

            //강보권
            boolean foundTicket = false;

            int currentLevel = pickaxeLevels.getOrDefault(userId, 1);
            double rawRate = 1.0 + (currentLevel * 0.5);

            // 2. 맥스치(10%) 적용: 10보다 크면 무조건 10으로 고정
            double ticketRate = Math.min(rawRate, 10.0);

            // 3. 확률 체크
            if (random.nextDouble() * 100 < ticketRate) {
                int currentTickets = protectionTickets.getOrDefault(userId, 0);
                protectionTickets.put(userId, currentTickets + 1);
                DataManaGer.saveProtectionTickets(protectionTickets);

                event.getChannel().sendMessage("✨ **[" + pureName + "]님, 채굴하다가 보호권을 발견했습니다! (현재 확률: " + ticketRate + "%)**").queue();
            }

            if (chance < diamondChance) {
                earn = 30 + bonus;
                resultMsg = "💎 **대박! 커다란 다이아몬드를 발견했습니다! (+" + earn + " P)**";
            } else if (chance < 20) {
                earn = 10 + bonus;
                resultMsg = "⛏️ 꽤 괜찮은 광석을 캤습니다. (+" + earn + " P)";
            } else if (chance < 70) {
                earn = 5 + bonus;
                resultMsg = "🪨 돌맹이만 잔뜩 캤네요... (+" + earn + " P)";
            } else {
                earn = 0; // 꽝은 보너스 없음
                resultMsg = "❌ **앗! 곡괭이가 부러져서 아무것도 못 캤습니다... (0 P)**";
            }

            // 포인트 업데이트 및 저장
            int current = userPoints.getOrDefault(userId, 0);
            userPoints.put(userId, current + earn);
            workCooldowns.put(userId, now);

            DataManaGer.savePoints(userPoints, event.getGuild());
            String finalMsg = resultMsg + "\n(현재 잔고: **" + (current + earn) + " P**)";
            if (foundTicket) {
                finalMsg += "\n✨ **와우! 채굴 중에 '강화 보호권'을 발견했습니다! (+1 장)**";
            }

            event.getChannel().sendMessage(finalMsg).queue();
        }

        if (message.equals("!강화")) {
            userId = event.getAuthor().getId();
            int currentLevel = pickaxeLevels.getOrDefault(userId, 1);
            int cost = (currentLevel * 20) + 100; // 강화 비용
            // [성공률 대폭 하향: 레벨당 15%씩 감소]
            int successRate = 100 - (currentLevel * 15);
            if (successRate < 5) successRate = 5; // 최소 5%는 보장

            // 포인트 체크 (미리 검사)
            if (userPoints.getOrDefault(userId, 0) < cost) {
                event.getChannel().sendMessage("❌ 포인트가 부족합니다! (필요: " + cost + " P)").queue();
                return;
            }

            // 버튼을 포함한 메시지 전송
            event.getChannel().sendMessage("🔨 **" + currentLevel + " → " + (currentLevel + 1) + "강화 시도!**\n" +
                            "📈 성공 확률: **" + successRate + "%**\n" +
                            "💰 소모 포인트: **" + cost + " P**\n" +
                            "정말로 강화하시겠습니까?")
                    .setComponents(ActionRow.of(
                            Button.primary("btn_confirm:" + userId, "강화하기"),
                            Button.danger("btn_cancel:" + userId, "취소")
                    ))
                    .queue();
        }


        //데이터복구
        if (message.equals("!데이터복구")) {
            if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
                event.getChannel().sendMessage("❌ 운영진만 사용 가능합니다.").queue();
                return;
            }

            event.getChannel().sendMessage("⏳ 데이터를 시트에 강제 동기화 중입니다...").queue();

            // 1. 멤버를 먼저 불러옵니다
            event.getGuild().loadMembers().onSuccess(members -> {

                // 2. [핵심] 불러온 멤버들을 캐시에 먼저 넣어둡니다
                for (net.dv8tion.jda.api.entities.Member m : members) {
                    DataManaGer.nicknameCache.put(m.getId(), m.getEffectiveName());
                }

                try {
                    // 3. 이제 안전하게 데이터를 싹 날리고 다시 저장합니다
                    GoogleSheetService.clearValues("시트1!A2:E");

                    // 데이터 전부 저장
                    DataManaGer.savePoints(userPoints, event.getGuild());
                    DataManaGer.saveTitles(userTitles);
                    DataManaGer.saveDebts(userDebt);
                    DataManaGer.saveDeadlines(debtDeadline); // <--- 이 한 줄을 꼭 넣으세요!

                    event.getChannel().sendMessage("✅ 시트 데이터가 현재 봇 상태로 강제 교체되었습니다!").queue();
                    System.out.println("[시스템] 데이터 복구 완료. 총 유저 수: " + userPoints.size());
                } catch (Exception e) {
                    event.getChannel().sendMessage("❌ 오류 발생: " + e.getMessage()).queue();
                    e.printStackTrace();
                }
            }).onError(e -> {
                event.getChannel().sendMessage("❌ 멤버 로드 실패: " + e.getMessage()).queue();
            });
        }
        if (message.equals("!데이터불러오기")) {
            if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
                event.getChannel().sendMessage("❌ 운영진만 사용 가능합니다.").queue();
                return;
            }

            event.getChannel().sendMessage("⏳ 시트에서 데이터를 불러오는 중...").queue();

            try {
                // DataManaGer에서 다시 로드
                this.userPoints = DataManaGer.loadPoints();
                this.userTitles = DataManaGer.loadTitles();
                this.userDebt = DataManaGer.loadDebts();
                this.debtDeadline = DataManaGer.loadDeadlines();

                event.getChannel().sendMessage("✅ 시트 데이터를 봇으로 성공적으로 불러왔습니다!").queue();
            } catch (Exception e) {
                event.getChannel().sendMessage("❌ 불러오기 실패: " + e.getMessage()).queue();
                e.printStackTrace();
            }
        }
        // 시트 비우기 명령어 (운영진만 사용 가능)
        if (message.startsWith("!시트비우기 ")) {
            // 운영진 권한 체크
            if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
                event.getChannel().sendMessage("❌ 운영진만 사용할 수 있는 기능입니다.").queue();
                return;
            }

            // 명령어 뒤에 적은 영역을 읽어서 비움 (예: !시트비우기 시트1!A2:C)
            String range = message.substring(7).trim();

            try {
                GoogleSheetService.clearValues(range);
                event.getChannel().sendMessage("✅ **[" + range + "]** 영역의 데이터가 삭제되었습니다.").queue();
            } catch (Exception e) {
                event.getChannel().sendMessage("❌ 삭제 중 오류 발생: " + e.getMessage()).queue();
            }
        }

        if (message.startsWith("!칭호지급 ")) {
            if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
                event.getChannel().sendMessage("❌ 운영진만 사용할 수 있습니다.").queue();
                return;
            }

            String[] parts = message.split(" ");
            if (parts.length < 3) {
                event.getChannel().sendMessage("사용법: `!칭호지급 [유저] [칭호이름]`").queue();
                return;
            }

            // 1. 타겟 유저 ID 찾기 (멘션이나 닉네임)
            String targetQuery = parts[1];
            String titleName = parts[2];
            String targetUserId = null;

            // (기존의 유저 ID 찾기 로직 재사용)
            for (net.dv8tion.jda.api.entities.Member m : event.getGuild().getMembers()) {
                if (m.getEffectiveName().contains(targetQuery) || m.getAsMention().equals(targetQuery)) {
                    targetUserId = m.getId();
                    break;
                }
            }

            if (targetUserId == null) {
                event.getChannel().sendMessage("❌ 유저를 찾을 수 없습니다.").queue();
                return;
            }

            // 2. 칭호 데이터 추가 (이미 있으면 뒤에 붙임)
            String currentData = userTitles.getOrDefault(targetUserId, "");
            String newEntry = titleName + "|" + getExpirationDate(14); // 14일짜리 지급
            userTitles.put(targetUserId, currentData.isEmpty() ? newEntry : currentData + "," + newEntry);

            DataManaGer.saveTitles(userTitles);

            event.getChannel().sendMessage("✅ **" + targetQuery + "**님에게 **[" + titleName + "]** 칭호를 지급했습니다!").queue();
        }

        if (message.startsWith("!칭호삭제 ")) {
            if (!event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
                event.getChannel().sendMessage("❌ 운영진만 사용할 수 있습니다.").queue();
                return;
            }

            String[] parts = message.split(" ");
            if (parts.length < 3) {
                event.getChannel().sendMessage("사용법: `!칭호삭제 [유저] [칭호이름]`").queue();
                return;
            }

            String targetQuery = parts[1];
            String titleToRemove = parts[2];
            String targetUserId = null;

            for (net.dv8tion.jda.api.entities.Member m : event.getGuild().getMembers()) {
                if (m.getEffectiveName().contains(targetQuery) || m.getAsMention().equals(targetQuery)) {
                    targetUserId = m.getId();
                    break;
                }
            }

            if (targetUserId == null || !userTitles.containsKey(targetUserId)) {
                event.getChannel().sendMessage("❌ 해당 유저나 유저의 칭호 데이터를 찾을 수 없습니다.").queue();
                return;
            }

            // 3. 칭호 삭제 로직
            String currentData = userTitles.get(targetUserId);
            String[] entries = currentData.split(",");
            StringBuilder sb = new StringBuilder();
            boolean found = false;

            for (String entry : entries) {
                if (entry.split("\\|")[0].equals(titleToRemove)) {
                    found = true; // 삭제할 칭호 발견
                    continue;
                }
                if (sb.length() > 0) sb.append(",");
                sb.append(entry);
            }

            if (!found) {
                event.getChannel().sendMessage("❌ 해당 유저는 그 칭호를 가지고 있지 않습니다.").queue();
                return;
            }

            if (sb.length() == 0) userTitles.remove(targetUserId);
            else userTitles.put(targetUserId, sb.toString());

            DataManaGer.saveTitles(userTitles);
            event.getChannel().sendMessage("✅ **" + targetQuery + "**님의 **[" + titleToRemove + "]** 칭호를 삭제했습니다.").queue();

        }


    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        // 1. ID를 쪼개서 action("btn_confirm")과 ownerId를 가져옵니다.
        String[] parts = event.getComponentId().split(":");
        String action = parts[0];
        String ownerId = parts[1];

        // 2. 보안 로직: 버튼 주인인지 확인
        if (!event.getUser().getId().equals(ownerId)) {
            event.reply("❌ 본인의 강화창만 조작 가능합니다!").setEphemeral(true).queue();
            return;
        }

        // 3. 취소 버튼 처리
        if (action.equals("btn_cancel")) {
            event.editMessage("❌ **강화가 취소되었습니다.**").setComponents().queue(); // 버튼 제거
            return;
        }

        // 4. 확인 버튼 처리 (여기서 action.equals를 사용해야 합니다!)
        if (action.equals("btn_confirm")) {
            String userId = event.getUser().getId();
            int currentLevel = pickaxeLevels.getOrDefault(userId, 1);
            int cost = (currentLevel * 20) + 100;

            if (userPoints.getOrDefault(userId, 0) < cost) {
                event.editMessage("❌ **강화 실패!** (포인트가 그새 부족해졌습니다)").setComponents().queue();
                return;
            }

            // 실제 강화 로직
            userPoints.put(userId, userPoints.get(userId) - cost);
            int successRate = 100 - (currentLevel * 15);
            if (successRate < 5) successRate = 5;

            if (random.nextInt(100) < successRate) {
                pickaxeLevels.put(userId, currentLevel + 1);
                event.editMessage("✅ **강화 성공!** " + currentLevel + " → " + (currentLevel + 1) + "레벨!").setComponents().queue();
                DataManaGer.savePickaxeLevels(pickaxeLevels);
            } else {
                int tickets = protectionTickets.getOrDefault(userId, 0);
                if (tickets > 0) {
                    protectionTickets.put(userId, tickets - 1);
                    event.editMessage("🛡️ **강화 실패!** 보호권 사용됨! (남은 보호권: " + (tickets - 1) + "장)").setComponents().queue();
                    DataManaGer.saveProtectionTickets(protectionTickets);
                } else {
                    int newLevel = Math.max(1, currentLevel - 1);
                    pickaxeLevels.put(userId, newLevel);
                    event.editMessage("💔 **강화 실패!** 곡괭이가 낡았습니다... (" + currentLevel + " → " + newLevel + " 레벨)").setComponents().queue();
                    DataManaGer.savePickaxeLevels(pickaxeLevels);
                }
            }
            DataManaGer.savePoints(userPoints, event.getGuild());
        }
    }
}

