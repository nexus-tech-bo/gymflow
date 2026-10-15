package com.example.gymflow.controller;

import com.example.gymflow.config.SecurityConfig;
import com.example.gymflow.dto.payment.PaymentResponse;
import com.example.gymflow.enums.PaymentMethod;
import com.example.gymflow.security.JwtUtil;
import com.example.gymflow.service.CashService;
import com.example.gymflow.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security contract of the filter chain and method security: 401 without token, 403 for the wrong role, 200 for OWNER.
 */
@WebMvcTest({PaymentController.class, CashController.class})
@Import({SecurityConfig.class, JwtUtil.class})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-test-secret-test-secret-123456",
        "security.public-paths=/api/auth/login"
})
@DisplayName("Security slice")
class SecurityWebMvcTest {
    private static final String VOID_BODY = "{\"reason\":\"Cobro duplicado\"}";

    @Autowired MockMvc mvc;
    @Autowired JwtUtil jwtUtil;
    @MockitoBean PaymentService paymentService;
    @MockitoBean CashService cashService;

    private String bearer(String role) {
        return "Bearer " + jwtUtil.generate(1L, role, 1L, "Ana Pérez");
    }

    @Test
    void voidPayment_should_return401Json_withoutToken() throws Exception {
        mvc.perform(post("/api/payments/5/void").contentType(MediaType.APPLICATION_JSON).content(VOID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.timestamp").exists());
        verifyNoInteractions(paymentService);
    }

    @Test
    void voidPayment_should_return403Json_forReceptionist() throws Exception {
        mvc.perform(post("/api/payments/5/void").header("Authorization", bearer("RECEPTIONIST"))
                        .contentType(MediaType.APPLICATION_JSON).content(VOID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(paymentService);
    }

    @Test
    void voidPayment_should_return200_forOwner() throws Exception {
        when(paymentService.voidPaymentById(eq(5L), any(), eq(1L))).thenReturn(new PaymentResponse(5L, 3L, "Luis Díaz",
                "Mensual", new BigDecimal("120.00"), PaymentMethod.CASH, null, "Ana Pérez", Instant.now(), true, "Cobro duplicado"));

        mvc.perform(post("/api/payments/5/void").header("Authorization", bearer("OWNER"))
                        .contentType(MediaType.APPLICATION_JSON).content(VOID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voided").value(true));
    }

    @Test
    void cashSessions_should_return403_forReceptionist() throws Exception {
        mvc.perform(get("/api/cash/sessions").header("Authorization", bearer("RECEPTIONIST")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(cashService);
    }

    @Test
    void cashSessions_should_return200WithPageShape_forOwner() throws Exception {
        when(cashService.findAllCashSessions(any())).thenReturn(Page.empty());

        mvc.perform(get("/api/cash/sessions").header("Authorization", bearer("OWNER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void cashSessions_should_return401_withTamperedToken() throws Exception {
        mvc.perform(get("/api/cash/sessions").header("Authorization", bearer("OWNER") + "x"))
                .andExpect(status().isUnauthorized());
    }
}
