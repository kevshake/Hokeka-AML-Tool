package com.posgateway.aml.integration.fix;

import com.posgateway.aml.dto.market.MarketSurveillanceDtos.*;
import com.posgateway.aml.entity.market.MarketOrderSide;
import com.posgateway.aml.entity.market.MarketOrderStatus;
import com.posgateway.aml.service.market.FixMessageEvidenceService;
import com.posgateway.aml.service.market.MarketSurveillanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import quickfix.*;
import quickfix.field.*;
import quickfix.fix44.ExecutionReport;
import quickfix.fix44.Logon;
import quickfix.fix44.NewOrderSingle;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FixMarketDataApplicationTest {
    @Mock MarketSurveillanceService surveillanceService;
    @Mock FixMessageEvidenceService evidenceService;

    private FixMarketDataApplication application;
    private SessionID sessionId;

    @BeforeEach
    void setUp() throws Exception {
        application = new FixMarketDataApplication(surveillanceService, evidenceService);
        sessionId = new SessionID("FIX.4.4", "HOKEKA", "BROKER");
        SessionSettings settings = new SessionSettings();
        settings.setString(sessionId, "ConnectionType", "acceptor");
        settings.setString(sessionId, "HokekaPspId", "9");
        settings.setString(sessionId, "HokekaUsername", "broker-user");
        settings.setString(sessionId, "HokekaPassword", "strong-fix-secret");
        settings.setString(sessionId, "HokekaDefaultCurrency", "KES");
        application.configure(settings);
    }

    @Test
    void newOrderSingleUsesConfiguredCustomerTagAndSharedSurveillanceLogic() throws Exception {
        when(evidenceService.receive(eq(9L), eq(sessionId), any(), anyCollection())).thenReturn(77L);
        NewOrderSingle message = new NewOrderSingle(
                new ClOrdID("ORD-100"), new Side(Side.BUY),
                new TransactTime(LocalDateTime.of(2026, 7, 16, 10, 30)), new OrdType(OrdType.LIMIT));
        message.getHeader().setField(new MsgSeqNum(11));
        message.set(new Account("ACC-7"));
        message.set(new Symbol("SCOM"));
        message.set(new OrderQty(100));
        message.set(new Price(25.50));
        message.set(new quickfix.field.Currency("KES"));
        message.setField(new StringField(9001, "42"));

        OrderResponse response = new OrderResponse(501L, "ORD-100", 42L, "Investor",
                "ACC-7", "SCOM", "SCOM", MarketOrderSide.BUY, "LIMIT",
                new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("25.50"),
                "KES", null, MarketOrderStatus.OPEN, LocalDateTime.now(), null);
        when(surveillanceService.placeOrderForPsp(eq(9L), any()))
                .thenReturn(new SurveillanceResult<>(response, List.of()));

        application.fromApp(message, sessionId);

        ArgumentCaptor<PlaceOrderRequest> request = ArgumentCaptor.forClass(PlaceOrderRequest.class);
        verify(surveillanceService).placeOrderForPsp(eq(9L), request.capture());
        assertEquals(42L, request.getValue().customerId());
        assertEquals("ORD-100", request.getValue().externalOrderId());
        assertEquals("SCOM", request.getValue().instrumentId());
        assertEquals("FIX", request.getValue().metadata().get("source"));
        verify(evidenceService).accepted(77L, 501L, null);
    }

    @Test
    void tradeExecutionReportCreatesExecutionAndLinksReceipt() throws Exception {
        when(evidenceService.receive(eq(9L), eq(sessionId), any(), anyCollection())).thenReturn(77L);
        ExecutionReport message = new ExecutionReport();
        message.getHeader().setField(new MsgSeqNum(12));
        message.set(new ClOrdID("ORD-100"));
        message.set(new ExecID("EXEC-2"));
        message.set(new ExecType(ExecType.TRADE));
        message.set(new LastQty(10));
        message.set(new LastPx(25.75));
        message.set(new TransactTime(LocalDateTime.of(2026, 7, 16, 10, 31)));

        ExecutionResponse response = new ExecutionResponse(601L, "EXEC-2", 501L,
                "ORD-100", 42L, "SCOM", MarketOrderSide.BUY, new BigDecimal("10"),
                new BigDecimal("25.75"), "KES", LocalDateTime.now(), null);
        when(surveillanceService.recordExecutionForPsp(eq(9L), any()))
                .thenReturn(new SurveillanceResult<>(response, List.of()));

        application.fromApp(message, sessionId);

        verify(surveillanceService).recordExecutionForPsp(eq(9L), argThat(request ->
                "EXEC-2".equals(request.externalExecutionId())
                        && "ORD-100".equals(request.externalOrderId())
                        && request.quantity().compareTo(new BigDecimal("10")) == 0));
        verify(evidenceService).accepted(77L, 501L, 601L);
    }

    @Test
    void acceptorRejectsInvalidLogonCredentials() {
        Logon logon = new Logon();
        logon.set(new Username("broker-user"));
        logon.set(new Password("wrong-secret"));

        assertThrows(RejectLogon.class, () -> application.fromAdmin(logon, sessionId));
    }
}
