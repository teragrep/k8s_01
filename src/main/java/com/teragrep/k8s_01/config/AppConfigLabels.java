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

/* POJO representing the .kubernetes.labels part of config.json */
public class AppConfigLabels implements BaseConfig {
    private AppConfigLabel hostname;
    private AppConfigLabel appname; // Lowercase instead of appName because it comes from json and needs to be case-sensitive
    private AppConfigWhitelistLabel whitelist;

    public AppConfigLabel getHostname() {
        return hostname;
    }

    public AppConfigLabel getAppName() {
        return appname;
    }
    public AppConfigWhitelistLabel getWhitelist() {
        return whitelist;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }

    @Override
    public void validate() throws InvalidConfigurationException {
        if(hostname == null) {
            throw new InvalidConfigurationException("hostname not found or is null in labels config object");
        }
        hostname.validate();

        if(appname == null) {
            throw new InvalidConfigurationException("appname not found or is null in labels config object");
        }
        appname.validate();

        if(whitelist == null) {
            throw new InvalidConfigurationException("whitelist not found or is null in labels config object");
        }
        whitelist.validate();
    }
}
