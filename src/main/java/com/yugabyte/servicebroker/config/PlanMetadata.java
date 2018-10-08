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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@EnableConfigurationProperties
@ConfigurationProperties(prefix="yugabyte.catalog.plans")
public class PlanMetadata {
  private String code;
  private String name;
  public Integer cores;
  public Integer memory;
  public Map<String, String> cloudInstanceType;

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Integer getCores() {
    return cores;
  }

  public void setCores(Integer cores) {
    this.cores = cores;
  }

  public Integer getMemory() {
    return memory;
  }

  public void setMemory(Integer memory) {
    this.memory = memory;
  }

  public Map<String, String> getCloudInstanceType() {
    return cloudInstanceType;
  }

  public void setCloudInstanceType(Map<String, String> cloudInstanceType) {
    this.cloudInstanceType = cloudInstanceType;
  }

  @Override
  public String toString() {
    return "Cores: " + cores + ", Memory (GB):" + memory;
  }

  public Map<String, Object> asMap() {
    HashMap<String, Object> metadataMap = new HashMap<>();
    metadataMap.put("cores", getCores());
    metadataMap.put("memory", getMemory());
    metadataMap.put("cloud_instance_type", getCloudInstanceType());
    return metadataMap;
  }

  public String getInstanceType(String cloud) {
    return getCloudInstanceType().getOrDefault(cloud, null);
  }
}
