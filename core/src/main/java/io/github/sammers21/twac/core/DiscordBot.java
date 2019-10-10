package io.github.sammers21.twac.core;

import org.javacord.api.DiscordApi;
import org.javacord.api.DiscordApiBuilder;

public class DiscordBot {
    private final String token;

    public DiscordBot(String token) {
        this.token = token;
    }

    public void start() {
        DiscordApi api = new DiscordApiBuilder().setToken(token).login().join();
        api.getServers().forEach(server -> {
            System.out.println(server.getName());
        });
    }
}
