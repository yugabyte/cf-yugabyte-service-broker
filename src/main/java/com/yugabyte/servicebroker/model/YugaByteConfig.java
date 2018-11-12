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

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;
import java.util.Map;

/**
 * YugaByteConfig model is used to store, internal configurations that YugaByte would
 * use such as admin credentials for universes across different client types (YCQL, YEDIS etc).
 * And in future any additional configuration/overrides specific to YugaByte that needs to be
 * persisted would be stored here.
 */
@Entity
@Table(name="yugabyte_configs")
public class YugaByteConfig {
  // Config key is a unique identifier for the YugaByte config we want to be persisted
  @Id
  @Column(length = 50)
  private final String configKey;

  /*
  Config itself stores the actual configuration as a map, and we encrypt this data
  before persisting this into the DB. In order to encrypt we use the ConverterMapToHash
  */
  @Lob
  @Column()
  @Convert(converter = ConverterMapToHash.class)
  private final Map<String, String> config;

  @SuppressWarnings("unused")
  private YugaByteConfig() {
    this.configKey = null;
    this.config = null;
  }

  public YugaByteConfig(String configKey, Map<String, String> config) {
    this.configKey = configKey;
    this.config = config;
  }

  public Map<String, String> getConfig() {
    return config;
  }

  public String getConfigKey() {
    return configKey;
  }
}