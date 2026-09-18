package com.posgateway.aml.integration.fix;

import com.posgateway.aml.dto.market.MarketSurveillanceDtos.CancelOrderRequest;
import com.posgateway.aml.dto.market.MarketSurveillanceDtos.PlaceOrderRequest;
import com.posgateway.aml.dto.market.MarketSurveillanceDtos.RecordExecutionRequest;
import com.posgateway.aml.entity.market.MarketOrderSide;
import com.posgateway.aml.service.market.FixMessageEvidenceService;
import com.posgateway.aml.service.market.MarketSurveillanceService;
import org.springframework.stereotype.Component;
import quickfix.*;
import quickfix.field.*;
import quickfix.fix44.BusinessMessageReject;
import quickfix.fix44.ExecutionReport;
import quickfix.fix44.NewOrderSingle;
import quickfix.fix44.OrderCancelRequest;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;

@Component
public class FixMarketDataApplication extends MessageCracker implements Application {
    private final MarketSurveillanceService surveillanceService;
    private final FixMessageEvidenceService evidenceService;
    private volatile SessionSettings settings;
    private final ThreadLocal<Long> currentEvidenceId = new ThreadLocal<>();

    public FixMarketDataApplication(MarketSurveillanceService surveillanceService,
            FixMessageEvidenceService evidenceService) {
        this.surveillanceService = surveillanceService;
        this.evidenceService = evidenceService;
    }

    public void configure(SessionSettings settings) {
        this.settings = Objects.requireNonNull(settings);
    }

    @Override
    public void onCreate(SessionID sessionId) {}

    @Override
    public void onLogon(SessionID sessionId) {}

    @Override
    public void onLogout(SessionID sessionId) {}

