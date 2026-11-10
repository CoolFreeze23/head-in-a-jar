package com.coolfreeze23.headinajar.block;

import com.coolfreeze23.headinajar.entity.HeadlessBody;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.form.HeadRegistry;
import com.coolfreeze23.headinajar.menu.BodyPrinterMenu;
import com.coolfreeze23.headinajar.registry.ModFluids;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.UUID;

/**
 * The Body Printer: 16 rotten flesh, 8 bone blocks, 4 redstone blocks, 8 slime balls and 2000 mB Cranial Saline, then
 * 200 kFE over 60 seconds, print a fresh Headless Body (Collar Cam fitted) for the head that pressed Print.
 */
public class BodyPrinterBlockEntity extends BlockEntity implements MenuProvider, HasItems {
    public static final int[] NEED = {16, 8, 4, 8};
    public static final int SALINE = 2000;
    public static final int TICKS = 1200;
    public static final int ENERGY = 200_000;
    /** What each slot holds, in order (the recipe {@link #NEED} counts). */
    private static final net.minecraft.world.item.Item[] MATERIALS = {Items.ROTTEN_FLESH, Items.BONE_BLOCK, Items.REDSTONE_BLOCK, Items.SLIME_BALL};
    public static final MachineItems.Role[] ROLES = MachineItems.all(4, MachineItems.Role.INPUT);

    private final Buffer energy = new Buffer(250_000, 5_000, 0, this::setChanged);
    private final FluidTank saline = new FluidTank(4000, f -> f.getFluid().isSame(ModFluids.SALINE.source.get())) {
        @Override
        protected void onContentsChanged() {
            setChanged();
        }
    };
    private final ItemStackHandler items = new ItemStackHandler(4) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return switch (slot) {
                case 0 -> stack.is(Items.ROTTEN_FLESH);
                case 1 -> stack.is(Items.BONE_BLOCK);
                case 2 -> stack.is(Items.REDSTONE_BLOCK);
                case 3 -> stack.is(Items.SLIME_BALL);
                default -> false;
            };
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private int progress = -1;
    private UUID client;
    private String clientName = "";

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            return switch (i) {
                case 0 -> energy.getEnergyStored() / 10;
                case 1 -> saline.getFluidAmount();
                case 2 -> Math.max(0, progress);
                case 3 -> printing() && energy.getEnergyStored() < ENERGY / TICKS ? com.coolfreeze23.headinajar.menu.MachineMenu.NO_POWER : 0;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    public BodyPrinterBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.PRINTER_BE.get(), pos, state);
    }

    public Buffer energy() {
        return energy;
    }

    public FluidTank saline() {
        return saline;
    }

    @Override
    public ItemStackHandler items() {
        return items;
    }

    /** Mid-print, the materials are already in the vat: breaking the printer gives them back (the saline is lost with the tank). */
    @Override
    public java.util.List<ItemStack> consumed() {
        if (!printing()) return java.util.List.of();
        java.util.List<ItemStack> out = new java.util.ArrayList<>();
        for (int i = 0; i < NEED.length; i++) out.add(new ItemStack(MATERIALS[i], NEED[i]));
        return out;
    }

    public boolean printing() {
        return progress >= 0;
    }

    /** The Print button: only a decapitated player can order a body, and only with everything loaded. */
    public boolean start(ServerPlayer player) {
        var refuser = com.coolfreeze23.headinajar.feedback.Refuser.at(this);
        if (printing()) return com.coolfreeze23.headinajar.feedback.Refusals.deny(player, refuser, "printer.busy", Math.max(0, (TICKS - progress) / 20));
        if (FormActions.state(player).form() != Form.JAR) return com.coolfreeze23.headinajar.feedback.Refusals.deny(player, refuser, "printer.has_head");
        for (int i = 0; i < NEED.length; i++) {
            int short_ = NEED[i] - items.getStackInSlot(i).getCount();
            if (short_ > 0) {
                return com.coolfreeze23.headinajar.feedback.Refusals.deny(player, refuser, "printer.missing", short_, Component.translatable(MATERIALS[i].getDescriptionId()));
            }
        }
        if (saline.getFluidAmount() < SALINE) {
            return com.coolfreeze23.headinajar.feedback.Refusals.deny(player, refuser, "printer.no_saline", SALINE - saline.getFluidAmount(),
                    Component.translatable("fluid_type.headinajar.cranial_saline"));
        }
        for (int i = 0; i < NEED.length; i++) items.extractItem(i, NEED[i], false);
        saline.drain(SALINE, IFluidHandler.FluidAction.EXECUTE);
        client = player.getUUID();
        clientName = player.getGameProfile().getName();
        progress = 0;
        setChanged();
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BodyPrinterBlockEntity printer) {
        if (!printer.printing()) return;
        int perTick = ENERGY / TICKS;
        if (printer.energy.getEnergyStored() < perTick) return;
        printer.energy.set(printer.energy.getEnergyStored() - perTick);
        printer.progress++;
        if (printer.progress % 40 == 0) level.playSound(null, pos, SoundEvents.SLIME_SQUISH, SoundSource.BLOCKS, 0.6f, 0.6f);
        if (printer.progress >= TICKS) printer.print((ServerLevel) level);
    }

    private void print(ServerLevel level) {
        Direction front = getBlockState().getValue(MachineBlock.FACING);
        Vec3 at = Vec3.atBottomCenterOf(worldPosition.relative(front));
        HeadlessBody body = new HeadlessBody(ModRegistry.HEADLESS_BODY.get(), level);
        body.moveTo(at.x, at.y, at.z, front.toYRot(), 0);
        body.setYBodyRot(front.toYRot());
        body.setOwner(client, clientName);
        body.setCollar(true);
        level.addFreshEntity(body);
        HeadRegistry.get(level.getServer()).setBody(client, new HeadRegistry.Body(body.getUUID(), HeadRegistry.Spot.of(body), false));
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(client);
        if (owner != null) {
            var state = FormActions.state(owner);
            if (state.form() == Form.JAR) state.setBody(body.getUUID());
            owner.displayClientMessage(Component.translatable("message.headinajar.printed"), false);
        }
        level.playSound(null, worldPosition, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.8f, 0.5f);
        level.sendParticles(ParticleTypes.POOF, at.x, at.y + 1, at.z, 20, 0.3, 0.6, 0.3, 0.02);
        progress = -1;
        client = null;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Energy", energy.serializeNBT(registries));
        tag.put("Items", items.serializeNBT(registries));
        tag.put("Saline", saline.writeToNBT(registries, new CompoundTag()));
        tag.putInt("Progress", progress);
        if (client != null) tag.putUUID("Client", client);
        tag.putString("ClientName", clientName);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Energy")) energy.deserializeNBT(registries, tag.get("Energy"));
        MachineItems.load(items, tag.getCompound("Items"), registries);
        saline.readFromNBT(registries, tag.getCompound("Saline"));
        progress = tag.contains("Progress") ? tag.getInt("Progress") : -1;
        client = tag.hasUUID("Client") ? tag.getUUID("Client") : null;
        clientName = tag.getString("ClientName");
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.headinajar.body_printer");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new BodyPrinterMenu(id, inventory, items, data, worldPosition);
    }
}
