package iudx.rs.proxy.databroker;

import static iudx.rs.proxy.apiserver.util.ApiServerConstants.*;
import static iudx.rs.proxy.common.ResponseUrn.SUCCESS_URN;
import static iudx.rs.proxy.databroker.util.Constants.*;
import static iudx.rs.proxy.metering.util.Constants.SUCCESS;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AMQP.Queue.DeclareOk;
import com.rabbitmq.client.BasicProperties;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rabbitmq.QueueOptions;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQConsumer;
import iudx.rs.proxy.common.Response;
import iudx.rs.proxy.common.ResponseUrn;
import iudx.rs.proxy.databroker.connector.ConnectorService;
import iudx.rs.proxy.databroker.connector.connectorServiceImpl;
import iudx.rs.proxy.databroker.util.Util;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DatabrokerServiceImpl implements DatabrokerService {

  private static final Logger LOGGER = LogManager.getLogger(DatabrokerServiceImpl.class);

  private final String replyQueue;
  private final String publishEx;
  private final int databrokerPort;
  private final String databrokerIp;
  private final String vhost;
  private final Vertx vertx;
  private final QueueOptions queueOption =
      new QueueOptions().setMaxInternalQueueSize(2).setAutoAck(false).setKeepMostRecent(true);
  RabbitMQClient client;
  RabbitClient rabbitClient;
  ConnectorService connectorService;

  public DatabrokerServiceImpl(
      Vertx vertx,
      RabbitMQClient rabbitMQClient,
      RabbitClient rabbitClient,
      final String publishEx,
      final String replyQueue,
      final int amqpsPort,
      final String databrokerIp,
      String vhost) {
    this.vertx = vertx;
    this.client = rabbitMQClient;
    this.replyQueue = replyQueue;
    this.publishEx = publishEx;
    this.rabbitClient = rabbitClient;
    this.databrokerPort = amqpsPort;
    this.databrokerIp = databrokerIp;
    this.vhost = vhost;
    connectorService = new connectorServiceImpl(rabbitClient, amqpsPort, databrokerIp, vhost);

    client.basicQos(1);
  }

  /**
   * create Exchanges, queues and proper bindings for routing, In this case producer and consumer
   * will be before hand know about the exchanges and queus for consuming and pushing messages
   *
   * <p>Issue : negatively acknowledge messages (Nack : in case of correlation id mismatch) there is
   * no way to flag a certain consumer to not consume same message again, thus this creates a
   * repeated consumption of same message (currently tested for single consumer only).
   */
  @Deprecated()
  @Override
  public DatabrokerService executeAdapterQuery(
      JsonObject request, Handler<AsyncResult<JsonObject>> handler) {

    final String corelationId = UUID.randomUUID().toString();
    LOGGER.debug("corelationid : {}", corelationId);
    AMQP.BasicProperties props =
        new AMQP.BasicProperties.Builder().correlationId(corelationId).replyTo(replyQueue).build();

    String routingKey = request.getJsonArray("id").getString(0);

    /*LOGGER.debug("queue declared : {}", replyQueue);*/
    Buffer buffer = Buffer.buffer(request.toString());
    client.basicPublish(
        "rpc-adapter-requests",
        routingKey,
        props,
        buffer,
        publishHandler -> {
          if (publishHandler.succeeded()) {
            LOGGER.debug("published with cid: {}", corelationId);
            client.basicConsumer(
                "rpc_responses",
                queueOption,
                rabbitMQConsumerResult -> {
                  if (rabbitMQConsumerResult.succeeded()) {
                    // LOGGER.info("message consumed : {}", rabbitMQConsumerResult.result());
                    RabbitMQConsumer rmqConsumer = rabbitMQConsumerResult.result();

                    long timerId =
                        vertx.setTimer(
                            5000,
                            timeout -> {
                              LOGGER.info(
                                  "max wait time elapsed for consumer, cancelling consumer");
                              rmqConsumer.cancel();

                              JsonObject json = new JsonObject();
                              json.put("type", ResponseUrn.YET_NOT_IMPLEMENTED_URN.getUrn());
                              json.put("title", "request timed out");
                              json.put(
                                  "details",
                                  "request taking more than allocated time, please contact admin");

                              handler.handle(Future.failedFuture(json.toString()));
                              return;
                            });

                    rmqConsumer.handler(
                        msg -> {
                          LOGGER.debug("Got message: " + msg);
                          BasicProperties properties = msg.properties();
                          String reply_correlationId = properties.getCorrelationId();
                          String replyQueue = properties.getReplyTo();
                          Long deliveryTag = msg.envelope().getDeliveryTag();
                          LOGGER.info(
                              "message consumed corerelationId: {}, replyQ : {}, deliveryTag : {} ",
                              reply_correlationId,
                              replyQueue,
                              deliveryTag);

                          Buffer body = msg.body();
                          if (body != null) {
                            JsonObject json = new JsonObject(msg.body());
                            LOGGER.debug("Got message: " + json);
                            if (reply_correlationId.equals(corelationId)) {
                              LOGGER.info("ack mode");
                              client.basicAck(
                                  deliveryTag,
                                  false,
                                  asyncResult -> {
                                    LOGGER.info(
                                        "response received for consumer, cancelling consumer");
                                    vertx.cancelTimer(timerId);
                                    rmqConsumer.cancel();
                                    handler.handle(Future.succeededFuture(json));
                                  });
                            } else {
                              client.basicNack(
                                  deliveryTag,
                                  true,
                                  true,
                                  resultHandler -> {
                                    LOGGER.info("nack for corelationId : {}", reply_correlationId);
                                  });
                            }
                          } else {
                            LOGGER.info("Empty message received by adapter");
                          }
                        });
                  } else {
                    rabbitMQConsumerResult.cause().printStackTrace();
                  }
                });
          } else {
            handler.handle(Future.failedFuture("failed"));
          }
        });
    return this;
  }

  /**
   * Create an exclusive queue and pass it as message properties to RMQ. message consumer(adaptor)
   * will look for 'reply_to' property in received message and send response in the same queue.
   *
   * <p>Requeued problem as stated above is not a problem in this since, dedicated queue is used for
   * every request/response
   *
   * <p>Since no dedicated exchange is there for responses, so there might be issue for message
   * tracking (not to sure about it)
   */
  @Override
  public DatabrokerService executeAdapterQueryRPC(
      JsonObject request, Handler<AsyncResult<JsonObject>> handler) {
    final String corelationId = UUID.randomUUID().toString();
    final String replyQueueName = UUID.randomUUID().toString();
    Map<String, Object> map = new HashMap<>();
    map.put(HEADER_PUBLIC_KEY, request.getValue(HEADER_PUBLIC_KEY));
    Future<DeclareOk> replyQueueDeclareFuture =
        client.queueDeclare(replyQueueName, false, true, true);

    LOGGER.debug("corelationid : {}", corelationId);
    AMQP.BasicProperties props =
        new AMQP.BasicProperties.Builder()
            .correlationId(corelationId)
            .replyTo(replyQueueName)
            .headers(map)
            .build();
    LOGGER.debug("queue declared : {}", replyQueueName);
    String routingKey =
        request.containsKey("routingKey")
            ? request.getString("routingKey")
            : request.getJsonArray("id").getString(0);
    LOGGER.debug("routing key : {}", routingKey);
    Buffer buffer = Buffer.buffer(request.toString());
    Future<Void> publishFut = client.basicPublish(publishEx, routingKey, props, buffer);

    client.basicConsumer(
        replyQueueName,
        queueOption,
        rabbitMQConsumerResult -> {
            LOGGER.debug("rabbitMQConsumerResult queue : {}", rabbitMQConsumerResult.succeeded());
          if (rabbitMQConsumerResult.succeeded()) {
            RabbitMQConsumer rmqConsumer = rabbitMQConsumerResult.result();

            long timerId =
                vertx.setTimer(
                    300000,
                    timeout -> {
                      LOGGER.info("max wait time elapsed for consumer, cancelling consumer");
                      rmqConsumer.cancel();
                      JsonObject json = new JsonObject();
                      json.put("type", ResponseUrn.YET_NOT_IMPLEMENTED_URN.getUrn());
                      json.put("title", "request timed out");
                      json.put(
                          "details",
                          "request taking more than allocated time, please contact admin");

                      handler.handle(Future.failedFuture(json.toString()));
                      return;
                    });

            rmqConsumer.handler(
                msg -> {
                  LOGGER.debug("Got message: ");
                  BasicProperties properties = msg.properties();
                  String reply_correlationId = properties.getCorrelationId();
                  String replyQueue = properties.getReplyTo();
                  Long deliveryTag = msg.envelope().getDeliveryTag();
                  LOGGER.info(
                      "message consumed corerelationId: {}, replyQ : {}, deliveryTag : {} ",
                      reply_correlationId,
                      replyQueue,
                      deliveryTag);

                  Buffer body = msg.body();
                  if (body != null) {
                    JsonObject json = new JsonObject(msg.body());
                    LOGGER.debug("Got message: ");
                    if (reply_correlationId.equals(corelationId)) {
                      client.basicAck(
                          deliveryTag,
                          false,
                          asyncResult -> {
                            LOGGER.info(
                                "[ACK] Response received for correlationId : {}, cancelling consumer",
                                corelationId);
                            vertx.cancelTimer(timerId);
                            rmqConsumer.cancel();
                            handler.handle(Future.succeededFuture(json));
                          });
                    } else {
                      client.basicNack(
                          deliveryTag,
                          true,
                          true,
                          resultHandler -> {
                            LOGGER.info("[Nack] corelationId : {}", reply_correlationId);
                          });
                      handler.handle(Future.failedFuture("Failed to get the response"));
                    }
                  } else {
                    LOGGER.info("Empty message received by adapter");
                    handler.handle(Future.failedFuture("Empty message received by adapter"));
                  }
                });
          } else {
            rabbitMQConsumerResult.cause().printStackTrace();
          }
        });
    return this;
  }

  @Override
  public DatabrokerService publishMessage(
      JsonObject request,
      String toExchange,
      String routingKey,
      Handler<AsyncResult<JsonObject>> handler) {

    Buffer buffer = Buffer.buffer(request.toString());

    if (!client.isConnected()) {
      client.start();
    }

    client.basicPublish(
        toExchange,
        routingKey,
        buffer,
        publishHandler -> {
          if (publishHandler.succeeded()) {
            JsonObject result = new JsonObject().put("type", SUCCESS);
            handler.handle(Future.succeededFuture(result));
          } else {
            Response respBuilder =
                new Response.Builder()
                    .withTitle(FAILED)
                    .withDetail(publishHandler.cause().getLocalizedMessage())
                    .build();
            handler.handle(Future.failedFuture(respBuilder.toString()));
          }
        });
    return this;
  }

  @Override
  public DatabrokerService createConnector(
      JsonObject request, Handler<AsyncResult<JsonObject>> handler) {
    LOGGER.trace("Info : DatabrokerServiceImpl#createConnector() started");
    request.put(EXCHANGE_NAME, publishEx);
    connectorService
        .registerConnector(request, vhost)
        .onSuccess(
            successRegistration -> {
              JsonObject response = new JsonObject();
              response.put(TYPE, SUCCESS_URN.getUrn());
              response.put(TITLE, "success");
              response.put(RESULTS, new JsonArray().add(successRegistration));
              handler.handle(Future.succeededFuture(response));
            })
        .onFailure(
            registrationFailure ->
                handler.handle(Future.failedFuture(registrationFailure.getMessage())));

    return this;
  }

  @Override
  public DatabrokerService deleteConnector(
      JsonObject request, Handler<AsyncResult<JsonObject>> handler) {
    LOGGER.trace("Info : DatabrokerServiceImpl#deleteConnector() started");
    request.put(EXCHANGE_NAME, publishEx);
    connectorService
        .deleteConnectors(request, vhost)
        .onSuccess(
            successDeletion -> {
              JsonObject response = new JsonObject();
              response.put(TYPE, SUCCESS_URN.getUrn());
              response.put(TITLE, "Success");
              response.put(RESULTS, new JsonArray().add(successDeletion));
              handler.handle(Future.succeededFuture(response));
            })
        .onFailure(
            registrationFailure -> {
              handler.handle(Future.failedFuture(registrationFailure.getMessage()));
            });

    return this;
  }

  @Override
  public DatabrokerService resetPassword(String userid, Handler<AsyncResult<JsonObject>> handler) {
    JsonObject response = new JsonObject();
    String password = Util.randomPassword.get();

    rabbitClient
        .resetPasswordInRmq(userid, password)
        .onSuccess(
            resetPasswordResult -> {
              response.put(TYPE_KEY, SUCCESS_URN.getUrn());
              response.put(TITLE, "successful");
              response.put(DETAIL, "Successfully changed the password");
              JsonArray result =
                  new JsonArray()
                      .add(new JsonObject().put("username", userid).put("apiKey", password));
              response.put("result", result);
              handler.handle(Future.succeededFuture(response));
            })
        .onFailure(
            resetPasswordFailure -> {
              JsonObject failureResponse = new JsonObject();
              failureResponse
                  .put("type", 401)
                  .put("title", "not authorized")
                  .put("detail", "not authorized");
              handler.handle(Future.failedFuture(failureResponse.toString()));
            });

    return this;
  }
}
