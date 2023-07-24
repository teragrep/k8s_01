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

/* POJO representing the .relp part of config.json */
public class AppConfigRelp implements BaseConfig {
    private String target;
    private int port;
    private int connectionTimeout;
    private int readTimeout;
    private int writeTimeout;
    private int reconnectInterval;
    private int outputThreads;

    public String getTarget() {
        return target;
    }

    public int getPort() {
        return port;
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public int getWriteTimeout() {
        return writeTimeout;
    }

    public int getReconnectInterval() {
        return reconnectInterval;
    }

    public int getOutputThreads() {
        return outputThreads;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }

    @Override
    public void validate() throws InvalidConfigurationException {
        if(port < 1 || port > 65535) {
            throw new InvalidConfigurationException("Relp port is invalid, expected integer between 1 and 65535");
        }
        if(connectionTimeout < 0) {
            throw new InvalidConfigurationException("Relp connection timeout is invalid, expected positive integer");
        }
        if(readTimeout < 0) {
            throw new InvalidConfigurationException("Relp read timeout is invalid, expected positive integer");
        }
        if(writeTimeout < 0) {
            throw new InvalidConfigurationException("Relp write timeout is invalid, expected positive integer");
        }
        if(outputThreads <= 0) {
            throw new InvalidConfigurationException("Relp output threads is invalid, expected >0");
        }
    }
}
