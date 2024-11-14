package iudx.rs.proxy.apiserver;

import static iudx.rs.proxy.apiserver.response.ResponseUtil.generateResponse;
import static iudx.rs.proxy.apiserver.util.ApiServerConstants.*;
import static iudx.rs.proxy.apiserver.util.RequestType.POST_CONNECTOR;
import static iudx.rs.proxy.apiserver.util.Util.errorResponse;
import static iudx.rs.proxy.authenticator.Constants.*;
import static iudx.rs.proxy.common.Constants.*;
import static iudx.rs.proxy.common.HttpStatusCode.*;
import static iudx.rs.proxy.common.ResponseUrn.*;
import static iudx.rs.proxy.databroker.util.Constants.CONNECTOR_ID;
import static iudx.rs.proxy.metering.util.Constants.DETAIL;
import static iudx.rs.proxy.metering.util.Constants.ERROR;

import io.netty.handler.codec.http.HttpConstants;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import iudx.rs.proxy.apiserver.exceptions.DxRuntimeException;
import iudx.rs.proxy.apiserver.handlers.*;
import iudx.rs.proxy.apiserver.query.NGSILDQueryParams;
import iudx.rs.proxy.apiserver.query.QueryMapper;
import iudx.rs.proxy.apiserver.response.ResponseType;
import iudx.rs.proxy.apiserver.util.ApiServerConstants;
import iudx.rs.proxy.apiserver.util.RequestType;
import iudx.rs.proxy.cache.CacheService;
import iudx.rs.proxy.cache.cacheImpl.CacheType;
import iudx.rs.proxy.common.Api;
import iudx.rs.proxy.common.HttpStatusCode;
import iudx.rs.proxy.common.ResponseUrn;
import iudx.rs.proxy.database.DatabaseService;
import iudx.rs.proxy.databroker.DatabrokerService;
import iudx.rs.proxy.metering.MeteringService;
import iudx.rs.proxy.optional.consentlogs.ConsentLoggingService;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ApiServerVerticle extends AbstractVerticle {

  private static final Logger LOGGER = LogManager.getLogger(ApiServerVerticle.class);

  private int port;
  private HttpServer server;
  private Router router;
  private String keystore;
  private String keystorePassword;
  // private boolean isSSL, isProduction;
  private ParamsValidator validator;
  private DatabaseService databaseService;
  private MeteringService meteringService;
  private DatabrokerService brokerService;
  private String dxCatalogueBasePath;
  private String dxAuthBasePath;
  private String dxApiBasePath;
  private ConsentLoggingService consentLoggingService;
  private CacheService cacheService;
  private boolean isAdexInstance;

  @Override
  public void start() throws Exception {
    databaseService = DatabaseService.createProxy(vertx, DB_SERVICE_ADDRESS);
    meteringService = MeteringService.createProxy(vertx, METERING_SERVICE_ADDRESS);
    brokerService = DatabrokerService.createProxy(vertx, DATABROKER_SERVICE_ADDRESS);
    consentLoggingService = ConsentLoggingService.createProxy(vertx, CONSEENTLOG_SERVICE_ADDRESS);
    cacheService = CacheService.createProxy(vertx, CACHE_SERVICE_ADDRESS);
    validator = new ParamsValidator(cacheService);

    /* Get base paths from config */
    dxApiBasePath = config().getString("dxApiBasePath");
    dxCatalogueBasePath = config().getString("dxCatalogueBasePath");
    dxAuthBasePath = config().getString("dxAuthBasePath");
    isAdexInstance = config().getBoolean("isAdexInstance");

    router = Router.router(vertx);
    attachCORSHandlers(router);
    attachDefaultResponses(router);
    router.route().handler(BodyHandler.create());

    FailureHandler validationsFailureHandler = new FailureHandler();
    Api apis = Api.getInstance(dxApiBasePath);
    ValidationHandler entityValidationHandler = new ValidationHandler(vertx, RequestType.ENTITY);
    router
        .get(apis.getEntitiesEndpoint())
        .handler(entityValidationHandler)
        .handler(TokenDecodeHandler.create(vertx))
        .handler(new ConsentLogRequestHandler(vertx, isAdexInstance))
        .handler(AuthHandler.create(vertx, apis, isAdexInstance))
        .handler(this::contextBodyCall)
        .handler(this::handleEntitiesQuery)
        .failureHandler(validationsFailureHandler);

    ValidationHandler temporalValidationHandler =
        new ValidationHandler(vertx, RequestType.TEMPORAL);
    router
        .get(apis.getTemporalEndpoint())
        .handler(temporalValidationHandler)
        .handler(TokenDecodeHandler.create(vertx))
        .handler(new ConsentLogRequestHandler(vertx, isAdexInstance))
        .handler(AuthHandler.create(vertx, apis, isAdexInstance))
        .handler(this::contextBodyCall)
        .handler(this::handleTemporalQuery)
        .failureHandler(validationsFailureHandler);

    router
        .get(apis.getConsumerAuditEndpoint())
        .handler(TokenDecodeHandler.create(vertx))
        .handler(AuthHandler.create(vertx, apis, isAdexInstance))
        .handler(this::getConsumerAuditDetail);

    router
        .get(apis.getProviderAuditEndpoint())
        .handler(TokenDecodeHandler.create(vertx))
        .handler(AuthHandler.create(vertx, apis, isAdexInstance))
        .handler(this::getProviderAuditDetail);

    // Post Queries
    ValidationHandler postEntitiesValidationHandler =
        new ValidationHandler(vertx, RequestType.POST_ENTITIES);
    router
        .post(apis.getPostEntitiesEndpoint())
        .consumes(APPLICATION_JSON)
        .handler(postEntitiesValidationHandler)
        .handler(TokenDecodeHandler.create(vertx))
        .handler(new ConsentLogRequestHandler(vertx, isAdexInstance))
        .handler(AuthHandler.create(vertx, apis, isAdexInstance))
        .handler(this::contextBodyCall)
        .handler(this::handlePostEntitiesQuery)
        .failureHandler(validationsFailureHandler);

    ValidationHandler postTemporalValidationHandler =
        new ValidationHandler(vertx, RequestType.POST_TEMPORAL);
    router
        .post(apis.getPostTemporalEndpoint())
        .consumes(APPLICATION_JSON)
        .handler(postTemporalValidationHandler)
        .handler(TokenDecodeHandler.create(vertx))
        .handler(new ConsentLogRequestHandler(vertx, isAdexInstance))
        .handler(AuthHandler.create(vertx, apis, isAdexInstance))
        .handler(this::contextBodyCall)
        .handler(this::handlePostEntitiesQuery)
        .failureHandler(validationsFailureHandler);

    router
        .get(apis.getOverviewEndPoint())
        .handler(TokenDecodeHandler.create(vertx))
        .handler(AuthHandler.create(vertx, apis, isAdexInstance))
        .handler(this::getMonthlyOverview)
        .failureHandler(validationsFailureHandler);

    // Metering Summary
    router
        .get(apis.getSummaryEndPoint())
        .handler(TokenDecodeHandler.create(vertx))
        .handler(AuthHandler.create(vertx, apis, isAdexInstance))
        .handler(this::getAllSummaryHandler)
        .failureHandler(validationsFailureHandler);

    ValidationHandler postConnectorValidation = new ValidationHandler(vertx, POST_CONNECTOR);

    router
        .post(apis.getConnectorsPath())
        .handler(postConnectorValidation)
        .handler(TokenDecodeHandler.create(vertx))
        .handler(AuthHandler.create(vertx, apis, isAdexInstance))
        .handler(this::handlePostConnectors)
        .failureHandler(validationsFailureHandler);

    router
        .delete(apis.getConnectorsPath())
        .handler(TokenDecodeHandler.create(vertx))
        .handler(AuthHandler.create(vertx, apis, isAdexInstance))
        .handler(this::handleDeleteConnectors)
        .failureHandler(validationsFailureHandler);

    router
        .post(dxApiBasePath + ApiServerConstants.RESET_PWD)
        .handler(TokenDecodeHandler.create(vertx))
        .handler(AuthHandler.create(vertx, apis, isAdexInstance))
        .handler(this::resetPassword);

    /* Static Resource Handler */
    /* Get openapiv3 spec */
    router
        .get(ROUTE_STATIC_SPEC)
        .produces(FORMAT_JSON)
        .handler(
            routingContext -> {
              HttpServerResponse response = routingContext.response();
              response.sendFile("docs/openapi.yaml");
            });
    /* Get redoc */
    router
        .get(ROUTE_DOC)
        .produces(FORMAT)
        .handler(
            routingContext -> {
              HttpServerResponse response = routingContext.response();
              response.sendFile("docs/apidoc.html");
            });

    router
        .route(apis.getAsyncPath() + "/*")
        .subRouter(new AsyncRestApi(vertx, router, apis, config()).init());

    router
        .route()
        .last()
        .handler(
            requestHandler -> {
              HttpServerResponse response = requestHandler.response();
              response
                  .putHeader(HEADER_CONTENT_TYPE, MIME_APPLICATION_JSON)
                  .setStatusCode(404)
                  .end(generateResponse(NOT_FOUND, YET_NOT_IMPLEMENTED_URN).toString());
            });

    HttpServerOptions serverOptions = new HttpServerOptions();
    setServerOptions(serverOptions);
    serverOptions.setCompressionSupported(true).setCompressionLevel(5);
    int port = config().getInteger("port");
    server = vertx.createHttpServer(serverOptions);
    server.requestHandler(router).listen(port);
    LOGGER.debug("port deployed : " + server.actualPort());
    printDeployedEndpoints(router);
  }

  public void contextBodyCall(RoutingContext context) {
    context.addBodyEndHandler(v -> logConsentResponse(context));
    context.next();
  }

  private void setServerOptions(HttpServerOptions serverOptions) {
    boolean isSsl = config().getBoolean("ssl");
    boolean isProduction = config().getBoolean("production");
    if (isSsl) {
      LOGGER.info("Info: Starting HTTPs server");
      keystore = config().getString("keystore");
      keystorePassword = config().getString("keystorePassword");
      serverOptions
          .setSsl(true)
          .setKeyStoreOptions(new JksOptions().setPath(keystore).setPassword(keystorePassword));
    } else {
      LOGGER.info("Info: Starting HTTP server");
      serverOptions.setSsl(false);
      if (isProduction) {
        port = 80;
      } else {
        port = 8080;
      }
    }
  }

  private void attachCORSHandlers(Router router) {
    router
        .route()
        .handler(
            CorsHandler.create("*").allowedHeaders(ALLOWED_HEADERS).allowedMethods(ALLOWED_METHODS))
        .handler(
            responseHeaderHandler -> {
              responseHeaderHandler
                  .response()
                  .putHeader("Cache-Control", "no-cache, no-store,  must-revalidate,max-age=0")
                  .putHeader("Pragma", "no-cache")
                  .putHeader("Expires", "0")
                  .putHeader("X-Content-Type-Options", "nosniff");
              responseHeaderHandler.next();
            });
  }

  private void attachDefaultResponses(Router router) {
    HttpStatusCode[] statusCodes = HttpStatusCode.values();
    Stream.of(statusCodes)
        .forEach(
            code -> {
              router.errorHandler(
                  code.getValue(),
                  errorHandler -> {
                    HttpServerResponse response = errorHandler.response();
                    if (response.headWritten()) {
                      try {
                        response.close();
                      } catch (RuntimeException e) {
                        LOGGER.error("Error : " + e);
                      }
                      return;
                    }
                    response
                        .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                        .setStatusCode(code.getValue())
                        .end(errorResponse(code));
                  });
            });
  }

  private Future<Void> logConsentResponse(RoutingContext routingContext) {
    LOGGER.info("logging consent log for response");
    Promise<Void> promise = Promise.promise();
    if (isAdexInstance) {
      String consentLog = null;
      switch (routingContext.response().getStatusCode()) {
        case 200:
        case 201:
          consentLog = "DATA_SENT";
          break;
        case 400:
        case 401:
        case 404:
          consentLog = "DATA_DENIED";
          break;
        default:
          consentLog = "DATA_DENIED";
      }
      LOGGER.info("response ended : {}", routingContext.response());
      LOGGER.info("consent log : {}", consentLog);
      LOGGER.info("response code : {}", routingContext.response().getStatusCode());
      LOGGER.info("response body : {}", routingContext.response().bodyEndHandler(null));

      String finalConsentLog = consentLog;

      JsonObject logRequest = new JsonObject();
      logRequest.put("logType", finalConsentLog);

      Future<JsonObject> consentAuditLog =
          consentLoggingService.log(logRequest, routingContext.get("jwtData"));
      consentAuditLog
          .onSuccess(auditLogHandler -> promise.complete())
          .onFailure(
              auditLogFailure -> {
                LOGGER.warn("failed info: {}", auditLogFailure.getMessage());
                promise.fail(auditLogFailure);
              });
    }

    return promise.future();
  }

  public void resetPassword(RoutingContext routingContext) {
    LOGGER.trace("Info: resetPassword method started");

    HttpServerResponse response = routingContext.response();
    JsonObject authInfo = (JsonObject) routingContext.data().get("authInfo");
    LOGGER.debug("authInfo: " + authInfo);
    String userid = authInfo.getString(USER_ID);
    LOGGER.debug("userid : {}", userid);

    brokerService.resetPassword(
        userid,
        handler -> {
          if (handler.succeeded()) {
            handleSuccessResponse(response, ResponseType.Ok.getCode(), handler.result().toString());
          } else {
            handleResponse(response, UNAUTHORIZED, INVALID_TOKEN_URN);
          }
        });
  }

  void handlePostConnectors(RoutingContext routingContext) {
    LOGGER.trace("handleRegisterAdapter () started");
    JsonObject requestJson = routingContext.body().asJsonObject();
    HttpServerRequest request = routingContext.request();
    HttpServerResponse response = routingContext.response();
    String instanceId = request.getHeader(HEADER_HOST);
    requestJson.put(JSON_INSTANCEID, instanceId);
    JsonObject authInfo = (JsonObject) routingContext.data().get("authInfo");
    requestJson.put(USER_ID, authInfo.getString(USER_ID));
    LOGGER.debug("authInfo: {}", authInfo);
    String resourceId = requestJson.getJsonArray("entities").getJsonObject(0).getString(ID);
    JsonObject newRequest = new JsonObject();
    newRequest.put("resourceId", resourceId);
    newRequest.put("userid", authInfo.getString(USER_ID));
    brokerService.createConnector(
        newRequest,
        connectorHandler -> {
          if (connectorHandler.succeeded()) {
            LOGGER.info("success: [registerConnector]");
            Future.future(fu -> updateAuditTable(routingContext));
            handleSuccessResponse(
                response, ResponseType.Created.getCode(), connectorHandler.result().toString());
            routingContext.data().put(RESPONSE_SIZE, response.bytesWritten());
            Future.future(fu -> updateAuditTable(routingContext));
          } else {
            processBackendResponse(response, connectorHandler.cause().getMessage());
          }
        });
  }

  void handleDeleteConnectors(RoutingContext routingContext) {
    LOGGER.trace("handleDeleteConnectors () started");
    HttpServerResponse response = routingContext.response();
    MultiMap params = getQueryParams(routingContext, response).get();
    String connectorId = params.get(ID);
    JsonObject authInfo = (JsonObject) routingContext.data().get("authInfo");
    JsonObject request = new JsonObject();
    request.put(CONNECTOR_ID, connectorId);
    request.put(USER_ID, authInfo.getString(USER_ID));
    brokerService.deleteConnector(
        request,
        deleteHandler -> {
          if (deleteHandler.succeeded()) {
            LOGGER.info("success: [handleDeleteConnectors] " + deleteHandler);
            handleSuccessResponse(
                response, ResponseType.Ok.getCode(), deleteHandler.result().toString());
            routingContext.data().put(RESPONSE_SIZE, response.bytesWritten());
            Future.future(fu -> updateAuditTable(routingContext));
          } else {
            LOGGER.error("Error: Connector/Queue deletion failed");
            processBackendResponse(response, deleteHandler.cause().getMessage());
          }
        });
  }

  private void handleEntitiesQuery(RoutingContext routingContext) {
    JsonObject authInfo = (JsonObject) routingContext.data().get("authInfo");
    HttpServerRequest request = routingContext.request();
    HttpServerResponse response = routingContext.response();
    MultiMap params = getQueryParams(routingContext, response).get();
    MultiMap headerParams = request.headers();
    boolean isTimeLimitEnabled = config().getBoolean(IS_TIME_LIMIT_ENABLED);
    Future<Boolean> validationResult = validator.validate(params);
    validationResult.onComplete(
        validationHandler -> {
          if (validationHandler.succeeded()) {
            NGSILDQueryParams ngsildQuery = new NGSILDQueryParams(params);
            if (isTemporalParamsPresent(ngsildQuery)) {
              DxRuntimeException ex =
                  new DxRuntimeException(
                      BAD_REQUEST.getValue(),
                      INVALID_TEMPORAL_PARAM_URN,
                      "Temporal parameters are not allowed in entities query.");
              routingContext.fail(ex);
            }
            QueryMapper queryMapper = new QueryMapper(routingContext, isTimeLimitEnabled);
            JsonObject json = queryMapper.toJson(ngsildQuery, false);
            if (json.containsKey(ERROR)) {
              return;
            }
            CacheType cacheType = CacheType.CATALOGUE_CACHE;
            JsonObject requestJson =
                new JsonObject()
                    .put("type", cacheType)
                    .put("key", json.getJsonArray("id").getString(0));

            Future<JsonObject> filtersFuture = cacheService.get(requestJson);
            String instanceId = request.getHeader(HEADER_HOST);
            json.put(JSON_INSTANCEID, instanceId);
            JsonObject requestBody = new JsonObject();
            requestBody.put("ids", json.getJsonArray("id"));
            filtersFuture.onComplete(
                filtersHandler -> {
                  if (filtersHandler.succeeded()) {
                    JsonObject catItemJson = filtersFuture.result();
                    json.put("applicableFilters", catItemJson.getJsonArray("iudxResourceAPIs"));
                    if (json.containsKey(IUDXQUERY_OPTIONS)
                        && JSON_COUNT.equalsIgnoreCase(json.getString(IUDXQUERY_OPTIONS))) {
                      adapterResponseForCountQuery(routingContext, json, response);
                    } else {
                      adapterResponseForSearchQuery(routingContext, json, response);
                    }
                  } else if (validationHandler.failed()) {
                    LOGGER.error("Fail: Validation failed");
                    handleResponse(
                        response,
                        BAD_REQUEST,
                        INVALID_PARAM_URN,
                        validationHandler.cause().getMessage());
                  }
                });
          } else if (validationHandler.failed()) {
            LOGGER.error("Fail: Validation failed");
            handleResponse(
                response, BAD_REQUEST, INVALID_PARAM_URN, validationHandler.cause().getMessage());
          }
        });
  }

  private boolean isTemporalParamsPresent(NGSILDQueryParams ngsildQueryParams) {

    return ngsildQueryParams.getTemporalRelation().getTimeRel() != null
        || ngsildQueryParams.getTemporalRelation().getTime() != null
        || ngsildQueryParams.getTemporalRelation().getEndTime() != null;
  }

  public void handleTemporalQuery(RoutingContext routingContext) {
    HttpServerRequest request = routingContext.request();
    HttpServerResponse response = routingContext.response();
    String instanceId = request.getHeader(HEADER_HOST);
    MultiMap params = getQueryParams(routingContext, response).get();
    Future<Boolean> validationResult = validator.validate(params);
    boolean isTimeLimitEnabled = config().getBoolean(IS_TIME_LIMIT_ENABLED);
    validationResult.onComplete(
        validationHandler -> {
          if (validationHandler.succeeded()) {
            NGSILDQueryParams ngsildquery = new NGSILDQueryParams(params);
            QueryMapper queryMapper = new QueryMapper(routingContext, isTimeLimitEnabled);
            JsonObject json = queryMapper.toJson(ngsildquery, true);
            if (json.containsKey(ERROR)) {
              LOGGER.error(json.getString(ERROR));
              return;
            }

            CacheType cacheType = CacheType.CATALOGUE_CACHE;
            JsonObject requestJson =
                new JsonObject()
                    .put("type", cacheType)
                    .put("key", json.getJsonArray("id").getString(0));

            json.put(JSON_INSTANCEID, instanceId);
            LOGGER.debug("Info: IUDX temporal json query;" + json);
            JsonObject requestBody = new JsonObject();
            Future<JsonObject> filtersFuture = cacheService.get(requestJson);
            requestBody.put("ids", json.getJsonArray("id"));
            filtersFuture.onComplete(
                filtersHandler -> {
                  if (filtersHandler.succeeded()) {
                    JsonObject catItemJson = filtersFuture.result();
                    json.put("applicableFilters", catItemJson.getJsonArray("iudxResourceAPIs"));
                    if (json.containsKey(IUDXQUERY_OPTIONS)
                        && JSON_COUNT.equalsIgnoreCase(json.getString(IUDXQUERY_OPTIONS))) {
                      adapterResponseForCountQuery(routingContext, json, response);
                    } else {
                      adapterResponseForSearchQuery(routingContext, json, response);
                    }
                  } else {
                    LOGGER.error("catalogue item/group doesn't have filters.");
                  }
                });
          } else if (validationHandler.failed()) {
            LOGGER.error("Fail: Bad request;");
            handleResponse(
                response, BAD_REQUEST, INVALID_PARAM_URN, validationHandler.cause().getMessage());
          }
        });
  }

  /**
   * To get response from the databroker
   *
   * @param context Routing Context
   * @param json Count Request
   * @param response Encrypted data within the results
   */
  private void adapterResponseForCountQuery(
      RoutingContext context, JsonObject json, HttpServerResponse response) {
    JsonObject authInfo = (JsonObject) context.data().get("authInfo");
    json.put(API, authInfo.getValue(API_ENDPOINT));
    String publicKey = context.request().getHeader(HEADER_PUBLIC_KEY);
    json.put(HEADER_PUBLIC_KEY, publicKey);
    if (isAdexInstance) {
      json.put("ppbNumber", extractPPBNo(authInfo)); // this is exclusive for ADeX deployment.
    }
    LOGGER.debug("publishing into rmq :" + json);
    brokerService.executeAdapterQueryRPC(
        json,
        handler -> {
          if (handler.succeeded()) {
            LOGGER.info("Success: Count Success");
            JsonObject adapterResponse = handler.result();
            JsonObject userResponse = new JsonObject();
            userResponse.put("type", ResponseUrn.SUCCESS_URN.getUrn());
            userResponse.put("title", ResponseUrn.SUCCESS_URN.getMessage());
            userResponse.put(
                "results",
                new JsonArray()
                    .add(
                        new JsonObject()
                            .put("totalHits", adapterResponse.getInteger("totalHits"))));
            response
                .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .setStatusCode(adapterResponse.getInteger("statusCode"))
                .end(userResponse.toString());
            Future.future(fu -> updateAuditTable(context));
          } else {
            LOGGER.error("Fail: Count Fail");
            response
                .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .setStatusCode(400)
                .end(handler.cause().getMessage());
          }
        });
  }

  /**
   * To get response from the databroker
   *
   * @param context Routing Context
   * @param json Search Request
   * @param response Encrypted data within the results
   */
  private void adapterResponseForSearchQuery(
      RoutingContext context, JsonObject json, HttpServerResponse response) {
    JsonObject authInfo = (JsonObject) context.data().get("authInfo");
    json.put(API, authInfo.getValue(API_ENDPOINT));
    String publicKey = context.request().getHeader(HEADER_PUBLIC_KEY);
    json.put(HEADER_PUBLIC_KEY, publicKey);
    if (isAdexInstance) {
      json.put("ppbNumber", extractPPBNo(authInfo)); // this is exclusive for ADeX deployment.
    }
    LOGGER.debug("publishing into rmq :" + json);
    brokerService.executeAdapterQueryRPC(
        json,
        handler -> {
          if (handler.succeeded()) {
            JsonObject adapterResponse = handler.result();
            int status =
                adapterResponse.containsKey("statusCode")
                    ? adapterResponse.getInteger("statusCode")
                    : 400;
            response.putHeader(CONTENT_TYPE, APPLICATION_JSON);
            response.setStatusCode(status);
            if (status == 200) {
              LOGGER.info("Success: adapter call Success with {}", status);
              JsonObject userResponse = new JsonObject();
              userResponse.put("type", ResponseUrn.SUCCESS_URN.getUrn());
              userResponse.put("title", ResponseUrn.SUCCESS_URN.getMessage());
              userResponse.put("results", adapterResponse.getValue("results"));
              userResponse.put("totalHits", adapterResponse.getValue("totalHits"));
              if (adapterResponse.containsKey("limit")) {
                userResponse.put("limit", adapterResponse.getValue("limit"));
              }
              if (adapterResponse.containsKey("offset")) {
                userResponse.put("offset", adapterResponse.getValue("offset"));
              }
              response.end(userResponse.toString());
              context.data().put(RESPONSE_SIZE, response.bytesWritten());
              Future.future(fu -> updateAuditTable(context));
            } else {
              LOGGER.info("Success: adapter call success with {}", status);
              HttpStatusCode responseUrn = HttpStatusCode.getByValue(status);
              String adapterFailureMessage = adapterResponse.getString("details");
              JsonObject responseJson = generateResponse(responseUrn, adapterFailureMessage);
              response.end(responseJson.toString());
            }

          } else {
            LOGGER.error("Failure: Adapter Search Fail");
            response
                .putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .setStatusCode(400)
                .end(handler.cause().getMessage());
          }
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

  private void handleResponse(
      HttpServerResponse response, HttpStatusCode statusCode, ResponseUrn urn, String message) {
    response
        .putHeader(CONTENT_TYPE, APPLICATION_JSON)
        .setStatusCode(statusCode.getValue())
        .end(generateResponse(statusCode, urn, message).toString());
  }

  private void handleResponse(HttpServerResponse response, HttpStatusCode code, ResponseUrn urn) {
    handleResponse(response, code, urn, urn.getMessage());
  }

  private void processBackendResponse(HttpServerResponse response, String failureMessage) {
    LOGGER.debug("processBackendResponse : " + failureMessage);
    try {
      JsonObject json = new JsonObject(failureMessage);
      int type = json.getInteger(JSON_TYPE);
      HttpStatusCode status = HttpStatusCode.getByValue(type);
      String urnTitle = json.getString(JSON_TITLE);
      String detail = json.getString(DETAIL);
      ResponseUrn urn;
      if (urnTitle != null) {
        urn = ResponseUrn.fromCode(urnTitle);
        LOGGER.error("urn with urnTitle: " + urn.toString());
      } else {
        urn = ResponseUrn.fromCode(type + "");
        LOGGER.error("urn without urnTitle: " + urn.toString());
      }
      if (detail != null) {
        response
            .putHeader(CONTENT_TYPE, APPLICATION_JSON)
            .setStatusCode(type)
            .end(generateResponse(status, urn, detail).toString());
      }

      // return urn in body
      response
          .putHeader(CONTENT_TYPE, APPLICATION_JSON)
          .setStatusCode(type)
          .end(generateResponse(status, urn).toString());
    } catch (DecodeException ex) {
      LOGGER.error("ERROR : Expecting Json from backend service [ jsonFormattingException ]");
      handleResponse(response, BAD_REQUEST, BACKING_SERVICE_FORMAT_URN);
    }
  }

  private Future<Void> getConsumerAuditDetail(RoutingContext routingContext) {
    LOGGER.debug("Info: getConsumerAuditDetail Started. ");
    JsonObject entries = new JsonObject();
    JsonObject consumer = (JsonObject) routingContext.data().get("authInfo");
    HttpServerRequest request = routingContext.request();

    entries.put("userid", consumer.getString("userid"));
    entries.put("endPoint", consumer.getString("apiEndpoint"));
    entries.put("startTime", request.getParam("time"));
    entries.put("endTime", request.getParam("endTime"));
    entries.put("timeRelation", request.getParam("timerel"));
    entries.put("options", request.headers().get("options"));
    entries.put("resourceId", request.getParam("id"));
    entries.put("api", request.getParam("api"));
    entries.put(OFFSETPARAM, request.getParam(OFFSETPARAM));
    entries.put(LIMITPARAM, request.getParam(LIMITPARAM));
    Promise<Void> promise = Promise.promise();
    LOGGER.debug(entries);
    HttpServerResponse response = routingContext.response();
    meteringService.executeReadQuery(
        entries,
        handler -> {
          if (handler.succeeded()) {
            LOGGER.debug("Table Reading Done.");
            JsonObject jsonObject = (JsonObject) handler.result();
            String checkType = jsonObject.getString("type");
            if (checkType.equalsIgnoreCase("204")) {
              handleSuccessResponse(
                  response, ResponseType.NoContent.getCode(), handler.result().toString());
            } else {
              handleSuccessResponse(
                  response, ResponseType.Ok.getCode(), handler.result().toString());
            }
            promise.complete();
          } else {
            LOGGER.error("Fail msg " + handler.cause().getMessage());
            LOGGER.error("Table reading failed.");
            processBackendResponse(response, handler.cause().getMessage());
            promise.complete();
          }
        });
    return promise.future();
  }

  private Future<Void> getProviderAuditDetail(RoutingContext routingContext) {
    LOGGER.trace("Info: getProviderAuditDetail Started.");
    JsonObject entries = new JsonObject();
    JsonObject provider = (JsonObject) routingContext.data().get("authInfo");
    HttpServerRequest request = routingContext.request();

    entries.put("endPoint", provider.getString("apiEndpoint"));
    entries.put("userid", provider.getString("userid"));
    entries.put("iid", provider.getString("iid"));
    entries.put("startTime", request.getParam("time"));
    entries.put("endTime", request.getParam("endTime"));
    entries.put("timeRelation", request.getParam("timerel"));
    entries.put("providerID", request.getParam("providerID"));
    entries.put("consumerID", request.getParam("consumer"));
    entries.put("resourceId", request.getParam("id"));
    entries.put("api", request.getParam("api"));
    entries.put("options", request.headers().get("options"));
    entries.put(OFFSETPARAM, request.getParam(OFFSETPARAM));
    entries.put(LIMITPARAM, request.getParam(LIMITPARAM));
    Promise<Void> promise = Promise.promise();
    LOGGER.debug(entries);
    HttpServerResponse response = routingContext.response();
    meteringService.executeReadQuery(
        entries,
        handler -> {
          if (handler.succeeded()) {
            LOGGER.debug("Table Reading Done.");
            JsonObject jsonObject = (JsonObject) handler.result();
            String checkType = jsonObject.getString("type");
            if (checkType.equalsIgnoreCase("204")) {
              handleSuccessResponse(
                  response, ResponseType.NoContent.getCode(), handler.result().toString());
            } else {
              handleSuccessResponse(
                  response, ResponseType.Ok.getCode(), handler.result().toString());
            }
            promise.complete();
          } else {
            LOGGER.error("Fail msg " + handler.cause().getMessage());
            LOGGER.error("Table reading failed.");
            processBackendResponse(response, handler.cause().getMessage());
            promise.complete();
          }
        });
    return promise.future();
  }

  private void handlePostEntitiesQuery(RoutingContext routingContext) {
    LOGGER.trace("Info: handlePostEntitiesQuery method started.");
    HttpServerRequest request = routingContext.request();
    JsonObject requestJson = routingContext.body().asJsonObject();
    LOGGER.debug("Info: request Json: " + requestJson);
    requestJson.remove("logType");
    HttpServerResponse response = routingContext.response();
    MultiMap headerParams = request.headers();
    MultiMap params = getQueryParams(routingContext, response).get();
    Future<Boolean> validationResult = validator.validate(requestJson);
    boolean isTimeLimitEnabled = config().getBoolean(IS_TIME_LIMIT_ENABLED);
    validationResult.onComplete(
        validationHandler -> {
          if (validationHandler.succeeded()) {
            NGSILDQueryParams ngsildquery = new NGSILDQueryParams(requestJson);
            QueryMapper queryMapper = new QueryMapper(routingContext, isTimeLimitEnabled);
            JsonObject json = queryMapper.toJson(ngsildquery, requestJson.containsKey("temporalQ"));
            if (json.containsKey(ERROR)) {
              return;
            }
            CacheType cacheType = CacheType.CATALOGUE_CACHE;
            JsonObject cacheRequest =
                new JsonObject()
                    .put("type", cacheType)
                    .put("key", json.getJsonArray("id").getString(0));

            String instanceId = request.getHeader(HEADER_HOST);
            json.put(JSON_INSTANCEID, instanceId);
            Future<JsonObject> filtersFuture = cacheService.get(cacheRequest);
            requestJson.put("ids", json.getJsonArray("id"));
            LOGGER.debug("Info: IUDX query json: " + json);
            filtersFuture.onComplete(
                filtersHandler -> {
                  if (filtersHandler.succeeded()) {
                    JsonObject catItemJson = filtersFuture.result();
                    json.put("applicableFilters", catItemJson.getJsonArray("iudxResourceAPIs"));
                    if (json.containsKey(IUDXQUERY_OPTIONS)
                        && JSON_COUNT.equalsIgnoreCase(json.getString(IUDXQUERY_OPTIONS))) {
                      adapterResponseForCountQuery(routingContext, json, response);
                    } else {
                      adapterResponseForSearchQuery(routingContext, json, response);
                    }
                  } else {
                    LOGGER.error("catalogue item/group doesn't have filters.");
                  }
                });
          } else if (validationHandler.failed()) {
            LOGGER.error("Fail: Bad request");
            handleResponse(
                response, BAD_REQUEST, INVALID_PARAM_URN, validationHandler.cause().getMessage());
          }
        });
  }

  private void getMonthlyOverview(RoutingContext routingContext) {
    HttpServerRequest request = routingContext.request();
    LOGGER.trace("Info: getMonthlyOverview Started.");
    JsonObject authInfo = (JsonObject) routingContext.data().get("authInfo");
    authInfo.put(STARTT, request.getParam(STARTT));
    authInfo.put(ENDT, request.getParam(ENDT));
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

    meteringService.monthlyOverview(
        authInfo,
        handler -> {
          if (handler.succeeded()) {
            LOGGER.debug("Successful");
            handleSuccessResponse(response, ResponseType.Ok.getCode(), handler.result().toString());
          } else {
            LOGGER.error("Fail: Bad request");
            processBackendResponse(response, handler.cause().getMessage());
          }
        });
  }

  private void getAllSummaryHandler(RoutingContext routingContext) {
    LOGGER.trace("Info: getAllSummary Started.");
    HttpServerRequest request = routingContext.request();
    JsonObject authInfo = (JsonObject) routingContext.data().get("authInfo");
    authInfo.put(STARTT, request.getParam(STARTT));
    authInfo.put(ENDT, request.getParam(ENDT));
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

    meteringService.summaryOverview(
        authInfo,
        handler -> {
          if (handler.succeeded()) {
            JsonObject jsonObject = handler.result();
            String checkType = jsonObject.getString("type");
            if (checkType.equalsIgnoreCase("204")) {
              handleSuccessResponse(
                  response, ResponseType.NoContent.getCode(), handler.result().toString());
            } else {
              LOGGER.debug("Successful");
              handleSuccessResponse(
                  response, ResponseType.Ok.getCode(), handler.result().toString());
            }
          } else {
            LOGGER.error("Fail: Bad request");
            processBackendResponse(response, handler.cause().getMessage());
          }
        });
  }

  private void updateAuditTable(RoutingContext context) {
    Promise<Void> promise = Promise.promise();
    JsonObject authInfo = (JsonObject) context.data().get("authInfo");

    JsonObject request = new JsonObject();

    ZonedDateTime zst = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
    long time = zst.toInstant().toEpochMilli();
    String isoTime = zst.truncatedTo(ChronoUnit.SECONDS).toString();
    String resourceid = authInfo.getString(ID);
    String role = authInfo.getString(ROLE);
    String drl = authInfo.getString(DRL);
    if (role.equalsIgnoreCase("delegate") && drl != null) {
      request.put(DELEGATOR_ID, authInfo.getString(DID));
    } else {
      request.put(DELEGATOR_ID, authInfo.getString(USER_ID));
    }

    CacheType cacheType = CacheType.CATALOGUE_CACHE;
    JsonObject requestJson = new JsonObject().put("type", cacheType).put("key", resourceid);

    getCacheItem(requestJson)
        .onComplete(
            cacheItemHandler -> {
              if (cacheItemHandler.succeeded()) {
                JsonObject cacheJson = cacheItemHandler.result();

                String providerId = cacheJson.getString("provider");
                String type = cacheJson.getString(TYPE_KEY);
                String resourceGroup = cacheJson.getString(RESOURCE_GROUP);

                request.put(RESOURCE_GROUP, resourceGroup);
                request.put(TYPE_KEY, type.toUpperCase());
                request.put(EPOCH_TIME, time);
                request.put(ISO_TIME, isoTime);
                request.put(USER_ID, authInfo.getValue(USER_ID));
                request.put(ID, authInfo.getValue(ID));
                request.put(API, authInfo.getValue(API_ENDPOINT));
                request.put(RESPONSE_SIZE, context.data().get(RESPONSE_SIZE));
                request.put(PROVIDER_ID, providerId);

                meteringService.publishMeteringData(
                    request,
                    handler -> {
                      if (handler.succeeded()) {
                        LOGGER.info("message published in RMQ.");
                        promise.complete();
                      } else {
                        LOGGER.error("failed to publish message in RMQ.");
                        promise.fail(handler.cause().getMessage());
                      }
                    });
              } else {
                LOGGER.error("info failed [auditing]: " + cacheItemHandler.cause().getMessage());
                promise.fail("info failed: [] " + cacheItemHandler.cause().getMessage());
              }
            });

    promise.future();
  }

  private void printDeployedEndpoints(Router router) {
    for (Route route : router.getRoutes()) {
      if (route.getPath() != null) {
        LOGGER.info("API Endpoints deployed :" + route.methods() + ":" + route.getPath());
      }
    }
  }

  public String extractPPBNo(JsonObject authInfo) {
    LOGGER.debug("info :extractPPBNo()");
    if (authInfo == null) {
      return "";
    }
    JsonObject cons = authInfo.getJsonObject(JSON_CONS);
    if (cons == null) {
      return "";
    }
    String ppbno = cons.getString("ppbNumber");
    if (ppbno == null) {
      return "";
    }
    return ppbno;
  }

  private Future<JsonObject> getCacheItem(JsonObject cacheJson) {
    Promise<JsonObject> promise = Promise.promise();

    cacheService
        .get(cacheJson)
        .onSuccess(
            cacheServiceResult -> {
              Set<String> type =
                  new HashSet<String>(cacheServiceResult.getJsonArray("type").getList());
              Set<String> itemTypeSet =
                  type.stream().map(e -> e.split(":")[1]).collect(Collectors.toSet());
              itemTypeSet.retainAll(ITEM_TYPES);
              String resourceGroup;
              if (!itemTypeSet.contains("Resource")) {
                resourceGroup = cacheServiceResult.getString("id");
              } else {
                resourceGroup = cacheServiceResult.getString("resourceGroup");
              }
              cacheServiceResult.put("type", itemTypeSet.iterator().next());
              cacheServiceResult.put("resourceGroup", resourceGroup);
              promise.complete(cacheServiceResult);
            })
        .onFailure(
            fail -> {
              LOGGER.debug("Failed: " + fail.getMessage());
              promise.fail(fail.getMessage());
            });

    return promise.future();
  }
}
