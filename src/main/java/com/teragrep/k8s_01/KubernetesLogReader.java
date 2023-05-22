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

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.teragrep.k8s_01.config.AppConfig;
import com.teragrep.rlo_12.DirectoryEventWatcher;
import com.teragrep.rlo_13.StatefulFileReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class KubernetesLogReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesLogReader.class);

    static Gson gson = new Gson();
    public static void main(String[] args) throws IOException {
        AppConfig appConfig;
        try {
            appConfig = gson.fromJson(new FileReader("etc/config.json"), AppConfig.class);
        }
        catch (FileNotFoundException e) {
            LOGGER.error(
                    "Can't find config 'etc/config.json':",
                    e
            );
            return;
        }
        catch (JsonParseException e) {
            LOGGER.error(
                    "Can't parse config 'etc/config.json':",
                    e
            );
            return;
        }
        catch (Exception e) {
            LOGGER.error(
                    "Unknown exception while handling config:",
                    e
            );
            return;
        }
        KubernetesCachingAPIClient cacheClient = new KubernetesCachingAPIClient(appConfig.getKubernetes());
        PrometheusMetrics prometheusMetrics = new PrometheusMetrics(appConfig.getMetrics().getPort());

        // Pool of Relp output threads to be shared by every consumer
        BlockingQueue<RelpOutput> relpOutputPool = new LinkedBlockingDeque<>(appConfig.getRelp().getOutputThreads());
        LOGGER.info(
                "Starting {} Relp threads towards {}:{}",
                appConfig.getRelp().getOutputThreads(),
                appConfig.getRelp().getTarget(),
                appConfig.getRelp().getPort()
        );
        for(int i=1; i <= appConfig.getRelp().getOutputThreads(); i++) {
            try {
                LOGGER.debug(
                        "Adding RelpOutput thread #{}",
                        i
                );
                relpOutputPool.put(new RelpOutput(appConfig.getRelp(), i));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        // Graceful shutdown so Relp sessions are gracefully terminated
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Shutting down.");
            for(int i=1; i <= appConfig.getRelp().getOutputThreads(); i++) {
                LOGGER.info(
                        "Disconnecting relp thread #{}/{}",
                        i,
                        appConfig.getRelp().getOutputThreads()
                );
                RelpOutput output;
                try {
                    output = relpOutputPool.take();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                output.disconnect();
            }
        }));

        // consumer supplier, returns always the same instance
        K8SConsumerSupplier consumerSupplier = new K8SConsumerSupplier(appConfig, cacheClient, relpOutputPool);

        String[] logfiles = appConfig.getKubernetes().getLogfiles();
        LOGGER.debug(
                "Monitored logfiles: {}",
                Arrays.toString(logfiles)
        );
        List<Thread> threads = new ArrayList<>();
        String statesStore = System.getProperty("user.dir") + "/var";
        LOGGER.debug(
                "Using {} as statestore",
                statesStore
        );
        // FIXME: VERIFY: SFR is not in try-with-resources block as it will have weird behaviour with threads.
        StatefulFileReader statefulFileReader = new StatefulFileReader(
            Paths.get(statesStore),
            consumerSupplier
        );
        // Start a new thread for all logfile watchers
        for (String logfile : logfiles) {
            Thread thread = new Thread(() -> {
                LOGGER.debug(
                        "Starting new DirectoryEventWatcher thread on directory '{}' with pattern '{}'",
                        appConfig.getKubernetes().getLogdir(),
                        logfile
                );
                try {
                    DirectoryEventWatcher dew = new DirectoryEventWatcher(
                            Paths.get(appConfig.getKubernetes().getLogdir()),
                            false,
                            Pattern.compile(logfile),
                            statefulFileReader,
                            500,
                            TimeUnit.MILLISECONDS
                    );
                    dew.watch();
                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
            thread.setName("DEW-" + threads.size());
            thread.start();
            threads.add(thread);
        }

        // FIXME: Is this necessary
        for (Thread thread : threads) {
            if(LOGGER.isTraceEnabled()) {
                LOGGER.trace(
                        "Waiting for thread {}#{} to finish",
                        thread.getName(),
                        thread.getId()
                );
            }
            try {
                thread.join();
            } catch (InterruptedException e) {
                LOGGER.error(
                        "Failed to stop thread {}#{}:",
                        thread.getName(),
                        thread.getId(),
                        e
                );
                throw new RuntimeException(e);
            }
        }
        prometheusMetrics.close();
    }
}
