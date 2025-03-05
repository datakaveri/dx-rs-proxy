package iudx.rs.proxy.databroker.model;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

@DataObject
public class RabbitMQPermission {
  private String user;
  private String vhost;
  private String configure;
  private String write;
  private String read;

  // Default Constructor
  public RabbitMQPermission() {}

  // Constructor to initialize from JsonObject
  public RabbitMQPermission(JsonObject json) {
    this.user = json.getString("user");
    this.vhost = json.getString("vhost");
    this.configure = json.getString("configure");
    this.write = json.getString("write");
    this.read = json.getString("read");
  }

  // Parameterized Constructor
  public RabbitMQPermission(
      String user, String vhost, String configure, String write, String read) {
    this.user = user;
    this.vhost = vhost;
    this.configure = configure;
    this.write = write;
    this.read = read;
  }

  // Getters and Setters
  public String getUser() {
    return user;
  }

  public void setUser(String user) {
    this.user = user;
  }

  public String getVhost() {
    return vhost;
  }

  public void setVhost(String vhost) {
    this.vhost = vhost;
  }

  public String getConfigure() {
    return configure;
  }

  public void setConfigure(String configure) {
    this.configure = configure;
  }

  public String getWrite() {
    return write;
  }

  public void setWrite(String write) {
    this.write = write;
  }

  public String getRead() {
    return read;
  }

  public void setRead(String read) {
    this.read = read;
  }

  // Convert to JsonObject
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    json.put("user", this.user);
    json.put("vhost", this.vhost);
    json.put("configure", this.configure);
    json.put("write", this.write);
    json.put("read", this.read);
    return json;
  }

  // toString Method
  @Override
  public String toString() {
    return "RabbitMQPermission{"
        + "user='"
        + user
        + '\''
        + ", vhost='"
        + vhost
        + '\''
        + ", configure='"
        + configure
        + '\''
        + ", write='"
        + write
        + '\''
        + ", read='"
        + read
        + '\''
        + '}';
  }
}
