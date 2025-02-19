package iudx.rs.proxy.apiserver.connector.controller;

import static iudx.rs.proxy.apiserver.response.ResponseUtil.generateResponse;
import static iudx.rs.proxy.apiserver.util.ApiServerConstants.*;
import static iudx.rs.proxy.apiserver.util.RequestType.POST_CONNECTOR;
import static iudx.rs.proxy.common.Constants.CACHE_SERVICE_ADDRESS;
import static iudx.rs.proxy.common.Constants.DATABROKER_SERVICE_ADDRESS;
import static iudx.rs.proxy.common.HttpStatusCode.BAD_REQUEST;
import static iudx.rs.proxy.common.ResponseUrn.INVALID_PARAM_URN;

import io.netty.handler.codec.http.HttpConstants;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import iudx.rs.proxy.apiserver.connector.model.DeleteConnectorRequest;
import iudx.rs.proxy.apiserver.connector.model.RegisterConnectorRequest;
import iudx.rs.proxy.apiserver.connector.service.ConnectorService;
import iudx.rs.proxy.apiserver.connector.service.ConnectorServiceImpl;
import iudx.rs.proxy.apiserver.exceptions.DxRuntimeException;
import iudx.rs.proxy.apiserver.handlers.AuthHandler;
import iudx.rs.proxy.apiserver.handlers.FailureHandler;
import iudx.rs.proxy.apiserver.handlers.TokenDecodeHandler;
import iudx.rs.proxy.apiserver.handlers.ValidationHandler;
import iudx.rs.proxy.apiserver.response.ResponseType;
import iudx.rs.proxy.apiserver.util.ApiServerConstants;
import iudx.rs.proxy.cache.CacheService;
import iudx.rs.proxy.common.Api;
import iudx.rs.proxy.databroker.DatabrokerService;
import iudx.rs.proxy.databroker.util.Vhosts;
import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ConnectorController {
  private static final Logger LOGGER = LogManager.getLogger(ConnectorController.class);

  private Router router;
  private Api apis;
  private boolean isAdexInstance;
  private String dxApiBasePath;
  private DatabrokerService brokerService;
  private CacheService cache;
  private ConnectorService connectorService;

  private Vertx vertx;

  public ConnectorController(
      Router router, Api apis, Vertx vertx, boolean isAdexInstance, String dxApiBasePath) {
    this.router = router;
    this.apis = apis;
    this.isAdexInstance = isAdexInstance;
    this.vertx = vertx;
    this.dxApiBasePath = dxApiBasePath;
  }

  public void setRouter() {
    ValidationHandler postConnectorValidation = new ValidationHandler(vertx, POST_CONNECTOR);
    FailureHandler failureHandler = new FailureHandler();
    brokerService = DatabrokerService.createProxy(vertx, DATABROKER_SERVICE_ADDRESS);
    connectorService = new ConnectorServiceImpl(brokerService);
    cache = CacheService.createProxy(vertx, CACHE_SERVICE_ADDRESS);

    router
        .post(apis.getConnectorsPath())
        .handler(postConnectorValidation)
        .handler(TokenDecodeHandler.create(vertx))
        .handler(AuthHandler.create(vertx, apis, isAdexInstance))
        .handler(this::handlePostConnectors);

    router
        .delete(apis.getConnectorsPath())
        .handler(TokenDecodeHandler.create(vertx))
        .handler(AuthHandler.create(vertx, apis, isAdexInstance))
        .handler(this::handleDeleteConnectors);

    router
        .post(dxApiBasePath + ApiServerConstants.RESET_PWD)
        .handler(TokenDecodeHandler.create(vertx))
        .handler(AuthHandler.create(vertx, apis, isAdexInstance))
        .handler(this::resetPassword);

    // Register FailureHandler Globally
    router.route().failureHandler(failureHandler);
  }

  void handlePostConnectors(RoutingContext routingContext) {
    LOGGER.trace("handlePostConnectors() started");
    JsonObject requestJson = routingContext.body().asJsonObject();
    HttpServerResponse response = routingContext.response();

    // Populate Required Fields
    JsonObject authInfo = (JsonObject) routingContext.data().get("authInfo");
    String resourceId = requestJson.getJsonArray("entities").getJsonObject(0).getString(ID);
    String userId = authInfo.getString(USER_ID);
    Vhosts vhosts = Vhosts.IUDX_INTERNAL;

    RegisterConnectorRequest request = new RegisterConnectorRequest(userId, resourceId, vhosts);

    LOGGER.debug("RegisterConnectorRequest: " + request.toJson());

    connectorService
        .registerConnector(request)
        .onSuccess(
            connectorResult -> {
              LOGGER.info("Success: [registerConnector]");
              handleSuccessResponse(
                  response, ResponseType.Created.getCode(), connectorResult.toJson().encode());
            })
        .onFailure(
            error -> {
              LOGGER.error("Error during connector registration: {}", error.getMessage());

              routingContext.fail(new DxRuntimeException(error.getMessage()));
            });
  }

  void handleDeleteConnectors(RoutingContext routingContext) {
    LOGGER.trace("handleDeleteConnectors () started");
    HttpServerResponse response = routingContext.response();
    MultiMap params = getQueryParams(routingContext, response).get();

    JsonObject authInfo = (JsonObject) routingContext.data().get("authInfo");

    String userId = authInfo.getString(USER_ID);
    String connectorId = params.get(ID);

    DeleteConnectorRequest deleteConnectorRequest =
        new DeleteConnectorRequest(userId, connectorId, Vhosts.IUDX_INTERNAL);

    connectorService
        .deleteConnectors(deleteConnectorRequest)
        .onSuccess(
            deleteHandler -> {
              LOGGER.info("success: [handleDeleteConnectors] " + deleteHandler);
              handleSuccessResponse(
                  response, ResponseType.Ok.getCode(), deleteHandler.toJson().encode());
            })
        .onFailure(
            failure -> {
              LOGGER.error("Error: Connector/Queue deletion failed: {}", failure.getMessage());
              routingContext.fail(new DxRuntimeException(failure.getMessage()));
            });
  }

  public void resetPassword(RoutingContext routingContext) {
    LOGGER.trace("Info: resetPassword() method started");

    HttpServerResponse response = routingContext.response();
    JsonObject authInfo = (JsonObject) routingContext.data().get("authInfo");
    LOGGER.debug("authInfo: " + authInfo);
    String userid = authInfo.getString(USER_ID);
    LOGGER.debug("userid : {}", userid);

    connectorService
        .resetPasswordOfRmqUser(userid)
        .onSuccess(
            password ->
                handleSuccessResponse(response, ResponseType.Ok.getCode(), password.encode()))
        .onFailure(
            failure -> {
              LOGGER.error("Error: uer rest password failed: {}", failure.getMessage());
              routingContext.fail(new DxRuntimeException(failure.getMessage()));
            });
  }

  private Optional<MultiMap> getQueryParams(
      RoutingContext routingContext, HttpServerResponse response) {
    MultiMap queryParams = null;
    try {
      queryParams = MultiMap.caseInsensitiveMultiMap();
      // Internally + sign is dropped and treated as space, replacing + with %2B do the trick
      String uri = routingContext.request().uri().replaceAll("\\+", "%2B");
      Map<String, List<String>> decodedParams =
          new QueryStringDecoder(uri, HttpConstants.DEFAULT_CHARSET, true, 1024, true).parameters();
      for (Map.Entry<String, List<String>> entry : decodedParams.entrySet()) {
        LOGGER.debug("Info: param :" + entry.getKey() + " value : " + entry.getValue());
        queryParams.add(entry.getKey(), entry.getValue());
      }
    } catch (IllegalArgumentException ex) {
      response
          .putHeader(CONTENT_TYPE, APPLICATION_JSON)
          .setStatusCode(BAD_REQUEST.getValue())
          .end(generateResponse(BAD_REQUEST, INVALID_PARAM_URN).toString());
    }
    return Optional.of(queryParams);
  }

  private void handleSuccessResponse(HttpServerResponse response, int statusCode, String result) {
    response.putHeader(CONTENT_TYPE, APPLICATION_JSON).setStatusCode(statusCode).end(result);
  }
}
