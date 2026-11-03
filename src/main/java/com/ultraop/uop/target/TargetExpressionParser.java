package com.ultraop.uop.target;

import java.util.ArrayList;
import java.util.List;

/** Splits comma-separated targets without splitting selector arguments. */
public final class TargetExpressionParser {
    private TargetExpressionParser() {}

    public static List<String> split(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Target expression is empty.");
        }
        List<String> parts = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < expression.length(); i++) {
            char current = expression.charAt(i);
            if (current == '[' && ++depth > 1) {
                throw new IllegalArgumentException("Nested selector brackets are not supported.");
            }
            if (current == ']') {
                if (--depth < 0) throw new IllegalArgumentException("Selector has an unexpected closing bracket.");
            }
            if (current == ',' && depth == 0) {
                add(parts, token);
            } else {
                token.append(current);
            }
        }
        if (depth != 0) throw new IllegalArgumentException("Selector is missing a closing bracket.");
        add(parts, token);
        return List.copyOf(parts);
    }

    private static void add(List<String> parts, StringBuilder token) {
        String value = token.toString().trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Target list contains an empty entry.");
        parts.add(value);
        token.setLength(0);
    }
}
