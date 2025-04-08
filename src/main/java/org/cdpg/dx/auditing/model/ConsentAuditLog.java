package org.cdpg.dx.auditing.model;

import io.vertx.core.json.JsonObject;

public class ConsentAuditLog implements AuditLog {
  private final String primaryKey;
  private final String isoTime;
  private final String aiu_id;
  private final String aip_id;
  private final String dp_id;
  private final String item_id;
  private final String artifact;
  private final String item_type;
  private final String event;
  private final String log;

  public ConsentAuditLog(
      String primaryKey,
      String isoTime,
      String aiu_id,
      String aip_id,
      String dp_id,
      String item_id,
      String artifact,
      String item_type,
      String event,
      String log) {
    this.primaryKey = primaryKey;
    this.isoTime = isoTime;
    this.aiu_id = aiu_id;
    this.aip_id = aip_id;
    this.dp_id = dp_id;
    this.item_id = item_id;
    this.artifact = artifact;
    this.item_type = item_type;
    this.event = event;
    this.log = log;
  }

  public static ConsentAuditLog fromJson(JsonObject json) {
    String[] requiredFields = {
      "primaryKey", "isoTime", "aiu_id", "aip_id", "dp_id",
      "item_id", "artifact", "item_type", "event", "log"
    };

    for (String field : requiredFields) {
      if (!json.containsKey(field) || json.getValue(field) == null) {
        throw new IllegalArgumentException("Missing or null field in ConsentAuditLog: " + field);
      }
    }

    return new ConsentAuditLog(
        json.getString("primaryKey"),
        json.getString("isoTime"),
        json.getString("aiu_id"),
        json.getString("aip_id"),
        json.getString("dp_id"),
        json.getString("item_id"),
        json.getString("artifact"),
        json.getString("item_type"),
        json.getString("event"),
        json.getString("log"));
  }

  @Override
  public JsonObject toJson() {
    return new JsonObject()
        .put("primaryKey", primaryKey)
        .put("isoTime", isoTime)
        .put("aiu_id", aiu_id)
        .put("aip_id", aip_id)
        .put("dp_id", dp_id)
        .put("item_id", item_id)
        .put("artifact", artifact)
        .put("item_type", item_type)
        .put("event", event)
        .put("log", log);
  }
}
