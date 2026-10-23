package com.unkittered.api.wallet;

import io.swagger.v3.oas.annotations.Hidden;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Safaricom posts payment results here. The path carries a secret token (set
 * in config and in the URLs we give Daraja), compared in constant time, so
 * only Safaricom — which we told the URL — can settle payments.
 */
@Hidden
@RestController
@RequestMapping("/v1/payments/mpesa/{token}")
public class MpesaCallbackController {

    private static final Logger log = LoggerFactory.getLogger(MpesaCallbackController.class);
    private static final Map<String, Object> ACCEPTED = Map.of("ResultCode", 0, "ResultDesc", "Accepted");

    private final WalletService wallet;
    private final byte[] token;

    public MpesaCallbackController(WalletService wallet,
                                   @Value("${unkittered.mpesa.callback-token:}") String token) {
        this.wallet = wallet;
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/stk")
    public ResponseEntity<Map<String, Object>> stk(@PathVariable String token, @RequestBody Map<String, Object> body) {
        if (!authorised(token)) return ResponseEntity.notFound().build();
        if (body.get("Body") instanceof Map<?, ?> b && b.get("stkCallback") instanceof Map<?, ?> cb) {
            Object ref = cb.get("CheckoutRequestID");
            boolean ok = "0".equals(String.valueOf(cb.get("ResultCode")));
            if (ref != null) wallet.completeTopUp(ref.toString(), ok);
        } else {
            log.warn("Malformed STK callback");
        }
        return ResponseEntity.ok(ACCEPTED);
    }

    @PostMapping({"/b2c-result", "/b2c-timeout"})
    public ResponseEntity<Map<String, Object>> b2c(@PathVariable String token, @RequestBody Map<String, Object> body) {
        if (!authorised(token)) return ResponseEntity.notFound().build();
        if (body.get("Result") instanceof Map<?, ?> r) {
            Object ref = r.get("OriginatorConversationID");
            boolean ok = "0".equals(String.valueOf(r.get("ResultCode")));
            if (ref != null) wallet.completePayout(ref.toString(), ok);
        } else {
            log.warn("Malformed B2C result");
        }
        return ResponseEntity.ok(ACCEPTED);
    }

    private boolean authorised(String presented) {
        return token.length > 0
                && MessageDigest.isEqual(token, presented.getBytes(StandardCharsets.UTF_8));
    }
}
