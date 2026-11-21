package com.coolfreeze23.headinajar.staff.second;

import com.coolfreeze23.headinajar.block.TimeClockBlockEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.registry.ModFluids;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * The Time Clock's Broth tank: 4,000 mB of Nutrient Broth for the site's seconded heads, enough to fill eight canisters
 * once each. A Nutrient Broth Bucket used on the clock pours in (as does any container of broth), pipes fill it from
 * any side, and nothing takes broth out but a secondee's canister: at its punch-in, or when it comes back dry.
 */
public final class BrothTank {
    /** The tank's size, mB. */
    public static final int TANK = 4000;

    private BrothTank() {
    }

    /** A new, empty tank for a clock; {@code changed} marks the clock to be saved. */
    public static FluidTank make(Runnable changed) {
        return new FluidTank(TANK, BrothTank::isBroth) {
            @Override
            protected void onContentsChanged() {
                changed.run();
            }
        };
    }

    public static boolean isBroth(FluidStack stack) {
        return stack.getFluid().isSame(ModFluids.BROTH.source.get());
    }

    /** What pipes see: one tank that takes broth and gives nothing back. */
    public static IFluidHandler pipes(FluidTank tank) {
        return new IFluidHandler() {
            @Override
            public int getTanks() {
                return 1;
            }

            @Override
            public FluidStack getFluidInTank(int i) {
                return tank.getFluid();
            }

            @Override
            public int getTankCapacity(int i) {
                return TANK;
            }

            @Override
            public boolean isFluidValid(int i, FluidStack stack) {
                return isBroth(stack);
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                return tank.fill(resource, action);
            }

            @Override
            public FluidStack drain(FluidStack resource, FluidAction action) {
                return FluidStack.EMPTY;
            }

            @Override
            public FluidStack drain(int maxDrain, FluidAction action) {
                return FluidStack.EMPTY;
            }
        };
    }

    /**
     * A container of fluid used on the clock (server): broth pours in and the container comes back empty; a full tank
     * refuses, as does anything that is not broth. The same checks as opening the clock come first (staffing off,
     * someone the owner does not trust).
     */
    public static void pour(ServerPlayer player, InteractionHand hand, TimeClockBlockEntity clock, ItemStack held, FluidStack fluid, Direction side) {
        if (!Workforce.enabled()) {
            Refusals.deny(player, clock, "clock.disabled");
            return;
        }
        if (!clock.trusts(player)) {
            Refusals.deny(player, clock, "clock.untrusted", clock.ownerName());
            return;
        }
        if (!isBroth(fluid)) {
            Refusals.deny(player, clock, "machine.wrong_fluid", fluid.getHoverName(), clock.getDisplayName());
            return;
        }
        FluidTank tank = clock.broth();
        if (tank.fill(fluid.copy(), IFluidHandler.FluidAction.SIMULATE) < fluid.getAmount()) {
            Refusals.deny(player, clock, "clock.broth_full");
            return;
        }
        if (!FluidUtil.interactWithFluidHandler(player, hand, clock.getLevel(), clock.getBlockPos(), side)) {
            Refusals.deny(player, clock, "clock.broth_full");
            return;
        }
        clock.getLevel().playSound(null, clock.getBlockPos(), SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.8f, 0.9f);
        player.displayClientMessage(Component.translatable("message.headinajar.clock.broth_poured", tank.getFluidAmount(), TANK), true);
    }

    /**
     * Fills a secondee's canister from the tank, as far as the tank goes (at most {@link SecondeeData#CANISTER}). Returns
     * the mB moved (0: it was full, or the tank is dry).
     */
    public static int topUp(TimeClockBlockEntity clock, Mob host, SecondeeData d) {
        int want = SecondeeData.CANISTER - d.canister;
        if (want <= 0) return 0;
        FluidTank tank = clock.broth();
        int got = tank.drain(want, IFluidHandler.FluidAction.EXECUTE).getAmount();
        if (got <= 0) return 0;
        int before = Broth.step(d.canister);
        d.canister += got;
        if (Broth.step(d.canister) != before) SecondeeSync.changed(host);
        clock.getLevel().playSound(null, clock.getBlockPos(), SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.5f, 0.8f);
        return got;
    }
}
