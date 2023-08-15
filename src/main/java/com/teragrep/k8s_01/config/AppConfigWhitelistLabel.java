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

import com.teragrep.k8s_01.InvalidConfigurationException;

public class AppConfigWhitelistLabel implements BaseConfig {
    private Boolean enabled;


    private String label;

    public Boolean isEnabled() {
        return enabled;
    }

    public String getLabel() {
        return label;
    }

    @Override
    public void validate() throws InvalidConfigurationException {
        if(enabled == null) {
            throw new InvalidConfigurationException("enabled not found or is null in discard labels config object");
        }
        if(label == null) {
            throw new InvalidConfigurationException("label not found or is null in discard labels config object");
        }
    }
}
