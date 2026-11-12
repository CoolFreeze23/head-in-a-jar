package com.coolfreeze23.headinajar.gametest;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.entity.HeadlessBody;
import com.coolfreeze23.headinajar.form.Courier;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.form.JarState;
import com.coolfreeze23.headinajar.module.ModuleEffects;
import com.coolfreeze23.headinajar.module.ModuleType;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Getting a jar home: tipping over and rolling, the Wheels module, and the Courier. */
@GameTestHolder(HeadInAJar.MOD_ID)
@PrefixGameTestTemplate(false)
public class MobilityGameTests {
    private static final BlockPos FLOOR = new BlockPos(7, 2, 7);

    private static ServerPlayer jar(GameTestHelper h) {
        ServerPlayer player = h.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        Vec3 v = h.absoluteVec(Vec3.atBottomCenterOf(FLOOR));
        player.moveTo(v.x, v.y, v.z, 0, 0);
        FormActions.detach(player, 3);
        return player;
    }

    private static void done(GameTestHelper h, ServerPlayer p) {
        h.getLevel().getServer().getPlayerList().remove(p);
        h.succeed();
    }

    private static double speed(ServerPlayer p) {
        ModuleEffects.apply(p, FormActions.state(p));
        return p.getAttributeValue(Attributes.MOVEMENT_SPEED);
    }

    @GameTest(template = "platform")
    public static void bareJarTipsAndRollsSlowly(GameTestHelper h) {
        ServerPlayer p = jar(h);
        double upright = speed(p);
        h.assertTrue(FormActions.drive(p).equals("message.headinajar.tipped") && FormActions.state(p).tipped(), "did not tip over");
        double rolling = speed(p);
        h.assertTrue(rolling > 0 && rolling < upright * 0.15, "rolling speed " + rolling + " vs " + upright);
        h.assertTrue(FormActions.drive(p).equals("message.headinajar.upright") && !FormActions.state(p).tipped(), "did not sit back up");
        h.assertTrue(Math.abs(speed(p) - upright) < 1e-6, "speed not restored");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void wheelsDriveOnCharge(GameTestHelper h) {
        ServerPlayer p = jar(h);
        JarState state = FormActions.state(p);
        double bare = speed(p);
        state.setModule(0, new ItemStack(ModRegistry.module(ModuleType.WHEELS)));
        h.assertTrue(FormActions.drive(p).equals("message.headinajar.wheels_down") && state.wheels() && !state.tipped(), "wheels did not come down");
        double driving = speed(p);
        h.assertTrue(Math.abs(driving - bare * 0.5) < 1e-6, "wheel speed " + driving + " vs " + bare);
        state.setCharge(0);
        h.assertTrue(Math.abs(speed(p) - bare) < 1e-6, "flat wheels still drove");
        state.setCharge(JarState.maxCharge());
        h.assertTrue(FormActions.drive(p).equals("message.headinajar.wheels_up") && !state.wheels(), "wheels did not fold");
        FormActions.state(p).setForm(com.coolfreeze23.headinajar.form.Form.BODY);
        state.setWheels(true);
        ModuleEffects.apply(p, state);
        h.assertFalse(state.wheels(), "wheels stayed down out of jar form");
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void compactJarIsHeadSized(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        Vec3 v = h.absoluteVec(Vec3.atBottomCenterOf(FLOOR));
        p.moveTo(v.x, v.y, v.z, 0, 0);
        h.assertTrue(FormActions.separate(p, new ItemStack(ModRegistry.COMPACT_JAR.get()), new ItemStack(ModRegistry.RELAY_GOGGLES.get()),
                new ItemStack(ModRegistry.COLLAR_CAM.get()), p.position().add(1.5, 0, 0), null), "could not separate");
        JarState state = FormActions.state(p);
        h.assertTrue(state.compact() && state.slots() == 3, "not a three-slot compact jar");
        var head = h.getLevel().getEntitiesOfClass(com.coolfreeze23.headinajar.entity.HeadJarEntity.class, p.getBoundingBox().inflate(4)).stream().findFirst().orElse(null);
        h.assertTrue(head != null && head.compact() && Math.abs(head.getBbWidth() - 0.7f) < 1e-4, "the head set down is not in a compact jar");
        h.assertTrue(FormActions.linkedToJar(p) || FormActions.state(p).form() == com.coolfreeze23.headinajar.form.Form.JAR, "could not become the jar");
        p.refreshDimensions();
        h.assertTrue(Math.abs(p.getBbWidth() - 0.7f) < 1e-4, "jar hitbox " + p.getBbWidth());
        state.setCompact(false);
        p.refreshDimensions();
        h.assertTrue(Math.abs(p.getBbWidth() - 0.86f) < 1e-4, "wide jar hitbox " + p.getBbWidth());
        done(h, p);
    }

    @GameTest(template = "platform")
    public static void courierBringsTheBodyHome(GameTestHelper h) {
        ServerPlayer p = jar(h);
        // its own body: a neighbouring test may have one standing within reach
        HeadlessBody body = h.getLevel().getEntitiesOfClass(HeadlessBody.class, p.getBoundingBox().inflate(3), b -> p.getUUID().equals(b.ownerId())).stream().findFirst().orElse(null);
        h.assertTrue(body != null, "no body");
        h.assertTrue(Courier.book(p).equals("courier.headinajar.already_here"), "booked a courier for a body right here");
        // far out of reach straight up: 40 blocks sideways lands in whichever test the grid placed next door
        Vec3 far = p.position().add(0, 40, 0);
        p.teleportTo(far.x, far.y, far.z);
        // a real player keeps the chunks round it loaded; a test player is never ticked, so hold its new chunk the same way
        var chunk = new net.minecraft.world.level.ChunkPos(BlockPos.containing(far));
        // 40 blocks off may be the next test's own ground: leave its hold on that chunk as it was
        boolean held = h.getLevel().getForcedChunks().contains(chunk.toLong());
        h.getLevel().setChunkForced(chunk.x, chunk.z, true);
        h.runAfterDelay(20, () -> {
            try {
                int before = FormActions.state(p).charge();
                h.assertTrue(Courier.book(p).equals("courier.headinajar.booked"), "could not book");
                h.assertTrue(before - FormActions.state(p).charge() == JarState.maxCharge() / 5, "wrong fee");
                h.assertTrue(Courier.book(p).equals("courier.headinajar.on_its_way"), "booked twice");
                h.assertTrue(Courier.deliver(p), "not delivered");
                HeadlessBody arrived = h.getLevel().getEntitiesOfClass(HeadlessBody.class, p.getBoundingBox().inflate(4), b -> b.getUUID().equals(body.getUUID()))
                        .stream().findFirst().orElse(null);
                h.assertTrue(arrived != null && arrived.getUUID().equals(body.getUUID()), "the body is not beside the head");
                h.assertTrue(arrived.position().distanceTo(p.position()) < Courier.NEARBY, "delivered too far away");
            } finally {
                if (!held) h.getLevel().setChunkForced(chunk.x, chunk.z, false);
            }
            done(h, p);
        });
    }
}
