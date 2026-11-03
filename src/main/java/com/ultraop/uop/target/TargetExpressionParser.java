package com.ultraop.uop.target;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses a comma-separated target expression while preserving commas inside
 * Minecraft selector brackets, such as @a[distance=..10,limit=2].
 */
public final class TargetExpressionParser {
    private TargetExpressionParser() {}

    public static List<String> split(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Target expression is empty.");
        }

        List<String> parts = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        boolean inSelectorArguments = false;

        for (int i = 0; i < expression.length(); i++) {
            char current = expression.charAt(i);

            if (current == '[') {
                if (inSelectorArguments) {
                    throw new IllegalArgumentException("Nested selector brackets are not supported.");
                }
                inSelectorArguments = true;
            } else if (current == ']') {
                if (!inSelectorArguments) {
                    throw new IllegalArgumentException("Selector has an unexpected closing bracket.");
                }
                inSelectorArguments = false;
            }

            if (current == ',' && !inSelectorArguments) {
                addToken(parts, token);
            } else {
                token.append(current);
            }
        }

        if (inSelectorArguments) {
            throw new IllegalArgumentException("Selector is missing a closing bracket.");
        }

        addToken(parts, token);
        return List.copyOf(parts);
    }

    private static void addToken(List<String> parts, StringBuilder token) {
        String value = token.toString().trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Target list contains an empty entry.");
        }
        parts.add(value);
        token.setLength(0);
    }
}
