package iudx.rs.proxy.apiserver;

import static iudx.rs.proxy.apiserver.util.ApiServerConstants.*;
import static iudx.rs.proxy.apiserver.util.Util.toList;
import static iudx.rs.proxy.common.ResponseUrn.INVALID_GEO_VALUE_URN;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import iudx.rs.proxy.apiserver.exceptions.DxRuntimeException;
import iudx.rs.proxy.apiserver.validation.types.*;
import iudx.rs.proxy.cache.CacheService;
import iudx.rs.proxy.cache.cacheImpl.CacheType;
import iudx.rs.proxy.common.HttpStatusCode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** This class is used to validate NGSI-LD request and request parameters. */
public class ParamsValidator {
  private static final Logger LOGGER = LogManager.getLogger(ParamsValidator.class);
  private static final Set<String> validParams = new HashSet<String>();
  private static final Set<String> validHeaders = new HashSet<String>();

  static {
    validParams.add(NGSILDQUERY_TYPE);
    validParams.add(NGSILDQUERY_ID);
    validParams.add(NGSILDQUERY_IDPATTERN);
    validParams.add(NGSILDQUERY_ATTRIBUTE);
    validParams.add(NGSLILDQUERY_Q);
    validParams.add(NGSILDQUERY_GEOREL);
    validParams.add(NGSILDQUERY_GEOMETRY);
    validParams.add(NGSILDQUERY_COORDINATES);
    validParams.add(NGSILDQUERY_GEOPROPERTY);
    validParams.add(NGSILDQUERY_TIMEPROPERTY);
    validParams.add(NGSILDQUERY_TIMEREL);
    validParams.add(NGSILDQUERY_TIME);
    validParams.add(NGSILDQUERY_ENDTIME);
    validParams.add(NGSILDQUERY_ENTITIES);
    validParams.add(NGSILDQUERY_GEOQ);
    validParams.add(NGSILDQUERY_TEMPORALQ);
    // Need to check with the timeProperty in Post Query property for NGSI-LD release v1.3.1
    validParams.add(NGSILDQUERY_TIME_PROPERTY);
    validParams.add(NGSILDQUERY_FROM);
    validParams.add(NGSILDQUERY_SIZE);

    // for IUDX count query
    validParams.add(IUDXQUERY_OPTIONS);
    // IUDX search-type
    validParams.add(IUDX_SEARCH_TYPE);
  }

  static {
    validHeaders.add(HEADER_OPTIONS);
    validHeaders.add(HEADER_TOKEN);
    validHeaders.add("User-Agent");
    validHeaders.add("Content-Type");
    validHeaders.add(HEADER_PUBLIC_KEY);
  }

  private CacheService cacheService;

  public ParamsValidator(CacheService cacheService) {
    this.cacheService = cacheService;
  }

  /**
   * Validate a http request.
   *
   * @param parameterMap parameters map of request query
   */
  private boolean validateParams(MultiMap parameterMap) {
    final List<Entry<String, String>> entries = parameterMap.entries();
    for (final Entry<String, String> entry : entries) {
      if (!validParams.contains(entry.getKey())) {
        return false;
      }
    }
    return true;
  }

  /**
   * validate request parameters.
   *
   * @param paramsMap map of request parameters
   * @return Future future JsonObject
   */
  public Future<Boolean> validate(MultiMap paramsMap) {

    Promise<Boolean> promise = Promise.promise();
    if (validateParams(paramsMap)) {
      isValidQueryWithFilters(paramsMap)
          .onComplete(
              handler -> {
                if (handler.succeeded()) {
                  {
                    promise.complete(true);
                  }
                } else {
                  promise.fail(handler.cause().getMessage());
                }
              });
    } else {
      promise.fail(MSG_BAD_QUERY);
    }
    return promise.future();
  }

  public Future<Boolean> validate(JsonObject requestJson) {
    Promise<Boolean> promise = Promise.promise();
    MultiMap paramsMap = MultiMap.caseInsensitiveMultiMap();

    requestJson.forEach(
        entry -> {
          if (entry.getKey().equalsIgnoreCase("geoQ")
              || entry.getKey().equalsIgnoreCase("temporalQ")) {
            JsonObject innerObject = (JsonObject) entry.getValue();
            paramsMap.add(entry.getKey().toString(), entry.getValue().toString());
            innerObject.forEach(
                innerentry -> {
                  paramsMap.add(innerentry.getKey().toString(), innerentry.getValue().toString());
                });
          } else if (entry.getKey().equalsIgnoreCase("entities")) {
            paramsMap.add(entry.getKey().toString(), entry.getValue().toString());
            JsonArray array = (JsonArray) entry.getValue();
            JsonObject innerObject = array.getJsonObject(0);
            innerObject.forEach(
                innerentry -> {
                  paramsMap.add(innerentry.getKey().toString(), innerentry.getValue().toString());
                });
          } else {
            paramsMap.add(entry.getKey().toString(), entry.getValue().toString());
          }
        });

    String attrs = paramsMap.get(NGSILDQUERY_ATTRIBUTE);
    // String q = paramsMap.get(NGSLILDQUERY_Q);
    String coordinates = paramsMap.get(NGSILDQUERY_COORDINATES);
    String geoRel = paramsMap.get(NGSILDQUERY_GEOREL);
    String[] georelArray = geoRel != null ? geoRel.split(";") : null;

    boolean validations1 =
        !(new AttrsTypeValidator(attrs, false).isValid())
            // || !(new QTypeValidator(q, false).isValid()) -- dropped this validation for IPeG
            || !(new CoordinatesTypeValidator(coordinates, false).isValid())
            || !(new GeoRelTypeValidator(georelArray != null ? georelArray[0] : null, false)
                .isValid())
            || !(georelArray != null && georelArray.length == 2
                ? isValidDistance(georelArray[1])
                : isValidDistance(null));

    validate(paramsMap)
        .onComplete(
            handler -> {
              if (handler.succeeded() && !validations1) {
                promise.complete(true);
              } else {
                promise.fail(MSG_BAD_QUERY);
              }
            });
    return promise.future();
  }

