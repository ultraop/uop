package com.ultraop.uop.target;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits comma-separated target expressions without splitting commas nested
 * inside selector argument brackets, SNBT compounds, lists, or quoted values.
 */
public final class TargetExpressionParser {
    private TargetExpressionParser() {}

    public static List<String> split(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Target expression is empty.");
        }
        List<String> parts = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        int squareDepth = 0;
        int curlyDepth = 0;
        char quote = 0;
        boolean escaped = false;
        for (int i = 0; i < expression.length(); i++) {
            char current = expression.charAt(i);
            if (quote != 0) {
                token.append(current);
                if (escaped) escaped = false;
                else if (current == '\\') escaped = true;
                else if (current == quote) quote = 0;
                continue;
            }
            if (current == '"' || current == '\'') {
                quote = current;
                token.append(current);
                continue;
            }
            switch (current) {
                case '[' -> squareDepth++;
                case ']' -> {
                    if (squareDepth == 0) throw new IllegalArgumentException("Selector has an unexpected closing bracket.");
                    squareDepth--;
                }
                case '{' -> curlyDepth++;
                case '}' -> {
                    if (curlyDepth == 0) throw new IllegalArgumentException("Selector has an unexpected closing brace.");
                    curlyDepth--;
                }
                case ',' -> {
                    if (squareDepth == 0 && curlyDepth == 0) {
                        addToken(parts, token);
                        continue;
                    }
                }
                default -> { }
            }
            token.append(current);
        }
        if (quote != 0) throw new IllegalArgumentException("Target expression contains an unclosed quoted value.");
        if (squareDepth != 0) throw new IllegalArgumentException("Selector is missing a closing bracket.");
        if (curlyDepth != 0) throw new IllegalArgumentException("Selector is missing a closing brace.");
        addToken(parts, token);
        return List.copyOf(parts);
    }

    private static void addToken(List<String> parts, StringBuilder token) {
        String value = token.toString().trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Target list contains an empty entry.");
        if (value.startsWith("★")) value = value.substring(1).trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Target list contains an empty entry.");
        parts.add(value);
        token.setLength(0);
    }
}
