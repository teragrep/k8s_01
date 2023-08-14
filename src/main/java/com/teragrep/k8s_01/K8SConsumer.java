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

import com.teragrep.rlo_14.Facility;
import com.teragrep.rlo_14.SDElement;
import com.teragrep.rlo_14.Severity;
import com.teragrep.rlo_14.SyslogMessage;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.teragrep.k8s_01.config.AppConfig;
import com.teragrep.k8s_01.metadata.KubernetesMetadata;
import com.teragrep.k8s_01.metadata.NamespaceMetadataContainer;
import com.teragrep.k8s_01.metadata.PodMetadataContainer;
import com.teragrep.rlo_13.FileRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Must be thread-safe
 */
public class K8SConsumer implements Consumer<FileRecord> {
    private static final Logger LOGGER = LoggerFactory.getLogger(K8SConsumer.class);
    private static final Gson gson = new Gson();
    private final AppConfig appConfig;
    private final KubernetesCachingAPIClient cacheClient;

    private final BlockingQueue<RelpOutput> relpOutputPool;
    private static final DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSxxx");
    private final ZoneId timezoneId;

    // Validators
    private static final Pattern hostnamePattern = Pattern.compile("^[a-zA-Z0-9.-]+$"); // Not perfect but filters basically all mistakes
    private static final Pattern appNamePattern = Pattern.compile("^[\\x21-\\x7e]+$"); // DEC 33 - DEC 126 as specified in RFC5424
    private final boolean discardEnabled;
    private final String discardLabel;
    private final String apiUrl;
    private final SDElement SDuserMetadata;
    K8SConsumer(
            AppConfig appConfig,
            KubernetesCachingAPIClient cacheClient,
            BlockingQueue<RelpOutput> relpOutputPool,
            String apiUrl
    ) {
        this.appConfig = appConfig;
        this.cacheClient = cacheClient;
        this.relpOutputPool = relpOutputPool;
        this.apiUrl = apiUrl;
        this.timezoneId = ZoneId.of(appConfig.getKubernetes().getTimezone());
        this.discardEnabled = appConfig.getKubernetes().getLabels().getDiscard().isEnabled();
        this.discardLabel = appConfig.getKubernetes().getLabels().getDiscard().getLabel();
        SDuserMetadata = new SDElement("user_metadata@48577");
        appConfig.getKubernetes().getMetadata().forEach(SDuserMetadata::addSDParam);
    }
    @Override
    public void accept(FileRecord record) {

            UUID uuid = java.util.UUID.randomUUID();
            if(LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        "[{}] Got a new record from file: {}/{}",
                        uuid,
                        record.getPath(),
                        record.getFilename()
                );
                LOGGER.debug(
                        "[{}] Reading {} starting from {}, file progress {}/{}",
                        uuid,
                        record.getRecord().length,
                        record.getStartOffset(),
                        (int) (record.getStartOffset() + record.getRecord().length),
                        record.getEndOffset()
                );
            }
            String namespace = ContainerInfo.getNamespace(record.getFilename());
            String podname = ContainerInfo.getPodname(record.getFilename());
            String containerId = ContainerInfo.getContainerID(record.getFilename());
            KubernetesLogFilePOJO log;
            try {
                // We want to read the kubernetes log event into a POJO
                log = gson.fromJson(new String(record.getRecord(), StandardCharsets.UTF_8), KubernetesLogFilePOJO.class);
            } catch (JsonParseException e) {
                LOGGER.trace(
                        "[{}] Invalid syntax message: {}",
                        uuid,
                        new String(record.getRecord(), StandardCharsets.UTF_8)
                );
                throw new RuntimeException(
                        String.format(
                                "[%s] Event from pod <%s>/<%s> on container <%s> in file <%s/%s> offset <%s> can't be parsed properly: %s",
                                uuid,
                                namespace,
                                podname,
                                containerId,
                                record.getPath(),
                                record.getFilename(),
                                record.getStartOffset(),
                                e.getMessage()
                        )
                );
            }

            // Log is in invalid format if timestamp is not available
            if(log == null || log.getTimestamp() == null) {
                LOGGER.debug(
                        "[{}] Can't parse this properly: {}",
                        uuid,
                        new String(record.getRecord(), StandardCharsets.UTF_8)
                );
                throw new RuntimeException(
                    String.format(
                        "[%s] Didn't find expected values for event from pod <%s/%s> on container <%s> in file %s/%s at offset %s",
                        uuid,
                        namespace,
                        podname,
                        containerId,
                        record.getPath(),
                        record.getFilename(),
                        record.getStartOffset()
                    )
                );
            }
            Instant instant;
            try {
                instant = Instant.parse(log.getTimestamp());
            }
            catch(DateTimeParseException e) {
                throw new RuntimeException(
                        String.format(
                                "[%s] Can't parse timestamp <%s> properly for event from pod <[%s]/[%s]> on container <%s> in file %s/%s at offset %s: ",
                                uuid,
                                log.getTimestamp(),
                                namespace,
                                podname,
                                containerId,
                                record.getPath(),
                                record.getFilename(),
                                record.getStartOffset()
                        ),
                        e
                );
            }
            if(instant == null) {
                throw new RuntimeException(
                        String.format(
                                "[%s] Unknown failure while parsing timestamp <%s> for event from pod <[%s]/[%s]> on container <%s> in file %s/%s at offset %s",
                                uuid,
                                log.getTimestamp(),
                                namespace,
                                podname,
                                containerId,
                                record.getPath(),
                                record.getFilename(),
                                record.getStartOffset()
                        )
                );
            }
            ZonedDateTime zdt = instant.atZone(timezoneId);
            String timestamp = zdt.format(format);

