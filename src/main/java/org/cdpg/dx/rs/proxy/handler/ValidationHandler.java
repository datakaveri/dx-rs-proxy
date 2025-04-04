package org.cdpg.dx.rs.proxy.handler;

import static iudx.rs.proxy.apiserver.util.ApiServerConstants.HEADER_PUBLIC_KEY;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.RoutingContext;
import iudx.rs.proxy.apiserver.util.RequestType;
import iudx.rs.proxy.apiserver.validation.ValidatorsHandlersFactory;
import iudx.rs.proxy.apiserver.validation.types.Validator;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ValidationHandler implements Handler<RoutingContext> {

  private RequestType requestType;
  private Vertx vertx;

  public ValidationHandler(Vertx vertx, RequestType apiRequestType) {
    this.vertx = vertx;
    this.requestType = apiRequestType;
  }

  @Override
  public void handle(RoutingContext context) {
    RequestBody requestBody = context.body();
    JsonObject body = null;
    if (requestBody != null && requestBody.asJsonObject() != null) {
      body = requestBody.asJsonObject().copy();
    }
    MultiMap parameters = context.request().params();
    Map<String, String> pathParams = context.pathParams();
    parameters.set(HEADER_PUBLIC_KEY, context.request().getHeader(HEADER_PUBLIC_KEY));
    parameters.addAll(pathParams);
    ValidatorsHandlersFactory validationFactory = new ValidatorsHandlersFactory();
    MultiMap headers = context.request().headers();
    List<Validator> validations =
        validationFactory.build(vertx, requestType, parameters, headers, body);
    for (Validator validator : Optional.ofNullable(validations).orElse(Collections.emptyList())) {
      validator.isValid();
    }
    context.next();
  }
}
