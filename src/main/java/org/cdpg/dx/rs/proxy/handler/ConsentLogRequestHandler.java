package org.cdpg.dx.rs.proxy.handler;

import static iudx.rs.proxy.common.Constants.CONSEENTLOG_SERVICE_ADDRESS;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import iudx.rs.proxy.authenticator.model.JwtData;
import iudx.rs.proxy.optional.consentlogs.ConsentLoggingService;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ConsentLogRequestHandler implements Handler<RoutingContext> {

  private static final Logger LOGGER = LogManager.getLogger(ConsentLogRequestHandler.class);

  ConsentLoggingService consentLoggingService;
  private boolean isAdexDeployment;

  public ConsentLogRequestHandler(Vertx vertx, boolean isAdexDeployment) {
    this.isAdexDeployment = isAdexDeployment;
    consentLoggingService = ConsentLoggingService.createProxy(vertx, CONSEENTLOG_SERVICE_ADDRESS);
  }

  @Override
  public void handle(RoutingContext context) {
    LOGGER.trace("ConsentLogRequestHandler started");

    if (isAdexDeployment) {
      Future.future(f -> logRequestReceived(context));
      LOGGER.info("consent log : {}", "DATA_REQUESTED");
      context.next();

    } else {
      context.next();
    }
  }

  private Future<Void> logRequestReceived(RoutingContext context) {
    Promise<Void> promise = Promise.promise();
    JwtData jwtData = (JwtData) context.data().get("jwtData");
    JsonObject jsonObject = new JsonObject().put("logType", "DATA_REQUESTED");
    consentLoggingService
        .log(jsonObject, jwtData)
        .onSuccess(logHandler -> promise.complete())
        .onFailure(
            failure -> {
              LOGGER.warn("failed info :{}", failure.getMessage());
              promise.fail(failure.getMessage());
            });
    return promise.future();
  }
}
