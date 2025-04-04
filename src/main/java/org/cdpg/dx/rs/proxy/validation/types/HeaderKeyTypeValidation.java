package org.cdpg.dx.rs.proxy.validation.types;

import static iudx.rs.proxy.apiserver.util.ApiServerConstants.ENCODED_PUBLIC_KEY_REGEX;
import static iudx.rs.proxy.common.ResponseUrn.INVALID_HEADER_VALUE_URN;

import iudx.rs.proxy.apiserver.exceptions.DxRuntimeException;
import iudx.rs.proxy.apiserver.handlers.ValidationHandler;
import iudx.rs.proxy.common.HttpStatusCode;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class HeaderKeyTypeValidation implements Validator {
  private static final Logger LOG = LogManager.getLogger(ValidationHandler.class);
  private final String value;
  private final boolean required;

  public HeaderKeyTypeValidation(final String value, final boolean required) {
    this.value = value;
    this.required = required;
  }

  @Override
  public boolean isValid() {
    LOG.info("inside isValid");
    if (required && (value == null || value.isEmpty())) {
      LOG.error("Validation error : Public key is null or empty");
      throw new DxRuntimeException(failureCode(), INVALID_HEADER_VALUE_URN, failureMessage());
    } else if (!required && value == null) {
      return true;
    } else if (!required && value.isEmpty()) {
      LOG.error("Validation error : The value of the public key is empty in the publicKey header");
      throw new DxRuntimeException(failureCode(), INVALID_HEADER_VALUE_URN, failureMessage());
    } else if (value.length() != 44) {
      LOG.error("Validation error : Invalid Public Key length");
      throw new DxRuntimeException(failureCode(), INVALID_HEADER_VALUE_URN, failureMessage());
    } else if (!Pattern.matches(ENCODED_PUBLIC_KEY_REGEX, value)) {
      LOG.error("Validation error : Public key contains invalid urlbase64 character");
      throw new DxRuntimeException(failureCode(), INVALID_HEADER_VALUE_URN, failureMessage());
    }
    return true;
  }

  @Override
  public int failureCode() {
    return HttpStatusCode.BAD_REQUEST.getValue();
  }

  @Override
  public String failureMessage() {
    return INVALID_HEADER_VALUE_URN.getMessage();
  }
}
