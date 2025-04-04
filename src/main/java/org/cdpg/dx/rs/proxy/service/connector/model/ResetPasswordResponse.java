package org.cdpg.dx.rs.proxy.service.connector.model;

import io.vertx.core.json.JsonObject;

public class ResetPasswordResponse {
  private final String userId;
  private final String password;

  public ResetPasswordResponse(String userId, String password) {
    this.userId = userId;
    this.password = password;
  }

  public String getUserId() {
    return userId;
  }

  public String getPassword() {
    return password;
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    json.put("userId", userId);
    json.put("password", password);
    return json;
  }
}
