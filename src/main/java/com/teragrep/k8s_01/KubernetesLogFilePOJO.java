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

    public Date getTimestamp() {
        // 2023-03-30T14:37:39.776175466Z
        DateTimeFormatter dateTimeFormatter = new DateTimeFormatterBuilder()
                .appendPattern("yyyy-MM-dd'T'HH:mm:ss.")
                .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, false)
                .appendPattern("X")
                .toFormatter();
        LocalDateTime localDateTime = LocalDateTime.parse(time, dateTimeFormatter);
        return Date.from(localDateTime.toInstant(ZoneOffset.ofHours(0))); // FIXME: The timestamp offset needs to be verified, we do not want to edit it?
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
