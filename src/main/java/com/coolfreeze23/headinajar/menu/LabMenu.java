package com.coolfreeze23.headinajar.menu;

import com.coolfreeze23.headinajar.block.LabMachine;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.Locale;

/** The menu for every genetics machine; the kind says where its slots go and how many numbers it syncs. */
public class LabMenu extends MachineMenu {
    public enum Kind {
        /** in: blood sample; out: DNA fraction, plasma vial. Data: energy, progress */
        CENTRIFUGE(new int[][]{{44, 35}, {116, 25}, {116, 47}}, 2),
        /** in: DNA fraction. Data: energy, progress, seed, auto running */
        DNA_SEQUENCER(new int[][]{{26, 35}}, 4),
        /** catalyst, bio-gel, bottle, out. Data: energy, progress, gene 1-3 (+1), stability, mutation %, load */
        GENE_SPLICER(new int[][]{{26, 60}, {48, 60}, {70, 60}, {146, 60}}, 8),
        /** serum. Data: energy */
        INJECTION_RIG(new int[][]{{80, 30}}, 2),
        /** nine heads and the Board Room Core. Data: energy, output FE/t, broth mB, heads */
        CEREBRAL_ARRAY(new int[][]{{44, 17}, {62, 17}, {80, 17}, {44, 35}, {62, 35}, {80, 35}, {44, 53}, {62, 53}, {80, 53}, {124, 53}}, 4);

        public final int[][] slots;
        public final int data;

        Kind(int[][] slots, int data) {
            this.slots = slots;
            this.data = data;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public MenuType<LabMenu> type() {
            return ModRegistry.LAB_MENUS.get(this).get();
        }
    }

    public final Kind kind;

    public LabMenu(Kind kind, int id, Inventory inventory, RegistryFriendlyByteBuf buf) {
        this(kind, id, inventory, new ItemStackHandler(kind.slots.length), new SimpleContainerData(kind.data + 1), buf.readBlockPos());
    }

    public LabMenu(Kind kind, int id, Inventory inventory, IItemHandler items, ContainerData data, BlockPos pos) {
        super(kind.type(), id, inventory, items, data, pos, kind.slots);
        this.kind = kind;
    }

    /** After the kind's own numbers comes the stall reason. */
    @Override
    public int statusIndex() {
        return kind.data;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        return player instanceof ServerPlayer server && player.level().getBlockEntity(pos()) instanceof LabMachine lab && lab.button(server, id);
    }
}
