-- Paws: the in-app currency. Two balances per member:
--   paws   — bought with M-Pesa, spent on gifts;
--   earned — received from gifts (the creator's share), withdrawable.
CREATE TABLE wallets (
    user_id     UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    paws        INT         NOT NULL DEFAULT 0 CHECK (paws >= 0),
    earned      INT         NOT NULL DEFAULT 0 CHECK (earned >= 0),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- The ledger. Pending rows wait for an M-Pesa callback.
CREATE TABLE wallet_transactions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    kind            VARCHAR(16) NOT NULL
        CHECK (kind IN ('top_up', 'gift_sent', 'gift_received', 'withdrawal')),
    status          VARCHAR(12) NOT NULL DEFAULT 'completed'
        CHECK (status IN ('pending', 'completed', 'failed')),
    -- Paws added (+) or removed (−) from the relevant balance.
    paws            INT         NOT NULL,
    amount_kes      INT,
    gift_id         VARCHAR(32),
    counterparty_id UUID REFERENCES users (id) ON DELETE SET NULL,
    phone           VARCHAR(16),
    provider_ref    VARCHAR(64) UNIQUE,
    note            VARCHAR(140),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_wallet_tx_user ON wallet_transactions (user_id, created_at DESC);
