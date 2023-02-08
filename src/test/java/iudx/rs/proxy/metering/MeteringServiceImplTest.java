package iudx.rs.proxy.metering;

import static iudx.rs.proxy.metering.util.Constants.API;
import static iudx.rs.proxy.metering.util.Constants.CONSUMER_ID;
import static iudx.rs.proxy.metering.util.Constants.DATABASE_IP;
import static iudx.rs.proxy.metering.util.Constants.DATABASE_NAME;
import static iudx.rs.proxy.metering.util.Constants.DATABASE_PASSWORD;
import static iudx.rs.proxy.metering.util.Constants.DATABASE_PORT;
import static iudx.rs.proxy.metering.util.Constants.DATABASE_TABLE_NAME;
import static iudx.rs.proxy.metering.util.Constants.DATABASE_USERNAME;
import static iudx.rs.proxy.metering.util.Constants.DETAIL;
import static iudx.rs.proxy.metering.util.Constants.DURING;
import static iudx.rs.proxy.metering.util.Constants.ENDPOINT;
import static iudx.rs.proxy.metering.util.Constants.END_TIME;
import static iudx.rs.proxy.metering.util.Constants.ID;
import static iudx.rs.proxy.metering.util.Constants.IID;
import static iudx.rs.proxy.metering.util.Constants.INVALID_DATE_DIFFERENCE;
import static iudx.rs.proxy.metering.util.Constants.INVALID_DATE_TIME;
import static iudx.rs.proxy.metering.util.Constants.INVALID_PROVIDER_ID;
import static iudx.rs.proxy.metering.util.Constants.INVALID_PROVIDER_REQUIRED;
import static iudx.rs.proxy.metering.util.Constants.POOL_SIZE;
import static iudx.rs.proxy.metering.util.Constants.PROVIDER_ID;
import static iudx.rs.proxy.metering.util.Constants.RESOURCE_ID;
import static iudx.rs.proxy.metering.util.Constants.RESPONSE_LIMIT_EXCEED;
import static iudx.rs.proxy.metering.util.Constants.RESPONSE_SIZE;
import static iudx.rs.proxy.metering.util.Constants.START_TIME;
import static iudx.rs.proxy.metering.util.Constants.SUCCESS;
import static iudx.rs.proxy.metering.util.Constants.TIME_NOT_FOUND;
import static iudx.rs.proxy.metering.util.Constants.TIME_RELATION;
import static iudx.rs.proxy.metering.util.Constants.TIME_RELATION_NOT_FOUND;
import static iudx.rs.proxy.metering.util.Constants.TITLE;
import static iudx.rs.proxy.metering.util.Constants.USERID_NOT_FOUND;
import static iudx.rs.proxy.metering.util.Constants.USER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
<<<<<<< refs/remotes/origin/update-files
=======
import io.vertx.rabbitmq.RabbitMQClient;
import iudx.rs.proxy.common.Api;
>>>>>>> local
import iudx.rs.proxy.configuration.Configuration;
import java.util.UUID;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
@Disabled
@ExtendWith(VertxExtension.class)
class MeteringServiceImplTest {

  private static final Logger LOGGER = LogManager.getLogger(MeteringServiceImplTest.class);
  public static String userId;
  public static String id;
  private static MeteringService meteringService;
  private static Vertx vertxObj;
  private static String databaseIP;
  private static int databasePort;
  private static String databaseName;
  private static String databaseUserName;
  private static String databasePassword;
  private static int databasePoolSize;
  private static String databaseTableName;
  private static Configuration config;

  @BeforeEach
  @DisplayName("Deploying Verticle")
  public void startVertex(Vertx vertx, VertxTestContext vertxTestContext) {
    vertxObj = vertx;
    config = new Configuration();
    JsonObject dbConfig = config.configLoader(4, vertx);
    databaseIP = dbConfig.getString(DATABASE_IP);
    databasePort = dbConfig.getInteger(DATABASE_PORT);
    databaseName = dbConfig.getString(DATABASE_NAME);
    databaseUserName = dbConfig.getString(DATABASE_USERNAME);
    databasePassword = dbConfig.getString(DATABASE_PASSWORD);
    databaseTableName = dbConfig.getString(DATABASE_TABLE_NAME);
    databasePoolSize = dbConfig.getInteger(POOL_SIZE);
    meteringService = new MeteringServiceImpl(dbConfig, vertxObj, Api.getInstance("/ngsi-ld/v1"));
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
    jsonObject.put(RESOURCE_ID,
        "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/pune-env-flood/FWR055");
    jsonObject.put(START_TIME, "2022-03-20T05:30:00+05:30[Asia/Kolkata]");
    jsonObject.put(END_TIME, "2022-03-30T02:00:00+05:30[Asia/Kolkata]");
    jsonObject.put(TIME_RELATION, DURING);
    jsonObject.put(API, "/ngsi-ld/v1/entities");
    jsonObject.put(PROVIDER_ID, "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86");
    jsonObject.put(CONSUMER_ID, "844e251b-574b-46e6-9247-f76f1f70a637");
    jsonObject.put(IID,
        "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/pune-env-flood/FWR055");
    jsonObject.put(ENDPOINT, "/ngsi-ld/v1/provider/audit");
    return jsonObject;
  }


