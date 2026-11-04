package com.ultraop.uop;

import com.ultraop.uop.service.ArmorKitService;
import com.ultraop.uop.service.InventoryService;
import com.ultraop.uop.target.TargetResolver;
import com.ultraop.uop.target.TargetExpressionParser;
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
    void targetParserKeepsSelectorCommasInsideSelectorArguments() {
        assertEquals(List.of("@a[distance=..10,limit=2]", "Red", "UltraOP"),
                TargetExpressionParser.split("@a[distance=..10,limit=2],Red,UltraOP"));
    }

    @Test
    void targetParserTrimsWhitespaceAndKeepsSingleSelector() {
        assertEquals(List.of("@a[gamemode=survival,sort=nearest]"),
                TargetExpressionParser.split("  @a[gamemode=survival,sort=nearest]  "));
    }

    @Test
    void targetParserRejectsEmptyOrMalformedEntries() {
        assertThrows(IllegalArgumentException.class, () -> TargetExpressionParser.split(""));
        assertThrows(IllegalArgumentException.class, () -> TargetExpressionParser.split("Red,,Blue"));
        assertThrows(IllegalArgumentException.class, () -> TargetExpressionParser.split(",Red"));
        assertThrows(IllegalArgumentException.class, () -> TargetExpressionParser.split("@a[distance=..10"));
        assertThrows(IllegalArgumentException.class, () -> TargetExpressionParser.split("@a]"));
        assertThrows(IllegalArgumentException.class, () -> TargetExpressionParser.split("@a[[limit=2]]"));
        assertEquals(List.of("@a[nbt={Inventory:[{id:minecraft:stone}]}]", "Red"),
                TargetExpressionParser.split("@a[nbt={Inventory:[{id:minecraft:stone}]}],★Red"));
        assertEquals(List.of("Red", "Blue"), TargetExpressionParser.split("★Red,★Blue"));
    }

    @Test
    void armorSetArgumentsValidateMaterialModeAndWeaponFlagBeforeMutation() {
        ArmorKitService kits = new ArmorKitService();
        assertDoesNotThrow(() -> kits.validateSetArgs("netherite", "max", false, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> kits.validateSetArgs("invalid", "plain", false, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> kits.validateSetArgs("iron", "enchanted 11", false, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> kits.validateSetArgs("iron", "plain", true, List.of("invalid-tool")));
        assertThrows(IllegalArgumentException.class,
                () -> kits.validateSetArgs("iron", "plain", false, List.of("sword")));
    }

    @Test
    void weaponSetArgumentsRejectEmptyAndUnknownSelectionsWithoutServerBootstrap() {
        ArmorKitService kits = new ArmorKitService();
        assertThrows(IllegalArgumentException.class,
                () -> kits.validateWeaponArgs("plain", List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> kits.validateWeaponArgs("plain", List.of("magic-staff")));
        assertThrows(IllegalArgumentException.class,
                () -> kits.validateWeaponArgs("enchanted 11", List.of("sword")));
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