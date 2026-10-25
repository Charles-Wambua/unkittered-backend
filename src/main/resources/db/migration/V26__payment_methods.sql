-- ─────────────────────────────────────────────────────────────────────────────
-- Several payment providers (M-Pesa, Google Play, App Store, card, PayPal,
-- bank), prices in more than one currency, and earnings based on what was
-- actually paid.
-- ─────────────────────────────────────────────────────────────────────────────

-- What the bought Paws in this wallet were paid for, net of provider fees, in
-- KES cents. A gift spends its proportional share; the creator earns a share
-- of that. Existing balances were bought at KES 1 a Paw.
ALTER TABLE wallets ADD COLUMN paws_value_cents BIGINT NOT NULL DEFAULT 0 CHECK (paws_value_cents >= 0);
UPDATE wallets SET paws_value_cents = paws::BIGINT * 100;

ALTER TABLE wallet_transactions
    ADD COLUMN method        VARCHAR(16),
    ADD COLUMN currency      VARCHAR(3),
    -- Price paid (top-ups) or amount sent (withdrawals), in minor units.
    ADD COLUMN amount_minor  INT,
    -- Top-ups: the purchase's net value in KES cents, credited on success.
    ADD COLUMN net_kes_cents BIGINT,
    -- Where a withdrawal goes: phone, PayPal email or bank account.
    ADD COLUMN destination   VARCHAR(64);

UPDATE wallet_transactions SET method = 'mpesa', currency = 'KES', amount_minor = amount_kes * 100
 WHERE kind IN ('top_up', 'withdrawal') AND amount_kes IS NOT NULL;
