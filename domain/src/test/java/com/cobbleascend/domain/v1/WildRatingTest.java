package com.cobbleascend.domain.v1;

import static org.junit.jupiter.api.Assertions.*;

import com.cobbleascend.domain.Rarity;
import java.util.*;
import org.junit.jupiter.api.Test;

class WildRatingTest {
    private final RankedRules rules = RankedRules.defaults();
    private final RankedProgression progression = new RankedProgression(rules);
    private final List<String> types = List.of("ghost", "poison");
    private final byte[] secret = "0123456789abcdef0123456789abcdef".getBytes();
    private final UUID authority = UUID.randomUUID();

    @Test void aPokemonAlwaysRatesTheSame() {
        var id = UUID.randomUUID();
        var first = progression.rateWild(secret, id, types, 22);
        for (int i = 0; i < 20; i++) assertEquals(first, progression.rateWild(secret, id, types, 22));
        assertEquals(first, new RankedProgression(RankedRules.defaults()).rateWild(secret.clone(), id, types, 22));
    }

    @Test void aScouterPreviewEqualsTheCaptureResult() {
        var random = new Random(9);
        for (int n = 0; n < 500; n++) {
            var id = new UUID(random.nextLong(), random.nextLong());
            int level = 1 + random.nextInt(100);
            var preview = progression.rateWild(secret, id, types, level);
            var captured = progression.createWild(secret, id, authority, Origin.of("wild_capture"), level, types);
            assertEquals(preview.rarity(), captured.rarity());
            assertEquals(preview.rarity(), captured.initialRarity());
            assertEquals(preview.slots(), captured.ordinarySlots());
            assertEquals(preview.upgradesOnCapture(), captured.pendingCredits());
            assertTrue(captured.ordinarySlots().stream().allMatch(s -> s.rank() == 1), "Capture spends no upgrades");
            assertNull(captured.unique());
            rules.validate(captured);
        }
    }

    @Test void theRatingDoesNotDependOnLevelOnlyTheUpgradesDo() {
        var id = UUID.randomUUID();
        var low = progression.rateWild(secret, id, types, 5);
        var high = progression.rateWild(secret, id, types, 95);
        assertEquals(low.rarity(), high.rarity());
        assertEquals(low.slots(), high.slots());
        assertEquals(0, low.upgradesOnCapture());
        assertEquals(9, high.upgradesOnCapture());
    }

    @Test void theServerSecretAndPokemonIdBothMatter() {
        int differentSecret = 0, differentPokemon = 0;
        var random = new Random(3);
        var otherSecret = "fedcba9876543210fedcba9876543210".getBytes();
        for (int n = 0; n < 200; n++) {
            var id = new UUID(random.nextLong(), random.nextLong());
            var base = progression.rateWild(secret, id, types, 30);
            if (!base.equals(progression.rateWild(otherSecret, id, types, 30))) differentSecret++;
            if (!base.equals(progression.rateWild(secret, new UUID(random.nextLong(), random.nextLong()), types, 30))) differentPokemon++;
        }
        assertTrue(differentSecret > 150, "A different secret rates Pokemon differently");
        assertTrue(differentPokemon > 150, "Different Pokemon rate differently");
    }

    @Test void rarityFollowsTheCaptureWeights() {
        var random = new Random(11);
        var counts = new EnumMap<Rarity, Integer>(Rarity.class);
        int samples = 60000;
        for (int n = 0; n < samples; n++)
            counts.merge(progression.rateWild(secret, new UUID(random.nextLong(), random.nextLong()), types, 1).rarity(), 1, Integer::sum);
        for (var rarity : Rarity.values()) {
            double expected = samples * rules.base().tier(rarity).weight() / (double) rules.base().totalWeight();
            assertEquals(expected, counts.getOrDefault(rarity, 0), Math.max(8, expected * 0.12), rarity.id());
        }
    }

    @Test void aContentVersionChangeIsAnExplicitChangeOfRating() {
        var bands = new LinkedHashMap<String, List<RankBand>>();
        rules.affixes().forEach(a -> bands.put(a.id(), a.bands()));
        var v2 = new RankedRules(rules.base(), 2, bands, List.of(new UniqueDefinition("ashen_heart", "Ashen Heart", 2)));
        var other = new RankedProgression(v2);
        int changed = 0;
        for (int n = 0; n < 100; n++) {
            var id = UUID.nameUUIDFromBytes(("p" + n).getBytes());
            if (!progression.rateWild(secret, id, types, 1).equals(other.rateWild(secret, id, types, 1))) changed++;
        }
        assertTrue(changed > 60);
    }

    @Test void weakOrMissingSecretsAreRefused() {
        var id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> progression.rateWild(new byte[8], id, types, 10));
        assertThrows(IllegalArgumentException.class, () -> progression.rateWild(null, id, types, 10));
        assertThrows(IllegalArgumentException.class, () -> progression.rateWild(secret, id, List.of("shadow"), 10));
        assertThrows(IllegalArgumentException.class, () -> progression.rateWild(secret, id, List.of(), 10));
        assertThrows(IllegalArgumentException.class, () -> progression.rateWild(secret, id, types, 0));
    }
}
