package iudx.rs.proxy.databroker.model;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class UserResponse {
  private String userId;
  private String apiKey;
  private String vhost;

  // Default Constructor
  public UserResponse() {}

  // Constructor with JsonObject (Manual Conversion)
  public UserResponse(JsonObject json) {
    this.userId = json.getString("userId");
    this.apiKey = json.getString("apiKey");
    this.vhost = json.getString("vhost");
  }

  // Convert Object to JsonObject (Manual Conversion)
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    if (userId != null) json.put("userId", userId);
    if (apiKey != null) json.put("apiKey", apiKey);
    if (vhost != null) json.put("detail", vhost);
    return json;
  }

  // Getters and Setters
  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public String getVhostl() {
    return vhost;
  }

  public void setVhostl(String detail) {
    this.vhost = detail;
  }
}
