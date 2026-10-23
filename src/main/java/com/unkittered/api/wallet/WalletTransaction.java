package com.unkittered.api.wallet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One line in a member's Paws ledger. */
@Entity
@Table(name = "wallet_transactions")
public class WalletTransaction {

    public static final String TOP_UP = "top_up";
    public static final String GIFT_SENT = "gift_sent";
    public static final String GIFT_RECEIVED = "gift_received";
    public static final String WITHDRAWAL = "withdrawal";

    public static final String PENDING = "pending";
    public static final String COMPLETED = "completed";
    public static final String FAILED = "failed";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String kind;

    @Column(nullable = false)
    private String status = COMPLETED;

    @Column(nullable = false)
    private int paws;

    @Column(name = "amount_kes")
    private Integer amountKes;

    @Column(name = "gift_id")
    private String giftId;

    @Column(name = "counterparty_id")
    private UUID counterpartyId;

    private String phone;

    @Column(name = "provider_ref", unique = true)
    private String providerRef;

    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected WalletTransaction() { }

    WalletTransaction(UUID userId, String kind, int paws) {
        this.userId = userId;
        this.kind = kind;
        this.paws = paws;
    }

    WalletTransaction status(String s) { this.status = s; return this; }
    WalletTransaction amountKes(Integer v) { this.amountKes = v; return this; }
    WalletTransaction gift(String id) { this.giftId = id; return this; }
    WalletTransaction counterparty(UUID id) { this.counterpartyId = id; return this; }
    WalletTransaction phone(String p) { this.phone = p; return this; }
    WalletTransaction providerRef(String r) { this.providerRef = r; return this; }
    WalletTransaction note(String n) { this.note = n; return this; }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getKind() { return kind; }
    public String getStatus() { return status; }
    public int getPaws() { return paws; }
    public Integer getAmountKes() { return amountKes; }
    public String getGiftId() { return giftId; }
    public UUID getCounterpartyId() { return counterpartyId; }
    public String getProviderRef() { return providerRef; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }
}
