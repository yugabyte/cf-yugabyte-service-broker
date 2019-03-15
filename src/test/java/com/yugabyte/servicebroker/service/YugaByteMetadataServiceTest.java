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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.yugabyte.servicebroker.YugaByteServiceTestConfig;
import com.yugabyte.servicebroker.config.CatalogConfig;
import com.yugabyte.servicebroker.config.PlanMetadata;
import com.yugabyte.servicebroker.utils.CommonUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.cloud.servicebroker.model.instance.CreateServiceInstanceRequest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = YugaByteServiceTestConfig.class)
public class YugaByteMetadataServiceTest {

  @InjectMocks
  private YugaByteMetadataService metadataService;

  @Mock
  private CatalogConfig mockCatalogConfig;

  @Mock
  private YugaByteAdminService mockAdminService;

  private Map<String, String> cloudInstanceTypeMap = ImmutableMap.of("aws", "c4.large", "gcp", "n1-standard-2", "kubernetes", "xsmall");

  private UUID setupCloudMetadata(String providerType, String kubeProvider) {
    UUID providerUUID = UUID.randomUUID();
    UUID regionUUID = UUID.randomUUID();
    ObjectMapper mapper = new ObjectMapper();
    try {
      String config = "{}";
      if (providerType.equals("kubernetes")) {
        config = "{\"KUBECONFIG_PROVIDER\": \"" + kubeProvider + "\"}";
      }

      when(mockAdminService.getProviders()).thenReturn(mapper.readTree(
          "[{\"uuid\": \"" + providerUUID.toString() + "\", \"code\":\"" + providerType + "\",\"config\":" + config + "}]"));
      when(mockAdminService.getRegions(providerUUID)).thenReturn(
          mapper.readTree("[{\"uuid\":\"" + regionUUID + "\"}]")
      );
      when(mockAdminService.getAccessKeys(providerUUID)).thenReturn(
          mapper.readTree("[{\"idKey\":{\"keyCode\":\"yb-demo-key\",\"providerUUID\":\""+ providerUUID.toString() + "\"}}]")
      );
    } catch (IOException e) {
      e.printStackTrace();
    }
    return providerUUID;
  }

  private CreateServiceInstanceRequest getServiceRequest(Map<String, Object> params) {
    PlanMetadata planMetadata = new PlanMetadata();
    planMetadata.setCode("xsmall");
    planMetadata.setCloudInstanceType(cloudInstanceTypeMap);
    when(mockCatalogConfig.getPlan("xsmall")).thenReturn(planMetadata);
    when(mockAdminService.getReleases()).thenReturn(ImmutableList.of("1.0.1", "1.0.0"));

    return CreateServiceInstanceRequest.builder()
        .serviceInstanceId("yugabyte")
        .planId("xsmall")
        .build();
  }

  private void assertResponse(UUID providerUUID, String providerType,
                              Map<String, Object> overrides, JsonNode response) {
    JsonNode clusters = response.get("clusters");
    assertNotNull(clusters);
    System.out.println(clusters);
    assertEquals(1, clusters.size());
    JsonNode primaryCluster =  clusters.get(0);
    assertNotNull(primaryCluster);
    JsonNode userIntent = primaryCluster.get("userIntent");
    JsonNode deviceInfo = userIntent.get("deviceInfo");
    assertEquals("PRIMARY", primaryCluster.get("clusterType").asText());
    assertEquals(providerUUID.toString(), userIntent.get("provider").asText());
    assertEquals(providerType, userIntent.get("providerType").asText());
    assertEquals(cloudInstanceTypeMap.get(providerType), userIntent.get("instanceType").asText());
    assertEquals("PRIMARY", response.get("currentClusterType").asText());
    assertEquals("CREATE", response.get("clusterOperation").asText());
    assertEquals("false", response.get("userAZSelected").asText());

    if (overrides == null) {
      assertEquals("3", userIntent.get("numNodes").asText());
      assertEquals("service-instance-yugabyte", userIntent.get("universeName").asText());
      assertEquals("1.0.1", userIntent.get("ybSoftwareVersion").asText());
      assertEquals("100", deviceInfo.get("volumeSize").asText());
      assertEquals("1", deviceInfo.get("numVolumes").asText());
    } else {
      for (Map.Entry<String, Object> override: overrides.entrySet()) {
        if (override.getKey().equals("volumeSize") || override.getKey().equals("numVolumes")) {
          assertEquals(override.getValue(), deviceInfo.get(override.getKey()).asText());
        } else {
          assertEquals(override.getValue(), userIntent.get(override.getKey()).asText());
        }
      }
    }
  }

