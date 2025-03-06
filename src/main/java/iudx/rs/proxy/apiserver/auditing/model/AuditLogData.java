package iudx.rs.proxy.apiserver.auditing.model;

import io.vertx.core.json.JsonObject;

public class AuditLogData {
  private String primaryKey;
  private String userid;
  private String id;
  private String resourceGroup;
  private String providerId;
  private String api;
  private long responseSize;
  private long epochTime;
  private String isoTime;
  private String delegatorId;
  private String type;
  private String event;
  private String origin;

  public String getApi() {
    return api;
  }

  public void setApi(String api) {
    this.api = api;
  }

  // Getters and Setters
  public void setEpochTime(long epochTime) {
    this.epochTime = epochTime;
  }

  public void setIsoTime(String isoTime) {
    this.isoTime = isoTime;
  }

  public void setId(String id) {
    this.id = id;
  }

  public void setResponseSize(long responseSize) {
    this.responseSize = responseSize;
  }

  public void setProviderId(String providerId) {
    this.providerId = providerId;
  }

  public void setPrimaryKey(String primaryKey) {
    this.primaryKey = primaryKey;
  }

  public void setOrigin(String origin) {
    this.origin = origin;
  }

  public void setResourceGroup(String resourceGroup) {
    this.resourceGroup = resourceGroup;
  }

  public void setDelegatorId(String delegatorId) {
    this.delegatorId = delegatorId;
  }

  public void setType(String type) {
    this.type = type;
  }

  public void setEvent(String event) {
    this.event = event;
  }

  public String getUserid() {
    return userid;
  }

  public void setUserid(String userid) {
    this.userid = userid;
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();

    // Insert fields in the required order
    json.put("primaryKey", primaryKey);
    json.put("userid", userid);
    json.put("id", id);
    json.put("resourceGroup", resourceGroup);
    json.put("providerId", providerId);
    json.put("api", api);
    json.put("responseSize", responseSize);
    json.put("epochTime", epochTime);
    json.put("isoTime", isoTime);
    json.put("delegatorId", delegatorId);
    json.put("type", type);
    json.put("event", event);
    json.put("origin", origin);

    return json;
  }
}
