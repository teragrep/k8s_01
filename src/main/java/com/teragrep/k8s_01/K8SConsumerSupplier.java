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

import com.teragrep.k8s_01.config.AppConfig;
import com.teragrep.rlo_13.FileRecord;

import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class K8SConsumerSupplier implements Supplier<Consumer<FileRecord>> {
    private final AppConfig appConfig;
    private final KubernetesCachingAPIClient cacheClient;
    private final BlockingQueue<RelpOutput> relpOutputPool;
    private final String apiUrl;

    K8SConsumerSupplier(
            AppConfig appConfig,
            KubernetesCachingAPIClient cacheClient,
            BlockingQueue<RelpOutput> relpOutputPool,
            String apiUrl
    ) {
        this.appConfig = appConfig;
        this.cacheClient = cacheClient;
        this.relpOutputPool = relpOutputPool;
        this.apiUrl = apiUrl;
    }

    @Override
    public Consumer<FileRecord> get() {
        return new K8SConsumer(appConfig, cacheClient, relpOutputPool, apiUrl);
    }
}
