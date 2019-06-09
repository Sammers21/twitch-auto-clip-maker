package io.github.sammers21.twitch;

import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Main {

    private static Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        String token = args[0];
        String channel = args[1];
        var credential = new OAuth2Credential("twitch", token);

        var twitchClient = TwitchClientBuilder.builder()
                .withEnableChat(true)
                .withChatAccount(credential)
                .build();

        var chat = twitchClient.getChat();
        chat.joinChannel(channel);
        var value = chat.getEventManager().onEvent(ChannelMessageEvent.class);
        value.subscribe(ok -> {
            log.info("{}: {}", ok.getUser().getName(), ok.getMessage());
        });
    }
}
