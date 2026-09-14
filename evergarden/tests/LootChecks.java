import com.example.voidscape.item.VaultLootTable;
import java.util.Arrays;

public final class LootChecks {
    public static void main(String[] args) {
        int[] counts=new int[6];
        for(int ticket=0;ticket<10000;ticket++)counts[VaultLootTable.reward(ticket).ordinal()]++;
        if(!Arrays.equals(counts,new int[]{3500,3500,1000,1500,490,10}))
            throw new AssertionError("Incorrect reward odds: "+Arrays.toString(counts));
        for(int invalid:new int[]{-1,10000}) {
            try {VaultLootTable.reward(invalid);throw new AssertionError("Invalid ticket accepted");}
            catch(IllegalArgumentException expected) {}
        }
        System.out.println("PASS: all 10,000 vault tickets; 5% cores including 0.1% mythic; invalid tickets rejected");
    }
}
