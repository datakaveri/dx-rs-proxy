package iudx.rs.proxy.apiserver.handlers;

import static iudx.rs.proxy.apiserver.util.ApiServerConstants.*;
import static iudx.rs.proxy.authenticator.Constants.*;
import static iudx.rs.proxy.common.Constants.AUTH_SERVICE_ADDRESS;
import static iudx.rs.proxy.common.Constants.CONSEENTLOG_SERVICE_ADDRESS;
import static iudx.rs.proxy.common.HttpStatusCode.BAD_REQUEST;
import static iudx.rs.proxy.common.ResponseUrn.*;

import io.netty.handler.codec.http.HttpConstants;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.vertx.core.*;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.RoutingContext;
import iudx.rs.proxy.authenticator.AuthenticationService;
import iudx.rs.proxy.authenticator.model.JwtData;
import iudx.rs.proxy.common.Api;
import iudx.rs.proxy.common.HttpStatusCode;
import iudx.rs.proxy.common.ResponseUrn;
import iudx.rs.proxy.optional.consentlogs.ConsentLoggingService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** IUDX Authentication handler to authenticate token passed in HEADER */
public class AuthHandler implements Handler<RoutingContext> {

  private static final Logger LOGGER = LogManager.getLogger(AuthHandler.class);

  static AuthenticationService authenticator;
  static Api api;
  static boolean isAdexInstance;
  static ConsentLoggingService consentLoggingService;
  private final String AUTHINFO = "authInfo";
  private HttpServerRequest request;
  private RoutingContext context;

  public static AuthHandler create(Vertx vertx, Api apiEndpoints, boolean isAdex) {
    authenticator = AuthenticationService.createProxy(vertx, AUTH_SERVICE_ADDRESS);
    api = apiEndpoints;
    isAdexInstance = isAdex;
    consentLoggingService = ConsentLoggingService.createProxy(vertx, CONSEENTLOG_SERVICE_ADDRESS);
    return new AuthHandler();
  }

  @Override
  public void handle(RoutingContext context) {
    LOGGER.debug("info handle() started");
    request = context.request();
    this.context = context;

    RequestBody requestBody = context.body();
    JsonObject requestJson = null;
    if (request != null && requestBody.asJsonObject() != null) {
      requestJson = requestBody.asJsonObject().copy();
    }
    if (requestJson == null) {
      requestJson = new JsonObject();
    }

    String token = request.headers().get(HEADER_TOKEN);
    final String path = getNormalizedPath(request.path());
    final String method = context.request().method().toString();

    if (token == null) {
      token = "public";
    }

    JsonObject authInfo =
        new JsonObject().put(API_ENDPOINT, path).put(HEADER_TOKEN, token).put(API_METHOD, method);

    LOGGER.debug("Info :" + context.request().path());
    String id = getId(context, ID);
    LOGGER.debug("ID : {}", id);

    if (isAdexInstance) {
      String ppbNumber = getId(context, NGSLILDQUERY_Q);
      LOGGER.debug("ppbNumber :{}", ppbNumber);
      if (ppbNumber != null) {
        authInfo.put(PPB_NUMBER, ppbNumber);
      }
    }

    authInfo.put(ID, id);
    JsonArray ids = new JsonArray();
    String[] idArray = id == null ? new String[0] : id.split(",");
    for (String i : idArray) {
      ids.add(i);
    }
    JwtData jwtData = (JwtData) context.data().get("jwtData");

    LOGGER.debug("authInfo: " + authInfo);
    requestJson.put(IDS, ids);
    authenticator.tokenIntrospect(
        requestJson,
        authInfo,
        jwtData,
        authHandler -> {
          if (authHandler.succeeded()) {
            authInfo.put(IID, authHandler.result().getValue(IID));
            authInfo.put(USER_ID, authHandler.result().getValue(USER_ID));
            authInfo.put(JSON_APD, authHandler.result().getValue(JSON_APD));
            authInfo.put(JSON_CONS, authHandler.result().getValue(JSON_CONS));
            authInfo.put(ROLE, authHandler.result().getValue(ROLE));
            authInfo.put(DID, authHandler.result().getValue(DID));
            authInfo.put(DRL, authHandler.result().getValue(DRL));
            authInfo.put(ACCESSIBLE_ATTRS, authHandler.result().getValue(ACCESSIBLE_ATTRS));
            context.data().put(AUTHINFO, authInfo);
          } else {
            processAuthFailure(context, authHandler.cause().getMessage());
            if (isAdexInstance) {
              Future.future(f -> logConsentResponse(jwtData));
            }
            return;
          }
          context.next();
        });
  }

  private void processAuthFailure(RoutingContext ctx, String result) {
    LOGGER.info("Failure : {}", result);
    if (result.contains("Not Found")) {
      LOGGER.error("Error : Item Not Found");
      HttpStatusCode statusCode = HttpStatusCode.getByValue(404);
      ctx.response()
          .putHeader(CONTENT_TYPE, APPLICATION_JSON)
          .setStatusCode(statusCode.getValue())
          .end(generateResponse(RESOURCE_NOT_FOUND_URN, statusCode).toString());
    } else {
      LOGGER.error("Error : Authentication Failure");
      HttpStatusCode statusCode = HttpStatusCode.getByValue(401);
      ctx.response()
          .putHeader(CONTENT_TYPE, APPLICATION_JSON)
          .setStatusCode(statusCode.getValue())
          .end(generateResponse(INVALID_TOKEN_URN, statusCode).toString());
    }
  }

