package com.unkittered.api.wallet;

import com.unkittered.api.profile.Profile;
import com.unkittered.api.profile.ProfileRepository;
import com.unkittered.api.security.CurrentUser;
import com.unkittered.api.wallet.WalletCatalog.Gift;
import com.unkittered.api.wallet.WalletCatalog.PawsPack;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1")
@Tag(name = "Wallet")
@SecurityRequirement(name = "bearerAuth")
public class WalletController {

    private final WalletService wallet;
    private final PaymentOptions options;
    private final ProfileRepository profiles;

    public WalletController(WalletService wallet, PaymentOptions options, ProfileRepository profiles) {
        this.wallet = wallet;
        this.options = options;
        this.profiles = profiles;
    }

    public record GiftDto(String id, String name, String emoji, int paws) {
        static GiftDto of(Gift g) { return new GiftDto(g.id, g.name, g.emoji, g.paws); }
    }

    /**
     * A Paws pack priced for the member's country. {@code priceKes} is the
     * M-Pesa price; store purchases use {@code storeProductId}.
     */
    public record PackDto(String id, int paws, int bonus, int priceKes, String currency, int priceMinor,
                          String storeProductId) {
        static PackDto of(PawsPack p, String currency) {
            return new PackDto(p.id, p.paws, p.bonus, p.priceKes(), currency, p.priceMinor(currency),
                    p.storeProductId());
        }
    }

    /** A way to pay or get paid; {@code sandbox} = no real money moves. */
    public record MethodDto(PaymentMethod id, String label, String kind, String needs, boolean sandbox) {
        static MethodDto of(PaymentMethod m, PaymentOptions o) {
            return new MethodDto(m, m.label(), m.kind(), m.needs().name().toLowerCase(),
                    o.mode(m) == PaymentOptions.Mode.SANDBOX);
        }
    }

    public record CatalogDto(List<GiftDto> gifts, List<PackDto> packs, double creatorShare,
                             double withdrawalFee, int withdrawalFeeFixed, int minWithdrawal, int holdDays,
                             String country, String currency, double kesPerUnit,
                             List<MethodDto> topUpMethods, List<MethodDto> payoutMethods,
                             boolean mpesaEnabled) { }

    public record TransactionDto(String id, String kind, String status, int paws, Integer amountKes,
                                 GiftDto gift, String counterpartyName, String note, Instant createdAt,
                                 String method, String currency, Integer amountMinor) { }

    public record WalletDto(int paws, int earned, int withdrawable, int held, Instant nextReleaseAt,
                            List<TransactionDto> transactions) { }

    /**
     * Buy a pack. {@code method} defaults to M-Pesa (older apps); M-Pesa needs
     * {@code phone}, store billing needs {@code purchaseToken}.
     */
    public record TopUpRequest(@NotBlank String packId, String method, String phone, String purchaseToken,
                               String country, String platform) { }

    /** Withdraw. {@code destination}: phone (M-Pesa), email (PayPal) or account (bank). */
    public record WithdrawRequest(@Min(1) int paws, String method, String destination, String phone,
                                  String country) { }

    public record SendGiftRequest(@NotBlank String toProfileId, @NotBlank String giftId) { }

    @Operation(summary = "Gifts, Paws packs priced for the country, payment methods and wallet rules")
    @GetMapping("/wallet/catalog")
    public CatalogDto catalog(@RequestParam(required = false) String country,
                              @RequestParam(required = false) String platform) {
        String c = PaymentOptions.country(country);
        String currency = PaymentOptions.currencyFor(c);
        PaymentsProperties rules = options.rules();
        return new CatalogDto(
                Arrays.stream(Gift.values()).map(GiftDto::of).toList(),
                Arrays.stream(PawsPack.values()).map(p -> PackDto.of(p, currency)).toList(),
                rules.getCreatorShare(), rules.getWithdrawalFeePercent(), rules.getWithdrawalFeeFixed(),
                rules.getMinWithdrawal(), rules.getHoldDays(), c, currency, options.kesPer(currency),
                options.topUpMethods(c, platform).stream().map(m -> MethodDto.of(m, options)).toList(),
                options.payoutMethods(c).stream().map(m -> MethodDto.of(m, options)).toList(),
                wallet.mpesaEnabled());
    }

    @Operation(summary = "My Paws balances and the last 100 transactions")
    @GetMapping("/me/wallet")
    public WalletDto mine() {
        return toDto(wallet.view(CurrentUser.id()));
    }

    @Operation(summary = "Buy a Paws pack (M-Pesa STK push, store purchase, or card)")
    @PostMapping("/me/wallet/top-ups")
    public Map<String, Object> topUp(@Valid @RequestBody TopUpRequest req) {
        var tx = wallet.startTopUp(CurrentUser.id(), new WalletService.TopUpCommand(
                req.packId(), req.method(), req.phone(), req.purchaseToken(), req.country(), req.platform()));
        return Map.of("status", tx.getStatus(), "wallet", mine());
    }

    @Operation(summary = "Withdraw earned Paws (M-Pesa, PayPal or bank; fee and minimum per the catalogue)")
    @PostMapping("/me/wallet/withdrawals")
    public Map<String, Object> withdraw(@Valid @RequestBody WithdrawRequest req) {
        String destination = req.destination() != null ? req.destination() : req.phone();
        var tx = wallet.withdraw(CurrentUser.id(),
                new WalletService.WithdrawCommand(req.paws(), req.method(), destination, req.country()));
        return Map.of("status", tx.getStatus(), "wallet", mine());
    }

    @Operation(summary = "Send someone a gift (a share of its value becomes their earnings)")
    @PostMapping("/gifts")
    public WalletDto sendGift(@Valid @RequestBody SendGiftRequest req) {
        UUID to;
        try {
            to = UUID.fromString(req.toProfileId());
        } catch (IllegalArgumentException e) {
            throw com.unkittered.api.common.ApiException.notFound("Profile not found");
        }
        return toDto(wallet.sendGift(CurrentUser.id(), to, req.giftId()));
    }

    private WalletDto toDto(WalletService.WalletView v) {
        Map<UUID, String> names = new HashMap<>();
        for (var tx : v.transactions()) {
            if (tx.getCounterpartyId() != null) names.putIfAbsent(tx.getCounterpartyId(), null);
        }
        profiles.findAllById(names.keySet()).forEach(p -> names.put(p.getUserId(), displayName(p)));
        return new WalletDto(v.paws(), v.earned(), v.withdrawable(), v.held(), v.nextReleaseAt(),
                v.transactions().stream().map(tx -> new TransactionDto(
                        tx.getId().toString(), tx.getKind(), tx.getStatus(), tx.getPaws(), tx.getAmountKes(),
                        tx.getGiftId() == null ? null : GiftDto.of(Gift.of(tx.getGiftId())),
                        tx.getCounterpartyId() == null ? null : names.get(tx.getCounterpartyId()),
                        tx.getNote(), tx.getCreatedAt(), tx.getMethod(), tx.getCurrency(),
                        tx.getAmountMinor())).toList());
    }

    private static String displayName(Profile p) {
        return p.getName() == null || p.getName().isBlank() ? "A member" : p.getName();
    }
}
