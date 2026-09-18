package com.posgateway.aml.controller.market;

import com.posgateway.aml.dto.market.MarketSurveillanceDtos.*;
import com.posgateway.aml.dto.market.FixSurveillanceDtos.*;
import com.posgateway.aml.integration.fix.FixEngineLifecycle;
import com.posgateway.aml.service.market.FixMessageEvidenceService;
import com.posgateway.aml.service.market.MarketSurveillanceService;
import com.posgateway.aml.service.security.PspIsolationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/market-surveillance")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','INVESTIGATOR','ANALYST','PSP_ADMIN','PSP_USER')")
public class MarketSurveillanceController {
    private final MarketSurveillanceService service;
    private final FixEngineLifecycle fixEngine;
    private final FixMessageEvidenceService fixEvidenceService;
    private final PspIsolationService isolationService;

    public MarketSurveillanceController(MarketSurveillanceService service,
            FixEngineLifecycle fixEngine,
            FixMessageEvidenceService fixEvidenceService,
            PspIsolationService isolationService) {
        this.service = service;
        this.fixEngine = fixEngine;
        this.fixEvidenceService = fixEvidenceService;
        this.isolationService = isolationService;
    }

    @PostMapping("/orders")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','ANALYST','PSP_ADMIN')")
    public ResponseEntity<SurveillanceResult<OrderResponse>> placeOrder(@Valid @RequestBody PlaceOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.placeOrder(request));
    }

    @PostMapping("/orders/{externalOrderId}/cancel")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','ANALYST','PSP_ADMIN')")
    public SurveillanceResult<OrderResponse> cancelOrder(@PathVariable String externalOrderId,
            @RequestBody CancelOrderRequest request) {
        return service.cancelOrder(externalOrderId, request);
    }

    @PostMapping("/executions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','ANALYST','PSP_ADMIN')")
    public ResponseEntity<SurveillanceResult<ExecutionResponse>> recordExecution(
            @Valid @RequestBody RecordExecutionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.recordExecution(request));
    }

    @GetMapping("/orders")
    public Page<OrderResponse> orders(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) { return service.listOrders(page, size); }

    @GetMapping("/signals")
    public Page<SignalResponse> signals(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) { return service.listSignals(page, size); }

    @PutMapping("/signals/{id}/review")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','COMPLIANCE_OFFICER','INVESTIGATOR','PSP_ADMIN')")
    public SignalResponse review(@PathVariable Long id, @Valid @RequestBody ReviewSignalRequest request) {
        return service.reviewSignal(id, request);
    }

    @GetMapping("/fix/sessions")
    public java.util.List<FixSessionResponse> fixSessions() {
        return fixEngine.sessions(isolationService.getCurrentUserPspId());
    }

    @GetMapping("/fix/messages")
    public Page<FixMessageEventResponse> fixMessages(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return fixEvidenceService.list(page, size);
    }
}
