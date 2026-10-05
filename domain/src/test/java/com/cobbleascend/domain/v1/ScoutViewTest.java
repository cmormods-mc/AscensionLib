package com.cobbleascend.domain.v1;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ScoutViewTest {
    private final RankedRules rules = RankedRules.defaults();
    private final EnemyGenerator generator = new EnemyGenerator(rules);
    private final EnemyTier tier = EnemyTiers.defaults().tier("trial_rank_2");

    @Test void viewListsEverySlotWithItsRankAndRoll() {
        var enemy = generator.generate("enc-1", 0, EnemySpec.regular(tier), List.of("water"));
        var view = ScoutView.of(enemy, rules);
        assertEquals(enemy.rarity().id(), view.rarityId());
        assertEquals(enemy.slots().size(), view.slots().size());
        for (int i = 0; i < view.slots().size(); i++) {
            var slot = enemy.slots().get(i);
            var line = view.slots().get(i);
            assertEquals(slot.category().id(), line.category());
            assertEquals(rules.affix(slot.affixId()).base().name(), line.name());
            assertEquals(slot.rank(), line.rank());
            assertEquals(slot.rolledValue(), line.rolledValue());
        }
        assertEquals("", view.uniqueName());
    }

    @Test void aBossUniqueIsShownByName() {
        var unique = rules.uniques().iterator().next();
        var boss = generator.generate("enc-2", 0, EnemySpec.boss(EnemyTiers.defaults().tier("boss"), unique.id()),
                List.of("fire"));
        assertEquals(unique.name(), ScoutView.of(boss, rules).uniqueName());
    }
}
