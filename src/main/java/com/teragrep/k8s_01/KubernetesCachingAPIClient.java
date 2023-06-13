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
import com.google.common.cache.*;
import com.teragrep.k8s_01.config.AppConfigKubernetes;
import com.teragrep.k8s_01.metadata.NamespaceMetadataContainer;
import com.teragrep.k8s_01.metadata.PodMetadataContainer;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.util.Config;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


class KubernetesCachingAPIClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(KubernetesCachingAPIClient.class);

    private final CoreV1Api api;
    private LoadingCache<HashMap<String, String>, PodMetadataContainer> podCache;
    private LoadingCache<String, NamespaceMetadataContainer> namespaceCache;
    private final AppConfigKubernetes config;

    KubernetesCachingAPIClient(AppConfigKubernetes appConfigKubernetes) throws IOException {
        config = appConfigKubernetes;
        LOGGER.info(
                "Starting Caching API Client using {}",
                config.getUrl()
        );
        LOGGER.info(
                "Keeping up to {} cached entries for {} seconds before evicting.",
                config.getCacheMaxEntries(),
                config.getCacheExpireInterval()
        );
        try {
            ApiClient client = Config.fromCluster();
            client.setBasePath(config.getUrl());
            Configuration.setDefaultApiClient(client);
            api = new CoreV1Api();
        } catch (Exception e) {
            LOGGER.error("Encountered an error while initializing the API Client, refusing to continue.");
            throw e;
        }
        buildPodLoader();
        buildNamespaceLoader();
    }

    private void buildPodLoader() {
        CacheLoader<HashMap<String, String>, PodMetadataContainer> podCacheLoader = new CacheLoader<HashMap<String, String>, PodMetadataContainer>() {
            @NotNull
            @Override
            public PodMetadataContainer load(@NotNull HashMap<String, String> key) throws Exception {
                LOGGER.debug(
                        "Cache miss for pod: {}",
                        key
                );
                return fetchPod(key.get("namespace"), key.get("id"));
            }
        };

        RemovalListener<HashMap<String, String>, PodMetadataContainer> listener = removalNotification -> {
            if (removalNotification.wasEvicted()) {
                if(LOGGER.isDebugEnabled()) {
                    LOGGER.debug(
                            "Evicted pod {} from cache: {}",
                            removalNotification.getKey(),
                            removalNotification.getCause().name()
                    );
                }
            }
        };
        podCache = CacheBuilder
                .newBuilder()
                .expireAfterWrite(config.getCacheExpireInterval(), TimeUnit.SECONDS)
                .maximumSize(config.getCacheMaxEntries())
                .removalListener(listener)
                .build(podCacheLoader);
    }

    private void buildNamespaceLoader() {
        CacheLoader<String, NamespaceMetadataContainer> namespaceCacheLoader = new CacheLoader<String, NamespaceMetadataContainer>() {
            @NotNull
            @Override
            public NamespaceMetadataContainer load(@NotNull String namespace) throws ApiException {
                LOGGER.debug(
                        "Cache miss for namespace: {}",
                        namespace
                );
                return fetchNamespace(namespace);
            }
        };

        RemovalListener<String, NamespaceMetadataContainer> listener = removalNotification -> {
            if (removalNotification.wasEvicted()) {
                if(LOGGER.isDebugEnabled()) {
                    LOGGER.debug(
                            "Evicted namespace {} from cache: {}",
                            removalNotification.getKey(),
                            removalNotification.getCause().name()
                    );
                }
            }
        };
        namespaceCache = CacheBuilder
                .newBuilder()
                .expireAfterWrite(config.getCacheExpireInterval(), TimeUnit.SECONDS)
                .maximumSize(config.getCacheMaxEntries())
                .removalListener(listener)
                .build(namespaceCacheLoader);
    }

    public PodMetadataContainer getPod(String namespace, String id) {
        HashMap<String, String> map = new HashMap<>();
        map.put("namespace", namespace);
        map.put("id", id);
        LOGGER.trace(
                "Getting pod: {}",
                map
        );
        return podCache.getUnchecked(map);
    }

    private PodMetadataContainer fetchPod(String namespace, String id) throws ApiException {
        LOGGER.debug(
                "Fetching pod metadata: {}/{}",
                namespace,
                id
        );
        return new PodMetadataContainer(api.readNamespacedPod(id, namespace, null));
    }
    public NamespaceMetadataContainer getNamespace(String namespace) {
        LOGGER.trace(
                "Getting namespace: {}",
                namespace
        );
        return namespaceCache.getUnchecked(namespace);
    }

    private NamespaceMetadataContainer fetchNamespace(String namespace) throws ApiException {
        LOGGER.debug(
                "Fetching namespace metadata: {}",
                namespace
        );
        return new NamespaceMetadataContainer(api.readNamespace(namespace, null));
    }
}
