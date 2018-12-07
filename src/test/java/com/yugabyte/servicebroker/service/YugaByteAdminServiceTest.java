package com.yugabyte.servicebroker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.yugabyte.servicebroker.exception.YugaByteServiceException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(MockitoJUnitRunner.class)
public class YugaByteAdminServiceTest extends ServiceTestBase {
  private YugaByteAdminService adminService;

  @Before
  public void setUp() {
    super.setUp();
    adminService = new YugaByteAdminService(mockAdminConfig,
        mockInstanceRepository, mockYugaByteConfigRepository);
  }

  @Test
  public void testGetReleases() {
    setAuthToken();
    mockEndpoint("GET", "releases", "[\"1\", \"2\"]");
    List<String> releases = adminService.getReleases();
    assertEquals(2, releases.size());
    assertEquals(Arrays.asList("2", "1"), releases);
  }

  @Test
  public void testGetReleasesUnAuthenticated() {
    mockEndpoint("GET", "releases", "[\"1\", \"2\"]");
    try {
      adminService.getReleases();
    } catch (YugaByteServiceException ye) {
      assertEquals("Unable to authenticate to YugaByte Admin Console", ye.getMessage());
    }
  }

  @Test
  public void testGetReleasesFailure() {
    setAuthToken();
    try {
      adminService.getReleases();
    } catch (YugaByteServiceException ye) {
      assertEquals("Unable to fetch YugaByte release metadata", ye.getMessage());
    }
  }

  @Test
  public void testGetProviders() {
    setAuthToken();
    mockEndpoint("GET", "providers", "[{\"code\": \"aws\"}, {\"code\": \"gcp\"}]");
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
    mockEndpoint("GET", "providers", "[{\"code\": \"aws\"}, {\"code\": \"gcp\"}]");
    try {
      adminService.getProviders();
    } catch (YugaByteServiceException ye) {
      assertEquals("Unable to authenticate to YugaByte Admin Console", ye.getMessage());
    }
  }

  @Test
  public void testGetProvidersFailure() {
    setAuthToken();
    assertNull(adminService.getProviders());
  }

  @Test
  public void testGetRegions() {
    setAuthToken();
    UUID providerUUID = UUID.randomUUID();
    mockEndpoint("GET", "providers/" + providerUUID + "/regions", "[{\"code\": \"region-1\"}, {\"code\": \"region-2\"}]");
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
    setAuthToken();
    assertNull(adminService.getRegions(UUID.randomUUID()));
  }

  @Test
  public void testGetRegionsUnAuthenticated() {
    UUID providerUUID = UUID.randomUUID();
    mockEndpoint("GET", "providers/" + providerUUID + "/regions", "[{\"code\": \"region-1\"}, {\"code\": \"region-2\"}]");
    try {
      adminService.getRegions(providerUUID);
    } catch (YugaByteServiceException ye) {
      assertEquals("Unable to authenticate to YugaByte Admin Console", ye.getMessage());
    }
  }

  @Test
  public void testGetAccessKeys() {
    setAuthToken();
    UUID providerUUID = UUID.randomUUID();
    mockEndpoint("GET", "providers/" + providerUUID + "/access_keys", "[{\"code\": \"key-1\"}, {\"code\": \"key-2\"}]");
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
    UUID providerUUID = UUID.randomUUID();
    mockEndpoint("GET", "providers/" + providerUUID + "/access_keys", "[{\"code\": \"key-1\"}, {\"code\": \"key-2\"}]");
    try {
      adminService.getAccessKeys(providerUUID);
    } catch (YugaByteServiceException ye) {
      assertEquals("Unable to authenticate to YugaByte Admin Console", ye.getMessage());
    }
  }

  @Test
  public void testGetAccessKeysFailure() {
    setAuthToken();
    assertNull(adminService.getAccessKeys(UUID.randomUUID()));
  }
}