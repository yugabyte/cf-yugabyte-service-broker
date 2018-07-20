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
package com.yugabyte.servicebroker.service.common;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Map;

public class PlanMetadata {
  Integer numCores;
  Integer memSizeGB;
  Integer volumeSizeGB;
  Integer numVolumes;

  public Map<String, Object> asMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("numCores", numCores);
    map.put("memSizeGB", memSizeGB);
    map.put("volumeSizeGB", volumeSizeGB);
    map.put("numVolumes", numVolumes);
    return map;
  }

  public String asText() {
    return String.format("Cores: %s, Memory (GB): %d, Volumes: %d x %s",
        numCores, memSizeGB, numVolumes, volumeSizeGB);
  }

  public static PlanMetadata fromInstanceType(JsonNode instanceType) {
    JsonNode volumeDetailsList = instanceType
        .get("instanceTypeDetails")
        .get("volumeDetailsList");

    PlanMetadata metadata = new PlanMetadata();
    metadata.numCores = instanceType.get("numCores").asInt();
    metadata.memSizeGB = instanceType.get("numCores").asInt();
    metadata.volumeSizeGB = volumeDetailsList.get(0).get("volumeSizeGB").asInt();
    metadata.numVolumes = volumeDetailsList.size();
    return metadata;
  }
}
