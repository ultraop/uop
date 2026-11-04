package com.ultraop.uop.storage;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
/** Main-thread YAML store with atomic replacement. */
public final class UopStore {
    private final JavaPlugin plugin; private final File file; private YamlConfiguration data;
    public UopStore(JavaPlugin plugin) {
        this.plugin=plugin; File folder=new File(plugin.getDataFolder(),"data");
        if(!folder.exists()&&!folder.mkdirs()) throw new IllegalStateException("Could not create UOP data directory.");
        this.file=new File(folder,"uop.yml"); reload();
    }
    public synchronized void reload() {
        YamlConfiguration loaded=new YamlConfiguration();
        if(file.exists()) {
            if(file.length()==0L) throw new IllegalStateException("UOP data file is empty. The plugin has stopped loading to protect the original file.");
            try { loaded.load(file); }
            catch(IOException|InvalidConfigurationException ex) { plugin.getLogger().severe("UOP data file could not be parsed; original file was left untouched: "+ex.getMessage()); throw new IllegalStateException("UOP data is invalid. The plugin has stopped loading to protect it.",ex); }
            if(loaded.getKeys(false).isEmpty()) throw new IllegalStateException("UOP data file contains no records. The plugin has stopped loading to protect the original file.");
        }
        Object schema=loaded.get("schema-version");
        if(schema!=null&&!(schema instanceof Number)) throw new IllegalStateException("UOP data schema-version is not numeric; original data was left untouched.");
        if(!loaded.contains("schema-version")) loaded.set("schema-version",1);
        int version=loaded.getInt("schema-version",1);
        if(version!=1) throw new IllegalStateException("Unsupported UOP data schema version "+version+"; expected version 1. Original data was left untouched.");
        data=loaded;
    }
    public synchronized YamlConfiguration data() { return data; }
    /** Creates a timestamped copy before destructive reset; throws if backup cannot be made. */
    public synchronized File backupCurrent() {
        if(!file.exists()) return null;
        File dir=new File(plugin.getDataFolder(),"backups");
        if(!dir.exists()&&!dir.mkdirs()) throw new IllegalStateException("Could not create UOP backup directory; reset was cancelled.");
        String stamp=LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
        File backup=new File(dir,"uop-"+stamp+".yml");
        int suffix=1;
        while(backup.exists()) backup=new File(dir,"uop-"+stamp+"-"+suffix++ + ".yml");
        try {
            Files.copy(file.toPath(),backup.toPath(),StandardCopyOption.COPY_ATTRIBUTES);
            // Keep the last ten recovery points; the newly created backup is never pruned.
            File[] oldBackups=dir.listFiles((folder,name)->name.startsWith("uop-")&&name.endsWith(".yml"));
            if(oldBackups!=null&&oldBackups.length>10) {
                java.util.Arrays.sort(oldBackups,java.util.Comparator.comparingLong(File::lastModified));
                for(int i=0;i<oldBackups.length-10;i++) {
                    if(!oldBackups[i].equals(backup)&&!oldBackups[i].delete()) {
                        plugin.getLogger().warning("Could not prune old UOP backup: "+oldBackups[i].getName());
                    }
                }
            }
            return backup;
        } catch(IOException ex) {
            throw new IllegalStateException("Could not back up UOP data; reset was cancelled.",ex);
        }
    }
    public synchronized void clearAll() {
        for(String key:data.getKeys(false)) if(!key.equals("schema-version")) data.set(key,null);
        save();
    }
    public synchronized void save() {
        File temp=new File(file.getParentFile(),file.getName()+".tmp");
        try {
            data.save(temp);
            try { Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
            catch(IOException atomicFailure) { Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING); }
        } catch(IOException ex) { plugin.getLogger().severe("Could not save UOP data: "+ex.getMessage()); throw new IllegalStateException("UOP data could not be saved.",ex); }
    }
}