package org.example;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent; // 이 줄이 있어야 합니다!

public class Main {
    public static void main(String[] args) {
        String token = System.getenv("DISCORD_TOKEN");

        // 여기서 .enableIntents(...) 안에 꼭 GUILD_MEMBERS가 있어야 합니다!
        JDABuilder.createDefault(token)
                .enableIntents(
                        GatewayIntent.MESSAGE_CONTENT,
                        GatewayIntent.GUILD_MEMBERS // [핵심] 이 줄이 없어서 에러가 난 것입니다!
                )
                .addEventListeners(new MessageListener())
                .build();

        System.out.println("봇 온");
    }
}