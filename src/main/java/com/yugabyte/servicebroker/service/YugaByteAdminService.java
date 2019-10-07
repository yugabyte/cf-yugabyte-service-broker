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
import com.yugabyte.servicebroker.exception.YugaByteServiceResponseHandler;
import com.yugabyte.servicebroker.model.ServiceBinding;
import com.yugabyte.servicebroker.model.ServiceInstance;
import com.yugabyte.servicebroker.repository.ServiceInstanceRepository;
import com.yugabyte.servicebroker.repository.YugaByteConfigRepository;
import com.yugabyte.servicebroker.utils.CommonUtils;
import com.yugabyte.servicebroker.utils.YBClient;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.servicebroker.exception.ServiceInstanceDoesNotExistException;
import org.springframework.cloud.servicebroker.model.binding.CreateServiceInstanceBindingRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

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
  private RestTemplate restTemplate;

  private String authToken = null;
  private String customerUUID = null;
  private static final Log logger = LogFactory.getLog(YugaByteAdminService.class);

  @Autowired
  public YugaByteAdminService(YugaByteServiceConfig adminConfig,
                              ServiceInstanceRepository instanceRepository,
                              YugaByteConfigRepository yugaByteConfigRepository,
                              RestTemplate restTemplate) {
    this.adminConfig = adminConfig;
    this.instanceRepository = instanceRepository;
    this.yugaByteConfigRepository = yugaByteConfigRepository;
    this.restTemplate = restTemplate;
    this.restTemplate.setErrorHandler(new YugaByteServiceResponseHandler());
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

  private HttpEntity getEntity(JsonNode bodyJson) {
    HttpHeaders headers = new HttpHeaders();
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (authToken != null && !authToken.isEmpty()) {
      headers.set("X-AUTH-TOKEN", authToken);
    }

    if (bodyJson == null) {
      return new HttpEntity<>(headers);
    } else {
      return new HttpEntity<>(bodyJson, headers);
    }
  }

  private ResponseEntity<JsonNode> doGet(String endpoint)  {
    validateAndRefreshToken();
    return makeRequest(endpoint, HttpMethod.GET, null);
  }

  private String doGetRaw(String endpoint) {
    validateAndRefreshToken();
    HttpEntity<JsonNode> entity = getEntity(null);
    ResponseEntity<String> response = restTemplate.exchange(getApiUrl(endpoint), HttpMethod.GET, entity, String.class);
    if (response.getStatusCode() == HttpStatus.OK) {
      return response.getBody();
    } else {
      throw new YugaByteServiceException("GET request failed with status: " +
          response.getStatusCode() + "body: " + response.getBody());
    }
  }

  private ResponseEntity<JsonNode> doPost(String endpoint, JsonNode params) {
    validateAndRefreshToken();
    return makeRequest(endpoint, HttpMethod.POST, params);
  }

  private ResponseEntity<JsonNode> doDelete(String endpoint) {
    validateAndRefreshToken();
    return makeRequest(endpoint, HttpMethod.DELETE, null);
  }

  private ResponseEntity<JsonNode> makeRequest(String endpoint, HttpMethod method, JsonNode bodyJson) {
    return restTemplate.exchange(getApiUrl(endpoint), method,
        getEntity(bodyJson), JsonNode.class);
  }

  private JsonNode getResponseOrThrow(ResponseEntity<JsonNode> responseEntity,
                                      String exceptionMessage) {
    if (responseEntity.getStatusCode() == HttpStatus.OK) {
      return responseEntity.getBody();
    } else {
      logger.warn("YugaWare API returned status: " + responseEntity.getStatusCode().value() +
                  ", body: " + responseEntity.getBody());
      throw new YugaByteServiceException(exceptionMessage);
    }
  }

  private void validateAndRefreshToken() {
    boolean invalidToken = (authToken == null);
    if (!invalidToken) {
      // If we have a authToken lets validate and confirm by hitting the customer
      // endpoint.
      try {
        makeRequest("", HttpMethod.GET, null);
      } catch (YugaByteServiceException ye) {
          resetAuthToken();
          invalidToken = true;
      }
    }

    // If the token we have is invalid, lets login and fetch the new token.
    if (invalidToken) {
      ObjectMapper mapper = new ObjectMapper();
      ObjectNode authParams = mapper.createObjectNode();
      authParams.put("email", this.adminConfig.user);
      authParams.put("password", this.adminConfig.password);
      ResponseEntity<JsonNode> authResponse = makeRequest("login", HttpMethod.POST, authParams);
      JsonNode responseBody = getResponseOrThrow(
          authResponse,
          "Unable to authenticate with YugaWare");
      if (!responseBody.has("authToken") ||
          !responseBody.has("customerUUID")) {
        throw new YugaByteServiceException("Unable to authenticate with YugaWare");
      }
      authToken = responseBody.get("authToken").asText();
      customerUUID = responseBody.get("customerUUID").asText();
    }
  }

  public List<String> getReleases() {
    ResponseEntity<JsonNode> responseEntity = doGet("releases");
    JsonNode response = getResponseOrThrow(responseEntity,
        "Unable to fetch releases metadata");
    ObjectMapper mapper = new ObjectMapper();
    List<String> releases = mapper.convertValue(response, List.class);
    if (releases == null) {
      throw new YugaByteServiceException("Unable to fetch YugaByte release metadata");
    }
    Collections.sort(releases, Collections.reverseOrder());
    return releases;
  }

  public void resetAuthToken() {
    authToken = null;
    customerUUID = null;
  }

  public JsonNode getProviders() {
    return getResponseOrThrow(doGet("providers"), "Unable to fetch providers");
  }

  public JsonNode getRegions(UUID providerUUID) {
    return getResponseOrThrow(doGet(String.format("providers/%s/regions", providerUUID)),
        "Unable to fetch regions");
  }

  public JsonNode getAccessKeys(UUID providerUUID) {
    return getResponseOrThrow(doGet(String.format("providers/%s/access_keys", providerUUID)),
        "Unable to fetch access keys");
  }

  public JsonNode configureUniverse(JsonNode params) {
    return getResponseOrThrow(doPost("universe_configure", params),
        "Unable to configure universe");
  }

  public JsonNode createUniverse(JsonNode params) {
    return getResponseOrThrow(doPost("universes", params),
        "Unable to create universe");
  }

  public JsonNode getUniverse(String universeUUID) {
    return getResponseOrThrow(doGet(String.format("universes/%s", universeUUID)),
        "Unable to fetch universe " + universeUUID);
  }

  public JsonNode deleteUniverse(String universeUUID) {
    return getResponseOrThrow(doDelete(String.format("universes/%s", universeUUID)),
        "Unable to delete universe " + universeUUID);
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
      case YSQL:
        url = String.format("universes/%s/ysqlservers", universeUUID);
        break;
    }
    String serverEndpointString =  doGetRaw(url);
    return CommonUtils.convertToHostPorts(
        serverEndpointString.replaceAll("^\"|\"$", "")
    );
  }

  public Map<String, Object> getUniverseServiceEndpoints(CreateServiceInstanceBindingRequest request) {
    String universeUUID = getUniverseUUIDFromServiceInstance(request.getServiceInstanceId());
    Map<String, Object> endpoints = new HashMap<>();
    for (YBClient.ClientType clientType : YBClient.ClientType.values()) {
      List<HostAndPort> hostAndPorts = getEndpointForServiceType(clientType, universeUUID);
      YBClient ybClient = clientType.getInstance(hostAndPorts, yugaByteConfigRepository);
      try {
        endpoints.put(clientType.name().toLowerCase(), ybClient.getCredentials(request.getParameters()));
      } catch (Exception e) {
        logger.warn("Unable to add credentials for " + clientType.name().toLowerCase(), e);
      }
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
      YBClient ybClient = YBClient.ClientType.valueOf(endpoint.toUpperCase()).getInstance(
          hostAndPorts,
          yugaByteConfigRepository
      );
      ybClient.deleteAuth(credentialMap);
    });
  }
}
