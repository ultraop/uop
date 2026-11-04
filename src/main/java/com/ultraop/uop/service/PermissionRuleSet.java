package com.ultraop.uop.service;

import java.util.List;
import java.util.Locale;

/** Pure, server-independent matching for UOP explicit permission grants and denies. */
public final class PermissionRuleSet {
    private PermissionRuleSet() {}

    /**
     * Returns an explicit allow/deny decision, or null when no stored rule matches.
     * Exact rules are more specific than wildcard rules. At equal specificity,
     * deny wins. This lets a grant for uop.armor override !uop.*, while !uop.armor
     * overrides an inherited uop.* grant.
     */
    public static Boolean explicitDecision(List<String> storedRules, String permission) {
        if (permission == null || permission.isBlank()) {
            throw new IllegalArgumentException("Permission node is required.");
        }
        String requested = permission.trim().toLowerCase(Locale.ROOT);
        int bestSpecificity = -1;
        Boolean decision = null;

        for (String raw : storedRules) {
            if (raw == null || raw.isBlank()) continue;
            String rule = raw.trim().toLowerCase(Locale.ROOT);
            boolean deny = rule.startsWith("!");
            String node = deny ? rule.substring(1) : rule;
            if (node.isBlank() || !matches(node, requested)) continue;

            int specificity = specificity(node);
            if (specificity > bestSpecificity || specificity == bestSpecificity && deny) {
                bestSpecificity = specificity;
                decision = !deny;
            }
        }
        return decision;
    }

    private static boolean matches(String rule, String requested) {
        if (rule.equals("*")) return true;
        if (rule.equals(requested)) return true;
        if (rule.endsWith(".*")) {
            String prefix = rule.substring(0, rule.length() - 1);
            return requested.startsWith(prefix);
        }
        return false;
    }

    private static int specificity(String rule) {
        if (rule.equals("*")) return 0;
        if (rule.endsWith(".*")) return rule.length() - 2;
        return 1000 + rule.length();
    }
}
