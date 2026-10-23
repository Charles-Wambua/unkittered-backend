package com.unkittered.api.wallet;

import com.unkittered.api.common.ApiException;

/**
 * Talks to M-Pesa: STK push to collect money (Paws top-ups) and B2C to pay
 * out (withdrawals). Both are asynchronous on the real network — the result
 * arrives on a callback — so each call returns a provider reference that the
 * callback later quotes.
 */
public interface MpesaGateway {

    /** {@code completed} is true only when the mode settles instantly (dev). */
    record Request(String providerRef, boolean completed) { }

    boolean enabled();

    Request collect(String phone, int amountKes, String reference, String description);

    Request payout(String phone, int amountKes, String reference);

    /** 0712345678 / +254712345678 / 254712345678 → 254712345678. */
    static String normalisePhone(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("[^0-9]", "");
        if (digits.startsWith("0") && digits.length() == 10) digits = "254" + digits.substring(1);
        if (digits.length() == 9 && (digits.startsWith("7") || digits.startsWith("1"))) digits = "254" + digits;
        if (!digits.matches("254[17]\\d{8}")) {
            throw ApiException.badRequest("Enter a Safaricom number like 0712 345 678.");
        }
        return digits;
    }
}
