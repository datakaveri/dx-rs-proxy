package org.cdpg.dx.rs.proxy.validation.types;

import static iudx.rs.proxy.apiserver.util.ApiServerConstants.*;
import static iudx.rs.proxy.common.ResponseUrn.*;

import iudx.rs.proxy.apiserver.exceptions.DxRuntimeException;
import iudx.rs.proxy.common.HttpStatusCode;
import java.util.regex.Pattern;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class AttrsTypeValidator implements Validator {

  private static final Logger LOGGER = LogManager.getLogger(AttrsTypeValidator.class);

  private final int maxAttrsItems = VALIDATION_MAX_ATTRS;
  private final int maxAttrLength = VALIDATIONS_MAX_ATTR_LENGTH;
  private static final Pattern attrsValueRegex = Pattern.compile("^[a-zA-Z0-9_]+");


  private final String value;
  private final boolean required;

  public AttrsTypeValidator(final String value, final boolean required) {
    this.value = value;
    this.required = required;
  }

  private boolean isValidAttributesCount(final String value) {
    String[] attrs = value.split(",");
    if (attrs.length > maxAttrsItems) {
      throw new DxRuntimeException(failureCode(), INVALID_ATTR_VALUE_URN, failureMessage(value));
    }
    return true;
  }


  private boolean isValidAttributeValue(final String value) {
    String[] attrs = value.split(",");
    for (String attr : attrs) {
      if (attr.length() > maxAttrLength) {
        throw new DxRuntimeException(failureCode(), INVALID_ATTR_VALUE_URN, failureMessage(value));
      }
      if (!attrsValueRegex.matcher(attr).matches()) {
        throw new DxRuntimeException(failureCode(), INVALID_ATTR_VALUE_URN, failureMessage(value));
      }
    }
    return true;
  }

  @Override
  public boolean isValid() {
    LOGGER.debug("value : " + value + "required : " + required);
    if (required && (value == null || value.isBlank())) {
      throw new DxRuntimeException(failureCode(), INVALID_ATTR_VALUE_URN, failureMessage());
    } else {
      if (value == null) {
        return true;
      }
      if (value.isBlank()) {
        throw new DxRuntimeException(failureCode(), INVALID_ATTR_VALUE_URN, failureMessage(value));
      }
    }
    if (!isValidAttributesCount(value)) {
      return false;
    }
    if (!isValidAttributeValue(value)) {
      return false;
    }
    return true;
  }

  @Override
  public int failureCode() {
    return HttpStatusCode.BAD_REQUEST.getValue();
  }

  @Override
  public String failureMessage() {
    return INVALID_ATTR_VALUE_URN.getMessage();
  }
}
