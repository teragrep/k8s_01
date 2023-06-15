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
import com.google.gson.Gson;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Date;

/* POJO presenting Kubernetes logs, for now used only for getting timestamp */
public class KubernetesLogFilePOJO {
    private String log;
    private String stream;
    private String time;

    public String getLog() {
        return log;
    }

    public String getStream() {
        return stream;
    }

    public String getTime() {
        return time;
    }

    public String getTimestamp() {
        return time;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
