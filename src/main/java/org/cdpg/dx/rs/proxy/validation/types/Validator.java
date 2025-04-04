package org.cdpg.dx.rs.proxy.validation.types;

public interface Validator {

  boolean isValid();

  int failureCode();

  String failureMessage();

  default String failureMessage(final String value) {
    return failureMessage() + " [ " + value + " ] ";
  }

}
