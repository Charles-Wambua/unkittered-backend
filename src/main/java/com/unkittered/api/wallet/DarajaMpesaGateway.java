package com.unkittered.api.wallet;

import com.unkittered.api.common.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Safaricom Daraja: Lipa na M-Pesa Online (STK push) to collect, B2C to pay
 * out. Results arrive on our callback URLs, which carry a secret token so
 * nobody else can post fake results.
 *
 * <p>{@code securityCredential} is the B2C initiator password already
 * encrypted with Safaricom's public certificate (generated once, from the
 * Daraja portal), as Daraja expects.
 */
class DarajaMpesaGateway implements MpesaGateway {

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("Africa/Nairobi"));

    private final RestClient http;
    private final String consumerKey;
    private final String consumerSecret;
    private final String shortcode;
    private final String passkey;
    private final String callbackBase;
    private final String b2cShortcode;
    private final String initiatorName;
    private final String securityCredential;

    private String token;
    private Instant tokenExpires = Instant.EPOCH;

    DarajaMpesaGateway(String baseUrl, String consumerKey, String consumerSecret, String shortcode,
                       String passkey, String callbackBaseUrl, String callbackToken, String b2cShortcode,
                       String initiatorName, String securityCredential) {
        this.http = RestClient.builder().baseUrl(baseUrl).build();
        this.consumerKey = consumerKey;
        this.consumerSecret = consumerSecret;
        this.shortcode = shortcode;
        this.passkey = passkey;
        this.callbackBase = callbackBaseUrl.replaceAll("/+$", "") + "/v1/payments/mpesa/" + callbackToken;
        this.b2cShortcode = b2cShortcode.isBlank() ? shortcode : b2cShortcode;
        this.initiatorName = initiatorName;
        this.securityCredential = securityCredential;
    }

    @Override
    public boolean enabled() {
        return !consumerKey.isBlank() && !consumerSecret.isBlank() && !shortcode.isBlank()
                && !passkey.isBlank() && !callbackBase.isBlank();
    }

    @Override
    public Request collect(String phone, int amountKes, String reference, String description) {
        String stamp = STAMP.format(Instant.now());
        String password = Base64.getEncoder().encodeToString(
                (shortcode + passkey + stamp).getBytes(StandardCharsets.UTF_8));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("BusinessShortCode", shortcode);
        body.put("Password", password);
        body.put("Timestamp", stamp);
        body.put("TransactionType", "CustomerPayBillOnline");
        body.put("Amount", amountKes);
        body.put("PartyA", phone);
        body.put("PartyB", shortcode);
        body.put("PhoneNumber", phone);
        body.put("CallBackURL", callbackBase + "/stk");
        body.put("AccountReference", reference);
        body.put("TransactionDesc", description);
        Map<?, ?> res = post("/mpesa/stkpush/v1/processrequest", body);
        Object id = res.get("CheckoutRequestID");
        if (id == null) throw unavailable();
        return new Request(id.toString(), false);
    }

    @Override
    public Request payout(String phone, int amountKes, String reference) {
        if (initiatorName.isBlank() || securityCredential.isBlank()) throw unavailable();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("OriginatorConversationID", reference);
        body.put("InitiatorName", initiatorName);
        body.put("SecurityCredential", securityCredential);
        body.put("CommandID", "BusinessPayment");
        body.put("Amount", amountKes);
        body.put("PartyA", b2cShortcode);
        body.put("PartyB", phone);
        body.put("Remarks", "Unkittered earnings");
        body.put("QueueTimeOutURL", callbackBase + "/b2c-timeout");
        body.put("ResultURL", callbackBase + "/b2c-result");
        body.put("Occasion", reference);
        Map<?, ?> res = post("/mpesa/b2c/v3/paymentrequest", body);
        Object id = res.get("OriginatorConversationID");
        return new Request(id == null ? reference : id.toString(), false);
    }

    private Map<?, ?> post(String path, Map<String, Object> body) {
        try {
            Map<?, ?> res = http.post().uri(path)
                    .header("Authorization", "Bearer " + accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            return res == null ? Map.of() : res;
        } catch (RestClientException e) {
            throw unavailable();
        }
    }

    private synchronized String accessToken() {
        if (token != null && Instant.now().isBefore(tokenExpires)) return token;
        String basic = Base64.getEncoder().encodeToString(
                (consumerKey + ":" + consumerSecret).getBytes(StandardCharsets.UTF_8));
        Map<?, ?> res = http.get().uri("/oauth/v1/generate?grant_type=client_credentials")
                .header("Authorization", "Basic " + basic)
                .retrieve()
                .body(Map.class);
        if (res == null || res.get("access_token") == null) throw unavailable();
        token = res.get("access_token").toString();
        long ttl = res.get("expires_in") == null ? 3000 : Long.parseLong(res.get("expires_in").toString());
        tokenExpires = Instant.now().plusSeconds(Math.max(60, ttl - 60));
        return token;
    }

    private static ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "M-Pesa isn't responding. Please try again.");
    }
}
