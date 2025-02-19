package iudx.rs.proxy.databroker.model;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;
import iudx.rs.proxy.databroker.util.Vhosts;

@DataObject
public class QueueRequest {
  private String resourceId;
  private String userId;
  private Vhosts vhostType; // Added field for Vhosts

  // Default Constructor
  public QueueRequest() {}

  // Constructor with JsonObject (Manual Conversion)
  public QueueRequest(JsonObject json) {
    this.resourceId = json.getString("resourceId");
    this.userId = json.getString("userId");

    // Convert string to Vhosts enum (assuming Vhosts is an enum)
    String vhostTypeStr = json.getString("vhostType");
    if (vhostTypeStr != null) {
      this.vhostType = Vhosts.valueOf(vhostTypeStr); // Convert string to Vhosts enum
    }
  }

  // Convert Object to JsonObject (Manual Conversion)
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    if (resourceId != null) json.put("resourceId", resourceId);
    if (userId != null) json.put("userId", userId);
    if (vhostType != null)
      json.put("vhostType", vhostType.name()); // Convert enum to string for JSON
    return json;
  }

  // Getters and Setters
  public String getResourceId() {
    return resourceId;
  }

  public void setResourceId(String resourceId) {
    this.resourceId = resourceId;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public Vhosts getVhostType() {
    return vhostType;
  }

  public void setVhostType(Vhosts vhostType) {
    this.vhostType = vhostType;
  }
}
