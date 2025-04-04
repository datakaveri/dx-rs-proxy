package org.cdpg.dx.rs.proxy.validation.types;

import static iudx.rs.proxy.apiserver.util.ApiServerConstants.VALIDATION_ALLOWED_DIST;
import static iudx.rs.proxy.apiserver.util.ApiServerConstants.VALIDATION_ALLOWED_DIST_FOR_ASYNC;
import static iudx.rs.proxy.common.ResponseUrn.INVALID_GEO_PARAM_URN;
import static iudx.rs.proxy.common.ResponseUrn.INVALID_GEO_VALUE_URN;

import iudx.rs.proxy.apiserver.exceptions.DxRuntimeException;
import iudx.rs.proxy.common.HttpStatusCode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class DistanceTypeValidator implements Validator {

  private static final Logger LOGGER = LogManager.getLogger(DistanceTypeValidator.class);

  private final String value;
  private final boolean required;
  private final boolean isAsyncQuery;

  public DistanceTypeValidator(final String value, final boolean required) {
    this.value = value;
    this.required = required;
    isAsyncQuery = false;
  }

  public DistanceTypeValidator(
      final String value, final boolean required, final boolean isAsyncQuery) {
    this.value = value;
    this.required = required;
    this.isAsyncQuery = isAsyncQuery;
  }

  private boolean isValidDistance(final String distance) {
    try {
      Double distanceValue = Double.parseDouble(distance);
      if (distanceValue > Integer.MAX_VALUE) {
        LOGGER.error("Validation error : Invalid integer value (Integer overflow).");
        throw new DxRuntimeException(failureCode(), INVALID_GEO_VALUE_URN, failureMessage(value));
      }
      if (isAsyncQuery
          && (distanceValue > VALIDATION_ALLOWED_DIST_FOR_ASYNC || distanceValue < 1)) {
        LOGGER.error("Validation error : Distance outside (1,10000)m range not allowed");
        throw new DxRuntimeException(failureCode(), INVALID_GEO_VALUE_URN, failureMessage(value));
      }
      if (!isAsyncQuery && (distanceValue > VALIDATION_ALLOWED_DIST || distanceValue < 1)) {
        LOGGER.error("Validation error : Distance outside (1,1000)m range not allowed");
        throw new DxRuntimeException(failureCode(), INVALID_GEO_VALUE_URN, failureMessage(value));
      }
    } catch (NumberFormatException ex) {
      LOGGER.error("Validation error : Number format error ( not a valid distance)");
      throw new DxRuntimeException(failureCode(), INVALID_GEO_VALUE_URN, failureMessage(value));
    }
    return true;
  }

  @Override
  public boolean isValid() {
    LOGGER.debug("value : " + value + "required : " + required);
    if (required && (value == null || value.isBlank())) {
      LOGGER.error("Validation error : null or blank value for required mandatory field");
      throw new DxRuntimeException(failureCode(), INVALID_GEO_PARAM_URN, failureMessage());
    } else {
      if (value == null) {
        return true;
      }
      if (value.isBlank()) {
        LOGGER.error("Validation error :  blank value for passed");
        throw new DxRuntimeException(failureCode(), INVALID_GEO_VALUE_URN, failureMessage(value));
      }
    }
    return isValidDistance(value);
  }

  @Override
  public int failureCode() {
    return HttpStatusCode.BAD_REQUEST.getValue();
  }

  @Override
  public String failureMessage() {
    return INVALID_GEO_VALUE_URN.getMessage();
  }
}
