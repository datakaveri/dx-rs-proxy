package iudx.rs.proxy.apiserver.query;

import static iudx.rs.proxy.apiserver.util.ApiServerConstants.*;
import static iudx.rs.proxy.common.HttpStatusCode.BAD_REQUEST;
import static iudx.rs.proxy.common.ResponseUrn.*;
import static iudx.rs.proxy.metering.util.Constants.ERROR;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import iudx.rs.proxy.apiserver.exceptions.DxRuntimeException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * QueryMapper class to convert NGSILD query into json object for the purpose of information
 * exchange among different verticals. TODO Need to add documentation.
 */
public class QueryMapper {

  private static final Logger LOGGER = LogManager.getLogger(QueryMapper.class);
  private final boolean isTimeLimitEnabled;
  private boolean isTemporal = false;
  private boolean isAttributeSearch = false;
  private boolean isGeoSearch = false;
  private RoutingContext context;

  public QueryMapper(RoutingContext context, boolean isTimeLimitEnabled) {
    this.context = context;
    this.isTimeLimitEnabled = isTimeLimitEnabled;
  }

  public JsonObject toJson(NGSILDQueryParams params, boolean isTemporal) {
    return toJson(params, isTemporal, false);
  }

  /**
   * This method is used to create a json object from NGSILDQueryParams.
   *
   * @param params A map of query parameters passed.
   * @param isTemporal flag indicating whether temporal or not.
   * @return JsonObject result.
   */
  public JsonObject toJson(NGSILDQueryParams params, boolean isTemporal, boolean isAsyncQuery) {
    this.isTemporal = isTemporal;
    JsonObject json = new JsonObject();
    JsonObject geoJson = new JsonObject();
    JsonObject temporal = new JsonObject();

    if (params.getId() != null) {
      JsonArray jsonArray = new JsonArray();
      params.getId().forEach(s -> jsonArray.add(s.toString()));
      json.put(JSON_ID, jsonArray);
      LOGGER.debug("Info : json " + json);
    }

    JsonObject authInfo;
    JsonArray accessibleList = null;
    if (context.data().get("authInfo") != null) {
      authInfo = (JsonObject) context.data().get("authInfo");
      accessibleList = authInfo.getJsonArray(ACCESSIBLE_ATTRS, new JsonArray());
    }

    if (params.getAttrs() != null) {
      JsonArray jsonArray = new JsonArray();
      params.getAttrs().forEach(attribute -> jsonArray.add(attribute));

      if (accessibleList != null && !accessibleList.isEmpty()) {
        JsonArray commonValues = new JsonArray();
        for (var element : jsonArray) {
          if (accessibleList.contains(element)) {
            commonValues.add(element);
          }
        }
        json.put(JSON_ATTRIBUTE_FILTER, commonValues);
      } else {
        json.put(JSON_ATTRIBUTE_FILTER, jsonArray);
      }
      LOGGER.debug("Info : json " + json);
    } else {
      LOGGER.debug("Info : json " + json);
      json.put(JSON_ATTRIBUTE_FILTER, accessibleList);
    }

    if (isGeoQuery(params)) {
      LOGGER.debug("getGeoRel:" + params.getGeoRel().getRelation());
      LOGGER.debug("getCoordinates:" + params.getCoordinates());
      LOGGER.debug("getGeometry:" + params.getGeometry());
      LOGGER.debug("getGeoProperty:" + params.getGeoProperty());

      if (params.getGeoRel().getRelation() != null
          && params.getCoordinates() != null
          && params.getGeometry() != null
          && params.getGeoProperty() != null) {
        isGeoSearch = true;
        if (params.getGeometry().equalsIgnoreCase(GEOM_POINT)
            && params.getGeoRel().getRelation().equals(JSON_NEAR)
            && params.getGeoRel().getMaxDistance() != null) {
          String[] coords = params.getCoordinates().replaceAll("\\[|\\]", "").split(",");
          geoJson.put(JSON_LAT, Double.parseDouble(coords[0]));
          geoJson.put(JSON_LON, Double.parseDouble(coords[1]));
          geoJson.put(JSON_RADIUS, params.getGeoRel().getMaxDistance());
        } else {
          geoJson.put(JSON_GEOMETRY, params.getGeometry());
          geoJson.put(JSON_COORDINATES, params.getCoordinates());
          geoJson.put(JSON_GEOREL, getOrDefault(params.getGeoRel().getRelation(), JSON_WITHIN));
          if (params.getGeoRel().getMaxDistance() != null) {
            geoJson.put(JSON_MAXDISTANCE, params.getGeoRel().getMaxDistance());
          } else if (params.getGeoRel().getMinDistance() != null) {
            geoJson.put(JSON_MINDISTANCE, params.getGeoRel().getMinDistance());
          }
        }
        LOGGER.debug("Info : json " + geoJson);
      } else {
        json.put(ERROR, INVALID_GEO_PARAM_URN);
        DxRuntimeException ex =
            new DxRuntimeException(
                BAD_REQUEST.getValue(),
                INVALID_GEO_PARAM_URN,
                "incomplete geo-query geoproperty, geometry, georel, coordinates all are mandatory.");
        context.fail(400, ex);
      }
      json.put(GEO_QUERY, geoJson);
    }
    if (isTemporal
        && params.getTemporalRelation().getTimeRel() != null
        && params.getTemporalRelation().getTime() != null) {
      isTemporal = true;
      if (params.getTemporalRelation().getTimeRel().equalsIgnoreCase(JSON_DURING)
          || params.getTemporalRelation().getTimeRel().equalsIgnoreCase(JSON_BETWEEN)) {
        LOGGER.debug("Info : inside during ");

        temporal.put(JSON_TIME, params.getTemporalRelation().getTime());
        temporal.put(JSON_ENDTIME, params.getTemporalRelation().getEndTime());
        temporal.put(JSON_TIMEREL, params.getTemporalRelation().getTimeRel());

        if (!isValidTimeInterval(
            JSON_DURING,
            temporal.getString(JSON_TIME),
            temporal.getString(JSON_ENDTIME),
            isAsyncQuery)) {
          json.put(ERROR, "BAD_REQUEST");
        }

      } else {
        LOGGER.debug("Info : outside during ");
        temporal.put(JSON_TIME, params.getTemporalRelation().getTime());
        temporal.put(JSON_TIMEREL, params.getTemporalRelation().getTimeRel());
      }
      LOGGER.debug("Info : json " + temporal);
      json.put(TEMPORAL_QUERY, temporal);
    }

    if (params.getQ() != null) {
      isAttributeSearch = true;
      // TODO: disabled this check for ESDS-IPeG deployment
      //      JsonArray query = new JsonArray();
      //      String[] qterms = params.getQ().split(";");
      //      for (String term : qterms) {
      //        query.add(getQueryTerms(term));
      //      }
      json.put(JSON_ATTR_QUERY, params.getQ());
    }
    if (params.getGeoProperty() != null) {
      geoJson.put(JSON_GEOPROPERTY, params.getGeoProperty());
      LOGGER.debug("Info : json " + json);
    }
    if (params.getOptions() != null) {
      json.put(IUDXQUERY_OPTIONS, params.getOptions());
      LOGGER.debug("Info : json " + json);
    }
    if (params.getPageFrom() != null) {
      json.put(NGSILDQUERY_FROM, params.getPageFrom());
    }
    if (params.getPageSize() != null) {
      json.put(NGSILDQUERY_SIZE, params.getPageSize());
    }
    json.put(JSON_SEARCH_TYPE, getSearchType(isAsyncQuery));
    LOGGER.debug("Info : json " + json);
    return json;
  }

