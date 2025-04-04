package org.cdpg.dx.rs.proxy.validation;

import static iudx.rs.proxy.apiserver.util.ApiServerConstants.*;
import static iudx.rs.proxy.apiserver.util.ApiServerConstants.HEADER_PUBLIC_KEY;
import static iudx.rs.proxy.common.ResponseUrn.SCHEMA_READ_ERROR_URN;

import com.google.common.base.Charsets;
import com.google.common.io.CharStreams;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.json.schema.Schema;
import io.vertx.json.schema.SchemaParser;
import io.vertx.json.schema.SchemaRouter;
import io.vertx.json.schema.SchemaRouterOptions;
import iudx.rs.proxy.apiserver.exceptions.DxRuntimeException;
import iudx.rs.proxy.apiserver.util.RequestType;
import iudx.rs.proxy.apiserver.validation.types.*;
import iudx.rs.proxy.common.HttpStatusCode;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ValidatorsHandlersFactory {

  private static final Logger LOGGER = LogManager.getLogger(ValidatorsHandlersFactory.class);
  private static Map<String, String> jsonSchemaMap = new HashMap<>();

  public List<Validator> build(
      final Vertx vertx,
      final RequestType requestType,
      final MultiMap parameters,
      final MultiMap headers,
      final JsonObject body) {
    LOGGER.debug("getValidation4Context() started for :" + requestType);
    LOGGER.debug("type :" + requestType);
    List<Validator> validator = null;

    switch (requestType) {
      case ENTITY:
        validator = getEntityRequestValidations(parameters);
        break;
      case TEMPORAL:
        validator = getTemporalRequestValidations(parameters);
        break;
      case POST_ENTITIES:
        validator = getPostTemporalValidations(vertx, parameters, headers, body, requestType);
        break;
      case POST_TEMPORAL:
        validator = getPostEntitiesValidations(vertx, parameters, headers, body, requestType);
        break;
      case ASYNC_SEARCH:
        validator = getAsyncRequestValidations(parameters);
        break;
      case ASYNC_STATUS:
        validator = getAsyncStatusRequestValidator(parameters);
        break;
      case POST_CONNECTOR:
        validator = getPostConnectorRequestValidator(vertx, parameters, body, requestType);
      default:
        break;
    }

    return validator;
  }

  private List<Validator> getEntityRequestValidations(final MultiMap parameters) {
    List<Validator> validators = new ArrayList<>();

    validators.add(new IDTypeValidator(parameters.get(NGSILDQUERY_ID), true));
    validators.add(new AttrsTypeValidator(parameters.get(NGSILDQUERY_ATTRIBUTE), false));
    validators.add(new GeoRelTypeValidator(parameters.get(NGSILDQUERY_GEOREL), false));
    validators.add(new GeometryTypeValidator(parameters.get(NGSILDQUERY_GEOMETRY), false));
    validators.add(new GeoPropertyTypeValidator(parameters.get(NGSILDQUERY_GEOPROPERTY), false));
    validators.add(new QTypeValidator(parameters.get(NGSLILDQUERY_Q), false));
    validators.add(new DistanceTypeValidator(parameters.get(NGSILDQUERY_MAXDISTANCE), false));
    validators.add(new DistanceTypeValidator(parameters.get("maxDistance"), false));
    validators.add(new OptionsTypeValidator(parameters.get(IUDXQUERY_OPTIONS), false));
    validators.add(new CoordinatesTypeValidator(parameters.get(NGSILDQUERY_COORDINATES), false));

    // pagination optional fields
    validators.add(new PaginationLimitTypeValidator(parameters.get(NGSILDQUERY_SIZE), false));
    validators.add(new PaginationOffsetTypeValidator(parameters.get(NGSILDQUERY_FROM), false));

    // optional header public key
    validators.add(new HeaderKeyTypeValidation(parameters.get(HEADER_PUBLIC_KEY), false));

    return validators;
  }

  private List<Validator> getTemporalRequestValidations(final MultiMap parameters) {

    List<Validator> validators = new ArrayList<>();

    validators.add(new IDTypeValidator(parameters.get(NGSILDQUERY_ID), true));
    validators.add(new AttrsTypeValidator(parameters.get(NGSILDQUERY_ATTRIBUTE), false));
    validators.add(new GeoRelTypeValidator(parameters.get(NGSILDQUERY_GEOREL), false));
    validators.add(new GeometryTypeValidator(parameters.get(NGSILDQUERY_GEOMETRY), false));
    validators.add(new GeoPropertyTypeValidator(parameters.get(NGSILDQUERY_GEOPROPERTY), false));
    validators.add(new QTypeValidator(parameters.get(NGSLILDQUERY_Q), false));
    validators.add(new DistanceTypeValidator(parameters.get(NGSILDQUERY_MAXDISTANCE), false));
    validators.add(new DistanceTypeValidator(parameters.get("maxDistance"), false));
    validators.add(new OptionsTypeValidator(parameters.get(IUDXQUERY_OPTIONS), false));
    validators.add(new CoordinatesTypeValidator(parameters.get(NGSILDQUERY_COORDINATES), false));
    validators.add(new TimeRelTypeValidator(parameters.get(NGSILDQUERY_TIMEREL), true));
    validators.add(new DateTypeValidator(parameters.get(NGSILDQUERY_TIME), true));
    validators.add(new DateTypeValidator(parameters.get(NGSILDQUERY_ENDTIME), false));

    // optional header public key
    validators.add(new HeaderKeyTypeValidation(parameters.get(HEADER_PUBLIC_KEY), false));

    return validators;
  }

  private List<Validator> getRequestSchemaValidator(
      Vertx vertx, JsonObject body, RequestType requestType) {
    List<Validator> validators = new ArrayList<>();
    SchemaRouter schemaRouter = SchemaRouter.create(vertx, new SchemaRouterOptions());
    SchemaParser schemaParser = SchemaParser.createOpenAPI3SchemaParser(schemaRouter);
    String jsonSchema = null;

    try {
      jsonSchema = loadJson(requestType.getFilename());
      Schema schema = schemaParser.parse(new JsonObject(jsonSchema));
      validators.add(new JsonSchemaTypeValidator(body, schema));
    } catch (Exception ex) {
      LOGGER.error(ex);
      throw new DxRuntimeException(HttpStatusCode.BAD_REQUEST.getValue(), SCHEMA_READ_ERROR_URN);
    }

    return validators;
  }

  private String loadJson(String filename) {
    String jsonStr = null;
    if (jsonSchemaMap.containsKey(filename)) {
      jsonStr = jsonSchemaMap.get(filename);
    } else {
      try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(filename)) {
        jsonStr = CharStreams.toString(new InputStreamReader(inputStream, Charsets.UTF_8));
        jsonSchemaMap.put(filename, jsonStr);
      } catch (IOException e) {
        LOGGER.error(e);
        throw new DxRuntimeException(HttpStatusCode.BAD_REQUEST.getValue(), SCHEMA_READ_ERROR_URN);
      }
    }
    return jsonStr;
  }

  private List<Validator> getPostTemporalValidations(
      Vertx vertx,
      final MultiMap parameters,
      final MultiMap headers,
      final JsonObject body,
      final RequestType requestType) {

    List<Validator> validators = new ArrayList<>();
    // pagination optional fields
    validators.add(new PaginationLimitTypeValidator(parameters.get(NGSILDQUERY_SIZE), false));
    validators.add(new PaginationOffsetTypeValidator(parameters.get(NGSILDQUERY_FROM), false));
    // request body validators.
    validators.addAll(getRequestSchemaValidator(vertx, body, requestType));

    return validators;
  }

  private List<Validator> getPostEntitiesValidations(
      Vertx vertx,
      final MultiMap parameters,
      final MultiMap headers,
      final JsonObject body,
      final RequestType requestType) {

    List<Validator> validators = new ArrayList<>();
    // pagination optional fields
    validators.add(new PaginationLimitTypeValidator(parameters.get(NGSILDQUERY_SIZE), false));
    validators.add(new PaginationOffsetTypeValidator(parameters.get(NGSILDQUERY_FROM), false));
    // request body validators.
    validators.addAll(getRequestSchemaValidator(vertx, body, requestType));

    return validators;
  }

  private List<Validator> getAsyncRequestValidations(final MultiMap parameters) {

    List<Validator> validators = new ArrayList<>();
    validators.add(new IDTypeValidator(parameters.get(NGSILDQUERY_ID), true));
    validators.add(new AttrsTypeValidator(parameters.get(NGSILDQUERY_ATTRIBUTE), false));
    validators.add(new OptionsTypeValidator(parameters.get(IUDXQUERY_OPTIONS), false));
    // geo fields
    validators.add(new GeoRelTypeValidator(parameters.get(NGSILDQUERY_GEOREL), false));
    validators.add(new GeometryTypeValidator(parameters.get(NGSILDQUERY_GEOMETRY), false));
    validators.add(new GeoPropertyTypeValidator(parameters.get(NGSILDQUERY_GEOPROPERTY), false));
    validators.add(new QTypeValidator(parameters.get(NGSLILDQUERY_Q), false));
    validators.add(new DistanceTypeValidator(parameters.get(NGSILDQUERY_MAXDISTANCE), false, true));
    validators.add(new DistanceTypeValidator(parameters.get("maxDistance"), false, true));
    validators.add(new CoordinatesTypeValidator(parameters.get(NGSILDQUERY_COORDINATES), false));
    // temporal fields
    validators.add(new TimeRelTypeValidator(parameters.get(NGSILDQUERY_TIMEREL), false));
    validators.add(new DateTypeValidator(parameters.get(NGSILDQUERY_TIME), false));
    validators.add(new DateTypeValidator(parameters.get(NGSILDQUERY_ENDTIME), false));

    // optional header public key
    validators.add(new HeaderKeyTypeValidation(parameters.get(HEADER_PUBLIC_KEY), false));
    validators.add(
        new ElasticSearchFileResponseTypeValidator(
            parameters.get(HEADER_RESPONSE_FILE_FORMAT), false));

    return validators;
  }

  private List<Validator> getAsyncStatusRequestValidator(final MultiMap parameters) {
    List<Validator> validators = new ArrayList<>();
    validators.add(
        new StringTypeValidator(parameters.get("searchId"), true, VALIDATION_ID_PATTERN));

    // optional header public key
    validators.add(new HeaderKeyTypeValidation(parameters.get(HEADER_PUBLIC_KEY), false));

    return validators;
  }

  private List<Validator> getPostConnectorRequestValidator(
      Vertx vertx,
      final MultiMap parameters,
      final JsonObject body,
      final RequestType requestType) {
    List<Validator> validators = new ArrayList<>();
    // optional header public key
    validators.add(new HeaderKeyTypeValidation(parameters.get(HEADER_PUBLIC_KEY), false));
    validators.addAll(getRequestSchemaValidator(vertx, body, requestType));

    return validators;
  }
}
