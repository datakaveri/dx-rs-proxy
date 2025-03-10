package iudx.rs.proxy.apiserver.auditing.util;

import static iudx.rs.proxy.apiserver.auditing.util.Constants.*;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import iudx.rs.proxy.common.ResponseUrn;

public class ResponseBuilder {
  private final String status;
  private final JsonObject response;

  /** Initialise the object with Success or Failure. */
  public ResponseBuilder(String status) {
    this.status = status;
    response = new JsonObject();
  }

  public ResponseBuilder setTypeAndTitle(int statusCode) {

    if (200 == statusCode) {
      response.put(TYPE_KEY, ResponseUrn.SUCCESS_URN.getUrn());
      response.put(TITLE,  ResponseUrn.SUCCESS_URN.getMessage());
    } else if (204 == statusCode) {
      response.put(TYPE_KEY, statusCode);
      response.put(TITLE,  ResponseUrn.SUCCESS_URN.getMessage());
    } else {
      response.put(TYPE_KEY, statusCode);
      response.put(TITLE, ResponseUrn.BAD_REQUEST_URN.getUrn());
    }
    return this;
  }

  public ResponseBuilder setTypeAndTitle(int statusCode, String title) {
    response.put(TYPE_KEY, statusCode);
    response.put(TITLE, title);
    return this;
  }

  /** Overloaded methods for Error messages. */
  public ResponseBuilder setMessage(String error) {
    response.put(DETAIL, error);
    return this;
  }

  public ResponseBuilder setCount(int count) {
    response.put(RESULT, new JsonArray().add(new JsonObject().put(TOTAL, count)));
    return this;
  }

  public ResponseBuilder setData(JsonArray jsonArray) {
    response.put(RESULT, jsonArray);
    return this;
  }

  public ResponseBuilder setTotalHits(int totalHits) {
    response.put(TOTAL_HITS, totalHits);
    return this;
  }

  public JsonObject getResponse() {
    return response;
  }
}