  @Test
  @DisplayName("Testing read query with invalid time interval")
  void readFromInvalidTimeInterval(VertxTestContext testContext) {
    JsonObject request = readConsumerRequest();
<<<<<<< refs/remotes/origin/update-files
=======

>>>>>>> local
    request.put(START_TIME, "2021-11-01T05:30:00+05:30[Asia/Kolkata]");
    request.put(END_TIME, "2021-11-24T02:00:00+05:30[Asia/Kolkata]");
    meteringService.executeReadQuery(
        request,
        testContext.failing(
            response -> testContext.verify(
                () -> {
                  assertEquals(
                      INVALID_DATE_DIFFERENCE,
                      new JsonObject(response.getMessage()).getString(DETAIL));
                  testContext.completeNow();
                })));
  }

<<<<<<< refs/remotes/origin/update-files

=======
    when(asyncResult.succeeded()).thenReturn(true);
    when(asyncResult.result()).thenReturn(json,responseJson);
    when(json.getJsonArray(anyString())).thenReturn(jsonArray);
    when(jsonArray.getJsonObject(anyInt())).thenReturn(json);
    when(json.getInteger(anyString())).thenReturn(39);

    doAnswer(new Answer<AsyncResult<JsonObject>>() {
      @Override
      public AsyncResult<JsonObject> answer(InvocationOnMock arg1) throws Throwable {
        ((Handler<AsyncResult<JsonObject>>) arg1.getArgument(1)).handle(asyncResult);
        return null;
      }
    }).when(MeteringServiceImpl.postgresService).executeQuery(any(), any());

    JsonObject request = read();

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
    JsonObject responseJson= new JsonObject().put(SUCCESS,"Success");
    AsyncResult<JsonObject> asyncResult= mock(AsyncResult.class);
    MeteringServiceImpl.postgresService =mock(DatabaseService.class);
    JsonObject json= mock(JsonObject.class);
    JsonArray jsonArray= mock(JsonArray.class);


    when(asyncResult.succeeded()).thenReturn(true);
    when(asyncResult.result()).thenReturn(json,responseJson);

    when(json.getJsonArray(anyString())).thenReturn(jsonArray);
    when(jsonArray.getJsonObject(anyInt())).thenReturn(json);
    when(json.getInteger(anyString())).thenReturn(0);

