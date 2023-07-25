/*
   Kubernetes log forwarder k8s_01
   Copyright (C) 2023  Suomen Kanuuna Oy

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/

package com.teragrep.k8s_01;

import com.codahale.metrics.*;
import com.codahale.metrics.jmx.JmxReporter;
import com.codahale.metrics.jvm.*;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.dropwizard.DropwizardExports;
import io.prometheus.client.exporter.MetricsServlet;
import io.prometheus.client.hotspot.DefaultExports;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.codahale.metrics.MetricRegistry.name;

public class PrometheusMetrics {
    private static final Logger LOGGER = LoggerFactory.getLogger(PrometheusMetrics.class);
    private final Server jettyServer;
    private final JmxReporter jmxReporter;
    public PrometheusMetrics(int port) {
        LOGGER.info("Starting prometheus metrics server on port {}", port);

        // prometheus-exporter
        jettyServer = new Server(port);
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        jettyServer.setHandler(context);

        MetricsServlet metricsServlet = new MetricsServlet();
        ServletHolder servletHolder = new ServletHolder(metricsServlet);
        context.addServlet(servletHolder, "/metrics");

        // Container for all metrics
        MetricRegistry metricRegistry = new MetricRegistry();

        // Totals
        metricRegistry.register(name("total", "reconnects"), new Counter());
        metricRegistry.register(name("total", "connections"), new Counter());

        // Throughput meters
        metricRegistry.register(name("throughput", "bytes"), new Meter(new SlidingTimeWindowMovingAverages()));
        metricRegistry.register(name("throughput", "records"), new Meter(new SlidingTimeWindowMovingAverages()));
        metricRegistry.register(name("throughput", "errors"), new Meter(new SlidingTimeWindowMovingAverages()));

        // Misc
        metricRegistry.register(name("jvm", "vm"), new JvmAttributeGaugeSet());
        metricRegistry.register(name("jvm", "memory"), new MemoryUsageGaugeSet());
        metricRegistry.register(name("jvm", "threads"), new ThreadStatesGaugeSet());
        metricRegistry.register(name("jvm", "gc"), new GarbageCollectorMetricSet());
        SharedMetricRegistries.add("default", metricRegistry);

        // Add to Prometheus metrics
        CollectorRegistry.defaultRegistry.register(
                new DropwizardExports(metricRegistry)
        );

        // Enable JMX listener
        jmxReporter = JmxReporter.forRegistry(metricRegistry).build();
        jmxReporter.start();

        // Add metrics about CPU, JVM memory etc.
        DefaultExports.initialize();

        // Start the webserver.
        try {
            jettyServer.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static void setupDropWizard() {
    }

    public void close() {
        try {
            LOGGER.info("Closing jettyserver");
            jettyServer.stop();
            LOGGER.info("Closing jmxReporter");
            jmxReporter.stop();
        } catch (Exception e) {
            LOGGER.error("Failed to stop jettyServer and/or jmxReporter:", e);
        }
    }
}
