package iudx.rs.proxy.apiserver.auditing.util;

import static iudx.rs.proxy.apiserver.auditing.util.Constants.*;

import iudx.rs.proxy.apiserver.exceptions.DxRuntimeException;
import iudx.rs.proxy.common.ResponseUrn;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class DateValidation {
  private static final Logger LOGGER = LogManager.getLogger(DateValidation.class);

  public static Boolean dateParamCheck(String startTime, String endTime) {

    if (startTime == null && endTime == null) {
      return true;
    }

    if (startTime != null && endTime == null || startTime == null && endTime != null) {
      throw new DxRuntimeException(400, ResponseUrn.BAD_REQUEST_URN, INVALID_DATE_TIME);
    }

    // since + is treated as space in uri
    startTime = startTime.trim().replaceAll("\\s", "+");
    endTime = endTime.trim().replaceAll("\\s", "+");

    ZonedDateTime zdt;
    try {
      zdt = ZonedDateTime.parse(startTime);
      LOGGER.debug("Parsed time: " + zdt);
      zdt = ZonedDateTime.parse(endTime);
      LOGGER.debug("Parsed time: " + zdt);
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
      LOGGER.error(INVALID_DATE_DIFFERENCE);
      throw new DxRuntimeException(400, ResponseUrn.BAD_REQUEST_URN, INVALID_DATE_DIFFERENCE);
    }
    /*request.put(ApiServerConstants.START_TIME, startTime);
    request.put(ApiServerConstants.END_TIME, endTime);*/
    return true;
  }

  private static long zonedDateTimeDayDifference(ZonedDateTime startTime, ZonedDateTime endTime) {
    return ChronoUnit.DAYS.between(startTime, endTime);
  }

  private static long zonedDateTimeMinuteDifference(
      ZonedDateTime startTime, ZonedDateTime endTime) {
    return ChronoUnit.MINUTES.between(startTime, endTime);
  }
}