  @Test
  public void testGetClusterNoParams() {
    UUID providerUUID = setupCloudMetadata("kubernetes", "pks");
    CreateServiceInstanceRequest request = getServiceRequest(null);
    JsonNode response = metadataService.getClusterPayload(request);
    assertResponse(providerUUID, "kubernetes", null, response);
  }

  @Test
  public void testGetClusterSuccess() {
    for (String providerType : cloudInstanceTypeMap.keySet()) {
      String kubeProvider = providerType.equals("kubernetes") ? "gke" : null;
      UUID providerUUID = setupCloudMetadata(providerType, kubeProvider);
      CreateServiceInstanceRequest request = getServiceRequest(null);
      Map<String, Object> params = request.getParameters();
      params.put("provider_type", providerType);
      if (kubeProvider != null) {
        params.put("kube_provider", "gke");
      }
      JsonNode response = metadataService.getClusterPayload(request);
      assertResponse(providerUUID, providerType, null, response);
    }
  }

  @Test
  public void testGetClusterWithOverrides() {
    for (String providerType : cloudInstanceTypeMap.keySet()) {
      String kubeProvider = providerType.equals("kubernetes") ? "gke" : null;
      UUID providerUUID = setupCloudMetadata(providerType, kubeProvider);
      CreateServiceInstanceRequest request = getServiceRequest(null);
      Map<String, Object> params = request.getParameters();
      params.put("provider_type", providerType);
      params.put("num_nodes", 2);
      params.put("replication", 1);
      params.put("num_volumes", 2);
      params.put("volume_size", 50);

      if (kubeProvider != null) {
        params.put("kube_provider", "gke");
      }
      Map<String, Object> overrides = ImmutableMap.of(
          "numNodes", "2",
          "replicationFactor", "1",
          "numVolumes", "2",
          "volumeSize", "50"
      );

      JsonNode response = metadataService.getClusterPayload(request);
      assertResponse(providerUUID, providerType, overrides, response);
    }
  }

  @Test
  public void testUpdateGflags() {
    CreateServiceInstanceRequest request = getServiceRequest(null);
    Map<String, Object> params = request.getParameters();
    params.putAll(ImmutableMap.of(
        "tserver_flags", ImmutableMap.of("foo", "bar"),
        "master_flags", ImmutableMap.of("foo", "bar")
    ));
    ObjectMapper mapper = new ObjectMapper();
    try {
      JsonNode currentPayload = mapper.readTree("{\"clusters\": [{\"clusterType\":\"PRIMARY\",\"userIntent\":{\"instanceType\":\"xsmall\",\"numNodes\":3,\"provider\":\"57b9b5b8-411a-48e0-bb70-14f0c1fb83a3\",\"providerType\":\"aws\",\"regionList\":[\"3848f4e4-1cc3-4341-aa11-7725606328de\"],\"replicationFactor\":3,\"universeName\":\"service-instance-yugabyte\",\"ybSoftwareVersion\":\"1.0.1\",\"accessKeyCode\":\"yb-demo-key\",\"deviceInfo\":{\"volumeSize\":100,\"numVolumes\":1}}}]}");
      JsonNode response = metadataService.updateGflags(currentPayload, request);
      Optional<JsonNode> primaryCluster =
          CommonUtils.getCluster(response.get("clusters"), "PRIMARY");
      assertTrue(primaryCluster.isPresent());
      JsonNode userIntent = primaryCluster.get().get("userIntent");
      List<Map<String, String>> masterFlags = mapper.treeToValue(userIntent.get("masterGFlags"), List.class);
      List<Map<String, String>> tserverFlags = mapper.treeToValue(userIntent.get("tserverGFlags"), List.class);
      Map expectedDummyFlags = ImmutableMap.of("name", "foo", "value", "bar");
      assertEquals(1, masterFlags.size());
      assertEquals(expectedDummyFlags, masterFlags.get(0));
      assertEquals(2, tserverFlags.size());
      assertTrue(tserverFlags.contains(expectedDummyFlags));
      Map expectedCassandraAuthFlag = ImmutableMap.of("name", "use_cassandra_authentication", "value", "true");
      assertTrue(tserverFlags.contains(expectedCassandraAuthFlag));
    } catch (IOException e) {
        assertNull(e.getMessage());
    }
  }


