package com.unkittered.api.wallet;

import com.unkittered.api.chat.ChatService;
import com.unkittered.api.common.ApiException;
import com.unkittered.api.profile.Profile;
import com.unkittered.api.profile.ProfileRepository;
import com.unkittered.api.realtime.RealtimeGateway;
import com.unkittered.api.safety.BlockRepository;
import com.unkittered.api.wallet.PaymentOptions.Mode;
import com.unkittered.api.wallet.PaymentRules.Payout;
import com.unkittered.api.wallet.WalletCatalog.Gift;
import com.unkittered.api.wallet.WalletCatalog.PawsPack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The Paws ledger. Every change locks the wallets involved (in a fixed order,
 * so two gifts between the same people can't deadlock) and writes a ledger
 * line in the same transaction. Provider results are applied at most once.
 *
 * <p>Providers run in one of three modes (see {@link PaymentOptions}):
 * disabled, sandbox (the payment "succeeds" at once and no money moves, for
 * testing before the accounts exist) or live. Live is wired for M-Pesa;
 * Google Play, App Store, card, PayPal and bank need their accounts first and
 * answer 503 until then.
 */
@Service
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern ACCOUNT = Pattern.compile("^[A-Za-z0-9 -]{6,34}$");

    private final WalletRepository wallets;
    private final WalletTransactionRepository ledger;
    private final ProfileRepository profiles;
    private final BlockRepository blocks;
    private final MpesaGateway mpesa;
    private final PaymentOptions options;
    private final ChatService chat;
    private final RealtimeGateway realtime;

    public WalletService(WalletRepository wallets, WalletTransactionRepository ledger,
                         ProfileRepository profiles, BlockRepository blocks, MpesaGateway mpesa,
                         PaymentOptions options, ChatService chat, RealtimeGateway realtime) {
        this.wallets = wallets;
        this.ledger = ledger;
        this.profiles = profiles;
        this.blocks = blocks;
        this.mpesa = mpesa;
        this.options = options;
        this.chat = chat;
        this.realtime = realtime;
    }

    /**
     * Balances: {@code paws} to spend; {@code earned} from gifts, of which
     * {@code held} is still in the holding period (released at
     * {@code nextReleaseAt}) and the rest is withdrawable.
     */
    public record WalletView(int paws, int earned, int withdrawable, int held, Instant nextReleaseAt,
                             List<WalletTransaction> transactions) { }

    public record TopUpCommand(String packId, String method, String phone, String purchaseToken,
                               String country, String platform) { }

    public record WithdrawCommand(int paws, String method, String destination, String country) { }

    @Transactional(readOnly = true)
    public WalletView view(UUID userId) {
        var w = wallets.findById(userId).orElse(new Wallet(userId));
        Instant since = Instant.now().minus(Duration.ofDays(options.rules().getHoldDays()));
        int held = (int) Math.min(w.getEarned(), Math.max(0, ledger.heldEarnings(userId, since)));
        Instant nextRelease = held == 0 ? null : ledger
                .findFirstByUserIdAndKindAndCreatedAtAfterOrderByCreatedAtAsc(userId, WalletTransaction.GIFT_RECEIVED, since)
                .map(tx -> tx.getCreatedAt().plus(Duration.ofDays(options.rules().getHoldDays())))
                .orElse(null);
        return new WalletView(w.getPaws(), w.getEarned(), w.getEarned() - held, held, nextRelease,
                ledger.findTop100ByUserIdOrderByCreatedAtDesc(userId));
    }

    public boolean mpesaEnabled() {
        return options.mode(PaymentMethod.MPESA) != Mode.DISABLED;
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
        PaymentsProperties rules = options.rules();
        long value = PaymentRules.giftValueCents(gift.paws, sender.getPaws(), sender.getPawsValueCents());
        int earned = PaymentRules.creatorEarnings(gift.paws, value, rules.getCreatorShare(), rules.netEarnings());
        sender.addPaws(-gift.paws);
        sender.addPawsValue(-value);
        recipient.addEarned(earned);
        ledger.save(new WalletTransaction(fromId, WalletTransaction.GIFT_SENT, -gift.paws)
                .gift(gift.id).counterparty(toId));
        ledger.save(new WalletTransaction(toId, WalletTransaction.GIFT_RECEIVED, earned)
                .gift(gift.id).counterparty(fromId));

        String fromName = profiles.findById(fromId).map(Profile::getName).filter(n -> !n.isBlank()).orElse("Someone");
        chat.postGift(fromId, toId, gift.id, gift.name, gift.emoji);
        realtime.sendToUser(toId, Map.of(
                "type", "gift",
                "fromName", fromName,
                "giftId", gift.id,
                "emoji", gift.emoji,
                "name", gift.name,
                "earned", earned));
        log.info("Gift {} from {} to {} (value {} KES cents, earned {})", gift.id, fromId, to.getUserId(), value, earned);
        return view(fromId);
    }

    // ── Top-ups ─────────────────────────────────────────────────────────────

    @Transactional(noRollbackFor = ApiException.class)
    public WalletTransaction startTopUp(UUID userId, TopUpCommand cmd) {
        PaymentMethod method = PaymentMethod.of(cmd.method());
        String country = PaymentOptions.country(cmd.country());
        options.requireTopUp(method, country, cmd.platform());
        PawsPack pack = PawsPack.of(cmd.packId());
        // M-Pesa only takes shillings; everything else charges the local price.
        String currency = method == PaymentMethod.MPESA ? "KES" : PaymentOptions.currencyFor(country);
        int amountMinor = pack.priceMinor(currency);
        long net = PaymentRules.netKesCents(amountMinor, options.kesPer(currency), options.feePercent(method));

        var tx = new WalletTransaction(userId, WalletTransaction.TOP_UP, pack.total())
                .status(WalletTransaction.PENDING).method(method).amount(currency, amountMinor).netKesCents(net)
                .note(pack.paws + (pack.bonus > 0 ? " + " + pack.bonus + " bonus" : "") + " Paws");
        String phone = null;
        if (method == PaymentMethod.MPESA) {
            phone = MpesaGateway.normalisePhone(cmd.phone());
            tx.amountKes(pack.priceKes()).phone(phone);
        }
        if (method.needs() == PaymentMethod.Needs.STORE_PURCHASE
                && (cmd.purchaseToken() == null || cmd.purchaseToken().isBlank())) {
            throw ApiException.badRequest("Missing the store purchase to verify.");
        }
        Mode mode = options.mode(method);
        // Test Paws cost nothing. Once real withdrawals are on, gifting them on to
        // a second account would turn them into real money, so they stop.
        if (mode == Mode.SANDBOX && options.realPayoutsOn()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    method.label() + " isn't available for buying Paws right now.");
        }
        // Checked before anything is recorded, so a misconfigured server leaves no
        // half-made top-ups behind.
        if (mode == Mode.LIVE && method == PaymentMethod.MPESA) requireMpesa();
        tx = ledger.save(tx);

        if (mode == Mode.SANDBOX) {
            tx.providerRef("sandbox-" + UUID.randomUUID());
            applyTopUp(tx, true);
            return tx;
        }
        if (method == PaymentMethod.MPESA) {
            MpesaGateway.Request req;
            try {
                req = mpesa.collect(phone, pack.priceKes(), "UNK-" + tx.getId().toString().substring(0, 8), "Paws top-up");
            } catch (MpesaGateway.Uncertain e) {
                // The PIN prompt may still reach their phone. Stay pending: the
                // callback is matched by phone and amount (see completeTopUp).
                log.warn("STK push for top-up {} may or may not have been sent; left pending", tx.getId());
                return tx;
            } catch (ApiException e) {
                tx.status(WalletTransaction.FAILED);
                throw e;
            }
            tx.providerRef(req.providerRef());
            if (req.completed()) applyTopUp(tx, true);
            return tx;
        }
        // Live store / card checks need the provider accounts (not set up yet).
        tx.status(WalletTransaction.FAILED);
        throw notConnected(method);
    }

    /** How long an STK prompt we lost track of can still be matched to its callback. */
    static final Duration UNMATCHED_STK_WINDOW = Duration.ofMinutes(15);

    /**
     * M-Pesa STK callback. Idempotent. {@code amountKes} and {@code phone} come
     * from the callback's metadata (null when it failed or M-Pesa left them out).
     */
    @Transactional
    public void completeTopUp(String providerRef, boolean success, Integer amountKes, String phone) {
        var found = ledger.findByProviderRef(providerRef);
        if (found.isEmpty() && success && amountKes != null && phone != null) {
            // A top-up whose request timed out on our side but reached M-Pesa:
            // the member paid, so find it by phone and amount.
            found = ledger.findUnmatchedTopUp(phone, amountKes, Instant.now().minus(UNMATCHED_STK_WINDOW));
            found.ifPresent(tx -> tx.providerRef(providerRef));
        }
        found.ifPresentOrElse(tx -> {
            boolean paid = success;
            if (paid && amountKes != null && tx.getAmountKes() != null && amountKes < tx.getAmountKes()) {
                log.warn("STK callback {} paid KES {} for a KES {} top-up; not credited", providerRef,
                        amountKes, tx.getAmountKes());
                paid = false;
            }
            applyTopUp(tx, paid);
        }, () -> log.warn("STK callback for unknown ref {} (success={}, KES {}, phone ending {})", providerRef,
                success, amountKes, phone == null || phone.length() < 3 ? "?" : phone.substring(phone.length() - 3)));
    }

    private void applyTopUp(WalletTransaction tx, boolean success) {
        if (!WalletTransaction.PENDING.equals(tx.getStatus())) return;
        if (success) {
            Wallet w = lock(tx.getUserId());
            w.addPaws(tx.getPaws());
            // Older rows have no recorded value: they were KES 1 a Paw.
            w.addPawsValue(tx.getNetKesCents() != null ? tx.getNetKesCents() : tx.getPaws() * 100L);
            tx.status(WalletTransaction.COMPLETED);
            realtime.sendToUser(tx.getUserId(), Map.of("type", "wallet", "event", "top_up_completed"));
        } else {
            tx.status(WalletTransaction.FAILED);
            realtime.sendToUser(tx.getUserId(), Map.of("type", "wallet", "event", "top_up_failed"));
        }
    }

    // ── Withdrawals ─────────────────────────────────────────────────────────

    @Transactional(noRollbackFor = ApiException.class)
    public WalletTransaction withdraw(UUID userId, WithdrawCommand cmd) {
        PaymentMethod method = PaymentMethod.of(cmd.method());
        String country = PaymentOptions.country(cmd.country());
        options.requirePayout(method, country);
        PaymentsProperties rules = options.rules();
        int paws = cmd.paws();
        if (paws < rules.getMinWithdrawal()) {
            throw ApiException.badRequest("You can withdraw from " + rules.getMinWithdrawal() + " earned Paws.");
        }
        String destination = destination(method, cmd.destination());

        // Lock first so two withdrawals can't both pass the balance check.
        Wallet w = lock(userId);
        WalletView before = view(userId);
        if (paws > before.withdrawable()) {
            throw ApiException.badRequest(before.held() > 0 && paws <= before.earned()
                    ? before.held() + " of your earned Paws are still on hold for " + rules.getHoldDays()
                      + " days after each gift."
                    : "You don't have that many earned Paws yet.");
        }
        Payout payout = Payout.of(paws, rules.getWithdrawalFeePercent(), rules.getWithdrawalFeeFixed());
        if (payout.payout() <= 0) throw ApiException.badRequest("That's less than the withdrawal fee.");

        // Paid in shillings over M-Pesa, otherwise in the local currency.
        String currency = method == PaymentMethod.MPESA ? "KES" : PaymentOptions.currencyFor(country);
        int amountMinor = (int) Math.floor(payout.payout() * 100 / options.kesPer(currency));

        Mode mode = options.mode(method);
        // Before any Paws move: a server missing its M-Pesa keys must not take them.
        if (mode == Mode.LIVE && method == PaymentMethod.MPESA) requireMpesa();

        // Take the Paws now; they come back if the payout fails.
        w.addEarned(-paws);
        var tx = ledger.save(new WalletTransaction(userId, WalletTransaction.WITHDRAWAL, -paws)
                .status(WalletTransaction.PENDING).method(method).amount(currency, amountMinor)
                .amountKes(payout.payout()).destination(destination)
                .phone(method == PaymentMethod.MPESA ? destination : null)
                .note(payout.fee() + " Paws fee"));

        if (mode == Mode.SANDBOX) {
            tx.providerRef("sandbox-" + UUID.randomUUID());
            applyPayout(tx, true);
            return tx;
        }
        if (method == PaymentMethod.MPESA) {
            String reference = "UNKW-" + tx.getId().toString().substring(0, 8);
            MpesaGateway.Request req;
            try {
                req = mpesa.payout(destination, payout.payout(), reference);
            } catch (MpesaGateway.Uncertain e) {
                // M-Pesa may be paying this out right now. Refunding would let them
                // withdraw the same Paws twice, so it stays pending under our own
                // reference, which the B2C result quotes back.
                tx.providerRef(reference);
                log.warn("B2C payout {} may or may not have been sent; left pending", tx.getId());
                return tx;
            } catch (ApiException e) {
                w.addEarned(paws);
                tx.status(WalletTransaction.FAILED);
                throw e;
            }
            tx.providerRef(req.providerRef());
            if (req.completed()) applyPayout(tx, true);
            return tx;
        }
        w.addEarned(paws);
        tx.status(WalletTransaction.FAILED);
        throw notConnected(method);
    }

    /**
     * M-Pesa's B2C queue timed out. That doesn't mean the money didn't go out
     * (a queued payment can still be processed), so nothing is refunded here;
     * the result callback or {@link #settleStale()} decides.
     */
    public void payoutTimedOut(String providerRef) {
        log.warn("B2C queue timeout for {}; left pending until M-Pesa confirms", providerRef);
    }

    /** Top-ups with no answer after this long are given up on (nothing was credited). */
    static final Duration TOP_UP_GIVE_UP = Duration.ofHours(2);
    /** Withdrawals with no answer after this long need a person to check them. */
    static final Duration PAYOUT_ALERT = Duration.ofHours(24);

    /**
     * Hourly: closes top-ups M-Pesa never answered (no Paws were given, so
     * failing them is safe) and flags withdrawals still waiting. Withdrawals
     * are never refunded automatically: if the money went out, a refund would
     * pay the member twice.
     */
    @Scheduled(cron = "${unkittered.wallet.settle-cron:0 20 * * * *}")
    @Transactional
    public void settleStale() {
        try {
            Instant now = Instant.now();
            for (WalletTransaction tx : ledger.findByKindAndStatusAndCreatedAtBefore(
                    WalletTransaction.TOP_UP, WalletTransaction.PENDING, now.minus(TOP_UP_GIVE_UP))) {
                log.warn("Top-up {} ({}, ref {}) had no answer from the provider; marked failed. "
                        + "If the member says they paid, check the M-Pesa statement.", tx.getId(), tx.getMethod(),
                        tx.getProviderRef());
                applyTopUp(tx, false);
            }
            List<WalletTransaction> waiting = ledger.findByKindAndStatusAndCreatedAtBefore(
                    WalletTransaction.WITHDRAWAL, WalletTransaction.PENDING, now.minus(PAYOUT_ALERT));
            if (!waiting.isEmpty()) {
                log.warn("{} withdrawal(s) still pending after {}h, check them against M-Pesa: {}", waiting.size(),
                        PAYOUT_ALERT.toHours(), waiting.stream().map(t -> t.getId() + " (ref " + t.getProviderRef() + ")").toList());
            }
        } catch (Exception e) {
            log.warn("Settling pending payments failed: {}", e.getMessage());
        }
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

    /** Validates where a withdrawal goes, per method. */
    private static String destination(PaymentMethod method, String raw) {
        String v = raw == null ? "" : raw.strip();
        return switch (method.needs()) {
            case PHONE -> MpesaGateway.normalisePhone(v);
            case EMAIL -> {
                if (!EMAIL.matcher(v).matches() || v.length() > 64) {
                    throw ApiException.badRequest("Enter the email address of your PayPal account.");
                }
                yield v.toLowerCase();
            }
            case ACCOUNT -> {
                if (!ACCOUNT.matcher(v).matches()) {
                    throw ApiException.badRequest("Enter your bank account number or IBAN.");
                }
                yield v;
            }
            default -> throw ApiException.badRequest(method.label() + " can't be used for withdrawals.");
        };
    }

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

    private static ApiException notConnected(PaymentMethod method) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                method.label() + " isn't connected yet. Please try another option.");
    }
}
