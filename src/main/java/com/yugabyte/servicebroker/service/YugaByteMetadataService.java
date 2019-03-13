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
import com.yugabyte.servicebroker.config.CatalogConfig;
import com.yugabyte.servicebroker.config.PlanMetadata;
import com.yugabyte.servicebroker.exception.YugaByteServiceException;
import com.yugabyte.servicebroker.utils.CommonUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.servicebroker.model.instance.AsyncParameterizedServiceInstanceRequest;
import org.springframework.cloud.servicebroker.model.instance.CreateServiceInstanceRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Service
public class YugaByteMetadataService {
  private static final Log logger = LogFactory.getLog(YugaByteMetadataService.class);
  
  private YugaByteAdminService adminService;
  private CatalogConfig catalogConfig;

  private static int DEFAULT_NUM_NODES = 3;
  private static int DEFAULT_REPLICATION_FACTOR = 3;
  private static int DEFAULT_NUM_VOLUMES = 1;
  private static int DEFAULT_VOLUME_SIZE_IN_GB = 100;
  private static String DEFAULT_PROVIDER = "kubernetes";
  private static String DEFAULT_KUBERNETES_PROVIDER = "pks";

  @Autowired
  public YugaByteMetadataService(YugaByteAdminService adminService,
                                 CatalogConfig catalogConfig) {
    this.adminService = adminService;
    this.catalogConfig = catalogConfig;
  }

  // YugaByte Admin org.yb.servicebroker.common metadata APIs
  private JsonNode fetchProvider(String providerType, String kubeProvider) {
    JsonNode response = adminService.getProviders();
    Iterator<JsonNode> it = response.iterator();
    while (it.hasNext()) {
      JsonNode provider = it.next();
      if (provider.get("code").asText().equals(providerType)) {
        if (providerType.equals("kubernetes")) {
            JsonNode providerConfig = provider.get("config");
            if (providerConfig.has("KUBECONFIG_PROVIDER") &&
                providerConfig.get("KUBECONFIG_PROVIDER").asText().equals(kubeProvider)) {
              return provider;
            }
        } else {
          return provider;
        }
      }
    }
    return null;
  }

  private List<String> fetchRegionUUIDs(UUID providerUUID, List<String> preferredRegions) {
    JsonNode response = adminService.getRegions(providerUUID);
    // Ideally we want user to provider the region they want to bring the universe
    // if they don't then we default pick the first region from the list of regions.
    if (!preferredRegions.isEmpty()) {
      return StreamSupport.stream(response.spliterator(), false )
          .filter( r -> preferredRegions.contains(r.get("code").asText()))
          .map( r -> r.get("uuid").asText())
          .collect(Collectors.toList());
    } else {
      return Collections.singletonList(response.get(0).get("uuid").asText());
    }
  }

  private List<String> fetchAccessKeys(UUID providerUUID) {
    JsonNode response = adminService.getAccessKeys(providerUUID);
    return StreamSupport.stream(response.spliterator(), false)
        .map( accessKey -> accessKey.get("idKey").get("keyCode").asText())
        .collect(Collectors.toList());
  }

