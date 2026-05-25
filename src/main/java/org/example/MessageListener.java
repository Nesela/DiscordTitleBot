package org.example;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.HashMap;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

public class MessageListener extends ListenerAdapter {
    private HashMap<String, Integer> userPoints = DataManaGer.loadPoints();
    private HashMap<String, String> userTitles = DataManaGer.loadTitles();
    private HashMap<String, Integer> userDebt = DataManaGer.loadDebts();
    private HashMap<String, LocalDate> debtDeadline = DataManaGer.loadDeadlines();
    private HashMap<String, LocalDate> lastCheckInDates = new HashMap<>();

    private int publicTitlePrice = 100;
    private int customTitlePrice = 150;

    private String getExpirationDate(int days) {
        return LocalDate.now().plusDays(days).format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    private boolean isExpiresd(String dateString) {
        LocalDate expiry = LocalDate.parse(dateString, DateTimeFormatter.BASIC_ISO_DATE);
        return LocalDate.now().isAfter(expiry);
    }

    private void checkTitlse(MessageReceivedEvent event) {
        String userId = event.getAuthor().getId();
        String currentNickname = event.getMember().getEffectiveName();
        String pureName = currentNickname.replaceAll("\\[.*?\\]", "").trim();

        if (!userTitles.containsKey(userId)) return;

        String data = userTitles.get(userId);
        String[] entries = data.split(",");
        StringBuilder newInventory = new StringBuilder();
        boolean isChanged = false;

        for (String entry : entries) {
            String[] parts = entry.split("\\|");
            if (parts.length < 2) continue;
            if (isExpiresd(parts[1])) {
                isChanged = true;
                event.getChannel().sendMessage(" [" + parts[0] + "] 칭호 기간이 만료되어 회수되었습니다.").queue();
            } else {
                if (newInventory.length() > 0) newInventory.append(",");
                newInventory.append(entry);
            }
        }

        if (isChanged) {
            if (newInventory.length() == 0) userTitles.remove(userId);
            else userTitles.put(userId, newInventory.toString());
            DataManaGer.saveTitles(userTitles);
            if (!event.getMember().isOwner()) event.getMember().modifyNickname(pureName).queue();
        }

        if (debtDeadline.containsKey(userId) && LocalDate.now().isAfter(debtDeadline.get(userId))) {
            int points = userPoints.getOrDefault(userId, 0);
            int debt = userDebt.get(userId);
            userPoints.put(userId, points - debt);
            userDebt.remove(userId);
            debtDeadline.remove(userId);
            DataManaGer.saveDebts(userDebt);
            DataManaGer.saveDeadlines(debtDeadline);
            DataManaGer.savePoints(userPoints);
            event.getMember().modifyNickname("[노예] " + pureName).queue();
            event.getChannel().sendMessage("⛓️ 대출 만기일이 지나 자산이 압류되었습니다.").queue();
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;

        String message = event.getMessage().getContentRaw();
        String userId = event.getAuthor().getId();
        String currentNickname = event.getMember().getEffectiveName();
        String pureName = currentNickname.replaceAll("\\[.*?\\]", "").trim();
        boolean isAdmin = event.getMember().hasPermission(Permission.ADMINISTRATOR);

        if (!isAdmin) checkTitlse(event);

        // 랭킹 명령어
        if (message.equals("!랭킹")) {
            if (userPoints.isEmpty()) {
                event.getChannel().sendMessage("아직 포인트 데이터가 없습니다!").queue();
                return;
            }
            List<Map.Entry<String, Integer>> ranking = new ArrayList<>(userPoints.entrySet());
            ranking.sort((o1, o2) -> {
                int b1 = o1.getValue() - userDebt.getOrDefault(o1.getKey(), 0);
                int b2 = o2.getValue() - userDebt.getOrDefault(o2.getKey(), 0);
                return Integer.compare(b2, b1);
            });
            StringBuilder sb = new StringBuilder("\uD83C\uDFC6 **[실제 잔고 랭킹]**\n\n");
            for (int i = 0; i < Math.min(ranking.size(), 10); i++) {
                Map.Entry<String, Integer> entry = ranking.get(i);
                Member m = event.getGuild().getMemberById(entry.getKey());
                String name = (m != null) ? m.getEffectiveName() : "알 수 없음";
                sb.append(String.format("%d등: **%s** - %d P\n", i + 1, name, entry.getValue() - userDebt.getOrDefault(entry.getKey(), 0)));
            }
            event.getChannel().sendMessage(sb.toString()).queue();
            return;
        }

        // 출첵, 선물, 대출 등 기존 기능들을 여기에 그대로 붙여넣으세요.
        // 모든 if문이 이 onMessageReceived 메서드 안쪽(마지막 } 위)에 있어야 합니다.

        if (message.equals("!랭킹")) {
            if (userPoints.isEmpty()) { event.getChannel().sendMessage("데이터 없음").queue(); return; }
            List<Map.Entry<String, Integer>> ranking = new ArrayList<>(userPoints.entrySet());
            ranking.sort((o1, o2) -> {
                int b1 = o1.getValue() - userDebt.getOrDefault(o1.getKey(), 0);
                int b2 = o2.getValue() - userDebt.getOrDefault(o2.getKey(), 0);
                return Integer.compare(b2, b1);
            });
            StringBuilder sb = new StringBuilder("\uD83C\uDFC6 **[잔고 랭킹]**\n\n");
            for (int i = 0; i < Math.min(ranking.size(), 10); i++) {
                Map.Entry<String, Integer> entry = ranking.get(i);
                Member m = event.getGuild().getMemberById(entry.getKey());
                String name = (m != null) ? m.getEffectiveName() : "알 수 없음";
                sb.append(String.format("%d등: **%s** - %d P\n", i + 1, name, entry.getValue() - userDebt.getOrDefault(entry.getKey(), 0)));
            }
            event.getChannel().sendMessage(sb.toString()).queue();
        }
        if (message.equals("!출첵")) {
            LocalDate today = LocalDate.now();
            if (lastCheckInDates.containsKey(userId) && lastCheckInDates.get(userId).equals(today)) {
                event.getChannel().sendMessage("이미 출첵함").queue();
                return;
            }
            int bonus = userPoints.containsKey(userId) ? 0 : 100;
            userPoints.put(userId, userPoints.getOrDefault(userId, 0) + 15 + bonus);
            lastCheckInDates.put(userId, today);
            DataManaGer.savePoints(userPoints);
            event.getChannel().sendMessage("출첵 완료!").queue();
        }
        if (message.startsWith("!선물")) { {
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

                String receiverId = null;
                for (Member m : event.getGuild().getMembers()) {
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
                DataManaGer.savePoints(userPoints);
                event.getChannel().sendMessage("🎁 **" + pureName + "**님이 **" + receiverName + "**님에게 **" + amount + " P**를 선물했습니다!").queue();
            } catch (NumberFormatException e) {
                event.getChannel().sendMessage("숫자를 입력해주세요.").queue();
            }
        }
        if (message.startsWith("!대출")) { userId = event.getAuthor().getId();
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

            if (loanAmount <= 0 || loanAmount > 100) {
                event.getChannel().sendMessage("❌ 1 P 이상, 최대 100 P까지 대출 가능합니다.").queue();
                return;
            }
            if (userDebt.containsKey(userId)) {
                event.getChannel().sendMessage("❌ 이미 대출 중입니다! 상환 후 이용해주세요.").queue();
                return;
            }

            userPoints.put(userId, userPoints.getOrDefault(userId, 0) + loanAmount);
            userDebt.put(userId, loanAmount);
            debtDeadline.put(userId, LocalDate.now().plusDays(3));
            DataManaGer.savePoints(userPoints);
            DataManaGer.saveDebts(userDebt);
            DataManaGer.saveDeadlines(debtDeadline);
            event.getMember().modifyNickname("[빚쟁이] " + pureName).queue();
            event.getChannel().sendMessage("💰 **" + loanAmount + " P**가 대출되었습니다!").queue();
        }
        if (message.startsWith("!상환")) { String targetUserId = userId;
            boolean isStaff = event.getMember().isOwner() || event.getMember().hasPermission(Permission.ADMINISTRATOR);

            if (isStaff && message.contains(" ")) {
                String targetName = message.substring(3).trim();
                for (Member m : event.getGuild().getMembers()) {
                    if (m.getEffectiveName().contains(targetName)) {
                        targetUserId = m.getId();
                        break;
                    }
                }
            }

            int myDebt = userDebt.getOrDefault(targetUserId, 0);
            int myPoints = userPoints.getOrDefault(targetUserId, 0);

            if (myDebt == 0) {
                event.getChannel().sendMessage("✅ 갚을 빚이 없습니다!").queue();
                return;
            }
            if (myPoints < myDebt) {
                event.getChannel().sendMessage("❌ 포인트가 부족합니다.").queue();
                return;
            }

            userPoints.put(targetUserId, myPoints - myDebt);
            userDebt.remove(targetUserId);
            debtDeadline.remove(targetUserId);
            DataManaGer.savePoints(userPoints);
            DataManaGer.saveDebts(userDebt);
            DataManaGer.saveDeadlines(debtDeadline);

            String currentTitles = userTitles.getOrDefault(targetUserId, "");
            String targetNickname = (!currentTitles.isEmpty()) ? "[" + currentTitles.split(",")[0].split("\\|")[0] + "] " + pureName : pureName;

            if (!event.getMember().isOwner()) event.getMember().modifyNickname(targetNickname).queue();
            event.getChannel().sendMessage("✅ 상환 완료! 자유의 몸이 되셨군요!").queue(); }

    } // <--- 여기가 onMessageReceived의 끝입니다. 모든 기능은 이 위에 있어야 합니다!
}
}
