package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.form.Form;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.form.FormEvents;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The upper cell of a two-cell machine answers a bare jar as the machine itself does: the click is let through and
 * reaches the machine below. Clicks go through the game's own use path.
 */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class MachineTopGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);

    /** A bare jar clicks the Body Printer's upper cell: not locked out, and the click opens the Printer's screen. */
    @GameTest(template = "platform", batch = "machine_line", timeoutTicks = 60)
    public static void aJarWorksTheUpperCellOfATallMachine(GameTestHelper h) {
        h.setBlock(FLOOR, ModRegistry.BODY_PRINTER.get());
        h.runAfterDelay(3, () -> {
            BlockPos top = FLOOR.above();
            h.assertTrue(h.getBlockState(top).is(ModRegistry.MACHINE_TOP.get()), "setup: the Printer has no upper cell");
            ServerPlayer p = h.makeMockServerPlayerInLevel();
            p.setGameMode(GameType.SURVIVAL);
            Vec3 at = h.absoluteVec(Vec3.atBottomCenterOf(FLOOR.south(2)));
            p.moveTo(at.x, at.y, at.z, 180, 0);
            FormActions.detach(p, 3);
            h.assertTrue(FormActions.state(p).form() == Form.JAR && FormEvents.lockedOut(p), "setup: not a bare jar");

            BlockPos abs = h.absolutePos(top);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).add(0, 0, 0.5), Direction.SOUTH, abs, false);
            var click = new PlayerInteractEvent.RightClickBlock(p, InteractionHand.MAIN_HAND, abs, hit);
            h.assertFalse(NeoForge.EVENT_BUS.post(click).isCanceled(), "the upper cell was locked to a jar");

            boolean opened;
            try {
                p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
                opened = p.containerMenu != p.inventoryMenu;
            } catch (RuntimeException e) {
                opened = false;
                for (Throwable t = e; t != null; t = t.getCause()) {
                    if (String.valueOf(t.getMessage()).contains("may not be sent")) opened = true;
                }
                if (!opened) throw e;
            }
            h.getLevel().getServer().getPlayerList().remove(p);
            h.assertTrue(opened, "the jar's click on the upper cell did not reach the Printer");
            h.succeed();
        });
    }
}
