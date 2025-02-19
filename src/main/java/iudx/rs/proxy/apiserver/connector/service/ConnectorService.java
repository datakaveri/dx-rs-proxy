package iudx.rs.proxy.apiserver.connector.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import iudx.rs.proxy.apiserver.connector.model.DeleteConnectorRequest;
import iudx.rs.proxy.apiserver.connector.model.DeleteConnectorResponse;
import iudx.rs.proxy.apiserver.connector.model.RegisterConnectorRequest;
import iudx.rs.proxy.apiserver.connector.model.RegisterConnectorResponse;

public interface ConnectorService {

    /**
     * create a subscription.
     *
     * @param request subscription json.
     * @return Future object
     */
    Future<RegisterConnectorResponse> registerConnector(RegisterConnectorRequest request);

    /**
     * delete a subscription request.
     *
     * @param request containing id for sub to delete
     * @return Future object
     */
    Future<DeleteConnectorResponse> deleteConnectors(DeleteConnectorRequest request);

    Future<JsonObject> resetPasswordOfRmqUser(String userId);

}
