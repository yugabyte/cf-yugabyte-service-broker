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
package com.yugabyte.servicebroker.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.net.HostAndPort;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class CommonUtils {
  private static int RANDOM_STRING_LENGTH = 12;
  private static String ALPHABETIC_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
  private static String NUMBERIC_CHARS = "0123456789";

  public static String generateRandomString(boolean onlyAlphabets) {
    String randomChars = onlyAlphabets ? ALPHABETIC_CHARS : ALPHABETIC_CHARS + NUMBERIC_CHARS;
    return new SecureRandom().ints(RANDOM_STRING_LENGTH, 0, randomChars.length())
        .mapToObj(idx -> "" + randomChars.charAt(idx))
        .collect(Collectors.joining());
  }

  public static List<HostAndPort> convertToHostPorts(String connectString) {
    String[] hostPortStrings = connectString.split(",");
    List<HostAndPort> hostAndPorts = new ArrayList<>();
    for (String hostPortString : hostPortStrings) {
      hostAndPorts.add(HostAndPort.fromString(hostPortString));
    }
    return hostAndPorts;
  }

  public static Optional<JsonNode> getCluster(JsonNode clusters, String clusterType) {
    return StreamSupport.stream(clusters.spliterator(), false).filter(
        (cluster) -> cluster.get("clusterType").asText().equals(clusterType)
    ).findFirst();
  }

  public static ArrayNode convertGflagMapToJson(Map<String, Object> gflagsMap) {
    ObjectMapper mapper = new ObjectMapper();
    ArrayNode gFlags = mapper.createArrayNode();
    gflagsMap.forEach((flagName, flagValue) -> {
      ObjectNode gFlag = mapper.createObjectNode();
      gFlag.put("name", flagName);
      gFlag.put("value", flagValue.toString());
      gFlags.add(gFlag);
    });
    return gFlags;
  }
}
