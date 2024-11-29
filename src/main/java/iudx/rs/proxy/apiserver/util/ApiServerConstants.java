package iudx.rs.proxy.apiserver.util;

import io.vertx.core.http.HttpMethod;
import java.util.*;
import java.util.regex.Pattern;

public class ApiServerConstants {

  // header
  public static final String HEADER_TOKEN = "token";
  public static final String HEADER_HOST = "Host";
  public static final String HEADER_ACCEPT = "Accept";
  public static final String HEADER_CONTENT_LENGTH = "Content-Length";
  public static final String HEADER_CONTENT_TYPE = "Content-Type";
  public static final String HEADER_ORIGIN = "Origin";
  public static final String HEADER_REFERER = "Referer";
  public static final String HEADER_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
  public static final String HEADER_OPTIONS = "options";
  public static final String HEADER_PUBLIC_KEY = "publicKey";
  public static final String HEADER_CSV = "csv";
  public static final String HEADER_JSON = "json";
  public static final String HEADER_PARQUET = "parquet";
  public static final String HEADER_RESPONSE_FILE_FORMAT = "format";
  public static final String JSON_ALIAS = "alias";

  public static final Set<String> ALLOWED_HEADERS =
      new HashSet<>(
          Arrays.asList(
              HEADER_ACCEPT,
              HEADER_TOKEN,
              HEADER_CONTENT_LENGTH,
              HEADER_CONTENT_TYPE,
              HEADER_HOST,
              HEADER_ORIGIN,
              HEADER_REFERER,
              HEADER_ALLOW_ORIGIN));

  public static final Set<HttpMethod> ALLOWED_METHODS =
      new HashSet<>(
          Arrays.asList(
              HttpMethod.GET,
              HttpMethod.POST,
              HttpMethod.OPTIONS,
              HttpMethod.DELETE,
              HttpMethod.PATCH,
              HttpMethod.PUT));

  // path regex
  // public static final String NGSILD_BASE_PATH = "/ngsi-ld/v1";
  public static final String ENTITIES_URL = "/entities";
  //  public static final String ENTITIES_URL_REGEX = ENTITIES_URL + "(.*)";
  public static final String TEMPORAL_URL = "/temporal/entities";
  // path regex
  //  public static final String TEMPORAL_URL_REGEX = TEMPORAL_URL + "(.*)";
  public static final String CONSUMER_AUDIT_URL = "/consumer/audit";
  // date-time format
  public static final String PROVIDER_AUDIT_URL = "/provider/audit";
  public static final String POST_ENTITIES_URL = "/entityOperations/query";
  public static final String POST_TEMPORAL_URL = "/temporal/entityOperations/query";
  public static final String ASYNC = "/async";

  // Async endpoints
  public static final String STATUS = "/status";
  public static final String SEARCH = "/search";
  public static final String MONTHLY_OVERVIEW = "/overview";
  public static final String SUMMARY_ENDPOINT = "/summary";
  public static final String CONNECTORS = "/connector";
  public static final String RESET_PWD = "/user/resetPassword";
  public static final String STARTT = "starttime";
  public static final String ENDT = "endtime";
  public static final String ACCESSIBLE_ATTRS = "accessibleAttrs";
  public static final String API_ENDPOINT = "apiEndpoint";
  public static final String API_METHOD = "method";
  public static final String ID = "id";
  public static final String PPB_NUMBER = "ppbNumber";
  public static final String PII = "PII";
  public static final String IDS = "ids";
  // ngsi-ld/IUDX query paramaters
  public static final String NGSILDQUERY_ID = "id";
  public static final String NGSILDQUERY_IDPATTERN = "idpattern";
  public static final String NGSILDQUERY_TYPE = "type";
  public static final String NGSILDQUERY_COORDINATES = "coordinates";
  public static final String NGSILDQUERY_GEOMETRY = "geometry";
  public static final String NGSILDQUERY_ATTRIBUTE = "attrs";
  public static final String NGSLILDQUERY_Q = "q";
  public static final String NGSILDQUERY_GEOREL = "georel";
  public static final String NGSILDQUERY_TIMEREL = "timerel";
  public static final String NGSILDQUERY_TIME = "time";
  public static final String NGSILDQUERY_ENDTIME = "endtime";
  public static final String NGSILDQUERY_GEOPROPERTY = "geoproperty";
  public static final String NGSILDQUERY_TIMEPROPERTY = "timeproperty";
  public static final String NGSILDQUERY_MAXDISTANCE = "maxdistance";
  public static final String NGSILDQUERY_MINDISTANCE = "mindistance";
  public static final String IUDXQUERY_OPTIONS = "options";
  public static final String NGSILDQUERY_ENTITIES = "entities";
  public static final String NGSILDQUERY_TEMPORALQ = "temporalQ";
  public static final String NGSILDQUERY_TIME_PROPERTY = "timeProperty";
  public static final String NGSILDQUERY_FROM = "offset";
  public static final String NGSILDQUERY_SIZE = "limit";
  public static final String NGSILDQUERY_GEOQ = "geoQ";
  public static final String IUDX_SEARCH_TYPE = "searchType";

