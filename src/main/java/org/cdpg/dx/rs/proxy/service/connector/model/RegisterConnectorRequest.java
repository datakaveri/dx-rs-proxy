package org.cdpg.dx.rs.proxy.service.connector.model;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.databroker.util.Vhosts;

public class RegisterConnectorRequest {
  private String userId;
  private String resourceId;
  private Vhosts vhostType;

  public RegisterConnectorRequest(String userId, String resourceId, Vhosts vhostType) {
    this.userId = userId;
    this.resourceId = resourceId;
    this.vhostType = vhostType;
  }

  public RegisterConnectorRequest() {}

  // Constructor to initialize from JsonObject
  public RegisterConnectorRequest(JsonObject json) {
    this.userId = json.getString("userId");
    this.resourceId = json.getString("resourceId");
    this.vhostType = Vhosts.valueOf(json.getString("vhostType"));
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getResourceId() {
    return resourceId;
  }

  public void setResourceId(String resourceId) {
    this.resourceId = resourceId;
  }

  public Vhosts getVhostType() {
    return vhostType;
  }

  public void setVhostType(Vhosts vhostType) {
    this.vhostType = vhostType;
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    json.put("userId", this.userId);
    json.put("resourceId", this.resourceId);
    json.put("vhostType", this.vhostType.name());
    return json;
  }
}
