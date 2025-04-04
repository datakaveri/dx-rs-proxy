package org.cdpg.dx.auditing.handler;

import static org.cdpg.dx.auditing.util.Constants.AUDITING_EXCHANGE;
import static org.cdpg.dx.auditing.util.Constants.ROUTING_KEY;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.databroker.service.DataBrokerService;
import org.cdpg.dx.util.RoutingContextHelper;

public class AuditingHandler {
  private static final Logger LOGGER = LogManager.getLogger(AuditingHandler.class);

  private final DataBrokerService databrokerService;

  public AuditingHandler(DataBrokerService databrokerService) {
    this.databrokerService = databrokerService;
  }

  public void handleApiAudit(RoutingContext context) {
    context.addBodyEndHandler(
        v -> {
          try {
            Optional<JsonObject> auditLogOpt = RoutingContextHelper.getAuditingLog(context);
            if (auditLogOpt.isPresent()) {
              JsonObject auditLog = auditLogOpt.get();
              publishAuditLogs(auditLog);
            } else {
              LOGGER.warn("No auditing log found in context");
            }
          } catch (Exception e) {
            LOGGER.error("Error while publishing auditing log: {}", e.getMessage(), e);
          }
        });
  }

  public void publishAuditLogs(JsonObject auditingLog) {
    LOGGER.trace("publishAuditLogs() started");
    databrokerService
        .publishMessage(auditingLog, AUDITING_EXCHANGE, ROUTING_KEY)
        .onSuccess(success -> LOGGER.info("Auditing log published successfully"))
        .onFailure(
            failure -> LOGGER.error("Failed to publish auditing log: {}", failure.getMessage()));
  }
}
