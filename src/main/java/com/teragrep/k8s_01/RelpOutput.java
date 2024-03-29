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

import com.teragrep.rlo_14.SyslogMessage;
import com.codahale.metrics.*;
import com.teragrep.k8s_01.config.AppConfigRelp;
import com.teragrep.rlp_01.RelpBatch;
import com.teragrep.rlp_01.RelpConnection;
import com.teragrep.rlp_01.SSLContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static com.codahale.metrics.MetricRegistry.name;

public class RelpOutput {
    private static final Logger LOGGER = LoggerFactory.getLogger(RelpOutput.class);
    private final RelpConnection relpConnection;
    private final AppConfigRelp relpConfig;
    private final int id;
    private final Counter totalReconnects;
    private final Counter totalConnections;
    private final Meter throughputBytes;
    private final Meter throughputRecords;
    private final Meter throughputErrors;
    RelpOutput(AppConfigRelp appConfigRelp, int threadId, MetricRegistry metricRegistry) {
        relpConfig = appConfigRelp;
        id = threadId;
        if(LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    "[#{}] Started Relp thread #{}",
                    getId(),
                    getId()
            );
        }
        if(LOGGER.isTraceEnabled()) {
            LOGGER.trace(
                    "[#{}] Received Relp config: {}",
                    getId(),
                    relpConfig
            );
        }
        if(relpConfig.getTls().getEnabled()) {
            if(LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        "[#{}] Creating TLS relp output using <[{}]> keystore",
                        getId(),
                        relpConfig.getTls().getKeystore()
                );
            }
            SSLContext sslContext;
            try {
                sslContext = SSLContextFactory.authenticatedContext(
                        relpConfig.getTls().getKeystore(),
                        relpConfig.getTls().getPassword(),
                        "TLSv1.3"
                );
            } catch (GeneralSecurityException | IOException e) {
                LOGGER.error(
                        "[#{}] Failed to construct sslContext: ",
                        getId(),
                        e
                );
                throw new RuntimeException(e);
            }
            Supplier<SSLEngine> sslEngineSupplier = sslContext::createSSLEngine;
            relpConnection = new RelpConnection(sslEngineSupplier);
        }
        else {
            if(LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        "[#{}] Creating plain relp output",
                        getId()
                );
            }
            relpConnection = new RelpConnection();
        }
        relpConnection.setConnectionTimeout(relpConfig.getConnectionTimeout());
        relpConnection.setReadTimeout(relpConfig.getReadTimeout());
        relpConnection.setWriteTimeout(relpConfig.getWriteTimeout());
        // Throughput
        throughputBytes = metricRegistry.meter(name("k8s01", "throughput", "bytes"));
        throughputRecords = metricRegistry.meter(name("k8s01", "throughput", "records"));
        throughputErrors = metricRegistry.meter(name("k8s01", "throughput", "errors"));

        // Totals
        totalConnections = metricRegistry.counter(name("k8s01", "total", "connections"));
        totalReconnects = metricRegistry.counter(name("k8s01", "total", "reconnects"));
        connect();
    }

    private void connect() {
        boolean connected = false;
        while (!connected) {
            try {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug(
                            "[#{}] Connecting to {}:{}",
                            getId(),
                            relpConfig.getTarget(),
                            relpConfig.getPort()
                    );
                }
                connected = relpConnection.connect(relpConfig.getTarget(), relpConfig.getPort());
                totalConnections.inc();
            } catch(ClosedChannelException e) {
                LOGGER.error(
                        "[#{}] Relp connection was unexpectedly closed, are you trying to talk tls to plain server: {}: {}",
                        getId(),
                        e.getClass().getName(),
                        e.getMessage()
               );
                if(LOGGER.isDebugEnabled()) {
                    LOGGER.debug(
                            "[#{}] Exception source: ",
                            getId(),
                            e
                    );
                }
                throughputErrors.mark();
                totalConnections.dec();
            } catch (IOException | TimeoutException e) {
                LOGGER.error(
                        "[#{}] Can't connect to Relp server: {}: {}",
                        getId(),
                        e.getClass().getName(),
                        e.getMessage()
                );
                if(LOGGER.isDebugEnabled()) {
                    LOGGER.debug(
                            "[#{}] Exception source: ",
                            getId(),
                            e
                    );
                }
                throughputErrors.mark();
                totalConnections.dec();
            }
            if (!connected) {
                totalReconnects.inc();
                try {
                    LOGGER.info(
                            "[#{}] Attempting to reconnect in {}ms.",
                            getId(),
                            relpConfig.getReconnectInterval()
                    );
                    Thread.sleep(relpConfig.getReconnectInterval());
                } catch (InterruptedException e) {
                    LOGGER.error(
                            "[#{}] Sleep was interrupted",
                            getId(),
                            e
                    );
                    throughputErrors.mark();
                }
            }
        }
    }

    public void disconnect() {
        LOGGER.debug(
                "[#{}] Disconnecting",
                getId()
        );
        try {
            totalConnections.dec();
            relpConnection.disconnect();
        } catch (IOException | TimeoutException e) {
            LOGGER.info(
                    "[#{}] Had to teardown connection",
                    getId()
            );
            relpConnection.tearDown();
        }
    }

    public void send(SyslogMessage syslogMessage) {
        if(LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    "[#{}] Got a new message from {}@{}",
                    getId(),
                    syslogMessage.getAppName(),
                    syslogMessage.getHostname()
            );
        }
        if(LOGGER.isTraceEnabled()) {
            LOGGER.trace(
                    "[#{}] Sending message: {}",
                    getId(),
                    syslogMessage.toRfc5424SyslogMessage()
            );
        }
        RelpBatch batch = new RelpBatch();
        batch.insert(syslogMessage.toRfc5424SyslogMessage().getBytes(StandardCharsets.UTF_8));

        boolean allSent = false;
        while (!allSent) {
            try {
                if(LOGGER.isTraceEnabled()) {
                    LOGGER.trace(
                            "[#{}] Committing batch",
                            getId()
                    );
                }
                relpConnection.commit(batch);
            } catch (IllegalStateException | IOException | java.util.concurrent.TimeoutException e) {
                LOGGER.error(
                        "[#{}] Failed to send messages:",
                        getId(),
                        e
                );
                throughputErrors.mark();
            }
            // Check if everything has been sent, retry and reconnect if not.
            if (!batch.verifyTransactionAll()) {
                LOGGER.debug(
                        "[#{}] Failed to verifyTransactionAll(), retrying",
                        getId()
                );
                batch.retryAllFailed();
                relpConnection.tearDown();
                totalConnections.dec();
                connect();
            } else {
                allSent = true;
                throughputBytes.mark(syslogMessage.toRfc5424SyslogMessage().getBytes(StandardCharsets.UTF_8).length);
                throughputRecords.mark();
            }
        }
    }

    public int getId() {
        return id;
    }
}
