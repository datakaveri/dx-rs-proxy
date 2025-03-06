package iudx.rs.proxy.apiserver.handlers;

import static iudx.rs.proxy.apiserver.util.ApiServerConstants.*;
import static iudx.rs.proxy.common.Constants.AUTH_SERVICE_ADDRESS;
import static iudx.rs.proxy.common.ResponseUrn.INVALID_TOKEN_URN;
import static iudx.rs.proxy.common.ResponseUrn.RESOURCE_NOT_FOUND_URN;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import iudx.rs.proxy.authenticator.AuthenticationService;
import iudx.rs.proxy.authenticator.model.JwtData;
import iudx.rs.proxy.common.HttpStatusCode;
import iudx.rs.proxy.common.ResponseUrn;
import iudx.rs.proxy.common.RoutingContextHelper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TokenDecodeHandler implements Handler<RoutingContext> {
  private static final Logger LOGGER = LogManager.getLogger(TokenDecodeHandler.class);

  static AuthenticationService authenticationServiceDecoder;
  private HttpServerRequest request;

  public static TokenDecodeHandler create(Vertx vertx) {
    authenticationServiceDecoder = AuthenticationService.createProxy(vertx, AUTH_SERVICE_ADDRESS);
    return new TokenDecodeHandler();
  }

  @Override
  public void handle(RoutingContext context) {
    LOGGER.trace("tokenDecoderHandler started");
    request = context.request();
    String token = request.headers().get(HEADER_TOKEN);
    authenticationServiceDecoder.decodeJwt(
        token,
        decodeHandler -> {
          if (decodeHandler.succeeded()) {
            JwtData jwtData = decodeHandler.result();
            context.data().put("jwtData", jwtData);
            RoutingContextHelper.setJwtData(context,jwtData);

          } else {
            processAuthFailure(context, decodeHandler.cause().getMessage());
            return;
          }
          context.next();
        });
  }

  private void processAuthFailure(RoutingContext ctx, String result) {
    LOGGER.info("Failure : {}", result);
    if (result.contains("Not Found")) {
      LOGGER.error("Error : Item Not Found");
      HttpStatusCode statusCode = HttpStatusCode.getByValue(404);
      ctx.response()
          .putHeader(CONTENT_TYPE, APPLICATION_JSON)
          .setStatusCode(statusCode.getValue())
          .end(generateResponse(RESOURCE_NOT_FOUND_URN, statusCode).toString());
    } else {
      LOGGER.error("Error : Authentication Failure");
      HttpStatusCode statusCode = HttpStatusCode.getByValue(401);
      ctx.response()
          .putHeader(CONTENT_TYPE, APPLICATION_JSON)
          .setStatusCode(statusCode.getValue())
          .end(generateResponse(INVALID_TOKEN_URN, statusCode).toString());
    }
  }

  private JsonObject generateResponse(ResponseUrn urn, HttpStatusCode statusCode) {
    return new JsonObject()
        .put(JSON_TYPE, urn.getUrn())
        .put(JSON_TITLE, statusCode.getDescription())
        .put(JSON_DETAIL, statusCode.getDescription());
  }
}
