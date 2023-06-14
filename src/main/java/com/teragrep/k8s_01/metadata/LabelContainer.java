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

import java.util.Map;

/* Helper for converting maps into jsonObjects */
public class LabelContainer {
    JsonObject jsonObject;
    public LabelContainer(Map<String, String> labels) {
        jsonObject = new JsonObject();
        // We are not sure if API always returns labels, so we just return early.
        if(labels == null) {
            return;
        }
        for(Map.Entry<String, String> entry : labels.entrySet()) {
            jsonObject.addProperty(entry.getKey(), entry.getValue());
        }
    }

    public JsonObject getObject() {
        return jsonObject;
    }
}