  @Test
  public void testUpdateGflagsOverrideAuth() {
    CreateServiceInstanceRequest request = getServiceRequest(null);
    Map<String, Object> params = request.getParameters();
    params.putAll(ImmutableMap.of(
        "tserver_flags", ImmutableMap.of("use_cassandra_authentication", "false")
    ));
    ObjectMapper mapper = new ObjectMapper();
    try {
      JsonNode currentPayload = mapper.readTree("{\"clusters\": [{\"clusterType\":\"PRIMARY\",\"userIntent\":{\"instanceType\":\"xsmall\",\"numNodes\":3,\"provider\":\"57b9b5b8-411a-48e0-bb70-14f0c1fb83a3\",\"providerType\":\"aws\",\"regionList\":[\"3848f4e4-1cc3-4341-aa11-7725606328de\"],\"replicationFactor\":3,\"universeName\":\"service-instance-yugabyte\",\"ybSoftwareVersion\":\"1.0.1\",\"accessKeyCode\":\"yb-demo-key\",\"deviceInfo\":{\"volumeSize\":100,\"numVolumes\":1}}}]}");
      JsonNode response = metadataService.updateGflags(currentPayload, request);
      Optional<JsonNode> primaryCluster =
          CommonUtils.getCluster(response.get("clusters"), "PRIMARY");
      assertTrue(primaryCluster.isPresent());
      JsonNode userIntent = primaryCluster.get().get("userIntent");
      List<Map<String, String>> masterFlags = mapper.treeToValue(userIntent.get("masterGFlags"), List.class);
      List<Map<String, String>> tserverFlags = mapper.treeToValue(userIntent.get("tserverGFlags"), List.class);
      assertEquals(0, masterFlags.size());
      assertEquals(1, tserverFlags.size());
      Map expectedCassandraAuthFlag = ImmutableMap.of("name", "use_cassandra_authentication", "value", "false");
      assertTrue(tserverFlags.contains(expectedCassandraAuthFlag));
    } catch (IOException e) {
      assertNull(e.getMessage());
    }
  }

  @Test
  public void testGetServiceMetadata() {
    Map actualMap = metadataService.getServiceMetadata();
    Map expectedMap = new HashMap();

    expectedMap.put("displayName", "YugaByte DB");
    expectedMap.put("imageUrl", "https://assets.yugabyte.com/yugabyte_full_logo.png");
    expectedMap.put("longDescription", "YugaByte DB Service");
    expectedMap.put("providerDisplayName", "YugaByte DB");
    expectedMap.put("documentationUrl", "https://docs.yugabyte.com");
    expectedMap.put("supportUrl", "mailto:support@yugabyte.com");
    assertEquals(expectedMap, actualMap);
  }
}