  // json fields
  public static final String JSON_INSTANCEID = "instanceID";
  public static final String JSON_TYPE = "type";
  public static final String JSON_ENTITIES = "entities";
  public static final String JSON_ID = "id";
  public static final String JSON_ATTRIBUTE_FILTER = "attrs";
  public static final String JSON_DURING = "during";
  public static final String JSON_TIME = "time";
  public static final String JSON_ENDTIME = "endtime";
  public static final String JSON_TIMEREL = "timerel";
  public static final String JSON_ATTR_QUERY = "attr-query";
  public static final String JSON_ATTRIBUTE = "attribute";
  public static final String JSON_OPERATOR = "operator";
  public static final String JSON_VALUE = "value";
  public static final String JSON_TITLE = "title";
  public static final String JSON_DETAIL = "detail";
  public static final String JSON_COUNT = "Count";
  public static final String IID = "iid";
  public static final String API = "api";
  public static final String USER_ID = "userid";
  public static final String RESOURCE_GROUP = "resourceGroup";
  public static final String TYPE_KEY = "type";
  public static final String GEO_QUERY = "geo-query";
  public static final String TEMPORAL_QUERY = "temporal-query";

  // searchtype
  public static final String JSON_SEARCH_TYPE = "searchType";
  public static final String JSON_TEMPORAL_SEARCH = "temporalSearch_";
  public static final String JSON_ATTRIBUTE_SEARCH = "attributeSearch_";

  // request/response params
  public static final String CONTENT_TYPE = "content-type";
  public static final String APPLICATION_JSON = "application/json";

  public static final String MSG_BAD_QUERY = "Bad query";

  // Validations
  public static final int VALIDATION_ID_MAX_LEN = 512;
  public static final Pattern VALIDATION_ID_PATTERN =
      Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
  public static final int VALIDATION_MAX_ATTRS = 5;
  public static final int VALIDATION_MAX_DAYS_INTERVAL_ALLOWED_FOR_ASYNC = 365;
  public static final int VALIDATION_MAX_DAYS_INTERVAL_ALLOWED = 10;
  public static final int VALIDATION_COORDINATE_PRECISION_ALLOWED = 6;
  public static final int VALIDATIONS_MAX_ATTR_LENGTH = 100;
  public static final int VALIDATION_ALLOWED_COORDINATES = 10;
  public static final String ENCODED_PUBLIC_KEY_REGEX = "^[a-zA-Z0-9_-]{42,43}={0,2}$";

  public static final String RESPONSE_SIZE = "response_size";
  public static final String PROVIDER_ID = "providerID";

  public static final double VALIDATION_ALLOWED_DIST = 1000.0;
  public static final double VALIDATION_ALLOWED_DIST_FOR_ASYNC = 10000.0;
  public static final int VALIDATION_PAGINATION_LIMIT_MAX = 5000;
  public static final int VALIDATION_PAGINATION_OFFSET_MAX = 49999;
  public static final List<Object> VALIDATION_ALLOWED_GEOM =
      List.of("Point", "point", "Polygon", "polygon", "LineString", "linestring", "bbox");
  public static final List<Object> VALIDATION_ALLOWED_GEOPROPERTY = List.of("location", "Location");
  public static final List<String> VALIDATION_ALLOWED_OPERATORS =
      List.of(">", "=", "<", ">=", "<=", "==", "!=");
  public static final List<String> VALIDATION_ALLOWED_TEMPORAL_REL =
      List.of("after", "before", "during", "between");

  public static final String MSG_INVALID_PARAM = "Invalid parameter in request.";
  public static final String JSON_LAT = "lat";
  public static final String JSON_LON = "lon";
  public static final String JSON_RADIUS = "radius";
  public static final String JSON_GEOMETRY = "geometry";
  public static final String JSON_GEOPROPERTY = "geoproperty";
  public static final String JSON_COORDINATES = "coordinates";
  public static final String JSON_GEOREL = "georel";
  public static final String JSON_WITHIN = "within";
  public static final String GEOM_POINT = "point";
  public static final String JSON_NEAR = "near";
  public static final String JSON_BETWEEN = "between";
  public static final String JSON_MAXDISTANCE = "maxdistance";
  public static final String JSON_MINDISTANCE = "mindistance";
  public static final String JSON_GEO_SEARCH = "geoSearch_";
  public static final String JSON_LATEST_SEARCH = "latestSearch_";

  public static final String EPOCH_TIME = "epochTime";
  public static final String ISO_TIME = "isoTime";
  public static final String FAILED = "Failed";
  public static final String TABLE_NAME = "tableName";

  // api documentation
  public static final String ROUTE_DOC = "/apis";
  public static final String FORMAT = "text/html";
  public static final String ROUTE_STATIC_SPEC = "/apis/spec";
  public static final String FORMAT_JSON = "application/json";
  public static final String LIMITPARAM = "limit";
  public static final String OFFSETPARAM = "offset";

  public static final String ITEM_TYPE_RESOURCE = "Resource";
  public static final String ITEM_TYPE_RESOURCE_GROUP = "ResourceGroup";
  public static final String ITEM_TYPE_RESOURCE_SERVER = "ResourceServer";
  public static final String ITEM_TYPE_PROVIDER = "Provider";
  public static final String MIME_APPLICATION_JSON = "application/json";

  public static final String IS_TIME_LIMIT_ENABLED = "isTimeLimitEnabled";
  public static final ArrayList<String> ITEM_TYPES =
      new ArrayList<String>(
          Arrays.asList(
              ITEM_TYPE_RESOURCE,
              ITEM_TYPE_RESOURCE_GROUP,
              ITEM_TYPE_RESOURCE_SERVER,
              ITEM_TYPE_PROVIDER));
}
