package iudx.rs.proxy.authenticator;

import static iudx.rs.proxy.apiserver.util.ApiServerConstants.PII;
import static iudx.rs.proxy.apiserver.util.ApiServerConstants.PPB_NUMBER;
import static iudx.rs.proxy.authenticator.Constants.*;

import io.vertx.core.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.authentication.TokenCredentials;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import iudx.rs.proxy.authenticator.authorization.*;
import iudx.rs.proxy.authenticator.model.JwtData;
import iudx.rs.proxy.cache.CacheService;
import iudx.rs.proxy.cache.cacheImpl.CacheType;
import iudx.rs.proxy.common.Api;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class JwtAuthenticationServiceImpl implements AuthenticationService {

  private static final Logger LOGGER = LogManager.getLogger(JwtAuthenticationServiceImpl.class);
  static WebClient catWebClient;
  final JWTAuth jwtAuth;
  final String audience;
  final CacheService cache;
  final Api apis;
  String relationshipCatPath;
  String host;
  int port;
  String catBasePath;

  JwtAuthenticationServiceImpl(
      Vertx vertx,
      final JWTAuth jwtAuth,
      final JsonObject config,
      final CacheService cacheService,
      final Api apis) {
    this.jwtAuth = jwtAuth;
    this.audience = config.getString("audience");
    this.apis = apis;
    this.cache = cacheService;
    this.host = config.getString("catServerHost");
    this.port = config.getInteger("catServerPort");
    this.catBasePath = config.getString("dxCatalogueBasePath");
    WebClientOptions options = new WebClientOptions();
    options.setTrustAll(true).setVerifyHost(false).setSsl(true);
    catWebClient = WebClient.create(vertx, options);
  }

  @Override
  public AuthenticationService tokenIntrospect(
      JsonObject request,
      JsonObject authenticationInfo,
      JwtData jwtData,
      Handler<AsyncResult<JsonObject>> handler) {
    LOGGER.info("tokenIntrospect() started");
    String id = authenticationInfo.getString("id");
    String ppbNumber = authenticationInfo.getString(PPB_NUMBER, "");

    ResultContainer result = new ResultContainer();
    result.jwtData = jwtData;
    String endPoint = authenticationInfo.getString("apiEndpoint");
    boolean skipResourceIdCheck =
        endPoint.equalsIgnoreCase(apis.getAsyncStatusEndpoint())
            || endPoint.equalsIgnoreCase(apis.getConsumerAuditEndpoint())
            || endPoint.equalsIgnoreCase(apis.getProviderAuditEndpoint())
            || endPoint.equalsIgnoreCase(apis.getOverviewEndPoint())
            || endPoint.equalsIgnoreCase(apis.getSummaryEndPoint())
            || endPoint.equalsIgnoreCase(apis.getManagementBasePath());

    Future<Boolean> audienceFuture = isValidAudienceValue(jwtData);
    audienceFuture
        .compose(
            audienceHandler -> {
              if (!result.jwtData.getIss().equals(result.jwtData.getSub())) {
                return isRevokedClientToken(result.jwtData);
              } else {
                return Future.succeededFuture(true);
              }
            })
        .compose(
            revokeTokenHandler -> {
              if (!skipResourceIdCheck
                  && !result.jwtData.getIss().equals(result.jwtData.getSub())) {
                return isOpenResource(id);
              } else {
                return Future.succeededFuture("OPEN");
              }
            })
        .compose(
            openResourceHandler -> {
              LOGGER.debug("isOpenResource messahe {}", openResourceHandler);
              result.isOpen = openResourceHandler.equalsIgnoreCase("OPEN");
              if (endPoint.equalsIgnoreCase(apis.getConnectorsPath())) {
                return isValidId(result.jwtData, id, ppbNumber, openResourceHandler);
              }

              if (result.isOpen && checkOpenEndPoints(endPoint)) {
                JsonObject json = new JsonObject();
                json.put(JSON_USERID, result.jwtData.getSub());
                return Future.succeededFuture(true);
              } else if (!skipResourceIdCheck && !result.isOpen) {
                return isValidId(result.jwtData, id, ppbNumber, openResourceHandler);
              } else {
                return Future.succeededFuture(true);
              }
            })
        .compose(
            validIdHandler -> {
              if (isConnectorEndpoints(authenticationInfo)) {
                return getProviderUserId(authenticationInfo.getString("id"));
              } else {
                return Future.succeededFuture("");
              }
            })
        .compose(
            providerUserHandler -> {
              if (isConnectorEndpoints(authenticationInfo)) {
                return validateProviderUser(providerUserHandler, result.jwtData);
              } else {
                return Future.succeededFuture(true);
              }
            })
        .compose(
            validProviderHandler -> {
              if (result.jwtData.getIss().equals(result.jwtData.getSub())) {
                JsonObject jsonResponse = new JsonObject();
                jsonResponse.put(JSON_USERID, result.jwtData.getSub());
                jsonResponse.put(
                    JSON_EXPIRY,
                    LocalDateTime.ofInstant(
                            Instant.ofEpochSecond(
                                Long.parseLong(result.jwtData.getExp().toString())),
                            ZoneId.systemDefault())
                        .toString());
                jsonResponse.put(ROLE, result.jwtData.getRole());
                jsonResponse.put(DRL, result.jwtData.getDrl());
                jsonResponse.put(DID, result.jwtData.getDid());
                jsonResponse.put(JSON_APD, result.jwtData.getApd());
                jsonResponse.put(JSON_CONS, result.jwtData.getCons());
                return Future.succeededFuture(jsonResponse);
              } else {
                return validateAccess(result.jwtData, result.isOpen, authenticationInfo);
              }
            })
        .onSuccess(
            successHandler -> {
              handler.handle(Future.succeededFuture(successHandler));
            })
        .onFailure(
            failureHandler -> {
              failureHandler.printStackTrace();
              LOGGER.error("error : " + failureHandler.getMessage());
              handler.handle(Future.failedFuture(failureHandler.getMessage()));
            });
    return this;
  }

  private boolean isConnectorEndpoints(JsonObject authenticationInfo) {
    return authenticationInfo.getString("apiEndpoint").equalsIgnoreCase("/ngsi-ld/v1/connectors");
  }

  Future<String> getProviderUserId(String id) {
    LOGGER.trace("getProviderUserId () started");
    relationshipCatPath = catBasePath + "/relationship";
    Promise<String> promise = Promise.promise();
    LOGGER.debug("id: " + id);
    catWebClient
        .get(port, host, relationshipCatPath)
        .addQueryParam("id", id)
        .addQueryParam("rel", "provider")
        .expect(ResponsePredicate.JSON)
        .send(
            catHandler -> {
              if (catHandler.succeeded()) {
                JsonArray response = catHandler.result().bodyAsJsonObject().getJsonArray("results");
                response.forEach(
                    json -> {
                      JsonObject res = (JsonObject) json;
                      String providerUserId = res.getString("ownerUserId");
                      LOGGER.info("providerUserId: " + providerUserId);
                      promise.complete(providerUserId);
                    });

              } else {
                LOGGER.error(
                    "Failed to call catalogue  while getting provider user id {}",
                    catHandler.cause().getMessage());
              }
            });

    return promise.future();
  }

  Future<Boolean> validateProviderUser(String providerUserId, JwtData jwtData) {
    LOGGER.trace("validateProviderUser() started");
    Promise<Boolean> promise = Promise.promise();
    try {
      if (jwtData.getRole().equalsIgnoreCase("delegate")) {
        if (jwtData.getDid().equalsIgnoreCase(providerUserId)) {
          LOGGER.info("success");
          promise.complete(true);
        } else {
          LOGGER.error("fail");
          promise.fail("incorrect providerUserId");
        }
      } else if (jwtData.getRole().equalsIgnoreCase("provider")) {
        if (jwtData.getSub().equalsIgnoreCase(providerUserId)) {
          LOGGER.info("success");
          promise.complete(true);
        } else {
          LOGGER.error("fail");
          promise.fail("incorrect providerUserId");
        }
      } else {
        promise.fail("Invalid role found");
      }
    } catch (Exception e) {
      LOGGER.error("exception occurred while validating provider user : " + e.getMessage());
      promise.fail("exception occurred while validating provider user");
    }
    return promise.future();
  }

  Future<Boolean> isRevokedClientToken(JwtData jwtData) {
    Promise<Boolean> promise = Promise.promise();
    CacheType cacheType = CacheType.REVOKED_CLIENT;
    String subId = jwtData.getSub();
    JsonObject requestJson = new JsonObject().put("type", cacheType).put("key", subId);
    Future<JsonObject> cacheCallFuture = cache.get(requestJson);
    cacheCallFuture
        .onSuccess(
            successHandler -> {
              JsonObject responseJson = successHandler;

              String timestamp = responseJson.getString("value");

              LocalDateTime revokedAt = LocalDateTime.parse(timestamp);
              LocalDateTime jwtIssuedAt =
                  LocalDateTime.ofInstant(
                      Instant.ofEpochSecond(jwtData.getIat()), ZoneId.systemDefault());

              if (jwtIssuedAt.isBefore(revokedAt)) {
                LOGGER.info("jwt issued at : " + jwtIssuedAt + " revokedAt : " + revokedAt);
                LOGGER.error("Privileges for client are revoked.");
                JsonObject result = new JsonObject().put("401", "revoked token passes");
                promise.fail(result.toString());
              } else {
                promise.complete(true);
              }
            })
        .onFailure(
            failureHandler -> {
              LOGGER.info("revoked_client cache call result : [fail] " + failureHandler);
              promise.complete(true);
            });

    return promise.future();
  }

  Future<String> isOpenResource(String id) {
    LOGGER.info("isOpenResource() started");
    Promise<String> promise = Promise.promise();
    CacheType cacheType = CacheType.CATALOGUE_CACHE;
    JsonObject requestJson = new JsonObject().put("type", cacheType).put("key", id);
    cache
        .get(requestJson)
        .onSuccess(
            cacheResult -> {
              if (cacheResult == null || !cacheResult.containsKey("accessPolicy")) {
                promise.fail("ACL not defined for resource item");
              } else {
                promise.complete(cacheResult.getString("accessPolicy"));
              }
            })
        .onFailure(
            failureHandler -> {
              LOGGER.error("catalogue_cache call result : [fail] " + failureHandler);
              promise.fail("Not Found");
            });
    return promise.future();
  }

  Future<Boolean> isValidId(JwtData jwtData, String id, String ppbNumber, String acl) {
    Promise<Boolean> promise = Promise.promise();
    if (acl.equalsIgnoreCase(PII)) {
      boolean isValid = isValidPpbNo(jwtData, ppbNumber);
      if (!isValid) {
        return Future.failedFuture("Incorrect ppbNumber value in jwt");
      }
    }
    String jwtId = jwtData.getIid().split(":")[1];
    if (id.equalsIgnoreCase(jwtId)) {
      promise.complete(true);
    } else {
      promise.fail("Incorrect id value in jwt");
    }

    return promise.future();
  }

  private boolean isValidPpbNo(JwtData jwtData, String ppbNumber) {
    String ppbNoInToken = extractPPBNo(jwtData);
    return ppbNoInToken.equalsIgnoreCase(ppbNumber) ? true : false;
  }

  public String extractPPBNo(JwtData jwtData) {
    JsonObject cons = jwtData.getCons();
    if (cons == null) {
      return "";
    }
    String ppbno = cons.getString(PPB_NUMBER);
    if (ppbno == null) {
      return "";
    }
    return ppbno;
  }

  public Future<JsonObject> validateAccess(
      JwtData jwtData, boolean openResource, JsonObject authInfo) {
    LOGGER.trace("validateAccess() started");
    Promise<JsonObject> promise = Promise.promise();
    String jwtId = jwtData.getIid().split(":")[1];

    if (openResource && checkOpenEndPoints(authInfo.getString("apiEndpoint"))) {
      LOGGER.info("User access is allowed.");
      JsonObject jsonResponse = new JsonObject();
      jsonResponse.put(JSON_IID, jwtId);
      jsonResponse.put(JSON_USERID, jwtData.getSub());
      jsonResponse.put(JSON_APD, jwtData.getApd());
      jsonResponse.put(JSON_CONS, jwtData.getCons());
      jsonResponse.put(ROLE, jwtData.getRole());
      jsonResponse.put(DRL, jwtData.getDrl());
      jsonResponse.put(DID, jwtData.getDid());
      return Future.succeededFuture(jsonResponse);
    }

    Method method = Method.valueOf(authInfo.getString("method"));
    String api = authInfo.getString("apiEndpoint");
    AuthorizationRequest authRequest = new AuthorizationRequest(method, api);
    IudxRole role = IudxRole.fromRole(jwtData.getRole());
    AuthorizationStrategy authStrategy = AuthorizationContextFactory.create(role, apis);
    JwtAuthorization jwtAuthStrategy = new JwtAuthorization(authStrategy);
    LOGGER.info("auth strategy " + authStrategy.getClass().getSimpleName());
    LOGGER.info("endPoint : " + authInfo.getString("apiEndpoint"));
    if (jwtAuthStrategy.isAuthorized(authRequest, jwtData)) {
      LOGGER.info("User access is allowed.");
      JsonObject jsonResponse = new JsonObject();
      jsonResponse.put(JSON_USERID, jwtData.getSub());
      jsonResponse.put(JSON_IID, jwtId);
      jsonResponse.put(JSON_APD, jwtData.getApd());
      jsonResponse.put(JSON_CONS, jwtData.getCons());
      jsonResponse.put(
          JSON_EXPIRY,
          LocalDateTime.ofInstant(
                  Instant.ofEpochSecond(Long.parseLong(jwtData.getExp().toString())),
                  ZoneId.systemDefault())
              .toString());
      jsonResponse.put(ROLE, jwtData.getRole());
      jsonResponse.put(DRL, jwtData.getDrl());
      jsonResponse.put(DID, jwtData.getDid());
      promise.complete(jsonResponse);
    } else {
      LOGGER.error("failed - no access provided to endpoint");
      JsonObject result = new JsonObject().put("401", "no access provided to endpoint");
      promise.fail(result.toString());
    }
    return promise.future();
  }

  private boolean checkOpenEndPoints(String endPoint) {
    for (String item : OPEN_ENDPOINTS) {
      if (endPoint.contains(item)) {
        return true;
      }
    }
    return false;
  }

  Future<Boolean> isValidAudienceValue(JwtData jwtData) {
    Promise<Boolean> promise = Promise.promise();
    if (audience != null && audience.equalsIgnoreCase(jwtData.getAud())) {
      promise.complete(true);
    } else {
      promise.fail("Incorrect audience value in jwt");
    }
    return promise.future();
  }

  @Override
  public AuthenticationService decodeJwt(String jwtToken, Handler<AsyncResult<JwtData>> handler) {
    TokenCredentials creds = new TokenCredentials(jwtToken);
    jwtAuth
        .authenticate(creds)
        .onSuccess(
            user -> {
              JwtData jwtData = new JwtData(user.principal());
              jwtData.setExp(user.get("exp"));
              jwtData.setIat(user.get("iat"));
              handler.handle(Future.succeededFuture(jwtData));
            })
        .onFailure(
            err -> {
              LOGGER.debug("err.getMessage():: " + err);
              LOGGER.error("failed to decode/validate jwt token : " + err.getMessage());
              handler.handle(Future.failedFuture(err.getMessage()));
            });

    return this;
  }

  // class to contain intermediate data for token introspection
  final class ResultContainer {
    JwtData jwtData;
    boolean isOpen;
  }
}
