package io.github.sammers21.twitch;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;
import com.github.philippheuer.credentialmanager.domain.OAuth2Credential;
import com.github.twitch4j.TwitchClientBuilder;
import com.github.twitch4j.chat.events.channel.ChannelMessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;


public class Main {

    private static Logger log = LoggerFactory.getLogger(Main.class);

    private static final String CARBON_HOST = "174.138.4.227";
    private static final Integer CARBON_PORT = 2003;

    public static void main(String[] args) {
        String token = args[0];
        String channel = args[1];


        MetricRegistry metricRegistry = new MetricRegistry();
        initReporters(metricRegistry);

        var credential = new OAuth2Credential("twitch", token);

        var twitchClient = TwitchClientBuilder.builder()
                .withEnableChat(true)
                .withChatAccount(credential)
                .build();

        Meter messagesPerSec = metricRegistry.meter(String.format("channel.%s.messages", channel));

        var chat = twitchClient.getChat();
        chat.joinChannel(channel);
        var value = chat.getEventManager().onEvent(ChannelMessageEvent.class);
        value.subscribe(ok -> {
            messagesPerSec.mark();
            log.info("{}: {}", ok.getUser().getName(), ok.getMessage());
        });
    }

    private static void initReporters(MetricRegistry metricRegistry) {
        Graphite graphite = new Graphite(new InetSocketAddress(CARBON_HOST, CARBON_PORT));
        final GraphiteReporter reporter = GraphiteReporter.forRegistry(metricRegistry)
                .prefixedWith("twitch.chat")
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .filter(MetricFilter.ALL)
                .build(graphite);
        reporter.start(1, TimeUnit.SECONDS);
    }
}
