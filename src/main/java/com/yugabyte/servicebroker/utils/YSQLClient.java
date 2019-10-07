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
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.yugabyte.servicebroker.utils.CommonUtils.generateRandomString;

public class YSQLClient extends YBClient {
  private static final Log logger = LogFactory.getLog(YSQLClient.class);

  private static String DEFAULT_YSQL_USER = "yugabyte";
  private static String DEFAULT_YSQL_PASSWORD = "yugabyte";
  private String ADMIN_CREDENTIAL_KEY = "ysql-admin-user";
  private static int DEFAULT_YSQL_PORT = 5433;

  private enum YSQLRole  {
    SUPERUSER("superuser_role"),
    NOSUPERUSER("user_role");

    public String value;
    private YSQLRole(String role) {
        this.value = role;
    }
  }

  private Connection connection;

  public YSQLClient(List<HostAndPort> serviceHosts,
                    YugaByteConfigRepository yugaByteConfigRepository) {
    super(serviceHosts, yugaByteConfigRepository);
  }

  private void getConnection() {
    HostAndPort initialHostPort = getServiceHostPorts().get(0);
    String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s", initialHostPort.getHostText(),
            initialHostPort.getPortOrDefault(getDefaultPort()), DEFAULT_YSQL_USER);

    try {
      connection = DriverManager.getConnection(jdbcUrl, DEFAULT_YSQL_USER, DEFAULT_YSQL_PASSWORD);
    } catch (SQLException e) {
      logger.error("Failed to connect to database: " + e);
      throw new RuntimeException(e);
    }
    createSystemRoles();
  }

  private void createSystemRoles() {
    // We will also create two roles in the system, one is admin and other is
    // readonly, of course users can add their own roles and grant them.
    String checkRoleStatement = "SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = '%s'";
    String createRoleStatement = "CREATE ROLE %s %s";
    for (YSQLRole role : YSQLRole.values()) {
      ResultSet rs = executeQuery(String.format(checkRoleStatement, role.value));
      try{
        if (!rs.next()) {
          executeUpdate(String.format(createRoleStatement, role.value, role));
        } else {
        }
      } catch (SQLException e) {
          logger.error(String.format("Failed to find roles: " + e));
          throw new RuntimeException(e);
      }
    }
  }

  private void executeUpdate(String query) {
    try {
      connection.createStatement().executeUpdate(query);
    } catch (SQLException e) {
      logger.error(String.format("Failed to execute query: '%s'. Error: %s", query, e));
      throw new RuntimeException(e);
    }
  }

  private ResultSet executeQuery(String query) {
    try {
      return connection.createStatement().executeQuery(query);
    } catch (SQLException e) {
      logger.error(String.format("Failed to execute query: '%s'. Error: %s", query, e));
      throw new RuntimeException(e);
    }
  }

  @Override
  protected int getDefaultPort() {
    return DEFAULT_YSQL_PORT;
  }

  @Override
  protected Map<String, String> createAuth(Map<String, Object> parameters) {
    getConnection();
    String username = generateRandomString(true).toLowerCase();
    String password = generateRandomString(false);
    executeUpdate(String.format("CREATE ROLE %s LOGIN PASSWORD '%s'", username, password));

    String role = (String) parameters.getOrDefault("role", YSQLRole.SUPERUSER.value);
    executeUpdate(String.format("GRANT %s to %s", role, username));

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
      getConnection();
      String username = credentials.get("username");
      String dropRole = "DROP ROLE IF EXISTS " + username;
      executeUpdate(dropRole);
      try {
        connection.close();
      } catch (SQLException e) {
        logger.error("Failed to close YSQL connection: " + e);
        throw new RuntimeException(e);
      }
    }
  }
}