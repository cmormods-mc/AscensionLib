package com.ascensionlib;

import com.cobbleascend.domain.v1.MaterialId;
import com.cobbleascend.domain.v1.ScouterDrops;
import com.cobbleascend.domain.v1.TowerRewardBands;
import com.cobbleascend.store.EncounterRewards;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The contract for a mod that owns an encounter (CobbleRaids, CobbleTowers) to pay its participants in the shared
 * wallet exactly once. Signatures use only {@code java.*}, so a caller can reach it by reflection when the library is
 * optional for it. The caller decides what each participant earned; the library decides only whether it is paid
 * (a victory, once per encounter, player and reward kind) and records it durably.
 *
 * <p>Call it from the encounter's end callback on the server thread, passing the encounter's own ID, which must be
 * unique and never reused. Calling again after a crash or a duplicate callback is the intended recovery: it pays
 * whoever was not yet paid and changes nothing for the rest.
 */
public final class AscensionRewards {
    private AscensionRewards() {}

    /**
     * Pays a CobbleTowers boss victory from the library's own bands ({@code TowerRewardBands}), flat per player, so
     * the caller supplies no amounts. Same exactly-once rules and result statuses as {@link #settle}; reward kind
     * {@code tower_boss}. Amounts are seeded by (encounter, player, boss floor), so a retry recomputes the same.
     *
     * @param encounterId the boss encounter's own ID (never reused)
     * @param outcome     as for {@link #settle}
     * @param fromFloor   first floor since the previous boss (1 after the entrance)
     * @param bossFloor   the boss floor just cleared
     * @param players     every participant to pay
     */
    public static Map<UUID, String> settleTowerBoss(UUID encounterId, String outcome, int fromFloor, int bossFloor,
                                                    java.util.Collection<UUID> players) {
        var payouts = new LinkedHashMap<UUID, Map<String, Long>>();
        for (var player : players) {
            var amounts = new LinkedHashMap<String, Long>();
            TowerRewardBands.DEFAULTS.payout(encounterId.toString(), player, fromFloor, bossFloor)
                    .forEach((material, amount) -> amounts.put(material.id(), amount));
            payouts.put(player, amounts);
        }
        var result = settle(encounterId, outcome, "tower_boss", payouts);
        var paid = new java.util.ArrayList<UUID>();
        result.forEach((player, status) -> { if (status.equals("GRANTED") || status.equals("ALREADY_GRANTED")) paid.add(player); });
        awardAttunement("tower_boss:" + encounterId, paid);
        return result;
    }

    /**
     * Pays a won CobbleTowers Trial (a floor-limited run) from {@code TrialRewardBands}: flat per player, victory only, reward kind
     * {@code trial_rank_N}, once per (encounter, player), and +3 attunement to each paid player's party. No entry gate or daily
     * budget yet. Same statuses as {@link #settle}.
     *
     * @param encounterId the encounter that ends the trial (its final floor's boss; never reused)
     * @param rank        1-3; {@code TrialRewardBands.rankForFloorLimit} maps a trial's floor limit to it
     */
    public static Map<UUID, String> settleTrial(UUID encounterId, String outcome, int rank, java.util.Collection<UUID> players) {
        var amounts = new LinkedHashMap<String, Long>();
        com.cobbleascend.domain.v1.TrialRewardBands.payout(rank).forEach((material, amount) -> amounts.put(material.id(), amount));
        var payouts = new LinkedHashMap<UUID, Map<String, Long>>();
        for (var player : players) payouts.put(player, amounts);
        var result = settle(encounterId, outcome, "trial_rank_" + rank, payouts);
        var paid = new java.util.ArrayList<UUID>();
        result.forEach((player, status) -> { if (status.equals("GRANTED") || status.equals("ALREADY_GRANTED")) paid.add(player); });
        awardAttunement("trial:" + encounterId, paid);
        return result;
    }

    /**
     * Gives each participant's party attunement for a won raid (a Pokemon's progress toward promotion), without paying any material:
     * Raids keeps its own rewards. Only {@code VICTORY} counts; call it for the players who earned the victory. Once per
     * (encounter, Pokemon), so a repeated call changes nothing more. Players who are offline miss it.
     *
     * @param encounterId the raid's own encounter ID (never reused); it may be any string without {@code |}
     * @param outcome     {@code VICTORY}, {@code DEFEAT} or {@code ABORTED}
     * @return how many players' parties were handled; 0 when the library is disabled or the outcome is not a victory
     */
    public static int settleRaidAttunement(String encounterId, String outcome, java.util.Collection<UUID> players) {
        if (!"VICTORY".equals(outcome)) return 0;
        if (encounterId == null || encounterId.isBlank() || encounterId.contains("|"))
            throw new IllegalArgumentException("Invalid encounter ID");
        return awardAttunement("raid:" + encounterId, players);
    }

