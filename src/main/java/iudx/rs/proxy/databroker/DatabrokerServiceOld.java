package iudx.rs.proxy.databroker;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

/*@ProxyGen
@VertxGen*/
public interface DatabrokerServiceOld {

 /* @GenIgnore
  static DatabrokerService createProxy(Vertx vertx, String address) {
    return new DatabrokerServiceVertxEBProxy(vertx, address);
  }*/

  @Fluent
  DatabrokerServiceOld executeAdapterQuery(
      JsonObject request, Handler<AsyncResult<JsonObject>> handler);

  @Fluent
  DatabrokerServiceOld executeAdapterQueryRPC(
      JsonObject request, Handler<AsyncResult<JsonObject>> handler);

  @Fluent
  DatabrokerServiceOld publishMessage(
      JsonObject body,
      String toExchange,
      String routingKey,
      Handler<AsyncResult<JsonObject>> handler);

  @Fluent
  DatabrokerServiceOld createConnector(JsonObject request, Handler<AsyncResult<JsonObject>> handler);

  @Fluent
  DatabrokerServiceOld deleteConnector(JsonObject request, Handler<AsyncResult<JsonObject>> handler);

  @Fluent
  DatabrokerServiceOld resetPassword(String userid, Handler<AsyncResult<JsonObject>> handler);
}
