package iudx.rs.proxy.apiserver;

import static iudx.rs.proxy.apiserver.response.ResponseUtil.generateResponse;
import static iudx.rs.proxy.apiserver.util.ApiServerConstants.*;
import static iudx.rs.proxy.apiserver.util.ApiServerConstants.HEADER_PUBLIC_KEY;
import static iudx.rs.proxy.apiserver.util.Util.errorResponse;
import static iudx.rs.proxy.common.Constants.DATABROKER_SERVICE_ADDRESS;
import static iudx.rs.proxy.common.Constants.DB_SERVICE_ADDRESS;
import static iudx.rs.proxy.common.Constants.METERING_SERVICE_ADDRESS;
import static iudx.rs.proxy.common.HttpStatusCode.BAD_REQUEST;
import static iudx.rs.proxy.common.ResponseUrn.BACKING_SERVICE_FORMAT_URN;
import static iudx.rs.proxy.common.ResponseUrn.INVALID_PARAM_URN;
import static iudx.rs.proxy.common.ResponseUrn.INVALID_TEMPORAL_PARAM_URN;

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
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.JksOptions;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import iudx.rs.proxy.apiserver.exceptions.DxRuntimeException;
import iudx.rs.proxy.apiserver.handlers.AuthHandler;
import iudx.rs.proxy.apiserver.handlers.FailureHandler;
import iudx.rs.proxy.apiserver.handlers.ValidationHandler;
import iudx.rs.proxy.apiserver.query.NGSILDQueryParams;
import iudx.rs.proxy.apiserver.query.QueryMapper;
import iudx.rs.proxy.apiserver.response.ResponseType;
import iudx.rs.proxy.apiserver.response.ResponseUtil;
import iudx.rs.proxy.apiserver.service.CatalogueService;
import iudx.rs.proxy.apiserver.util.RequestType;
import iudx.rs.proxy.common.Api;
import iudx.rs.proxy.common.HttpStatusCode;
import iudx.rs.proxy.common.ResponseUrn;
import iudx.rs.proxy.database.DatabaseService;
import iudx.rs.proxy.databroker.DatabrokerService;
import iudx.rs.proxy.metering.MeteringService;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ApiServerVerticle extends AbstractVerticle {

  private static final Logger LOGGER = LogManager.getLogger(ApiServerVerticle.class);

  private int port;
  private HttpServer server;
  private Router router;
  private String keystore, keystorePassword;
 // private boolean isSSL, isProduction;
  private ParamsValidator validator;
  private CatalogueService catalogueService;
  private DatabaseService databaseService;
  private MeteringService meteringService;
  private DatabrokerService brokerService;
  
  private String dxApiBasePath;

  @Override
  public void start() throws Exception {
    catalogueService = new CatalogueService(vertx, config());
    databaseService = DatabaseService.createProxy(vertx, DB_SERVICE_ADDRESS);
    meteringService = MeteringService.createProxy(vertx, METERING_SERVICE_ADDRESS);
    brokerService = DatabrokerService.createProxy(vertx, DATABROKER_SERVICE_ADDRESS);
    validator = new ParamsValidator(catalogueService);
    
    dxApiBasePath=config().getString("dxApiBasePath");
    Api apis=Api.getInstance(dxApiBasePath);
    
    router = Router.router(vertx);
    attachCORSHandlers(router);
    attachDefaultResponses(router);
    router.route().handler(BodyHandler.create());
    
    FailureHandler validationsFailureHandler = new FailureHandler();

    ValidationHandler entityValidationHandler = new ValidationHandler(vertx, RequestType.ENTITY);
    router
          .get(apis.getEntitiesEndpoint())
          .handler(entityValidationHandler)
          .handler(AuthHandler.create(vertx, apis))
          .handler(this::handleEntitiesQuery)
          .failureHandler(validationsFailureHandler);

    ValidationHandler temporalValidationHandler =
        new ValidationHandler(vertx, RequestType.TEMPORAL);
    router
          .get(apis.getTemporalEndpoint())
          .handler(temporalValidationHandler)
          .handler(AuthHandler.create(vertx, apis))
          .handler(this::handleTemporalQuery)
          .failureHandler(validationsFailureHandler);

    router
          .get(apis.getConsumerAuditEndpoint())
          .handler(AuthHandler.create(vertx, apis))
          .handler(this::getConsumerAuditDetail);
    
    router
          .get(apis.getProviderAuditEndpoint())
          .handler(AuthHandler.create(vertx, apis))
          .handler(this::getProviderAuditDetail);

    // Post Queries
    ValidationHandler postEntitiesValidationHandler =
            new ValidationHandler(vertx, RequestType.POST_ENTITIES);
    router
            .post(apis.getPostEntitiesEndpoint())
            .consumes(APPLICATION_JSON)
            .handler(postEntitiesValidationHandler)
            .handler(AuthHandler.create(vertx,apis))
            .handler(this::handlePostEntitiesQuery)
            .failureHandler(validationsFailureHandler);

    ValidationHandler postTemporalValidationHandler =
            new ValidationHandler(vertx, RequestType.POST_TEMPORAL);
    router
            .post(apis.getPostTemporalEndpoint())
            .consumes(APPLICATION_JSON)
            .handler(postTemporalValidationHandler)
            .handler(AuthHandler.create(vertx,apis))
            .handler(this::handlePostEntitiesQuery)
            .failureHandler(validationsFailureHandler);

    /** Documentation routes */
    /* Static Resource Handler */
    /* Get openapiv3 spec */
    router.get(ROUTE_STATIC_SPEC).produces(FORMAT_JSON).handler(routingContext -> {
      HttpServerResponse response = routingContext.response();
      response.sendFile("docs/openapi.yaml");
    });
    /* Get redoc */
    router.get(ROUTE_DOC).produces(FORMAT).handler(routingContext -> {
      HttpServerResponse response = routingContext.response();
      response.sendFile("docs/apidoc.html");
    });

    HttpServerOptions serverOptions = new HttpServerOptions();
    int port = config().getInteger("port");
    setServerOptions(serverOptions);
    serverOptions.setCompressionSupported(true).setCompressionLevel(5);
    server = vertx.createHttpServer(serverOptions);
    server.requestHandler(router).listen(port);
    LOGGER.debug("port deployed : "+server.actualPort());
    printDeployedEndpoints(router);
  }

  private void setServerOptions(HttpServerOptions serverOptions) {
    boolean isSSL = config().getBoolean("ssl");
    boolean isProduction = config().getBoolean("production");
    if (isSSL) {
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
          .handler(CorsHandler
              .create("*")
                .allowedHeaders(ALLOWED_HEADERS)
                .allowedMethods(ALLOWED_METHODS))
          .handler(responseHeaderHandler -> {
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
    Stream.of(statusCodes).forEach(code -> {
      router.errorHandler(code.getValue(), errorHandler -> {
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

  private void handleEntitiesQuery(RoutingContext routingContext) {
    JsonObject authInfo = (JsonObject) routingContext.data().get("authInfo");
    HttpServerRequest request = routingContext.request();
    HttpServerResponse response = routingContext.response();
    MultiMap params = getQueryParams(routingContext, response).get();
    MultiMap headerParams = request.headers();
    Future<Boolean> validationResult = validator.validate(params);
    validationResult.onComplete(validationHandler -> {
      if (validationHandler.succeeded()) {
        NGSILDQueryParams ngsildQuery = new NGSILDQueryParams(params);
        if (isTemporalParamsPresent(ngsildQuery)) {
          DxRuntimeException ex =
              new DxRuntimeException(BAD_REQUEST.getValue(), INVALID_TEMPORAL_PARAM_URN,
                  "Temporal parameters are not allowed in entities query.");
          routingContext.fail(ex);
        }
        QueryMapper queryMapper = new QueryMapper();
        JsonObject json = queryMapper.toJson(ngsildQuery, false);
        Future<List<String>> filtersFuture =
            catalogueService.getApplicableFilters(json.getJsonArray("id").getString(0));
        String instanceID = request.getHeader(HEADER_HOST);
        json.put(JSON_INSTANCEID, instanceID);
        JsonObject requestBody = new JsonObject();
        requestBody.put("ids", json.getJsonArray("id"));
        filtersFuture.onComplete(filtersHandler -> {
          if (filtersHandler.succeeded()) {
            json.put("applicableFilters", filtersHandler.result());
            if (json.containsKey(IUDXQUERY_OPTIONS) &&
                JSON_COUNT.equalsIgnoreCase(json.getString(IUDXQUERY_OPTIONS))) {
              adapterResponseForCountQuery(routingContext, json, response);
            } else {
              adapterResponseForSearchQuery(routingContext, json, response);
            }
          } else if (validationHandler.failed()) {
            LOGGER.error("Fail: Validation failed");
            handleResponse(response, BAD_REQUEST, INVALID_PARAM_URN,
                validationHandler.cause().getMessage());
          }
        });
      } else if (validationHandler.failed()) {
        LOGGER.error("Fail: Validation failed");
        handleResponse(response, BAD_REQUEST, INVALID_PARAM_URN,
            validationHandler.cause().getMessage());
      }
    });
  }

  private boolean isTemporalParamsPresent(NGSILDQueryParams ngsildQueryParams) {

    return ngsildQueryParams.getTemporalRelation().getTimeRel() != null ||
        ngsildQueryParams.getTemporalRelation().getTime() != null ||
        ngsildQueryParams.getTemporalRelation().getEndTime() != null;
  }

  public void handleTemporalQuery(RoutingContext routingContext) {
    HttpServerRequest request = routingContext.request();
    HttpServerResponse response = routingContext.response();
    String instanceID = request.getHeader(HEADER_HOST);
    MultiMap params = getQueryParams(routingContext, response).get();
    Future<Boolean> validationResult = validator.validate(params);

    validationResult.onComplete(validationHandler -> {
      if (validationHandler.succeeded()) {
        NGSILDQueryParams ngsildquery = new NGSILDQueryParams(params);
        QueryMapper queryMapper = new QueryMapper();
        JsonObject json = queryMapper.toJson(ngsildquery, true);
        Future<List<String>> filtersFuture =
            catalogueService.getApplicableFilters(json.getJsonArray("id").getString(0));
        json.put(JSON_INSTANCEID, instanceID);
        LOGGER.debug("Info: IUDX temporal json query;" + json);
        JsonObject requestBody = new JsonObject();
        requestBody.put("ids", json.getJsonArray("id"));
        filtersFuture.onComplete(filtersHandler -> {
          if (filtersHandler.succeeded()) {
            json.put("applicableFilters", filtersHandler.result());
            if (json.containsKey(IUDXQUERY_OPTIONS) &&
                JSON_COUNT.equalsIgnoreCase(json.getString(IUDXQUERY_OPTIONS))) {
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
        handleResponse(response, BAD_REQUEST, INVALID_PARAM_URN,
            validationHandler.cause().getMessage());
      }
    });
  }


  /**
   * To get response from the databroker
   * @param context Routing Context
   * @param json Count Request
   * @param response Encrypted data within the results
   */
  private void adapterResponseForCountQuery(RoutingContext context, JsonObject json,
                                            HttpServerResponse response) {
    JsonObject authInfo = (JsonObject) context.data().get("authInfo");
    json.put(USER_ID, authInfo.getValue(USER_ID));

    String publicKey = context.request().getHeader(HEADER_PUBLIC_KEY);
    json.put(HEADER_PUBLIC_KEY, publicKey);
    brokerService.executeAdapterQueryRPC(json, handler -> {
      if (handler.succeeded()) {
        LOGGER.info("Success: Count Success");
        JsonObject adapterResponse=handler.result();
        response.putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .setStatusCode(adapterResponse.getInteger("status"))
                .end(adapterResponse.toString());
      } else {
        LOGGER.error("Fail: Count Fail");
        response.putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .setStatusCode(400)
                .end(handler.cause().getMessage());
      }
    });
  }

  /**
   * To get response from the databroker
   * @param context Routing Context
   * @param json Search Request
   * @param response Encrypted data within the results
   */
  private void adapterResponseForSearchQuery(RoutingContext context, JsonObject json,
                                             HttpServerResponse response) {
    JsonObject authInfo = (JsonObject) context.data().get("authInfo");
    json.put(USER_ID, authInfo.getValue(USER_ID));
    String publicKey = context.request().getHeader(HEADER_PUBLIC_KEY);
    json.put(HEADER_PUBLIC_KEY, publicKey);
    brokerService.executeAdapterQueryRPC(json, handler -> {
      if (handler.succeeded()) {
        JsonObject adapterResponse=handler.result();
        int status=adapterResponse.containsKey("status")?adapterResponse.getInteger("status"):400;
        response.putHeader(CONTENT_TYPE, APPLICATION_JSON);
        response.setStatusCode(status);
        if(status==200) {
          LOGGER.info("Success: adapter call Success with {}",status);
          adapterResponse.put("type", ResponseUrn.SUCCESS_URN.getUrn());
          adapterResponse.put("title", ResponseUrn.SUCCESS_URN.getMessage());
          adapterResponse.remove("status");
          response.end(adapterResponse.toString());
          context.data().put(RESPONSE_SIZE, response.bytesWritten());
          Future.future(fu -> updateAuditTable(context));
        }else {
          LOGGER.info("Success: adapter call success with {}",status);
          HttpStatusCode responseUrn=HttpStatusCode.getByValue(status);
          String adapterFailureMessage=adapterResponse.getString("details");
          JsonObject responseJson=ResponseUtil.generateResponse(responseUrn,adapterFailureMessage);
          response.end(responseJson.toString());
        }
                
      } else {
        LOGGER.error("Fail: Search Fail");
        response.putHeader(CONTENT_TYPE, APPLICATION_JSON)
                .setStatusCode(400)
                .end(handler.cause().getMessage());
      }
    });

  }
  private Optional<MultiMap> getQueryParams(RoutingContext routingContext,
      HttpServerResponse response) {
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
      response.putHeader(CONTENT_TYPE, APPLICATION_JSON)
          .setStatusCode(HttpStatusCode.BAD_REQUEST.getValue())
          .end(generateResponse(HttpStatusCode.BAD_REQUEST, INVALID_PARAM_URN).toString());
    }
    return Optional.of(queryParams);
  }

  private void handleSuccessResponse(HttpServerResponse response, int statusCode, String result) {
    response.putHeader(CONTENT_TYPE, APPLICATION_JSON).setStatusCode(statusCode).end(result);
  }

  private void handleResponse(HttpServerResponse response, HttpStatusCode statusCode,
      ResponseUrn urn, String message) {
    response.putHeader(CONTENT_TYPE, APPLICATION_JSON).setStatusCode(statusCode.getValue())
        .end(generateResponse(statusCode, urn, message).toString());
  }

  private void handleResponse(HttpServerResponse response, HttpStatusCode code, ResponseUrn urn) {
    handleResponse(response, code, urn, code.getDescription());
  }

  private void processBackendResponse(HttpServerResponse response, String failureMessage) {
    LOGGER.debug("Info : " + failureMessage);
    try {
      JsonObject json = new JsonObject(failureMessage);
      int type = json.getInteger(JSON_TYPE);
      HttpStatusCode status = HttpStatusCode.getByValue(type);
      String urnTitle = json.getString(JSON_TITLE);
      ResponseUrn urn;
      if (urnTitle != null) {
        urn = ResponseUrn.fromCode(urnTitle);
      } else {
        urn = ResponseUrn.fromCode(type + "");
      }
      // return urn in body
      response
              .putHeader(CONTENT_TYPE, APPLICATION_JSON)
              .setStatusCode(type)
              .end(generateResponse(status, urn).toString());
    } catch (DecodeException ex) {
      LOGGER.error("ERROR : Expecting Json from backend service [ jsonFormattingException ]");
      handleResponse(response, HttpStatusCode.BAD_REQUEST, BACKING_SERVICE_FORMAT_URN);
    }
  }

  private Future<Void> getConsumerAuditDetail(RoutingContext routingContext) {
    LOGGER.debug("Info: getConsumerAuditDetail Started. ");
    Promise<Void> promise = Promise.promise();
    JsonObject entries = new JsonObject();
    JsonObject consumer = (JsonObject) routingContext.data().get("authInfo");
    HttpServerRequest request = routingContext.request();
    HttpServerResponse response = routingContext.response();

    entries.put("userid", consumer.getString("userid"));
    entries.put("endPoint", consumer.getString("apiEndpoint"));
    entries.put("startTime", request.getParam("time"));
    entries.put("endTime", request.getParam("endTime"));
    entries.put("timeRelation", request.getParam("timerel"));
    entries.put("options", request.headers().get("options"));
    entries.put("resourceId", request.getParam("id"));
    entries.put("api", request.getParam("api"));


    LOGGER.debug(entries);
    meteringService.executeReadQuery(entries, handler -> {
      if (handler.succeeded()) {
        LOGGER.debug("Table Reading Done.");
        JsonObject jsonObject = (JsonObject) handler.result();
        String checkType = jsonObject.getString("type");
        if (checkType.equalsIgnoreCase("204")) {
          handleSuccessResponse(response, ResponseType.NoContent.getCode(),
              handler.result().toString());
        } else {
          handleSuccessResponse(response, ResponseType.Ok.getCode(), handler.result().toString());
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
    Promise<Void> promise = Promise.promise();
    JsonObject entries = new JsonObject();
    JsonObject provider = (JsonObject) routingContext.data().get("authInfo");
    HttpServerRequest request = routingContext.request();
    HttpServerResponse response = routingContext.response();

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


    LOGGER.debug(entries);
    meteringService.executeReadQuery(entries, handler -> {
      if (handler.succeeded()) {
        LOGGER.debug("Table Reading Done.");
        JsonObject jsonObject = (JsonObject) handler.result();
        String checkType = jsonObject.getString("type");
        if (checkType.equalsIgnoreCase("204")) {
          handleSuccessResponse(response, ResponseType.NoContent.getCode(),
              handler.result().toString());
        } else {
          handleSuccessResponse(response, ResponseType.Ok.getCode(), handler.result().toString());
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

  private void handlePostEntitiesQuery(RoutingContext routingContext){
    LOGGER.trace("Info: handlePostEntitiesQuery method started.");
    HttpServerRequest request = routingContext.request();
    JsonObject requestJson = routingContext.body().asJsonObject();
    LOGGER.debug("Info: request Json: " + requestJson);
    HttpServerResponse response = routingContext.response();
    MultiMap headerParams = request.headers();
    MultiMap params = getQueryParams(routingContext, response).get();
    Future<Boolean> validationResult = validator.validate(requestJson);
    validationResult.onComplete(validationHandler -> {
      if (validationHandler.succeeded()) {
        NGSILDQueryParams ngsildquery = new NGSILDQueryParams(requestJson);
        QueryMapper queryMapper = new QueryMapper();
        JsonObject json = queryMapper.toJson(ngsildquery, requestJson.containsKey("temporalQ"));
        LOGGER.debug("json value : "+json);
        Future<List<String>> filtersFuture =
                catalogueService.getApplicableFilters(json.getJsonArray("id").getString(0));
        String instanceID = request.getHeader(HEADER_HOST);
        json.put(JSON_INSTANCEID, instanceID);
        requestJson.put("ids", json.getJsonArray("id"));
        LOGGER.debug("Info: IUDX query json: " + json);
        filtersFuture.onComplete(filtersHandler -> {
          if (filtersHandler.succeeded()) {
            json.put("applicableFilters", filtersHandler.result());
            if (json.containsKey(IUDXQUERY_OPTIONS) &&
                    JSON_COUNT.equalsIgnoreCase(json.getString(IUDXQUERY_OPTIONS))) {
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
        handleResponse(response, BAD_REQUEST, INVALID_PARAM_URN,
                validationHandler.cause().getMessage());
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

    request.put(EPOCH_TIME,time);
    request.put(ISO_TIME,isoTime);
    request.put(USER_ID, authInfo.getValue(USER_ID));
    request.put(ID, authInfo.getValue(ID));
    request.put(API, authInfo.getValue(API_ENDPOINT));
    request.put(RESPONSE_SIZE, context.data().get(RESPONSE_SIZE));

    meteringService.insertMeteringValuesInRMQ(
            request,
            handler -> {
              if (handler.succeeded()) {
                LOGGER.info("message published in RMQ.");
                promise.complete();
              } else {
                LOGGER.error("failed to publish message in RMQ.");
                promise.complete();
              }
            });

    promise.future();
  }
  
  private void printDeployedEndpoints(Router router) {
    for(Route route:router.getRoutes()) {
      if(route.getPath()!=null) {
        LOGGER.info("API Endpoints deployed :"+ route.methods() +":"+ route.getPath());
      }
    }
  }
}
