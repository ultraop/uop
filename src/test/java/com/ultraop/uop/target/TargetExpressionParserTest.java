package com.ultraop.uop.target;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TargetExpressionParserTest {
    @Test void keepsSelectorOptionCommasInsideOneTarget() {
        assertEquals(List.of("@a[distance=..10,limit=2]", "TeamBlue", "PlayerOne"),
                TargetExpressionParser.split("@a[distance=..10,limit=2],TeamBlue,PlayerOne"));
    }
    @Test void trimsWhitespace() {
        assertEquals(List.of("@a[gamemode=survival,sort=nearest]"),
                TargetExpressionParser.split("  @a[gamemode=survival,sort=nearest]  "));
    }
    @Test void rejectsEmptyEntries() {
        assertThrows(IllegalArgumentException.class, () -> TargetExpressionParser.split("TeamBlue,,PlayerOne"));
        assertThrows(IllegalArgumentException.class, () -> TargetExpressionParser.split(",TeamBlue"));
    }
    @Test void rejectsMalformedBrackets() {
        assertThrows(IllegalArgumentException.class, () -> TargetExpressionParser.split("@a[distance=..10"));
        assertThrows(IllegalArgumentException.class, () -> TargetExpressionParser.split("@a]"));
        assertThrows(IllegalArgumentException.class, () -> TargetExpressionParser.split("@a[[limit=2]]"));
    }
}
