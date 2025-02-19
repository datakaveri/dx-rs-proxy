package iudx.rs.proxy.apiserver.connector.model;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class DeleteConnectorResponse {
  private String type;
  private String title;
  private String userName;
  private String connectorName;

  // Default Constructor
  public DeleteConnectorResponse() {}

  // Constructor with all fields
  public DeleteConnectorResponse(String type, String title, String userName, String connectorName) {
    this.type = type;
    this.title = title;
    this.userName = userName;
    this.connectorName = connectorName;
  }

  // Convert to JsonObject
  public JsonObject toJson() {
    JsonObject result =
        new JsonObject().put("userName", this.userName).put("connectorName", this.connectorName);

    return new JsonObject()
        .put("type", this.type)
        .put("title", this.title)
        .put("results", new JsonArray().add(result));
  }

  // Getters and Setters
  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getUserName() {
    return userName;
  }

  public void setUserName(String userName) {
    this.userName = userName;
  }

  public String getConnectorName() {
    return connectorName;
  }

  public void setConnectorName(String connectorName) {
    this.connectorName = connectorName;
  }
}
