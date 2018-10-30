/* Copyright (c) YugaByte, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied.  See the License for the specific language governing permissions and limitations
 * under the License.
 */
package com.yugabyte.servicebroker.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.HostAndPort;
import com.yugabyte.servicebroker.model.ServiceBinding;
import org.springframework.beans.factory.annotation.Autowired;
import redis.clients.jedis.Jedis;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.yugabyte.servicebroker.utils.CommonUtils.generateRandomString;

public class YEDISClient extends YBClient {
  private static int DEFAULT_YEDIS_PORT = 6379;
  private Jedis client;
  List<ServiceBinding> bindings;

  @Override
  protected int getDefaultPort() {
    return DEFAULT_YEDIS_PORT;
  }

  @Autowired
  public YEDISClient(List<HostAndPort> serviceHosts) {
    super(serviceHosts);
    HostAndPort hostAndPort = serviceHosts.get(0);
    client = new Jedis(hostAndPort.getHostText(), hostAndPort.getPortOrDefault(DEFAULT_YEDIS_PORT));
    client.connect();
  }

  @Override
  protected Map<String, String> createAuth() {
    Map<String, String> credentials = new HashMap();
    // We would only call this method the first time to create a auth, after that, we would just fetch
    // the auth from existing service binding.
    ObjectMapper mapper = new ObjectMapper();
    if (bindings.size() > 0) {
      bindings.forEach((binding) -> {
        // We need to check this to handle older credentials which were not Map
        if (binding.getCredentials().get("yedis") instanceof Map) {
          Map<String, Object> oldCredentials = (Map<String, Object>) binding.getCredentials().get("yedis");
          if (oldCredentials.containsKey("password")) {
            credentials.put("password", oldCredentials.get("password").toString());
          }
        }
      });
    }
    if (credentials.isEmpty()) {
      String password = generateRandomString(false);
      client.configSet("requirepass", password);
      client.flushAll();
      client.disconnect();
      credentials.put("password", password);
    }
    return credentials;
  }

  public void setExistingBindings(List<ServiceBinding> bindings) {
    this.bindings = bindings;
  }
}