  private JsonObject generateResponse(ResponseUrn urn, HttpStatusCode statusCode) {
    return new JsonObject()
        .put(JSON_TYPE, urn.getUrn())
        .put(JSON_TITLE, statusCode.getDescription())
        .put(JSON_DETAIL, statusCode.getDescription());
  }

  /**
   * extract id from request (path/query or body )
   *
   * @param context current routing context
   * @return id extraced fro path if present
   */
  private String getId(RoutingContext context, String option) {
    String paramId = getId4rmRequest(option);
    String bodyId = getId4rmBody(context, option);
    String id;
    if (paramId != null && !paramId.isBlank()) {
      id = paramId;
    } else {
      id = bodyId;
    }
    return id;
  }

  private String getId4rmRequest(String option) {
    if (option.equalsIgnoreCase(NGSLILDQUERY_Q)) {
      String ppbnoValue = "";
      try {
        MultiMap params = getQueryParams(context, context.response()).get();
        String q = params.get(NGSLILDQUERY_Q);
        ppbnoValue = extractPpbno(q);

      } catch (IllegalArgumentException e) {
        LOGGER.error("Error: " + e.getMessage());
      }
      return ppbnoValue;
    }
    return request.getParam(option);
  }

  private String getId4rmBody(RoutingContext context, String option) {
    JsonObject body = context.body().asJsonObject();
    if (option.equalsIgnoreCase(NGSLILDQUERY_Q) && body != null) {
      String ppbnoValue = "";
      try {
        LOGGER.debug(body.getString(NGSLILDQUERY_Q));
        ppbnoValue = extractPpbno(body.getString(NGSLILDQUERY_Q));

      } catch (IllegalArgumentException e) {
        LOGGER.error("Error: " + e.getMessage());
      }
      return ppbnoValue;
    }
    String id = null;
    if (body != null) {
      JsonArray array = body.getJsonArray(JSON_ENTITIES);
      if (array != null) {
        JsonObject json = array.getJsonObject(0);
        if (json != null) {
          id = json.getString(ID);
        }
      }
    }
    return id;
  }

  private String extractPpbno(String q) {
    LOGGER.info("q: " + q);
    try {
      int PpbIndex = q.indexOf("Ppbno==");
      int firstSemiIndex = q.indexOf(';', PpbIndex);
      firstSemiIndex = firstSemiIndex == -1 ? q.length() : firstSemiIndex;
      String ppbno = q.substring(PpbIndex + 7, firstSemiIndex);
      return ppbno;
    } catch (Exception e) {
      return null;
    }
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
        // LOGGER.debug("Info: param :" + entry.getKey() + " value : " + entry.getValue());
        queryParams.add(entry.getKey(), entry.getValue());
      }
    } catch (IllegalArgumentException ex) {
      HttpStatusCode statusCode = HttpStatusCode.getByValue(BAD_REQUEST.getValue());
      response
          .putHeader(CONTENT_TYPE, APPLICATION_JSON)
          .setStatusCode(BAD_REQUEST.getValue())
          .end(generateResponse(INVALID_PARAM_URN, statusCode).toString());
    }
    return Optional.of(queryParams);
  }

  /**
   * get normalized path without id as path param.
   *
   * @param url complete path from request
   * @return path without id.
   */
  private String getNormalizedPath(String url) {
    LOGGER.debug("URL : " + url);
    String path = null;
    if (url.matches(getpathRegex(api.getTemporalEndpoint()))) {
      path = api.getTemporalEndpoint();
    } else if (url.matches(getpathRegex(api.getEntitiesEndpoint()))) {
      path = api.getEntitiesEndpoint();
    } else if (url.matches(api.getConsumerAuditEndpoint())) {
      path = api.getConsumerAuditEndpoint();
    } else if (url.matches(api.getProviderAuditEndpoint())) {
      path = api.getProviderAuditEndpoint();
    } else if (url.matches(api.getPostEntitiesEndpoint())) {
      path = api.getPostEntitiesEndpoint();
    } else if (url.matches(api.getPostTemporalEndpoint())) {
      path = api.getPostTemporalEndpoint();
    } else if (url.matches(api.getAsyncSearchEndPoint())) {
      path = api.getAsyncSearchEndPoint();
    } else if (url.matches(api.getAsyncStatusEndpoint())) {
      path = api.getAsyncStatusEndpoint();
    } else if (url.matches(api.getSummaryEndPoint())) {
      path = api.getSummaryEndPoint();
    } else if (url.matches(api.getOverviewEndPoint())) {
      path = api.getOverviewEndPoint();
    } else if (url.matches(api.getConnectorsPath())) {
      path = api.getConnectorsPath();
    } else if (url.matches(getpathRegex(api.getManagementBasePath()))) {
      path = api.getManagementBasePath();
    }

    return path;
  }

  private String getpathRegex(String path) {
    return path + "(.*)";
  }

  private Future<Void> logConsentResponse(JwtData jwtData) {
    LOGGER.info("logging consent log for response");
    Promise<Void> promise = Promise.promise();
    if (isAdexInstance) {

      JsonObject logRequest = new JsonObject();
      logRequest.put("logType", "DATA_DENIED");

      Future<JsonObject> consentAuditLog = consentLoggingService.log(logRequest, jwtData);
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
}
