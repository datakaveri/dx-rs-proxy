package iudx.rs.proxy.apiserver.auditing.util;

import static iudx.rs.proxy.apiserver.auditing.util.Constants.*;

import io.vertx.core.json.JsonObject;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

import iudx.rs.proxy.apiserver.util.ApiServerConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DateValidation {
  private static final Logger LOGGER = LogManager.getLogger(DateValidation.class);

  public static JsonObject dateParamCheck(JsonObject request) {

    // since + is treated as space in uri
    String startTime = request.getString(ApiServerConstants.START_TIME).trim().replaceAll("\\s", "+");
    String endTime = request.getString(ApiServerConstants.END_TIME).trim().replaceAll("\\s", "+");

    ZonedDateTime zdt;
    try {
      zdt = ZonedDateTime.parse(startTime);
      LOGGER.debug("Parsed time: " + zdt.toString());
      zdt = ZonedDateTime.parse(endTime);
      LOGGER.debug("Parsed time: " + zdt.toString());
    } catch (DateTimeParseException e) {
      LOGGER.error("Invalid Date exception: " + e.getMessage());
      return new JsonObject().put(ERROR, INVALID_DATE_TIME);
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
      LOGGER.error(INVALID_DATE_DIFFERENCE);
      return new JsonObject().put(ERROR, INVALID_DATE_DIFFERENCE);
    }
    request.put(ApiServerConstants.START_TIME, startTime);
    request.put(ApiServerConstants.END_TIME, endTime);
    return request;
  }

  private static long  zonedDateTimeDayDifference(ZonedDateTime startTime, ZonedDateTime endTime) {
    return ChronoUnit.DAYS.between(startTime, endTime);
  }

  private static long zonedDateTimeMinuteDifference(ZonedDateTime startTime, ZonedDateTime endTime) {
    return ChronoUnit.MINUTES.between(startTime, endTime);
  }
}
