package com.coolfreeze23.headinajar.genetics;

import com.coolfreeze23.headinajar.clarity.Memos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

/**
 * What every sample is, wherever it was drawn: its species' genes (none for a Nitwit's: HR has checked twice), and what
 * a draw files (DOLORES's apology to the cow, the Staff Directory's "sampled" stamp). Every sample source calls
 * {@link #drawn} once its sample exists: the Syringe and the bloodless tools, the Hemo-Syringe, a placed or station
 * head (the Living Library), the Leech Jar (no player).
 */
public final class Samples {
    /** On a Blood Sample or DNA Fraction drawn from a Nitwit: it carries no readable genes. The Centrifuge copies it. */
    public static final String NITWIT = "Nitwit";

    private Samples() {
    }

    /** Whether this sample came from a Nitwit. */
    public static boolean nitwit(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.getUnsafe().getBoolean(NITWIT);
    }

    /** The genes a sample carries: its species' genes, none for a Nitwit's or a species this world does not have. */
    public static List<Gene> genes(ItemStack stack) {
        EntityType<?> type = com.coolfreeze23.headinajar.item.GeneticsItems.species(stack);
        if (type == null || nitwit(stack)) return List.of();
        return Gene.of(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
    }

    /** Whether a creature is a Nitwit (a villager by profession). */
    public static boolean nitwit(Entity creature) {
        return creature instanceof Villager v && v.getVillagerData().getProfession() == VillagerProfession.NITWIT;
    }

    /** Whether a stored creature (a jarred head's record) was a Nitwit. */
    public static boolean nitwit(CompoundTag entity) {
        if (entity == null || !entity.contains("VillagerData")) return false;
        String job = entity.getCompound("VillagerData").getString("profession");
        return job.equals(BuiltInRegistries.VILLAGER_PROFESSION.getKey(VillagerProfession.NITWIT).toString());
    }

    /** Marks a sample as a Nitwit's (when {@code nitwit}); returns the stack. */
    public static ItemStack mark(ItemStack sample, boolean nitwit) {
        if (nitwit) CustomData.update(DataComponents.CUSTOM_DATA, sample, t -> t.putBoolean(NITWIT, true));
        return sample;
    }

    /**
     * A sample of {@code type} was drawn, by {@code by} (null for a Leech Jar). The first cow sample a player draws after
     * the update brings DOLORES's apology and the hidden advancement.
     */
    public static void drawn(ServerPlayer by, EntityType<?> type) {
        if (by == null || type == null) return;
        Directory.file(by, type, Directory.Stamp.SAMPLED);
        if (type == EntityType.COW) {
            com.coolfreeze23.headinajar.Advance.grant(by, "apology_to_the_cow");
            Memos.send(by, Memos.Memo.COW_APOLOGY);
        }
    }
}
