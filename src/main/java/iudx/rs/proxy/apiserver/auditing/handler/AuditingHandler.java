package iudx.rs.proxy.apiserver.auditing.handler;

import static iudx.rs.proxy.apiserver.auditing.util.Constants.AUDITING_EXCHANGEE;
import static iudx.rs.proxy.apiserver.auditing.util.Constants.ROUTING_KEY;
import static iudx.rs.proxy.apiserver.util.ApiServerConstants.*;
import static iudx.rs.proxy.common.Constants.CACHE_SERVICE_ADDRESS;
import static iudx.rs.proxy.common.Constants.DATABROKER_SERVICE_ADDRESS;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import iudx.rs.proxy.apiserver.auditing.model.AuditLogData;
import iudx.rs.proxy.authenticator.model.JwtData;
import iudx.rs.proxy.cache.CacheService;
import iudx.rs.proxy.cache.cacheImpl.CacheType;
import iudx.rs.proxy.common.RoutingContextHelper;
import iudx.rs.proxy.databroker.service.DatabrokerService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class AuditingHandler implements Handler<RoutingContext> {
  private static final Logger LOGGER = LogManager.getLogger(AuditingHandler.class);
  private static final List<Integer> STATUS_CODES_TO_AUDIT = List.of(200, 201);
  private static final String SERVER_ORIGIN = "rs-server";

  private final CacheService cacheService;
  private final DatabrokerService databrokerService;

  private final Supplier<Long> epochSupplier =
      () -> LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
  private final Supplier<String> isoTimeSupplier =
      () -> LocalDateTime.now().atZone(ZoneOffset.UTC).toString();
  private final Supplier<String> primaryKeySupplier =
      () -> UUID.randomUUID().toString().replace("-", "");

  public AuditingHandler(Vertx vertx) {
    this.cacheService = CacheService.createProxy(vertx, CACHE_SERVICE_ADDRESS);
    this.databrokerService = DatabrokerService.createProxy(vertx, DATABROKER_SERVICE_ADDRESS);
  }

  @Override
  public void handle(RoutingContext context) {
    LOGGER.trace("AuditingHandler() started");
    JwtData jwtData = RoutingContextHelper.getJwtData(context);
    AuditLogData auditLogData = createAuditLogData(jwtData, context);

    JsonObject requestJson = buildCacheRequest(context);

    getCacheItem(requestJson)
        .compose(
            cacheResult -> {
              LOGGER.debug("cacheResult " + cacheResult);
              String providerId = cacheResult.getString("provider");
              String type = cacheResult.getString(TYPE_KEY);
              String resourceGroup = cacheResult.getString(RESOURCE_GROUP);
              auditLogData.setProviderId(providerId);
              auditLogData.setResourceGroup(resourceGroup);
              auditLogData.setType(type);
              LOGGER.debug("Auditing log data: {}", auditLogData.toJson());
              return databrokerService.publishMessage(
                  auditLogData.toJson(), AUDITING_EXCHANGEE, ROUTING_KEY);
            })
        .onSuccess(
            success ->
                LOGGER.info(
                    "Auditing log published successfully for user: {} endPoint: {}",
                    auditLogData.getUserid(),
                    auditLogData.getApi()))
        .onFailure(
            failure -> LOGGER.error("Failed to publish auditing log: {}", failure.getMessage()));
  }

  private AuditLogData createAuditLogData(JwtData jwtData, RoutingContext context) {
    AuditLogData auditLogData = new AuditLogData();
    auditLogData.setOrigin(SERVER_ORIGIN);
    auditLogData.setPrimaryKey(primaryKeySupplier.get());
    auditLogData.setEpochTime(epochSupplier.get());
    auditLogData.setIsoTime(isoTimeSupplier.get());
    auditLogData.setResponseSize(RoutingContextHelper.getResponseSize(context));
    auditLogData.setApi(RoutingContextHelper.getEndPoint(context));
    auditLogData.setId(RoutingContextHelper.getId(context));
    auditLogData.setUserid(jwtData.getSub());
    auditLogData.setDelegatorId(getDelegatorId(jwtData));

    return auditLogData;
  }

  private String getDelegatorId(JwtData jwtData) {
    if ("delegate".equalsIgnoreCase(jwtData.getRole()) && jwtData.getDrl() != null) {
      return jwtData.getDid();
    }
    return jwtData.getSub();
  }

  private JsonObject buildCacheRequest(RoutingContext context) {
    return new JsonObject()
        .put("type", CacheType.CATALOGUE_CACHE)
        .put("key", RoutingContextHelper.getId(context));
  }

  private Future<JsonObject> getCacheItem(JsonObject cacheJson) {
    Promise<JsonObject> promise = Promise.promise();
    cacheService
        .get(cacheJson)
        .onSuccess(cacheResult -> promise.complete(handleCacheSuccess(cacheResult)))
        .onFailure(
            failure -> {
              LOGGER.debug(
                  "Failed to fetech cat item for id: {} cause: {} ",
                  cacheJson.getString("key"),
                  failure.getMessage());
              promise.fail(failure);
            });

    return promise.future();
  }

  private JsonObject handleCacheSuccess(JsonObject cacheServiceResult) {
    String itemType = extractItemTypes(cacheServiceResult);
    String resourceGroup = getResourceGroup(cacheServiceResult, itemType);

    cacheServiceResult.put("type", itemType);
    cacheServiceResult.put("resourceGroup", resourceGroup);

    return cacheServiceResult;
  }

  private String extractItemTypes(JsonObject cacheServiceResult) {
    Set<String> type = new HashSet<>(cacheServiceResult.getJsonArray("type").getList());
    Set<String> itemTypeSet = type.stream().map(e -> e.split(":")[1]).collect(Collectors.toSet());
    itemTypeSet.retainAll(ITEM_TYPES);
    return itemTypeSet.iterator().next();
  }

  private String getResourceGroup(JsonObject cacheServiceResult, String itemType) {
    if (itemType.equalsIgnoreCase("Resource")) {
      return cacheServiceResult.getString("resourceGroup");
    } else {
      return cacheServiceResult.getString("id");
    }
  }
}
