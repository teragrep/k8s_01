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

import java.io.*;
import java.nio.file.Files;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

public class AppConfigRelpTls implements BaseConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(AppConfigRelpTls.class);
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

        // Try to read and load the keystore
        try(InputStream is = Files.newInputStream(new File(keystore).toPath())) {
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(is, password.toCharArray());
        } catch (FileNotFoundException e) {
            LOGGER.error(
                    "keystore <[{}]> not found: ",
                    keystore,
                    e
            );
            throw new InvalidConfigurationException(e);
        } catch (IOException e) {
            LOGGER.error(
                    "Encountered an IOException with <[{}]>: ",
                    keystore,
                    e
            );
            throw new UncheckedIOException(e);
        }
        catch (KeyStoreException e) {
            LOGGER.error(
                    "keystore can't be instantiated: ",
                    e
            );
            throw new RuntimeException(e);
        }
        catch (NoSuchAlgorithmException | CertificateException e) {
            LOGGER.error(
                    "keystore <[{}]> can't be opened: ",
                    keystore,
                    e
            );
            throw new InvalidConfigurationException(e);
        }
    }
}
