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

import org.springframework.cloud.servicebroker.model.binding.CreateServiceInstanceAppBindingResponse;
import org.springframework.cloud.servicebroker.model.binding.CreateServiceInstanceBindingRequest;
import org.springframework.cloud.servicebroker.model.binding.CreateServiceInstanceBindingResponse;
import org.springframework.cloud.servicebroker.model.binding.DeleteServiceInstanceBindingRequest;
import org.springframework.cloud.servicebroker.model.binding.GetServiceInstanceAppBindingResponse;
import org.springframework.cloud.servicebroker.model.binding.GetServiceInstanceBindingRequest;
import org.springframework.cloud.servicebroker.model.binding.GetServiceInstanceBindingResponse;
import org.springframework.cloud.servicebroker.service.ServiceInstanceBindingService;
import org.springframework.stereotype.Service;

@Service
public class YugaByteBindingService implements ServiceInstanceBindingService {

  @Override
  public CreateServiceInstanceBindingResponse createServiceInstanceBinding(CreateServiceInstanceBindingRequest request) {
    String serviceInstanceId = request.getServiceInstanceId();
    String bindingId = request.getBindingId();

    //
    // create credentials and store for later retrieval
    //

    String url = new String(/* build a URL to access the service instance */);
    String bindingUsername = new String(/* create a user */);
    String bindingPassword = new String(/* create a password */);

    return CreateServiceInstanceAppBindingResponse.builder()
        .credentials("url", url)
        .credentials("username", bindingUsername)
        .credentials("password", bindingPassword)
        .bindingExisted(false)
        .build();
  }

  @Override
  public void deleteServiceInstanceBinding(DeleteServiceInstanceBindingRequest request) {
    String serviceInstanceId = request.getServiceInstanceId();
    String bindingId = request.getBindingId();

    //
    // delete any binding-specific credentials
    //
  }

  @Override
  public GetServiceInstanceBindingResponse getServiceInstanceBinding(GetServiceInstanceBindingRequest request) {
    String serviceInstanceId = request.getServiceInstanceId();
    String bindingId = request.getBindingId();

    //
    // retrieve the details of the specified service binding
    //

    String url = new String(/* retrieved URL */);
    String bindingUsername = new String(/* retrieved user */);
    String bindingPassword = new String(/* retrieved password */);

    return GetServiceInstanceAppBindingResponse.builder()
        .credentials("username", bindingUsername)
        .credentials("password", bindingPassword)
        .credentials("url", url)
        .build();
  }
}