    @Override
    public void toAdmin(Message message, SessionID sessionId) {
        try {
            if (MsgType.LOGON.equals(message.getHeader().getString(MsgType.FIELD))
                    && "initiator".equalsIgnoreCase(setting(sessionId, "ConnectionType", ""))) {
                message.setField(new Username(requiredSetting(sessionId, "HokekaUsername")));
                message.setField(new Password(requiredSetting(sessionId, "HokekaPassword")));
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Could not prepare FIX administrative message", exception);
        }
    }

    @Override
    public void fromAdmin(Message message, SessionID sessionId)
            throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon {
        if (!MsgType.LOGON.equals(message.getHeader().getString(MsgType.FIELD))
                || !"acceptor".equalsIgnoreCase(setting(sessionId, "ConnectionType", ""))) {
            return;
        }
        String expectedUsername = requiredSetting(sessionId, "HokekaUsername");
        String expectedPassword = requiredSetting(sessionId, "HokekaPassword");
        String username = message.isSetField(Username.FIELD) ? message.getString(Username.FIELD) : "";
        String password = message.isSetField(Password.FIELD) ? message.getString(Password.FIELD) : "";
        if (!constantTimeEquals(expectedUsername, username) || !constantTimeEquals(expectedPassword, password)) {
            throw new RejectLogon("Invalid FIX session credentials");
        }
    }

    @Override
    public void toApp(Message message, SessionID sessionId) throws DoNotSend {}

    @Override
    public void fromApp(Message message, SessionID sessionId)
            throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType {
        Long eventId = evidenceService.receive(pspId(sessionId), sessionId, message, customEvidenceTags(sessionId));
        currentEvidenceId.set(eventId);
        try {
            crack(message, sessionId);
        } catch (UnsupportedMessageType exception) {
            evidenceService.rejected(eventId, "UNSUPPORTED_MESSAGE_TYPE", "Unsupported application message");
            sendBusinessReject(message, sessionId, "Unsupported application message type");
        } catch (Exception exception) {
            evidenceService.rejected(eventId, "MESSAGE_PROCESSING_FAILED", safeMessage(exception));
            sendBusinessReject(message, sessionId, safeMessage(exception));
        } finally {
            currentEvidenceId.remove();
        }
    }

    public void onMessage(NewOrderSingle message, SessionID sessionId) throws FieldNotFound {
        Long eventId = requireEvidenceId();
        Map<String, Object> metadata = baseMetadata(message, sessionId);
        String symbol = optional(message, Symbol.FIELD);
        String instrumentId = firstNonBlank(optional(message, SecurityID.FIELD), symbol);
        if (instrumentId == null) throw new IllegalArgumentException("SecurityID(48) or Symbol(55) is required");
        int customerTag = intSetting(sessionId, "HokekaCustomerIdTag", 9001);
        PlaceOrderRequest request = new PlaceOrderRequest(
                required(message, ClOrdID.FIELD),
                requiredLong(message, customerTag),
                required(message, Account.FIELD),
                optional(message, intSetting(sessionId, "HokekaBeneficialOwnerTag", 9002)),
                instrumentId,
                symbol,
                side(message.getChar(Side.FIELD)),
                orderType(message.getChar(OrdType.FIELD)),
                decimal(message, OrderQty.FIELD, true),
                decimal(message, Price.FIELD, false),
                firstNonBlank(optional(message, quickfix.field.Currency.FIELD),
                        setting(sessionId, "HokekaDefaultCurrency", null)),
                optional(message, ExDestination.FIELD),
                timestamp(message, TransactTime.FIELD, LocalDateTime.now()),
                customTimestamp(message, sessionId, "HokekaMarketCloseAtTag"),
                customDecimal(message, sessionId, "HokekaReferencePriceTag"),
                optional(message, intSetting(sessionId, "HokekaDeviceFingerprintTag", 9009)),
                metadata);
        if (request.currency() == null || request.currency().length() != 3) {
            throw new IllegalArgumentException("Currency(15) or HokekaDefaultCurrency is required");
        }
        var result = surveillanceService.placeOrderForPsp(pspId(sessionId), request);
        evidenceService.accepted(eventId, result.record().id(), null);
    }

    public void onMessage(OrderCancelRequest message, SessionID sessionId) throws FieldNotFound {
        Long eventId = requireEvidenceId();
        String externalOrderId = required(message, OrigClOrdID.FIELD);
        var result = surveillanceService.cancelOrderForPsp(pspId(sessionId), externalOrderId,
                new CancelOrderRequest(timestamp(message, TransactTime.FIELD, LocalDateTime.now()),
                        firstNonBlank(optional(message, Text.FIELD), "FIX OrderCancelRequest")));
        evidenceService.accepted(eventId, result.record().id(), null);
    }

    public void onMessage(ExecutionReport message, SessionID sessionId) throws FieldNotFound {
        Long eventId = requireEvidenceId();
        String externalOrderId = required(message, ClOrdID.FIELD);
        char executionType = message.getChar(ExecType.FIELD);
        if (executionType == ExecType.CANCELED) {
            var result = surveillanceService.cancelOrderForPsp(pspId(sessionId), externalOrderId,
                    new CancelOrderRequest(timestamp(message, TransactTime.FIELD, LocalDateTime.now()),
                            firstNonBlank(optional(message, Text.FIELD), "FIX ExecutionReport cancellation")));
            evidenceService.accepted(eventId, result.record().id(), null);
            return;
        }
        if (executionType == ExecType.REJECTED) {
            var order = surveillanceService.rejectOrderForPsp(pspId(sessionId), externalOrderId,
                    timestamp(message, TransactTime.FIELD, LocalDateTime.now()),
                    firstNonBlank(optional(message, Text.FIELD), "FIX ExecutionReport rejection"),
                    baseMetadata(message, sessionId));
            evidenceService.accepted(eventId, order.id(), null);
            return;
        }
        if (executionType != ExecType.PARTIAL_FILL
                && executionType != ExecType.FILL
                && executionType != ExecType.TRADE) {
            evidenceService.ignored(eventId, "ExecutionReport does not change order or execution state", null);
            return;
        }

        RecordExecutionRequest request = new RecordExecutionRequest(
                required(message, ExecID.FIELD),
                externalOrderId,
                decimal(message, LastQty.FIELD, true),
                decimal(message, LastPx.FIELD, true),
                timestamp(message, TransactTime.FIELD, LocalDateTime.now()),
                optionalLong(message, intSetting(sessionId, "HokekaCounterpartyCustomerIdTag", 9005)),
                firstNonBlank(optional(message, ContraBroker.FIELD),
                        optional(message, intSetting(sessionId, "HokekaCounterpartyReferenceTag", 9006))),
                optional(message, intSetting(sessionId, "HokekaCounterpartyBeneficialOwnerTag", 9007)),
                optional(message, ExDestination.FIELD),
                customTimestamp(message, sessionId, "HokekaMarketCloseAtTag"),
                customDecimal(message, sessionId, "HokekaReferencePriceTag"),
                optional(message, intSetting(sessionId, "HokekaSettlementAccountTag", 9008)),
                baseMetadata(message, sessionId));
        var result = surveillanceService.recordExecutionForPsp(pspId(sessionId), request);
        evidenceService.accepted(eventId, result.record().orderId(), result.record().id());
    }

    public Long pspId(SessionID sessionId) {
        String value = requiredSetting(sessionId, "HokekaPspId");
        try {
            long pspId = Long.parseLong(value);
            if (pspId <= 0) throw new NumberFormatException();
            return pspId;
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("HokekaPspId must be a positive integer for " + sessionId);
        }
    }

    public List<Integer> customEvidenceTags(SessionID sessionId) {
        return List.of(
                intSetting(sessionId, "HokekaCustomerIdTag", 9001),
                intSetting(sessionId, "HokekaBeneficialOwnerTag", 9002),
                intSetting(sessionId, "HokekaReferencePriceTag", 9003),
                intSetting(sessionId, "HokekaMarketCloseAtTag", 9004),
                intSetting(sessionId, "HokekaCounterpartyCustomerIdTag", 9005),
                intSetting(sessionId, "HokekaCounterpartyReferenceTag", 9006),
                intSetting(sessionId, "HokekaCounterpartyBeneficialOwnerTag", 9007),
                intSetting(sessionId, "HokekaSettlementAccountTag", 9008),
                intSetting(sessionId, "HokekaDeviceFingerprintTag", 9009));
    }

    private Map<String, Object> baseMetadata(Message message, SessionID sessionId) throws FieldNotFound {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "FIX");
        metadata.put("fixSessionId", sessionId.toString());
        metadata.put("fixMessageType", message.getHeader().getString(MsgType.FIELD));
        metadata.put("fixMessageSequence", message.getHeader().getInt(MsgSeqNum.FIELD));
        metadata.put("fixMessageHash", sha256(message.toString()));
        return metadata;
    }

