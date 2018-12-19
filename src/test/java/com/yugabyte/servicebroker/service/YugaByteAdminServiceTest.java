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
import com.yugabyte.servicebroker.YugaByteServiceTestConfig;
import com.yugabyte.servicebroker.exception.YugaByteServiceException;
import com.yugabyte.servicebroker.model.ServiceInstance;
import com.yugabyte.servicebroker.repository.ServiceInstanceRepository;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.servicebroker.model.instance.CreateServiceInstanceRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = YugaByteServiceTestConfig.class)
public class YugaByteAdminServiceTest  {
  @Autowired
  private YugaByteAdminService adminService;

  @Autowired
  ServiceInstanceRepository instanceRepository;

  @Autowired
  private RestTemplate restTemplate;

  private MockRestServiceServer mockServer;

  @Before
  public void setUp() {
    mockServer = MockRestServiceServer.createServer(restTemplate);
  }

  @After
  public void tearDown() {
    adminService.resetAuthToken();
  }

  private ObjectMapper mapper = new ObjectMapper();

  private void expectLoginRequest() {
    mockServer.expect(ExpectedCount.once(),
        requestTo("http://localhost:9000/api/login"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withStatus(HttpStatus.OK)
            .contentType(MediaType.APPLICATION_JSON)
            .body("{\"authToken\": \"someToken\", \"customerUUID\": \"someUUID\"}"));

  }

  private void expectEndpointRequest(HttpMethod method, String endpoint,
                                     HttpStatus status, String response) {
    mockServer.expect(ExpectedCount.once(),
        requestTo("http://localhost:9000/api/customers/someUUID" + endpoint))
        .andExpect(method(method))
        .andRespond(withStatus(status)
          .contentType(MediaType.APPLICATION_JSON)
          .body(response)
        );
  }

  private void expectEndpointRequestWithBody(HttpMethod method, String endpoint, String bodyJson,
                                             HttpStatus status, String response) {
    mockServer.expect(ExpectedCount.once(),
        requestTo("http://localhost:9000/api/customers/someUUID" + endpoint))
        .andExpect(method(method))
        .andExpect(content().string(bodyJson))
        .andRespond(withStatus(status)
            .contentType(MediaType.APPLICATION_JSON)
            .body(response)
        );
  }

  @Test
  public void testGetReleases() {
    expectLoginRequest();
    expectEndpointRequest(HttpMethod.GET, "/releases", HttpStatus.OK, "[\"1\", \"2\"]");
    List<String> releases = adminService.getReleases();
    assertEquals(2, releases.size());
    assertEquals(Arrays.asList("2", "1"), releases);
    mockServer.verify();
  }

  @Test
  public void testGetReleasesUnAuthenticated() {
    try {
      expectLoginRequest();
      adminService.getReleases();
    } catch (AssertionError ae) {
      assertTrue(ae.getMessage()
          .contains("1 request(s) executed:\nPOST http://localhost:9000/api/login")
      );
    }
  }

  @Test
  public void testGetReleasesWithAuthTokenPresent() {
    expectLoginRequest();
    expectEndpointRequest(HttpMethod.GET, "/releases", HttpStatus.OK, "[\"1\", \"2\"]");
    List<String> releases = adminService.getReleases();
    assertEquals(2, releases.size());
    assertEquals(Arrays.asList("2", "1"), releases);
    mockServer.verify();
    mockServer.reset();
    expectEndpointRequest(HttpMethod.GET, "", HttpStatus.OK, "{\"foo\": \"bar\"}");
    expectEndpointRequest(HttpMethod.GET, "/releases", HttpStatus.OK, "[\"1\", \"2\", \"3\"]");
    releases = adminService.getReleases();
    mockServer.verify();
    assertEquals(3, releases.size());
    assertEquals(Arrays.asList("3", "2", "1"), releases);
    mockServer.verify();

  }

  @Test
  public void testGetReleasesFailure() {
    expectLoginRequest();
    expectEndpointRequest(HttpMethod.GET, "/releases", HttpStatus.OK, "");
    try {
      adminService.getReleases();
    } catch (YugaByteServiceException ye) {
      assertTrue(ye.getMessage().contains("Unable to fetch YugaByte release metadata"));
    }
  }


  @Test
  public void testGetProviders() {
    expectLoginRequest();
    expectEndpointRequest(HttpMethod.GET, "/providers", HttpStatus.OK,
        "[{\"code\": \"aws\"}, {\"code\": \"gcp\"}]");
    JsonNode providers = adminService.getProviders();
    Iterator<JsonNode> it = providers.iterator();
    while (it.hasNext()) {
      JsonNode provider = it.next();
      assertNotNull(provider.get("code").asText());
    }
    assertEquals(2, providers.size());
  }

  @Test
  public void testGetProvidersUnAuthenticated() {
    try {
      expectLoginRequest();
      adminService.getProviders();
    } catch (AssertionError ae) {
      assertTrue(ae.getMessage()
          .contains("1 request(s) executed:\nPOST http://localhost:9000/api/login")
      );
    }
  }

  @Test
  public void testGetRegions() {
    expectLoginRequest();
    UUID providerUUID = UUID.randomUUID();
    expectEndpointRequest(HttpMethod.GET, "/providers/" + providerUUID + "/regions", HttpStatus.OK, "[{\"code\": \"region-1\"}, {\"code\": \"region-2\"}]");
    JsonNode regions = adminService.getRegions(providerUUID);
    Iterator<JsonNode> it = regions.iterator();
    while (it.hasNext()) {
      JsonNode region = it.next();
      assertNotNull(region.get("code").asText());
    }
    assertEquals(2, regions.size());
  }

  @Test
  public void testGetRegionsFailure() {
    try {
      expectLoginRequest();
      UUID providerUUID = UUID.randomUUID();
      adminService.getRegions(providerUUID);
    } catch (AssertionError ae) {
      assertTrue(ae.getMessage()
          .contains("1 request(s) executed:\nPOST http://localhost:9000/api/login")
      );
    }
  }

  @Test
  public void testGetAccessKeys() {
    expectLoginRequest();
    UUID providerUUID = UUID.randomUUID();
    expectEndpointRequest(HttpMethod.GET, "/providers/" + providerUUID + "/access_keys", HttpStatus.OK,
        "[{\"code\": \"key-1\"}, {\"code\": \"key-2\"}]");
    JsonNode accessKeys = adminService.getAccessKeys(providerUUID);
    Iterator<JsonNode> it = accessKeys.iterator();
    while (it.hasNext()) {
      JsonNode accessKey = it.next();
      assertNotNull(accessKey.get("code").asText());
    }
    assertEquals(2, accessKeys.size());
  }

  @Test
  public void testGetAccessKeysUnAuthenticated() {
    try {
      expectLoginRequest();
      UUID providerUUID = UUID.randomUUID();
      adminService.getAccessKeys(providerUUID);
      mockServer.verify();
    } catch (AssertionError ae) {
      assertTrue(ae.getMessage()
          .contains("1 request(s) executed:\nPOST http://localhost:9000/api/login")
      );
    }
  }

  @Test
  public void testGetAccessKeysFailure() {
    expectLoginRequest();
    UUID providerUUID = UUID.randomUUID();
    expectEndpointRequest(HttpMethod.GET, "/providers/" + providerUUID + "/access_keys", HttpStatus.INTERNAL_SERVER_ERROR,
        "[{\"error\" : \"something happened\"}]");
    try {
      adminService.getAccessKeys(providerUUID);
    } catch (HttpServerErrorException he) {
      assertEquals("500 Internal Server Error", he.getLocalizedMessage());
    }
  }

  @Test
  public void testCreateUniverseSuccess() {
    expectLoginRequest();
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode params = mapper.createObjectNode();
    params.put("foo", "bar");
    ObjectNode responseJson = mapper.createObjectNode();
    responseJson.put("success", true);
    expectEndpointRequestWithBody(HttpMethod.POST,"/universes", params.toString(),
        HttpStatus.OK, responseJson.toString());
    JsonNode actualResponse = adminService.createUniverse(params);
    assertEquals(responseJson, actualResponse);
  }

  @Test
  public void testCreateUniverseFailure() {
    expectLoginRequest();
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode params = mapper.createObjectNode();
    params.put("foo", "bar");
    expectEndpointRequestWithBody(HttpMethod.POST,"/universes", params.toString(),
        HttpStatus.INTERNAL_SERVER_ERROR, "Invalid JSON");
    try {
      adminService.createUniverse(params);
    } catch (HttpServerErrorException he) {
      assertEquals("500 Internal Server Error", he.getLocalizedMessage());
    }
  }

  @Ignore
  // TODO: ADD TESTS
  public void testGetEndpointForServiceType() {
    UUID universeUUID = UUID.randomUUID();
    CreateServiceInstanceRequest request = CreateServiceInstanceRequest.builder()
        .serviceDefinitionId("sd-1")
        .planId("plan-1")
        .serviceInstanceId("instance-1").build();
    ServiceInstance serviceInstance = new ServiceInstance(request, universeUUID.toString());
    instanceRepository.save(serviceInstance);
    mockServer.expect(ExpectedCount.once(),
        requestTo("http://localhost:9000/api/universes/" + universeUUID + "/yqlservers"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(withStatus(HttpStatus.OK)
            .contentType(MediaType.APPLICATION_JSON)
            .body("10.0.0.1:9042,10.0.0.2:9042,10.0.0.3:9042")
        );
    adminService.getUniverseServiceEndpoints("instance-1");
  }
}