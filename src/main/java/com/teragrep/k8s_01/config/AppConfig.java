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
import com.teragrep.k8s_01.InvalidConfigurationException;

/* POJO representing the main config.json */
public class AppConfig implements BaseConfig {
    private AppConfigKubernetes kubernetes;

    public AppConfigMetrics getMetrics() {
        return metrics;
    }

    private AppConfigMetrics metrics;
    private AppConfigRelp relp;

    public AppConfigKubernetes getKubernetes() {
        return kubernetes;
    }

    public AppConfigRelp getRelp() {
        return relp;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }

    @Override
    public void validate() throws InvalidConfigurationException {
        getMetrics().validate();
        getKubernetes().validate();
        getRelp().validate();
    }
}
