package iudx.rs.proxy.apiserver.connector.model;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class DeleteConnectorResponse {
  private String type;
  private String title;
  private String details;

  public DeleteConnectorResponse(String type, String title, String connectorId) {
    this.type = type;
    this.title = title;
    this.details = "Connector/queue deleted Successfully [" + connectorId + "]";
  }

  public JsonObject toJson() {
    JsonObject result = new JsonObject().put("details", this.details);

    return new JsonObject()
        .put("type", this.type)
        .put("title", this.title)
        .put("results", new JsonArray().add(result));
  }

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

  public String getDetails() {
    return details;
  }
}
