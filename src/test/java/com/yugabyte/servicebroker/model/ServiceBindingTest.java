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
package com.yugabyte.servicebroker.model;

import com.yugabyte.servicebroker.repository.ServiceBindingRepository;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

@RunWith(SpringRunner.class)
@DataJpaTest
public class ServiceBindingTest {


  @Autowired
  private TestEntityManager entityManager;

  @Autowired
  private ServiceBindingRepository serviceBindingRepository;

  private ServiceBinding defaultServiceBinding;


  @Before
  public void setUp() {
    HashMap<String, Object> credentials = new HashMap<>();
    credentials.put("foo", "bar");
    defaultServiceBinding = new ServiceBinding("binding-1",
        "instance-id",
        credentials
    );
    entityManager.persistAndFlush(defaultServiceBinding);
  }

  @Test
  public void testGetCredentials() {
    ServiceBinding foundSB = serviceBindingRepository
        .findById(defaultServiceBinding.getBindingId()).orElse(null);
    assertNotNull(foundSB);
    assertFalse(foundSB.getCredentials().isEmpty());
    assertEquals("bar", foundSB.getCredentials().get("foo"));
  }
}