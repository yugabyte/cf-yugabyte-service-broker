package com.yugabyte.servicebroker.model;

import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;
import java.util.Map;

@Entity
@Table(name="yugabyte_configs")
public class YugaByteConfig {
  @Id
  @Column(length = 50)
  private final String configKey;

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



