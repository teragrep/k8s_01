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

import java.io.*;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

public class AppConfigRelpTls implements BaseConfig {
    private Boolean enabled;
    private String keystore;
    private String password;

    public Boolean getEnabled() {
        return enabled;
    }

    public String getKeystore() {
        return keystore;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }

    @Override
    public void validate() throws InvalidConfigurationException {
        if(enabled == null) {
            throw new InvalidConfigurationException("enabled not found or is null in relp tls config object");
        }
        if(!enabled) {
            return; // We do not check further if not enabled
        }
        if(password == null) {
            throw new InvalidConfigurationException("password not found or is null in relp tls config object");
        }
        if(keystore == null) {
            throw new InvalidConfigurationException("keystore not found or is null in relp tls config object");
        }

        // Try reading the file
        File keystorePath = new File(keystore);
        InputStream is;
        try {
            is = new FileInputStream(keystorePath);
        } catch (FileNotFoundException e) {
            throw new InvalidConfigurationException(
                    String.format(
                            "keystore <[%s]> not found",
                             keystore
                    ),
                    e
            );
        }
        // Try creating a keystore
        KeyStore ks;
        try {
            ks = KeyStore.getInstance(KeyStore.getDefaultType());
        } catch (KeyStoreException e) {
            throw new InvalidConfigurationException(
                    "keystore can't be instantiated",
                    e
            );
        }
        // Try loading the keystore with provided password
        try {
            ks.load(is, password.toCharArray());
        } catch (IOException | NoSuchAlgorithmException | CertificateException e) {
            throw new InvalidConfigurationException(
                    String.format(
                            "keystore <[%s]> can't be parsed",
                            keystore
                    ),
                    e
            );
        }
    }
}