  // YugaByte Admin Universe metadata APIs
  public JsonNode getClusterPayload(CreateServiceInstanceRequest request) {
    String instanceId = request.getServiceInstanceId();
    PlanMetadata requestedPlan = catalogConfig.getPlan(request.getPlanId());

    if (requestedPlan == null) {
      throw new YugaByteServiceException("Invalid CatalogConfig Id: " + request.getPlanId() );
    }
    Map<String, Object> parameters = request.getParameters();
    if (parameters == null) {
      parameters = new HashMap<>();
    }
    List<String> ybReleases = adminService.getReleases();
    // Only use the software version if the one that is passed is valid.
    if (parameters.containsKey("yb_version") &&
        !ybReleases.contains(parameters.get("yb_version").toString())) {
      throw new YugaByteServiceException("Invalid YB Software version.");
    }

    // Override the defaults based on parameters passed.
    String universeName =
        parameters.getOrDefault("universe_name",
            "service-instance-" + instanceId.substring(0, 8)).toString();

    if (!universeName.matches("^[a-zA-Z0-9-]*$")) {
      throw new YugaByteServiceException("Invalid Universe Name : " + universeName);
    }

    String ybSoftwareVersion =
        parameters.getOrDefault("yb_version", ybReleases.get(0)).toString();

    int numVolumes =
        Integer.parseInt(parameters.getOrDefault("num_volumes", DEFAULT_NUM_VOLUMES).toString());
    int volumeSizeGB =
        Integer.parseInt(parameters.getOrDefault("volume_size", DEFAULT_VOLUME_SIZE_IN_GB).toString());


    int numNodes =
        Integer.parseInt(parameters.getOrDefault("num_nodes", DEFAULT_NUM_NODES).toString());
    int replication =
        Integer.parseInt(parameters.getOrDefault("replication", DEFAULT_REPLICATION_FACTOR).toString());
    String providerType =
        parameters.getOrDefault("provider_type", DEFAULT_PROVIDER).toString();
    String kubeProvider =
        parameters.getOrDefault("kube_provider", DEFAULT_KUBERNETES_PROVIDER).toString();
    List<String> regionCodes = (ArrayList<String>) parameters.getOrDefault("region_codes", new ArrayList());

    JsonNode provider = fetchProvider(providerType, kubeProvider);
    if (provider == null) {
      throw new YugaByteServiceException("Unable to fetch Provider Metadata");
    }
    UUID providerUUID = UUID.fromString(provider.get("uuid").asText());
    List<String> regionUUIDs = fetchRegionUUIDs(providerUUID, regionCodes);
    if (regionUUIDs.isEmpty()) {
      throw new YugaByteServiceException("Unable to fetch Region Metadata");
    }
    // Fetch the accessKey for the provider.
    List<String> accessKeys = fetchAccessKeys(providerUUID);
    if (accessKeys.isEmpty() && !providerType.equals("kubernetes")) {
      throw new YugaByteServiceException("Unable to fetch AccessKey Information");
    }

    ObjectMapper mapper = new ObjectMapper();
    ArrayNode clusters = mapper.createArrayNode();

    // Create PRIMARY
    ObjectNode primaryCluster = mapper.createObjectNode();
    primaryCluster.put("clusterType", "PRIMARY");

    ObjectNode userIntent = mapper.createObjectNode();
    userIntent.put("instanceType", requestedPlan.getInstanceType(providerType));
    userIntent.put("numNodes", numNodes);
    userIntent.put("provider", provider.get("uuid").asText());
    userIntent.put("providerType", provider.get("code").asText());
    userIntent.set("regionList", mapper.valueToTree(regionUUIDs));
    userIntent.put("replicationFactor", replication);
    userIntent.put("universeName", universeName);
    userIntent.put("ybSoftwareVersion", ybSoftwareVersion);
    if (!accessKeys.isEmpty()) {
      userIntent.put("accessKeyCode", accessKeys.get(0));
    }

    ObjectNode deviceInfo = mapper.createObjectNode();
    deviceInfo.put("volumeSize", volumeSizeGB);
    deviceInfo.put("numVolumes", numVolumes);
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

  public JsonNode updateGflags(JsonNode currentPayload, AsyncParameterizedServiceInstanceRequest request) {
    Map<String, Object> parameters = request.getParameters();
    if (parameters == null) {
      parameters = new HashMap<>();
    }
    Optional<JsonNode> primaryCluster =
        CommonUtils.getCluster(currentPayload.get("clusters"), "PRIMARY");

    if (!primaryCluster.isPresent()) {
      // This is actually bad that we don't even have a primary cluster
      throw new YugaByteServiceException("Primary Cluster data not found.");
    }

    ObjectMapper mapper = new ObjectMapper();
    Map<String, Object> tserverFlags = (Map<String, Object>)
        parameters.getOrDefault("tserver_flags", new HashMap<>());
    Map<String, Object> masterFlags = (Map<String, Object>)
        parameters.getOrDefault("master_flags", new HashMap<>());

    ObjectNode primaryUserIntent = (ObjectNode) primaryCluster.get().get("userIntent");
    ArrayNode tserverGFlags = CommonUtils.convertGflagMapToJson(tserverFlags);
    ArrayNode masterGFlags = CommonUtils.convertGflagMapToJson(masterFlags);

    // If use_cassandra_authentication is not set explicitly, we default to true.
    if (!tserverFlags.containsKey("use_cassandra_authentication")) {
      ObjectNode authGFlag = mapper.createObjectNode();
      authGFlag.put("name", "use_cassandra_authentication");
      authGFlag.put("value", "true");
      tserverGFlags.add(authGFlag);
    }

    primaryUserIntent.set("masterGFlags", masterGFlags);
    primaryUserIntent.set("tserverGFlags", tserverGFlags);

    return currentPayload;
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
