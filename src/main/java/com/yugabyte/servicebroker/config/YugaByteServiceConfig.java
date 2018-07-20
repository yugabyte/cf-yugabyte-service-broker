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
package com.yugabyte.servicebroker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class YugaByteServiceConfig {
  @Value("${yugabyte.admin.host:localhost}")
  public String hostname;

  @Value("${yugabyte.admin.port:9000}")
  public String port;

  @Value("${yugabyte.admin.user:cf_admin}")
  public String user;

  @Value("${yugabyte.admin.password:cf_password}")
  public String password;

  @Value("${yugabyte.service.id:yugabyte-service-broker}")
  public String serviceId;

  @Value("${yugabyte.service.name:YugaByte DB Service Broker}")
  public String serviceName;

  @Value("${yugabyte.service.description:Service Broker for Managing YugaByte DB}")
  public String serviceDescription;
}
