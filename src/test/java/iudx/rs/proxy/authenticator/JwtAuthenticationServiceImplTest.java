package iudx.rs.proxy.authenticator;

import static iudx.rs.proxy.authenticator.authorization.Method.GET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.micrometer.core.ipc.http.HttpSender;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.PubSecKeyOptions;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.auth.jwt.JWTAuthOptions;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import iudx.rs.proxy.authenticator.authorization.AuthorizationRequest;
import iudx.rs.proxy.authenticator.model.JwtData;
import iudx.rs.proxy.cache.CacheService;
import iudx.rs.proxy.cache.cacheImpl.CacheType;
import iudx.rs.proxy.common.Api;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(VertxExtension.class)
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationServiceImplTest {
  private static final Logger LOGGER = LogManager.getLogger(JwtAuthenticationServiceImplTest.class);
  static WebClient catWebClient;
  static JwtData jwtData = new JwtData();
  private static JsonObject authConfig;
  private static JwtAuthenticationServiceImpl jwtAuthenticationService;
  private static JwtAuthenticationServiceImpl jwtAuthImplSpy;
  private static CacheService cacheServiceMock;
  private static Api apis;
  private static String delegateJwt =
      "eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiJ9.eyJzdWIiOiJhMTNlYjk1NS1jNjkxLTRmZDMtYjIwMC1mMThiYzc4ODEwYjUiLCJpc3MiOiJhdXRoLnRlc3QuY29tIiwiYXVkIjoiZm9vYmFyLml1ZHguaW8iLCJleHAiOjE2MjgxODIzMjcsImlhdCI6MTYyODEzOTEyNywiaWlkIjoicmk6ZXhhbXBsZS5jb20vNzllN2JmYTYyZmFkNmM3NjViYWM2OTE1NGMyZjI0Yzk0Yzk1MjIwYS9yZXNvdXJjZS1ncm91cC9yZXNvdXJjZSIsInJvbGUiOiJkZWxlZ2F0ZSIsImNvbnMiOnsiYWNjZXNzIjpbImFwaSIsInN1YnMiLCJpbmdlc3QiLCJmaWxlIl19fQ.tUoO1L-tXByxNtjY_iK41neeshCiYrNr505wWn1hC1ACwoeL9frebABeFiCqJQGrsBsGOZ1-OACZdHBNcetwyw";
  private static String consumerJwt =
      "eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiJ9.eyJzdWIiOiIzMmE0Yjk3OS00ZjRhLTRjNDQtYjBjMy0yZmUxMDk5NTJiNWYiLCJpc3MiOiJhdXRoLnRlc3QuY29tIiwiYXVkIjoiZm9vYmFyLml1ZHguaW8iLCJleHAiOjE2MjgxODUzNTksImlhdCI6MTYyODE0MjE1OSwiaWlkIjoicmc6ZXhhbXBsZS5jb20vNzllN2JmYTYyZmFkNmM3NjViYWM2OTE1NGMyZjI0Yzk0Yzk1MjIwYS9yZXNvdXJjZS1ncm91cCIsInJvbGUiOiJjb25zdW1lciIsImNvbnMiOnsiYWNjZXNzIjpbImFwaSIsInN1YnMiLCJpbmdlc3QiLCJmaWxlIl19fQ.NoEiJB_5zwTU-zKbFHTefMuqDJ7L6mA11mfckzA4IZOSrdweSmR6my0zGcf7hEVljX9OOFm4tToZQYfCtPg4Uw";
  private static String providerJwt =
      "eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiJ9.eyJzdWIiOiJhMTNlYjk1NS1jNjkxLTRmZDMtYjIwMC1mMThiYzc4ODEwYjUiLCJpc3MiOiJhdXRoLnRlc3QuY29tIiwiYXVkIjoiZm9vYmFyLml1ZHguaW8iLCJleHAiOjE2MjgxODU4MjEsImlhdCI6MTYyODE0MjYyMSwiaWlkIjoicmc6ZXhhbXBsZS5jb20vNzllN2JmYTYyZmFkNmM3NjViYWM2OTE1NGMyZjI0Yzk0Yzk1MjIwYS9yZXNvdXJjZS1ncm91cCIsInJvbGUiOiJwcm92aWRlciIsImNvbnMiOnsiYWNjZXNzIjpbImFwaSIsInN1YnMiLCJpbmdlc3QiLCJmaWxlIl19fQ.BSoCQPUT8_YA-6p7-_OEUBOfbbvQZs8VKwDzdnubT3gutVueRe42a9d9mhszhijMQK7Qa0ww_rmAaPhA_2jP6w";
  private static String closedResourceToken =
      "eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiJ9.eyJzdWIiOiJhMTNlYjk1NS1jNjkxLTRmZDMtYjIwMC1mMThiYzc4ODEwYjUiLCJpc3MiOiJhdXRoLnRlc3QuY29tIiwiYXVkIjoicnMuaXVkeC5pbyIsImV4cCI6MTYyODYxMjg5MCwiaWF0IjoxNjI4NTY5NjkwLCJpaWQiOiJyZzppaXNjLmFjLmluLzg5YTM2MjczZDc3ZGFjNGNmMzgxMTRmY2ExYmJlNjQzOTI1NDdmODYvcnMuaXVkeC5pby9zdXJhdC1pdG1zLXJlYWx0aW1lLWluZm9ybWF0aW9uL3N1cmF0LWl0bXMtbGl2ZS1ldGEiLCJyb2xlIjoiY29uc3VtZXIiLCJjb25zIjp7ImFjY2VzcyI6WyJhcGkiLCJzdWJzIiwiaW5nZXN0IiwiZmlsZSJdfX0.OBJZUc15s8gDA6PB5IK3KkUGmjvJQWr7RvByhMXmmrCULmPGgtesFmNDVG2gqD4WXZob5OsjxZ1vxRmgMBgLxw";
  private String id =
      "datakaveri.org/04a15c9960ffda227e9546f3f46e629e1fe4132b/rs.iudx.io/pune-env-flood/FWR053";
  ;

  @BeforeAll
  @DisplayName("Initialize Vertx and deploy Auth Verticle")
  static void init(Vertx vertx, VertxTestContext testContext) {

    jwtData.setIss("auth.test.com");
    jwtData.setAud("rs.iudx.io");
    jwtData.setExp(1627408865);
    jwtData.setIat(1627408865);
    jwtData.setIid(
        "ri:datakaveri.org/04a15c9960ffda227e9546f3f46e629e1fe4132b/rs.iudx.io/pune-env-flood/FWR053");
    jwtData.setRole("consumer");
    jwtData.setCons(null);

    authConfig = new JsonObject();
    authConfig.put("catServerHost", "rs.iudx.io");
    authConfig.put("host", "rs.iudx.io");
    authConfig.put("catServerPort", 8080);
    authConfig.put("dxApiBasePath", "/ngsi-ld/v1");
    authConfig.put("dxCatalogueBasePath", "/iudx/cat/v1");
    authConfig.put("dxAuthBasePath", "/auth/v1");
    authConfig.put("audience", "rs.iudx.io");

    JWTAuthOptions jwtAuthOptions = new JWTAuthOptions();
    jwtAuthOptions.addPubSecKey(
        new PubSecKeyOptions()
            .setAlgorithm("ES256")
            .setBuffer(
                "-----BEGIN PUBLIC KEY-----\n"
                    + "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE8BKf2HZ3wt6wNf30SIsbyjYPkkTS\n"
                    + "GGyyM2/MGF/zYTZV9Z28hHwvZgSfnbsrF36BBKnWszlOYW0AieyAUKaKdg==\n"
                    + "-----END PUBLIC KEY-----\n"
                    + ""));

    jwtAuthOptions
        .getJWTOptions()
        .setIgnoreExpiration(true); // ignore token expiration only for test
    JWTAuth jwtAuth = JWTAuth.create(vertx, jwtAuthOptions);

    String dxApiBasePath = authConfig.getString("dxApiBasePath");
    apis = Api.getInstance(dxApiBasePath);

    cacheServiceMock = mock(CacheService.class);
    jwtAuthenticationService =
        new JwtAuthenticationServiceImpl(vertx, jwtAuth, authConfig, cacheServiceMock, apis);
    jwtAuthImplSpy = spy(jwtAuthenticationService);

    LOGGER.info("Auth tests setup complete");
    testContext.completeNow();
  }

  @Test
  public void test(VertxTestContext testContext) {
    AuthorizationRequest authReq = new AuthorizationRequest(GET, apis.getEntitiesEndpoint());
    AuthorizationRequest authReq1 = new AuthorizationRequest(GET, "/ngsi-ld/v1/entities");
    assertEquals(authReq, authReq1);
    testContext.completeNow();
  }

  @Test
  @DisplayName("decode valid jwt - consumer")
  public void decodeJwtConsumerSuccess(VertxTestContext testContext) {

    jwtAuthImplSpy.decodeJwt(
        consumerJwt,
        handler -> {
          System.out.println(handler.succeeded());
          if (handler.succeeded()) {
            assertEquals("consumer", handler.result().getRole());
            testContext.completeNow();
          } else {
            testContext.failNow(handler.cause());
          }
        });
  }

  @Test
  @DisplayName("decode invalid jwt")
  public void decodeJwtFailure(VertxTestContext testContext) {
    String jwt =
        "eyJ0eXAiOiJKV1QiLCJbGciOiJFUzI1NiJ9.eyJzdWIiOiJhM2U3ZTM0Yy00NGJmLTQxZmYtYWQ4Ni0yZWUwNGE5NTQ0MTgiLCJpc3MiOiJhdXRoLnRlc3QuY29tIiwiYXVkIjoiZm9vYmFyLml1ZHguaW8iLCJleHAiOjE2Mjc2ODk5NDAsImlhdCI6MTYyNzY0Njc0MCwiaWlkIjoicmc6ZXhhbXBsZS5jb20vNzllN2JmYTYyZmFkNmM3NjViYWM2OTE1NGMyZjI0Yzk0Yzk1MjIwYS9yZXNvdXJjZS1ncm91cCIsInJvbGUiOiJkZWxlZ2F0ZSIsImNvbnMiOnt9fQ.eJjCUvWuGD3L3Dn2fKj8Ydl1byGoyRS59VfL6ZJcdKR3_eIhm6SOY-CW3p5XDSYVhRTlWvlPLjfXYo9t_PxgnA";
    jwtAuthenticationService.decodeJwt(
        jwt,
        handler -> {
          if (handler.succeeded()) {
            testContext.failNow(handler.cause());
          } else {
            testContext.completeNow();
          }
        });
  }

  @Test
  @DisplayName("success - allow consumer access to /entities endpoint for access [api,subs]")
  public void access4ConsumerTokenEntitiesAPI(VertxTestContext testContext) {

    JsonObject authInfo = new JsonObject();

    String id =
        "datakaveri.org/04a15c9960ffda227e9546f3f46e629e1fe4132b/rs.iudx.io/pune-env-flood/FWR053";

    authInfo.put("token", consumerJwt);
    authInfo.put("id", id);
    authInfo.put("apiEndpoint", apis.getEntitiesEndpoint());
    authInfo.put("method", GET);

    JwtData jwtData = new JwtData();
    jwtData.setIss("auth.test.com");
    jwtData.setAud("rs.iudx.io");
    jwtData.setExp(1627408865);
    jwtData.setIat(1627408865);
    jwtData.setIid(
        "ri:datakaveri.org/04a15c9960ffda227e9546f3f46e629e1fe4132b/rs.iudx.io/pune-env-flood/FWR053");
    jwtData.setRole("consumer");
    jwtData.setCons(new JsonObject().put("access", new JsonArray().add("file")));

    jwtAuthenticationService
        .validateAccess(jwtData, true, authInfo)
        .onComplete(
            handler -> {
              if (handler.succeeded()) {
                testContext.completeNow();
              } else {
                testContext.failNow("invalid access");
              }
            });
  }

  @Test
  @DisplayName("success - allow provider access to /entities endpoint for access [api,subs]")
  public void access4ProviderTokenEntitiesAPI(VertxTestContext testContext) {

    JsonObject authInfo = new JsonObject();

    String id =
        "datakaveri.org/04a15c9960ffda227e9546f3f46e629e1fe4132b/rs.iudx.io/pune-env-flood/FWR053";

    authInfo.put("token", consumerJwt);
    authInfo.put("id", id);
    authInfo.put("apiEndpoint", apis.getEntitiesEndpoint());
    authInfo.put("method", GET);

    JwtData jwtData = new JwtData();
    jwtData.setIss("auth.test.com");
    jwtData.setAud("rs.iudx.io");
    jwtData.setExp(1627408865);
    jwtData.setIat(1627408865);
    jwtData.setIid(
        "ri:datakaveri.org/04a15c9960ffda227e9546f3f46e629e1fe4132b/rs.iudx.io/pune-env-flood/FWR053");
    jwtData.setRole("provider");
    jwtData.setCons(new JsonObject().put("access", new JsonArray().add("file")));

    jwtAuthenticationService
        .validateAccess(jwtData, false, authInfo)
        .onComplete(
            handler -> {
              if (handler.succeeded()) {
                testContext.completeNow();
              } else {
                testContext.failNow("invalid access");
              }
            });
  }

  @Test
  @DisplayName("success - allow delegate access to /entities endpoint for access [api,subs]")
  public void access4DelegateTokenEntitiesAPI(VertxTestContext testContext) {

    JsonObject authInfo = new JsonObject();

    String id =
        "datakaveri.org/04a15c9960ffda227e9546f3f46e629e1fe4132b/rs.iudx.io/pune-env-flood/FWR053";

    authInfo.put("token", consumerJwt);
    authInfo.put("id", id);
    authInfo.put("apiEndpoint", apis.getEntitiesEndpoint());
    authInfo.put("method", GET);

    JwtData jwtData = new JwtData();
    jwtData.setIss("auth.test.com");
    jwtData.setAud("rs.iudx.io");
    jwtData.setExp(1627408865);
    jwtData.setIat(1627408865);
    jwtData.setIid(
        "ri:datakaveri.org/04a15c9960ffda227e9546f3f46e629e1fe4132b/rs.iudx.io/pune-env-flood/FWR053");
    jwtData.setRole("delegate");
    jwtData.setCons(new JsonObject().put("access", new JsonArray().add("file")));

    jwtAuthenticationService
        .validateAccess(jwtData, false, authInfo)
        .onComplete(
            handler -> {
              if (handler.succeeded()) {
                testContext.completeNow();
              } else {
                testContext.failNow("invalid access");
              }
            });
  }

  @Test
  @DisplayName("fail - allow consumer access to /entities endpoint for null access")
  public void failAccess4ConsumerTokenEntitiesAPI(VertxTestContext testContext) {

    JsonObject authInfo = new JsonObject();

    authInfo.put("token", consumerJwt);
    authInfo.put(
        "id",
        "datakaveri.org/04a15c9960ffda227e9546f3f46e629e1fe4132b/rs.iudx.io/pune-env-flood/FWR053");
    authInfo.put("apiEndpoint", apis.getEntitiesEndpoint());
    authInfo.put("method", GET);

    JwtData jwtData = new JwtData();
    jwtData.setIss("auth.test.com");
    jwtData.setAud("rs.iudx.io");
    jwtData.setExp(1627408865);
    jwtData.setIat(1627408865);
    jwtData.setIid(
        "ri:datakaveri.org/04a15c9960ffda227e9546f3f46e629e1fe4132b/rs.iudx.io/pune-env-flood/FWR053");
    jwtData.setRole("consumer");
    jwtData.setCons(null);

    jwtAuthenticationService
        .validateAccess(jwtData, false, authInfo)
        .onComplete(
            handler -> {
              if (handler.succeeded()) {
                testContext.failNow("success for invalid access");
              } else {
                testContext.completeNow();
              }
            });
  }

  @Test
  @DisplayName("fail - consumer access to /entities endpoint for access [subs]")
  public void fail4ConsumerTokenEntitiesAPI(VertxTestContext testContext) {

    JsonObject authInfo = new JsonObject();

    authInfo.put("token", consumerJwt);
    authInfo.put(
        "id",
        "datakaveri.org/04a15c9960ffda227e9546f3f46e629e1fe4132b/rs.iudx.io/pune-env-flood/FWR053");
    authInfo.put("apiEndpoint", apis.getEntitiesEndpoint());
    authInfo.put("method", GET);

    JwtData jwtData = new JwtData();
    jwtData.setIss("auth.test.com");
    jwtData.setAud("file.iudx.io");
    jwtData.setExp(1627408865);
    jwtData.setIat(1627408865);
    jwtData.setIid(
        "ri:datakaveri.org/04a15c9960ffda227e9546f3f46e629e1fe4132b/rs.iudx.io/pune-env-flood/FWR053");
    jwtData.setRole("consumer");

    JsonObject access = new JsonObject()
            .put("api", new JsonObject().put("limit", 1000).put("unit", "number"))
            .put("sub", new JsonObject().put("limit", 10000).put("unit", "MB"))
            .put("file", new JsonObject().put("limit", 1000).put("unit", "number"))
            .put("async", new JsonObject().put("limit", 10000).put("unit", 122));

    JsonArray attrs = new JsonArray()
            .add("trip_direction")
            .add("trip_id")
            .add("location")
            .add("id")
            .add("observationDateTime");

    JsonObject cons = new JsonObject()
            .put("access", access)
            .put("attrs", attrs);

    jwtData.setCons(cons);
    jwtAuthenticationService
        .validateAccess(jwtData, false, authInfo)
        .onComplete(
            handler -> {
              if (handler.succeeded()) {
                testContext.completeNow();
              } else {
                testContext.failNow("invalid access allowed");
              }
            });
  }

  @Test
  @DisplayName("success - token interospection allow access")
  public void successTokenInterospect(VertxTestContext testContext) {
    JsonObject authInfo = new JsonObject();
    JwtData jwtData = new JwtData();
    jwtData.setSub("a152db1f-4897-4fa2-bded-f4329c16333333");
    jwtData.setIss("auth.test.com");
    jwtData.setAud("rs.iudx.io");
    jwtData.setExp(1627408865);
    jwtData.setIat(1627408865);
    jwtData.setIid("ri:a152db1f-4897-4fa2-bded-f4329c1642af");
    jwtData.setRole("consumer");
    jwtData.setCons(null);

    authInfo.put("token", consumerJwt);
    authInfo.put("id", "a152db1f-4897-4fa2-bded-f4329c1642af");
    authInfo.put("apiEndpoint", apis.getEntitiesEndpoint());
    authInfo.put("method", GET);

    JsonObject revokedTokenRequest = new JsonObject();
    revokedTokenRequest.put("type", CacheType.REVOKED_CLIENT);
    revokedTokenRequest.put("key", jwtData.getSub());

    when(cacheServiceMock.get(any()))
        .thenReturn(
            Future.succeededFuture(
                new JsonObject().put("value", "2021-06-09T12:52:37").put("accessPolicy", "OPEN")));

    jwtAuthenticationService.tokenIntrospect(
        new JsonObject(),
        authInfo,
        jwtData,
        ar -> {
          if (ar.succeeded()) {
            JsonObject result = ar.result();
            assertNotNull(result);
            assertEquals("a152db1f-4897-4fa2-bded-f4329c16333333", result.getString("userid"));
            assertEquals("a152db1f-4897-4fa2-bded-f4329c1642af", result.getString("iid"));
            testContext.completeNow();

          } else {
            testContext.failNow("failed: " + ar.cause().getMessage());
          }
        });
  }

  // @Test
  @DisplayName("success - token interospection deny access[invalid client id]")
  public void failureTokenInterospectRevokedClient(VertxTestContext testContext) {
    JsonObject authInfo = new JsonObject();

    authInfo.put("token", consumerJwt);
    authInfo.put("id", "example.com/79e7bfa62fad6c765bac69154c2f24c94c95220a/resource-group");
    authInfo.put("apiEndpoint", apis.getEntitiesEndpoint());
    authInfo.put("method", GET);

    JwtData jwtData = new JwtData();
    jwtData.setIss("auth.test.co");
    jwtData.setAud("rs.iudx.i");
    jwtData.setExp(1627408865);
    jwtData.setIat(1627408865);
    jwtData.setIid(
        "ri:datakaveri.org/04a15c9960ffda227e9546f3f46e629e1fe4132b/rs.iudx.io/pune-env-flood/FWR053");
    jwtData.setRole("consumer");
    jwtData.setCons(null);

    JsonObject request = new JsonObject();

    doAnswer(Answer -> Future.succeededFuture(true))
        .when(jwtAuthImplSpy)
        .isValidAudienceValue(any());

    JsonObject cacheresponse = new JsonObject();
    JsonArray responseArray = new JsonArray();
    cacheresponse.put("value", "2019-10-19T14:20:00");
    responseArray.add(cacheresponse);

    jwtAuthImplSpy.tokenIntrospect(
        request,
        authInfo,
        jwtData,
        handler -> {
          if (handler.succeeded()) {
            testContext.failNow("failed");
          } else {
            testContext.completeNow();
          }
        });
  }

  @Test
  @DisplayName("fail - token interospection deny access [invalid audience]")
  public void failureTokenInterospectInvalidAudience(VertxTestContext testContext) {
    JsonObject authInfo = new JsonObject();

    authInfo.put("token", consumerJwt);
    authInfo.put("id", "example.com/79e7bfa62fad6c765bac69154c2f24c94c95220a/resource-group");
    authInfo.put("apiEndpoint", apis.getEntitiesEndpoint());
    authInfo.put("method", GET);

    JsonObject request = new JsonObject();

    doAnswer(Answer -> Future.failedFuture("invalid audience value"))
        .when(jwtAuthImplSpy)
        .isValidAudienceValue(any());

    jwtAuthImplSpy.tokenIntrospect(
        request,
        authInfo,
        jwtData,
        handler -> {
          if (handler.succeeded()) {
            testContext.failNow("failed");
          } else {
            testContext.completeNow();
          }
        });
  }

  @Test
  @DisplayName("fail - token interospection deny access [invalid resource]")
  public void failureTokenInterospectInvalidResource(VertxTestContext testContext) {
    JsonObject authInfo = new JsonObject();

    authInfo.put("token", consumerJwt);
    authInfo.put("id", "example.com/79e7bfa62fad6c765bac69154c2f24c94c95220a/resource-group");
    authInfo.put("apiEndpoint", apis.getEntitiesEndpoint());
    authInfo.put("method", GET);

    JsonObject request = new JsonObject();

    doAnswer(Answer -> Future.succeededFuture(true))
        .when(jwtAuthImplSpy)
        .isValidAudienceValue(any());

    jwtAuthImplSpy.tokenIntrospect(
        request,
        authInfo,
        jwtData,
        handler -> {
          if (handler.succeeded()) {
            testContext.failNow("failed");
          } else {
            testContext.completeNow();
          }
        });
  }

  @Test
  @DisplayName("fail - invalid audience value")
  public void fail4InvalidAud(VertxTestContext testContext) {

    JwtData jwt = new JwtData();
    jwt.setAud("rs.iudx.in");
    jwtAuthenticationService
        .isValidAudienceValue(jwt)
        .onComplete(
            handler -> {
              if (handler.succeeded()) {
                testContext.failNow("passed for invalid value");
              } else {
                testContext.completeNow();
              }
            });
  }

  @Test
  @DisplayName("success - valid audience value")
  public void success4ValidAud(VertxTestContext testContext) {

    JwtData jwt = new JwtData();
    jwt.setAud("rs.iudx.io");
    jwtAuthenticationService
        .isValidAudienceValue(jwt)
        .onComplete(
            handler -> {
              if (handler.succeeded()) {
                testContext.completeNow();
              } else {
                testContext.failNow("failed for valid value");
              }
            });
  }

  @Test
  @DisplayName("success - is open resource")
  public void success4openResource(VertxTestContext testContext) {
    JsonObject authInfo = new JsonObject();

    String id =
        "datakaveri.org/04a15c9960ffda227e9546f3f46e629e1fe4132b/rs.iudx.io/pune-env-flood/FWR053";

    authInfo.put("token", consumerJwt);
    authInfo.put("id", id);
    authInfo.put("apiEndpoint", apis.getEntitiesEndpoint());
    authInfo.put("method", GET);

    JwtData jwtData = new JwtData();
    jwtData.setIss("auth.test.com");
    jwtData.setAud("rs.iudx.io");
    jwtData.setExp(1627408865);
    jwtData.setIat(1627408865);
    jwtData.setIid(
        "ri:datakaveri.org/04a15c9960ffda227e9546f3f46e629e1fe4132b/rs.iudx.io/pune-env-flood/FWR053");
    jwtData.setRole("consumer");
    jwtData.setCons(new JsonObject().put("access", new JsonArray().add("file")));

    doAnswer(Answer -> Future.succeededFuture("OPEN")).when(jwtAuthImplSpy).isOpenResource(any());

    jwtAuthImplSpy
        .isOpenResource(id)
        .onComplete(
            openResourceHandler -> {
              if (openResourceHandler.succeeded() && openResourceHandler.result().equals("OPEN")) {
                testContext.completeNow();
              } else {
                testContext.failNow("open resource validation failed");
              }
            });
  }

  @Test
  @DisplayName("failure - is open resource")
  public void failure4openResource(VertxTestContext testContext) {
    JsonObject authInfo = new JsonObject();

    String id = "example.com/79e7bfa62fad6c765bac69154c2f24c94c95220a/resource-group";

    authInfo.put("token", consumerJwt);
    authInfo.put("id", id);
    authInfo.put("apiEndpoint", apis.getEntitiesEndpoint());
    authInfo.put("method", HttpSender.Method.GET);

    JwtData jwtData = new JwtData();
    jwtData.setIss("auth.test.com");
    jwtData.setAud("file.iudx.io");
    jwtData.setExp(1627408865);
    jwtData.setIat(1627408865);
    jwtData.setIid("ri:example.com/79e7bfa62fad6c765bac69154c2f24c94c95220a/resource-group");
    jwtData.setRole("consumer");
    jwtData.setCons(new JsonObject().put("access", new JsonArray().add("file")));

    when(cacheServiceMock.get(any()))
        .thenReturn(Future.succeededFuture(new JsonObject().put("value", "2021-06-09T12:52:37")));

    jwtAuthenticationService
        .isOpenResource(id)
        .onComplete(
            openResourceHandler -> {
              if (openResourceHandler.succeeded()) {
                testContext.failNow("open resource validation failed");
              } else {
                testContext.completeNow();
              }
            });
  }

  @Test
  @DisplayName("authRequest should have same hashcode")
  public void authRequestShouldhaveSamehash() {
    AuthorizationRequest authR1 = new AuthorizationRequest(GET, apis.getEntitiesEndpoint());
    AuthorizationRequest authR2 = new AuthorizationRequest(GET, apis.getEntitiesEndpoint());
    assertEquals(authR1.hashCode(), authR2.hashCode());
  }

  @Test
  @DisplayName("authRequest should not equal")
  public void authRequestShouldNotEquals() {
    AuthorizationRequest authR1 = new AuthorizationRequest(GET, apis.getTemporalEndpoint());
    AuthorizationRequest authR2 = new AuthorizationRequest(GET, apis.getEntitiesEndpoint());
    Assertions.assertFalse(authR1.equals(authR2));
  }
}
