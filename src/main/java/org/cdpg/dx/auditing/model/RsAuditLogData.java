package org.cdpg.dx.auditing.model;

import io.vertx.core.json.JsonObject;

public class RsAuditLogData implements AuditLog {
  private String primaryKey;
  private String userid;
  private String id;
  private String api;
  private long responseSize;
  private long epochTime;
  private String isoTime;
  private String delegatorId;
  private String origin;

  private RsAuditLogData(Builder builder) {
    this.primaryKey = builder.primaryKey;
    this.userid = builder.userid;
    this.id = builder.id;
    this.api = builder.api;
    this.responseSize = builder.responseSize;
    this.epochTime = builder.epochTime;
    this.isoTime = builder.isoTime;
    this.delegatorId = builder.delegatorId;
    this.origin = builder.origin;
  }

  @Override
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    json.put("primaryKey", primaryKey);
    json.put("userid", userid);
    json.put("id", id);
    json.put("api", api);
    json.put("responseSize", responseSize);
    json.put("epochTime", epochTime);
    json.put("isoTime", isoTime);
    json.put("delegatorId", delegatorId);
    json.put("origin", origin);

    return json;
  }

  public static class Builder {
    private String primaryKey;
    private String userid;
    private String id;
    private String api;
    private long responseSize;
    private long epochTime;
    private String isoTime;
    private String delegatorId;
    private String origin;

    public Builder setPrimaryKey(String primaryKey) {
      this.primaryKey = primaryKey;
      return this;
    }

    public Builder setUserid(String userid) {
      this.userid = userid;
      return this;
    }

    public Builder setId(String id) {
      this.id = id;
      return this;
    }

    public Builder setApi(String api) {
      this.api = api;
      return this;
    }

    public Builder setResponseSize(long responseSize) {
      this.responseSize = responseSize;
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

    public Builder setDelegatorId(String delegatorId) {
      this.delegatorId = delegatorId;
      return this;
    }

    public Builder setOrigin(String origin) {
      this.origin = origin;
      return this;
    }

    public RsAuditLogData build() {
      return new RsAuditLogData(this);
    }
  }
}