    /**
     * The quantity of an item reward after the 777 Unique: +20% while any Pokemon in the player's party holds it, otherwise
     * {@code count} unchanged. A fraction of an item is settled by a roll seeded by {@code key}, so a retried or repeated grant of the
     * same reward gives the same amount. For item rewards only (never materials, currency or cards). Call it when the reward is
     * handed over, with the player online; offline, disabled or without 777 it returns {@code count}.
     *
     * @param key identifies the reward (source, item, claim), so the rounding is stable
     */
    public static int scaleItemQuantity(UUID player, int count, String key) {
        if (count < 1) return count;
        var service = AscensionApi.service().orElse(null);
        var server = AscensionApi.server();
        if (service == null || server == null) return count;
        var online = server.getPlayerList().getPlayer(player);
        if (online == null || !service.partyHoldsUnique(online, "triple_seven")) return count;
        return com.cobbleascend.domain.v1.ItemQuantityBonus.scale(count, player + "|" + key);
    }

    /** Attunement a Pokemon earns per qualifying victory (ECONOMY.md: 3). */
    static final int VICTORY_ATTUNEMENT = 3;

    /** Credits each player's party, once per key; players who are offline miss it. Returns the players handled. */
    private static int awardAttunement(String key, java.util.Collection<UUID> players) {
        var service = AscensionApi.service().orElse(null);
        var server = AscensionApi.server();
        if (service == null || server == null) return 0;
        int handled = 0;
        for (var player : players) {
            var online = server.getPlayerList().getPlayer(player);
            if (online == null) continue;
            service.awardPartyAttunement(online, key, VICTORY_ATTUNEMENT);
            handled++;
        }
        return handled;
    }

    /**
     * Rolls the Scouter drop for a cleared tower floor, once per participant ({@code ScouterDrops}: 5%, 15% on a
     * Keen Eye floor), and pays each winner one Scouter. Reward kind {@code scouter_drop}; the roll is seeded by
     * (encounter, player), so a retry recomputes the same winners. Returns only the players who rolled a drop (empty
     * when nobody did, in which case nothing is written); statuses are as for {@link #settle}.
     *
     * @param encounterId  the floor's boss encounter ID (never reused)
     * @param keenEyeFloor whether the floor uses the raised chance
     */
    public static Map<UUID, String> settleScouterDrops(UUID encounterId, String outcome, boolean keenEyeFloor,
                                                       java.util.Collection<UUID> players) {
        var payouts = new LinkedHashMap<UUID, Map<String, Long>>();
        for (var player : players) {
            if (ScouterDrops.DEFAULTS.rollFor(keenEyeFloor, encounterId.toString(), player))
                payouts.put(player, Map.of(MaterialId.SCOUTER.id(), 1L));
        }
        if (payouts.isEmpty()) return new LinkedHashMap<>();
        return settle(encounterId, outcome, "scouter_drop", payouts);
    }

    /**
     * @param encounterId the encounter's own ID
     * @param outcome   {@code VICTORY}, {@code DEFEAT} or {@code ABORTED}; only a victory pays (these are the names
     *                    of CobbleRaids' {@code EncounterOutcome}, so {@code result.outcome().name()} is passed as is)
     * @param rewardKind  a stable lower-case key such as {@code boss_defeated}; one encounter may settle several
     * @param payouts     per player, material id (for example {@code resonance_dust}) to a positive amount
     * @return per player one of {@code GRANTED}, {@code ALREADY_GRANTED}, {@code NOT_PAID}, {@code CONFLICT} (already
     *         paid with different amounts; the first stands), {@code REFUSED} (the wallet refused this player's
     *         credit) or {@code DISABLED} (no running world, or progression is disabled for it; nothing was written,
     *         so settle again once it is back)
     * @throws IllegalArgumentException for a malformed call (unknown outcome or material, bad ID, empty payout)
     */
    public static Map<UUID, String> settle(UUID encounterId, String outcome, String rewardKind,
                                           Map<UUID, Map<String, Long>> payouts) {
        var parsed = new java.util.ArrayList<EncounterRewards.Payout>();
        payouts.forEach((player, materials) -> {
            var amounts = new java.util.EnumMap<MaterialId, Long>(MaterialId.class);
            materials.forEach((id, amount) -> amounts.put(MaterialId.fromId(id), amount));
            parsed.add(new EncounterRewards.Payout(player, amounts));
        });
        var settlement = new EncounterRewards.Settlement(encounterId.toString(),
                EncounterRewards.EncounterOutcome.valueOf(outcome), rewardKind, parsed);
        var rewards = AscensionApi.encounterRewards();
        var result = new LinkedHashMap<UUID, String>();
        if (rewards.isEmpty()) {
            parsed.forEach(payout -> result.put(payout.playerId(), "DISABLED"));
            return result;
        }
        rewards.get().settle(settlement).forEach((player, status) -> result.put(player, status.name()));
        return result;
    }
}
