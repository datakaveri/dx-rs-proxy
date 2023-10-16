package iudx.rs.proxy.optional.consentlogs;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

@ProxyGen
@VertxGen
public interface ConsentLoggingService {
  
  @GenIgnore
  static ConsentLoggingService createProxy(Vertx vertx, String address) {
    return new ConsentLoggingServiceVertxEBProxy(vertx, address);
  }

  Future<JsonObject> log(JsonObject request);

  
}
