package com.example.gymflow.controller;

import com.example.gymflow.dto.payment.PaymentResponse;
import com.example.gymflow.dto.payment.PaymentVoidRequest;
import com.example.gymflow.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Payment corrections.
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payments Controller")
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping("/{paymentId}/void")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @Operation(summary = "POST /api/payments/{paymentId}/void — void a payment of the open cash session and cancel its membership")
    public ResponseEntity<PaymentResponse> voidPaymentById(@PathVariable Long paymentId, @Valid @RequestBody PaymentVoidRequest request, Authentication auth) {
        return ResponseEntity.ok(paymentService.voidPaymentById(paymentId, request, Long.parseLong(auth.getName())));
    }
}
