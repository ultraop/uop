package com.ultraop.uop.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InventoryValidationTest {
    @Test void acceptsInventorySlotBounds() {
        assertEquals(0, InventoryService.validateSlot(0));
        assertEquals(40, InventoryService.validateSlot(40));
    }
    @Test void rejectsInvalidInventorySlots() {
        assertThrows(IllegalArgumentException.class, () -> InventoryService.validateSlot(-1));
        assertThrows(IllegalArgumentException.class, () -> InventoryService.validateSlot(41));
    }
    @Test void acceptsStackAmountBounds() {
        assertEquals(1, InventoryService.validateAmount(1));
        assertEquals(99, InventoryService.validateAmount(99));
    }
    @Test void rejectsInvalidStackAmounts() {
        assertThrows(IllegalArgumentException.class, () -> InventoryService.validateAmount(0));
        assertThrows(IllegalArgumentException.class, () -> InventoryService.validateAmount(100));
    }
}
