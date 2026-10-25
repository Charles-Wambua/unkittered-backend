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
        tx = ledger.save(tx);

        Mode mode = options.mode(method);
        if (mode == Mode.SANDBOX) {
            tx.providerRef("sandbox-" + UUID.randomUUID());
            applyTopUp(tx, true);
            return tx;
        }
        if (method == PaymentMethod.MPESA) {
            requireMpesa();
            MpesaGateway.Request req;
            try {
                req = mpesa.collect(phone, pack.priceKes(), "UNK-" + tx.getId().toString().substring(0, 8), "Paws top-up");
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

        // Take the Paws now; they come back if the payout fails.
        w.addEarned(-paws);
        var tx = ledger.save(new WalletTransaction(userId, WalletTransaction.WITHDRAWAL, -paws)
                .status(WalletTransaction.PENDING).method(method).amount(currency, amountMinor)
                .amountKes(payout.payout()).destination(destination)
                .phone(method == PaymentMethod.MPESA ? destination : null)
                .note(payout.fee() + " Paws fee"));

        Mode mode = options.mode(method);
        if (mode == Mode.SANDBOX) {
            tx.providerRef("sandbox-" + UUID.randomUUID());
            applyPayout(tx, true);
            return tx;
        }
        if (method == PaymentMethod.MPESA) {
            requireMpesa();
            MpesaGateway.Request req;
            try {
                req = mpesa.payout(destination, payout.payout(), "UNKW-" + tx.getId().toString().substring(0, 8));
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
