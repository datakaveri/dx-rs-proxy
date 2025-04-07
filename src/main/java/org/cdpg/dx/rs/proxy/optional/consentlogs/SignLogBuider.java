package org.cdpg.dx.rs.proxy.optional.consentlogs;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.rs.proxy.optional.consentlogs.dss.SignablePayload;

public class SignLogBuider implements SignablePayload {
  private String primaryKey;
  private String isoTime;
  private String aiu_id;
  private String aip_id;
  private String dp_id;
  private String item_id;
  private String artifact;
  private String item_type;
  private String event;

  public SignLogBuider(Builder builder) {
    this.isoTime = builder.isoTime;
    this.aiu_id = builder.aiu_id;
    this.aip_id = builder.aip_id;
    this.dp_id = builder.dp_id;
    this.item_id = builder.item_id;
    this.primaryKey = builder.primaryKey;
    this.artifact = builder.artifact;
    this.item_type = builder.item_type;
    this.event = builder.event;
  }

  @Override
  public JsonObject toJson() {

    JsonObject consentLogData =
        new JsonObject()
            .put("primaryKey", primaryKey)
            .put("aiu_id", aiu_id)
            .put("item_id", item_id)
            .put("dp_id", dp_id)
            .put("aip_id", aip_id)
            .put("artifact", artifact)
            .put("item_type", item_type)
            .put("event", event)
            .put("isoTime", isoTime);
    return consentLogData;
  }

  public static class Builder {
    private String primaryKey;
    private String isoTime;
    private String aiu_id;
    private String aip_id;
    private String dp_id;
    private String item_id;
    private String artifact;
    private String item_type;
    private String event;

    public Builder atIsoTime(String isoTime) {
      this.isoTime = isoTime;
      return this;
    }

    public Builder forAiu_id(String aiu_id) {
      this.aiu_id = aiu_id;
      return this;
    }

    public Builder witAipId(String aip_id) {
      this.aip_id = aip_id;
      return this;
    }

    public Builder withDpId(String dp_id) {
      this.dp_id = dp_id;
      return this;
    }

    public Builder withArtifactId(String artifact) {
      this.artifact = artifact;
      return this;
    }

    public Builder withPrimaryKey(String primaryKey) {
      this.primaryKey = primaryKey;
      return this;
    }

    public Builder forItem_id(String item_id) {
      this.item_id = item_id;
      return this;
    }

    public Builder forItemType(String item_type) {
      this.item_type = item_type;
      return this;
    }

    public Builder forEvent(String event) {
      this.event = event;
      return this;
    }

    public SignLogBuider build() {
      return new SignLogBuider(this);
    }
  }
}
