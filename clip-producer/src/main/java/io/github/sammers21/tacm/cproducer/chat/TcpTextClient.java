package io.github.sammers21.tacm.cproducer.chat;


import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LineBasedFrameDecoder;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.vertx.core.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

public class TcpTextClient {

    private static final Logger log = LoggerFactory.getLogger(TcpTextClient.class);
    private final String host;
    private final Integer port;
    private Handler<String> outputHandler;
    private NioEventLoopGroup group;
    private ChannelFuture channel;
    private MetricRegistry metricRegistry;

    public TcpTextClient(String host, Integer port) {
        this.host = host;
        this.port = port;
    }

    public void outputHandler(Handler<String> outputHandler) {
        this.outputHandler = outputHandler;
    }

    public void input(String text) {
        log.info(text);
        channel.channel().writeAndFlush(text + "\n");
    }

    public void start() {
        group = new NioEventLoopGroup();
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group);
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.remoteAddress(host, port);
        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) throws Exception {
                ChannelPipeline pipeline = ch.pipeline();

                pipeline.addLast(new LineBasedFrameDecoder(501));

                pipeline.addLast(new StringDecoder());
                pipeline.addLast(new StringEncoder());

                pipeline.addLast(new SimpleChannelInboundHandler<String>() {
                    @Override
                    protected void channelRead0(ChannelHandlerContext ctx, String msg) {
                        if (metricRegistry != null) {
                            Meter bytes = metricRegistry.meter("total.bytes");
                            bytes.mark(msg.getBytes(StandardCharsets.UTF_8).length);
                        }
                        if (outputHandler != null) {
                            outputHandler.handle(msg);
                        }
                    }

                    @Override
                    public void channelActive(ChannelHandlerContext ctx) {
                        log.info("Channel active");
                    }
                });
            }
        });
        try {
            channel = bootstrap.connect().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        group.shutdownGracefully();
    }

    public void setMetricRegistry(MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
    }
}