  private Future<Boolean> isValidQueryWithFilters(MultiMap paramsMap) {
    Promise<Boolean> promise = Promise.promise();
    Future<List<String>> filtersFuture = getApplicableFilters(paramsMap.get("id"));

    filtersFuture
        .onSuccess(
            filters -> {
              LOGGER.debug("Filters: " + filters);

              if (isTemporalQuery(paramsMap) && !filters.contains("TEMPORAL")) {
                promise.fail("Temporal parameters are not supported by RS group/Item.");
                return;
              }
              if (isAttributeQuery(paramsMap) && !filters.contains("ATTR")) {
                promise.fail("Attribute parameters are not supported by RS group/Item.");
                return;
              }
              if (isSpatialQuery(paramsMap) && !filters.contains("SPATIAL")) {
                promise.fail("Spatial parameters are not supported by RS group/Item.");
                return;
              }

              promise.complete(true);
            })
        .onFailure(
            error -> {
              promise.fail("Failed to get filters for validation: " + error.getMessage());
            });

    return promise.future();
  }

  private Boolean isTemporalQuery(MultiMap params) {
    return params.contains(NGSILDQUERY_TIMEREL)
        || params.contains(NGSILDQUERY_TIME)
        || params.contains(NGSILDQUERY_ENDTIME)
        || params.contains(NGSILDQUERY_TIME_PROPERTY);
  }

  private Boolean isAttributeQuery(MultiMap params) {
    return params.contains(NGSILDQUERY_ATTRIBUTE);
  }

  private Boolean isSpatialQuery(MultiMap params) {
    return params.contains(NGSILDQUERY_GEOREL)
        || params.contains(NGSILDQUERY_GEOMETRY)
        || params.contains(NGSILDQUERY_GEOPROPERTY)
        || params.contains(NGSILDQUERY_COORDINATES);
  }

  private boolean isValidDistance(String value) {
    if (value == null) {
      return true;
    }
    Validator validator;
    try {
      String[] distanceArray = value.split("=");
      if (distanceArray.length == 2) {
        String distanceValue = distanceArray[1];
        validator = new DistanceTypeValidator(distanceValue, false);
        return validator.isValid();
      } else {
        throw new DxRuntimeException(
            HttpStatusCode.BAD_REQUEST.getValue(),
            INVALID_GEO_VALUE_URN,
            INVALID_GEO_VALUE_URN.getMessage());
      }
    } catch (Exception ex) {
      LOGGER.error(ex);
      throw new DxRuntimeException(
          HttpStatusCode.BAD_REQUEST.getValue(),
          INVALID_GEO_VALUE_URN,
          INVALID_GEO_VALUE_URN.getMessage());
    }
  }

  public Future<List<String>> getApplicableFilters(String id) {
    Promise<List<String>> promise = Promise.promise();
    JsonObject cacheRequests = new JsonObject();
    cacheRequests.put("type", CacheType.CATALOGUE_CACHE);
    cacheRequests.put("key", id);

    Future<JsonObject> groupIdFuture = cacheService.get(cacheRequests);
    groupIdFuture.onComplete(
        grpId -> {
          if (grpId.succeeded()) {
            Set<String> type = new HashSet<String>(grpId.result().getJsonArray("type").getList());
            Set<String> itemTypeSet =
                type.stream().map(e -> e.split(":")[1]).collect(Collectors.toSet());
            itemTypeSet.retainAll(ITEM_TYPES);

            String groupId;
            if (!itemTypeSet.contains("Resource")) {
              groupId = id;
            } else {
              groupId = grpId.result().getString("resourceGroup");
            }

            LOGGER.debug("groupId = " + groupId);
            JsonObject cacheRequest = new JsonObject();
            cacheRequest.put("type", CacheType.CATALOGUE_CACHE);
            cacheRequest.put("key", groupId);

            Future<JsonObject> groupFilter = cacheService.get(cacheRequest);
            JsonObject itemCacheRequest = cacheRequest.copy();
            itemCacheRequest.put("key", id);
            Future<JsonObject> itemFilters = cacheService.get(itemCacheRequest);

            List<String> filters = new ArrayList<>();
            CompositeFuture.all(List.of(groupFilter, itemFilters))
                .onComplete(
                    ar -> {
                      if (ar.failed()) {
                        promise.fail("no filters available for : " + id);
                        return;
                      }

                      // Add group level filters
                      if (groupFilter.result().containsKey("iudxResourceAPIs")) {
                        filters.addAll(
                            toList(groupFilter.result().getJsonArray("iudxResourceAPIs")));
                      }

                      // Add item level filters
                      if (itemFilters.result().containsKey("iudxResourceAPIs")) {
                        filters.addAll(
                            toList(itemFilters.result().getJsonArray("iudxResourceAPIs")));
                      }

                      // Complete only after both filters are checked
                      if (filters.isEmpty()) {
                        promise.fail("no filters available for : " + id);
                      } else {
                        promise.complete(filters);
                      }
                    });
          } else {
            LOGGER.debug("Failed : " + grpId.cause().getMessage());
            promise.fail(grpId.cause().getMessage());
          }
        });
    return promise.future();
  }
}
