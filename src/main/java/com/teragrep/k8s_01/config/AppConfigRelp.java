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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* POJO representing the .relp part of config.json */
public class AppConfigRelp implements BaseConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(AppConfigRelp.class);
    public void handleOverrides() throws InvalidConfigurationException {
        String relpTarget = System.getenv("K8S_01_RELP_TARGET");
        if(relpTarget != null) {
            LOGGER.info(
                    "Found K8S_01_RELP_TARGET environment variable <[{}]>, using it relp target.",
                    relpTarget
            );
            this.target = relpTarget;
        }

        String relpPortString = System.getenv("K8S_01_RELP_PORT");
        if(relpPortString != null) {
            int relpPort;
            try {
                relpPort = Integer.parseInt(relpPortString);
            }
            catch(NumberFormatException e) {
                throw new InvalidConfigurationException(
                        "Got invalid number for K8S_01_RELP_PORT: ",
                        e
                );
            }
            LOGGER.info(
                    "Found K8S_01_RELP_PORT environment variable <[{}]>, using it relp port.",
                    relpPort
            );
            this.port = relpPort;
        }
    }
    private String target;
    private Integer port;
    private Integer connectionTimeout;
    private Integer readTimeout;
    private Integer writeTimeout;
    private Integer reconnectInterval;
    private AppConfigRelpTls tls;

    public String getTarget() {
        return target;
    }

    public Integer getPort() {
        return port;
    }

    public Integer getConnectionTimeout() {
        return connectionTimeout;
    }

    public Integer getReadTimeout() {
        return readTimeout;
    }

    public Integer getWriteTimeout() {
        return writeTimeout;
    }

    public Integer getReconnectInterval() {
        return reconnectInterval;
    }

    public AppConfigRelpTls getTls() {
        return tls;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }

    @Override
    public void validate() throws InvalidConfigurationException {
        if(target == null) {
            throw new InvalidConfigurationException("target not found or is null in relp config object");
        }

        if(port == null) {
            throw new InvalidConfigurationException("port not found or is null in relp config object");
        }
        if(port < 1 || port > 65535) {
            throw new InvalidConfigurationException("Relp port is invalid, expected integer between 1 and 65535");
        }

        if(connectionTimeout == null) {
            throw new InvalidConfigurationException("connectionTimeout not found or is null in relp config object");
        }
        if(connectionTimeout < 0) {
            throw new InvalidConfigurationException("Relp connection timeout is invalid, expected positive integer");
        }

        if(readTimeout == null) {
            throw new InvalidConfigurationException("readTimeout not found or is null in relp config object");
        }
        if(readTimeout < 0) {
            throw new InvalidConfigurationException("Relp read timeout is invalid, expected positive integer");
        }

        if(writeTimeout == null) {
            throw new InvalidConfigurationException("writeTimeout not found or is null in relp config object");
        }
        if(writeTimeout < 0) {
            throw new InvalidConfigurationException("Relp write timeout is invalid, expected positive integer");
        }

        if(tls == null) {
            throw new InvalidConfigurationException("tls not found or is null in relp config object");
        }
        tls.validate();
    }
}
