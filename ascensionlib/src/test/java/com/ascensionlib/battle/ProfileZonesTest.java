package com.ascensionlib.battle;

import static org.junit.jupiter.api.Assertions.*;

import com.ascensionlib.AscensionZones;
import net.minecraft.resources.ResourceLocation;
import com.cobbleascend.domain.v1.Tunables;
import com.cobbleascend.domain.v1.ZoneSettings;
import org.junit.jupiter.api.Test;

class ProfileZonesTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("fantasy", path); }

    @Test void aRunLevelIsAZoneWhileItIsOpenAndNotAfter() {
        var run = id("run_ab12");
        assertEquals(1, ProfileZones.tierOf(run), "an unregistered dimension is a tier-1 zone by default");
        assertEquals(0, ProfileZones.registeredTierOf(run));
        ProfileZones.open(run, 4);
        assertEquals(4, ProfileZones.tierOf(run));
        assertTrue(ProfileZones.isZone(run));
        ProfileZones.open(run, 6);
        assertEquals(6, ProfileZones.tierOf(run), "Opening again changes the tier");
        ProfileZones.close(run);
        ProfileZones.close(run);
        assertEquals(1, ProfileZones.tierOf(run), "closed, it is an ordinary dimension again");
        assertEquals(0, ProfileZones.registeredTierOf(run));
    }

    @Test void theConfigurationSetsTheDefaultAndOverridesPerDimension() {
        var nether = ResourceLocation.fromNamespaceAndPath("minecraft", "the_nether");
        var run = id("run_override");
        try {
            Tunables.use(withZones(new ZoneSettings(0, java.util.Map.of(), ZoneSettings.DEFAULT_TILT, java.util.Map.of())));
            assertEquals(0, ProfileZones.tierOf(nether), "default tier 0 switches profiles off everywhere");
            assertFalse(ProfileZones.isZone(nether));
            ProfileZones.open(run, 5);
            assertEquals(5, ProfileZones.tierOf(run), "a mod's zone still counts when the default is off");
            Tunables.use(withZones(new ZoneSettings(1, java.util.Map.of("minecraft:the_nether", 3, "fantasy:run_override", 0), ZoneSettings.DEFAULT_TILT, java.util.Map.of())));
            assertEquals(3, ProfileZones.tierOf(nether), "a named dimension takes its own tier");
            assertEquals(0, ProfileZones.tierOf(run), "and the owner's setting beats a zone a mod opened");
            assertEquals(1, ProfileZones.tierOf(id("elsewhere")));
            assertEquals(0, ProfileZones.tierOf(null));
        } finally {
            ProfileZones.close(run);
            Tunables.use(Tunables.defaults());
        }
    }

    private static Tunables withZones(ZoneSettings zones) {
        var d = Tunables.defaults();
        return new Tunables(d.budget(), d.withdrawalFeePercent(), d.respecMarksPrice(), d.attunementPerVictory(), d.towerCompletion(), d.miniChest(), zones);
    }

    @Test void allowMakesATierOneZoneAndTheTowerIsAlwaysOne() {
        var fixed = id("fixed_zone");
        ProfileZones.allow(fixed);
        assertEquals(1, ProfileZones.tierOf(fixed));
        ProfileZones.close(fixed);
        assertEquals(1, ProfileZones.tierOf(ProfileZones.TOWER));
        ProfileZones.close(ProfileZones.TOWER);
        assertTrue(ProfileZones.isZone(ProfileZones.TOWER), "The tower dimension cannot be closed");
    }

    @Test void tiersOutsideTheRangeAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> ProfileZones.open(id("x"), 0));
        assertThrows(IllegalArgumentException.class, () -> ProfileZones.open(id("x"), ProfileZones.MAX_TIER + 1));
        assertThrows(NullPointerException.class, () -> ProfileZones.open(null, 1));
    }

    @Test void theContractTakesStringsAndNeverThrowsOnBadInput() {
        assertTrue(AscensionZones.openZone("fantasy:run_c", 3));
        assertEquals(3, AscensionZones.zoneTier("fantasy:run_c"));
        assertFalse(AscensionZones.openZone("fantasy:run_c", 11));
        assertFalse(AscensionZones.openZone("Not A Valid Id", 3));
        assertFalse(AscensionZones.openZone(null, 3));
        assertEquals(3, AscensionZones.zoneTier("fantasy:run_c"), "A refused call changes nothing");
        AscensionZones.closeZone("fantasy:run_c");
        AscensionZones.closeZone("fantasy:run_c");
        AscensionZones.closeZone(null);
        assertEquals(1, AscensionZones.zoneTier("fantasy:run_c"), "closed: the default tier");
        assertEquals(0, AscensionZones.zoneTier(null));
    }
}
