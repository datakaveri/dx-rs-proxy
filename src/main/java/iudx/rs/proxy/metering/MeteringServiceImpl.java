package iudx.rs.proxy.metering;

import static iudx.rs.proxy.apiserver.util.ApiServerConstants.*;
import static iudx.rs.proxy.apiserver.util.ApiServerConstants.TABLE_NAME;
import static iudx.rs.proxy.common.Constants.DB_SERVICE_ADDRESS;
import static iudx.rs.proxy.metering.util.Constants.*;
import static iudx.rs.proxy.metering.util.Constants.IID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgConnectOptions;
import io.vertx.pgclient.PgPool;
import io.vertx.sqlclient.PoolOptions;
import iudx.rs.proxy.cache.CacheService;
import iudx.rs.proxy.cache.cacheImpl.CacheType;
import iudx.rs.proxy.common.Api;
import iudx.rs.proxy.common.Response;
import iudx.rs.proxy.database.DatabaseService;
import iudx.rs.proxy.databroker.DatabrokerServiceOld;
import iudx.rs.proxy.metering.util.DateValidation;
import iudx.rs.proxy.metering.util.ParamsValidation;
import iudx.rs.proxy.metering.util.QueryBuilder;
import iudx.rs.proxy.metering.util.ResponseBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MeteringServiceImpl implements MeteringService {

  private static final Logger LOGGER = LogManager.getLogger(MeteringServiceImpl.class);
  public static DatabrokerServiceOld rmqService;
  public static DatabaseService postgresService;
  private final QueryBuilder queryBuilder = new QueryBuilder();
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final DateValidation dateValidation = new DateValidation();
  private final CacheService cacheService;
  PgConnectOptions connectOptions;
  PoolOptions poolOptions;
  PgPool pool;
  JsonObject validationCheck = new JsonObject();
  int total;
  String queryOverview;
  String summaryOverview;
  JsonArray jsonArray;
  JsonArray resultJsonArray;
  int loopi;
  private ParamsValidation validation;
  private JsonObject query = new JsonObject();
  private String databaseIp;
  private int databasePort;
  private String databaseName;
  private String databaseUserName;
  private String databasePassword;
  private int databasePoolSize;
  private String databaseTableName;
  private ResponseBuilder responseBuilder;

  public MeteringServiceImpl(
      JsonObject propObj, Vertx vertxInstance, Api api, CacheService cacheService) {

    if (propObj != null && !propObj.isEmpty()) {
      databaseIp = propObj.getString(DATABASE_IP);
      databasePort = propObj.getInteger(DATABASE_PORT);
      databaseName = propObj.getString(DATABASE_NAME);
      databaseUserName = propObj.getString(DATABASE_USERNAME);
      databasePassword = propObj.getString(DATABASE_PASSWORD);
      databaseTableName = propObj.getString(DATABASE_TABLE_NAME);
      databasePoolSize = propObj.getInteger(POOL_SIZE);
      validation = new ParamsValidation(api);
    }

    this.connectOptions =
        new PgConnectOptions()
            .setPort(databasePort)
            .setHost(databaseIp)
            .setDatabase(databaseName)
            .setUser(databaseUserName)
            .setPassword(databasePassword)
            .setReconnectAttempts(2)
            .setReconnectInterval(1000);

    this.poolOptions = new PoolOptions().setMaxSize(databasePoolSize);
    this.pool = PgPool.pool(vertxInstance, connectOptions, poolOptions);
   // this.rmqService = DatabrokerService.createProxy(vertxInstance, DATABROKER_SERVICE_ADDRESS);
    if (postgresService == null) {
      postgresService = DatabaseService.createProxy(vertxInstance, DB_SERVICE_ADDRESS);
    }
    this.cacheService = cacheService;
    LOGGER.info("cache service created" + cacheService);
  }

  @Override
  public MeteringService executeReadQuery(
      JsonObject request, Handler<AsyncResult<JsonObject>> handler) {
    LOGGER.trace("Info: Read Query" + request.toString());

    validationCheck = validation.paramsCheck(request);

    if (validationCheck != null && validationCheck.containsKey(ERROR)) {
      responseBuilder =
          new ResponseBuilder(FAILED)
              .setTypeAndTitle(400)
              .setMessage(validationCheck.getString(ERROR));
      handler.handle(Future.failedFuture(responseBuilder.getResponse().toString()));
      return this;
    }
    request.put(TABLE_NAME, databaseTableName);

    String count = request.getString("options");
    if (count == null) {
      countQueryForRead(request, handler);
    } else {
      countQuery(request, handler);
    }

    return this;
  }

  private void countQuery(JsonObject request, Handler<AsyncResult<JsonObject>> handler) {
    query = queryBuilder.buildCountReadQueryFromPg(request);
    Future<JsonObject> resultCountPg = executeQueryDatabaseOperation(query);
    resultCountPg.onComplete(
        countHandler -> {
          if (countHandler.succeeded()) {
            try {
              var countHandle = countHandler.result().getJsonArray("result");
              total = countHandle.getJsonObject(0).getInteger("count");
              if (total == 0) {
                responseBuilder = new ResponseBuilder(FAILED).setTypeAndTitle(204).setCount(0);
                handler.handle(Future.succeededFuture(responseBuilder.getResponse()));

              } else {
                responseBuilder = new ResponseBuilder(SUCCESS).setTypeAndTitle(200).setCount(total);
                handler.handle(Future.succeededFuture(responseBuilder.getResponse()));
              }
            } catch (NullPointerException nullPointerException) {
              LOGGER.debug(nullPointerException.toString());
            }
          }
        });
  }

  private void countQueryForRead(JsonObject request, Handler<AsyncResult<JsonObject>> handler) {
    query = queryBuilder.buildCountReadQueryFromPg(request);
    Future<JsonObject> resultCountPg = executeQueryDatabaseOperation(query);
    resultCountPg.onComplete(
        countHandler -> {
          if (countHandler.succeeded()) {
            try {
              var countHandle = countHandler.result().getJsonArray("result");
              total = countHandle.getJsonObject(0).getInteger("count");
              request.put("totalHits", total);
              if (total == 0) {
                responseBuilder = new ResponseBuilder(FAILED).setTypeAndTitle(204).setCount(0);
                handler.handle(Future.succeededFuture(responseBuilder.getResponse()));
              } else {
                readMethod(request, handler);
              }
            } catch (NullPointerException nullPointerException) {
              LOGGER.debug(nullPointerException.toString());
            }
          }
        });
  }

  private void readMethod(JsonObject request, Handler<AsyncResult<JsonObject>> handler) {
    String limit;
    String offset;
    if (request.getString(LIMITPARAM) == null) {
      limit = "2000";
      request.put(LIMITPARAM, limit);
    } else {
      limit = request.getString(LIMITPARAM);
    }
    if (request.getString(OFFSETPARAM) == null) {
      offset = "0";
      request.put(OFFSETPARAM, offset);
    } else {
      offset = request.getString(OFFSETPARAM);
    }
    query = queryBuilder.buildReadQueryFromPg(request);
    LOGGER.debug("read query = " + query);
    Future<JsonObject> resultsPg = executeQueryDatabaseOperation(query);
    resultsPg.onComplete(
        readHandler -> {
          if (readHandler.succeeded()) {
            LOGGER.info("Read Completed successfully");
            JsonObject resultJsonObject = readHandler.result();
            resultJsonObject.put(LIMITPARAM, limit);
            resultJsonObject.put(OFFSETPARAM, offset);
            resultJsonObject.put("totalHits", request.getLong("totalHits"));
            handler.handle(Future.succeededFuture(resultJsonObject));
          } else {
            LOGGER.debug("Could not read from DB : " + readHandler.cause());
            handler.handle(Future.failedFuture(readHandler.cause().getMessage()));
          }
        });
  }

  @Override
  public MeteringService publishMeteringData(
      JsonObject request, Handler<AsyncResult<JsonObject>> handler) {

    JsonObject writeMessage = queryBuilder.buildMessageForRmq(request);

    rmqService.publishMessage(
        writeMessage,
        EXCHANGE_NAME,
        ROUTING_KEY,
        rmqHandler -> {
          if (rmqHandler.succeeded()) {
            handler.handle(Future.succeededFuture());
            LOGGER.info("inserted into rmq");
          } else {
            LOGGER.error(rmqHandler.cause());
            try {
              Response resp =
                  objectMapper.readValue(rmqHandler.cause().getMessage(), Response.class);
              LOGGER.debug("response from rmq " + resp);
              handler.handle(Future.failedFuture(resp.toString()));
            } catch (JsonProcessingException e) {
              LOGGER.error("Failure message not in format [type,title,detail]");
              handler.handle(Future.failedFuture(e.getMessage()));
            }
          }
        });
    return this;
  }

  private Future<JsonObject> executeQueryDatabaseOperation(JsonObject jsonObject) {
    Promise<JsonObject> promise = Promise.promise();
    postgresService.executeQuery(
        jsonObject,
        dbHandler -> {
          if (dbHandler.succeeded()) {
            promise.complete(dbHandler.result());
          } else {
            promise.fail(dbHandler.cause().getMessage());
          }
        });

    return promise.future();
  }

  @Override
  public MeteringService monthlyOverview(
      JsonObject request, Handler<AsyncResult<JsonObject>> handler) {
    String startTime = request.getString(STARTT);
    String endTime = request.getString(ENDT);
    if (startTime != null && endTime == null || startTime == null && endTime != null) {
      handler.handle(Future.failedFuture("Bad Request"));
    }
    if (startTime != null && endTime != null) {
      validationCheck = dateValidation.dateParamCheck(request);

      if (validationCheck != null && validationCheck.containsKey(ERROR)) {
        responseBuilder =
            new ResponseBuilder("failed")
                .setTypeAndTitle(400)
                .setMessage(validationCheck.getString(ERROR));
        handler.handle(Future.failedFuture(responseBuilder.getResponse().toString()));
        return this;
      }
    }

    String role = request.getString(ROLE);
    if (role.equalsIgnoreCase("admin") || role.equalsIgnoreCase("consumer")) {
      queryOverview = queryBuilder.buildMonthlyOverview(request);
      LOGGER.debug("query Overview =" + queryOverview);
      JsonObject query = new JsonObject().put(QUERY_KEY, queryOverview);
      Future<JsonObject> result = executeQueryDatabaseOperation(query);
      result.onComplete(
          handlers -> {
            if (handlers.succeeded()) {
              LOGGER.debug("Count return Successfully");
              handler.handle(Future.succeededFuture(handlers.result()));
            } else {
              LOGGER.debug("Could not read from DB : " + handlers.cause());
              handler.handle(Future.failedFuture(handlers.cause().getMessage()));
            }
          });
    } else if (role.equalsIgnoreCase("provider") || role.equalsIgnoreCase("delegate")) {
      String resourceId = request.getString(IID);
      JsonObject jsonObject =
          new JsonObject().put("type", CacheType.CATALOGUE_CACHE).put("key", resourceId);

      cacheService
          .get(jsonObject)
          .onSuccess(
              providerHandler -> {
                String providerId = providerHandler.getString("provider");
                request.put("providerid", providerId);

                queryOverview = queryBuilder.buildMonthlyOverview(request);
                LOGGER.debug("query Overview =" + queryOverview);
                JsonObject query = new JsonObject().put(QUERY_KEY, queryOverview);
                Future<JsonObject> result = executeQueryDatabaseOperation(query);
                result.onComplete(
                    handlers -> {
                      if (handlers.succeeded()) {
                        LOGGER.debug("Count return Successfully");
                        handler.handle(Future.succeededFuture(handlers.result()));
                      } else {
                        LOGGER.debug("Could not read from DB : " + handlers.cause());
                        handler.handle(Future.failedFuture(handlers.cause().getMessage()));
                      }
                    });
              })
          .onFailure(fail -> LOGGER.debug(fail.getMessage()));
    }
    return this;
  }

  @Override
  public MeteringService summaryOverview(
      JsonObject request, Handler<AsyncResult<JsonObject>> handler) {
    String startTime = request.getString(STARTT);
    String endTime = request.getString(ENDT);
    if (startTime != null && endTime == null || startTime == null && endTime != null) {
      handler.handle(Future.failedFuture("Bad Request"));
    }
    if (startTime != null && endTime != null) {
      validationCheck = dateValidation.dateParamCheck(request);

      if (validationCheck != null && validationCheck.containsKey(ERROR)) {
        responseBuilder =
            new ResponseBuilder("failed")
                .setTypeAndTitle(400)
                .setMessage(validationCheck.getString(ERROR));
        handler.handle(Future.failedFuture(responseBuilder.getResponse().toString()));
        return this;
      }
    }

    String role = request.getString(ROLE);
    if (role.equalsIgnoreCase("admin") || role.equalsIgnoreCase("consumer")) {
      summaryOverview = queryBuilder.buildSummaryOverview(request);
      LOGGER.debug("summary query =" + summaryOverview);
      JsonObject query = new JsonObject().put(QUERY_KEY, summaryOverview);
      Future<JsonObject> result = executeQueryDatabaseOperation(query);
      result.onComplete(
          handlers -> {
            if (handlers.succeeded()) {
              jsonArray = handlers.result().getJsonArray("result");
              if (jsonArray.size() == 0) {
                responseBuilder =
                    new ResponseBuilder("not found")
                        .setTypeAndTitle(204)
                        .setMessage("NO ID Present");
                handler.handle(Future.succeededFuture(responseBuilder.getResponse()));
              }
              cacheCall(jsonArray)
                  .onSuccess(
                      resultHandler -> {
                        JsonObject resultJson =
                            new JsonObject()
                                .put("type", "urn:dx:dm:Success")
                                .put("title", "Success")
                                .put("results", resultHandler);
                        handler.handle(Future.succeededFuture(resultJson));
                      });
            } else {
              LOGGER.debug("Could not read from DB : " + handlers.cause());
              handler.handle(Future.failedFuture(handlers.cause().getMessage()));
            }
          });
    } else if (role.equalsIgnoreCase("provider") || role.equalsIgnoreCase("delegate")) {
      String resourceId = request.getString(IID);
      JsonObject jsonObject =
          new JsonObject().put("type", CacheType.CATALOGUE_CACHE).put("key", resourceId);
      cacheService
          .get(jsonObject)
          .onSuccess(
              providerHandler -> {
                String providerId = providerHandler.getString("provider");
                request.put("providerid", providerId);
                summaryOverview = queryBuilder.buildSummaryOverview(request);
                LOGGER.debug("summary query =" + summaryOverview);
                JsonObject query = new JsonObject().put(QUERY_KEY, queryOverview);
                Future<JsonObject> result = executeQueryDatabaseOperation(query);
                result.onComplete(
                    handlers -> {
                      if (handlers.succeeded()) {
                        jsonArray = handlers.result().getJsonArray("result");
                        if (jsonArray.size() == 0) {
                          responseBuilder =
                              new ResponseBuilder("not found")
                                  .setTypeAndTitle(204)
                                  .setMessage("NO ID Present");
                          handler.handle(Future.succeededFuture(responseBuilder.getResponse()));
                        }
                        cacheCall(jsonArray)
                            .onSuccess(
                                resultHandler -> {
                                  JsonObject resultJson =
                                      new JsonObject()
                                          .put("type", "urn:dx:dm:Success")
                                          .put("title", "Success")
                                          .put("results", resultHandler);
                                  handler.handle(Future.succeededFuture(resultJson));
                                });
                      } else {
                        LOGGER.debug("Could not read from DB : " + handlers.cause());
                        handler.handle(Future.failedFuture(handlers.cause().getMessage()));
                      }
                    });
              })
          .onFailure(
              fail -> {
                LOGGER.debug(fail.getMessage());
                handler.handle(Future.failedFuture(fail.getMessage()));
              });
    }
    return this;
  }

  public Future<JsonArray> cacheCall(JsonArray jsonArray) {
    Promise<JsonArray> promise = Promise.promise();
    HashMap<String, Integer> resourceCount = new HashMap<>();
    resultJsonArray = new JsonArray();
    List<Future> list = new ArrayList<>();

    for (loopi = 0; loopi < jsonArray.size(); loopi++) {
      JsonObject jsonObject =
          new JsonObject()
              .put("type", CacheType.CATALOGUE_CACHE)
              .put("key", jsonArray.getJsonObject(loopi).getString("resourceid"));
      resourceCount.put(
          jsonArray.getJsonObject(loopi).getString("resourceid"),
          Integer.valueOf(jsonArray.getJsonObject(loopi).getString("count")));

      list.add(cacheService.get(jsonObject).recover(f -> Future.succeededFuture(null)));
    }

    CompositeFuture.join(list)
        .map(CompositeFuture::list)
        .map(result -> result.stream().filter(Objects::nonNull).collect(Collectors.toList()))
        .onSuccess(
            l -> {
              for (int i = 0; i < l.size(); i++) {
                JsonObject result = (JsonObject) l.get(i);
                JsonObject outputFormat =
                    new JsonObject()
                        .put("resourceid", result.getString("id"))
                        .put("resource_label", result.getString("description"))
                        .put("publisher", result.getString("name"))
                        .put("publisher_id", result.getString("provider"))
                        .put("city", result.getString("instance"))
                        .put("count", resourceCount.get(result.getString("id")));
                resultJsonArray.add(outputFormat);
              }
              promise.complete(resultJsonArray);
            });

    return promise.future();
  }
}
