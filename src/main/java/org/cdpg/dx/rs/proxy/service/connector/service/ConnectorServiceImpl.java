package org.cdpg.dx.rs.proxy.service.connector.service;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.databroker.service.DataBrokerService;
import org.cdpg.dx.databroker.util.PermissionOpType;
import org.cdpg.dx.rs.proxy.service.connector.model.DeleteConnectorRequest;
import org.cdpg.dx.rs.proxy.service.connector.model.RegisterConnectorRequest;
import org.cdpg.dx.rs.proxy.service.connector.model.RegisterConnectorResponse;
import org.cdpg.dx.rs.proxy.service.connector.model.ResetPasswordResponse;

public class ConnectorServiceImpl implements ConnectorService {

  private final DataBrokerService dataBrokerService;
  Logger LOGGER = LogManager.getLogger(ConnectorServiceImpl.class);

  public ConnectorServiceImpl(DataBrokerService dataBrokerService) {
    this.dataBrokerService = dataBrokerService;
  }

  @Override
  public Future<RegisterConnectorResponse> registerConnector(RegisterConnectorRequest request) {
    LOGGER.trace("ConnectorServiceImpl#registerConnector() started");
    AtomicBoolean queueCreated = new AtomicBoolean(false);

    return dataBrokerService
        .registerQueue(request.getUserId(), request.getResourceId(), request.getVhostType())
        .compose(
            queResult -> {
              LOGGER.info(
                  "Queue: {} created successfully for User: {}",
                  queResult.getQueueName(),
                  request.getUserId());
              queueCreated.set(true);

              return dataBrokerService
                  .queueBinding(
                      "publishEx",
                      queResult.getQueueName(),
                      request.getResourceId(),
                      request.getVhostType())
                  .map(bindingSuccess -> queResult);
            })
        .compose(
            queResult -> {
              return dataBrokerService
                  .updatePermission(
                      queResult.getUserId(),
                      queResult.getQueueName(),
                      PermissionOpType.ADD_WRITE,
                      request.getVhostType())
                  .map(permissionSuccess -> queResult);
            })
        .map(
            queResult -> {
              LOGGER.info(
                  "Connector: {} registered and User permissions updated successfully for User: {}",
                  queResult.getQueueName(),
                  queResult.getUserId());

              return new RegisterConnectorResponse(
                  queResult.getQueueName(), queResult.getApiKey(), queResult.getUserId());
            })
        .recover(
            failure -> {
              if (queueCreated.get()) {
                return dataBrokerService
                    .deleteQueue(
                        request.getUserId(), request.getResourceId(), request.getVhostType())
                    .onSuccess(deletion -> LOGGER.info("Queue deleted after failure"))
                    .onFailure(
                        deleteFailure ->
                            LOGGER.error("Failed to delete queue: {}", deleteFailure.getMessage()))
                    .compose(v -> Future.failedFuture(failure));
              } else {
                return Future.failedFuture(failure);
              }
            });
  }

  @Override
  public Future<String> deleteConnectors(DeleteConnectorRequest request) {
    LOGGER.trace("deleteConnectors() started");

    return dataBrokerService
        .deleteQueue(request.getConnectorId(), request.getUserId(), request.getVhostType())
        .compose(
            deleteResult -> {
              LOGGER.info(
                  "Queue: {} deleted successfully for User: {}",
                  request.getConnectorId(),
                  request.getUserId());

              return dataBrokerService.updatePermission(
                  request.getUserId(),
                  request.getConnectorId(),
                  PermissionOpType.DELETE_READ,
                  request.getVhostType());
            })
        .map(
            permissionUpdateResult ->
                "Connector: " + request.getConnectorId() + " deleted successfully")
        .recover(
            failure -> {
              LOGGER.error(
                  "Failed to delete queue: {} for User: {} - {}",
                  request.getConnectorId(),
                  request.getUserId(),
                  failure.getMessage(),
                  failure);
              return Future.failedFuture(failure);
            });
  }

  @Override
  public Future<ResetPasswordResponse> resetPasswordOfRmqUser(String userId) {
    LOGGER.trace("resetPasswordOfRmqUser() started");

    Promise<ResetPasswordResponse> promise = Promise.promise();

    dataBrokerService
        .resetPassword(userId)
        .onSuccess(
            resetResult -> {
              LOGGER.info("Password reset successfully for User: {}", userId);
              ResetPasswordResponse response = new ResetPasswordResponse(userId, resetResult);
              promise.complete(response);
            })
        .onFailure(
            error -> {
              LOGGER.error(
                  "Failed to reset password for User: {} - {}", userId, error.getMessage());
              promise.fail(error);
            });
    return promise.future();
  }
}
