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

import com.google.gson.JsonObject;

/* Crafts a structured-data appropriate response using MetadataContainers */
public class KubernetesMetadata {
    JsonObject jsonObject;

    public KubernetesMetadata(NamespaceMetadataContainer namespace, PodMetadataContainer pod, String containerName, String basepath) {
        jsonObject = new JsonObject();
        jsonObject.addProperty("namespace_id", namespace.getUid());
        jsonObject.add("namespace_labels", new LabelContainer(namespace.getLabels()).getObject());
        jsonObject.addProperty("creation_timestamp", pod.getCreationTimestamp());
        jsonObject.addProperty("pod_id", pod.getPodId());
        jsonObject.add("labels", new LabelContainer(pod.getLabels()).getObject());
        jsonObject.addProperty("host", pod.getHost());
        jsonObject.addProperty("podname", pod.getPodname());
        jsonObject.addProperty("namespace_name", pod.getNamespaceName());
        jsonObject.addProperty("container_name", containerName); // FIXME: Check if this is correct
        jsonObject.addProperty("master_url", basepath); // FIXME: Check if this is correct
    }

    @Override
    public String toString() {
        return jsonObject.toString();
    }
}
