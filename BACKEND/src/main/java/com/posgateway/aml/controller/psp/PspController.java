package com.posgateway.aml.controller.psp;



import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.posgateway.aml.entity.User;
import com.posgateway.aml.dto.psp.PspLoginRequest;
import com.posgateway.aml.dto.psp.PspRegistrationRequest;
import com.posgateway.aml.dto.psp.PspResponse;
import com.posgateway.aml.dto.psp.PspStatusUpdateRequest;
import com.posgateway.aml.dto.psp.PspUserCreationRequest;
import com.posgateway.aml.dto.psp.PspUpdateRequest;
import com.posgateway.aml.dto.psp.PspUserResponse;
import com.posgateway.aml.entity.psp.Psp;
import com.posgateway.aml.mapper.PspMapper;
import com.posgateway.aml.service.psp.PspService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

// @Slf4j removed
// @RequiredArgsConstructor removed
@RestController
@RequestMapping("/psps")
public class PspController {

    private static final Logger log = LoggerFactory.getLogger(PspController.class);

    private final PspService pspService;
    private final PspMapper pspMapper;

    public PspController(PspService pspService, PspMapper pspMapper) {
        this.pspService = pspService;
        this.pspMapper = pspMapper;
    }


    @PostMapping
    public ResponseEntity<PspResponse> registerPsp(@RequestBody PspRegistrationRequest request) {
        log.info("Received PSP registration request");
        Psp psp = pspService.registerPsp(request);
        return ResponseEntity.ok(pspMapper.toResponse(psp));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Void> updatePspStatus(@PathVariable Long id, @RequestBody PspStatusUpdateRequest request) {
        log.info("Received status update for PSP {}", id);
        pspService.updatePspStatus(id, request.getStatus());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<PspResponse> updatePspProfile(@PathVariable Long id, @RequestBody PspUpdateRequest request) {
        log.info("Received profile update for PSP {}", id);
        Psp psp = pspService.updatePspProfile(id, request);
        return ResponseEntity.ok(pspMapper.toResponse(psp));
    }

    @PostMapping("/users")
    public ResponseEntity<PspUserResponse> createPspUser(@RequestBody PspUserCreationRequest request) {
        log.info("Received PSP user creation request");
        User user = pspService.createPspUser(request);
        return ResponseEntity.ok(pspMapper.toResponse(user));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<PspUserResponse> login(@RequestBody PspLoginRequest request) {
        Optional<User> userOpt = pspService.authenticatePspUser(request.getEmail(), request.getPassword());
        return userOpt.map(user -> ResponseEntity.ok(pspMapper.toResponse(user)))
                .orElse(ResponseEntity.status(401).build());
    }
}
