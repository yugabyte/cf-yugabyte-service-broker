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
package com.yugabyte.servicebroker.config;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.servicebroker.model.catalog.Plan;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@Component
@EnableAutoConfiguration
@EnableConfigurationProperties
@ConfigurationProperties(prefix="yugabyte.catalog")
public class CatalogConfig {
  List<PlanMetadata> plans;

  public List<PlanMetadata> getPlans() {
    return plans;
  }

  public void setPlans(List<PlanMetadata> planInfoList) {
    this.plans = planInfoList;
  }

  public List<Plan> getCatalogPlans() {
    return StreamSupport.stream(plans.spliterator(), false)
        .map(planMetadata -> Plan.builder()
            .id(planMetadata.getCode())
            .name(planMetadata.getName())
            .description(planMetadata.toString())
            .metadata(planMetadata.asMap())
            .free(false)
            .build()).collect(Collectors.toList());
  }

  public PlanMetadata getPlan(String planCode) {
    Optional<PlanMetadata> requestedPlan = getPlans().stream().filter( (plan ) ->
        plan.getCode().equals(planCode)).findFirst();
    return requestedPlan.orElse(null);
  }


}
