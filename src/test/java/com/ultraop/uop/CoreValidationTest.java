package com.ultraop.uop;

import com.ultraop.uop.service.ArmorKitService;
import com.ultraop.uop.service.InventoryService;
import com.ultraop.uop.target.TargetResolver;
import com.ultraop.uop.team.TeamService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CoreValidationTest {
    @Test
    void teamNamesAcceptOnlySafeIdentifiers() {
        assertDoesNotThrow(() -> TeamService.validateTeamName("Red_1"));
        assertThrows(IllegalArgumentException.class, () -> TeamService.validateTeamName("Red Team"));
        assertThrows(IllegalArgumentException.class, () -> TeamService.validateTeamName("../data"));
    }

    @Test
    void playerNamesFollowMinecraftNameConstraints() {
        assertDoesNotThrow(() -> TeamService.validatePlayerName("UltraOP"));
        assertThrows(IllegalArgumentException.class, () -> TeamService.validatePlayerName("name with spaces"));
        assertThrows(IllegalArgumentException.class, () -> TeamService.validatePlayerName("name-too-long-123"));
    }

    @Test
    void teamSuggestionMarkerIsRemovedBeforeResolution() {
        assertEquals("Red", TargetResolver.clean("★Red"));
        assertEquals("@a[distance=..10,limit=3]", TargetResolver.clean("@a[distance=..10,limit=3]"));
    }

    @Test
    void armorCatalogsKeepDirectAndLegacyMaterialSetsDistinct() {
        ArmorKitService kits = new ArmorKitService();
        assertEquals(List.of("iron", "gold", "diamond", "netherite", "mixed"), kits.materials());
        assertEquals(List.of("leather", "chainmail", "iron", "gold", "diamond", "netherite"), kits.classicMaterials());
        assertEquals(List.of("sword", "mace", "axe", "bow", "crossbow", "trident", "pickaxe", "shovel", "hoe"), kits.weaponNames());
    }

    @Test
    void inventorySlotAndAmountBoundsAreEnforced() {
        assertEquals(40, InventoryService.validateSlot(40));
        assertThrows(IllegalArgumentException.class, () -> InventoryService.validateSlot(-1));
        assertThrows(IllegalArgumentException.class, () -> InventoryService.validateSlot(41));
        assertEquals(99, InventoryService.validateAmount(99));
        assertThrows(IllegalArgumentException.class, () -> InventoryService.validateAmount(0));
    }
}