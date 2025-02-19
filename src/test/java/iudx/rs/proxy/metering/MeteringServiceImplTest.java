package iudx.rs.proxy.metering;

import static iudx.rs.proxy.apiserver.util.ApiServerConstants.*;
import static iudx.rs.proxy.apiserver.util.ApiServerConstants.API;
import static iudx.rs.proxy.authenticator.Constants.ROLE;
import static iudx.rs.proxy.metering.MeteringServiceImpl.postgresService;
import static iudx.rs.proxy.metering.util.Constants.*;
import static iudx.rs.proxy.metering.util.Constants.ID;
import static iudx.rs.proxy.metering.util.Constants.IID;
import static iudx.rs.proxy.metering.util.Constants.PROVIDER_ID;
import static iudx.rs.proxy.metering.util.Constants.RESPONSE_SIZE;
import static iudx.rs.proxy.metering.util.Constants.USER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.rabbitmq.RabbitMQClient;
import iudx.rs.proxy.cache.CacheService;
import iudx.rs.proxy.cache.cacheImpl.CacheType;
import iudx.rs.proxy.common.Api;
import iudx.rs.proxy.configuration.Configuration;
import iudx.rs.proxy.database.DatabaseService;
import iudx.rs.proxy.databroker.DatabrokerServiceOld;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

@ExtendWith({VertxExtension.class, MockitoExtension.class})
class MeteringServiceImplTest {

  private static final Logger LOGGER = LogManager.getLogger(MeteringServiceImplTest.class);
  public static String userId;
  public static String id;
  static JsonObject dbConfig;
  static CacheService cacheService;
  private static MeteringServiceImpl meteringService;
  private static Vertx vertxObj;
  private static Configuration config;

  @BeforeAll
  @DisplayName("Deploying Verticle")
  static void startVertex(Vertx vertx, VertxTestContext vertxTestContext) {
    vertxObj = vertx;
    config = new Configuration();
    dbConfig =  new JsonObject()
            .put(DATABASE_IP,"ip")
            .put(DATABASE_PORT,123)
            .put(DATABASE_NAME,"name")
            .put(DATABASE_USERNAME,"user")
            .put(DATABASE_PASSWORD,"passs")
            .put(DATABASE_TABLE_NAME,"table")
            .put(POOL_SIZE,123);
  //config.configLoader(4, vertx);
    cacheService = mock(CacheService.class);
    meteringService =
        new MeteringServiceImpl(dbConfig, vertxObj, Api.getInstance("/ngsi-ld/v1"), cacheService);
    userId = UUID.randomUUID().toString();
    id = "89a36273d77dac4cf38114fca1bbe64392547f86";
    vertxTestContext.completeNow();
  }

  private JsonObject readConsumerRequest() {
    JsonObject jsonObject = new JsonObject();
    jsonObject.put(USER_ID, "15c7506f-c800-48d6-adeb-0542b03947c6");
    jsonObject.put(RESOURCE_ID, "15c7506f-c800-48d6-adeb-0542b03947c6/integration-test-alias/");
    jsonObject.put(START_TIME, "2022-05-29T05:30:00+05:30[Asia/Kolkata]");
    jsonObject.put(END_TIME, "2022-06-04T02:00:00+05:30[Asia/Kolkata]");
    jsonObject.put(TIME_RELATION, DURING);
    jsonObject.put(API, "/ngsi-ld/v1/subscription");
    jsonObject.put(ENDPOINT, "/ngsi-ld/v1/consumer/audit");

    return jsonObject;
  }

  private JsonObject readProviderRequest() {
    JsonObject jsonObject = new JsonObject();
    jsonObject.put(USER_ID, "15c7506f-c800-48d6-adeb-0542b03947c6");
    jsonObject.put(
        RESOURCE_ID,
        "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/pune-env-flood/FWR055");
    jsonObject.put(START_TIME, "2022-03-20T05:30:00+05:30[Asia/Kolkata]");
    jsonObject.put(END_TIME, "2022-03-30T02:00:00+05:30[Asia/Kolkata]");
    jsonObject.put(TIME_RELATION, DURING);
    jsonObject.put(API, "/ngsi-ld/v1/entities");
    jsonObject.put(PROVIDER_ID, "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86");
    jsonObject.put(CONSUMER_ID, "844e251b-574b-46e6-9247-f76f1f70a637");
    jsonObject.put(
        IID,
        "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/pune-env-flood/FWR055");
    jsonObject.put(ENDPOINT, "/ngsi-ld/v1/provider/audit");
    jsonObject.put(ROLE, "provider");
    return jsonObject;
  }

  private JsonObject read() {
    JsonObject jsonObject = new JsonObject();
    jsonObject.put(START_TIME, "2022-06-20T00:00:00Z");
    jsonObject.put(USER_ID, "15c7506f-c800-48d6-adeb-0542b03947c6");
    jsonObject.put(END_TIME, "2022-06-21T16:00:00Z");
    jsonObject.put(TIME_RELATION, "between");
    jsonObject.put(API, "/ngsi-ld/v1/subscription");
    jsonObject.put(PROVIDER_ID, "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86");
    jsonObject.put(ENDPOINT, "/ngsi-ld/v1/provider/audit");
    jsonObject.put(
        IID,
        "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/pune-env-flood/FWR055");
    return jsonObject;
  }

