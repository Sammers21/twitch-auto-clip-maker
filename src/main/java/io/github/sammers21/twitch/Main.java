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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;


public class Main {

    private static Logger log = LoggerFactory.getLogger(Main.class);

    private static String CARBON_HOST;
    private static Integer CARBON_PORT;

    public static void main(String[] args) {
        String token = args[0];
        CARBON_HOST = args[1];
        CARBON_PORT = Integer.parseInt(args[2]);

        if (args.length < 4) {
            log.error("Should be at least 4 args");
            System.exit(-1);
        }
        List<String> channelToWatch = Arrays.stream(args).skip(3).collect(Collectors.toList());
        log.info("Token={}", token);
        log.info("CARBON_HOST={}", CARBON_HOST);
        log.info("CARBON_PORT={}", CARBON_PORT);
        log.info("Channels={}", channelToWatch.stream().collect(Collectors.joining(",", "[", "]")));

        MetricRegistry metricRegistry = new MetricRegistry();
        initReporters(metricRegistry);
        var credential = new OAuth2Credential("twitch", token);
        var twitchClient = TwitchClientBuilder.builder()
                .withEnableChat(true)
                .withChatAccount(credential)
                .build();
        var chat = twitchClient.getChat();
        channelToWatch.forEach(chat::joinChannel);
        var value = chat.getEventManager().onEvent(ChannelMessageEvent.class);
        value.subscribe(ok -> {
            String channelName = ok.getChannel().getName();
            Meter messagesPerSec = metricRegistry.meter(String.format("channel.%s.messages", channelName));
            messagesPerSec.mark();
            log.info("[{}] {}: {}", channelName, ok.getUser().getName(), ok.getMessage());
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
