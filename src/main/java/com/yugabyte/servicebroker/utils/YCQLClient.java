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

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Session;
import com.google.common.net.HostAndPort;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.yugabyte.servicebroker.utils.CommonUtils.generateRandomString;

public class YCQLClient extends YBClient {
  private static String DEFAULT_CASSANDRA_USER = "cassandra";
  private static String DEFAULT_CASSANDRA_PASSWORD = "cassandra";
  private static int DEFAULT_YCQL_PORT = 9042;

  private Session session;
  public YCQLClient(List<HostAndPort> serviceHosts) {
    super(serviceHosts);

    Cluster.Builder builder = Cluster.builder();
    getServiceHostPorts().forEach( serviceIpPort -> {
      builder.addContactPointsWithPorts(new InetSocketAddress(
          serviceIpPort.getHostText(),
          serviceIpPort.getPortOrDefault(DEFAULT_YCQL_PORT)
      ));
    });
    builder.withCredentials(DEFAULT_CASSANDRA_USER, DEFAULT_CASSANDRA_PASSWORD);
    Cluster cluster = builder.build();
    session = cluster.connect();
  }

  @Override
  protected int getDefaultPort() {
    return DEFAULT_YCQL_PORT;
  }

  @Override
  protected Map<String, String> createAuth() {
    // Cassandra username are all lowercase and avoid numbers in the beginning.
    String username = generateRandomString(true).toLowerCase();
    String password = generateRandomString(false);
    String createRole = "CREATE ROLE " + username + " with superuser = false " +
        "and login = true and password = '" + password + "'";
    session.execute(createRole);
    session.close();
    Map<String, String> credentials = new HashMap();
    credentials.put("username", username);
    credentials.put("password", password);
    return credentials;
  }
}