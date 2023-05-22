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

import com.cloudbees.syslog.SyslogMessage;
import com.teragrep.k8s_01.config.AppConfigRelp;
import com.teragrep.rlp_01.RelpBatch;
import com.teragrep.rlp_01.RelpConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

public class RelpOutput {
    private static final Logger LOGGER = LoggerFactory.getLogger(RelpOutput.class);
    private final RelpConnection relpConnection;
    private final AppConfigRelp relpConfig;
    private final int id;

    RelpOutput(AppConfigRelp appConfigRelp, int threadId) {
        relpConfig = appConfigRelp;
        id = threadId;
        LOGGER.debug(
                "[#{}] Started Relp thread #{}",
                getId(),
                getId()
        );
        LOGGER.trace(
                "[#{}] Received Relp config: {}",
                getId(),
                relpConfig
        );
        relpConnection = new RelpConnection();
        relpConnection.setConnectionTimeout(relpConfig.getConnectionTimeout());
        relpConnection.setReadTimeout(relpConfig.getReadTimeout());
        relpConnection.setWriteTimeout(relpConfig.getWriteTimeout());
        connect();
    }

    private void connect() {
        boolean connected = false;
        while (!connected) {
            try {
                LOGGER.debug(
                        "[#{}] Connecting to {}:{}",
                        getId(),
                        relpConfig.getTarget(),
                        relpConfig.getPort()
                );
                connected = relpConnection.connect(relpConfig.getTarget(), relpConfig.getPort());
            } catch (IOException | TimeoutException e) {
                LOGGER.error(
                        "[#{}] Can't connect to Relp server:",
                        getId(),
                        e
                );
            }
            if (!connected) {
                try {
                    LOGGER.info(
                            "[#{}] Attempting to reconnect in {}ms.",
                            getId(),
                            relpConfig.getReconnectInterval()
                    );
                    Thread.sleep(relpConfig.getReconnectInterval());
                } catch (InterruptedException e) {
                    e.printStackTrace();
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
            relpConnection.disconnect();
        } catch (IOException | TimeoutException e) {
            LOGGER.debug(
                    "[#{}] Had to teardown connection",
                    getId()
            );
            relpConnection.tearDown();
            throw new RuntimeException(e);
        }
    }

    public void send(SyslogMessage syslogMessage) {
        LOGGER.debug(
                "[#{}] Got a new message from {}@{}",
                getId(),
                syslogMessage.getAppName(),
                syslogMessage.getHostname()
        );
        LOGGER.trace(
                "[#{}] Sending message: {}",
                getId(),
                syslogMessage.toRfc5424SyslogMessage()
        );
        RelpBatch batch = new RelpBatch();
        batch.insert(syslogMessage.toRfc5424SyslogMessage().getBytes(StandardCharsets.UTF_8));

        boolean allSent = false;
        while (!allSent) {
            try {
                LOGGER.trace(
                        "[#{}] Committing batch",
                        getId()
                );
                relpConnection.commit(batch);
            } catch (IllegalStateException | IOException | java.util.concurrent.TimeoutException e) {
                LOGGER.error(
                        "[#{}] Failed to send messages:",
                        getId(),
                        e
                );
            }
            // Check if everything has been sent, retry and reconnect if not.
            if (!batch.verifyTransactionAll()) {
                LOGGER.debug(
                        "[#{}] Failed to verifyTransactionAll(), retrying",
                        getId()
                );
                batch.retryAllFailed();
                relpConnection.tearDown();
                connect();
            } else {
                allSent = true;
            }
        }
    }

    public int getId() {
        return id;
    }
}
