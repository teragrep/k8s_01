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

package com.teragrep.k8s_01;

// Helper class for getting some information out of filename
public class ContainerInfo {
    // third-pod_default_third-pod-one-e1705519dbdb87214d6575ce740a313345cbee915681d66ada0143b8860a7302.log
    // ^^^^^^^^^
    static String getPodname(String filename) {
        return filename.split("_")[0];
    }
    // third-pod_default_third-pod-one-e1705519dbdb87214d6575ce740a313345cbee915681d66ada0143b8860a7302.log
    //          ^^^^^^^^^
    static String getNamespace(String filename) {
        return filename.split("_")[1];
    }
    // third-pod_default_third-pod-one-e1705519dbdb87214d6575ce740a313345cbee915681d66ada0143b8860a7302.log
    //                   ^^^^^^^^^^^^^
    static String getContainerName(String filename) {
        String subpath = filename.split("_")[2];
        return subpath.substring(0, subpath.lastIndexOf("-"));
    }

    // third-pod_default_third-pod-one-e1705519dbdb87214d6575ce740a313345cbee915681d66ada0143b8860a7302.log
    //                                 ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
    static String getContainerID(String filename) {
        String subpath = filename.split("_")[2];
        return subpath.substring(subpath.lastIndexOf("-")+1, subpath.lastIndexOf("."));
    }
}
