package com.ultraop.uop.service;

import com.ultraop.uop.storage.UopStore;
import com.ultraop.uop.team.TeamService;
import com.ultraop.uop.target.TargetResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DamageService {
    private final UopStore store;
    private final TeamService teams;
    private final TargetResolver targets;

    public DamageService(UopStore store, TeamService teams, TargetResolver targets) {
        this.store = store;
        this.teams = teams;
        this.targets = targets;
    }

    private double clamp(double value) {
        if (!Double.isFinite(value) || value < 0.0 || value > 10.0) {
            throw new IllegalArgumentException("Damage multiplier must be between 0.0 and 10.0.");
        }
        return value;
    }

    private String descriptor(CommandSender sender, String expression) {
        String value = TargetResolver.clean(expression);
        if (teams.exists(value)) return "team:" + value.toLowerCase(Locale.ROOT);
        List<String> names = targets.resolveNames(sender, value);
        return "players:" + String.join(",", names).toLowerCase(Locale.ROOT);
    }

    public void global(double multiplier) {
        store.data().set("damage.global", clamp(multiplier));
        store.save();
    }

    public void scoped(CommandSender sender, String scope, String expression, double multiplier) {
        if (!scope.equals("outgoing") && !scope.equals("incoming")) throw new IllegalArgumentException("Invalid damage scope.");
        String descriptor = descriptor(sender, expression);
        String path = "damage.rules." + scope;
        List<Map<?, ?>> old = store.data().getMapList(path);
        List<Map<String, Object>> next = new ArrayList<>();
        for (Map<?, ?> entry : old) {
            if (!descriptor.equals(entry.get("target"))) next.add(copy(entry));
        }
        Map<String, Object> rule = new HashMap<>();
        rule.put("target", descriptor);
        rule.put("multiplier", clamp(multiplier));
        next.add(rule);
        store.data().set(path, next);
        store.save();
    }

    public void pair(CommandSender sender, String attacker, String victim, double multiplier) {
        String a = descriptor(sender, attacker);
        String v = descriptor(sender, victim);
        List<Map<?, ?>> old = store.data().getMapList("damage.rules.pairs");
        List<Map<String, Object>> next = new ArrayList<>();
        for (Map<?, ?> entry : old) {
            if (!(a.equals(entry.get("attacker")) && v.equals(entry.get("victim")))) next.add(copy(entry));
        }
        Map<String, Object> rule = new HashMap<>();
        rule.put("attacker", a);
        rule.put("victim", v);
        rule.put("multiplier", clamp(multiplier));
        next.add(rule);
        store.data().set("damage.rules.pairs", next);
        store.save();
    }

    private Map<String, Object> copy(Map<?, ?> source) {
        Map<String, Object> copy = new HashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) copy.put(String.valueOf(entry.getKey()), entry.getValue());
        return copy;
    }

    private boolean matches(String descriptor, Player player) {
        if (descriptor == null) return false;
        if (descriptor.startsWith("team:")) return teams.contains(descriptor.substring(5), player.getName());
        if (descriptor.startsWith("players:")) {
            for (String name : descriptor.substring(8).split(",")) {
                if (name.equalsIgnoreCase(player.getName())) return true;
            }
        }
        return false;
    }

    private int specificity(String descriptor) {
        if (descriptor == null) return 0;
        if (descriptor.startsWith("players:")) return 2;
        if (descriptor.startsWith("team:")) return 1;
        return 0;
    }

    private Double matchingRule(String path, Player player) {
        Double selected = null;
        int bestSpecificity = -1;
        for (Map<?, ?> entry : store.data().getMapList(path)) {
            String descriptor = String.valueOf(entry.get("target"));
            if (!matches(descriptor, player)) continue;
            Object value = entry.get("multiplier");
            if (!(value instanceof Number number)) continue;
            int currentSpecificity = specificity(descriptor);
            // Most specific target wins; for equal specificity, the most recently saved rule wins.
            if (currentSpecificity >= bestSpecificity) {
                bestSpecificity = currentSpecificity;
                selected = clamp(number.doubleValue());
            }
        }
        return selected;
    }

    public double multiplier(Player attacker, Player victim) {
        if (attacker != null) {
            String team = teams.teamOf(attacker.getName());
            if (team != null && team.equalsIgnoreCase(teams.teamOf(victim.getName()))
                    && !teams.friendlyFire(team)) return 0.0;
            Double pairMultiplier = null;
            int pairSpecificity = -1;
            for (Map<?, ?> entry : store.data().getMapList("damage.rules.pairs")) {
                String a = String.valueOf(entry.get("attacker"));
                String v = String.valueOf(entry.get("victim"));
                if (!matches(a, attacker) || !matches(v, victim) || !(entry.get("multiplier") instanceof Number n)) continue;
                int current = specificity(a) + specificity(v);
                if (current >= pairSpecificity) {
                    pairSpecificity = current;
                    pairMultiplier = clamp(n.doubleValue());
                }
            }
            if (pairMultiplier != null) return pairMultiplier;
            Double outgoing = matchingRule("damage.rules.outgoing", attacker);
            if (outgoing != null) return outgoing;
        }
        Double incoming = matchingRule("damage.rules.incoming", victim);
        if (incoming != null) return incoming;
        return clamp(store.data().getDouble("damage.global", 1.0));
    }

    public void resetGlobal() {
        store.data().set("damage.global", 1.0);
        store.save();
    }

    public void resetScope(String scope, CommandSender sender, String expression) {
        String descriptor = descriptor(sender, expression);
        String path = "damage.rules." + scope;
        List<Map<?, ?>> old = store.data().getMapList(path);
        List<Map<String, Object>> next = new ArrayList<>();
        for (Map<?, ?> entry : old) if (!descriptor.equals(entry.get("target"))) next.add(copy(entry));
        store.data().set(path, next);
        store.save();
    }

    public void resetPair(CommandSender sender, String attacker, String victim) {
        String a = descriptor(sender, attacker), v = descriptor(sender, victim);
        List<Map<?, ?>> old = store.data().getMapList("damage.rules.pairs");
        List<Map<String, Object>> next = new ArrayList<>();
        for (Map<?, ?> entry : old) {
            if (!(a.equals(entry.get("attacker")) && v.equals(entry.get("victim")))) next.add(copy(entry));
        }
        store.data().set("damage.rules.pairs", next);
        store.save();
    }

    public void resetAll() {
        store.data().set("damage", null);
        store.save();
    }

    public List<String> describeRules() {
        List<String> result = new ArrayList<>();
        result.add("global=" + global());
        for (String scope : List.of("outgoing", "incoming")) {
            for (Map<?, ?> entry : store.data().getMapList("damage.rules." + scope)) {
                result.add(scope + " " + entry.get("target") + " x" + entry.get("multiplier"));
            }
        }
        for (Map<?, ?> entry : store.data().getMapList("damage.rules.pairs")) {
            result.add("pair " + entry.get("attacker") + " -> " + entry.get("victim") + " x" + entry.get("multiplier"));
        }
        return result;
    }

    public double global() {
        return store.data().getDouble("damage.global", 1.0);
    }
}