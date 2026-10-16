package com.ascensionlib;

import static org.junit.jupiter.api.Assertions.*;

import com.cobbleascend.domain.v1.Tunables;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TunablesConfigTest {
    @TempDir Path dir;

    @Test void aNewInstallGetsAFileHoldingEveryDefaultAndTheDefaultsApply() throws Exception {
        var file = dir.resolve("config/ascensionlib.json");
        var result = TunablesConfig.read(file);
        assertTrue(Files.exists(file), "the template is written");
        assertTrue(result.problems().isEmpty());
        assertEquals(Tunables.defaults().toJson(), result.tunables().toJson());
        var text = Files.readString(file);
        for (var key : new String[] {"_about", "dailyBudget", "withdrawalFeePercent", "hourglassMarksPrice", "attunementPerVictory", "towerCompletion", "towerMiniChest", "crownWeights"})
            assertTrue(text.contains("\"" + key + "\""), key + " is in the template");
        var again = TunablesConfig.read(file);
        assertTrue(again.problems().isEmpty(), again.problems().toString());
    }

    @Test void anEditedValueIsPickedUpAndTheFileIsNeverRewritten() throws Exception {
        var file = dir.resolve("ascensionlib.json");
        String edited = "{\"hourglassMarksPrice\": 60, \"dailyBudget\": {\"fullChestsPerDay\": 12}}";
        Files.writeString(file, edited);
        var result = TunablesConfig.read(file);
        assertEquals(60, result.tunables().respecMarksPrice());
        assertEquals(12, result.tunables().budget().fullPerDay());
        assertEquals(edited, Files.readString(file), "an existing file is left exactly as it is");
        var changes = TunablesConfig.differences(Tunables.defaults(), result.tunables());
        assertEquals(2, changes.size(), changes.toString());
        assertTrue(changes.stream().anyMatch(c -> c.startsWith("hourglassMarksPrice: 90 -> 60")));
        assertTrue(changes.stream().anyMatch(c -> c.startsWith("dailyBudget.fullChestsPerDay: 20 -> 12")));
    }

    @Test void aBrokenFileLeavesTheDefaultsInForceAndIsNotTouched() throws Exception {
        var file = dir.resolve("ascensionlib.json");
        Files.writeString(file, "{ this is not json");
        var result = TunablesConfig.read(file);
        assertEquals(Tunables.defaults().toJson(), result.tunables().toJson());
        assertEquals(1, result.problems().size());
        assertEquals("{ this is not json", Files.readString(file));
        Files.writeString(file, "");
        assertEquals(1, TunablesConfig.read(file).problems().size(), "an empty file is a problem too, not a crash");
    }

    @Test void noChangeMeansNoDifferences() {
        assertTrue(TunablesConfig.differences(Tunables.defaults(), Tunables.defaults()).isEmpty());
    }
}