    Mockito.doAnswer(new Answer<AsyncResult<JsonObject>>() {
      @Override
      public AsyncResult<JsonObject> answer(InvocationOnMock arg1) throws Throwable {
        ((Handler<AsyncResult<JsonObject>>) arg1.getArgument(1)).handle(asyncResult);
        return null;
      }
    }).when(MeteringServiceImpl.postgresService).executeQuery(any(), any());
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
>>>>>>> local
  @Test
  @DisplayName("Testing read query for missing userId")
  void readForMissingUserId(VertxTestContext vertxTestContext) {
    JsonObject request = readConsumerRequest();
    request.remove(USER_ID);

<<<<<<< refs/remotes/origin/update-files
=======

>>>>>>> local
    meteringService.executeReadQuery(
        request,
        vertxTestContext.failing(
            response -> vertxTestContext.verify(
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
            response -> vertxTestContext.verify(
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
            response -> vertxTestContext.verify(
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
            response -> vertxTestContext.verify(
                () -> {
                  LOGGER.debug(
                      "RESPONSE " + new JsonObject(response.getMessage()).getString(DETAIL));
                  assertEquals(
                      INVALID_DATE_TIME,
                      new JsonObject(response.getMessage()).getString(DETAIL));
                  vertxTestContext.completeNow();
                })));
  }

  @Test
<<<<<<< refs/remotes/origin/update-files
  @DisplayName("Testing read query for given time.")
  void readForGivenTime(VertxTestContext vertxTestContext) {
    JsonObject jsonObject = readConsumerRequest();
    jsonObject.remove(RESOURCE_ID);
    jsonObject.remove(API);
=======
  @DisplayName("Testing read query with missing providerId.")
  void readForMissingProviderId(VertxTestContext vertxTestContext) {
    JsonObject request = readProviderRequest();
    request.remove(PROVIDER_ID);
>>>>>>> local
    meteringService.executeReadQuery(
        jsonObject,
        vertxTestContext.succeeding(
            response -> vertxTestContext.verify(
                () -> {
                  LOGGER.debug("RESPONSE" + response);
                  assertEquals(SUCCESS, response.getString(TITLE));
                  vertxTestContext.completeNow();
                })));
  }


  @Test
  @DisplayName("Testing read query for given time and id.")
  void readForGivenTimeAndId(VertxTestContext vertxTestContext) {
    JsonObject jsonObject = readConsumerRequest();
    jsonObject.remove(API);
    meteringService.executeReadQuery(
        jsonObject,
        vertxTestContext.succeeding(
            response -> vertxTestContext.verify(
                () -> {
                  LOGGER.debug("RESPONSE" + response);
                  assertEquals(SUCCESS, response.getString(TITLE));
                  vertxTestContext.completeNow();
                })));
  }

  @Test
  @DisplayName("Testing read query for given time and api.")
  void readForGivenTimeAndApi(VertxTestContext vertxTestContext) {
    JsonObject jsonObject = readConsumerRequest();
    jsonObject.remove(RESOURCE_ID);

    meteringService.executeReadQuery(
        jsonObject,
        vertxTestContext.succeeding(
            response -> vertxTestContext.verify(
                () -> {
                  LOGGER.debug("RESPONSE" + response);
                  assertEquals(SUCCESS, response.getString(TITLE));
                  vertxTestContext.completeNow();
                })));
  }

  @Test
  @DisplayName("Testing read query for given time,api and resourceId.")
  void readForGivenTimeApiAndID(VertxTestContext vertxTestContext) {
    JsonObject jsonObject = readConsumerRequest();

<<<<<<< refs/remotes/origin/update-files
    meteringService.executeReadQuery(
        jsonObject,
        vertxTestContext.succeeding(
            response -> vertxTestContext.verify(
                () -> {
                  LOGGER.debug("RESPONSE" + response);
                  assertEquals(SUCCESS, response.getString(TITLE));
                  vertxTestContext.completeNow();
                })));
  }
=======

    when(asyncResult.succeeded()).thenReturn(true);
    when(asyncResult.result()).thenReturn(json);
>>>>>>> local


  @Test
  @DisplayName("Testing read query for given time,api and id.")
  void readForGivenTimeApiAndIDEmptyResponse(VertxTestContext vertxTestContext) {
    JsonObject jsonObject = readConsumerRequest();
    jsonObject.put(START_TIME, "2021-11-19T05:30:00+05:30[Asia/Kolkata]");
    jsonObject.put(END_TIME, "2021-11-21T02:00:00+05:30[Asia/Kolkata]");

    meteringService.executeReadQuery(
        jsonObject,
        vertxTestContext.failing(
            response -> vertxTestContext.verify(
                () -> {
                  LOGGER.debug(
                      "RESPONSE " + new JsonObject(response.getMessage()).getString(DETAIL));
                  assertEquals(RESPONSE_LIMIT_EXCEED,
                      new JsonObject(response.getMessage()).getString(DETAIL));
                  vertxTestContext.completeNow();
                })));
  }

<<<<<<< refs/remotes/origin/update-files
  @Test
  @DisplayName("Testing count query for given time,api and id.")
  void countForGivenTimeApiAndIDEmptyResponse(VertxTestContext vertxTestContext) {
    JsonObject jsonObject = readConsumerRequest();
    jsonObject.put(START_TIME, "2021-11-19T05:30:00+05:30[Asia/Kolkata]");
    jsonObject.put(END_TIME, "2021-11-21T02:00:00+05:30[Asia/Kolkata]");
    jsonObject.put("options", "count");
=======
>>>>>>> local

    meteringService.executeReadQuery(
        jsonObject,
        vertxTestContext.succeeding(
            response -> vertxTestContext.verify(
                () -> {
                  assertEquals(SUCCESS, response.getString(TITLE));
                  vertxTestContext.completeNow();
                })));
  }

  @Test
  @DisplayName("Testing Write Query")
  void writeData(VertxTestContext vertxTestContext) {
    JsonObject request = new JsonObject();
    request.put(USER_ID, "15c7506f-c800-48d6-adeb-0542b03947c6");
    request.put(ID, "15c7506f-c800-48d6-adeb-0542b03947c6/integration-test-alias/");
    request.put(API, "/ngsi-ld/v1/subscription");
<<<<<<< refs/remotes/origin/update-files
    request.put(RESPONSE_SIZE, 12);
    meteringService.executeWriteQuery(
        request,
        vertxTestContext.succeeding(
            response -> vertxTestContext.verify(
                () -> {
                  LOGGER.debug("RESPONSE" + response.getString("title"));
                  assertEquals(SUCCESS, response.getString("title"));
                  vertxTestContext.completeNow();
                })));
  }
=======
    request.put(RESPONSE_SIZE,12);
    DatabaseService postgresService = mock(DatabaseService.class);
>>>>>>> local

  @Test
  @DisplayName("Testing read query with missing providerId.")
  void readForMissingProviderId(VertxTestContext vertxTestContext) {
    JsonObject request = readProviderRequest();
    request.remove(PROVIDER_ID);
    meteringService.executeReadQuery(
        request,
        vertxTestContext.failing(
            response -> vertxTestContext.verify(
                () -> {
                  LOGGER.debug(
                      "RESPONSE " + new JsonObject(response.getMessage()).getString(DETAIL));
                  assertEquals(
                      INVALID_PROVIDER_REQUIRED,
                      new JsonObject(response.getMessage()).getString(DETAIL));
                  vertxTestContext.completeNow();
                })));
  }

  @Test
  @DisplayName("Testing read query with invalid providerId.")
  void readForInvalidProviderId(VertxTestContext vertxTestContext) {
    JsonObject request = readProviderRequest();
    request.put(PROVIDER_ID, "15c7506f-c800-48d6-adeb-0542b03947c6/integration-tsst-alias");

<<<<<<< refs/remotes/origin/update-files
    meteringService.executeReadQuery(
        request,
        vertxTestContext.failing(
            response -> vertxTestContext.verify(
                () -> {
                  LOGGER.debug(
                      "RESPONSE " + new JsonObject(response.getMessage()).getString(DETAIL));
                  assertEquals(
                      INVALID_PROVIDER_ID,
                      new JsonObject(response.getMessage()).getString(DETAIL));
                  vertxTestContext.completeNow();
                })));
  }

  @Test
  @DisplayName("Testing count query for given time,api and id.")
  void countForGivenTimeApiIdConsumerProviderID(VertxTestContext vertxTestContext) {
    JsonObject jsonObject = readProviderRequest();
    jsonObject.put("options", "count");

    meteringService.executeReadQuery(
        jsonObject,
        vertxTestContext.succeeding(
            response -> vertxTestContext.verify(
                () -> {
                  LOGGER.debug("RESPONSE" + response);
                  assertEquals(SUCCESS, response.getString(TITLE));
                  vertxTestContext.completeNow();
                })));
  }
=======
>>>>>>> local

  @Test
  @DisplayName("Testing count query for given time,api and providerId.")
  void readForGivenTimeApiAndProviderID(VertxTestContext vertxTestContext) {
    JsonObject jsonObject = readProviderRequest();
    jsonObject.remove(RESOURCE_ID);
    jsonObject.remove(CONSUMER_ID);

    meteringService.executeReadQuery(
        jsonObject,
        vertxTestContext.succeeding(
            response -> vertxTestContext.verify(
                () -> {
                  LOGGER.debug("RESPONSE" + response);
                  assertEquals(SUCCESS, response.getString(TITLE));
                  vertxTestContext.completeNow();
                })));
  }

  @Test
  @DisplayName("Testing read query for given time,api and resourceId where count > 10000")
  void invalidReadForGivenTimeApiAndID(VertxTestContext vertxTestContext) {
    JsonObject jsonObject = readConsumerRequest();
    jsonObject.put(START_TIME, "2022-05-01T14:20:00+05:30[Asia/Kolkata]");
    jsonObject.put(END_TIME, "2022-05-15T14:19:00+05:30[Asia/Kolkata]");
    jsonObject.put(API, "/ngsi-ld/v1/entities");
    jsonObject.put(RESOURCE_ID,
        "iisc.ac.in/89a36273d77dac4cf38114fca1bbe64392547f86/rs.iudx.io/surat-itms-realtime-information/surat-itms-live-eta");

    meteringService.executeReadQuery(
        jsonObject,
        vertxTestContext.failing(
            response ->
                vertxTestContext.verify(
                    () -> {
                      LOGGER.debug("RESPONSE " + response);
                      assertEquals(
                          RESPONSE_LIMIT_EXCEED,
                          new JsonObject(response.getMessage()).getString(DETAIL));
                      vertxTestContext.completeNow();
                    })));

  }
}
