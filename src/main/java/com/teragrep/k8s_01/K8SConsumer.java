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

import com.cloudbees.syslog.Facility;
import com.cloudbees.syslog.SDElement;
import com.cloudbees.syslog.Severity;
import com.cloudbees.syslog.SyslogMessage;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.teragrep.k8s_01.config.AppConfig;
import com.teragrep.k8s_01.metadata.KubernetesMetadata;
import com.teragrep.k8s_01.metadata.NamespaceMetadataContainer;
import com.teragrep.k8s_01.metadata.PodMetadataContainer;
import com.teragrep.rlo_13.FileRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;

/**
 * Must be thread-safe
 */
public class K8SConsumer implements Consumer<FileRecord> {
    private static final Logger LOGGER = LoggerFactory.getLogger(K8SConsumer.class);
    private static final Gson gson = new Gson();
    private final AppConfig appConfig;
    private final KubernetesCachingAPIClient cacheClient;

    private final BlockingQueue<RelpOutput> relpOutputPool;

    K8SConsumer(
            AppConfig appConfig,
            KubernetesCachingAPIClient cacheClient,
            BlockingQueue<RelpOutput> relpOutputPool
    ) {
        this.appConfig = appConfig;
        this.cacheClient = cacheClient;
        this.relpOutputPool = relpOutputPool;
    }
    @Override
    public void accept(FileRecord record) {

            UUID uuid = java.util.UUID.randomUUID();
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
                    (int) (record.getStartOffset()+record.getRecord().length),
                    record.getEndOffset()
            );
            KubernetesLogFilePOJO log;
            try {
                // We want to read the kubernetes log event into a POJO, mostly for the timestamp
                log = gson.fromJson(new String(record.getRecord()), KubernetesLogFilePOJO.class);
            } catch (JsonSyntaxException e) {
                LOGGER.error(
                        "[{}] Can't continue as syntax for the event was invalid:",
                        uuid,
                        e
                );
                LOGGER.debug(
                        "[{}] Invalid syntax message: {}",
                        uuid,
                        new String(record.getRecord())
                );
                throw new RuntimeException(e);
            } catch (JsonParseException e) {
                LOGGER.error(
                        "[{}] Can't parse log event:",
                        uuid,
                        e
                );
                LOGGER.debug(
                        "[{}] Invalid json message: {}",
                        uuid,
                        new String(record.getRecord())
                );
                throw new RuntimeException(e);
            }

            // Log is in invalid format if timestamp is not available
            if(log == null || log.getTimestamp() == null) {
                LOGGER.debug(
                        "[{}] Can't parse this properly: {}",
                        uuid,
                        new String(record.getRecord())
                );
                throw new RuntimeException(
                    String.format(
                        "[%s] Can't parse record properly from %s/%s at %s",
                        uuid,
                        record.getPath(),
                        record.getFilename(),
                        record.getStartOffset()
                    )
                );
            }

            LOGGER.trace(
                    "[{}] Raw record value: {}",
                    uuid,
                    log
            );

            String namespace = ContainerInfo.getNamespace(record.getFilename());
            String podname = ContainerInfo.getPodname(record.getFilename());
            NamespaceMetadataContainer namespaceMetadataContainer = cacheClient.getNamespace(namespace);
            PodMetadataContainer podMetadataContainer = cacheClient.getPod(namespace, podname);
            KubernetesMetadata kubernetesMetadata = new KubernetesMetadata(
                    namespaceMetadataContainer,
                    podMetadataContainer,
                    ContainerInfo.getContainerName(record.getFilename()),
                    appConfig.getKubernetes().getUrl()
            );
            JsonObject dockerMetadata = new JsonObject();
            dockerMetadata.addProperty("container_id", ContainerInfo.getContainerID(record.getFilename()));

            // Handle hostname and appname, use fallback values when labels are empty or if label not found
            String hostname;
            String appname;
            if(podMetadataContainer.getLabels() == null) {
                LOGGER.warn(
                        "[{}] Can't resolve metadata and/or labels, using fallback values for hostname and appname",
                        uuid
                );
                hostname = appConfig.getKubernetes().getLabels().getHostname().getFallback();
                appname = appConfig.getKubernetes().getLabels().getAppname().getFallback();
            }
            else {
                hostname = podMetadataContainer.getLabels().getOrDefault(
                        appConfig.getKubernetes().getLabels().getHostname().getLabel(),
                        appConfig.getKubernetes().getLabels().getHostname().getFallback()
                );
                appname = podMetadataContainer.getLabels().getOrDefault(
                        appConfig.getKubernetes().getLabels().getAppname().getLabel(),
                        appConfig.getKubernetes().getLabels().getAppname().getFallback()
                );
            }
            LOGGER.debug(
                    "[{}] Resolved message to be {}@{} from {}/{} generated at {}",
                    uuid,
                    appname,
                    hostname,
                    namespace,
                    podname,
                    log.getTime()
            );

            // Craft syslog message and structured-data
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
                    .withTimestamp(log.getTimestamp())
                    .withSeverity(Severity.WARNING)
                    .withHostname(appConfig.getKubernetes().getLabels().getHostname().getPrefix() + hostname)
                    .withAppName(appConfig.getKubernetes().getLabels().getAppname().getPrefix() + appname)
                    .withFacility(Facility.USER)
                    .withSDElement(SDMetadata)
                    .withMsg(new String(record.getRecord()));
            try {
                RelpOutput output = relpOutputPool.take();
                output.send(syslog);
                relpOutputPool.put(output);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
    }
}
