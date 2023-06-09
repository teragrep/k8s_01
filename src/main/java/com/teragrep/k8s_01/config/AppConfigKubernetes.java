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

/* POJO representing the .kubernetes part of config.json */
public class AppConfigKubernetes {
    private int cacheExpireInterval;
    private int cacheMaxEntries;
    private String logdir;
    private AppConfigLabels labels;
    private String[] logfiles;
    private String url;

    public int getCacheExpireInterval() {
        return cacheExpireInterval;
    }

    public String getLogdir() {
        return logdir;
    }

    public AppConfigLabels getLabels() {
        return labels;
    }

    public String[] getLogfiles() {
        return logfiles;
    }

    public String getUrl() {
        return url;
    }
    public int getCacheMaxEntries() {
        return cacheMaxEntries;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
