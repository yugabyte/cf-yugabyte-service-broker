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
import com.google.common.collect.ImmutableMap;
import com.google.common.net.HostAndPort;
import com.yugabyte.servicebroker.repository.YugaByteConfigRepository;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.yugabyte.servicebroker.utils.CommonUtils.generateRandomString;

public class YCQLClient extends YBClient {
  private static final Log logger = LogFactory.getLog(YCQLClient.class);

  private static String DEFAULT_CASSANDRA_USER = "cassandra";
  private static String DEFAULT_CASSANDRA_PASSWORD = "cassandra";
  private String ADMIN_CREDENTIAL_KEY = "ycql-admin-user";
  private static int DEFAULT_YCQL_PORT = 9042;

  private enum CQLRole  {
    ADMIN,
    READ_ONLY;

    public String grantStatement() {
      switch (this) {
        case ADMIN:
          return "ALL PERMISSIONS ON ALL KEYSPACES TO ADMIN";
        case READ_ONLY:
          return "SELECT ON ALL KEYSPACES TO READ_ONLY";
      }
      return null;
    }
  }

  private Session session;

  public YCQLClient(List<HostAndPort> serviceHosts,
                    YugaByteConfigRepository yugaByteConfigRepository) {
    super(serviceHosts, yugaByteConfigRepository);
  }

  public Session getSession() {
    Cluster.Builder builder = Cluster.builder();
    getServiceHostPorts().forEach( serviceIpPort -> {
      builder.addContactPointsWithPorts(new InetSocketAddress(
          serviceIpPort.getHostText(),
          serviceIpPort.getPortOrDefault(DEFAULT_YCQL_PORT)
      ));
    });
    // We will save the admin credentials in our config table.
    Map<String, String> adminCreds = getAdminCredentials(ADMIN_CREDENTIAL_KEY);
    boolean hasAdminCreds = !adminCreds.isEmpty();
    if (!hasAdminCreds) {
      adminCreds = ImmutableMap.of(
          "username", DEFAULT_CASSANDRA_USER,
          "password", DEFAULT_CASSANDRA_PASSWORD
      );
      setAdminCredentials(ADMIN_CREDENTIAL_KEY, adminCreds);
    }
    builder.withCredentials(adminCreds.get("username"),
                            adminCreds.get("password"));
    Cluster cluster = builder.build();
    session = cluster.connect();
    createSystemRoles();
    return session;
  }

  private void createSystemRoles() {
    // We will also create two roles in the system, one is admin and other is
    // readonly, of course users can add their own roles and grant them.
    for (CQLRole role : CQLRole.values()) {
      String createRole = String.format("CREATE ROLE IF NOT EXISTS %s", role);
      session.execute(createRole);

      String grantPermission = String.format("GRANT %s", role.grantStatement());
      session.execute(grantPermission);
    }
  }

  @Override
  protected int getDefaultPort() {
    return DEFAULT_YCQL_PORT;
  }

  @Override
  protected Map<String, String> createAuth(Map<String, Object> parameters) {
    session = getSession();
    // Cassandra username are all lowercase and avoid numbers in the beginning.
    String username = generateRandomString(true).toLowerCase();
    String password = generateRandomString(false);
    String createRole = "CREATE ROLE " + username + " with superuser = false " +
        "and login = true and password = '" + password + "'";

    session.execute(createRole);
    String role = (String) parameters.getOrDefault("role", "admin");
    String grantRole = String.format("GRANT %s to %s", role, username);
    session.execute(grantRole);

    Map<String, String> credentials = new HashMap();
    credentials.put("username", username);
    credentials.put("password", password);
    return credentials;
  }

  @Override
  public void deleteAuth(Map<String, String> credentials) {
    if (!credentials.containsKey("username")) {
      logger.warn("Role name is empty in credentials: " + credentials);
    } else {
      session = getSession();
      String username = credentials.get("username");
      String dropRole = "DROP ROLE IF EXISTS " + username;
      session.execute(dropRole);
      session.close();
    }
  }
}