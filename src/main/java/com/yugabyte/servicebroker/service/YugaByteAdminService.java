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
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.servicebroker.exception.ServiceInstanceDoesNotExistException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class YugaByteAdminService {
  private YugaByteServiceConfig adminConfig;
  private ServiceInstanceRepository instanceRepository;
  private YugaByteConfigRepository yugaByteConfigRepository;

  private String authToken = null;
  private String customerUUID = null;

  @Autowired
  public YugaByteAdminService(YugaByteServiceConfig adminConfig,
                              ServiceInstanceRepository instanceRepository,
                              YugaByteConfigRepository yugaByteConfigRepository) {
    this.adminConfig = adminConfig;
    this.instanceRepository = instanceRepository;
    this.yugaByteConfigRepository = yugaByteConfigRepository;
  }

  private String getApiUrl(String endpoint) {
    String url = String.format("http://%s:%s/api",
        adminConfig.hostname, adminConfig.port);

    if (customerUUID != null && !customerUUID.isEmpty()) {
      url = url.concat(String.format("/customers/%s", customerUUID));
    }

    if (endpoint != null && !endpoint.isEmpty()) {
      url = url.concat(String.format("/%s", endpoint));
    }
    return url;
  }

  private void updateHeaders(HttpRequestBase request) {
    request.setHeader("Accept", "application/json");
    request.setHeader("Content-type", "application/json");
    if (authToken != null && !authToken.isEmpty()) {
      request.setHeader("X-AUTH-TOKEN", authToken);
    }
  }

  private JsonNode doGet(String endpoint)  {
    validateAndRefreshToken();
    HttpGet getRequest = new HttpGet(getApiUrl(endpoint));
    updateHeaders(getRequest);

    return makeRequest(getRequest);
  }

  private String doGetRaw(String endpoint) {
    HttpGet getRequest = new HttpGet(getApiUrl(endpoint));
    updateHeaders(getRequest);

    try {
      CloseableHttpClient client = HttpClients.createDefault();
      CloseableHttpResponse response = client.execute(getRequest);
      return EntityUtils.toString(response.getEntity());
    } catch (IOException e) {
      e.printStackTrace();
    }
    return null;
  }

  private JsonNode doPost(String endpoint, JsonNode params) {
    validateAndRefreshToken();
    HttpPost postRequest = new HttpPost(getApiUrl(endpoint));
    try {
      StringEntity entity = new StringEntity(params.toString());
      updateHeaders(postRequest);
      postRequest.setEntity(entity);
      return makeRequest(postRequest);
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    throw new YugaByteServiceException("Unable to make the POST request");
  }

  private JsonNode doDelete(String endpoint) {
    HttpDelete deleteRequest = new HttpDelete(getApiUrl(endpoint));
    updateHeaders(deleteRequest);
    return makeRequest(deleteRequest);
  }

  private JsonNode makeRequest(HttpRequestBase request) {
    CloseableHttpClient client = HttpClients.createDefault();
    try {
      CloseableHttpResponse response = client.execute(request);
      String body = EntityUtils.toString(response.getEntity());
      ObjectMapper mapper = new ObjectMapper();
      return mapper.readTree(body);
    } catch (IOException e) {
      e.printStackTrace();
    }
    throw new YugaByteServiceException("Unable to parse json");
  }

  private void validateAndRefreshToken() {
    CloseableHttpClient client = HttpClients.createDefault();
    CloseableHttpResponse response = null;

    boolean invalidToken = (authToken == null);
    if (!invalidToken) {
      // If we have a authToken lets validate and confirm by hitting the customer
      // endpoint.
      HttpGet getRequest = new HttpGet(getApiUrl(""));
      updateHeaders(getRequest);
      try {
        response = client.execute(getRequest);
      } catch (IOException e) {
        e.printStackTrace();
      }

      if (response.getStatusLine().getStatusCode() == 403) {
        // Delete the authToken, since it is invalid, they need to re-authenticate
        authToken = null;
        customerUUID = null;
        invalidToken = true;
      }
    }

    // If the token we have is invalid, lets login and fetch the new token.
    if (invalidToken) {
      ObjectMapper mapper = new ObjectMapper();
      try {
        ObjectNode authParams = mapper.createObjectNode();
        authParams.put("email", this.adminConfig.user);
        authParams.put("password", this.adminConfig.password);
        StringEntity entity = new StringEntity(authParams.toString());
        HttpPost authRequest = new HttpPost(getApiUrl("login"));
        updateHeaders(authRequest);
        authRequest.setEntity(entity);
        response = client.execute(authRequest);
        if (response.getStatusLine().getStatusCode() == 200) {
          String body = EntityUtils.toString(response.getEntity());
          JsonNode authResponse = mapper.readTree(body);
          authToken = authResponse.get("authToken").asText();
          customerUUID = authResponse.get("customerUUID").asText();
        } else {
          throw new YugaByteServiceException("Unable to authenticate to YugaByte Admin Console");
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  public List<String> getReleases() {
    JsonNode response = doGet("releases");
    ObjectMapper mapper = new ObjectMapper();
    List<String> releases = mapper.convertValue(response, List.class);
    if (releases == null) {
      throw new YugaByteServiceException("Unable to fetch YugaByte release metadata");
    }
    Collections.sort(releases, Collections.reverseOrder());
    return releases;
  }

  public JsonNode getProviders() {
    return doGet("providers");
  }

  public JsonNode getRegions(UUID providerUUID) {
    String regionsEndpoint = String.format("providers/%s/regions", providerUUID);
    return doGet(regionsEndpoint);
  }

  public JsonNode getAccessKeys(UUID providerUUID) {
    String accessKeysEndpoint = String.format("providers/%s/access_keys", providerUUID);
    return doGet(accessKeysEndpoint);
  }

  public JsonNode configureUniverse(JsonNode params) {
    return doPost("universe_configure", params);
  }

  public JsonNode createUniverse(JsonNode params) {
    return doPost("universes", params);
  }

  public JsonNode getUniverse(String universeUUID) {
    return doGet(String.format("universes/%s", universeUUID));
  }

  public JsonNode deleteUniverse(String universeUUID) {
    return doDelete(String.format("universes/%s", universeUUID));
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
        url = String.format("universes/%s/yqlservers", universeUUID);
        break;
      case YEDIS:
        url = String.format("universes/%s/redisservers",  universeUUID);
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
