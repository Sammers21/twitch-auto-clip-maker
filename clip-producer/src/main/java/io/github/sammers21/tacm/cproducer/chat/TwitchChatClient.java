package io.github.sammers21.tacm.cproducer.chat;


import io.vertx.core.Handler;

import java.util.concurrent.CountDownLatch;

public class TwitchChatClient {

    public static final String IRC_HOST = "irc.chat.twitch.tv";
    public static final Integer IRC_PORT = 6667;

    private final String oauthToken;
    private final String nickName;
    private final TcpTextClient tcpTextClient;
    private Handler<ChatMessage> handler;
    private final CountDownLatch countDownLatch = new CountDownLatch(1);

    public TwitchChatClient(String oauthToken, String nickName) {
        this.oauthToken = oauthToken;
        this.nickName = nickName;
        tcpTextClient = new TcpTextClient(IRC_HOST, IRC_PORT);
        tcpTextClient.outputHandler(event -> {
            if (event.endsWith(">") && countDownLatch.getCount() == 1) {
                countDownLatch.countDown();
            }
            System.out.println("EVENT: " + event);
            if (handler != null) {
                ChatMessage parse = ChatMessage.parse(event);
                if (parse != null) {
                    handler.handle(parse);
                }
            }
        });
    }

    public void start() {
        tcpTextClient.start();
        tcpTextClient.input(String.format("PASS oauth:%s\n", oauthToken));
        tcpTextClient.input(String.format("NICK %s\n", nickName));
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
        tcpTextClient.input(String.format("JOIN #%s\n", chan));
    }
}
