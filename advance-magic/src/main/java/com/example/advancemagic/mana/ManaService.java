package com.example.advancemagic.mana;

import com.example.advancemagic.spell.Spell;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import java.util.*;

public final class ManaService {
    private final Plugin plugin;
    private final Map<UUID,CastAccount> accounts=new HashMap<>();
    public ManaService(Plugin plugin) { this.plugin=plugin; }
    private NamespacedKey key(String id) { return new NamespacedKey(plugin,id); }
    public CastAccount account(Player p) {
        return accounts.computeIfAbsent(p.getUniqueId(),id->{
            var data=p.getPersistentDataContainer();
            CastAccount a=new CastAccount(data.getOrDefault(key("mana"),PersistentDataType.INTEGER,100));
            for (Spell s:Spell.values()) a.restore(s.id(),data.getOrDefault(key("cd_"+s.id()),PersistentDataType.LONG,0L));
            return a;
        });
    }
    public void save(Player p) {
        CastAccount a=accounts.get(p.getUniqueId()); if(a==null)return;
        var data=p.getPersistentDataContainer();
        data.set(key("mana"),PersistentDataType.INTEGER,a.mana());
        for(Spell s:Spell.values()) data.set(key("cd_"+s.id()),PersistentDataType.LONG,a.end(s.id()));
    }
    public void quit(Player p) { save(p); accounts.remove(p.getUniqueId()); }
    public void regenerate(Player p) { if(account(p).regenerate()) save(p); }
}
