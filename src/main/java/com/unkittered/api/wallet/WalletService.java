package com.unkittered.api.wallet;

import com.unkittered.api.chat.ChatService;
import com.unkittered.api.common.ApiException;
import com.unkittered.api.profile.Profile;
import com.unkittered.api.profile.ProfileRepository;
import com.unkittered.api.realtime.RealtimeGateway;
import com.unkittered.api.safety.BlockRepository;
import com.unkittered.api.wallet.WalletCatalog.Gift;
import com.unkittered.api.wallet.WalletCatalog.PawsPack;
import com.unkittered.api.wallet.WalletCatalog.Payout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Paws ledger. Every change locks the wallets involved (in a fixed order,
 * so two gifts between the same people can't deadlock) and writes a ledger
 * line in the same transaction. M-Pesa results are applied at most once.
 */
@Service
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    private final WalletRepository wallets;
    private final WalletTransactionRepository ledger;
    private final ProfileRepository profiles;
    private final BlockRepository blocks;
    private final MpesaGateway mpesa;
    private final ChatService chat;
    private final RealtimeGateway realtime;

    public WalletService(WalletRepository wallets, WalletTransactionRepository ledger,
                         ProfileRepository profiles, BlockRepository blocks, MpesaGateway mpesa,
                         ChatService chat, RealtimeGateway realtime) {
        this.wallets = wallets;
        this.ledger = ledger;
        this.profiles = profiles;
        this.blocks = blocks;
        this.mpesa = mpesa;
        this.chat = chat;
        this.realtime = realtime;
    }

    public record WalletView(int paws, int earned, List<WalletTransaction> transactions) { }

    @Transactional(readOnly = true)
    public WalletView view(UUID userId) {
        var w = wallets.findById(userId).orElse(new Wallet(userId));
        return new WalletView(w.getPaws(), w.getEarned(), ledger.findTop100ByUserIdOrderByCreatedAtDesc(userId));
    }

    public boolean mpesaEnabled() {
        return mpesa.enabled();
    }

    // ── Gifts ────────────────────────────────────────────────────────────────

    @Transactional
    public WalletView sendGift(UUID fromId, UUID toId, String giftId) {
        Gift gift = Gift.of(giftId);
        if (fromId.equals(toId)) throw ApiException.badRequest("You can't send yourself a gift.");
        Profile to = profiles.findById(toId).orElseThrow(() -> ApiException.notFound("Profile not found"));
        if (blocks.existsByBlockerIdAndBlockedId(fromId, toId) || blocks.existsByBlockerIdAndBlockedId(toId, fromId)) {
            throw ApiException.notFound("Profile not found");
        }

        // Lock both wallets, lowest id first.
        boolean senderFirst = fromId.compareTo(toId) < 0;
        Wallet first = lock(senderFirst ? fromId : toId);
        Wallet second = lock(senderFirst ? toId : fromId);
        Wallet sender = senderFirst ? first : second;
        Wallet recipient = senderFirst ? second : first;

        if (sender.getPaws() < gift.paws) {
            throw new ApiException(HttpStatus.PAYMENT_REQUIRED,
                    "Not enough Paws for a " + gift.name + ". Top up to send it.");
        }
        sender.addPaws(-gift.paws);
        recipient.addEarned(gift.creatorShare());
        ledger.save(new WalletTransaction(fromId, WalletTransaction.GIFT_SENT, -gift.paws)
                .gift(gift.id).counterparty(toId));
        ledger.save(new WalletTransaction(toId, WalletTransaction.GIFT_RECEIVED, gift.creatorShare())
                .gift(gift.id).counterparty(fromId));

        String fromName = profiles.findById(fromId).map(Profile::getName).filter(n -> !n.isBlank()).orElse("Someone");
        chat.postGift(fromId, toId, gift.id, gift.name, gift.emoji);
        realtime.sendToUser(toId, Map.of(
                "type", "gift",
                "fromName", fromName,
                "giftId", gift.id,
                "emoji", gift.emoji,
                "name", gift.name,
                "earned", gift.creatorShare()));
        log.info("Gift {} from {} to {}", gift.id, fromId, to.getUserId());
        return view(fromId);
    }

    // ── Top-ups (M-Pesa STK push) ───────────────────────────────────────────

    @Transactional(noRollbackFor = ApiException.class)
    public WalletTransaction startTopUp(UUID userId, String packId, String rawPhone) {
        requireMpesa();
        PawsPack pack = PawsPack.of(packId);
        String phone = MpesaGateway.normalisePhone(rawPhone);
        var tx = ledger.save(new WalletTransaction(userId, WalletTransaction.TOP_UP, pack.total())
                .status(WalletTransaction.PENDING).amountKes(pack.priceKes).phone(phone)
                .note(pack.paws + (pack.bonus > 0 ? " + " + pack.bonus + " bonus" : "") + " Paws"));
        MpesaGateway.Request req;
        try {
            req = mpesa.collect(phone, pack.priceKes, "UNK-" + tx.getId().toString().substring(0, 8), "Paws top-up");
        } catch (ApiException e) {
            tx.status(WalletTransaction.FAILED);
            throw e;
        }
        tx.providerRef(req.providerRef());
        if (req.completed()) applyTopUp(tx, true);
        return tx;
    }

    /** M-Pesa STK callback. Idempotent. */
    @Transactional
    public void completeTopUp(String providerRef, boolean success) {
        ledger.findByProviderRef(providerRef).ifPresentOrElse(
                tx -> applyTopUp(tx, success),
                () -> log.warn("STK callback for unknown ref {}", providerRef));
    }

    private void applyTopUp(WalletTransaction tx, boolean success) {
        if (!WalletTransaction.PENDING.equals(tx.getStatus())) return;
        if (success) {
            lock(tx.getUserId()).addPaws(tx.getPaws());
            tx.status(WalletTransaction.COMPLETED);
            realtime.sendToUser(tx.getUserId(), Map.of("type", "wallet", "event", "top_up_completed"));
        } else {
            tx.status(WalletTransaction.FAILED);
            realtime.sendToUser(tx.getUserId(), Map.of("type", "wallet", "event", "top_up_failed"));
        }
    }

    // ── Withdrawals (M-Pesa B2C) ─────────────────────────────────────────────

    @Transactional(noRollbackFor = ApiException.class)
    public WalletTransaction withdraw(UUID userId, int paws, String rawPhone) {
        requireMpesa();
        if (paws < WalletCatalog.MIN_WITHDRAWAL) {
            throw ApiException.badRequest("You can withdraw from " + WalletCatalog.MIN_WITHDRAWAL + " earned Paws.");
        }
        String phone = MpesaGateway.normalisePhone(rawPhone);
        Wallet w = lock(userId);
        if (w.getEarned() < paws) throw ApiException.badRequest("You don't have that many earned Paws yet.");
        Payout payout = Payout.of(paws);
        // Hold the Paws now; they come back if the payout fails.
        w.addEarned(-paws);
        var tx = ledger.save(new WalletTransaction(userId, WalletTransaction.WITHDRAWAL, -paws)
                .status(WalletTransaction.PENDING).amountKes(payout.payoutKes()).phone(phone)
                .note("KES " + payout.feeKes() + " fee"));
        MpesaGateway.Request req;
        try {
            req = mpesa.payout(phone, payout.payoutKes(), "UNKW-" + tx.getId().toString().substring(0, 8));
        } catch (ApiException e) {
            w.addEarned(paws);
            tx.status(WalletTransaction.FAILED);
            throw e;
        }
        tx.providerRef(req.providerRef());
        if (req.completed()) applyPayout(tx, true);
        return tx;
    }

    /** M-Pesa B2C result. Idempotent. */
    @Transactional
    public void completePayout(String providerRef, boolean success) {
        ledger.findByProviderRef(providerRef).ifPresentOrElse(
                tx -> applyPayout(tx, success),
                () -> log.warn("B2C result for unknown ref {}", providerRef));
    }

    private void applyPayout(WalletTransaction tx, boolean success) {
        if (!WalletTransaction.PENDING.equals(tx.getStatus())) return;
        if (success) {
            tx.status(WalletTransaction.COMPLETED);
        } else {
            lock(tx.getUserId()).addEarned(-tx.getPaws()); // paws is negative: refund
            tx.status(WalletTransaction.FAILED);
        }
        realtime.sendToUser(tx.getUserId(), Map.of("type", "wallet",
                "event", success ? "withdrawal_completed" : "withdrawal_failed"));
    }

    // ── internals ────────────────────────────────────────────────────────────

    private Wallet lock(UUID userId) {
        wallets.ensure(userId);
        return wallets.findForUpdate(userId).orElseThrow();
    }

    private void requireMpesa() {
        if (!mpesa.enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "M-Pesa payments aren't available yet. Please try again later.");
        }
    }
}