  /*
   * check for a valid days interval for temporal queries
   */
  // TODO : decide how to enforce for before and after queries.
  private boolean isValidTimeInterval(
      String timeRel, String time, String endTime, boolean isAsyncQuery) {
    boolean isValid = true;
    long totalDaysAllowed = 0;
    if (timeRel.equalsIgnoreCase(JSON_DURING)) {
      if (isNullOrEmpty(time) || isNullOrEmpty(endTime)) {
        isValid = false;
        DxRuntimeException ex =
            new DxRuntimeException(
                BAD_REQUEST.getValue(),
                INVALID_TEMPORAL_PARAM_URN,
                "time and endTime both are mandatory for during Query.");
        context.fail(400, ex);
      }

      try {
        ZonedDateTime start = ZonedDateTime.parse(time);
        ZonedDateTime end = ZonedDateTime.parse(endTime);
        Duration duration = Duration.between(start, end);
        totalDaysAllowed = duration.toDays();
        LOGGER.debug("totalDaysAllowed:: " + totalDaysAllowed);
        if (start.isAfter(end)) {
          isValid = false;
          DxRuntimeException ex =
              new DxRuntimeException(
                  BAD_REQUEST.getValue(),
                  INVALID_TEMPORAL_PARAM_URN,
                  "end date is before start date");
          context.fail(400, ex);
        }
      } catch (Exception e) {
        isValid = false;
        DxRuntimeException ex =
            new DxRuntimeException(
                BAD_REQUEST.getValue(),
                INVALID_TEMPORAL_PARAM_URN,
                "time and endTime both are mandatory for during Query.");
        context.fail(400, ex);
      }
    } else if (timeRel.equalsIgnoreCase("after")) {
      // how to enforce days duration for after and before,i.e here or DB
    } else if (timeRel.equalsIgnoreCase("before")) {

    }
    LOGGER.debug("isTimeLimitEnabled : {}", isTimeLimitEnabled);

    if (isTimeLimitEnabled
        && !isAsyncQuery
        && totalDaysAllowed > VALIDATION_MAX_DAYS_INTERVAL_ALLOWED) {
      isValid = false;
      DxRuntimeException ex =
          new DxRuntimeException(
              BAD_REQUEST.getValue(),
              INVALID_TEMPORAL_PARAM_URN,
              "time interval greater than 10 days is not allowed");
      this.context.fail(400, ex);
    }

    if (isTimeLimitEnabled
        && isAsyncQuery
        && totalDaysAllowed > VALIDATION_MAX_DAYS_INTERVAL_ALLOWED_FOR_ASYNC) {
      isValid = false;
      DxRuntimeException ex =
          new DxRuntimeException(
              BAD_REQUEST.getValue(),
              INVALID_TEMPORAL_PARAM_URN,
              "time interval greater than 365 days is not allowed");
      context.fail(400, ex);
    }
    return isValid;
  }

