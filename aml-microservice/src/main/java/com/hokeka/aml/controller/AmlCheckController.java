package com.hokeka.aml.controller;

import com.hokeka.aml.model.AmlResult;
import com.hokeka.aml.model.TransactionRequest;
import com.hokeka.aml.service.AmlCheckService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/aml")
public class AmlCheckController {

    @Autowired
    private AmlCheckService amlCheckService;

    @PostMapping("/check")
    public ResponseEntity<AmlResult> check(@RequestBody TransactionRequest request) {
        AmlResult result = amlCheckService.check(request);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "aml-microservice"));
    }
}
