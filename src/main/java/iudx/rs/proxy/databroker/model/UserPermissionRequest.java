package iudx.rs.proxy.databroker.model;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;
import iudx.rs.proxy.databroker.util.PermissionOpType;
import iudx.rs.proxy.databroker.util.Vhosts;

@DataObject
public class UserPermissionRequest {

    private String userId;
    private Vhosts vhostType;
    private PermissionOpType type;
    private String resourceId;

    // Default Constructor (for DataObject)
    public UserPermissionRequest() {
    }

    // Constructor that accepts JsonObject (for DataObject deserialization)
    public UserPermissionRequest(JsonObject json) {
        this.userId = json.getString("userId");
        this.vhostType = Vhosts.valueOf(json.getString("vhost"));
        this.type = PermissionOpType.valueOf(json.getString("type"));
        this.resourceId = json.getString("resourceId");
    }

    // Convert Object to JsonObject (Manual Conversion)
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        if (userId != null) json.put("userId", userId);
        if (vhostType != null) json.put("vhost", vhostType.name()); // Convert enum to string
        if (type != null) json.put("type", type.name()); // Convert enum to string
        if (resourceId != null) json.put("resourceId", resourceId);
        return json;
    }

    // Getters and Setters
    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Vhosts getVhostType() {
        return vhostType;
    }

    public void setVhostType(Vhosts vhostType) {
        this.vhostType = vhostType;
    }

    public PermissionOpType getType() {
        return type;
    }

    public void setType(PermissionOpType type) {
        this.type = type;
    }

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    // Builder Pattern for creating instances
    public static class Builder {
        private String userId;
        private Vhosts vhostType;
        private PermissionOpType type;
        private String resourceId;

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder vhostType(Vhosts vhostType) {
            this.vhostType = vhostType;
            return this;
        }

        public Builder type(PermissionOpType type) {
            this.type = type;
            return this;
        }

        public Builder resourceId(String resourceId) {
            this.resourceId = resourceId;
            return this;
        }

        // Builds the UserPermissionRequest instance
        public UserPermissionRequest build() {
            UserPermissionRequest request = new UserPermissionRequest();
            request.userId = this.userId;
            request.vhostType = this.vhostType;
            request.type = this.type;
            request.resourceId = this.resourceId;
            return request;
        }
    }
}
