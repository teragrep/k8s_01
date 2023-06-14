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

package com.teragrep.k8s_01.config;

import com.google.gson.Gson;

/* POJO representing the .kubernetes.labels.{hostname,appname} part of config.json */
public class AppConfigLabel {
    private String prefix;
    private String fallback;
    private String labelStdout;
    private String labelStderr;

    public String getPrefix() {
        return prefix;
    }

    public String getFallback() {
        return fallback;
    }

    public String getLabel(String label) {
        if(label.equals("stderr") && labelStderr != null) {
            return labelStderr;
        }
        return labelStdout;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
