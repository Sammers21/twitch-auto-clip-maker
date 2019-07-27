package io.github.sammers21.twac.core;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.codahale.metrics.jvm.ThreadStatesGaugeSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

public class Utils {

    private static final Logger log = LoggerFactory.getLogger(Utils.class);

    public static String version() throws IOException {
        InputStream resourceAsStream = Utils.class.getResourceAsStream("/version.txt");
        BufferedReader reader = new BufferedReader(new InputStreamReader(resourceAsStream));
        return reader.readLine();
    }

    public static void carbonReporting(MetricRegistry metricRegistry, String prefix, String carbonHost, Integer carbonPort) throws UnknownHostException, SocketException {
        try (final DatagramSocket socket = new DatagramSocket()) {
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002);
            String ip = socket.getLocalAddress().getHostAddress();
            log.info("Reporting from IP: '{}'", ip);
            Graphite graphite = new Graphite(new InetSocketAddress(carbonHost, carbonPort));
            final GraphiteReporter reporter = GraphiteReporter.forRegistry(metricRegistry)
                    .prefixedWith(String.format("%s.%s", ip.replace(".", "_"), prefix))
                    .convertRatesTo(TimeUnit.SECONDS)
                    .convertDurationsTo(TimeUnit.MILLISECONDS)
                    .filter(MetricFilter.ALL)
                    .build(graphite);
            reporter.start(1, TimeUnit.SECONDS);
            metricRegistry.register("memory", new MemoryUsageGaugeSet());
            metricRegistry.register("gc", new GarbageCollectorMetricSet());
            metricRegistry.register("thread", new ThreadStatesGaugeSet());
        }
    }
}