  @Test
  @DisplayName("Testing read query with given Time Interval")
  void readFromValidTimeInterval(VertxTestContext vertxTestContext) {
    JsonObject responseJson = new JsonObject().put(SUCCESS, "successful operations");
    AsyncResult<JsonObject> asyncResult = mock(AsyncResult.class);
    postgresService = mock(DatabaseService.class);
    JsonObject json = mock(JsonObject.class);
    JsonArray jsonArray = mock(JsonArray.class);

    when(asyncResult.succeeded()).thenReturn(true);
    when(asyncResult.result()).thenReturn(json, responseJson);
    when(json.getJsonArray(anyString())).thenReturn(jsonArray);
    when(jsonArray.getJsonObject(anyInt())).thenReturn(json);
    when(json.getInteger(anyString())).thenReturn(39);

    doAnswer(
            new Answer<AsyncResult<JsonObject>>() {
              @Override
              public AsyncResult<JsonObject> answer(InvocationOnMock arg1) throws Throwable {
                ((Handler<AsyncResult<JsonObject>>) arg1.getArgument(1)).handle(asyncResult);
                return null;
              }
            })
        .when(postgresService)
        .executeQuery(any(), any());

    JsonObject request = read();
    request.put(
        RESOURCE_ID,
        "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/pune-env-flood/FWR055");
    request.put(CONSUMER_ID, "125-fc132-23rwsd");
    meteringService.executeReadQuery(
        request,
        vertxTestContext.succeeding(
            response ->
                vertxTestContext.verify(
                    () -> {
                      LOGGER.info(response);
                      assertEquals(SUCCESS, response.getString(SUCCESS));
                      vertxTestContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Testing read query with given Time Interval")
  void readFromValidTimeIntervalConsumer(VertxTestContext vertxTestContext) {
    JsonObject responseJson = new JsonObject().put(SUCCESS, "successful operations");
    AsyncResult<JsonObject> asyncResult = mock(AsyncResult.class);
    postgresService = mock(DatabaseService.class);
    JsonObject json = mock(JsonObject.class);
    JsonArray jsonArray = mock(JsonArray.class);

    when(asyncResult.succeeded()).thenReturn(true);
    when(asyncResult.result()).thenReturn(json, responseJson);
    when(json.getJsonArray(anyString())).thenReturn(jsonArray);
    when(jsonArray.getJsonObject(anyInt())).thenReturn(json);
    when(json.getInteger(anyString())).thenReturn(39);

    doAnswer(
            new Answer<AsyncResult<JsonObject>>() {
              @Override
              public AsyncResult<JsonObject> answer(InvocationOnMock arg1) throws Throwable {
                ((Handler<AsyncResult<JsonObject>>) arg1.getArgument(1)).handle(asyncResult);
                return null;
              }
            })
        .when(postgresService)
        .executeQuery(any(), any());

    JsonObject request = read();
    request.remove(PROVIDER_ID);
    request.put(
        RESOURCE_ID,
        "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/pune-env-flood/FWR055");
    meteringService.executeReadQuery(
        request,
        vertxTestContext.succeeding(
            response ->
                vertxTestContext.verify(
                    () -> {
                      LOGGER.info(response);
                      assertEquals(SUCCESS, response.getString(SUCCESS));
                      vertxTestContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Testing read query for given time,api and id.")
  void readForGivenTimeApiIdConsumerProviderIDZero(VertxTestContext vertxTestContext) {
    JsonObject responseJson = new JsonObject().put(SUCCESS, "Success");
    AsyncResult<JsonObject> asyncResult = mock(AsyncResult.class);
    postgresService = mock(DatabaseService.class);
    JsonObject json = mock(JsonObject.class);
    JsonArray jsonArray = mock(JsonArray.class);

    when(asyncResult.succeeded()).thenReturn(true);
    when(asyncResult.result()).thenReturn(json, responseJson);

    when(json.getJsonArray(anyString())).thenReturn(jsonArray);
    when(jsonArray.getJsonObject(anyInt())).thenReturn(json);
    when(json.getInteger(anyString())).thenReturn(0);

    Mockito.doAnswer(
            new Answer<AsyncResult<JsonObject>>() {
              @Override
              public AsyncResult<JsonObject> answer(InvocationOnMock arg1) throws Throwable {
                ((Handler<AsyncResult<JsonObject>>) arg1.getArgument(1)).handle(asyncResult);
                return null;
              }
            })
        .when(postgresService)
        .executeQuery(any(), any());
    JsonObject jsonObject = readProviderRequest();

    meteringService.executeReadQuery(
        jsonObject,
        vertxTestContext.succeeding(
            response ->
                vertxTestContext.verify(
                    () -> {
                      assertEquals(SUCCESS, response.getString("title"));
                      vertxTestContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Testing read query for missing userId")
  void readForMissingUserId(VertxTestContext vertxTestContext) {
    JsonObject request = readConsumerRequest();
    request.remove(USER_ID);

    meteringService.executeReadQuery(
        request,
        vertxTestContext.failing(
            response ->
                vertxTestContext.verify(
                    () -> {
                      assertEquals(
                          USERID_NOT_FOUND,
                          new JsonObject(response.getMessage()).getString(DETAIL));
                      vertxTestContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Testing read query for missing time Relation")
  void readForMissingTimeRel(VertxTestContext vertxTestContext) {
    JsonObject request = readConsumerRequest();
    request.remove(TIME_RELATION);

    meteringService.executeReadQuery(
        request,
        vertxTestContext.failing(
            response ->
                vertxTestContext.verify(
                    () -> {
                      assertEquals(
                          TIME_RELATION_NOT_FOUND,
                          new JsonObject(response.getMessage()).getString(DETAIL));
                      vertxTestContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Testing read query for missing time")
  void readForMissingTime(VertxTestContext vertxTestContext) {
    JsonObject request = readConsumerRequest();
    request.remove(START_TIME);

    meteringService.executeReadQuery(
        request,
        vertxTestContext.failing(
            response ->
                vertxTestContext.verify(
                    () -> {
                      assertEquals(
                          TIME_NOT_FOUND, new JsonObject(response.getMessage()).getString(DETAIL));
                      vertxTestContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Testing read query with invalid start/end time")
  void readForInvalidStartTime(VertxTestContext vertxTestContext) {
    JsonObject request = readConsumerRequest();
    request.put(START_TIME, "2021-009-18T00:30:00+05:30[Asia/Kolkata]");

    meteringService.executeReadQuery(
        request,
        vertxTestContext.failing(
            response ->
                vertxTestContext.verify(
                    () -> {
                      assertEquals(
                          INVALID_DATE_TIME,
                          new JsonObject(response.getMessage()).getString(DETAIL));
                      vertxTestContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Testing count query for given time.")
  void readCountForGivenTime(VertxTestContext vertxTestContext) {
    AsyncResult<JsonObject> asyncResult = mock(AsyncResult.class);
    postgresService = mock(DatabaseService.class);
    JsonObject json = mock(JsonObject.class);
    JsonArray jsonArray = mock(JsonArray.class);

    when(asyncResult.succeeded()).thenReturn(true);
    when(asyncResult.result()).thenReturn(json);

    when(json.getJsonArray(anyString())).thenReturn(jsonArray);
    when(jsonArray.getJsonObject(anyInt())).thenReturn(json);
    when(json.getInteger(anyString())).thenReturn(300);

    doAnswer(
            new Answer<AsyncResult<JsonObject>>() {
              @Override
              public AsyncResult<JsonObject> answer(InvocationOnMock arg1) throws Throwable {
                ((Handler<AsyncResult<JsonObject>>) arg1.getArgument(1)).handle(asyncResult);
                return null;
              }
            })
        .when(postgresService)
        .executeQuery(any(), any());

    JsonObject jsonObject = read();
    jsonObject.put("options", "count");
    jsonObject.remove(API);
    meteringService.executeReadQuery(
        jsonObject,
        vertxTestContext.succeeding(
            response ->
                vertxTestContext.verify(
                    () -> {
                      assertEquals(SUCCESS, response.getString("title"));
                      vertxTestContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Testing count query for given time for Consumer")
  void readCountForGivenTimeConsumer(VertxTestContext vertxTestContext) {
    AsyncResult<JsonObject> asyncResult = mock(AsyncResult.class);
    postgresService = mock(DatabaseService.class);
    JsonObject json = mock(JsonObject.class);
    JsonArray jsonArray = mock(JsonArray.class);

    when(asyncResult.succeeded()).thenReturn(true);
    when(asyncResult.result()).thenReturn(json);
    when(json.getJsonArray(anyString())).thenReturn(jsonArray);
    when(jsonArray.getJsonObject(anyInt())).thenReturn(json);
    when(json.getInteger(anyString())).thenReturn(300);

    doAnswer(
            new Answer<AsyncResult<JsonObject>>() {
              @Override
              public AsyncResult<JsonObject> answer(InvocationOnMock arg1) throws Throwable {
                ((Handler<AsyncResult<JsonObject>>) arg1.getArgument(1)).handle(asyncResult);
                return null;
              }
            })
        .when(postgresService)
        .executeQuery(any(), any());

    JsonObject jsonObject = read();
    jsonObject.put("options", "count");
    jsonObject.remove(PROVIDER_ID);
    jsonObject.put(
        RESOURCE_ID,
        "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/pune-env-flood/FWR055");
    meteringService.executeReadQuery(
        jsonObject,
        vertxTestContext.succeeding(
            response ->
                vertxTestContext.verify(
                    () -> {
                      assertEquals(SUCCESS, response.getString("title"));
                      vertxTestContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Testing count query for given time.")
  void readCountForGivenTimeForZero(VertxTestContext vertxTestContext) {
    AsyncResult<JsonObject> asyncResult = mock(AsyncResult.class);
    postgresService = mock(DatabaseService.class);
    JsonObject json = mock(JsonObject.class);
    JsonArray jsonArray = mock(JsonArray.class);

    when(asyncResult.succeeded()).thenReturn(true);
    when(asyncResult.result()).thenReturn(json);
    when(json.getJsonArray(anyString())).thenReturn(jsonArray);
    when(jsonArray.getJsonObject(anyInt())).thenReturn(json);
    when(json.getInteger(anyString())).thenReturn(0);

    doAnswer(
            new Answer<AsyncResult<JsonObject>>() {
              @Override
              public AsyncResult<JsonObject> answer(InvocationOnMock arg1) throws Throwable {
                ((Handler<AsyncResult<JsonObject>>) arg1.getArgument(1)).handle(asyncResult);
                return null;
              }
            })
        .when(postgresService)
        .executeQuery(any(), any());
    JsonObject jsonObject = read();
    jsonObject.put("options", "count");
    jsonObject.remove(API);
    meteringService.executeReadQuery(
        jsonObject,
        vertxTestContext.succeeding(
            response ->
                vertxTestContext.verify(
                    () -> {
                      assertEquals(SUCCESS, response.getString("title"));
                      vertxTestContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Testing Write Query Successful")
  void writeDataSuccessful(VertxTestContext vertxTestContext) {
    JsonObject request = new JsonObject();
    ZonedDateTime zst = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
    long time = zst.toInstant().toEpochMilli();
    String isoTime = zst.truncatedTo(ChronoUnit.SECONDS).toString();
    request.put(EPOCH_TIME, time);
    request.put(ISO_TIME, isoTime);
    request.put(USER_ID, "15c7506f-c800-48d6-adeb-0542b03947c6");
    request.put(ID, "15c7506f-c800-48d6-adeb-0542b03947c6/integration-test-alias/");
    request.put(API, "/ngsi-ld/v1/subscription");
    request.put(RESPONSE_SIZE, 12);
    request.put(PROVIDER_ID, "dummy");
    DatabaseService postgresService = mock(DatabaseService.class);
    AsyncResult<JsonObject> asyncResult = mock(AsyncResult.class);
    MeteringServiceImpl.rmqService = mock(DatabrokerServiceOld.class);

    when(asyncResult.succeeded()).thenReturn(true);
    doAnswer(
            new Answer<AsyncResult<JsonObject>>() {
              @Override
              public AsyncResult<JsonObject> answer(InvocationOnMock arg0) throws Throwable {
                ((Handler<AsyncResult<JsonObject>>) arg0.getArgument(3)).handle(asyncResult);
                return null;
              }
            })
        .when(MeteringServiceImpl.rmqService)
        .publishMessage(any(), anyString(), anyString(), any());

    meteringService.publishMeteringData(
        request,
        handler -> {
          if (handler.succeeded()) {
            vertxTestContext.completeNow();
          } else {
            vertxTestContext.failNow("Failed");
          }
        });
  }

  @Test
  @DisplayName("Testing Write Query Failure")
  void writeDataFailure(VertxTestContext vertxTestContext) {
    JsonObject request = new JsonObject();
    ZonedDateTime zst = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
    long time = zst.toInstant().toEpochMilli();
    String isoTime = zst.truncatedTo(ChronoUnit.SECONDS).toString();
    request.put(EPOCH_TIME, time);
    request.put(ISO_TIME, isoTime);
    request.put(USER_ID, "15c7506f-c800-48d6-adeb-0542b03947c6");
    request.put(ID, "15c7506f-c800-48d6-adeb-0542b03947c6/integration-test-alias/");
    request.put(API, "/ngsi-ld/v1/subscription");
    request.put(RESPONSE_SIZE, 12);
    request.put(PROVIDER_ID, "dummy");

    RabbitMQClient rabbitMQClient = mock(RabbitMQClient.class);
    AsyncResult<JsonObject> asyncResult = mock(AsyncResult.class);

    meteringService.publishMeteringData(
        request,
        handler -> {
          if (handler.failed()) {
            vertxTestContext.completeNow();
          } else {
            vertxTestContext.failNow("Failed");
          }
        });
  }

  @Test
  public void testOverallMethodAdmin(VertxTestContext vertxTestContext) {
    AsyncResult<JsonObject> asyncResult = mock(AsyncResult.class);
    postgresService = mock(DatabaseService.class);
    JsonObject json = new JsonObject().put("role", "admin");
    JsonObject expected = new JsonObject().put(SUCCESS, "count return");

    when(asyncResult.succeeded()).thenReturn(true);
    when(asyncResult.result()).thenReturn(expected);

    Mockito.doAnswer(
            new Answer<AsyncResult<JsonObject>>() {
              @Override
              public AsyncResult<JsonObject> answer(InvocationOnMock arg1) throws Throwable {
                ((Handler<AsyncResult<JsonObject>>) arg1.getArgument(1)).handle(asyncResult);
                return null;
              }
            })
        .when(postgresService)
        .executeQuery(any(), any());

    meteringService.monthlyOverview(
        json,
        vertxTestContext.succeeding(
            response ->
                vertxTestContext.verify(
                    () -> {
                      assertEquals("count return", response.getString(SUCCESS));
                      vertxTestContext.completeNow();
                    })));
  }

  @Test
  public void testOverallMethodConsumer(VertxTestContext vertxTestContext) {
    AsyncResult<JsonObject> asyncResult = mock(AsyncResult.class);
    postgresService = mock(DatabaseService.class);
    JsonObject json = new JsonObject().put("role", "Consumer");
    json.put(IID, "15c7506f-c800-48d6-adeb-0542b03947c6/integration-test-alias/");
    json.put(USER_ID, "15c7506f-c800-48d6-adeb-0542b03947c6");
    JsonObject expected = new JsonObject().put(SUCCESS, "count return");

    JsonObject providerJson =
        new JsonObject()
            .put("provider", "8b95ab80-2aaf-4636-a65e-7f2563d0d371")
            .put("id", "5b7556b5-0779-4c47-9cf2-3f209779aa22")
            .put("resourceGroup", "dummy_resource");

    when(asyncResult.succeeded()).thenReturn(true);
    when(asyncResult.result()).thenReturn(expected);

    Mockito.doAnswer(
            new Answer<AsyncResult<JsonObject>>() {
              @Override
              public AsyncResult<JsonObject> answer(InvocationOnMock arg1) throws Throwable {
                ((Handler<AsyncResult<JsonObject>>) arg1.getArgument(1)).handle(asyncResult);
                return null;
              }
            })
        .when(postgresService)
        .executeQuery(any(), any());

    meteringService.monthlyOverview(
        json,
        vertxTestContext.succeeding(
            response ->
                vertxTestContext.verify(
                    () -> {
                      assertEquals("count return", response.getString(SUCCESS));
                      vertxTestContext.completeNow();
                    })));
  }

  @Test
  public void testOverallMethodProvider(VertxTestContext vertxTestContext) {
    AsyncResult<JsonObject> asyncResult = mock(AsyncResult.class);
    postgresService = mock(DatabaseService.class);
    JsonObject json = new JsonObject().put("role", "Provider");
    json.put(IID, "15c7506f-c800-48d6-adeb-0542b03947c6/integration-test-alias/");
    json.put(USER_ID, "15c7506f-c800-48d6-adeb-0542b03947c6");

    JsonObject expected = new JsonObject().put(SUCCESS, "count return");
    JsonObject providerJson =
        new JsonObject()
            .put("provider", "8b95ab80-2aaf-4636-a65e-7f2563d0d371")
            .put("id", "5b7556b5-0779-4c47-9cf2-3f209779aa22")
            .put("resourceGroup", "dummy_resource");

    when(cacheService.get(any())).thenReturn(Future.succeededFuture(providerJson));

    when(asyncResult.succeeded()).thenReturn(true);
    when(asyncResult.result()).thenReturn(expected);

    Mockito.doAnswer(
            new Answer<AsyncResult<JsonObject>>() {
              @Override
              public AsyncResult<JsonObject> answer(InvocationOnMock arg1) throws Throwable {
                ((Handler<AsyncResult<JsonObject>>) arg1.getArgument(1)).handle(asyncResult);
                return null;
              }
            })
        .when(postgresService)
        .executeQuery(any(), any());

    meteringService.monthlyOverview(
        json,
        vertxTestContext.succeeding(
            response ->
                vertxTestContext.verify(
                    () -> {
                      assertEquals("count return", response.getString(SUCCESS));
                      vertxTestContext.completeNow();
                    })));
  }

  @Test
  public void testOverallMethodAdminWithETST(VertxTestContext vertxTestContext) {
    AsyncResult<JsonObject> asyncResult = mock(AsyncResult.class);
    postgresService = mock(DatabaseService.class);
    JsonObject json = new JsonObject().put("role", "admin");
    json.put(STARTT, "2022-11-20T00:00:00Z");
    json.put(ENDT, "2022-12-20T00:00:00Z");
    JsonObject expected = new JsonObject().put(SUCCESS, "count return");

    when(asyncResult.succeeded()).thenReturn(true);
    when(asyncResult.result()).thenReturn(expected);

    Mockito.doAnswer(
            new Answer<AsyncResult<JsonObject>>() {
              @Override
              public AsyncResult<JsonObject> answer(InvocationOnMock arg1) throws Throwable {
                ((Handler<AsyncResult<JsonObject>>) arg1.getArgument(1)).handle(asyncResult);
                return null;
              }
            })
        .when(postgresService)
        .executeQuery(any(), any());

    meteringService.monthlyOverview(
        json,
        vertxTestContext.succeeding(
            response ->
                vertxTestContext.verify(
                    () -> {
                      assertEquals("count return", response.getString(SUCCESS));
                      vertxTestContext.completeNow();
                    })));
  }

  @Test
  public void testOverallMethodConsumerWithSTET(VertxTestContext vertxTestContext) {
    AsyncResult<JsonObject> asyncResult = mock(AsyncResult.class);
    postgresService = mock(DatabaseService.class);
    JsonObject json = new JsonObject().put("role", "Consumer");
    json.put(IID, "15c7506f-c800-48d6-adeb-0542b03947c6/integration-test-alias/");
    json.put(USER_ID, "15c7506f-c800-48d6-adeb-0542b03947c6");
    json.put(STARTT, "2022-11-20T00:00:00Z");
    json.put(ENDT, "2022-12-20T00:00:00Z");
    JsonObject expected = new JsonObject().put(SUCCESS, "count return");
    JsonObject providerJson =
        new JsonObject()
            .put("provider", "8b95ab80-2aaf-4636-a65e-7f2563d0d371")
            .put("id", "5b7556b5-0779-4c47-9cf2-3f209779aa22")
            .put("resourceGroup", "dummy_resource");

    when(asyncResult.succeeded()).thenReturn(true);
    when(asyncResult.result()).thenReturn(expected);

    Mockito.doAnswer(
            new Answer<AsyncResult<JsonObject>>() {
              @Override
              public AsyncResult<JsonObject> answer(InvocationOnMock arg1) throws Throwable {
                ((Handler<AsyncResult<JsonObject>>) arg1.getArgument(1)).handle(asyncResult);
                return null;
              }
            })
        .when(postgresService)
        .executeQuery(any(), any());

    meteringService.monthlyOverview(
        json,
        vertxTestContext.succeeding(
            response ->
                vertxTestContext.verify(
                    () -> {
                      assertEquals("count return", response.getString(SUCCESS));
                      vertxTestContext.completeNow();
                    })));
  }

  @Test
  public void testOverallMethodProviderWithSTET(VertxTestContext vertxTestContext) {
    AsyncResult<JsonObject> asyncResult = mock(AsyncResult.class);
    postgresService = mock(DatabaseService.class);
    JsonObject json = new JsonObject().put("role", "Provider");
    json.put(IID, "15c7506f-c800-48d6-adeb-0542b03947c6/integration-test-alias/");
    json.put(USER_ID, "15c7506f-c800-48d6-adeb-0542b03947c6");
    json.put(STARTT, "2022-11-20T00:00:00Z");
    json.put(ENDT, "2022-12-20T00:00:00Z");
    JsonObject expected = new JsonObject().put(SUCCESS, "count return");
    JsonObject providerJson =
        new JsonObject()
            .put("provider", "8b95ab80-2aaf-4636-a65e-7f2563d0d371")
            .put("id", "5b7556b5-0779-4c47-9cf2-3f209779aa22")
            .put("resourceGroup", "dummy_resource");

    when(cacheService.get(any())).thenReturn(Future.succeededFuture(providerJson));

    when(asyncResult.succeeded()).thenReturn(true);
    when(asyncResult.result()).thenReturn(expected);

    Mockito.doAnswer(
            new Answer<AsyncResult<JsonObject>>() {
              @Override
              public AsyncResult<JsonObject> answer(InvocationOnMock arg1) throws Throwable {
                ((Handler<AsyncResult<JsonObject>>) arg1.getArgument(1)).handle(asyncResult);
                return null;
              }
            })
        .when(postgresService)
        .executeQuery(any(), any());

    meteringService.monthlyOverview(
        json,
        vertxTestContext.succeeding(
            response ->
                vertxTestContext.verify(
                    () -> {
                      assertEquals("count return", response.getString(SUCCESS));
                      vertxTestContext.completeNow();
                    })));
  }

  @Test
  public void testForSummaryApi(VertxTestContext vertxTestContext) {
    AsyncResult<JsonObject> asyncResult = mock(AsyncResult.class);
    postgresService = mock(DatabaseService.class);
    JsonObject json = new JsonObject().put("role", "consumer");
    json.put(IID, "5b7556b5-0779-4c47-9cf2-3f209779aa22");
    json.put(USER_ID, "15c7506f-c800-48d6-adeb-0542b03947c6");

    JsonObject jsonObject =
        new JsonObject().put("resourceid", "5b7556b5-0779-4c47-9cf2-3f209779aa22").put("count", 5);
    JsonArray jsonArray = new JsonArray().add(jsonObject);

    JsonObject postgresJson =
        new JsonObject()
            .put("type", "urn:dx:rs:success")
            .put("title", "Success")
            .put("result", jsonArray);

    JsonObject outputFormat =
        new JsonObject().put("resourceid", "5b7556b5-0779-4c47-9cf2-3f209779aa22");
    JsonArray outputArray = new JsonArray().add(outputFormat);

    JsonObject providerJson =
        new JsonObject()
            .put("provider", "8b95ab80-2aaf-4636-a65e-7f2563d0d371")
            .put("id", "5b7556b5-0779-4c47-9cf2-3f209779aa22")
            .put("resourceGroup", "dummy_resource");

    MeteringServiceImpl spyMeteringService = Mockito.spy(meteringService);

    when(asyncResult.succeeded()).thenReturn(true);
    when(asyncResult.result()).thenReturn(postgresJson);
    Mockito.lenient()
        .doAnswer(
            new Answer<AsyncResult<JsonObject>>() {
              @Override
              public AsyncResult<JsonObject> answer(InvocationOnMock arg1) throws Throwable {
                ((Handler<AsyncResult<JsonObject>>) arg1.getArgument(1)).handle(asyncResult);
                return null;
              }
            })
        .when(postgresService)
        .executeQuery(any(), any());

    doAnswer(Answer -> Future.succeededFuture(outputArray))
        .when(spyMeteringService)
        .cacheCall(any());

    spyMeteringService.summaryOverview(
        json,
        vertxTestContext.succeeding(
            response ->
                vertxTestContext.verify(
                    () -> {
                      assertEquals(response.getString("type"), "urn:dx:dm:Success");
                      assertEquals(response.getString("title"), "Success");
                      vertxTestContext.completeNow();
                    })));
    vertxTestContext.completeNow();
  }

  @Test
  public void testForSummaryApiWithSTET(VertxTestContext vertxTestContext) {
    AsyncResult<JsonObject> asyncResult = mock(AsyncResult.class);
    postgresService = mock(DatabaseService.class);
    JsonObject json = new JsonObject().put("role", "consumer");
    json.put(IID, "15c7506f-c800-48d6-adeb-0542b03947c6/integration-test-alias/");
    json.put(USER_ID, "15c7506f-c800-48d6-adeb-0542b03947c6");
    json.put(STARTT, "2022-11-20T00:00:00Z");
    json.put(ENDT, "2022-12-20T00:00:00Z");

    JsonObject jsonObject =
        new JsonObject().put("resourceid", "5b7556b5-0779-4c47-9cf2-3f209779aa22").put("count", 5);
    JsonArray jsonArray = new JsonArray().add(jsonObject);

    JsonObject postgresJson =
        new JsonObject()
            .put("type", "urn:dx:rs:success")
            .put("title", "Success")
            .put("result", jsonArray);

    JsonObject cacheInteraction = new JsonObject();
    cacheInteraction.put("type", CacheType.CATALOGUE_CACHE);
    cacheInteraction.put("key", jsonArray.getJsonObject(0).getString("resourceid"));

    JsonObject outputFormat =
        new JsonObject().put("resourceid", "5b7556b5-0779-4c47-9cf2-3f209779aa22");
    JsonArray outputArray = new JsonArray().add(outputFormat);
    JsonObject providerJson =
        new JsonObject()
            .put("provider", "8b95ab80-2aaf-4636-a65e-7f2563d0d371")
            .put("id", "5b7556b5-0779-4c47-9cf2-3f209779aa22")
            .put("resourceGroup", "dummy_resource");

    MeteringServiceImpl spyMeteringService = Mockito.spy(meteringService);

    when(asyncResult.succeeded()).thenReturn(true);
    when(asyncResult.result()).thenReturn(postgresJson);
    Mockito.doAnswer(
            new Answer<AsyncResult<JsonObject>>() {
              @Override
              public AsyncResult<JsonObject> answer(InvocationOnMock arg1) throws Throwable {
                ((Handler<AsyncResult<JsonObject>>) arg1.getArgument(1)).handle(asyncResult);
                return null;
              }
            })
        .when(postgresService)
        .executeQuery(any(), any());

    doAnswer(Answer -> Future.succeededFuture(outputArray))
        .when(spyMeteringService)
        .cacheCall(any());

    spyMeteringService.summaryOverview(
        json,
        vertxTestContext.succeeding(
            response ->
                vertxTestContext.verify(
                    () -> {
                      assertEquals(response.getString("type"), "urn:dx:dm:Success");
                      assertEquals(response.getString("title"), "Success");
                      vertxTestContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("summary api where count is zero")
  public void testForSummaryApiZeroValues(VertxTestContext vertxTestContext) {

    JsonObject responseJson = new JsonObject().put(SUCCESS, "Success");
    AsyncResult<JsonObject> asyncResult = mock(AsyncResult.class);
    postgresService = mock(DatabaseService.class);
    JsonObject json = mock(JsonObject.class);
    JsonArray jsonArray = mock(JsonArray.class);
    JsonObject providerJson =
        new JsonObject()
            .put("provider", "8b95ab80-2aaf-4636-a65e-7f2563d0d371")
            .put("id", "5b7556b5-0779-4c47-9cf2-3f209779aa22")
            .put("resourceGroup", "dummy_resource");

    when(cacheService.get(any())).thenReturn(Future.succeededFuture(providerJson));

    when(asyncResult.succeeded()).thenReturn(true);
    when(asyncResult.result()).thenReturn(json);

    when(json.getJsonArray(anyString())).thenReturn(jsonArray);
    when(jsonArray.size()).thenReturn(0);

    Mockito.doAnswer(
            new Answer<AsyncResult<JsonObject>>() {
              @Override
              public AsyncResult<JsonObject> answer(InvocationOnMock arg1) throws Throwable {
                ((Handler<AsyncResult<JsonObject>>) arg1.getArgument(1)).handle(asyncResult);
                return null;
              }
            })
        .when(postgresService)
        .executeQuery(any(), any());
    JsonObject jsonObject = readProviderRequest();

    meteringService.summaryOverview(
        jsonObject,
        vertxTestContext.succeeding(
            response ->
                vertxTestContext.verify(
                    () -> {
                      assertNotNull(response.getString("title"));
                      vertxTestContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("summary api for bad request")
  public void testForSummaryApiFail(VertxTestContext vertxTestContext) {
    AsyncResult<JsonObject> asyncResult = mock(AsyncResult.class);
    Future future = mock(Future.class);
    Throwable throwable = mock(Throwable.class);
    postgresService = mock(DatabaseService.class);
    JsonObject json = new JsonObject().put("role", "consumer");
    json.put(IID, "15c7506f-c800-48d6-adeb-0542b03947c6/integration-test-alias/");
    json.put(USER_ID, "15c7506f-c800-48d6-adeb-0542b03947c6");
    json.put(STARTT, "2022-11-20T00:00:00Z");

    lenient().when(future.succeeded()).thenReturn(false);
    JsonObject providerJson =
        new JsonObject()
            .put("provider", "8b95ab80-2aaf-4636-a65e-7f2563d0d371")
            .put("id", "5b7556b5-0779-4c47-9cf2-3f209779aa22")
            .put("resourceGroup", "dummy_resource");

    meteringService.summaryOverview(
        json,
        handler -> {
          if (handler.failed()) {
            assertEquals(handler.cause().getMessage(), "Bad Request");
            vertxTestContext.completeNow();
          } else {
            vertxTestContext.failNow("failed");
          }
        });
  }

  @Test
  @DisplayName("Overview api for bad request")
  public void testForOverviewApiFail(VertxTestContext vertxTestContext) {
    AsyncResult<JsonObject> asyncResult = mock(AsyncResult.class);
    Future future = mock(Future.class);
    Throwable throwable = mock(Throwable.class);
    postgresService = mock(DatabaseService.class);
    JsonObject json = new JsonObject().put("role", "admin");
    json.put(IID, "15c7506f-c800-48d6-adeb-0542b03947c6/integration-test-alias/");
    json.put(USER_ID, "15c7506f-c800-48d6-adeb-0542b03947c6");
    json.put(STARTT, "2022-11-20T00:00:00Z");
    meteringService.monthlyOverview(
        json,
        handler -> {
          if (handler.failed()) {
            assertEquals(handler.cause().getMessage(), "Bad Request");
            vertxTestContext.completeNow();
          } else {
            vertxTestContext.failNow("failed");
          }
        });
  }

  @Test
  @DisplayName("Testing read query with given Time Interval")
  void readFromValidTimeIntervalWithLimitAndOffSet(VertxTestContext vertxTestContext) {
    JsonObject responseJson = new JsonObject().put(SUCCESS, "Success");
    AsyncResult<JsonObject> asyncResult = mock(AsyncResult.class);
    postgresService = mock(DatabaseService.class);
    JsonObject json = mock(JsonObject.class);
    JsonArray jsonArray = mock(JsonArray.class);

    when(asyncResult.succeeded()).thenReturn(true);
    when(asyncResult.result()).thenReturn(json, responseJson);

    when(json.getJsonArray(anyString())).thenReturn(jsonArray);
    when(jsonArray.getJsonObject(anyInt())).thenReturn(json);
    when(json.getInteger(anyString())).thenReturn(39);

    Mockito.doAnswer(
            new Answer<AsyncResult<JsonObject>>() {
              @Override
              public AsyncResult<JsonObject> answer(InvocationOnMock arg1) throws Throwable {
                ((Handler<AsyncResult<JsonObject>>) arg1.getArgument(1)).handle(asyncResult);
                return null;
              }
            })
        .when(postgresService)
        .executeQuery(any(), any());

    JsonObject request = readConsumerRequest();
    request.put("limit", "110").put("offset", "0");

    meteringService.executeReadQuery(
        request,
        vertxTestContext.succeeding(
            response ->
                vertxTestContext.verify(
                    () -> {
                      LOGGER.info(response);
                      assertNotNull(response.getString(SUCCESS));
                      vertxTestContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Testing read query with given Time Interval")
  void readFromValidTimeIntervalWithLimitAndOffSetProvider(VertxTestContext vertxTestContext) {
    JsonObject responseJson = new JsonObject().put(SUCCESS, "Success");
    AsyncResult<JsonObject> asyncResult = mock(AsyncResult.class);
    postgresService = mock(DatabaseService.class);
    JsonObject json = mock(JsonObject.class);
    JsonArray jsonArray = mock(JsonArray.class);

    when(asyncResult.succeeded()).thenReturn(true);
    when(asyncResult.result()).thenReturn(json, responseJson);

    when(json.getJsonArray(anyString())).thenReturn(jsonArray);
    when(jsonArray.getJsonObject(anyInt())).thenReturn(json);
    when(json.getInteger(anyString())).thenReturn(39);

    Mockito.doAnswer(
            new Answer<AsyncResult<JsonObject>>() {
              @Override
              public AsyncResult<JsonObject> answer(InvocationOnMock arg1) throws Throwable {
                ((Handler<AsyncResult<JsonObject>>) arg1.getArgument(1)).handle(asyncResult);
                return null;
              }
            })
        .when(postgresService)
        .executeQuery(any(), any());

    JsonObject request = read();
    request.put("limit", "110").put("offset", "0");

    meteringService.executeReadQuery(
        request,
        vertxTestContext.succeeding(
            response ->
                vertxTestContext.verify(
                    () -> {
                      assertNotNull(response.getString(SUCCESS));
                      vertxTestContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("Summary Api for admin role")
  public void testForSummaryApiWithSTETAdmin(VertxTestContext vertxTestContext) {
    AsyncResult<JsonObject> asyncResult = mock(AsyncResult.class);
    postgresService = mock(DatabaseService.class);
    JsonObject json = new JsonObject().put("role", "admin");
    json.put(IID, "15c7506f-c800-48d6-adeb-0542b03947c6/integration-test-alias/");
    json.put(USER_ID, "15c7506f-c800-48d6-adeb-0542b03947c6");
    json.put(STARTT, "2022-11-20T00:00:00Z");
    json.put(ENDT, "2022-12-20T00:00:00Z");

    JsonObject jsonObject =
        new JsonObject().put("resourceid", "5b7556b5-0779-4c47-9cf2-3f209779aa22").put("count", 5);
    JsonArray jsonArray = new JsonArray().add(jsonObject);

    JsonObject postgresJson =
        new JsonObject()
            .put("type", "urn:dx:rs:success")
            .put("title", "Success")
            .put("result", jsonArray);

    JsonObject cacheInteraction = new JsonObject();
    cacheInteraction.put("type", CacheType.CATALOGUE_CACHE);
    cacheInteraction.put("key", jsonArray.getJsonObject(0).getString("resourceid"));

    JsonObject outputFormat =
        new JsonObject().put("resourceid", "5b7556b5-0779-4c47-9cf2-3f209779aa22");
    JsonArray outputArray = new JsonArray().add(outputFormat);
    MeteringServiceImpl spyMeteringService = Mockito.spy(meteringService);

    when(asyncResult.succeeded()).thenReturn(true);
    when(asyncResult.result()).thenReturn(postgresJson);
    Mockito.doAnswer(
            new Answer<AsyncResult<JsonObject>>() {
              @Override
              public AsyncResult<JsonObject> answer(InvocationOnMock arg1) throws Throwable {
                ((Handler<AsyncResult<JsonObject>>) arg1.getArgument(1)).handle(asyncResult);
                return null;
              }
            })
        .when(postgresService)
        .executeQuery(any(), any());

    doAnswer(Answer -> Future.succeededFuture(outputArray))
        .when(spyMeteringService)
        .cacheCall(any());

    spyMeteringService.summaryOverview(
        json,
        vertxTestContext.succeeding(
            response ->
                vertxTestContext.verify(
                    () -> {
                      assertEquals(response.getString("type"), "urn:dx:dm:Success");
                      assertEquals(response.getString("title"), "Success");
                      vertxTestContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("summary api where count is zero for Admin")
  public void testForSummaryApiZeroValuesAdmin(VertxTestContext vertxTestContext) {

    JsonObject responseJson = new JsonObject().put(SUCCESS, "Success");
    AsyncResult<JsonObject> asyncResult = mock(AsyncResult.class);
    postgresService = mock(DatabaseService.class);
    JsonObject json = mock(JsonObject.class);
    JsonArray jsonArray = mock(JsonArray.class);

    when(asyncResult.succeeded()).thenReturn(true);
    when(asyncResult.result()).thenReturn(json);

    when(json.getJsonArray(anyString())).thenReturn(jsonArray);
    when(jsonArray.size()).thenReturn(0);

    Mockito.doAnswer(
            new Answer<AsyncResult<JsonObject>>() {
              @Override
              public AsyncResult<JsonObject> answer(InvocationOnMock arg1) throws Throwable {
                ((Handler<AsyncResult<JsonObject>>) arg1.getArgument(1)).handle(asyncResult);
                return null;
              }
            })
        .when(postgresService)
        .executeQuery(any(), any());
    JsonObject json1 = new JsonObject().put("role", "admin");
    json.put(IID, "15c7506f-c800-48d6-adeb-0542b03947c6/integration-test-alias/");
    json.put(USER_ID, "15c7506f-c800-48d6-adeb-0542b03947c6");
    json.put(STARTT, "2022-11-20T00:00:00Z");
    json.put(ENDT, "2022-12-20T00:00:00Z");

    meteringService.summaryOverview(
        json1,
        vertxTestContext.succeeding(
            response ->
                vertxTestContext.verify(
                    () -> {
                      assertNotNull(response.getString("title"));
                      vertxTestContext.completeNow();
                    })));
  }

  @Test
  @DisplayName("summary api Validation fail Admin")
  public void testForSummaryApiZeroValuesAdmin2(VertxTestContext vertxTestContext) {

    JsonObject responseJson = new JsonObject().put(SUCCESS, "Success");
    AsyncResult<JsonObject> asyncResult = mock(AsyncResult.class);
    postgresService = mock(DatabaseService.class);
    JsonObject json = mock(JsonObject.class);
    JsonArray jsonArray = mock(JsonArray.class);

    JsonObject json1 = new JsonObject().put("role", "admin");
    json1.put(IID, "15c7506f-c800-48d6-adeb-0542b03947c6/integration-test-alias/");
    json1.put(USER_ID, "15c7506f-c800-48d6-adeb-0542b03947c6");
    json1.put(STARTT, "2022-11-20T");
    json1.put(ENDT, "2022-12-20T00:00:00Z");

    meteringService.summaryOverview(
        json1,
        handler -> {
          if (handler.failed()) {
            assertEquals(
                handler.cause().getMessage(),
                "{\"type\":400,\"title\":\"urn:dx:rs:badRequest\",\"detail\":\"invalid date-time\"}");
            vertxTestContext.completeNow();
          }
        });
  }

  @Test
  @DisplayName("overview api Validation fail Admin")
  public void testForOverivewApiZeroValuesAdmin2(VertxTestContext vertxTestContext) {

    JsonObject responseJson = new JsonObject().put(SUCCESS, "Success");
    AsyncResult<JsonObject> asyncResult = mock(AsyncResult.class);
    postgresService = mock(DatabaseService.class);
    JsonObject json = mock(JsonObject.class);
    JsonArray jsonArray = mock(JsonArray.class);

    JsonObject json1 = new JsonObject().put("role", "admin");
    json1.put(IID, "15c7506f-c800-48d6-adeb-0542b03947c6/integration-test-alias/");
    json1.put(USER_ID, "15c7506f-c800-48d6-adeb-0542b03947c6");
    json1.put(STARTT, "2022-11-20T");
    json1.put(ENDT, "2022-12-20T00:00:00Z");

    meteringService.monthlyOverview(
        json1,
        handler -> {
          if (handler.failed()) {
            assertEquals(
                handler.cause().getMessage(),
                "{\"type\":400,\"title\":\"urn:dx:rs:badRequest\",\"detail\":\"invalid date-time\"}");
            vertxTestContext.completeNow();
          }
        });
  }

  @Test
  @DisplayName("Summary Api for admin role without spying")
  public void testForSummaryApiWithSTETAdmin2(VertxTestContext vertxTestContext) {
    AsyncResult<JsonObject> asyncResult = mock(AsyncResult.class);
    postgresService = mock(DatabaseService.class);
    JsonObject json =
        new JsonObject()
            .put("role", "admin")
            .put("resourceid", "5b7556b5-0779-4c47-9cf2-3f209779aa22");
    json.put(IID, "15c7506f-c800-48d6-adeb-0542b03947c6");
    json.put(USER_ID, "15c7506f-c800-48d6-adeb-0542b03947c6");
    json.put(STARTT, "2022-11-20T00:00:00Z");
    json.put(ENDT, "2022-12-20T00:00:00Z");

    JsonObject jsonObject =
        new JsonObject().put("resourceid", "5b7556b5-0779-4c47-9cf2-3f209779aa22").put("count", 1);
    JsonArray jsonArray = new JsonArray().add(jsonObject);

    JsonObject postgresJson =
        new JsonObject()
            .put("type", "urn:dx:rs:success")
            .put("title", "Success")
            .put("result", jsonArray);

    when(asyncResult.succeeded()).thenReturn(true);
    when(asyncResult.result()).thenReturn(postgresJson);

    Mockito.doAnswer(
            new Answer<AsyncResult<JsonObject>>() {
              @Override
              public AsyncResult<JsonObject> answer(InvocationOnMock arg1) throws Throwable {
                ((Handler<AsyncResult<JsonObject>>) arg1.getArgument(1)).handle(asyncResult);
                return null;
              }
            })
        .when(postgresService)
        .executeQuery(any(), any());

    when(cacheService.get(any())).thenReturn(Future.succeededFuture(postgresJson));

    meteringService.summaryOverview(
        json,
        handler -> {
          if (handler.succeeded()) {
            assertEquals(handler.result().getString("type"), "urn:dx:dm:Success");
            vertxTestContext.completeNow();
          }
        });
  }
}
