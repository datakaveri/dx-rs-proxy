package iudx.rs.proxy.databroker.model;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class QueueResponse {
  private String queueName;
  private String url;
  private int port;
  private String vHost;

  // Default Constructor
  public QueueResponse() {}

  // Constructor with JsonObject (Manual Conversion)
  public QueueResponse(JsonObject json) {
    this.queueName = json.getString("queueName");
    this.url = json.getString("url");
    this.port = json.getInteger("port");
    this.vHost = json.getString("vHost");
  }

  // Convert Object to JsonObject (Manual Conversion)
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    if (queueName != null) json.put("queueName", queueName);
    if (url != null) json.put("url", url);
    json.put("port", port); // Integer defaults to 0 if not set
    if (vHost != null) json.put("vHost", vHost);
    return json;
  }

  // Getters and Setters
  public String getQueueName() {
    return queueName;
  }

  public void setQueueName(String queueName) {
    this.queueName = queueName;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public int getPort() {
    return port;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public String getvHost() {
    return vHost;
  }

  public void setvHost(String vHost) {
    this.vHost = vHost;
  }
}
