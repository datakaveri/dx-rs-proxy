package iudx.rs.proxy.common;

public enum DXServiceExceptionCode {
  INTERNAL_SERVER_ERROR(0, 500, "Internal Server Error", "urn:dx:rs:internalServerError"),
  BAD_REQUEST(1, 400, "Bad Request", "urn:dx:rs:badRequest"),
  NOT_FOUND(2, 404, "Not Found", "urn:dx:rs:notFound"),
  CONFLICT(3, 409, "Conflict", "urn:dx:rs:conflict");

  private final int value;
  private final int StatusCode;
  private final String description;
  private final String urn;

  DXServiceExceptionCode(int value, int StatusCode, String description, String urn) {
    this.value = value;
    this.StatusCode = StatusCode;
    this.description = description;
    this.urn = urn;
  }

  // Optional: Method to get an enum based on the value
  public static DXServiceExceptionCode getByValue(int value) {
    for (DXServiceExceptionCode code : DXServiceExceptionCode.values()) {
      if (code.getValue() == value) {
        return code;
      }
    }
    throw new IllegalArgumentException("Unexpected value: " + value);
  }

  public int getStatusCode() {
    return StatusCode;
  }

  public int getValue() {
    return value;
  }

  // Getter for description
  public String getDescription() {
    return description;
  }

  public String getUrn() {
    return urn;
  }
}
