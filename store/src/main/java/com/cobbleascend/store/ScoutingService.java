package com.cobbleascend.store;

import com.cobbleascend.domain.v1.CombatSnapshot;
import com.cobbleascend.domain.v1.MaterialId;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Scouter rules: one use reveals ONE enemy for ONE encounter, spent only on a valid reveal, never stored beyond
 * the encounter. The wallet debit is a durable idempotent store operation; the reveal itself lives in memory
 * because an encounter does not survive a restart. Encounter IDs must be unique and never reused, otherwise a
 * replayed spend could reveal for free. Tower, raid and wild adapters call this; none of them own the wallet.
 */
public final class ScoutingService {
    public enum Status { REVEALED, ALREADY_SCOUTED, NO_SCOUTER }

    /** PERSONAL reveals to the user only (towers). SHARED reveals to the whole encounter party (raids). */
    public enum Scope { PERSONAL, SHARED }

    public record Result(Status status, CombatSnapshot snapshot) {}

    private final ProgressionStore store;
    private final Map<String, Set<String>> revealed = new HashMap<>();

    public ScoutingService(ProgressionStore store) { this.store = Objects.requireNonNull(store); }

    /** Reward entry point for CobbleTowers. The caller's operation ID makes a retried reward pay out once. */
    public synchronized void grantScouters(UUID operationId, UUID playerId, long count, String reason) {
        store.grant(operationId, playerId, Map.of(MaterialId.SCOUTER, count), reason);
    }

    /**
     * Reveals {@code enemy} (the frozen snapshot the encounter holds) to the player, spending one Scouter. A target
     * that is already revealed to this player, or a player with none left, costs nothing.
     */
    public synchronized Result use(UUID playerId, String encounterId, CombatSnapshot enemy) {
        return use(playerId, encounterId, enemy, Scope.PERSONAL);
    }

    /**
     * As above, with a scope. A SHARED reveal is recorded for the encounter itself; the adapter shows it to the
     * encounter's participants only. The Scouter is always spent from the user's own wallet.
     */
    public synchronized Result use(UUID playerId, String encounterId, CombatSnapshot enemy, Scope scope) {
        if (enemy.source() != CombatSnapshot.Source.ENEMY)
            throw new IllegalArgumentException("Only an encounter enemy can be scouted");
        if (encounterId.isBlank() || encounterId.contains("|")) throw new IllegalArgumentException("Invalid encounter ID");
        if (isRevealed(playerId, encounterId, enemy.subjectId())) return new Result(Status.ALREADY_SCOUTED, enemy);
        String key = (scope == Scope.SHARED ? "shared" : playerId.toString()) + "|" + encounterId;
        if (store.wallet(playerId).balance(MaterialId.SCOUTER) < 1) return new Result(Status.NO_SCOUTER, null);
        UUID operation = UUID.nameUUIDFromBytes(("scout|" + scope + "|" + key + "|" + enemy.subjectId()).getBytes(StandardCharsets.UTF_8));
        store.spend(operation, playerId, Map.of(MaterialId.SCOUTER, 1L), "scout " + encounterId + " " + enemy.subjectId());
        revealed.computeIfAbsent(key, k -> new HashSet<>()).add(enemy.subjectId());
        return new Result(Status.REVEALED, enemy);
    }

    /** True when the enemy was revealed to this player personally or to the encounter's whole party. */
    public synchronized boolean isRevealed(UUID playerId, String encounterId, String enemyId) {
        return revealed.getOrDefault(playerId + "|" + encounterId, Set.of()).contains(enemyId)
                || revealed.getOrDefault("shared|" + encounterId, Set.of()).contains(enemyId);
    }

    /** Forgets every reveal of the encounter (it ended, the player left or the server stopped). */
    public synchronized void endEncounter(String encounterId) {
        revealed.keySet().removeIf(key -> key.endsWith("|" + encounterId));
    }
}
