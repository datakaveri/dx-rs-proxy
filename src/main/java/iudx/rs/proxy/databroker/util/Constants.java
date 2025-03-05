package iudx.rs.proxy.databroker.util;

public class Constants {

  // Registration response fields used for construction JSON
  public static final String APIKEY = "apiKey";

  public static final String BAD_REQUEST =
      "Bad request : insufficient request data to register adaptor";
  public static final String CHECK_CREDENTIALS =
      "Something went wrong while creating user using mgmt API. Check credentials";
  public static final String CONFIGURE = "configure";
  public static final String CONSUMER = "consumer";
  public static final String USER_ID = "userId";

  public static final String DETAILS = "details";
  public static final String DENY = "";
  public static final String DETAIL = "detail";
  public static final String DATABASE_READ_SUCCESS = "Read Database Success";
  public static final String DATABASE_READ_FAILURE = "Read Database Failed";
  public static final String EXCHANGE = "exchange";
  public static final String EXCHANGE_NAME = "exchangeName";
  public static final String ERROR = "error";
  public static final String FAILURE = "failure";

  public static final String NETWORK_ISSUE = "Network Issue";
  public static final String NONE = "None";

  public static final String PASSWORD = "password";
  public static final int PASSWORD_LENGTH = 16;
  public static final String PORT = "port";

  public static final String QUEUE_NAME = "connectorName";
  public static final String AUDITING_EXCHANGE = "auditing";
  public static final String QUEUE_BIND_ERROR = "error in connector/queue binding with adaptor";
  public static final String QUEUE_DOES_NOT_EXISTS = "Connector does not exist";
  public static final String QUEUE_ALREADY_EXISTS = "Connector already exists";
  public static final String QUEUE_ALREADY_EXISTS_WITH_DIFFERENT_PROPERTIES =
      "Queue already exists with different properties";
  public static final String QUEUE_DELETE_ERROR = "Deletion of Connector failed";
  public static final String QUEUE_CREATE_ERROR = "Creation of Connector failed";
  public static final String QUEUE_EXCHANGE_NOT_FOUND = "Queue/Exchange does not exist";

  public static final String READ = "read";
  public static final String REQUEST_GET = "GET";
  public static final String REQUEST_POST = "POST";
  public static final String REQUEST_PUT = "PUT";
  public static final String REQUEST_DELETE = "DELETE";

  public static final String STATUS = "status";
  public static final String SUCCESS = "success";
  public static final String CONNECTOR_ID = "connectorId";
  public static final String TAGS = "tags";
  public static final String TYPE = "type";
  public static final String TITLE = "title";
  public static final String RESULTS = "results";

  public static final String USER_NAME = "userName";
  public static final String USER_CREATION_ERROR = "User creation failed";

  public static final String URL = "URL";

  public static final String VHOST = "vHost";

  public static final String VHOST_PERMISSIONS_WRITE = "write permission set";
  public static final String VHOST_PERMISSION_SET_ERROR = "Error in setting vHost permissions";
  public static final String VHOST_PERMISSIONS = "vhostPermissions";
  public static final String VHOST_PERMISSIONS_FAILURE = "Error in setting vhostPermissions";

  public static final String WRITE = "write";

  public static final String X_MESSAGE_TTL_NAME = "x-message-ttl";
  public static final String X_MAXLENGTH_NAME = "x-max-length";
  public static final String X_QUEUE_MODE_NAME = "x-queue-mode";
  public static final long X_MESSAGE_TTL_VALUE = 86400000; // 24hours
  public static final int X_MAXLENGTH_VALUE = 10000;
  public static final String X_QUEUE_MODE_VALUE = "lazy";
  public static final String X_QUEUE_TYPE = "durable";
  public static final String X_QUEUE_ARGUMENTS = "arguments";

  public static final int BAD_REQUEST_CODE = 400;
  public static final int INTERNAL_ERROR_CODE = 500;
  public static final int SUCCESS_CODE = 200;
  public static final String INVALID_ROUTING_KEY = "Invalid or null routing key";
  public static final String BINDING_FAILED = "Binding failed";
  public static final String BAD_REQUEST_DATA = "Bad Request data";
  public static final String PAYLOAD_ERROR = "Invalid request payload";
  // sql errors
  public static final String DUPLICATE_KEY = "duplicate key value violates unique constraint";

  // message
  public static final String API_KEY_MESSAGE =
      "Use existing apiKey,if lost please use /resetPassword API";
}
