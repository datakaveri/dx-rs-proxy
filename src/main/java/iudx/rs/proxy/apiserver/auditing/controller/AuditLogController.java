package iudx.rs.proxy.apiserver.auditing.controller;

import static iudx.rs.proxy.apiserver.response.ResponseUtil.generateResponse;
import static iudx.rs.proxy.apiserver.util.ApiServerConstants.*;
import static iudx.rs.proxy.common.Constants.*;
import static iudx.rs.proxy.common.HttpStatusCode.UNAUTHORIZED;
import static iudx.rs.proxy.common.ResponseUrn.UNAUTHORIZED_RESOURCE_URN;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import iudx.rs.proxy.apiserver.handlers.AuthHandler;
import iudx.rs.proxy.apiserver.handlers.FailureHandler;
import iudx.rs.proxy.apiserver.handlers.TokenDecodeHandler;
import iudx.rs.proxy.apiserver.response.ResponseType;
import iudx.rs.proxy.apiserver.auditing.model.AuditLogSearchRequest;
import iudx.rs.proxy.apiserver.auditing.model.OverviewRequest;
import iudx.rs.proxy.apiserver.auditing.service.AuditLogService;
import iudx.rs.proxy.apiserver.auditing.service.AuditLogServiceImpl;
import iudx.rs.proxy.cache.CacheService;
import iudx.rs.proxy.common.Api;
import iudx.rs.proxy.database.DatabaseService;
import iudx.rs.proxy.databroker.service.DatabrokerService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AuditLogController {
  private static final Logger LOGGER = LogManager.getLogger(AuditLogController.class);

  private final Router router;
  private final Api apis;
  private final FailureHandler failureHandler;
  private final boolean isAdexInstance;
  DatabaseService databaseService;
  private DatabrokerService brokerService;
  private CacheService cache;
  private AuditLogService auditLogService;
  private Vertx vertx;

  public AuditLogController(
      Router router, Api apis, FailureHandler failureHandler, Vertx vertx, boolean isAdexInstance) {
    this.router = router;
    this.apis = apis;
    this.failureHandler = failureHandler;
    this.isAdexInstance = isAdexInstance;
    this.vertx = vertx;

    // creating proxy
    brokerService = DatabrokerService.createProxy(vertx, DATABROKER_SERVICE_ADDRESS);
    databaseService = DatabaseService.createProxy(vertx, DB_SERVICE_ADDRESS);
    cache = CacheService.createProxy(vertx, CACHE_SERVICE_ADDRESS);
    auditLogService = new AuditLogServiceImpl(databaseService, cache, apis);
  }

  public void setRouter() {

    router
        .get(apis.getConsumerAuditEndpoint())
        .handler(TokenDecodeHandler.create(vertx))
        .handler(AuthHandler.create(vertx, apis, isAdexInstance))
        .handler(this::handleConsumerAuditDetailRequest);

    router
        .get(apis.getProviderAuditEndpoint())
        .handler(TokenDecodeHandler.create(vertx))
        .handler(AuthHandler.create(vertx, apis, isAdexInstance))
        .handler(this::handleProviderAuditDetail);

    router
        .get(apis.getOverviewEndPoint())
        .handler(TokenDecodeHandler.create(vertx))
        .handler(AuthHandler.create(vertx, apis, isAdexInstance))
        .handler(this::handleMonthlyOverview);

    router
        .get(apis.getSummaryEndPoint())
        .handler(TokenDecodeHandler.create(vertx))
        .handler(AuthHandler.create(vertx, apis, isAdexInstance))
        .handler(this::handleSummaryRequest);

    // Register FailureHandler Globally
    router.route().failureHandler(failureHandler);
  }

  private void handleConsumerAuditDetailRequest(RoutingContext routingContext) {

    LOGGER.debug("Info: handleConsumerAuditDetailRequest() Started.");
    JsonObject authInfo = (JsonObject) routingContext.data().get("authInfo");
    HttpServerRequest request = routingContext.request();

    AuditLogSearchRequest auditLogSearchRequest = AuditLogSearchRequest.fromHttpRequest(request, authInfo);
    LOGGER.debug("AuditLogSearchRequest: {}", auditLogSearchRequest.toJson());
    HttpServerResponse response = routingContext.response();

    auditLogService
        .executeAudigingSearchQuery(auditLogSearchRequest)
        .onSuccess(
            result -> {
              LOGGER.debug("Table Reading Done.");
              String checkType = result.getString("type");
              if (checkType.equalsIgnoreCase("204")) {
                handleSuccessResponse(
                    response, ResponseType.NoContent.getCode(), result.toString());
              } else {
                handleSuccessResponse(response, ResponseType.Ok.getCode(), result.toString());
              }
            })
        .onFailure(
            failure -> {
              LOGGER.error("Error during audit log: {}", failure.getMessage());
              routingContext.fail(failure);
            });
  }

  private void handleProviderAuditDetail(RoutingContext routingContext) {
    LOGGER.trace("Info: getProviderAuditDetail() Started.");
    JsonObject authInfo = (JsonObject) routingContext.data().get("authInfo");
    HttpServerRequest request = routingContext.request();

    AuditLogSearchRequest auditLogSearchRequest = AuditLogSearchRequest.fromHttpRequest(request, authInfo);
    LOGGER.debug("AuditLogSearchRequest: {}", auditLogSearchRequest.toJson());

    HttpServerResponse response = routingContext.response();

    auditLogService
        .executeAudigingSearchQuery(auditLogSearchRequest)
        .onSuccess(
            result -> {
              LOGGER.debug("Table Reading Done.");
              String checkType = result.getString("type");
              if (checkType.equalsIgnoreCase("204")) {
                handleSuccessResponse(
                    response, ResponseType.NoContent.getCode(), result.toString());
              } else {
                handleSuccessResponse(response, ResponseType.Ok.getCode(), result.toString());
              }
            })
        .onFailure(
            failure -> {
              LOGGER.error("Error during audit log: {}", failure.getMessage());
              routingContext.fail(failure);
            });
  }

  private void handleMonthlyOverview(RoutingContext routingContext) {
    LOGGER.trace("Info: getMonthlyOverview Started.");
    HttpServerRequest request = routingContext.request();
    JsonObject authInfo = (JsonObject) routingContext.data().get("authInfo");
    authInfo.put(START_TIME, request.getParam(START_TIME));
    authInfo.put(END_TIME, request.getParam(END_TIME));
    HttpServerResponse response = routingContext.response();

    String iid = authInfo.getString("iid");
    String role = authInfo.getString("role");

    if (!VALIDATION_ID_PATTERN.matcher(iid).matches()
        && (role.equalsIgnoreCase("provider") || role.equalsIgnoreCase("delegate"))) {
      JsonObject jsonResponse =
          generateResponse(UNAUTHORIZED, UNAUTHORIZED_RESOURCE_URN, "Not Authorized");
      response
          .putHeader(CONTENT_TYPE, APPLICATION_JSON)
          .setStatusCode(401)
          .end(jsonResponse.toString());
      return;
    }
    OverviewRequest monthlyOverviewRequest = OverviewRequest.fromJson(authInfo);
    auditLogService
        .monthlyOverview(monthlyOverviewRequest)
        .onSuccess(
            result -> {
              handleSuccessResponse(response, ResponseType.Ok.getCode(), result.encode());
            })
        .onFailure(
            failureHandler -> {
              LOGGER.error(
                  "Error during get getMonthlyOverview data: {}", failureHandler.getMessage());
              routingContext.fail(failureHandler);
            });
  }

  private void handleSummaryRequest(RoutingContext routingContext) {
    LOGGER.trace("getAllSummaryHandler() started");
    HttpServerRequest request = routingContext.request();
    JsonObject authInfo = (JsonObject) routingContext.data().get("authInfo");
    authInfo.put(START_TIME, request.getParam(START_TIME));
    authInfo.put(END_TIME, request.getParam(END_TIME));
    LOGGER.debug("auth info = " + authInfo);
    HttpServerResponse response = routingContext.response();

    String iid = authInfo.getString("iid");
    String role = authInfo.getString("role");

    if (!VALIDATION_ID_PATTERN.matcher(iid).matches()
        && (role.equalsIgnoreCase("provider") || role.equalsIgnoreCase("delegate"))) {
      JsonObject jsonResponse =
          generateResponse(UNAUTHORIZED, UNAUTHORIZED_RESOURCE_URN, "Not Authorized");
      response
          .putHeader(CONTENT_TYPE, APPLICATION_JSON)
          .setStatusCode(401)
          .end(jsonResponse.toString());
      return;
    }
    OverviewRequest summaryOverviewRequest = OverviewRequest.fromJson(authInfo);
    auditLogService
        .summaryOverview(summaryOverviewRequest)
        .onSuccess(
            result -> {
              String checkType = result.getString("type");
              if (checkType.equalsIgnoreCase("204")) {
                handleSuccessResponse(
                    response, ResponseType.NoContent.getCode(), result.toString());
              } else {
                LOGGER.debug("Successfull response: " + result.encode());
                handleSuccessResponse(response, ResponseType.Ok.getCode(), result.toString());
              }
            })
        .onFailure(
            failureHandler -> {
              LOGGER.error(
                  "Error during get summaryOverview data: {}", failureHandler.getMessage());
              routingContext.fail(failureHandler);
            });
  }

  private void handleSuccessResponse(HttpServerResponse response, int statusCode, String result) {
    response.putHeader(CONTENT_TYPE, APPLICATION_JSON).setStatusCode(statusCode).end(result);
  }
}
