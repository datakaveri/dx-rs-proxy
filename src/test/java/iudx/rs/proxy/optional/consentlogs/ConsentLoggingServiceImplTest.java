package iudx.rs.proxy.optional.consentlogs;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import iudx.rs.proxy.authenticator.model.JwtData;
import iudx.rs.proxy.cache.CacheService;
import iudx.rs.proxy.databroker.service.DatabrokerService;
import iudx.rs.proxy.metering.MeteringService;
import iudx.rs.proxy.optional.consentlogs.dss.PayloadSigningManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(VertxExtension.class)
@ExtendWith(MockitoExtension.class)
class ConsentLoggingServiceImplTest {
    static JwtData jwtData = new JwtData();
    ConsentLoggingService consentLoggingServiceInterface;
    @Mock
    AsyncResult<JsonObject> asyncResult;
    @Mock
    private PayloadSigningManager payloadSigningManager;
    @Mock
    private DatabrokerService databrokerService;
    @Mock
    private CacheService cacheServiceMock;
    private ConsentLoggingServiceImpl consentLoggingService;


    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        consentLoggingServiceInterface = mock(ConsentLoggingServiceImpl.class);
        consentLoggingService = new ConsentLoggingServiceImpl(Vertx.vertx(), payloadSigningManager, databrokerService, cacheServiceMock);
    }

    @Test
    @DisplayName("logging success")
    void testLogSuccess(VertxTestContext vertxTestContext) {
        JsonObject cons = new JsonObject()
                .put("artifact", "0d05fe35-ff14-4476-917c-baa2a9d50aa3")
                .put("ppbNumber", "T13010001107");
        jwtData.setSub("2f1e2980-fdee-4afc-909e-6e308867d85e");
        jwtData.setIid("ri:9a442ef4-0082-4cc6-a2da-64bac5c9a876");
        jwtData.setProvider("5d172738-7259-4e77-8ddc-e524c7be3ce3");
        jwtData.setType("RESOURCE");
        jwtData.setCons(cons);
        JsonObject request = new JsonObject().put("logType", "DATA_DENIED");
        payloadSigningManager = mock(PayloadSigningManager.class);
        when(asyncResult.succeeded()).thenReturn(true);
        when(asyncResult.succeeded()).thenReturn(true);

        when(cacheServiceMock.get(any())).thenReturn(Future.succeededFuture(new JsonObject().put("provider", "provider")));

        doAnswer(new Answer<AsyncResult<JsonObject>>() {
            @Override
            public AsyncResult<JsonObject> answer(InvocationOnMock arg1) throws Throwable {
                ((Handler<AsyncResult<JsonObject>>) arg1.getArgument(1)).handle(asyncResult);
                return null;
            }
        }).when(meteringService).publishMeteringData(any(), any());

        consentLoggingService.log(request, jwtData).onComplete(logHandler -> {
            if (logHandler.succeeded()) {
                JsonObject logJson = logHandler.result();
                System.out.println(logJson);
                assertEquals(cons.getString("artifact"), logJson.getString("artifact"));
                assertEquals(cons.getString("ppbNumber"), logJson.getString("dp_id"));
                assertEquals(jwtData.getType(), logJson.getString("item_type"));
                assertEquals(jwtData.getProvider(), logJson.getString("aip_id"));
                assertTrue(logJson.containsKey("log"));
                assertTrue(logJson.containsKey("origin"));
                vertxTestContext.completeNow();
            } else {
                vertxTestContext.failNow("fail");
            }
        });
    }
}