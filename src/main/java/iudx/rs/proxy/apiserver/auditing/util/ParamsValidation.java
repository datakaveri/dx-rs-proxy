package iudx.rs.proxy.apiserver.auditing.util;

import static iudx.rs.proxy.apiserver.auditing.util.Constants.*;

import io.vertx.core.json.JsonObject;
import iudx.rs.proxy.apiserver.exceptions.DxRuntimeException;
import iudx.rs.proxy.common.Api;
import iudx.rs.proxy.common.ResponseUrn;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ParamsValidation {
  private static final Logger LOGGER = LogManager.getLogger(ParamsValidation.class);
  private Api api;

  public ParamsValidation(Api api) {
    this.api = api;
  }

  public boolean paramsCheck(JsonObject request) {
    if (request.getString(ENDPOINT).equals("/ngsi-ld/v1/provider/audit")
        && request.getString(PROVIDER_ID) == null) {
      throw new DxRuntimeException(
          400, ResponseUrn.BAD_REQUEST_URN, "Provider id required but not found or null");
    }
    if (request.getString(TIME_RELATION) == null
        || !(request.getString(TIME_RELATION).equals(DURING)
            || request.getString(TIME_RELATION).equals(BETWEEN))) {
      LOGGER.debug("Info: " + TIME_RELATION_NOT_FOUND);
      throw new DxRuntimeException(400, ResponseUrn.BAD_REQUEST_URN, TIME_RELATION_NOT_FOUND);
    }

    if (request.getString(START_TIME) == null || request.getString(END_TIME) == null) {
      throw new DxRuntimeException(400, ResponseUrn.BAD_REQUEST_URN, TIME_NOT_FOUND);
    }

    if (request.getString(USER_ID) == null || request.getString(USER_ID).isEmpty()) {
      LOGGER.debug("Info: " + USERID_NOT_FOUND);
      throw new DxRuntimeException(400, ResponseUrn.BAD_REQUEST_URN, USERID_NOT_FOUND);
    }

    // since + is treated as space in uri
    String startTime = request.getString(START_TIME).trim().replaceAll("\\s", "+");
    String endTime = request.getString(END_TIME).trim().replaceAll("\\s", "+");

    ZonedDateTime zdt;
    try {
      zdt = ZonedDateTime.parse(startTime);
      LOGGER.debug("Parsed time: " + zdt.toString());
      zdt = ZonedDateTime.parse(endTime);
      LOGGER.debug("Parsed time: " + zdt.toString());
    } catch (DateTimeParseException e) {
      LOGGER.error("Invalid Date exception: " + e.getMessage());
      throw new DxRuntimeException(400, ResponseUrn.BAD_REQUEST_URN, INVALID_DATE_TIME);
    }
    ZonedDateTime startZdt = ZonedDateTime.parse(startTime);
    ZonedDateTime endZdt = ZonedDateTime.parse(endTime);

    long zonedDateTimeDayDifference = zonedDateTimeDayDifference(startZdt, endZdt);
    long zonedDateTimeMinuteDifference = zonedDateTimeMinuteDifference(startZdt, endZdt);

    LOGGER.trace(
        "PERIOD between given time day :{} , minutes :{}",
        zonedDateTimeDayDifference,
        zonedDateTimeMinuteDifference);

    if (zonedDateTimeDayDifference < 0 || zonedDateTimeMinuteDifference < 0) {
      throw new DxRuntimeException(400, ResponseUrn.BAD_REQUEST_URN, INVALID_DATE_DIFFERENCE);
    }
    return true;
  }

  private long zonedDateTimeDayDifference(ZonedDateTime startTime, ZonedDateTime endTime) {
    return ChronoUnit.DAYS.between(startTime, endTime);
  }

  private long zonedDateTimeMinuteDifference(ZonedDateTime startTime, ZonedDateTime endTime) {
    return ChronoUnit.MINUTES.between(startTime, endTime);
  }
}
