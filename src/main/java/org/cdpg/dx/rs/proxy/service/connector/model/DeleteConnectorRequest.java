package org.cdpg.dx.rs.proxy.service.connector.model;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.databroker.util.Vhosts;

public class DeleteConnectorRequest {
  private String userId;
  private String connectorId;
  private Vhosts vhostType;

  // Default Constructor
  public DeleteConnectorRequest() {}

  // Constructor with fields
  public DeleteConnectorRequest(String userId, String connectorId, Vhosts vhostType) {
    this.userId = userId;
    this.connectorId = connectorId;
    this.vhostType = vhostType;
  }

  // Convert from JsonObject
  public static DeleteConnectorRequest fromJson(JsonObject json) {
    return new DeleteConnectorRequest(
        json.getString("userId"),
        json.getString("connectorId"),
        Vhosts.valueOf(json.getString("vhostType", "IUDX_INTERNAL")) // Default to IUDX_INTERNAL
        );
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getConnectorId() {
    return connectorId;
  }

  public void setConnectorId(String connectorId) {
    this.connectorId = connectorId;
  }

  public Vhosts getVhostType() {
    return vhostType;
  }

  public void setVhostType(Vhosts vhostType) {
    this.vhostType = vhostType;
  }

  public JsonObject toJson() {
    return new JsonObject()
        .put("userId", this.userId)
        .put("connectorId", this.connectorId)
        .put("vhostType", this.vhostType.name());
  }
}
