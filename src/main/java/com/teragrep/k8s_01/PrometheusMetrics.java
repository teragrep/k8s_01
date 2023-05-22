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

import io.prometheus.client.exporter.MetricsServlet;
import io.prometheus.client.hotspot.DefaultExports;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrometheusMetrics {
    private static final Logger LOGGER = LoggerFactory.getLogger(PrometheusMetrics.class);
    Server jettyServer;
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
        // Add metrics about CPU, JVM memory etc.
        DefaultExports.initialize();
        // Start the webserver.
        try {
            jettyServer.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void close() {
        LOGGER.info("Closing prometheus metrics server");
        try {
            jettyServer.stop();
        } catch (Exception e) {
            LOGGER.error("Failed to stop jettyServer:", e);
        }
    }
}
