package org.cdpg.dx.rs.proxy.optional.consentlogs.service;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import iudx.rs.proxy.cache.cacheImpl.CacheType;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.auditing.model.AuditLog;
import org.cdpg.dx.auditing.model.ConsentAuditLog;
import org.cdpg.dx.cache.CacheService;
import org.cdpg.dx.common.models.JwtData;
import org.cdpg.dx.rs.proxy.optional.consentlogs.util.ConsentType;
import org.cdpg.dx.rs.proxy.optional.consentlogs.dss.PayloadSigningManager;

public class ConsentLoggingServiceImpl implements ConsentLoggingService {

  public static final Pattern VALIDATION_ID_PATTERN =
      Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

  private static final Logger LOGGER = LogManager.getLogger(ConsentLoggingServiceImpl.class);
  private final PayloadSigningManager payloadSigningManager;
  private final CacheService cacheService;

  Supplier<String> isoTimeSupplier =
      () -> ZonedDateTime.now(ZoneId.of(ZoneId.SHORT_IDS.get("IST"))).toString();
  Supplier<String> primaryKeySuppler = () -> UUID.randomUUID().toString();

  public ConsentLoggingServiceImpl(
      PayloadSigningManager signingManager, CacheService cacheService) {
    this.payloadSigningManager = signingManager;
    this.cacheService = cacheService;
  }

  @Override
  public Future<AuditLog> log(String consentType, JwtData jwtData) {
    LOGGER.trace("log started");
    LOGGER.debug("consent log: {} ", consentType);
    Promise<AuditLog> promise = Promise.promise();
    ConsentType type;
    try {
      type = getLogType(consentType);
    } catch (IllegalArgumentException e) {
      return Future.failedFuture("No Consent defined for given type");
    }

    if (!isConsentRequired(jwtData)) {
      LOGGER.info("token doesn't contains PII data/consent not required.");
      return Future.failedFuture("token doesn't contains PII data/consent not required.");
    }
    String resourceId = jwtData.iid().split(":")[1];
    getProvider(resourceId)
        .onSuccess(
            provider -> {
              LOGGER.info("provider :{} ", provider);
              AuditLog consentAuditLog =
                  generateConsentAuditLog(type.toString(), jwtData, provider);
              promise.complete(consentAuditLog);
            })
        .onFailure(
            failure -> {
              LOGGER.error("failed info :{}", failure.getMessage());
              promise.fail(failure.getMessage());
            });

    return promise.future();
  }

  private ConsentType getLogType(String type) {
    ConsentType logType = null;
    try {
      logType = ConsentType.valueOf(type);

    } catch (IllegalArgumentException ex) {
      LOGGER.error("No consent type defined for given argument.");
    }
    return logType;
  }

  private AuditLog generateConsentAuditLog(
      String consentLogType, JwtData jwtData, String provider) {
    LOGGER.trace("generateAuditLog started");
    JsonObject cons = jwtData.cons();
    String itemId = jwtData.iid().split(":")[1];
    String type = "RESOURCE"; // make sure item should be RESOURCE only
    SignLogBuider signLog =
        new SignLogBuider.Builder()
            .withPrimaryKey(primaryKeySuppler.get())
            .forAiu_id(jwtData.sub())
            .forEvent(consentLogType)
            .forItemType(type)
            .forItem_id(itemId)
            .witAipId((provider))
            .withDpId(cons.getString("ppbNumber"))
            .withArtifactId(cons.getString("artifact"))
            .atIsoTime(isoTimeSupplier.get())
            .build();
    LOGGER.debug("log to be singed: " + signLog.toJson());
    String signedLog = payloadSigningManager.signDocWithPKCS12(signLog);

    JsonObject jsonLog = signLog.toJson();

    jsonLog.put("log", signedLog);
    jsonLog.put("origin", "consent-log");

    return ConsentAuditLog.fromJson(jsonLog);
  }

  private Boolean isConsentRequired(JwtData jwtData) {
    String resourceId = jwtData.iid().split(":")[1];
    boolean isRequired = false;
    JsonObject cons = jwtData.cons();
    if (VALIDATION_ID_PATTERN.matcher(resourceId).matches()
        && cons.containsKey("artifact")
        && cons.containsKey("ppbNumber")) {
      isRequired = true;
    }
    return isRequired;
  }

  private Future<String> getProvider(String resourceId) {
    LOGGER.debug("resourceId :{} ", resourceId);
    Promise<String> promise = Promise.promise();

    CacheType cacheType = CacheType.CATALOGUE_CACHE;
    JsonObject requestJson = new JsonObject().put("type", cacheType).put("key", resourceId);
    cacheService
        .get(requestJson)
        .onSuccess(
            cacheResult -> {
              if (cacheResult == null || cacheResult.containsKey("provider")) {
                promise.fail(
                    "Info: ID invalid ["
                        + resourceId
                        + "]: Empty response in results from Catalogue or provider not found");
              } else {
                promise.complete(cacheResult.getString("provider"));
              }
            })
        .onFailure(
            failureHandler ->
                promise.fail("catalogue_cache call result : [fail] " + failureHandler));
    return promise.future();
  }
}
