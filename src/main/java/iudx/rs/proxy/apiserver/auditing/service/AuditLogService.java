package iudx.rs.proxy.apiserver.auditing.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import iudx.rs.proxy.apiserver.auditing.model.AuditLogSearchRequest;
import iudx.rs.proxy.apiserver.auditing.model.OverviewRequest;

public interface AuditLogService {

  Future<JsonObject> executeAudigingSearchQuery(AuditLogSearchRequest searchRequest);

  Future<JsonObject> monthlyOverview(OverviewRequest overviewRequest);

  Future<JsonObject> summaryOverview(OverviewRequest overviewRequest);
}
