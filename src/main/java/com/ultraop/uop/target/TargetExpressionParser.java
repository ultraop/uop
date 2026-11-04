package com.ultraop.uop.target;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Splits target lists without treating commas inside selector structures or
 * quoted NBT strings as separators.
 */
public final class TargetExpressionParser {
    private TargetExpressionParser() {}

    public static List<String> split(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Target expression is empty.");
        }

        List<String> parts = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        Deque<Character> brackets = new ArrayDeque<>();
        char quote = 0;
        boolean escaped = false;

        for (int i = 0; i < expression.length(); i++) {
            char current = expression.charAt(i);

            if (quote != 0) {
                token.append(current);
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == quote) {
                    quote = 0;
                }
                continue;
            }

            if (current == '"' || current == '\'') {
                quote = current;
                token.append(current);
                continue;
            }

            if (current == '[' && brackets.contains('[') && !brackets.contains('{')) {
                throw new IllegalArgumentException("Selector brackets cannot be nested directly.");
            }
            if (current == '[' || current == '{') {
                brackets.push(current);
                token.append(current);
                continue;
            }

            if (current == ']' || current == '}') {
                if (brackets.isEmpty()) {
                    throw new IllegalArgumentException("Target expression has an unexpected closing bracket.");
                }
                char expected = current == ']' ? '[' : '{';
                if (brackets.pop() != expected) {
                    throw new IllegalArgumentException("Target expression contains mismatched brackets.");
                }
                token.append(current);
                continue;
            }

            if (current == ',' && brackets.isEmpty()) {
                addToken(parts, token);
            } else {
                token.append(current);
            }
        }

        if (quote != 0) throw new IllegalArgumentException("Target expression has an unterminated quoted value.");
        if (!brackets.isEmpty()) throw new IllegalArgumentException("Target expression has an unclosed bracket.");

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
