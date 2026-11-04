package com.ultraop.uop.target;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Parses comma-separated target expressions without splitting nested selector data. */
public final class TargetExpressionParser {
    private TargetExpressionParser() {}

    public static List<String> split(String expression) {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Target expression is empty.");
        }
        List<String> parts = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        Deque<Character> delimiters = new ArrayDeque<>();
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
            if (current == '[' || current == '{' || current == '(') {
                // Nested square brackets are valid inside NBT compounds (e.g. Inventory:[...]),
                // but a selector cannot contain a second '[' directly inside its own argument list.
                if (current == '[' && delimiters.contains('[') && !delimiters.contains('{')) {
                    throw new IllegalArgumentException("Nested selector brackets are only valid inside an NBT compound.");
                }
                delimiters.push(current);
                token.append(current);
                continue;
            }
            if (current == ']' || current == '}' || current == ')') {
                if (delimiters.isEmpty() || !matches(delimiters.pop(), current)) {
                    throw new IllegalArgumentException("Target expression has an unmatched closing delimiter.");
                }
                token.append(current);
                continue;
            }
            if (current == ',' && delimiters.isEmpty()) {
                addToken(parts, token);
            } else {
                token.append(current);
            }
        }
        if (quote != 0) throw new IllegalArgumentException("Target expression has an unterminated quoted value.");
        if (!delimiters.isEmpty()) throw new IllegalArgumentException("Target expression has an unclosed selector/data delimiter.");
        addToken(parts, token);
        return List.copyOf(parts);
    }

    private static boolean matches(char opening, char closing) {
        return opening == '[' && closing == ']'
                || opening == '{' && closing == '}'
                || opening == '(' && closing == ')';
    }

    private static void addToken(List<String> parts, StringBuilder token) {
        String value = token.toString().trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Target list contains an empty entry.");
        parts.add(value);
        token.setLength(0);
    }
}