            LOGGER.trace(
                    "[{}] Raw record value: {}",
                    uuid,
                    log
            );

            NamespaceMetadataContainer namespaceMetadataContainer = cacheClient.getNamespace(namespace);
            PodMetadataContainer podMetadataContainer = cacheClient.getPod(namespace, podname);
            if(discardEnabled) {
                if(
                        podMetadataContainer.getLabels() != null
                        && podMetadataContainer.getLabels().containsKey(discardLabel)
                        && podMetadataContainer.getLabels().get(discardLabel).equalsIgnoreCase("true")
                ) {
                    LOGGER.debug(
                            "[{}] Discarding event from pod <{}/{}> on container <{}>",
                            uuid,
                            namespace,
                            podname,
                            containerId
                    );
                    return;
                }
            }
            KubernetesMetadata kubernetesMetadata = new KubernetesMetadata(
                    namespaceMetadataContainer,
                    podMetadataContainer,
                    ContainerInfo.getContainerName(record.getFilename()),
                    apiUrl
            );
            JsonObject dockerMetadata = new JsonObject();
            dockerMetadata.addProperty("container_id", containerId);

            // Handle hostname and appName, use fallback values when labels are empty or if label not found
            String hostname;
            String appName;
            if(podMetadataContainer.getLabels() == null) {
                LOGGER.warn(
                        "[{}] Can't resolve metadata and/or labels for container <{}>, using fallback values for hostname and appName",
                        uuid,
                        containerId
                );
                hostname = appConfig.getKubernetes().getLabels().getHostname().getFallback();
                appName = appConfig.getKubernetes().getLabels().getAppName().getFallback();
            }
            else {
                hostname = podMetadataContainer.getLabels().getOrDefault(
                        appConfig.getKubernetes().getLabels().getHostname().getLabel(log.getStream()),
                        appConfig.getKubernetes().getLabels().getHostname().getFallback()
                );
                appName = podMetadataContainer.getLabels().getOrDefault(
                        appConfig.getKubernetes().getLabels().getAppName().getLabel(log.getStream()),
                        appConfig.getKubernetes().getLabels().getAppName().getFallback()
                );
            }

            if(!hostnamePattern.matcher(hostname).matches()) {
                throw new RuntimeException(
                        String.format(
                                "[%s] Detected hostname <[%s]> from pod <[%s]/[%s]> on container <%s> contains invalid characters, can't continue",
                                uuid,
                                hostname,
                                namespace,
                                podname,
                                containerId
                        )
                );
            }

            if(hostname.length() >= 255) {
                throw new RuntimeException(
                        String.format(
                                "[%s] Detected hostname <[%s]...> from pod <[%s]/[%s]> on container <%s> is too long, can't continue",
                                uuid,
                                hostname.substring(0,30),
                                namespace,
                                podname,
                                containerId
                        )
                );
            }

            if(!appNamePattern.matcher(appName).matches()) {
                throw new RuntimeException(
                        String.format(
                                "[%s] Detected appName <[%s]> from pod <[%s]/[%s]> on container <%s> contains invalid characters, can't continue",
                                uuid,
                                appName,
                                namespace,
                                podname,
                                containerId
                        )
                );
            }
            if(appName.length() > 48) {
                throw new RuntimeException(
                        String.format(
                                "[%s] Detected appName <[%s]...> from pod <[%s]/[%s]> on container <%s> is too long, can't continue",
                                uuid,
                                appName.substring(0,30),
                                namespace,
                                podname,
                                containerId
                        )
                );
            }

            if(LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        "[{}] Resolved message to be {}@{} from {}/{} generated at {}",
                        uuid,
                        appName,
                        hostname,
                        namespace,
                        podname,
                        timestamp
                );
            }

            // Craft syslog message and structured-data
            SDElement SDOrigin = new SDElement("origin@48577")
                    .addSDParam("hostname", podMetadataContainer.getHost() + "/" + containerId);
            SDElement SDMetadata = new SDElement("kubernetesmeta@48577")
                    .addSDParam("kubernetes", kubernetesMetadata.toString())
                    .addSDParam("docker", dockerMetadata.toString())
                    .addSDParam("stream", log.getStream());
            LOGGER.trace(
                    "[{}] Kubernetes metadata: {}",
                    uuid,
                    kubernetesMetadata
            );
            LOGGER.trace(
                    "[{}] Docker metadata: {}",
                    uuid,
                    dockerMetadata
            );
            SyslogMessage syslog = new SyslogMessage()
                    .withTimestamp(timestamp, true)
                    .withSeverity(Severity.WARNING)
                    .withHostname(hostname)
                    .withAppName(appName)
                    .withFacility(Facility.USER)
                    .withSDElement(SDuserMetadata)
                    .withSDElement(SDOrigin)
                    .withSDElement(SDMetadata)
                    .withMsg(new String(record.getRecord(), StandardCharsets.UTF_8));
            try {
                RelpOutput output = relpOutputPool.take();
                output.send(syslog);
                relpOutputPool.put(output);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
    }
}
