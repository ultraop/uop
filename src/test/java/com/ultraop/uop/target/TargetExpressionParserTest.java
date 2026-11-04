package com.ultraop.uop.target;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TargetExpressionParserTest {
    @Test
    void splitsTeamAndPlayerTokens() {
        assertEquals(List.of("★TeamA", "★TeamB", "Steve"),
                TargetExpressionParser.split("★TeamA,★TeamB,Steve"));
    }

    @Test
    void preservesCommasInsideSelectorObjects() {
        assertEquals(List.of("@a[scores={a=1,b=2}]", "Steve"),
                TargetExpressionParser.split("@a[scores={a=1,b=2}],Steve"));
    }

    @Test
    void preservesNestedNbtListsAndQuotedCommas() {
        String selector = "@a[nbt={Inventory:[{id:\"minecraft:stone\",tag:{display:{Name:'A,B'}}},{id:\"minecraft:dirt\"}]}]";
        assertEquals(List.of(selector, "Alex"),
                TargetExpressionParser.split(selector + ",Alex"));
    }

    @Test
    void rejectsMismatchedBrackets() {
        assertThrows(IllegalArgumentException.class,
                () -> TargetExpressionParser.split("@a[scores={a=1,b=2]]"));
    }

    @Test
    void rejectsUnclosedQuotes() {
        assertThrows(IllegalArgumentException.class,
                () -> TargetExpressionParser.split("@a[nbt={Name:\"unfinished}]"));
    }

    @Test
    void rejectsEmptyListEntries() {
        assertThrows(IllegalArgumentException.class,
                () -> TargetExpressionParser.split("Steve,,Alex"));
    }
}
