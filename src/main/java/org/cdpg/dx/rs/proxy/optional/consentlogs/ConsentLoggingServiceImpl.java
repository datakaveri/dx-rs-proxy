package org.cdpg.dx.rs.proxy.optional.consentlogs;

import static iudx.rs.proxy.apiserver.auditing.util.Constants.ORIGIN;
import static iudx.rs.proxy.apiserver.util.ApiServerConstants.VALIDATION_ID_PATTERN;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import iudx.rs.proxy.authenticator.model.JwtData;
import iudx.rs.proxy.cache.CacheService;
import iudx.rs.proxy.cache.cacheImpl.CacheType;
import iudx.rs.proxy.common.ConsentLogType;
import iudx.rs.proxy.databroker.service.DatabrokerService;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.rs.proxy.optional.consentlogs.dss.PayloadSigningManager;

public class ConsentLoggingServiceImpl implements ConsentLoggingService {

  private static final Logger LOGGER = LogManager.getLogger(ConsentLoggingServiceImpl.class);
  static WebClient catWebClient;
  private final PayloadSigningManager payloadSigningManager;
  private final DatabrokerService databrokerService;
  private final CacheService cacheService;
  Supplier<String> isoTimeSupplier =
      () -> ZonedDateTime.now(ZoneId.of(ZoneId.SHORT_IDS.get("IST"))).toString();
  Supplier<String> primaryKeySuppler = () -> UUID.randomUUID().toString();

  public ConsentLoggingServiceImpl(
      Vertx vertx,
      PayloadSigningManager signingManager,
      DatabrokerService databrokerService,
      CacheService cacheService) {
    this.payloadSigningManager = signingManager;
    this.databrokerService = databrokerService;
    this.cacheService = cacheService;
    WebClientOptions options = new WebClientOptions();
    options.setTrustAll(true).setVerifyHost(false).setSsl(true);
    catWebClient = WebClient.create(vertx, options);
  }

  @Override
  public Future<JsonObject> log(JsonObject request, JwtData jwtData) {
    LOGGER.trace("log started");
    LOGGER.debug("consent log: {} ", request);
    Promise<JsonObject> promise = Promise.promise();
    String logType = request.getString("logType");
    ConsentLogType consentLogType;
    try {
      consentLogType = getLogType(logType);
    } catch (IllegalArgumentException e) {
      return Future.failedFuture("No Consent defined for given type");
    }

    if (!isConsentRequired(jwtData)) {
      LOGGER.info("token doesn't contains PII data/consent not required.");
      return Future.failedFuture("token doesn't contains PII data/consent not required.");
    }
    if (consentLogType != null) {
      getCatItem(jwtData)
          .onSuccess(
              catItems -> {
                jwtData.setProvider(catItems.getString("provider"));
                // jwtData.setType(catItems.getString("type"));
                JsonObject consentAuditLog =
                    generateConsentAuditLog(consentLogType.toString(), jwtData);
                Future<Void> consentAuditFuture = auditingConsentLog(consentAuditLog);
                consentAuditFuture.onComplete(
                    auditHandler -> {
                      if (auditHandler.succeeded()) {
                        promise.complete(consentAuditLog);
                      } else {
                        promise.fail(auditHandler.cause().getMessage());
                      }
                    });
              })
          .onFailure(
              err -> {
                LOGGER.error("cat failure : " + err.getMessage());
                promise.fail(err.getMessage());
              });

    } else {
      promise.fail("null value passed as ConsentLogType");
    }

    return promise.future();
  }

  private ConsentLogType getLogType(String type) {
    ConsentLogType logType = null;
    try {
      logType = ConsentLogType.valueOf(type);

    } catch (IllegalArgumentException ex) {
      LOGGER.error("No consent type defined for given argument.");
    }
    return logType;
  }

  private JsonObject generateConsentAuditLog(String consentLogType, JwtData jwtData) {
    LOGGER.trace("generateAuditLog started");
    JsonObject cons = jwtData.getCons();
    String itemId = jwtData.getIid().split(":")[1];
    String type = "RESOURCE"; // make sure item should be RESOURCE only
    SignLogBuider signLog =
        new SignLogBuider.Builder()
            .withPrimaryKey(primaryKeySuppler.get())
            .forAiu_id(jwtData.getSub())
            .forEvent(consentLogType)
            .forItemType(type)
            .forItem_id(itemId)
            .witAipId(jwtData.getProvider())
            .withDpId(cons.getString("ppbNumber"))
            .withArtifactId(cons.getString("artifact"))
            .atIsoTime(isoTimeSupplier.get())
            .build();
    LOGGER.debug("log to be singed: " + signLog.toJson());
    String signedLog = payloadSigningManager.signDocWithPKCS12(signLog.toJson());
    JsonObject consentAuditLog = signLog.toJson();
    consentAuditLog.put("log", signedLog);
    consentAuditLog.put(ORIGIN, "consent-log");
    return consentAuditLog;
  }

  private Future<Void> auditingConsentLog(JsonObject consentAuditLog) {
    LOGGER.trace("auditingConsentLog started");
    Promise<Void> promise = Promise.promise();
    //todo: have to implement metering service for this
   /* databrokerService.publishMessage(
        consentAuditLog,
        handler -> {
          if (handler.succeeded()) {
            LOGGER.info("Log published into RMQ.");
            promise.complete();
          } else {
            LOGGER.error("failed to publish log into RMQ.");
            promise.fail("failed to publish log into RMQ.");
          }
        });*/
    return promise.future();
  }

  private Boolean isConsentRequired(JwtData jwtData) {
    String resourceId = jwtData.getIid().split(":")[1];
    Boolean isRequired = false;
    JsonObject cons = jwtData.getCons();
    if (VALIDATION_ID_PATTERN.matcher(resourceId).matches()
        && cons.containsKey("artifact")
        && cons.containsKey("ppbNumber")) {
      isRequired = true;
    }
    return isRequired;
  }

  private Future<JsonObject> getCatItem(JwtData jwtData) {
    String resourceId = jwtData.getIid().split(":")[1];
    LOGGER.debug("resourceId :{} ", resourceId);
    Promise promise = Promise.promise();

    CacheType cacheType = CacheType.CATALOGUE_CACHE;
    JsonObject requestJson = new JsonObject().put("type", cacheType).put("key", resourceId);
    cacheService
        .get(requestJson)
        .onSuccess(
            cacheResult -> {
              if (cacheResult == null) {
                promise.fail(
                    "Info: ID invalid ["
                        + resourceId
                        + "]: Empty response in results from Catalogue");
              } else {

                promise.complete(cacheResult);
              }
            })
        .onFailure(
            failureHandler ->
                promise.fail("catalogue_cache call result : [fail] " + failureHandler));
    return promise.future();
  }
}
