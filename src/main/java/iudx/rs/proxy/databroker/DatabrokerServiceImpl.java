package iudx.rs.proxy.databroker;

import static iudx.rs.proxy.apiserver.util.ApiServerConstants.FAILED;
import static iudx.rs.proxy.databroker.util.Constants.*;
import static iudx.rs.proxy.databroker.util.Util.*;

import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.rabbitmq.QueueOptions;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQOptions;
import iudx.rs.proxy.common.HttpStatusCode;
import iudx.rs.proxy.common.Response;
import iudx.rs.proxy.common.ResponseUrn;
import iudx.rs.proxy.databroker.model.*;
import iudx.rs.proxy.databroker.util.PermissionOpType;
import iudx.rs.proxy.databroker.util.Vhosts;
import iudx.rs.proxy.metering.util.ResponseBuilder;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DatabrokerServiceImpl implements DatabrokerService {
  private static final Logger LOGGER = LogManager.getLogger(DatabrokerServiceImpl.class);

  private final RabbitMQClient client;
  private final RabbitWebClient webClient;
  private final String publishEx;
  private final int databrokerPort;
  private final String dataBrokerIp;
  private final Vertx vertx;
  private final QueueOptions queueOption =
      new QueueOptions().setMaxInternalQueueSize(2).setAutoAck(false).setKeepMostRecent(true);
  private JsonObject brokerConfig;

  public DatabrokerServiceImpl(
      Vertx vertx,
      RabbitMQClient rabbitMQClient,
      RabbitWebClient rabbitWebClient,
      JsonObject config) {
    this.vertx = vertx;
    this.client = rabbitMQClient;
    this.webClient = rabbitWebClient;
    this.brokerConfig = config;
    this.publishEx = config.getString("adapterQueryPublishExchange");
    this.dataBrokerIp = config.getString("dataBrokerIP");
    this.databrokerPort = config.getInteger("brokerAmqpsPort");
    client.basicQos(1);
  }

  private RabbitMQClient getRabbitmqClient(Vertx vertx, RabbitMQOptions rabbitConfigs) {
    return RabbitMQClient.create(vertx, rabbitConfigs);
  }

  @Override
  public Future<QueueResponse> createQueue(QueueRequest queueRequest) {
    LOGGER.trace("Info : RabbitClient#createQueue() started");
    Promise<QueueResponse> promise = Promise.promise();

    if (queueRequest != null) {
      JsonObject configProp = new JsonObject();
      JsonObject arguments = new JsonObject();

      arguments
          .put(X_MESSAGE_TTL_NAME, X_MESSAGE_TTL_VALUE)
          .put(X_MAXLENGTH_NAME, X_MAXLENGTH_VALUE)
          .put(X_QUEUE_MODE_NAME, X_QUEUE_MODE_VALUE);

      configProp.put(X_QUEUE_TYPE, true);
      configProp.put(X_QUEUE_ARGUMENTS, arguments);

      String queueName = queueRequest.getResourceId();
      String vhost = getVhost(queueRequest.getVhostType());
      String url = "/api/queues/" + vhost + "/" + encodeValue(queueName);

      LOGGER.info("Queue creation initiated for QueueName: {}", queueName);
      LOGGER.debug("URL for Queue Creation: {}", url);

      webClient
          .requestAsync(REQUEST_PUT, url, configProp)
          .onComplete(
              ar -> {
                if (ar.succeeded()) {
                  HttpResponse<Buffer> response = ar.result();
                  int status = response.statusCode();
                  LOGGER.debug("Queue creation response status: {}", status);

                  if (status == HttpStatus.SC_CREATED) {
                    // Success Response
                    LOGGER.info("Queue created successfully: {}", queueName);

                    QueueResponse queueResponse = new QueueResponse();
                    queueResponse.setQueueName(queueName);
                    queueResponse.setUrl(dataBrokerIp);
                    queueResponse.setPort(databrokerPort);
                    queueResponse.setvHost(vhost);
                    promise.complete(queueResponse);
                  } else if (status == HttpStatus.SC_NO_CONTENT) {
                    // Queue already exists
                    LOGGER.warn("Queue already exists: {}", queueName);
                    promise.fail(
                        getResponseJson(
                                HttpStatus.SC_CONFLICT,
                                HttpStatusCode.CONFLICT.getUrn(),
                                QUEUE_ALREADY_EXISTS)
                            .toString());
                  } else if (status == HttpStatus.SC_BAD_REQUEST) {
                    // Conflicting properties
                    LOGGER.error(
                        "Queue creation failed due to conflicting properties for StatusCode={}, QueueName={}",
                        status,
                        queueName);

                    promise.fail(
                        getResponseJson(
                                status, FAILURE, QUEUE_ALREADY_EXISTS_WITH_DIFFERENT_PROPERTIES)
                            .toString());
                  }
                } else {
                  // Request failed
                  LOGGER.error(
                      "Queue creation failed for QueueName: {}. Cause: {}",
                      queueName,
                      ar.cause().getMessage());

                  promise.fail(
                      getResponseJson(400, FAILURE, QUEUE_ALREADY_EXISTS_WITH_DIFFERENT_PROPERTIES)
                          .toString());
                }
              });
    } else {
      // Invalid Request
      LOGGER.error("Invalid request: Request body is null or empty.");

      promise.fail(
          getResponseJson(400, ResponseUrn.INVALID_PARAM_URN.getUrn(), "Invalid request payload")
              .toString());
    }

    return promise.future();
  }

  /**
   * The deleteQueue implements the delete queue operation.
   *
   * @param queueName which is a Json object
   * @return response which is a Future object of promise of Json type
   */
  @Override
  public Future<String> deleteQueue(String queueName, Vhosts vhostType) {
    LOGGER.trace("RabbitClient#deleteQueue() started");
    Promise<String> promise = Promise.promise();

    LOGGER.debug("DeleteQueue request received: {}", queueName);

    // Input Validation
    if ((queueName == null) || queueName.isEmpty()) {
      LOGGER.warn("Invalid request: queueName is null or empty");
      ResponseBuilder responseBuilder =
          new ResponseBuilder(FAILED)
              .setTypeAndTitle(HttpStatus.SC_BAD_REQUEST, ResponseUrn.INVALID_PARAM_URN.getUrn())
              .setMessage("Invalid request: queueName is null or empty");
      promise.fail(responseBuilder.getResponse().toString());
    }

    String vhost = getVhost(vhostType);

    // Construct URL for Delete Request
    String url = "/api/queues/" + vhost + "/" + encodeValue(queueName);
    LOGGER.debug("Constructed URL for deleting queue: {}", url);

    // Asynchronous Delete Request
    webClient
        .requestAsync(REQUEST_DELETE, url)
        .onComplete(
            ar -> {
              if (ar.succeeded()) {
                HttpResponse<Buffer> response = ar.result();
                int status = response.statusCode();
                LOGGER.debug(
                    "Received response: Status Code = {}, Body = {}",
                    status,
                    response.bodyAsString());

                // Handle Different Status Codes
                if (status == HttpStatus.SC_NO_CONTENT) {
                  // Queue Deleted Successfully
                  LOGGER.info("Queue deleted successfully: {}", queueName);
                  promise.complete(queueName);

                } else if (status == HttpStatus.SC_NOT_FOUND) {
                  // Queue Not Found
                  ResponseBuilder responseBuilder =
                      new ResponseBuilder(FAILED)
                          .setTypeAndTitle(404, ResponseUrn.QUEUE_NOT_FOUND_URN.getUrn());
                  promise.fail(responseBuilder.getResponse().toString());
                }
              } else {
                // Request Failed
                LOGGER.error(
                    "Failed to delete queue: {}. Cause: {}", queueName, ar.cause().getMessage());
                ResponseBuilder responseBuilder =
                    new ResponseBuilder(FAILED)
                        .setTypeAndTitle(500, ResponseUrn.INTERNAL_SERVER_ERROR_URN.getMessage());
                promise.fail(responseBuilder.getResponse().toString());
              }
            });

    return promise.future();
  }

  /**
   * The bindQueue implements the bind queue to exchange by routing key.
   *
   * @param queueRequest which is a Json object
   * @return response which is a Future object of promise of Json type
   */
  @Override
  public Future<QueueResponse> bindQueue(QueueRequest queueRequest) {
    LOGGER.trace("Info : RabbitClient#bindQueue() started");
    Promise<QueueResponse> promise = Promise.promise();

    if (queueRequest != null && queueRequest.getResourceId() != null) {
      String queueName = queueRequest.getResourceId();
      String routingKey = queueRequest.getResourceId();
      JsonObject request = queueRequest.toJson();
      request.put("routing_key", routingKey);
      String vhost = getVhost(queueRequest.getVhostType());

      String url =
          "/api/bindings/"
              + vhost
              + "/e/"
              + encodeValue(publishEx)
              + "/q/"
              + encodeValue(queueName);
      LOGGER.debug("URL for queue binding : {}", url);

      webClient
          .requestAsync(REQUEST_POST, url, request)
          .onComplete(
              ar -> {
                if (ar.succeeded()) {
                  HttpResponse<Buffer> response = ar.result();
                  int status = response.statusCode();
                  LOGGER.debug("Queue binding response status: {}", status);

                  if (status == HttpStatus.SC_CREATED) {
                    // Success Response
                    LOGGER.info("Queue bound successfully: {}", queueName);

                    QueueResponse queueResponse = new QueueResponse();
                    queueResponse.setQueueName(queueName);
                    queueResponse.setUrl(dataBrokerIp);
                    queueResponse.setPort(databrokerPort);
                    queueResponse.setvHost(vhost);

                    promise.complete(queueResponse);
                  } else if (status == HttpStatus.SC_NOT_FOUND) {

                    ResponseBuilder responseBuilder =
                        new ResponseBuilder(FAILED)
                            .setTypeAndTitle(
                                HttpStatus.SC_NOT_FOUND, ResponseUrn.QUEUE_NOT_FOUND_URN.getUrn())
                            .setMessage(QUEUE_EXCHANGE_NOT_FOUND);
                    promise.fail(responseBuilder.getResponse().toString());

                  } else if (status == HttpStatus.SC_UNAUTHORIZED) {
                    ResponseBuilder responseBuilder =
                        new ResponseBuilder(FAILED)
                            .setTypeAndTitle(
                                HttpStatus.SC_FORBIDDEN, ResponseUrn.FORBIDDEN_URN.getUrn())
                            .setMessage("Unauthorized: user doesn't have sufficient permission");
                    promise.fail(responseBuilder.getResponse().toString());
                  }
                } else {
                  LOGGER.error("Empty response from RabbitMQ {}", ar.cause().getLocalizedMessage());
                  ResponseBuilder responseBuilder =
                      new ResponseBuilder(FAILED)
                          .setTypeAndTitle(
                              HttpStatus.SC_INTERNAL_SERVER_ERROR,
                              ResponseUrn.INTERNAL_SERVER_ERROR_URN.getUrn());
                  promise.fail(responseBuilder.getResponse().toString());
                }
              });
    } else {
      ResponseBuilder responseBuilder =
          new ResponseBuilder(FAILED)
              .setTypeAndTitle(HttpStatus.SC_BAD_REQUEST, ResponseUrn.BAD_REQUEST_URN.getUrn())
              .setMessage("Invalid request for binding : queueRequest or resourceId");
      promise.fail(responseBuilder.getResponse().toString());
    }

    return promise.future();
  }

  /**
   * The createUserIfNotExist implements the create user if does not exist.
   *
   * @param userid which is a String
   * @return response which is a Future object of promise of Json type
   */
  @Override
  public Future<UserResponse> createUserIfNotExist(String userid, Vhosts vhostType) {
    LOGGER.trace("RabbitClient#createUserIfNotExist() started for userid: {}", userid);
    String vhost = brokerConfig.getString(Vhosts.IUDX_INTERNAL.value);

    Promise<UserResponse> promise = Promise.promise();
    String url = "/api/users/" + encodeValue(userid);

    LOGGER.debug("Checking if user exists with URL: {}", url);

    // Check if User Exists
    webClient
        .requestAsync(REQUEST_GET, url)
        .onComplete(
            reply -> {
              if (reply.succeeded()) {
                int statusCode = reply.result().statusCode();
                LOGGER.debug(
                    "Received response: Status Code = {}, Body = {}",
                    statusCode,
                    reply.result().bodyAsString());

                /* If user not found, create new user */
                if (statusCode == HttpStatus.SC_NOT_FOUND) {
                  LOGGER.info("User not found: {}. Proceeding to create user.", userid);

                  createUser(userid, url, vhostType)
                      .onComplete(
                          createUserHandler -> {
                            if (createUserHandler.succeeded()) {
                              UserResponse userResponse = createUserHandler.result();
                              LOGGER.info("User creation successful for userid: {}", userid);
                              promise.complete(userResponse);
                            } else {
                              LOGGER.error(
                                  "User creation failed for userid: {}. Cause: {}",
                                  userid,
                                  createUserHandler.cause().getMessage());
                              promise.fail(createUserHandler.cause().getMessage());
                            }
                          });
                }
                /* If user already exists */
                else if (statusCode == HttpStatus.SC_OK) {
                  LOGGER.info("User already exists: {}. Updating permissions.", userid);

                  // Future.future(fu -> updateUserPermissions(userid, PermissionOpType.ADD_WRITE,
                  // "amq.default"));
                  UserPermissionRequest permissionRequest =
                      new UserPermissionRequest.Builder()
                          .userId(userid)
                          .vhostType(vhostType)
                          .type(PermissionOpType.ADD_WRITE)
                          .resourceId("amq.default")
                          .build();
                  Future.future(fu -> updateUserPermissions(permissionRequest));
                  JsonObject response = new JsonObject();
                  response.put(USER_ID, userid);
                  response.put(APIKEY, API_KEY_MESSAGE);
                  response.put(VHOST, vhost);
                  LOGGER.debug("User exists response: {}", response);
                  promise.complete(new UserResponse(response));
                } else {
                  /* Handle unexpected status */
                  LOGGER.warn(
                      "Unexpected status code while checking user existence: {}", statusCode);
                  ResponseBuilder responseBuilder =
                      new ResponseBuilder(FAILED)
                          .setTypeAndTitle(
                              HttpStatus.SC_INTERNAL_SERVER_ERROR,
                              ResponseUrn.INTERNAL_SERVER_ERROR_URN.getUrn())
                          .setMessage("Unexpected status while checking user existence");
                  promise.fail(responseBuilder.getResponse().toString());
                }
              } else {
                // Request Failed
                LOGGER.error(
                    "Failed to check user existence for userid: {}. Cause: {}",
                    userid,
                    reply.cause().getMessage());

                ResponseBuilder responseBuilder =
                    new ResponseBuilder(FAILED)
                        .setTypeAndTitle(
                            HttpStatus.SC_INTERNAL_SERVER_ERROR,
                            ResponseUrn.INTERNAL_SERVER_ERROR_URN.getUrn())
                        .setMessage("Failed to check user existence for userid: " + userid);
                promise.fail(responseBuilder.getResponse().toString());
              }
            });

    return promise.future();
  }

  /* changed the access modifier to default as setTopicPermissions is not being called anywhere */

  /**
   * set vhost permissions for given userName.
   *
   * @param username which is a String
   * @return response which is a Future object of promise of Json type
   */
  private Future<JsonObject> setVhostPermissions(String username, String vhost) {
    LOGGER.trace("Info : RabbitClient#setVhostPermissions() started");

    /* Construct URL to use */
    JsonObject vhostPermissions = new JsonObject();
    // all keys are mandatory. empty strings used for configure, read as not permitted.
    vhostPermissions.put(CONFIGURE, DENY);
    vhostPermissions.put(WRITE, "amq.default");
    vhostPermissions.put(READ, NONE);

    Promise<JsonObject> promise = Promise.promise();

    /* Construct a response object */
    JsonObject vhostPermissionResponse = new JsonObject();
    String url = "/api/permissions/" + vhost + "/" + encodeValue(username);

    webClient
        .requestAsync(REQUEST_PUT, url, vhostPermissions)
        .onComplete(
            handler -> {
              if (handler.succeeded()) {
                /* Check if permission was set */
                if (handler.result().statusCode() == HttpStatus.SC_CREATED) {
                  LOGGER.debug(
                      "Success: write permission set for user [ {} ] in vHost [ {} ]",
                      username,
                      vhost);

                  vhostPermissionResponse.mergeIn(
                      getResponseJson(SUCCESS_CODE, VHOST_PERMISSIONS, VHOST_PERMISSIONS_WRITE));
                  LOGGER.debug("vhostPermissionResponse: {}", vhostPermissionResponse);
                  promise.complete(vhostPermissionResponse);
                } else {
                  // Error: Unexpected status code
                  LOGGER.error(
                      "Error: error in write permission set for user [ {} ] in vHost [ {} ]",
                      username,
                      vhost);

                  // Create error response using ResponseBuilder
                  ResponseBuilder responseBuilder =
                      new ResponseBuilder(FAILED)
                          .setTypeAndTitle(
                              HttpStatus.SC_INTERNAL_SERVER_ERROR,
                              ResponseUrn.INTERNAL_SERVER_ERROR_URN.getUrn())
                          .setMessage("Unexpected error while setting write permission for user");

                  promise.fail(responseBuilder.getResponse().toString());
                }
              } else {
                // Request failed: web client failure
                LOGGER.error(
                    "Error: error in write permission set for user [ {} ] in vHost [ {} ]. Cause: {}",
                    username,
                    vhost,
                    handler.cause().getMessage());

                // Create error response using ResponseBuilder
                ResponseBuilder responseBuilder =
                    new ResponseBuilder(FAILED)
                        .setTypeAndTitle(
                            HttpStatus.SC_INTERNAL_SERVER_ERROR,
                            ResponseUrn.INTERNAL_SERVER_ERROR_URN.getUrn())
                        .setMessage("Failed to set write permission for user");

                promise.fail(responseBuilder.getResponse().toString());
              }
            });

    return promise.future();
  }

  private Future<JsonObject> getUserPermissions(String userId, Vhosts vhostType) {
    LOGGER.trace("Info : RabbitClient#getUserpermissions() started");
    Promise<JsonObject> promise = Promise.promise();
    String url = "/api/permissions/" + getVhost(vhostType) + "/" + userId;

    webClient
        .requestAsync(REQUEST_GET, url)
        .onComplete(
            handler -> {
              if (handler.succeeded()) {
                HttpResponse<Buffer> rmqResponse = handler.result();
                if (rmqResponse.statusCode() == HttpStatus.SC_NOT_FOUND) {
                  LOGGER.info("setting defaoult permission for new user");
                  JsonObject vhostPermissions = new JsonObject();
                  // all keys are mandatory. empty strings used for configure,read as not
                  // permitted.
                  vhostPermissions.put(CONFIGURE, DENY);
                  vhostPermissions.put(WRITE, NONE);
                  vhostPermissions.put(READ, NONE);
                  promise.complete(vhostPermissions);
                }
                if (rmqResponse.statusCode() == HttpStatus.SC_OK) {
                  JsonObject permissionArray = new JsonObject(rmqResponse.body().toString());
                  promise.complete(permissionArray);
                } else {
                  LOGGER.error(handler.cause());
                  LOGGER.error(handler.result());
                  Response response =
                      new Response.Builder()
                          .withStatus(rmqResponse.statusCode())
                          .withTitle(ResponseUrn.BAD_REQUEST_URN.getUrn())
                          .withDetail("problem while getting user permissions")
                          .withUrn(ResponseUrn.BAD_REQUEST_URN.getUrn())
                          .build();
                  promise.fail(response.toString());
                }
              } else {
                Response response =
                    new Response.Builder()
                        .withStatus(HttpStatus.SC_BAD_REQUEST)
                        .withTitle(ResponseUrn.BAD_REQUEST_URN.getUrn())
                        .withDetail(handler.cause().getLocalizedMessage())
                        .withUrn(ResponseUrn.BAD_REQUEST_URN.getUrn())
                        .build();
                promise.fail(response.toString());
              }
            });
    return promise.future();
  }

  @Override
  public Future<RabbitMQPermission> updateUserPermissions(UserPermissionRequest permissionRequest) {
    LOGGER.trace(
        "RabbitClient#updateUserPermissions() started for userId: {}, type: {}, resourceId: {}",
        permissionRequest.getUserId(),
        permissionRequest.getType(),
        permissionRequest.getResourceId());

    Promise<RabbitMQPermission> promise = Promise.promise();

    // Get Existing Permissions
    getUserPermissions(permissionRequest.getUserId(), permissionRequest.getVhostType())
        .onComplete(
            permissionHandler -> {
              if (permissionHandler.succeeded()) {
                LOGGER.debug("permissions retrieved from RMMQ " + permissionHandler.result());
                RabbitMQPermission existingPermissions =
                    new RabbitMQPermission(permissionHandler.result());
                LOGGER.debug(
                    "Existing permissions for user {}: {}",
                    permissionRequest.getUserId(),
                    existingPermissions.toJson());

                // Update Permissions
                RabbitMQPermission updatedPermission =
                    getUpdatedPermission(
                        existingPermissions,
                        permissionRequest.getType(),
                        permissionRequest.getResourceId());
                LOGGER.debug("Updated permissions to be applied: {}", updatedPermission.toJson());

                // Construct URL
                String url =
                    "/api/permissions/"
                        + getVhost(permissionRequest.getVhostType())
                        + "/"
                        + encodeValue(permissionRequest.getUserId());
                LOGGER.debug("URL for updating permissions: {}", url);

                // Send Updated Permissions
                webClient
                    .requestAsync(REQUEST_PUT, url, updatedPermission.toJson())
                    .onComplete(
                        updateHandler -> {
                          if (updateHandler.succeeded()) {
                            HttpResponse<Buffer> response = updateHandler.result();
                            int statusCode = response.statusCode();
                            LOGGER.debug("status code: {} while updating permissions", statusCode);
                            String statusMessage = response.statusMessage();
                            LOGGER.info(
                                "Permissions update response: Status Code = {}, Message = {}",
                                statusCode,
                                statusMessage);

                            if (statusCode == HttpStatus.SC_NO_CONTENT
                                || statusCode == HttpStatus.SC_CREATED) {
                              // Success Response
                              LOGGER.debug(
                                  "Permission update successful for userId: {}",
                                  permissionRequest.getUserId());
                              promise.complete(updatedPermission);
                            } else {
                              // Unexpected Status
                              LOGGER.warn(
                                  "Unexpected status code while updating permissions: {}",
                                  statusCode);

                              // Create error response using ResponseBuilder
                              ResponseBuilder responseBuilder =
                                  new ResponseBuilder(FAILED)
                                      .setTypeAndTitle(
                                          statusCode, ResponseUrn.BAD_REQUEST_URN.getUrn())
                                      .setMessage(
                                          "Unexpected status code while updating permissions. Status Code = ");

                              promise.fail(responseBuilder.getResponse().toString());
                            }
                          } else {
                            // Request Failed (WebClient request)
                            LOGGER.error(
                                "Failed to update permissions for user: {}. Cause: {}",
                                permissionRequest.getUserId(),
                                updateHandler.cause().getMessage());

                            // Create error response using ResponseBuilder
                            ResponseBuilder responseBuilder =
                                new ResponseBuilder(FAILED)
                                    .setTypeAndTitle(
                                        HttpStatus.SC_INTERNAL_SERVER_ERROR,
                                        ResponseUrn.INTERNAL_SERVER_ERROR_URN.getUrn())
                                    .setMessage("Failed to update permissions for user: ");

                            promise.fail(responseBuilder.getResponse().toString());
                          }
                        });
              } else {
                // Failed to Get Existing Permissions
                LOGGER.error(
                    "Failed to get existing permissions for user: {}. Cause: {}",
                    permissionRequest.getUserId(),
                    permissionHandler.cause().getMessage());

                // Create error response using ResponseBuilder
                ResponseBuilder responseBuilder =
                    new ResponseBuilder(FAILED)
                        .setTypeAndTitle(
                            HttpStatus.SC_INTERNAL_SERVER_ERROR,
                            ResponseUrn.INTERNAL_SERVER_ERROR_URN.getUrn())
                        .setMessage("Failed to get existing permissions for user: ");

                promise.fail(responseBuilder.getResponse().toString());
              }
            });

    return promise.future();
  }

  @Override
  public Future<JsonObject> resetPasswordInRmq(String userid, String password) {
    LOGGER.trace("Info : RabbitClient#resetPassword() started");
    Promise<JsonObject> promise = Promise.promise();
    JsonObject response = new JsonObject();
    JsonObject arg = new JsonObject();
    arg.put(PASSWORD, password);
    arg.put(TAGS, NONE);
    String url = "/api/users/" + userid;
    webClient
        .requestAsync(REQUEST_PUT, url, arg)
        .onComplete(
            ar -> {
              if (ar.succeeded()) {
                if (ar.result().statusCode() == HttpStatus.SC_NO_CONTENT) {
                  response.put(USER_NAME, userid);
                  response.put(APIKEY, password);
                  LOGGER.debug("user password changed");
                  promise.complete(response);
                } else {
                  LOGGER.error("Error :reset pwd method failed", ar.cause());

                  ResponseBuilder responseBuilder =
                      new ResponseBuilder(FAILED)
                          .setTypeAndTitle(
                              HttpStatus.SC_INTERNAL_SERVER_ERROR,
                              ResponseUrn.INTERNAL_SERVER_ERROR_URN.getUrn());
                  promise.fail(responseBuilder.getResponse().encode());
                }
              } else {
                LOGGER.error("User creation failed using mgmt API :", ar.cause());

                ResponseBuilder responseBuilder =
                    new ResponseBuilder(FAILED)
                        .setTypeAndTitle(
                            HttpStatus.SC_INTERNAL_SERVER_ERROR,
                            ResponseUrn.INTERNAL_SERVER_ERROR_URN.getUrn())
                        .setMessage(CHECK_CREDENTIALS);
                promise.fail(responseBuilder.getResponse().encode());
              }
            });
    return promise.future();
  }

  /**
   * CreateUserIfNotPresent's helper method which creates user if not present.
   *
   * @param userId which is a String
   * @return response which is a Future object of promise of Json type
   */
  private Future<UserResponse> createUser(String userId, String url, Vhosts vhostType) {
    LOGGER.trace("Info : RabbitClient#createUser() started");

    Promise<UserResponse> promise = Promise.promise();
    String password = randomPassword.get();
    String vhost = getVhost(vhostType);

    JsonObject arg = new JsonObject();
    arg.put(PASSWORD, password);
    arg.put(TAGS, NONE);

    // Send Request to Create User
    webClient
        .requestAsync(REQUEST_PUT, url, arg)
        .onComplete(
            ar -> {
              if (ar.succeeded()) {
                int statusCode = ar.result().statusCode();
                LOGGER.info("Create user response status: {}", statusCode);

                if (statusCode == HttpStatus.SC_CREATED) {
                  // User Created Successfully
                  LOGGER.debug("Info : user created successfully");

                  // Set vHost Permissions
                  setVhostPermissions(userId, vhost)
                      .onComplete(
                          handler -> {
                            if (handler.succeeded()) {
                              UserResponse userResponse = new UserResponse();
                              userResponse.setUserId(userId);
                              userResponse.setApiKey(password);
                              userResponse.setVhostl(vhost);
                              promise.complete(userResponse);
                            } else {
                              LOGGER.error(
                                  "Error: Failed to set vHost permissions. Cause: {}",
                                  handler.cause().getMessage());

                              ResponseBuilder responseBuilder =
                                  new ResponseBuilder(FAILED)
                                      .setTypeAndTitle(
                                          HttpStatus.SC_INTERNAL_SERVER_ERROR,
                                          ResponseUrn.INTERNAL_SERVER_ERROR_URN.getUrn())
                                      .setMessage("Error : error while setting vhostPermissions");
                              promise.fail(responseBuilder.getResponse().toString());
                            }
                          });
                } else {
                  // Unexpected Status Code
                  LOGGER.error(
                      "Unexpected status code while creating user: {}",
                      ar.cause().getLocalizedMessage());

                  ResponseBuilder responseBuilder =
                      new ResponseBuilder(FAILED)
                          .setTypeAndTitle(
                              HttpStatus.SC_INTERNAL_SERVER_ERROR,
                              ResponseUrn.INTERNAL_SERVER_ERROR_URN.getUrn())
                          .setMessage("Unexpected status while creating user");
                  promise.fail(responseBuilder.getResponse().toString());
                }
              } else {
                // Request Failed
                LOGGER.error("Error: Failed to create user. Cause: {}", ar.cause().getMessage());

                ResponseBuilder responseBuilder =
                    new ResponseBuilder(FAILED)
                        .setTypeAndTitle(
                            HttpStatus.SC_INTERNAL_SERVER_ERROR,
                            ResponseUrn.INTERNAL_SERVER_ERROR_URN.getUrn())
                        .setMessage(CHECK_CREDENTIALS);
                promise.fail(responseBuilder.getResponse().toString());
              }
            });

    return promise.future();
  }

  private RabbitMQPermission getUpdatedPermission(
      RabbitMQPermission permission, PermissionOpType type, String permissionId) {
    StringBuilder permissionBuilder;
    JsonObject permissionsJson = permission.toJson(); // Convert to JsonObject for easy manipulation

    switch (type) {
      case ADD_READ:
      case ADD_WRITE:
        permissionBuilder = new StringBuilder(permissionsJson.getString(type.permission));
        // Check if permission already contains resourceId
        if (!permissionBuilder.isEmpty() && permissionBuilder.indexOf(permissionId) == -1) {
          // Remove ".*" if present at the start
          if (permissionBuilder.indexOf(".*") != -1) {
            permissionBuilder.deleteCharAt(0).deleteCharAt(0);
          }
          // Append resourceId if it's not empty
          if (!permissionBuilder.isEmpty()) {
            permissionBuilder.append("|").append(permissionId);
          } else {
            permissionBuilder.append(permissionId);
          }
          // Update permissionsJson with the new permission string
          permissionsJson.put(type.permission, permissionBuilder.toString());
        }
        break;

      case DELETE_READ:
      case DELETE_WRITE:
        permissionBuilder = new StringBuilder(permissionsJson.getString(type.permission));
        String[] permissionsArray = permissionBuilder.toString().split("\\|");
        if (permissionsArray.length > 0) {
          Stream<String> stream = Arrays.stream(permissionsArray);
          String updatedPermission =
              stream.filter(item -> !item.equals(permissionId)).collect(Collectors.joining("|"));
          permissionsJson.put(type.permission, updatedPermission);
        }
        break;

      default:
        break;
    }
    // Convert JsonObject back to Permission and Return
    return new RabbitMQPermission(permissionsJson);
  }

  @Override
  public Future<JsonObject> executeAdapterQueryRPC(JsonObject request) {
    return null;
  }

  private String getVhost(Vhosts vhosts) {
    return brokerConfig.getString(vhosts.value);
  }
}
