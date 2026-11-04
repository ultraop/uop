package com.ultraop.uop.service;
import com.ultraop.uop.storage.UopStore;
import com.ultraop.uop.target.TargetResolver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
public final class NameService {
    private static final String HIDDEN_TEAM = "uop_hidden";
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final Set<String> COLORS = Set.of("black","dark_blue","dark_green","dark_aqua","dark_red","dark_purple","gold","gray","dark_gray","blue","green","aqua","red","light_purple","yellow","white");
    private final UopStore store;
    private final java.util.Map<UUID, Component> chatPrefixes = new ConcurrentHashMap<>();
    public NameService(UopStore store) { this.store = store; }
    private String playerPath(String name) { return "players." + name.toLowerCase(Locale.ROOT) + ".name"; }
    public void hide(String name) { store.data().set(playerPath(name)+".hidden",true); store.save(); Player p=TargetResolver.onlinePlayer(name); if(p!=null) refresh(p); }
    public void show(String name) { store.data().set(playerPath(name)+".hidden",false); store.save(); Player p=TargetResolver.onlinePlayer(name); if(p!=null) refresh(p); }
    public boolean hidden(Player p) { return store.data().getBoolean(playerPath(p.getName())+".hidden",false); }
    private Team hiddenTeam() {
        Scoreboard board=Bukkit.getScoreboardManager().getMainScoreboard(); Team team=board.getTeam(HIDDEN_TEAM);
        if(team==null) team=board.registerNewTeam(HIDDEN_TEAM);
        team.setOption(Team.Option.NAME_TAG_VISIBILITY,Team.OptionStatus.NEVER); return team;
    }
    private void removeHiddenEntry(Player p) {
        Scoreboard board=Bukkit.getScoreboardManager().getMainScoreboard(); Team team=board.getTeam(HIDDEN_TEAM);
        if(team!=null) team.removeEntry(p.getName());
        String prev=store.data().getString(playerPath(p.getName())+".previous-scoreboard-team");
        if(prev!=null) { Team old=board.getTeam(prev); if(old!=null) old.addEntry(p.getName()); store.data().set(playerPath(p.getName())+".previous-scoreboard-team",null); store.save(); }
    }
    public void refresh(Player p) {
        removeHiddenEntry(p);
        if(hidden(p)) {
            Scoreboard board=Bukkit.getScoreboardManager().getMainScoreboard(); Team existing=board.getEntryTeam(p.getName());
            if(existing!=null && !existing.getName().equals(HIDDEN_TEAM)) {
                store.data().set(playerPath(p.getName())+".previous-scoreboard-team",existing.getName()); existing.removeEntry(p.getName()); store.save();
            }
            hiddenTeam().addEntry(p.getName());
        }
        applyTag(p);
    }
    public void refreshAll() { for(Player p:Bukkit.getOnlinePlayers()) refresh(p); }
    private void applyTag(Player p) {
        String tag=tagText(p.getName()); String display=tag==null?p.getName():tag+"§r "+p.getName();
        p.setDisplayName(display); p.setCustomName(tag==null?null:display); p.setCustomNameVisible(tag!=null&&!hidden(p));
        if(tag==null) chatPrefixes.remove(p.getUniqueId()); else chatPrefixes.put(p.getUniqueId(),LEGACY.deserialize(tag+"§r "));
    }
    /** Thread-safe cached prefix for AsyncChatEvent. */
    public Component chatPrefix(Player p) { return p==null?Component.empty():chatPrefixes.getOrDefault(p.getUniqueId(),Component.empty()); }
    public Component chatName(Player p) { return chatPrefix(p).append(Component.text(p.getName())); }
    public void create(String id,String color,boolean bold,boolean italic,String text) {
        String normalized=normalizeId(id); validateId(normalized);
        if(store.data().contains("name-tags."+normalized)) throw new IllegalArgumentException("Name tag already exists: "+normalized);
        writeTag(normalized,color,bold,italic,text);
    }
    public void edit(String id,String color,boolean bold,boolean italic,String text) {
        String normalized=normalizeId(id); validateId(normalized);
        if(!store.data().contains("name-tags."+normalized)) throw new IllegalArgumentException("Name tag not found: "+normalized);
        writeTag(normalized,color,bold,italic,text); refreshAssigned(normalized);
    }
    private void writeTag(String id,String color,boolean bold,boolean italic,String text) {
        String c=normalizeColor(color), safe=normalizeText(text), value=legacyColor(c)+(bold?"§l":"")+(italic?"§o":"")+safe;
        String base="name-tags."+id; store.data().set(base+".text",value); store.data().set(base+".color",c);
        store.data().set(base+".bold",bold); store.data().set(base+".italic",italic); store.save();
    }
    public List<String> tags() { var s=store.data().getConfigurationSection("name-tags"); if(s==null)return List.of(); List<String> r=new ArrayList<>(s.getKeys(false)); r.sort(String.CASE_INSENSITIVE_ORDER); return r; }
    public void deleteTag(String id) {
        String n=normalizeId(id); validateId(n); if(!store.data().contains("name-tags."+n)) throw new IllegalArgumentException("Name tag not found: "+n);
        List<Player> affected=new ArrayList<>(); var players=store.data().getConfigurationSection("players");
        if(players!=null) for(String key:players.getKeys(false)) {
            String path="players."+key+".name.tag";
            if(n.equalsIgnoreCase(store.data().getString(path,""))) { Player p=Bukkit.getPlayerExact(key); if(p!=null) affected.add(p); store.data().set(path,null); }
        }
        store.data().set("name-tags."+n,null); store.save();
        for(Player p:affected) refresh(p);
    }
    public void give(String name,String id) {
        String n=normalizeId(id); validateId(n); if(!store.data().contains("name-tags."+n)) throw new IllegalArgumentException("Name tag not found: "+n);
        store.data().set(playerPath(name)+".tag",n); store.save(); Player p=Bukkit.getPlayerExact(name); if(p!=null) refresh(p);
    }
    public void removeTag(String name) { store.data().set(playerPath(name)+".tag",null); store.save(); Player p=Bukkit.getPlayerExact(name); if(p!=null) refresh(p); }
    public boolean hasTag(Player p) { return p!=null && chatPrefixes.containsKey(p.getUniqueId()); }
    public String tagId(String name) { return store.data().getString(playerPath(name)+".tag"); }
    private String tagText(String name) { String id=tagId(name); if(id==null||!store.data().contains("name-tags."+id))return null; String t=store.data().getString("name-tags."+id+".text"); return t==null||t.isBlank()?null:t; }
    private void refreshAssigned(String id) { for(Player p:Bukkit.getOnlinePlayers()) if(id.equalsIgnoreCase(String.valueOf(tagId(p.getName())))) refresh(p); }
    /** Restore scoreboard changes before /uop reset all clears persistent profile data. */
    public void resetRuntimeState() {
        Scoreboard board=Bukkit.getScoreboardManager().getMainScoreboard(); Team hidden=board.getTeam(HIDDEN_TEAM);
        if(hidden!=null) {
            for(String entry:new ArrayList<>(hidden.getEntries())) {
                String prev=store.data().getString("players."+entry.toLowerCase(Locale.ROOT)+".name.previous-scoreboard-team");
                if(prev!=null) { Team old=board.getTeam(prev); if(old!=null) old.addEntry(entry); }
                hidden.removeEntry(entry);
            }
            if(hidden.getEntries().isEmpty()) hidden.unregister();
        }
        var players=store.data().getConfigurationSection("players");
        if(players!=null) for(String k:players.getKeys(false)) {
            store.data().set("players."+k+".name.hidden",null); store.data().set("players."+k+".name.tag",null);
            store.data().set("players."+k+".name.previous-scoreboard-team",null);
        }
        chatPrefixes.clear();
        for(Player p:Bukkit.getOnlinePlayers()) { p.setDisplayName(p.getName()); p.setCustomName(null); p.setCustomNameVisible(false); }
    }
    /** Restore transient scoreboard ownership on disable while retaining persistent UOP settings. */
    public void shutdown() {
        Scoreboard board=Bukkit.getScoreboardManager().getMainScoreboard(); Team hidden=board.getTeam(HIDDEN_TEAM);
        if(hidden!=null) {
            for(String entry:new ArrayList<>(hidden.getEntries())) {
                String prev=store.data().getString("players."+entry.toLowerCase(Locale.ROOT)+".name.previous-scoreboard-team");
                if(prev!=null) { Team old=board.getTeam(prev); if(old!=null) old.addEntry(entry); }
                hidden.removeEntry(entry);
            }
            if(hidden.getEntries().isEmpty()) hidden.unregister();
        }
        chatPrefixes.clear();
    }
    private String normalizeId(String id) { return id==null?"":id.trim().toLowerCase(Locale.ROOT); }
    private void validateId(String id) { if(id.isBlank()||id.length()>32||!id.matches("[a-z0-9_-]+")) throw new IllegalArgumentException("Tag ID must use 1-32 letters, numbers, '_' or '-'."); }
    private String normalizeText(String text) {
        if(text==null) throw new IllegalArgumentException("Tag text is required."); String safe=ChatColor.stripColor(text).trim();
        if(safe.isBlank()) throw new IllegalArgumentException("Tag text is required."); if(safe.length()>32) throw new IllegalArgumentException("Tag text must be 32 characters or fewer."); return safe;
    }
    private String normalizeColor(String color) {
        if(color==null||color.isBlank()) throw new IllegalArgumentException("Tag color is required."); String v=color.trim().toLowerCase(Locale.ROOT);
        if(!COLORS.contains(v)&&!v.matches("#[0-9a-f]{6}")) throw new IllegalArgumentException("Color must be a Minecraft color name or #RRGGBB."); return v;
    }
    private String legacyColor(String color) {
        if(color.startsWith("#")) { StringBuilder b=new StringBuilder("§x"); for(char d:color.substring(1).toCharArray()) b.append('§').append(d); return b.toString(); }
        return switch(color) {
            case "black"->"§0"; case "dark_blue"->"§1"; case "dark_green"->"§2"; case "dark_aqua"->"§3";
            case "dark_red"->"§4"; case "dark_purple"->"§5"; case "gold"->"§6"; case "gray"->"§7"; case "dark_gray"->"§8";
            case "blue"->"§9"; case "green"->"§a"; case "aqua"->"§b"; case "red"->"§c"; case "light_purple"->"§d";
            case "yellow"->"§e"; case "white"->"§f"; default->throw new IllegalArgumentException("Unsupported tag color: "+color);
        };
    }
}