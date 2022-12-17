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
package nycu.sdnfv.vrouter;

import java.util.List;

import org.onosproject.core.ApplicationId;
import org.onosproject.net.config.Config;

public class RouterConfig extends Config<ApplicationId> {

    public static final String QUAGGA_LOCATION = "quagga";
    public static final String QUAGGA_MAC = "quagga-mac";
    public static final String VIRTUAL_IP = "virtual-ip";
    public static final String VIRTUAL_MAC = "virtual-mac";
    public static final String PEERS = "peers";



    public String quaggaLocation() {
        return get(QUAGGA_LOCATION, null);
    }

    public String quaggaMac() {
        return get(QUAGGA_MAC, null);
    }

    public String virtualIp() {
        return get(VIRTUAL_IP, null);
    }

    public String virtualMac() {
        return get(VIRTUAL_MAC, null);
    }

    public List<String> peers() {
        return getList(PEERS, (String s) -> s);
    }


}
