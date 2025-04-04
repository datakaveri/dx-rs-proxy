package org.cdpg.dx.auditing.model;

import io.vertx.core.json.JsonObject;

public class AclAuditLog implements AuditLog {
  private String primaryKey;
  private String userId;
  private JsonObject body;
  private String api;
  private String httpMethod;
  private String epochTime;
  private String isoTime;
  private String responseSize;
  private String origin;

  // Private constructor to prevent direct instantiation
  private AclAuditLog(Builder builder) {
    this.primaryKey = builder.primaryKey;
    this.userId = builder.userId;
    this.body = builder.body;
    this.api = builder.api;
    this.httpMethod = builder.httpMethod;
    this.epochTime = builder.epochTime;
    this.isoTime = builder.isoTime;
    this.responseSize = builder.responseSize;
    this.origin = builder.origin;
  }

  @Override
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    json.put("primaryKey", this.primaryKey);
    json.put("userId", this.userId);
    json.put("body", this.body);
    json.put("api", this.api);
    json.put("httpMethod", this.httpMethod);
    json.put("epochTime", this.epochTime);
    json.put("isoTime", this.isoTime);
    json.put("responseSize", this.responseSize);
    json.put("origin", this.origin);

    return json;
  }

  public static class Builder {
    private String primaryKey;
    private String userId;
    private JsonObject body;
    private String api;
    private String httpMethod;
    private String epochTime;
    private String isoTime;
    private String responseSize;
    private String origin;

    public Builder setPrimaryKey(String primaryKey) {
      this.primaryKey = primaryKey;
      return this;
    }

    public Builder setUserId(String userId) {
      this.userId = userId;
      return this;
    }

    public Builder setBody(JsonObject body) {
      this.body = body;
      return this;
    }

    public Builder setApi(String api) {
      this.api = api;
      return this;
    }

    public Builder setHttpMethod(String httpMethod) {
      this.httpMethod = httpMethod;
      return this;
    }

    public Builder setEpochTime(String epochTime) {
      this.epochTime = epochTime;
      return this;
    }

    public Builder setIsoTime(String isoTime) {
      this.isoTime = isoTime;
      return this;
    }

    public Builder setResponseSize(String responseSize) {
      this.responseSize = responseSize;
      return this;
    }

    public Builder setOrigin(String origin) {
      this.origin = origin;
      return this;
    }

    public AclAuditLog build() {
      return new AclAuditLog(this);
    }
  }
}
