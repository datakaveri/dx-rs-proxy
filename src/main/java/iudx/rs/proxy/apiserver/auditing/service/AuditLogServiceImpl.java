package iudx.rs.proxy.apiserver.auditing.service;

import static iudx.rs.proxy.apiserver.util.ApiServerConstants.*;
import static iudx.rs.proxy.apiserver.auditing.util.Constants.*;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import iudx.rs.proxy.apiserver.exceptions.DxRuntimeException;
import iudx.rs.proxy.apiserver.auditing.model.AuditLogSearchRequest;
import iudx.rs.proxy.apiserver.auditing.model.OverviewRequest;
import iudx.rs.proxy.cache.CacheService;
import iudx.rs.proxy.cache.cacheImpl.CacheType;
import iudx.rs.proxy.common.Api;
import iudx.rs.proxy.common.ResponseUrn;
import iudx.rs.proxy.database.DatabaseService;
import iudx.rs.proxy.apiserver.auditing.util.DateValidation;
import iudx.rs.proxy.apiserver.auditing.util.ParamsValidation;
import iudx.rs.proxy.apiserver.auditing.util.QueryBuilder;
import iudx.rs.proxy.apiserver.auditing.util.ResponseBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AuditLogServiceImpl implements AuditLogService {
  private static final Logger LOGGER = LogManager.getLogger(AuditLogServiceImpl.class);

  private final DatabaseService pgService;
  private final CacheService cache;
  private final QueryBuilder queryBuilder = new QueryBuilder();
  String summaryOverview;
  private ParamsValidation validation;
  private Api api;

  public AuditLogServiceImpl(DatabaseService pgService, CacheService cache, Api api) {
    this.pgService = pgService;
    this.cache = cache;
    this.api = api;
    validation = new ParamsValidation(api);
  }

  @Override
  public Future<JsonObject> executeAudigingSearchQuery(AuditLogSearchRequest searchRequest) {
    LOGGER.trace("Info: Read Query {}", searchRequest.toJson());
    JsonObject request =  searchRequest.toJson();

    JsonObject validationCheck = validation.paramsCheck(searchRequest.toJson());
    if (validationCheck != null && validationCheck.containsKey(ERROR)) {
      return Future.failedFuture(
          new DxRuntimeException(
              400, ResponseUrn.BAD_REQUEST_URN, validationCheck.getString(ERROR)));
    }

    String options = searchRequest.getOptions();

    return executeCountQuery(request)
        .compose(
            countResponse -> {
              int total = countResponse.getInteger("total", 0);
              request.put("totalHits", total);

              // If options = "count", return only count as response
              if ("count".equalsIgnoreCase(options)) {
                return Future.succeededFuture(
                    new ResponseBuilder("Success")
                        .setTypeAndTitle(200)
                        .setCount(total)
                        .getResponse());
              }

              // If total count is zero, return empty response
              if (total == 0) {
                return Future.succeededFuture(
                    new ResponseBuilder(FAILED).setTypeAndTitle(204).setCount(0).getResponse());
              }

              // Otherwise, fetch the full data
              return executeReadQueryForData(request);
            });
  }

  private Future<JsonObject> executeCountQuery(JsonObject request) {
    JsonObject query = queryBuilder.buildCountReadQueryFromPg(request);
    return executeQueryDatabaseOperation(query)
        .map(
            countHandler -> {
              JsonArray countResult = countHandler.getJsonArray("result");
              int total = 0;

              if (countResult != null && !countResult.isEmpty()) {
                JsonObject countObj = countResult.getJsonObject(0);
                total = countObj.getInteger("count", 0);
              }

              return new JsonObject().put("total", total);
            });
  }

  private Future<JsonObject> executeReadQueryForData(JsonObject request) {
    int limit;
    int offset;
    if (request.getString(LIMITPARAM) == null) {
      limit = 2000;
      request.put(LIMITPARAM, limit);
    } else {
      limit = Integer.parseInt(request.getString(LIMITPARAM));
    }
    if (request.getString(OFFSETPARAM) == null) {
      offset = 0;
      request.put(OFFSETPARAM, offset);
    } else {
      offset = Integer.parseInt(request.getString(OFFSETPARAM));
    }

    JsonObject query = queryBuilder.buildReadQueryFromPg(request);
    LOGGER.debug("Read query = {}", query);

    return executeQueryDatabaseOperation(query)
        .map(
            result -> {
              result.put(LIMITPARAM, limit);
              result.put(OFFSETPARAM, offset);
              result.put("totalHits", request.getInteger("totalHits"));
              return result;
            });
  }

  @Override
  public Future<JsonObject> monthlyOverview(OverviewRequest overviewRequest) {

    if ((overviewRequest.getStartTime() != null && overviewRequest.getEndTime() == null)
        || (overviewRequest.getStartTime() == null && overviewRequest.getEndTime() != null)) {
      return Future.failedFuture(
          new DxRuntimeException(
              400, ResponseUrn.BAD_REQUEST_URN, "Invalid Start Time or End Time"));
    }

    if (overviewRequest.getStartTime() != null && overviewRequest.getEndTime() != null) {
      JsonObject validationCheck = DateValidation.dateParamCheck(overviewRequest.toJson());
      if (validationCheck != null && validationCheck.containsKey(ERROR)) {
        return Future.failedFuture(
            new DxRuntimeException(
                400, ResponseUrn.BAD_REQUEST_URN, validationCheck.getString(ERROR)));
      }
    }

    LOGGER.info("fetching monthlyOverview for role: {}", overviewRequest.getRole());

    if ("admin".equalsIgnoreCase(overviewRequest.getRole())
        || "consumer".equalsIgnoreCase(overviewRequest.getRole())) {
      return executeQueryDatabaseOperation(
          new JsonObject()
              .put(QUERY_KEY, queryBuilder.buildMonthlyOverviewQueryNew(overviewRequest)));
    }

    if ("provider".equalsIgnoreCase(overviewRequest.getRole())
        || "delegate".equalsIgnoreCase(overviewRequest.getRole())) {
      return fetchProviderId(overviewRequest.getIid())
          .compose(
              providerId -> {
                OverviewRequest request = overviewRequest.withUpdatedProviderId(providerId);

                return executeQueryDatabaseOperation(
                    new JsonObject()
                        .put(QUERY_KEY, queryBuilder.buildMonthlyOverviewQueryNew(request)));
              });
    }

    return Future.failedFuture("Invalid Role");
  }

  @Override
  public Future<JsonObject> summaryOverview(OverviewRequest overviewRequest) {
    String startTime = overviewRequest.getStartTime();
    String endTime = overviewRequest.getEndTime();

    if (startTime != null && endTime == null || startTime == null && endTime != null) {
      return Future.failedFuture(
          new DxRuntimeException(
              400, ResponseUrn.BAD_REQUEST_URN, "Invalid Start Time or End time"));
    }

    if (startTime != null && endTime != null) {
      JsonObject validationCheck = DateValidation.dateParamCheck(overviewRequest.toJson());
      if (validationCheck != null && validationCheck.containsKey(ERROR)) {
        return Future.failedFuture(
            new DxRuntimeException(
                400, ResponseUrn.BAD_REQUEST_URN, validationCheck.getString(ERROR)));
      }
    }

    String role = overviewRequest.getRole();
    if ("admin".equalsIgnoreCase(role) || "consumer".equalsIgnoreCase(role)) {
      return executeSummaryQuery(overviewRequest);
    } else if ("provider".equalsIgnoreCase(role) || "delegate".equalsIgnoreCase(role)) {
      return fetchProviderId(overviewRequest.getIid())
          .compose(
              providerId -> {
                OverviewRequest request = overviewRequest.withUpdatedProviderId(providerId);

                return executeSummaryQuery(request);
              });
    }

    return Future.failedFuture("Invalid Role");
  }

  private Future<JsonObject> executeSummaryQuery(OverviewRequest request) {
    summaryOverview = queryBuilder.buildSummaryOverview(request);
    LOGGER.debug("summary query = {}", summaryOverview);
    JsonObject query = new JsonObject().put(QUERY_KEY, summaryOverview);
    return executeQueryDatabaseOperation(query).compose(this::processSummaryResult);
  }

  private Future<String> fetchProviderId(String id) {
    JsonObject jsonObject = new JsonObject().put("type", CacheType.CATALOGUE_CACHE).put("key", id);

    return cache.get(jsonObject).map(providerHandler -> providerHandler.getString("provider"));
  }

  private Future<JsonObject> processSummaryResult(JsonObject result) {
    JsonArray jsonArray = result.getJsonArray("result");
    if (jsonArray == null || jsonArray.isEmpty()) {
      ResponseBuilder responseBuilder =
          new ResponseBuilder("not found").setTypeAndTitle(204).setMessage("NO ID Present");
      return Future.succeededFuture(responseBuilder.getResponse());
    }

    return cacheCall(jsonArray)
        .map(
            resultHandler -> {
              return new JsonObject()
                  .put("type", "urn:dx:dm:Success")
                  .put("title", "Success")
                  .put("results", resultHandler);
            });
  }

  public Future<JsonArray> cacheCall(JsonArray jsonArray) {
    HashMap<String, Integer> resourceCount = new HashMap<>();
    JsonArray resultJsonArray = new JsonArray();
    List<Future<JsonObject>> futureList = new ArrayList<>();

    for (int i = 0; i < jsonArray.size(); i++) {
      JsonObject jsonObject =
          new JsonObject()
              .put("type", CacheType.CATALOGUE_CACHE)
              .put("key", jsonArray.getJsonObject(i).getString("resourceid"));

      resourceCount.put(
          jsonArray.getJsonObject(i).getString("resourceid"),
          Integer.parseInt(jsonArray.getJsonObject(i).getString("count")));

      // Add cache lookup to future list, recover null for failures
      futureList.add(cache.get(jsonObject).recover(f -> Future.succeededFuture(null)));
    }

    return CompositeFuture.all(new ArrayList<>(futureList))
        .map(
            composite -> {
              List<Object> results = composite.list(); // Get results as list
              JsonArray jsonArrayResult = new JsonArray();

              results.stream()
                  .filter(Objects::nonNull) // Remove null results (failed cache lookups)
                  .map(obj -> (JsonObject) obj)
                  .forEach(
                      res -> {
                        jsonArrayResult.add(
                            new JsonObject()
                                .put("resourceid", res.getString("id"))
                                .put("resource_label", res.getString("description"))
                                .put("publisher", res.getString("name"))
                                .put("publisher_id", res.getString("provider"))
                                .put("city", res.getString("instance"))
                                .put("count", resourceCount.get(res.getString("id"))));
                      });

              return jsonArrayResult; // Return the manually constructed JsonArray
            });
  }

  private Future<JsonObject> executeQueryDatabaseOperation(JsonObject jsonObject) {
    return pgService.executeQuery(jsonObject);
  }
}
