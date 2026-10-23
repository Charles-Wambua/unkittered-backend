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
    private final ProfileRepository profiles;

    public WalletController(WalletService wallet, ProfileRepository profiles) {
        this.wallet = wallet;
        this.profiles = profiles;
    }

    public record GiftDto(String id, String name, String emoji, int paws) {
        static GiftDto of(Gift g) { return new GiftDto(g.id, g.name, g.emoji, g.paws); }
    }

    public record PackDto(String id, int paws, int bonus, int priceKes) {
        static PackDto of(PawsPack p) { return new PackDto(p.id, p.paws, p.bonus, p.priceKes); }
    }

    public record CatalogDto(List<GiftDto> gifts, List<PackDto> packs, double creatorShare,
                             double withdrawalFee, int minWithdrawal, boolean mpesaEnabled) { }

    public record TransactionDto(String id, String kind, String status, int paws, Integer amountKes,
                                 GiftDto gift, String counterpartyName, String note, Instant createdAt) { }

    public record WalletDto(int paws, int earned, List<TransactionDto> transactions) { }

    public record TopUpRequest(@NotBlank String packId, @NotBlank String phone) { }

    public record WithdrawRequest(@Min(1) int paws, @NotBlank String phone) { }

    public record SendGiftRequest(@NotBlank String toProfileId, @NotBlank String giftId) { }

    @Operation(summary = "Gift and Paws-pack catalogue, and the wallet rules")
    @GetMapping("/wallet/catalog")
    public CatalogDto catalog() {
        return new CatalogDto(
                Arrays.stream(Gift.values()).map(GiftDto::of).toList(),
                Arrays.stream(PawsPack.values()).map(PackDto::of).toList(),
                WalletCatalog.CREATOR_SHARE, WalletCatalog.WITHDRAWAL_FEE, WalletCatalog.MIN_WITHDRAWAL,
                wallet.mpesaEnabled());
    }

    @Operation(summary = "My Paws balances and the last 100 transactions")
    @GetMapping("/me/wallet")
    public WalletDto mine() {
        return toDto(wallet.view(CurrentUser.id()));
    }

    @Operation(summary = "Buy a Paws pack with M-Pesa (STK push to the phone)")
    @PostMapping("/me/wallet/top-ups")
    public Map<String, Object> topUp(@Valid @RequestBody TopUpRequest req) {
        var tx = wallet.startTopUp(CurrentUser.id(), req.packId(), req.phone());
        return Map.of("status", tx.getStatus(), "wallet", mine());
    }

    @Operation(summary = "Withdraw earned Paws to M-Pesa (min 500, 15% fee)")
    @PostMapping("/me/wallet/withdrawals")
    public Map<String, Object> withdraw(@Valid @RequestBody WithdrawRequest req) {
        var tx = wallet.withdraw(CurrentUser.id(), req.paws(), req.phone());
        return Map.of("status", tx.getStatus(), "wallet", mine());
    }

    @Operation(summary = "Send someone a gift (half its price becomes their earnings)")
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
        return new WalletDto(v.paws(), v.earned(), v.transactions().stream().map(tx -> new TransactionDto(
                tx.getId().toString(), tx.getKind(), tx.getStatus(), tx.getPaws(), tx.getAmountKes(),
                tx.getGiftId() == null ? null : GiftDto.of(Gift.of(tx.getGiftId())),
                tx.getCounterpartyId() == null ? null : names.get(tx.getCounterpartyId()),
                tx.getNote(), tx.getCreatedAt())).toList());
    }

    private static String displayName(Profile p) {
        return p.getName() == null || p.getName().isBlank() ? "A member" : p.getName();
    }
}
