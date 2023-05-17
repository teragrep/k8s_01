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

package com.teragrep.k8s_01.metadata;

import io.kubernetes.client.openapi.models.V1Pod;

import java.util.Map;

/* POJO for storing V1Pod information instead of the full object */
public class PodMetadataContainer {
    private final String creationTimestamp;
    private final String podId;
    private final Map<String, String> labels;
    private final String host;
    private final String podname;
    private final String namespaceName;
    public PodMetadataContainer(V1Pod pod) {
        if(pod.getMetadata() == null) {
            throw new RuntimeException("Pod metadata is empty, can't continue.");
        }
        if(pod.getSpec() == null) {
            throw new RuntimeException("Pod spec is empty, can't continue.");
        }
        creationTimestamp = String.valueOf(pod.getMetadata().getCreationTimestamp());
        podId = pod.getMetadata().getUid();
        labels = pod.getMetadata().getLabels();
        host = pod.getSpec().getNodeName();
        podname = pod.getMetadata().getName();
        namespaceName = pod.getMetadata().getNamespace();
    }

    public String getCreationTimestamp() {
        return creationTimestamp;
    }

    public String getPodId() {
        return podId;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public String getHost() {
        return host;
    }

    public String getPodname() {
        return podname;
    }

    public String getNamespaceName() {
        return namespaceName;
    }
}
