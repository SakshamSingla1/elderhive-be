package com.elderhive.controllers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers what PaymentServiceImplTest's unit tests can't: that
 * POST /api/v1/payments/webhook is actually reachable with NO JWT cookie (SecurityConfig's
 * permitAll, Prompt 97's own addition) and that the raw request body + Stripe-Signature
 * header really arrive at the controller/service as-is, rather than this only working in
 * theory. No real Stripe secret is configured in the test profile, so this can't exercise
 * actual signature verification (that's PaymentServiceImplTest's job, via mockStatic) -
 * this only proves the wiring: unauthenticated access gets past Spring Security at all
 * (a 400 from the service layer, never a 401 from the security filter chain), and the
 * endpoint is a public POST with no PreAuthorize gate unlike every other payments route.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PaymentControllerWebhookTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void webhookIsReachableWithoutAnyAuthenticationCookie() throws Exception {
        mockMvc.perform(post("/api/v1/payments/webhook")
                        .header("Stripe-Signature", "t=1,v1=bogus")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"evt_test\",\"type\":\"payment_intent.succeeded\"}"))
                // Never 401/403 - SecurityConfig's permitAll let it all the way through to
                // the service layer, which then rejects it on its own terms (no
                // STRIPE_WEBHOOK_SECRET configured in the test profile).
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("WEBHOOK_SIGNATURE_INVALID"));
    }

    @Test
    void webhookRequiresTheStripeSignatureHeader() throws Exception {
        mockMvc.perform(post("/api/v1/payments/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is4xxClientError());
    }
}
