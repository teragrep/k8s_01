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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class KubernetesLogReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesLogReader.class);
    static Gson gson = new Gson();
    public static void main(String[] args) throws IOException, InterruptedException {
        AppConfig appConfig;
        try {
            try(InputStreamReader isr = new InputStreamReader(Files.newInputStream(Paths.get("etc/config.json")), StandardCharsets.UTF_8)) {
                appConfig = gson.fromJson(
                    isr,
                    AppConfig.class
                );
            }
        }
        catch (NoSuchFileException | FileNotFoundException e) {
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
                    "Caught exception while handling config:",
                    e
            );
            return;
        }
        if (appConfig == null) {
            LOGGER.error("Unknown parsing failure happened with config 'etc/config.json', can't continue. Check if the configuration file is empty?");
            return;
        }
        try {
            appConfig.getRelp().handleOverrides();
            appConfig.validate();
        }
        catch (InvalidConfigurationException e) {
            LOGGER.error(
                    "Failed to validate config 'etc/config.json':",
                    e
            );
            return;
        }

        String apiAddress = System.getenv("KUBERNETES_SERVICE_HOST");
        String apiPort = System.getenv("KUBERNETES_SERVICE_PORT");
        if(apiAddress == null || apiPort == null) {
            LOGGER.error("Can't resolve KUBERNETES_SERVICE_HOST and/or KUBERNETES_SERVICE_PORT environment variables, can't continue.");
            return;
        }
        String apiUrl = String.format("https://%s:%s", apiAddress, apiPort);

        if(appConfig.getKubernetes().getLabels().getWhitelist().isEnabled()) {
            LOGGER.warn(
                    "WARNING: Event whitelisting is enabled. This will collect data only from pods that has label <[{}]> that is equal to \"true\".",
                    appConfig.getKubernetes().getLabels().getWhitelist().getLabel()
            );
        }
        KubernetesCachingAPIClient cacheClient = new KubernetesCachingAPIClient(appConfig.getKubernetes(), apiUrl);
        PrometheusMetrics prometheusMetrics = new PrometheusMetrics(appConfig.getMetrics().getPort());

        // Pool of Relp output threads to be shared by every consumer
        int logFileCount = appConfig.getKubernetes().getLogfiles().length;
        int outputThreads = appConfig.getKubernetes().getMaxLogReadingThreads() * logFileCount;
        LOGGER.info(
                "Found {} monitored logfile definitions, reading them with maximum of {} threads each.",
                logFileCount,
                appConfig.getKubernetes().getMaxLogReadingThreads()
        );

        BlockingQueue<RelpOutput> relpOutputPool = new LinkedBlockingDeque<>(outputThreads);
        LOGGER.info(
                "Starting {} Relp threads towards {}:{}, using tls: {}",
                outputThreads,
                appConfig.getRelp().getTarget(),
                appConfig.getRelp().getPort(),
                appConfig.getRelp().getTls().getEnabled()
        );
        for(int i=1; i <= outputThreads; i++) {
            try {
                LOGGER.debug(
                        "Adding RelpOutput thread #{}",
                        i
                );
                relpOutputPool.put(new RelpOutput(appConfig.getRelp(), i, prometheusMetrics.getRegistry()));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        // consumer supplier, returns always the same instance
        K8SConsumerSupplier consumerSupplier = new K8SConsumerSupplier(appConfig, cacheClient, relpOutputPool, apiUrl);
        String[] logfiles = appConfig.getKubernetes().getLogfiles();
        LOGGER.debug(
                "Monitored logfiles: {}",
                Arrays.toString(logfiles)
        );

        List<DirectoryEventWatcher> dews = new ArrayList<>();
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

        // Graceful shutdown so Relp sessions are gracefully terminated
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Shutting down.");
            for(DirectoryEventWatcher dew : dews) {
                LOGGER.debug("Shutting down dew " + dew);
                try {
                    dew.stop();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            LOGGER.info(
                    "Disconnecting {} relp threads",
                    outputThreads
            );
            for(int i=1; i <= outputThreads; i++) {
                LOGGER.debug(
                        "Disconnecting relp thread #{}/{}",
                        i,
                        outputThreads
                );
                RelpOutput output;
                try {
                    output = relpOutputPool.take();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                output.disconnect();
            }
            prometheusMetrics.close();
            statefulFileReader.close();
            LOGGER.info("Goodbye");
        }, "ShutdownHook"));

        // Start a new thread for all logfile watchers
        for (String logfile : logfiles) {
            LOGGER.debug(
                    "Starting new DirectoryEventWatcher on directory '{}' with pattern '{}'",
                    appConfig.getKubernetes().getLogdir(),
                    logfile
            );
            DirectoryEventWatcher dew = new DirectoryEventWatcher(
                    Paths.get(appConfig.getKubernetes().getLogdir()),
                    false,
                    Pattern.compile(logfile),
                    statefulFileReader,
                    500,
                    TimeUnit.MILLISECONDS,
                    appConfig.getKubernetes().getMaxLogReadingThreads()
            );
            dew.start();
            dews.add(dew);
        }
        Thread.sleep(Long.MAX_VALUE);
    }
}
