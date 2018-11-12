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

import com.google.common.net.HostAndPort;
import com.yugabyte.servicebroker.repository.YugaByteConfigRepository;
import redis.clients.jedis.Jedis;

import java.util.List;
import java.util.Map;

import static com.yugabyte.servicebroker.utils.CommonUtils.generateRandomString;

public class YEDISClient extends YBClient {
  private static int DEFAULT_YEDIS_PORT = 6379;
  private String ADMIN_CREDENTIAL_KEY = "yedis-admin-user";
  private Jedis client;

  @Override
  protected int getDefaultPort() {
    return DEFAULT_YEDIS_PORT;
  }

  public YEDISClient(List<HostAndPort> serviceHosts,
                     YugaByteConfigRepository yugaByteConfigRepository) {
    super(serviceHosts, yugaByteConfigRepository);
    HostAndPort hostAndPort = serviceHosts.get(0);
    client = new Jedis(hostAndPort.getHostText(), hostAndPort.getPortOrDefault(DEFAULT_YEDIS_PORT));
    client.connect();
  }

  @Override
  protected Map<String, String> createAuth() {
    // We would only call this method the first time to create a auth, after that, we would just fetch
    // the auth from yugabyte_config table.
    Map<String, String> credentials = getAdminCredentials(
        ADMIN_CREDENTIAL_KEY
    );

    // If we have credentials in the yugabyte config table, we would just return that credential.
    if (!credentials.isEmpty()) {
      return credentials;
    }

    String password = generateRandomString(false);
    client.configSet("requirepass", password);
    client.flushAll();
    client.disconnect();
    credentials.put("password", password);
    // We will save the admin credentials in our config table.
    setAdminCredentials(ADMIN_CREDENTIAL_KEY, credentials);
    return credentials;
  }

  @Override
  // In case of redis the deleteAuth is noop today, because we don't cycle the passwords.
  public void deleteAuth(Map<String, String> credentials) {
    return;
  }
}