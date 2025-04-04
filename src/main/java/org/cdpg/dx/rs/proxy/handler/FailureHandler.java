package org.cdpg.dx.rs.proxy.handler;

import static iudx.rs.proxy.apiserver.util.ApiServerConstants.APPLICATION_JSON;
import static iudx.rs.proxy.apiserver.util.ApiServerConstants.CONTENT_TYPE;
import static iudx.rs.proxy.apiserver.util.ApiServerConstants.JSON_DETAIL;
import static iudx.rs.proxy.apiserver.util.ApiServerConstants.JSON_TITLE;
import static iudx.rs.proxy.apiserver.util.ApiServerConstants.JSON_TYPE;
import static iudx.rs.proxy.apiserver.util.ApiServerConstants.MSG_BAD_QUERY;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.serviceproxy.ServiceException;
import iudx.rs.proxy.apiserver.exceptions.DxRuntimeException;
import iudx.rs.proxy.apiserver.response.RestResponse;
import iudx.rs.proxy.common.DXServiceExceptionCode;
import iudx.rs.proxy.common.HttpStatusCode;
import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class FailureHandler implements Handler<RoutingContext> {

  private static final Logger LOGGER = LogManager.getLogger(FailureHandler.class);

  @Override
  public void handle(RoutingContext context) {
    Throwable failure = context.failure();

    if (failure instanceof DxRuntimeException exception) {
      LOGGER.error(exception.getUrn().getUrn() + " : " + exception.getMessage());
      HttpStatusCode code = HttpStatusCode.getByValue(exception.getStatusCode());

      JsonObject response =
          new RestResponse.Builder()
              .withType(exception.getUrn().getUrn())
              .withTitle(code.getDescription())
              .withMessage(exception.getMessage())
              .build()
              .toJson();

      context
          .response()
          .putHeader(CONTENT_TYPE, APPLICATION_JSON)
          .setStatusCode(exception.getStatusCode())
          .end(response.toString());
    } else if (failure instanceof ServiceException exception) {

      DXServiceExceptionCode code = DXServiceExceptionCode.getByValue(exception.failureCode());

      JsonObject response =
          new RestResponse.Builder()
              .withType(code.getUrn())
              .withTitle(code.getDescription())
              .withMessage(exception.getMessage())
              .build()
              .toJson();

      LOGGER.error("response: " + response);

      context
          .response()
          .putHeader(CONTENT_TYPE, APPLICATION_JSON)
          .setStatusCode(code.getStatusCode())
          .end(response.encode());
    } else if (failure instanceof RuntimeException) {

      context
          .response()
          .putHeader(CONTENT_TYPE, APPLICATION_JSON)
          .setStatusCode(HttpStatus.SC_BAD_REQUEST)
          .end(validationFailureResponse(MSG_BAD_QUERY).toString());
    } else {
      LOGGER.error("failue in FailureHandldr " + failure);
    }

    context.next();
  }

  private JsonObject validationFailureResponse(String message) {
    return new JsonObject()
        .put(JSON_TYPE, HttpStatus.SC_BAD_REQUEST)
        .put(JSON_TITLE, "Bad Request")
        .put(JSON_DETAIL, message);
  }
}
