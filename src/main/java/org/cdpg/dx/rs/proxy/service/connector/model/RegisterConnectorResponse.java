package org.cdpg.dx.rs.proxy.service.connector.model;

import io.vertx.core.json.JsonObject;

public class RegisterConnectorResponse {
  private String userName;
  private String apiKey;
  private String connectorName;

  public RegisterConnectorResponse() {}

  public RegisterConnectorResponse(String userName, String apiKey, String connectorName) {
    this.userName = userName;
    this.apiKey = apiKey;
    this.connectorName = connectorName;
  }

  public String getUserName() {
    return userName;
  }

  public void setUserName(String userName) {
    this.userName = userName;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public String getConnectorName() {
    return connectorName;
  }

  public void setConnectorName(String connectorName) {
    this.connectorName = connectorName;
  }

  public JsonObject toJson() {
    return new JsonObject()
        .put("userName", this.userName)
        .put("apiKey", this.apiKey)
        .put("connectorName", this.connectorName);
  }
}
