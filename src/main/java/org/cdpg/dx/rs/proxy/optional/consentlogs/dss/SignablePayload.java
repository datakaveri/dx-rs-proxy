package org.cdpg.dx.rs.proxy.optional.consentlogs.dss;

import io.vertx.core.json.JsonObject;
import java.nio.charset.StandardCharsets;

public interface SignablePayload {
  JsonObject toJson();

  default byte[] toBytes() {
    return toJson().encode().getBytes(StandardCharsets.UTF_8);
  }
}
