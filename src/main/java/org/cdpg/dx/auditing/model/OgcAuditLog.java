package org.cdpg.dx.auditing.model;

import io.vertx.core.json.JsonObject;

public class OgcAuditLog implements AuditLog {
  private String primaryKey;
  private String userId;
  private String id;
  private String providerId;
  private String resourceGroup;
  private String api;
  private long epochTime;
  private String isoTime;
  private long responseSize;
  private JsonObject requestJson;
  private String delegatorId;
  private String origin;

  private OgcAuditLog(Builder builder) {
    this.primaryKey = builder.primaryKey;
    this.userId = builder.userId;
    this.id = builder.id;
    this.providerId = builder.providerId;
    this.resourceGroup = builder.resourceGroup;
    this.api = builder.api;
    this.epochTime = builder.epochTime;
    this.isoTime = builder.isoTime;
    this.responseSize = builder.responseSize;
    this.requestJson = builder.requestJson;
    this.delegatorId = builder.delegatorId;
    this.origin = builder.origin;
  }

  @Override
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    json.put("primaryKey", primaryKey);
    json.put("userId", userId);
    json.put("id", id);
    json.put("providerId", providerId);
    json.put("resourceGroup", resourceGroup);
    json.put("api", api);
    json.put("epochTime", epochTime);
    json.put("isoTime", isoTime);
    json.put("responseSize", responseSize);
    json.put("requestJson", requestJson);
    json.put("delegatorId", delegatorId);
    json.put("origin", origin);

    return json;
  }

  public static class Builder {
    private String primaryKey;
    private String userId;
    private String id;
    private String providerId;
    private String resourceGroup;
    private String api;
    private long epochTime;
    private String isoTime;
    private long responseSize;
    private JsonObject requestJson;
    private String delegatorId;
    private String origin;

    public Builder setPrimaryKey(String primaryKey) {
      this.primaryKey = primaryKey;
      return this;
    }

    public Builder setUserId(String userId) {
      this.userId = userId;
      return this;
    }

    public Builder setId(String id) {
      this.id = id;
      return this;
    }

    public Builder setProviderId(String providerId) {
      this.providerId = providerId;
      return this;
    }

    public Builder setResourceGroup(String resourceGroup) {
      this.resourceGroup = resourceGroup;
      return this;
    }

    public Builder setApi(String api) {
      this.api = api;
      return this;
    }

    public Builder setEpochTime(long epochTime) {
      this.epochTime = epochTime;
      return this;
    }

    public Builder setIsoTime(String isoTime) {
      this.isoTime = isoTime;
      return this;
    }

    public Builder setResponseSize(long responseSize) {
      this.responseSize = responseSize;
      return this;
    }

    public Builder setRequestJson(JsonObject requestJson) {
      this.requestJson = requestJson;
      return this;
    }

    public Builder setDelegatorId(String delegatorId) {
      this.delegatorId = delegatorId;
      return this;
    }

    public Builder setOrigin(String origin) {
      this.origin = origin;
      return this;
    }

    public OgcAuditLog build() {
      return new OgcAuditLog(this);
    }
  }
}
