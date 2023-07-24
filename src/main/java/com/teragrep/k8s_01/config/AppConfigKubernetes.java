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

/* POJO representing the .kubernetes part of config.json */
public class AppConfigKubernetes implements BaseConfig {
    private Integer cacheExpireInterval;
    private Integer cacheMaxEntries;
    private String logdir;
    private AppConfigLabels labels;
    private String[] logfiles;
    private String url;
    private String timezone;

    public Integer getCacheExpireInterval() {
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
    public Integer getCacheMaxEntries() {
        return cacheMaxEntries;
    }
    public String getTimezone() { return timezone; }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }

    @Override
    public void validate() throws InvalidConfigurationException {
        if(cacheExpireInterval == null) {
            throw new InvalidConfigurationException("cacheExpireInterval not found or is null in kubernetes config object");
        }
        if(cacheMaxEntries == null) {
            throw new InvalidConfigurationException("cacheMaxEntries not found or is null in kubernetes config object");
        }
        if(logdir == null) {
            throw new InvalidConfigurationException("logdir not found or is null in kubernetes config object");
        }
        if(labels == null) {
            throw new InvalidConfigurationException("labels not found or is null in kubernetes config object");
        }
        labels.validate();

        if(logfiles == null) {
            throw new InvalidConfigurationException("logfiles not found or is null in kubernetes config object");
        }
        for (String logfile : logfiles) {
            if (logfile == null) {
                throw new InvalidConfigurationException("Found null logfile definition in configuration file, expected string");
            }
        }

        if(url == null) {
            throw new InvalidConfigurationException("url not found or is null in kubernetes config object");
        }

        if(timezone == null) {
            throw new InvalidConfigurationException("timezone not found or is null in kubernetes config object");
        }
    }
}
