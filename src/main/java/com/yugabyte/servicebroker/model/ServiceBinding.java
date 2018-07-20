package com.yugabyte.servicebroker.model;


import javax.persistence.Column;
import javax.persistence.Convert;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Map;

@Entity
@Table(name = "service_bindings")
public class ServiceBinding {
  @Id
  @Column(length = 50)
  private final String bindingId;


  @Column()
  @Convert(converter = ConverterMapToJson.class)
  private final Map<String, Object> credentials;

  @SuppressWarnings("unused")
  private ServiceBinding() {
    this.bindingId = null;
    this.credentials = null;
  }

  public ServiceBinding(String bindingId, Map<String, Object> credentials) {
    this.bindingId = bindingId;
    this.credentials = credentials;
  }

  public Map<String, Object> getCredentials() {
    return credentials;
  }

  public String getBindingId() {
    return bindingId;
  }
}
