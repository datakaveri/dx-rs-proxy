package iudx.rs.proxy.databroker.service;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import iudx.rs.proxy.databroker.model.*;
import iudx.rs.proxy.databroker.util.Vhosts;

@ProxyGen
@VertxGen
public interface DatabrokerService {

  @GenIgnore
  static DatabrokerService createProxy(Vertx vertx, String address) {
    return new DatabrokerServiceVertxEBProxy(vertx, address);
  }

  Future<QueueResponse> createQueue(QueueRequest queueRequest);

  Future<String> deleteQueue(String queueName, Vhosts vhostType);

  Future<QueueResponse> bindQueue(QueueRequest request);

  Future<UserResponse> createUserIfNotExist(String userid, Vhosts vhostType);

  Future<RabbitMQPermission> updateUserPermissions(UserPermissionRequest permissionRequest);

  Future<JsonObject> resetPasswordInRmq(String userid, String password);

  Future<Void> publishMessage(JsonObject request, String toExchange, String routingKey);

  Future<JsonObject> executeAdapterQueryRPC(JsonObject request);
}
