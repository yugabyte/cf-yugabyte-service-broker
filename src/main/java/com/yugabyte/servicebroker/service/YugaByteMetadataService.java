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
package com.yugabyte.servicebroker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yugabyte.servicebroker.exception.YugaByteServiceException;
import com.yugabyte.servicebroker.service.common.PlanMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.servicebroker.model.catalog.Plan;
import org.springframework.cloud.servicebroker.model.instance.CreateServiceInstanceRequest;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class YugaByteMetadataService {

  private YugaByteAdminService adminService;

  // TODO: Do we want to get these from parameters?
  private static int DEFAULT_NUM_NODES = 3;
  private static int DEFAULT_REPLICATION_FACTOR = 3;
  // TODO: change to this EE version once that is available.
  private static String DEFAULT_YB_RELEASE = "latest";

  // YugaByte Admin Console metadata
  private JsonNode provider;
  private JsonNode regions;
  private JsonNode instanceTypes;

  @Autowired
  public YugaByteMetadataService(YugaByteAdminService adminService) {
    this.adminService = adminService;
    fetchMetadata();
  }

  // YugaByte Admin org.yb.servicebroker.common metadata APIs
  private void fetchMetadata() {
    String url = String.format("%s/providers", adminService.getApiBaseUrl());
    JsonNode response = adminService.doGet(url);
    provider = StreamSupport.stream(response.spliterator(), false )
        .findFirst().orElse(null);
    if (provider != null) {
      String providerBaseUrl = String.format("%s/providers/%s",
          adminService.getApiBaseUrl(), provider.get("uuid").asText());
      regions = adminService.doGet(String.format("%s/regions", providerBaseUrl));
      instanceTypes = adminService.doGet(String.format("%s/instance_types", providerBaseUrl));
    } else {
      throw new YugaByteServiceException("Unable to fetch Provider Metadata");
    }
  }

  // YugaByte Admin Universe metadata APIs
  public JsonNode getClusterPayload(CreateServiceInstanceRequest request) {
    String instanceId = request.getServiceInstanceId();
    Plan requestedPlan = getPlan(request.getPlanId());

    if (requestedPlan == null) {
      throw new YugaByteServiceException("Invalid Plan Id: " + request.getPlanId() );
    }
    Map<String, Object> parameters = request.getParameters();

    // We will override the universe name if a parameter is passed.
    String universeName = "universe-" + instanceId.substring(0, 5);
    if (parameters != null && parameters.containsKey("universe_name")) {
      universeName = parameters.get("universe_name").toString();
    }

    ObjectMapper mapper = new ObjectMapper();
    ArrayNode clusters = mapper.createArrayNode();

    // Create PRIMARY
    ObjectNode primaryCluster = mapper.createObjectNode();
    primaryCluster.put("clusterType", "PRIMARY");

    ObjectNode userIntent = mapper.createObjectNode();
    userIntent.put("instanceType", requestedPlan.getId());
    userIntent.put("numNodes", DEFAULT_NUM_NODES);
    userIntent.put("provider", provider.get("uuid").asText());
    userIntent.put("providerType", provider.get("code").asText());

    List<String> regionUUIDs = StreamSupport.stream(regions.spliterator(), false )
        .map( r -> r.get("uuid").asText())
        .collect(Collectors.toList());
    userIntent.set("regionList", mapper.valueToTree(regionUUIDs));
    userIntent.put("replicationFactor", DEFAULT_REPLICATION_FACTOR);
    userIntent.put("universeName", universeName);
    userIntent.put("ybSoftwareVersion", DEFAULT_YB_RELEASE);

    ObjectNode deviceInfo = mapper.createObjectNode();
    deviceInfo.put("volumeSize",
        requestedPlan.getMetadata().get("volumeSizeGB").toString());
    deviceInfo.put("numVolumes",
        requestedPlan.getMetadata().get("numVolumes").toString());
    userIntent.set("deviceInfo", deviceInfo);

    primaryCluster.set("userIntent", userIntent);
    clusters.add(primaryCluster);
    ObjectNode payload = mapper.createObjectNode();
    payload.set("clusters", clusters);
    payload.put("currentClusterType", "PRIMARY");
    payload.put("clusterOperation", "CREATE");
    payload.put("userAZSelected",  false);

    return payload;
  }

  // Service Broker Catalog metadata APIs
  public List<Plan> getPlans() {
    return StreamSupport.stream(instanceTypes.spliterator(), false)
      .map(instanceType -> {
        PlanMetadata metadata = PlanMetadata.fromInstanceType(instanceType);

        return Plan.builder()
            .id(instanceType.get("instanceTypeCode").asText())
            .name(instanceType.get("instanceTypeCode").asText())
            .description(metadata.asText())
            .metadata(metadata.asMap())
            .free(true)
            .build();
      }).collect(Collectors.toList());
  }

  private Plan getPlan(String planId) {
    Optional<Plan> requestedPlan = getPlans().stream().filter( (plan ) ->
        plan.getId().equals(planId)).findFirst();
    return requestedPlan.orElse(null);
  }

  public Map<String, Object> getServiceMetadata() {
    Map<String, Object> ybMetadata = new HashMap<>();
    ybMetadata.put("displayName", "YugaByte DB");
    ybMetadata.put("imageUrl", "https://assets.yugabyte.com/yugabyte_full_logo.png");
    ybMetadata.put("longDescription", "YugaByte DB Service");
    ybMetadata.put("providerDisplayName", "YugaByte DB");
    ybMetadata.put("documentationUrl", "https://docs.yugabyte.com");
    ybMetadata.put("supportUrl", "mailto:support@yugabyte.com");
    return ybMetadata;
  }
}
