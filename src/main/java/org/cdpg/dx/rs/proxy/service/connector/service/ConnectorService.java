package org.cdpg.dx.rs.proxy.service.connector.service;

import io.vertx.core.Future;
import org.cdpg.dx.rs.proxy.service.connector.model.DeleteConnectorRequest;
import org.cdpg.dx.rs.proxy.service.connector.model.RegisterConnectorRequest;
import org.cdpg.dx.rs.proxy.service.connector.model.RegisterConnectorResponse;
import org.cdpg.dx.rs.proxy.service.connector.model.ResetPasswordResponse;

public interface ConnectorService {

  /**
   * create a connector/queue in dataBroker(RMQ).
   *
   * @param request RegisterConnectorRequest.
   * @return Future RegisterConnectorResponse
   */
  Future<RegisterConnectorResponse> registerConnector(RegisterConnectorRequest request);

  /**
   * delete a connector/queue from dataBroker(RMQ).
   *
   * @param request containing id for sub to delete
   * @return Future String(connectorId)
   */
  Future<String> deleteConnectors(DeleteConnectorRequest request);

  /**
   * delete a connector/queue from dataBroker(RMQ).
   *
   * @param userId sub from jwt token to reset password
   * @return Future String(connectorId)
   */
  Future<ResetPasswordResponse> resetPasswordOfRmqUser(String userId);
}