    private MarketOrderSide side(char value) {
        return switch (value) {
            case Side.BUY -> MarketOrderSide.BUY;
            case Side.SELL, Side.SELL_SHORT, Side.SELL_SHORT_EXEMPT -> MarketOrderSide.SELL;
            default -> throw new IllegalArgumentException("Unsupported Side(54): " + value);
        };
    }

    private String orderType(char value) {
        return switch (value) {
            case OrdType.MARKET -> "MARKET";
            case OrdType.LIMIT -> "LIMIT";
            case OrdType.STOP -> "STOP";
            case OrdType.STOP_LIMIT -> "STOP_LIMIT";
            default -> "FIX_" + value;
        };
    }

    private BigDecimal decimal(Message message, int tag, boolean required) throws FieldNotFound {
        String value = optional(message, tag);
        if (value == null) {
            if (required) throw new IllegalArgumentException("Required FIX decimal tag is missing: " + tag);
            return null;
        }
        BigDecimal result = new BigDecimal(value);
        if (result.signum() <= 0) throw new IllegalArgumentException("FIX decimal tag must be positive: " + tag);
        return result;
    }

    private BigDecimal customDecimal(Message message, SessionID sessionId, String setting) throws FieldNotFound {
        String tag = setting(sessionId, setting, null);
        return tag == null || tag.isBlank() ? null : decimal(message, Integer.parseInt(tag), false);
    }

    private LocalDateTime customTimestamp(Message message, SessionID sessionId, String setting) throws FieldNotFound {
        String tag = setting(sessionId, setting, null);
        if (tag == null || tag.isBlank()) return null;
        String value = optional(message, Integer.parseInt(tag));
        return value == null ? null : LocalDateTime.parse(value);
    }

    private LocalDateTime timestamp(Message message, int tag, LocalDateTime fallback) throws FieldNotFound {
        return message.isSetField(tag) ? message.getUtcTimeStamp(tag) : fallback;
    }

    private String required(Message message, int tag) throws FieldNotFound {
        String value = optional(message, tag);
        if (value == null) throw new IllegalArgumentException("Required FIX tag is missing: " + tag);
        return value;
    }

    private Long requiredLong(Message message, int tag) throws FieldNotFound {
        Long value = optionalLong(message, tag);
        if (value == null || value <= 0) throw new IllegalArgumentException("Required positive FIX identifier tag is missing: " + tag);
        return value;
    }

    private Long optionalLong(Message message, int tag) throws FieldNotFound {
        String value = optional(message, tag);
        return value == null ? null : Long.valueOf(value);
    }

    private String optional(Message message, int tag) throws FieldNotFound {
        if (tag <= 0 || !message.isSetField(tag)) return null;
        String value = message.getString(tag);
        return value == null || value.isBlank() ? null : value.trim();
    }

    private int intSetting(SessionID sessionId, String key, int defaultValue) {
        String value = setting(sessionId, key, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(key + " must be an integer for " + sessionId);
        }
    }

    private String requiredSetting(SessionID sessionId, String key) {
        String value = setting(sessionId, key, null);
        if (value == null || value.isBlank()) throw new IllegalStateException(key + " is required for " + sessionId);
        return value;
    }

    private String setting(SessionID sessionId, String key, String defaultValue) {
        SessionSettings current = settings;
        if (current == null) throw new IllegalStateException("FIX application has not been configured");
        try {
            return current.isSetting(sessionId, key) ? current.getString(sessionId, key) : defaultValue;
        } catch (ConfigError exception) {
            throw new IllegalStateException("Invalid FIX setting " + key + " for " + sessionId, exception);
        }
    }

    private void sendBusinessReject(Message source, SessionID sessionId, String reason) {
        try {
            BusinessMessageReject reject = new BusinessMessageReject();
            reject.set(new RefMsgType(source.getHeader().getString(MsgType.FIELD)));
            reject.set(new BusinessRejectReason(0));
            reject.set(new Text(reason.substring(0, Math.min(reason.length(), 250))));
            Session.sendToTarget(reject, sessionId);
        } catch (Exception ignored) {
            // The persisted rejected receipt remains the authoritative evidence.
        }
    }

    private Long requireEvidenceId() {
        Long value = currentEvidenceId.get();
        if (value == null) throw new IllegalStateException("FIX message evidence context is missing");
        return value;
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash FIX message", exception);
        }
    }

    private String safeMessage(Exception exception) {
        String value = exception.getMessage();
        return value == null || value.isBlank() ? exception.getClass().getSimpleName()
                : value.substring(0, Math.min(value.length(), 500));
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }
}
