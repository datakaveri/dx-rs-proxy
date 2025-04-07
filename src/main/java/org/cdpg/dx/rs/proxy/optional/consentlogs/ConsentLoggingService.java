package org.cdpg.dx.rs.proxy.optional.consentlogs;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.cdpg.dx.auditing.model.AuditLog;
import org.cdpg.dx.common.models.JwtData;


@ProxyGen
@VertxGen
public interface ConsentLoggingService {

  @GenIgnore
  static ConsentLoggingService createProxy(Vertx vertx, String address) {
    return new ConsentLoggingServiceVertxEBProxy(vertx, address);
  }

  Future<AuditLog> log(String consentType, JwtData jwtData);
}
