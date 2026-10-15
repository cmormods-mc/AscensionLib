package com.ascensionlib.battle;

import static org.junit.jupiter.api.Assertions.*;

import com.ascensionlib.AscensionZones;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ProfileZonesTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("fantasy", path); }

    @Test void aRunLevelIsAZoneWhileItIsOpenAndNotAfter() {
        var run = id("run_ab12");
        assertEquals(0, ProfileZones.tierOf(run));
        ProfileZones.open(run, 4);
        assertEquals(4, ProfileZones.tierOf(run));
        assertTrue(ProfileZones.isZone(run));
        ProfileZones.open(run, 6);
        assertEquals(6, ProfileZones.tierOf(run), "Opening again changes the tier");
        ProfileZones.close(run);
        ProfileZones.close(run);
        assertFalse(ProfileZones.isZone(run));
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
        assertEquals(0, AscensionZones.zoneTier("fantasy:run_c"));
        assertEquals(0, AscensionZones.zoneTier(null));
    }
}
