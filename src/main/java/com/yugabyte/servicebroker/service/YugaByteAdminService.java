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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.net.HostAndPort;
import com.yugabyte.servicebroker.config.YugaByteServiceConfig;
import com.yugabyte.servicebroker.exception.YugaByteServiceException;
import com.yugabyte.servicebroker.model.ServiceBinding;
import com.yugabyte.servicebroker.model.ServiceInstance;
import com.yugabyte.servicebroker.repository.ServiceInstanceRepository;
import com.yugabyte.servicebroker.repository.YugaByteConfigRepository;
import com.yugabyte.servicebroker.utils.CommonUtils;
import com.yugabyte.servicebroker.utils.YBClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.servicebroker.exception.ServiceInstanceDoesNotExistException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class YugaByteAdminService {

  private YugaByteServiceConfig adminConfig;
  private ServiceInstanceRepository instanceRepository;
  private YugaByteConfigRepository yugaByteConfigRepository;

  private String authToken;
  private String customerUUID;

  @Autowired
  public YugaByteAdminService(YugaByteServiceConfig adminConfig,
                              ServiceInstanceRepository instanceRepository,
                              YugaByteConfigRepository yugaByteConfigRepository) {
    this.adminConfig = adminConfig;
    this.instanceRepository = instanceRepository;
    this.yugaByteConfigRepository = yugaByteConfigRepository;
    authenticate();
  }

  public String getApiBaseUrl() {
    if (customerUUID != null) {
      return String.format("http://%s:%s/api/customers/%s",
          adminConfig.hostname, adminConfig.port, customerUUID);
    } else {
      return String.format("http://%s:%s/api",
          adminConfig.hostname, adminConfig.port);
    }
  }

  public JsonNode doGet(String endpoint) {
    HttpGet getRequest = new HttpGet(endpoint);
    getRequest.setHeader("Accept", "application/json");
    getRequest.setHeader("Content-type", "application/json");
    if (authToken != null) {
      getRequest.setHeader("X-AUTH-TOKEN", authToken);
    }
    return makeRequest(getRequest);
  }

  public String doGetRaw(String endpoint) {
    HttpGet getRequest = new HttpGet(endpoint);
    getRequest.setHeader("Accept", "application/json");
    getRequest.setHeader("Content-type", "application/json");
    try {
      CloseableHttpClient client = HttpClients.createDefault();
      CloseableHttpResponse response = client.execute(getRequest);
      ResponseHandler<String> handler = new BasicResponseHandler();
      return handler.handleResponse(response);
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  public JsonNode doPost(String endpoint, JsonNode params) {
    HttpPost postRequest = new HttpPost(endpoint);
    try {
      StringEntity entity = new StringEntity(params.toString());
      postRequest.setHeader("Accept", "application/json");
      postRequest.setHeader("Content-Type", "application/json");
      if (authToken != null) {
        postRequest.setHeader("X-AUTH-TOKEN", authToken);
      }

      postRequest.setEntity(entity);
      return makeRequest(postRequest);
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    throw new YugaByteServiceException("Unable to make the POST request");
  }

  public JsonNode doDelete(String endpoint) {
    HttpDelete deleteRequest = new HttpDelete(endpoint);
    deleteRequest.setHeader("Accept", "application/json");
    deleteRequest.setHeader("Content-type", "application/json");
    deleteRequest.setHeader("X-AUTH-TOKEN", authToken);
    return makeRequest(deleteRequest);
  }

  private JsonNode makeRequest(HttpRequestBase request) {
    CloseableHttpClient client = HttpClients.createDefault();
    try {
      CloseableHttpResponse response = client.execute(request);
      ObjectMapper mapper = new ObjectMapper();
      return mapper.readTree(response.getEntity().getContent());
    } catch (IOException e) {
      e.printStackTrace();
    }
    throw new YugaByteServiceException("Unable to parse json");
  }

  private void authenticate() {
    String url = String.format("%s/login", getApiBaseUrl());
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode params = mapper.createObjectNode();
    params.put("email", this.adminConfig.user);
    params.put("password", this.adminConfig.password);

    JsonNode response = doPost(url, params);
    if (response != null) {
      authToken = response.get("authToken").asText();
      customerUUID = response.get("customerUUID").asText();
    } else {
      throw new YugaByteServiceException("Unable to authenticate to YugaByte Admin Console");
    }
  }

  public JsonNode configureUniverse(JsonNode params) {
    String url = String.format("%s/universe_configure", getApiBaseUrl());
    return doPost(url, params);
  }

  public JsonNode createUniverse(JsonNode params) {
    String url = String.format("%s/universes", getApiBaseUrl());
    return doPost(url, params);
  }

  public JsonNode getUniverse(String universeUUID) {
    String url = String.format("%s/universes/%s", getApiBaseUrl(), universeUUID);
    return doGet(url);
  }

  public JsonNode deleteUniverse(String universeUUID) {
    String url = String.format("%s/universes/%s", getApiBaseUrl(), universeUUID);
    return doDelete(url);
  }

  public JsonNode getUniverseByServiceInstance(String instanceId) {
    String universeUUID = getUniverseUUIDFromServiceInstance(instanceId);
    return getUniverse(universeUUID);
  }

  private String getUniverseUUIDFromServiceInstance(String instanceId) {
    Optional<ServiceInstance> serviceInstance = instanceRepository.findById(instanceId);

    if (!serviceInstance.isPresent()) {
      throw new ServiceInstanceDoesNotExistException(instanceId);
    }
    ServiceInstance si = serviceInstance.get();
    return si.getUniverseUUID();
  }

  private List<HostAndPort> getEndpointForServiceType(YBClient.ClientType serviceType, String universeUUID) {
    String url = null;
    switch (serviceType) {
      case YCQL:
        url = String.format("%s/universes/%s/yqlservers", getApiBaseUrl(), universeUUID);
        break;
      case YEDIS:
        url = String.format("%s/universes/%s/redisservers", getApiBaseUrl(), universeUUID);
        break;
    }
    String serverEndpointString =  doGetRaw(url);
    return CommonUtils.convertToHostPorts(
        serverEndpointString.replaceAll("^\"|\"$", "")
    );
  }

  public Map<String, Object> getUniverseServiceEndpoints(String instanceId) {
    String universeUUID = getUniverseUUIDFromServiceInstance(instanceId);
    Map<String, Object> endpoints = new HashMap<>();
    for (YBClient.ClientType clientType : YBClient.ClientType.values()) {
      List<HostAndPort> hostAndPorts = getEndpointForServiceType(clientType, universeUUID);
      YBClient ybClient = YBClient.getClientForType(clientType, hostAndPorts, yugaByteConfigRepository);
      try {
        endpoints.put(clientType.name().toLowerCase(), ybClient.getCredentials());
      } catch (Exception e) {}

    }
    return endpoints;
  }

  public void deleteServiceBindingCredentials(ServiceBinding serviceBinding) {
    serviceBinding.getCredentials().forEach((endpoint, credentials) -> {
      ObjectMapper mapper = new ObjectMapper();
      Map<String, String> credentialMap = mapper.convertValue(credentials, Map.class);
      String[] hosts = credentialMap.get("host").split(",");
      List<HostAndPort> hostAndPorts = new ArrayList<>();
      hostAndPorts.add(
          HostAndPort.fromParts(
              hosts[0],
              Integer.parseInt(credentialMap.get("port"))
          )
      );
      YBClient ybClient = YBClient.getClientForType(
          YBClient.ClientType.valueOf(endpoint.toUpperCase()),
          hostAndPorts,
          yugaByteConfigRepository
      );
      ybClient.deleteAuth(credentialMap);
    });
  }
}
