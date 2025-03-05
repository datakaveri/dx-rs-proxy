package iudx.rs.proxy.apiserver.connector.model;

public class ConnectorResultContainer {
  private String apiKey;
  private String userId;
  private String connectorId;
  private String vhost;
  private boolean isQueueCreated;

  // Default Constructor
  public ConnectorResultContainer() {}

  // Getters and Setters
  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getConnectorId() {
    return connectorId;
  }

  public void setConnectorId(String connectorId) {
    this.connectorId = connectorId;
  }

  public String getVhost() {
    return vhost;
  }

  public void setVhost(String vhost) {
    this.vhost = vhost;
  }

  public boolean isQueueCreated() {
    return isQueueCreated;
  }

  public void setQueueCreated(boolean isQueueCreated) {
    this.isQueueCreated = isQueueCreated;
  }

  @Override
  public String toString() {
    return "ConnectorResultContainer{"
        + "apiKey='"
        + apiKey
        + '\''
        + ", userid='"
        + userId
        + '\''
        + ", connectorId='"
        + connectorId
        + '\''
        + ", vhost='"
        + vhost
        + '\''
        + ", isQueueCreated="
        + isQueueCreated
        + '}';
  }
}