  public boolean isNullOrEmpty(String value) {
    return value == null || value.isEmpty();
  }

  private String getSearchType(boolean isAsyncQuery) {
    StringBuilder searchType = new StringBuilder();
    if (isTemporal) {
      searchType.append(JSON_TEMPORAL_SEARCH);
    } else if (!isTemporal && !isAsyncQuery) {
      searchType.append(JSON_LATEST_SEARCH);
    }
    if (isAttributeSearch) {
      searchType.append(JSON_ATTRIBUTE_SEARCH);
    }
    if (isGeoSearch) {
      searchType.append(JSON_GEO_SEARCH);
    }
    return searchType.substring(0, searchType.length() - 1).toString();
  }

  JsonObject getQueryTerms(String queryTerms) {
    JsonObject json = new JsonObject();
    int length = queryTerms.length();
    List<Character> allowedSpecialCharacter = Arrays.asList('>', '=', '<', '!');
    List<String> allowedOperators = Arrays.asList(">", "=", "<", ">=", "<=", "==", "!=");
    int startIndex = 0;
    boolean specialCharFound = false;
    for (int i = 0; i < length; i++) {
      Character c = queryTerms.charAt(i);
      if (!(Character.isLetter(c) || Character.isDigit(c)) && !specialCharFound) {
        if (allowedSpecialCharacter.contains(c)) {
          json.put(JSON_ATTRIBUTE, queryTerms.substring(startIndex, i));
          startIndex = i;
          specialCharFound = true;
        } else {
          LOGGER.debug("Ignore " + c);
          DxRuntimeException ex =
              new DxRuntimeException(
                  BAD_REQUEST.getValue(), INVALID_ATTR_PARAM_URN, "Operator not allowed.");
          context.fail(400, ex);
        }
      } else {
        if (specialCharFound && (Character.isLetter(c) || Character.isDigit(c))) {
          json.put(JSON_OPERATOR, queryTerms.substring(startIndex, i));
          json.put(JSON_VALUE, queryTerms.substring(i));
          break;
        }
      }
    }
    if (!allowedOperators.contains(json.getString(JSON_OPERATOR))) {
      DxRuntimeException ex =
          new DxRuntimeException(
              BAD_REQUEST.getValue(), INVALID_ATTR_PARAM_URN, "Operator not allowed.");
      this.context.fail(400, ex);
    }
    return json;
  }

  private boolean isGeoQuery(NGSILDQueryParams params) {
    LOGGER.debug(
        "georel " + params.getGeoRel() + " relation : " + params.getGeoRel().getRelation());
    return params.getGeoRel().getRelation() != null
        || params.getCoordinates() != null
        || params.getGeometry() != null
        || params.getGeoProperty() != null;
  }

  private <T> T getOrDefault(T value, T def) {
    return (value == null) ? def : value;
  }
}
