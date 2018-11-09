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
import com.yugabyte.servicebroker.model.YugaByteConfig;
import com.yugabyte.servicebroker.repository.YugaByteConfigRepository;
import org.apache.commons.collections.map.HashedMap;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public abstract class YBClient {
  public enum ClientType {
    YCQL,
    YEDIS
  }

  private List<HostAndPort> serviceHostPorts;
  protected List<HostAndPort> getServiceHostPorts() { return serviceHostPorts; }
  protected abstract int getDefaultPort();
  protected abstract Map<String, String> createAuth();
  public abstract void deleteAuth(Map<String, String> credentials);

  private YugaByteConfigRepository yugaByteConfigRepository;

  @Autowired
  public YBClient(List<HostAndPort> serviceHosts, YugaByteConfigRepository yugaByteConfigRepository) {
    this.serviceHostPorts = serviceHosts;
    this.yugaByteConfigRepository = yugaByteConfigRepository;
  }

  protected Map<String, String> getAdminCredentials(String clientType) {
    System.out.println(yugaByteConfigRepository);
    Optional<YugaByteConfig> config = yugaByteConfigRepository.findById(clientType);
    if (config.isPresent()) {
      return config.get().getConfig();
    }
    return new HashedMap();
  }

  protected void setAdminCredentials(String clientType, Map<String, String> credentials) {
    YugaByteConfig newConfig = new YugaByteConfig(clientType, credentials);
    yugaByteConfigRepository.save(newConfig);
  }

  public Map<String, String> getCredentials() {
    String serviceHost = serviceHostPorts.stream().map(sh -> sh.getHostText()).collect(Collectors.joining( "," ));
    int servicePort = serviceHostPorts.get(0).getPortOrDefault(getDefaultPort());
    Map<String, String> credentials =  createAuth();
    credentials.put("host", serviceHost);
    credentials.put("port", String.valueOf(servicePort));
    return credentials;
  }

  public static YBClient getClientForType(ClientType type, List<HostAndPort> serviceHosts,
                                          YugaByteConfigRepository yugaByteConfigRepository) {
    switch (type) {
      case YCQL:
        return new YCQLClient(serviceHosts, yugaByteConfigRepository);
      case YEDIS:
        return new YEDISClient(serviceHosts, yugaByteConfigRepository);
    }
    return null;
  }
}
