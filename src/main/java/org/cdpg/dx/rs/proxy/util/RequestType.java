package org.cdpg.dx.rs.proxy.util;

public enum RequestType {
  ENTITY("entity"),
  TEMPORAL("temporal"),
  POST_TEMPORAL("post_temporal_schema.json"),
  POST_ENTITIES("post_entities_schema.json"),
  ASYNC_SEARCH("async_search"),
  ASYNC_STATUS("async_status"),
  POST_CONNECTOR("Post_connector_schema.json");

  private String filename;

  RequestType(String fileName) {
    this.filename = fileName;
  }

  public String getFilename() {
    return this.filename;
  }
}
