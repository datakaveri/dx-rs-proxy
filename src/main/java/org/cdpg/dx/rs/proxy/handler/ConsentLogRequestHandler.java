package org.cdpg.dx.rs.proxy.handler;

import static iudx.rs.proxy.common.Constants.CONSEENTLOG_SERVICE_ADDRESS;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.auditing.model.AuditLog;
import org.cdpg.dx.common.models.JwtData;
import org.cdpg.dx.rs.proxy.optional.consentlogs.service.ConsentLoggingService;
import org.cdpg.dx.util.RoutingContextHelper;

public class ConsentLogRequestHandler implements Handler<RoutingContext> {

  private static final Logger LOGGER = LogManager.getLogger(ConsentLogRequestHandler.class);

  ConsentLoggingService consentLoggingService;
  private boolean isConsentAuditRequired;

  public ConsentLogRequestHandler(Vertx vertx, boolean isConsentAuditRequired) {
    this.isConsentAuditRequired = isConsentAuditRequired;
    consentLoggingService = ConsentLoggingService.createProxy(vertx, CONSEENTLOG_SERVICE_ADDRESS);
  }

  @Override
  public void handle(RoutingContext context) {
    LOGGER.trace("ConsentLogRequestHandler started");

    if (isConsentAuditRequired) {
      Optional<JwtData> jwtData = RoutingContextHelper.getJwtData(context);
      if (jwtData.isEmpty()) {
        LOGGER.error("JWT data is not present in the context");
        context.fail(400, new IllegalArgumentException("JWT data is not present in the context"));
        return;
      }
      logRequestReceived(jwtData.get())
          .onSuccess(
              auditLog -> {
                List<AuditLog> logList = new ArrayList<>();
                logList.add(auditLog);
                RoutingContextHelper.setAuditingLog(context, logList);
              });
      LOGGER.info("consent log : {}", "DATA_REQUESTED");
      context.next();

    } else {
      context.next();
    }
  }

  private Future<AuditLog> logRequestReceived(JwtData jwtData) {
    Promise<AuditLog> promise = Promise.promise();
    consentLoggingService
        .log("DATA_REQUESTED", jwtData)
        .onSuccess(
            consentAuditLog -> {
              LOGGER.info("Consent log created successfully");
              promise.complete(consentAuditLog);
            })
        .onFailure(
            failure -> {
              LOGGER.warn("Failed to create consent log: {}", failure.getMessage());
              promise.fail(failure);
            });

    return promise.future();
  }
}
