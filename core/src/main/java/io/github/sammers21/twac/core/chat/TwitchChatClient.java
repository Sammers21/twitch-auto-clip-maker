package io.github.sammers21.twac.core.chat;


import com.codahale.metrics.MetricRegistry;
import io.vertx.core.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.CountDownLatch;

public class TwitchChatClient {

    private static final Logger log = LoggerFactory.getLogger(TwitchChatClient.class);
    public static final String IRC_HOST = "irc.chat.twitch.tv";
    public static final Integer IRC_PORT = 6667;

    private final String oauthToken;
    private final String nickName;
    private final TcpTextClient tcpTextClient;
    private Handler<ChatMessage> handler;
    private Handler<Void> loginResult;
    private final CountDownLatch countDownLatch = new CountDownLatch(1);
    private MetricRegistry metricRegistry;

    public TwitchChatClient(String oauthToken, String nickName) {
        this.oauthToken = oauthToken;
        this.nickName = nickName;
        tcpTextClient = new TcpTextClient(IRC_HOST, IRC_PORT);
        tcpTextClient.outputHandler(event -> {
            if (event.equals("PING :tmi.twitch.tv")) {
                tcpTextClient.input("PONG :tmi.twitch.tv");
                log.info("PING PONG OK");
                return;
            }
            if (event.contains("NOTICE")) {
                log.info(event);
            }
            if (event.contains("Login unsuccessful")) {
                log.error("FAILED TWITCH CHAT LOGIN", new IllegalStateException("FAILED TWITCH CHAT LOGIN"));
                return;
            }
            if (event.endsWith(">") && countDownLatch.getCount() == 1) {
                countDownLatch.countDown();
                log.info("Input can be started");
                return;
            }
            if (handler != null) {
                ChatMessage parse = ChatMessage.parse(event);
                if (parse != null) {
                    Objects.requireNonNull(handler);
                    Objects.requireNonNull(parse);
                    handler.handle(parse);
                } else {
                    log.info(event);
                }
            }
        });
    }

    public void start() {
        tcpTextClient.start();
        tcpTextClient.input(String.format("PASS oauth:%s", oauthToken));
        tcpTextClient.input(String.format("NICK %s", nickName));
    }

    public void stop() {
        tcpTextClient.stop();
    }

    public void messageHandler(Handler<ChatMessage> handler) {
        this.handler = handler;
    }

    public void joinChannel(String chan) {
        try {
            countDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        tcpTextClient.input(String.format("JOIN #%s", chan));
    }

    public void setMetricRegistry(MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
        tcpTextClient.setMetricRegistry(metricRegistry);
    }
}
