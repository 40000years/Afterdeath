import com.example.advancemagic.mana.CastAccount;
import com.example.advancemagic.effect.Geometry;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

public final class AccountingChecks {
    static int count;
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);count++;}
    public static void main(String[] args) {
        CastAccount a=new CastAccount(100);
        check(a.reserve("lightning",60,8,1000),"first cast");
        check(a.mana()==40,"exact mana cost");
        check(!a.reserve("lightning",60,8,1000),"same-tick duplicate blocked");
        check(!a.reserve("frost",50,12,1000),"insufficient mana blocked");
        check(a.mana()==40,"failures do not consume mana");
        for(int i=0;i<5;i++)a.regenerate();
        check(a.mana()==50,"two mana per regeneration step");
        check(a.reserve("frost",50,12,2000),"independent per-spell cooldown");
        a.refund("frost",50);
        check(a.mana()==50&&a.remaining("frost",2000)==0,"failed target refunds mana and cooldown");
        check(a.remaining("lightning",8999)==1,"cooldown boundary before expiry");
        check(a.remaining("lightning",9000)==0,"cooldown exact expiry");
        for(int i=0;i<100;i++)a.regenerate();
        check(a.mana()==100,"regen capped at 100");
        CastAccount restored=new CastAccount(a.mana());restored.restore("lightning",20000);
        check(restored.remaining("lightning",18000)==2000,"persisted cooldown survives reconstruction");
        check(new CastAccount(-30).mana()==0&&new CastAccount(900).mana()==100,"stored mana clamped");
        BoundingBox wall=new BoundingBox(3,0,-2,4,3,3);
        check(Geometry.intersects(wall,new Vector(0,1,0),new Vector(20,1,0)),"fast projectile cannot tunnel through wall");
        check(!Geometry.intersects(wall,new Vector(0,4,0),new Vector(20,4,0)),"projectile above wall passes");
        check(Geometry.intersects(wall,new Vector(3.5,1,0),new Vector(3.5,1,0)),"stationary projectile inside wall");
        check(!Geometry.intersects(wall,new Vector(0,1,0),new Vector(0,1,0)),"stationary projectile outside wall");
        System.out.println("PASS: "+count+" accounting and collision assertions");
    }
}
