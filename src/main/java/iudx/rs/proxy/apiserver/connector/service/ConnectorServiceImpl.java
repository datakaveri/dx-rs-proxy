package iudx.rs.proxy.apiserver.connector.service;

import static iudx.rs.proxy.databroker.util.Constants.*;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import iudx.rs.proxy.apiserver.connector.model.*;
import iudx.rs.proxy.common.ResponseUrn;
import iudx.rs.proxy.databroker.DatabrokerService;
import iudx.rs.proxy.databroker.model.QueueRequest;
import iudx.rs.proxy.databroker.model.UserPermissionRequest;
import iudx.rs.proxy.databroker.util.PermissionOpType;
import iudx.rs.proxy.databroker.util.Util;
import iudx.rs.proxy.databroker.util.Vhosts;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ConnectorServiceImpl implements ConnectorService {
  Logger LOGGER = LogManager.getLogger(ConnectorServiceImpl.class);
  DatabrokerService rabbitClient;

  public ConnectorServiceImpl(DatabrokerService brokerService) {
    this.rabbitClient = brokerService;
  }

  @Override
  public Future<RegisterConnectorResponse> registerConnector(RegisterConnectorRequest request) {
    LOGGER.trace("Info: ConnectorServiceImpl#registerConnector() started");

    String userid = request.getUserId();
    String resourceId = request.getResourceId();
    Vhosts vhostType = request.getVhostType();

    ConnectorResultContainer resultContainer = new ConnectorResultContainer();
    resultContainer.setUserId(userid);

    QueueRequest queueRequest = new QueueRequest();
    queueRequest.setResourceId(resourceId);
    queueRequest.setUserId(userid);
    queueRequest.setVhostType(vhostType);

    // Step 1: Validate/Create User
    return rabbitClient
        .createUserIfNotExist(userid, vhostType)
        .compose(
            createUserResult -> {
              LOGGER.info("User validation/creation successful for User: {}", userid);
              resultContainer.setUserId(createUserResult.getUserId());
              resultContainer.setApiKey(createUserResult.getApiKey());

              // Step 2: Create Queue
              return rabbitClient.createQueue(queueRequest);
            })
        .compose(
            createQueueResult -> {
              LOGGER.info("Queue: {} created successfully for User: {}", userid);
              resultContainer.setConnectorId(createQueueResult.getQueueName());
              resultContainer.setQueueCreated(true);

              // Step 3: Bind Queue
              return rabbitClient.bindQueue(queueRequest);
            })
        .compose(
            bindQueueResult -> {
              LOGGER.info(
                  "Queue binding successful for QueueName: {}", resultContainer.getConnectorId());

              // Step 4: Update User Permissions
              UserPermissionRequest permissionRequest =
                  new UserPermissionRequest.Builder()
                      .userId(userid)
                      .vhostType(vhostType)
                      .type(PermissionOpType.ADD_WRITE)
                      .resourceId("amq.default")
                      .build();

              return rabbitClient.updateUserPermissions(permissionRequest);
            })
        .map(
            successHandler -> {
              LOGGER.info("User permissions updated successfully for User: {}", userid);

              // Build Success Response
              RegisterConnectorResponse response = new RegisterConnectorResponse();
              response.setType(ResponseUrn.SUCCESS_URN.getUrn());
              response.setTitle(ResponseUrn.SUCCESS_URN.getMessage());
              response.setUserName(resultContainer.getUserId());
              response.setApiKey(resultContainer.getApiKey());
              response.setConnectorName(resultContainer.getConnectorId());

              return response;
            })
        .recover(
            error -> {
              LOGGER.error(
                  "Error during connector registration for User: {}. Error: {}",
                  userid,
                  error.getMessage());

              if (resultContainer.isQueueCreated()) {
                LOGGER.warn(
                    "Rolling back: Deleting created queue: {}", resultContainer.getConnectorId());

                return rabbitClient
                    .deleteQueue(resultContainer.getConnectorId(), vhostType)
                    .compose(v -> Future.failedFuture(error));
              }

              return Future.failedFuture(error);
            });
  }

  @Override
  public Future<DeleteConnectorResponse> deleteConnectors(DeleteConnectorRequest request) {
    LOGGER.trace("Info: ConnectorServiceImpl#deleteConnector() started");

    String userid = request.getUserId();
    String resourceId = request.getConnectorId();
    Vhosts vhostType = request.getVhostType();

    ConnectorResultContainer resultContainer = new ConnectorResultContainer();
    resultContainer.setUserId(userid);
    resultContainer.setConnectorId(resourceId);

    // Step 1: Delete Queue
    return rabbitClient
        .deleteQueue(resourceId, vhostType)
        .compose(
            deleteQueueResult -> {
              LOGGER.info("Queue: {} deleted successfully for User: {}", resourceId, userid);

              // Step 2: Update User Permissions
              UserPermissionRequest permissionRequest =
                  new UserPermissionRequest.Builder()
                      .userId(userid)
                      .vhostType(Vhosts.IUDX_INTERNAL)
                      .type(PermissionOpType.ADD_WRITE)
                      .resourceId("amq.default")
                      .build();
              return rabbitClient.updateUserPermissions(permissionRequest);
            })
        .map(
            successHandler -> {
              LOGGER.info("User permissions updated successfully for User: {}", userid);

              // Build Success Response
              DeleteConnectorResponse response = new DeleteConnectorResponse();
              response.setType(ResponseUrn.SUCCESS_URN.getUrn());
              response.setTitle(ResponseUrn.SUCCESS_URN.getMessage());
              response.setUserName(resultContainer.getUserId());
              response.setConnectorName(resultContainer.getConnectorId());
              return response;
            })
        .recover(
            error -> {
              // Error Handling
              LOGGER.error(
                  "Error during connector deletion for User: {}. Error: {}",
                  userid,
                  error.getMessage());

              // If deletion failed, return failure response
              return Future.failedFuture(error);
            });
  }

  @Override
  public Future<JsonObject> resetPasswordOfRmqUser(String userId) {
    Promise<JsonObject> promise = Promise.promise();
    String password = Util.randomPassword.get();
    rabbitClient
        .resetPasswordInRmq(userId, password)
        .onSuccess(promise::complete)
        .onFailure(
            failure -> {
              LOGGER.error(
                  "Error during resting password for User: {}. Error: {}",
                  userId,
                  failure.getMessage());
              promise.fail(failure);
            });
    return promise.future();
  }
}
