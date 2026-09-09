package com.example.advancemagic.mana;

import java.util.HashMap;
import java.util.Map;

/** Pure accounting; milliseconds are supplied by the caller for deterministic tests. */
public final class CastAccount {
    private int mana;
    private final Map<String, Long> cooldowns = new HashMap<>();
    public CastAccount(int mana) { this.mana=Math.max(0,Math.min(100,mana)); }
    public int mana() { return mana; }
    public boolean regenerate() { if(mana>=100)return false; mana=Math.min(100,mana+2); return true; }
    public long remaining(String spell,long now) { return Math.max(0,cooldowns.getOrDefault(spell,0L)-now); }
    public boolean reserve(String spell,int cost,int seconds,long now) {
        if (cost<0 || seconds<0 || mana<cost || remaining(spell,now)>0) return false;
        mana-=cost; cooldowns.put(spell,now+seconds*1000L); return true;
    }
    public void refund(String spell,int cost) { mana=Math.min(100,mana+cost); cooldowns.remove(spell); }
    public void restore(String spell,long end) { cooldowns.put(spell,end); }
    public long end(String spell) { return cooldowns.getOrDefault(spell,0L); }
}
