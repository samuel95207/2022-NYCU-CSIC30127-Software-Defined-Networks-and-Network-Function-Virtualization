/*
 * Copyright 2020-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nctu.winlab.unicastdhcp;

import org.onosproject.core.ApplicationId;
import org.onosproject.net.config.Config;

public class ServerConfig extends Config<ApplicationId> {

    public static final String SERVICE_LOCATION = "serverLocation";

    @Override
    public boolean isValid() {
        return hasOnlyFields(SERVICE_LOCATION);
    }

    public String serverLocation() {
        return get(SERVICE_LOCATION, null);
    }

    public String serverSwitchId() {
        String location = get(SERVICE_LOCATION, null);
        if (location == null) {
            return null;
        }
        return location.split("/")[0];
    }

    public String serverSwitchPort() {
        String location = get(SERVICE_LOCATION, null);
        if (location == null) {
            return null;
        }
        return location.split("/")[1];
    }
}
