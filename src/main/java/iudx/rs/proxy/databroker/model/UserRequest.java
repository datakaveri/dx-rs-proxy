package iudx.rs.proxy.databroker.model;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;
import iudx.rs.proxy.databroker.util.Vhosts;

@DataObject
public class UserRequest {
  private String userId;
  private String password;
  private Vhosts vhostType; // Added field for Vhosts

  // Default Constructor
  public UserRequest() {}

  // Constructor with JsonObject (Manual Conversion)
  public UserRequest(JsonObject json) {
    this.userId = json.getString("userId");
    this.password = json.getString("password");

    // Convert string to Vhosts enum (assuming Vhosts is an enum)
    String vhostTypeStr = json.getString("vhostType");
    if (vhostTypeStr != null) {
      this.vhostType = Vhosts.valueOf(vhostTypeStr); // Convert string to Vhosts enum
    }
  }

  // Convert Object to JsonObject (Manual Conversion)
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    if (userId != null) json.put("userId", userId);
    if (password != null) json.put("password", password);
    if (vhostType != null)
      json.put("vhostType", vhostType.name()); // Convert enum to string for JSON
    return json;
  }

  // Getters and Setters
  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public Vhosts getVhostType() {
    return vhostType;
  }

  public void setVhostType(Vhosts vhostType) {
    this.vhostType = vhostType;
  }
}
