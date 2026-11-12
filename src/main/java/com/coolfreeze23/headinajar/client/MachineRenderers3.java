package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.block.ChargingDockBlock;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import software.bernie.geckolib.cache.object.GeoBone;

/** The third batch's renderers on the kit ({@link MachineRenderers.Base}). */
public final class MachineRenderers3 {
    private MachineRenderers3() {
    }

    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModRegistry.DOCK_BE.get(), c -> new Dock());
        event.registerBlockEntityRenderer(ModRegistry.TESLA_BE.get(), c -> new Tesla());
        event.registerBlockEntityRenderer(ModRegistry.RIG_BE.get(), c -> new Rig());
        event.registerBlockEntityRenderer(ModRegistry.SEQUENCER_BE.get(), c -> new Sequencer());
        event.registerBlockEntityRenderer(ModRegistry.ARRAY_BE.get(), c -> new Array());
        event.registerBlockEntityRenderer(ModRegistry.MOUNT_BE.get(), c -> new Mount());
    }

    /** The gene categories' colours (the Splicer's spools, the Rig's globe). */
    static final int[] CATEGORY = {0x5FA8D3, 0x8E9BA6, 0xB48AD8, 0xD9534F, 0x7CB342, 0xE8B931, 0x9C3FB0};

    /** The Rig's lit globe tinted by the loaded serum's category (on the machine's top cell, where the globe is). */
    public static void colors(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> {
            if (tint != 0) return -1;
            int serum = state.hasProperty(com.coolfreeze23.headinajar.block.InjectionRigBlock.SERUM) ? state.getValue(com.coolfreeze23.headinajar.block.InjectionRigBlock.SERUM)
                    : state.getValue(com.coolfreeze23.headinajar.machine.MachineTopBlock.SALINE);
            return serum <= 0 ? -1 : CATEGORY[Math.min(CATEGORY.length, serum) - 1];
        }, ModRegistry.INJECTION_RIG.get(), ModRegistry.MACHINE_TOP.get());
    }

    // --- Charging Dock -----------------------------------------------------------------------------------------

    static class Dock extends MachineRenderers.Base<ChargingDockBlock.Entity> {
        Dock() {
            super("charging_dock", -8, 0, -8, 8, 32, 8);
        }

        @Override
        protected boolean awake(ChargingDockBlock.Entity dock, double now) {
            return MachineAnimations3.awake(dock, now);
        }

        /** The dial's needle: the occupant's charge, E (0) to F (180) through the top. */
        @Override
        protected void pose(ChargingDockBlock.Entity dock, MachineRenderers.Model<ChargingDockBlock.Entity> model, float partial) {
            GeoBone needle = model.bone("needle");
            if (needle == null) return;
            float angle = Mth.lerp(partial, dock.needlePrev, dock.needle);
            needle.setRotZ(needle.getInitialSnapshot().getRotZ() + (float) Math.toRadians(angle));
        }
    }

    // --- Tesla Node ----------------------------------------------------------------------------------------------

    static class Tesla extends MachineRenderers.Base<com.coolfreeze23.headinajar.block.PowerBlockEntities.TeslaNode> {
        Tesla() {
            // the ring's corners pass the block by a pixel as it spins; the ball rides its rim
            super("tesla_node", -9.1, 0, -9.1, 9.1, 33.5, 9.1);
        }

        @Override
        protected boolean awake(com.coolfreeze23.headinajar.block.PowerBlockEntities.TeslaNode node, double now) {
            return MachineAnimations3.awake(node, now);
        }

        /** The big shape is a bone: drawn as far as a linked pair is seen. */
        @Override
        public int getViewDistance() {
            return 96;
        }

        @Override
        public boolean shouldRenderOffScreen(com.coolfreeze23.headinajar.block.PowerBlockEntities.TeslaNode node) {
            return true;
        }

        /**
         * The ring spins; the ball rides it but keeps its aim (its yaw is the aim less the spin), a spring toward the
         * partner, swept round on a perk and shaking its head on a refusal.
         */
        @Override
        protected void pose(com.coolfreeze23.headinajar.block.PowerBlockEntities.TeslaNode node, MachineRenderers.Model<com.coolfreeze23.headinajar.block.PowerBlockEntities.TeslaNode> model,
                            float partial) {
            GeoBone torus = model.bone("torus"), pointer = model.bone("pointer");
            if (torus == null || pointer == null) return;
            float spin = Mth.lerp(partial, node.spinPrev, node.spin);
            float aim = Mth.rotLerp(partial, node.aimPrev, node.aim) - node.turn();
            double now = MachineAnimations.now(node);
            double perk = node.kit().since(com.coolfreeze23.headinajar.machine.MachineKit.EV_OWN, now);
            if (perk >= 6 && perk < 32) aim += (float) (100 * Math.sin(2 * Math.PI * (perk - 6) / 26));
            double r = node.kit().since(com.coolfreeze23.headinajar.machine.MachineKit.EV_REFUSE, now);
            if (r >= 0 && r < 12) aim += (float) (40 * Math.sin(2 * Math.PI * r / 6) * (1 - r / 12));
            torus.setRotY(torus.getInitialSnapshot().getRotY() + (float) Math.toRadians(spin));
            pointer.setRotY(pointer.getInitialSnapshot().getRotY() + (float) Math.toRadians(aim - spin));
            // each pulse of a send kicks the ring (and the ball on it) up a pixel
            if (pulse(node, partial) > 0) torus.setPosY(torus.getPosY() + 1);
            node.torusY = torus.getPosY();
        }

        /** Ticks of a pulse ({@value #PULSE} long, one a second, on the server's arc tick) while this node sends or is sent to; 0 none. */
        private static final int PULSE = 4;

        static float pulse(com.coolfreeze23.headinajar.block.PowerBlockEntities.TeslaNode node, float partial) {
            var level = node.getLevel();
            if (level == null || com.coolfreeze23.headinajar.client.fx.Comfort.photosensitive()) return 0;
            boolean live = node.sending();
            if (!live && node.source() != null && level.getBlockEntity(node.source()) instanceof com.coolfreeze23.headinajar.block.PowerBlockEntities.TeslaNode s) live = s.sending();
            if (!live) return 0;
            float t = (level.getGameTime() % 20) + partial;
            return t < PULSE ? PULSE - t : 0;
        }

        /** The send made to read from ten blocks: the ball lit on each pulse (the arc between the balls is the effect kit's, {@code BlockBeams}). */
        @Override
        public void render(com.coolfreeze23.headinajar.block.PowerBlockEntities.TeslaNode node, float partial, com.mojang.blaze3d.vertex.PoseStack pose,
                           net.minecraft.client.renderer.MultiBufferSource buffers, int light, int overlay) {
            super.render(node, partial, pose, buffers, light, overlay);
            float p = pulse(node, partial);
            if (p <= 0 || MachineRenderers.shadowPass() || (MachineRenderers.debugOff & 2) != 0) return;
            float k = p / PULSE;
            var buffer = buffers.getBuffer(net.minecraft.client.renderer.RenderType.lightning());
            var base = node.getBlockPos();
            net.minecraft.world.phys.Vec3 from = node.arcStart(partial).subtract(base.getX(), base.getY(), base.getZ());
            // the ball: a glow just round it (7 x 7 x 7 px about its centre, 2 px in from the arc's start)
            float theta = (float) Math.toRadians(Mth.rotLerp(partial, node.aimPrev, node.aim));
            double cx = 0.5 + 5 / 16.0 * Math.sin(theta), cz = 0.5 + 5 / 16.0 * Math.cos(theta);
            pose.pushPose();
            pose.translate(cx, from.y, cz);
            pose.mulPose(com.mojang.math.Axis.YP.rotation(theta));
            glowBox(buffer, pose.last().pose(), 3.6f / 16, 0.55f, 0.75f, 1f, 0.55f * k);
            pose.popPose();
        }

        private static void glowBox(com.mojang.blaze3d.vertex.VertexConsumer b, org.joml.Matrix4f m, float h, float red, float green, float blue, float alpha) {
            float y0 = -h, y1 = h;
            var v = new net.minecraft.world.phys.Vec3[8];
            for (int i = 0; i < 8; i++) v[i] = new net.minecraft.world.phys.Vec3((i & 1) == 0 ? -h : h, (i & 2) == 0 ? y0 : y1, (i & 4) == 0 ? -h : h);
            int[][] faces = {{0, 1, 3, 2}, {4, 6, 7, 5}, {0, 2, 6, 4}, {1, 5, 7, 3}, {0, 4, 5, 1}, {2, 3, 7, 6}};
            for (int[] f : faces) quad(b, m, v[f[0]], v[f[1]], v[f[2]], v[f[3]], red, green, blue, alpha);
        }

        private static void quad(com.mojang.blaze3d.vertex.VertexConsumer b, org.joml.Matrix4f m, net.minecraft.world.phys.Vec3 p0, net.minecraft.world.phys.Vec3 p1,
                                 net.minecraft.world.phys.Vec3 p2, net.minecraft.world.phys.Vec3 p3, float red, float green, float blue, float alpha) {
            for (var p : new net.minecraft.world.phys.Vec3[]{p0, p1, p2, p3, p3, p2, p1, p0}) {
                b.addVertex(m, (float) p.x, (float) p.y, (float) p.z).setColor(red, green, blue, alpha);
            }
        }
    }

    // --- Injection Rig -------------------------------------------------------------------------------------------

    static class Rig extends MachineRenderers.Base<com.coolfreeze23.headinajar.block.LabMachines.InjectionRig> {
        private static final double RECOIL = 18, SHUDDER = 8;

        Rig() {
            // the arm swings the syringe round the globe, the needle reaching 14 px past the front
            super("injection_rig", -23, 0, -23, 23, 35, 23);
        }

        @Override
        protected boolean awake(com.coolfreeze23.headinajar.block.LabMachines.InjectionRig rig, double now) {
            return MachineAnimations3.awake(rig, now);
        }

        /** The cassette on its ledge while loaded. */
        @Override
        protected void contents(com.coolfreeze23.headinajar.block.LabMachines.InjectionRig rig, com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light, float partial) {
            if (rig.loaded()) item(pose, buffers, light, rig, new net.minecraft.world.item.ItemStack(ModRegistry.SERUM_CASSETTE.get()), 2.5, 5, -5.5, 0.25f, 0, 0);
        }

        /** The arm's swing (code): out to the patient's bearing, the result's answer, home; a cancel eases it home. */
        @Override
        protected void pose(com.coolfreeze23.headinajar.block.LabMachines.InjectionRig rig, MachineRenderers.Model<com.coolfreeze23.headinajar.block.LabMachines.InjectionRig> model, float partial) {
            GeoBone arm = model.bone("nozzle_arm");
            if (arm == null) return;
            double now = MachineAnimations.now(rig);
            double t = MachineAnimations3.shot(rig, now);
            double yaw = 0;
            if (t >= 0 && t < 47) yaw = armYaw(t, aim(rig), MachineAnimations3.cancelledAt(rig, now), MachineAnimations3.result(rig, now));
            // Blockbench's sense (+ swings the syringe from the holster toward the front); the geometry's y is negated
            arm.setRotY(arm.getInitialSnapshot().getRotY() - (float) Math.toRadians(yaw));
        }

        /** The patient's bearing in the machine's frame: 0 the holster side (+X), 90 straight out of the front, 180 its left; 30-180. */
        private static double aim(com.coolfreeze23.headinajar.block.LabMachines.InjectionRig rig) {
            var level = rig.getLevel();
            net.minecraft.world.entity.Entity patient = level == null || rig.patientId() < 0 ? null : level.getEntity(rig.patientId());
            if (patient == null) return 90;
            var c = rig.getBlockPos().getCenter();
            var d = new net.minecraft.world.phys.Vec3(patient.getX() - c.x, 0, patient.getZ() - c.z);
            // into the machine's own frame (the inverse of its turn)
            float turn = switch (MachineRenderers.Base.facing(rig)) {
                case SOUTH -> 180;
                case WEST -> 90;
                case EAST -> 270;
                default -> 0;
            };
            d = d.yRot((float) -Math.toRadians(turn));
            double a = Math.toDegrees(Math.atan2(-d.z, d.x));
            if (a < -90) a += 360;
            return Math.max(30, Math.min(180, a));
        }

        private static double eoQuad(double x) {
            x = Mth.clamp(x, 0, 1);
            return 1 - (1 - x) * (1 - x);
        }

        private static double eioSine(double x) {
            x = Mth.clamp(x, 0, 1);
            return -(Math.cos(Math.PI * x) - 1) / 2;
        }

        /** The model's schedule (rig_anim.arm_yaw): degrees at shot tick t for bearing phi. */
        static double armYaw(double t, double phi, double tc, int result) {
            if (!Double.isNaN(tc) && t >= tc) {
                double y0 = armYaw(tc, phi, Double.NaN, 0);
                return y0 * (1 - eioSine((t - tc - 2) / 8.0));
            }
            if (t < 7) return 0;
            if (t < 9) return -0.08 * phi * eoQuad((t - 7) / 2.0);
            if (t < 18) return -0.08 * phi + 1.12 * phi * eioSine((t - 9) / 9.0);
            if (t < 21) return 1.04 * phi - 0.04 * phi * eioSine((t - 18) / 3.0);
            if (t < 30) return phi;
            if (t < 34) {
                if (result == 1) return phi - RECOIL * (t < 32 ? eoQuad((t - 30) / 2.0) : 1);
                if (result == 2) return phi + SHUDDER * Math.sin(Math.PI * (t - 30) * 1.5) * (1 - (t - 30) / 4.0);
                return phi;
            }
            double start = result == 1 ? phi - RECOIL : phi;
            if (t < 41) return start - (start + 0.03 * phi) * eioSine((t - 34) / 7.0);
            if (t < 43) return -0.03 * phi * (1 - eioSine((t - 41) / 2.0));
            return 0;
        }
    }

    // --- DNA Sequencer -----------------------------------------------------------------------------------------

    static class Sequencer extends MachineRenderers.Base<com.coolfreeze23.headinajar.block.LabMachines.Sequencer> {
        /** The keys' colours, A C G T (the call plate takes the struck base's). */
        private static final int[] BASE_COLOUR = {0x6FBF4A, 0x3F9BE4, 0xF0A030, 0xE05050};

        Sequencer() {
            super("dna_sequencer", -8, 0, -9, 8, 35, 8);
        }

        @Override
        protected boolean awake(com.coolfreeze23.headinajar.block.LabMachines.Sequencer s, double now) {
            return MachineAnimations3.awake(s, now);
        }

        /** The DNA Fraction standing in the reader on top while loaded. */
        @Override
        protected void contents(com.coolfreeze23.headinajar.block.LabMachines.Sequencer s, com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light, float partial) {
            if (s.loaded()) item(pose, buffers, light, s, new net.minecraft.world.item.ItemStack(ModRegistry.DNA_FRACTION.get()), 2.5, 28, 2.5, 0.25f, 0, 0);
        }

        /** The receipt's length: a pixel per two calls rung, fed while the crank turns; gone once torn. */
        @Override
        protected void pose(com.coolfreeze23.headinajar.block.LabMachines.Sequencer s, MachineRenderers.Model<com.coolfreeze23.headinajar.block.LabMachines.Sequencer> model, float partial) {
            GeoBone receipt = model.bone("receipt");
            if (receipt == null) return;
            double now = MachineAnimations.now(s);
            int count = 0;
            double since = 99;
            double t = MachineAnimations3.tally(s, now);
            double end = MachineAnimations3.ending(s, now);
            if (end >= 6 + 2) {
                count = 0;
            } else if (t >= 0) {
                int slots = (int) Math.min(12, Math.floor(t / 7) + 1);
                int mask = s.kit().eventParam(com.coolfreeze23.headinajar.block.LabMachines.Sequencer.EV_TALLY);
                count = Integer.bitCount(mask & ((1 << slots) - 1));
                since = t - 7 * (slots - 1);
            } else if (s.kit().running() && s.beat >= 0) {
                count = s.beat + 1;
                since = now - s.beatAt;
            }
            receipt.setPosY(receipt.getInitialSnapshot().getOffsetY() + (float) receiptLength(count, since));
        }

        private static double receiptLength(int count, double since) {
            if (count <= 0) return 0;
            int base = (count - 1) / 2;
            if (count % 2 == 1) return base;
            double f = Mth.clamp((since - 3) / 5.0, 0, 1);
            return base + -(Math.cos(Math.PI * f) - 1) / 2;
        }

        /** The call flag: the plate tinted by the struck base; in a tally the banner shows for a wrong call. */
        @Override
        protected int hiddenCubes(com.coolfreeze23.headinajar.block.LabMachines.Sequencer s, GeoBone bone) {
            if (!bone.getName().equals("flag")) return 0;
            double t = MachineAnimations3.tally(s, MachineAnimations.now(s));
            if (t >= 0 && t < 96) {
                int slot = (int) Math.min(11, Math.floor(t / 7));
                boolean right = (s.kit().eventParam(com.coolfreeze23.headinajar.block.LabMachines.Sequencer.EV_TALLY) & (1 << slot)) != 0;
                return right ? 0b10 : 0b01;
            }
            return 0b10;
        }

        @Override
        protected int cubeTint(com.coolfreeze23.headinajar.block.LabMachines.Sequencer s, GeoBone bone) {
            if (!bone.getName().equals("flag")) return -1;
            double now = MachineAnimations.now(s);
            double t = MachineAnimations3.tally(s, now);
            int slot = t >= 0 ? (int) Math.min(11, Math.floor(t / 7)) : Math.max(0, s.beat);
            int base = s.shownBases()[slot] & 3;
            // a key struck in the screen: the plate takes the struck base
            if (t < 0 && MachineAnimations3.keyBeat(s, now) >= 0) base = s.kit().eventParam(com.coolfreeze23.headinajar.block.LabMachines.Sequencer.EV_BEAT) & 3;
            return BASE_COLOUR[base] & 0xFFFFFF;
        }
    }

    // --- Cerebral Array ---------------------------------------------------------------------------------------

    static class Array extends MachineRenderers.Base<com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity> {
        private static final int AYE = 0x5DBB4A, NAY = 0xD9534F, BUFF = 0xE8DCC0;
        /** The seats (px): x across from the viewer's left, the three raked rows front first. */
        private static final double[] SEAT_X = {6, 0, -6}, ROW_Y = {4.25, 12.25, 20.25}, ROW_Z = {-5, -1, 3};

        Array() {
            super("cerebral_array", -9, 0, -9, 9, 37, 9);
        }

        @Override
        protected boolean awake(com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity a, double now) {
            return MachineAnimations3.awake(a, now);
        }

        /**
         * While the board sits (fed, with a head in a seat) the gavel lifts and settles every 40 to 80 ticks, on the
         * machine's own clock (two Arrays are never in step); a vote or a refusal plays its clip instead.
         */
        @Override
        protected void pose(com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity a, MachineRenderers.Model<com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity> model, float partial) {
            GeoBone gavel = model.bone("gavel");
            if (gavel == null || a.getLevel() == null) return;
            double lift = MachineAnimations3.gavelLift(a, a.getLevel().getGameTime() + partial);
            if (lift >= 0) gavel.setRotZ(gavel.getInitialSnapshot().getRotZ() - (float) Math.toRadians(40 * lift));
        }

        /** Which seats vote aye: the ayes shuffled over the occupied seats, the same for every player (seeded by the vote). */
        private static boolean aye(com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity a, int seat) {
            java.util.List<Integer> occupied = new java.util.ArrayList<>();
            for (int i = 0; i < com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity.HEAD_SLOTS; i++) if (!a.items().getStackInSlot(i).isEmpty()) occupied.add(i);
            double at = MachineAnimations.now(a) - MachineAnimations3.vote(a, MachineAnimations.now(a));
            java.util.Collections.shuffle(occupied, new java.util.Random((long) at * 31 + a.getBlockPos().asLong()));
            int index = occupied.indexOf(seat);
            return index >= 0 && index < MachineAnimations3.ayes(a);
        }

        private static int seat(GeoBone bone) {
            String name = bone.getName();
            return name.startsWith("card_") ? name.charAt(5) - '1' : -1;
        }

        /** An empty seat's card is not there. */
        @Override
        protected int hiddenCubes(com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity a, GeoBone bone) {
            int seat = seat(bone);
            return seat >= 0 && a.items().getStackInSlot(seat).isEmpty() ? ~0 : 0;
        }

        /** A card shows its vote from its rise until it is down again; buff otherwise. */
        @Override
        protected int cubeTint(com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity a, GeoBone bone) {
            int seat = seat(bone);
            if (seat < 0) return -1;
            double t = MachineAnimations3.vote(a, MachineAnimations.now(a));
            if (t >= 14 + 3 * seat && t < 56) return aye(a, seat) ? AYE : NAY;
            return BUFF;
        }

        /** The heads in their specimen jars: each mob's face at the jar's own pixel size (or, for comparison, its head shrunk). */
        @Override
        protected void contents(com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity a, com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light, float partial) {
            float age = a.getLevel() == null ? 0 : a.getLevel().getGameTime() + partial;
            for (int i = 0; i < com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity.HEAD_SLOTS; i++) {
                var stack = a.items().getStackInSlot(i);
                if (stack.isEmpty()) continue;
                double x = SEAT_X[i % 3], y = ROW_Y[i / 3], z = ROW_Z[i / 3];
                if (stack.is(net.minecraft.world.item.Items.DRAGON_HEAD)) {
                    item(pose, buffers, light, a, stack, x, y - 1.5, z, 0.18f, 0, 0);
                    continue;
                }
                var info = a.headInfo(i);
                if (info.type() == null) continue;
                var head = JarredHeadRenderer.head(info.type(), info.villagerData());
                if (head == null) continue;
                // fed, they bob a little; unfed, they sag
                float bob = (float) Math.sin((age + i * 17) * 0.08) * 0.15f;
                float sag = a.fed() ? 0 : 10;
                if ((MachineRenderers.debugOff & 128) != 0) shrunk(pose, buffers, light, head, x, y + bob, z, sag);
                else face(pose, buffers, light, head, x, y + bob, z, sag, a.fed() ? alive(a, i, age) : HeadFaces.OPEN);
            }
        }

        /**
         * A fed head between votes: a blink (3 ticks) every 3 to 7 seconds and, one cycle in four, a glance aside for a
         * second; each seat on its own clock.
         */
        private static int alive(com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity a, int seat, float age) {
            long seed = a.getBlockPos().asLong() * 31 + seat * 7919L;
            int period = 60 + (int) Math.floorMod(seed, 81L);
            long tick = (long) age + Math.floorMod(seed, 1000L);
            int t = (int) Math.floorMod(tick, (long) period);
            if (t < 3) return HeadFaces.SHUT;
            long cycle = Math.floorDiv(tick, (long) period);
            if (Math.floorMod(cycle * 13 + seat, 4L) == 0 && t >= period / 2 && t < period / 2 + 20) return HeadFaces.ASIDE;
            return HeadFaces.OPEN;
        }

        /** The face, three texels across, a flat quad just inside the glass's front. */
        private static void face(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light,
                                 JarredHeadRenderer.Head head, double x, double y, double z, float sag, int variant) {
            var texture = HeadFaces.face(head.part(), head.texture(), variant);
            if (texture == null) return;
            var v = buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(texture));
            pose.pushPose();
            pose.translate(x / 16, (y - 1.5) / 16, (z - 1.5) / 16);
            pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(sag));
            var m = pose.last();
            float h = HeadFaces.SIZE / 32f;
            // seen from the front (-Z): the texture's left at the viewer's left (+X)
            faceVertex(v, m, h, 2 * h, 0, 0, light);
            faceVertex(v, m, h, 0, 0, 1, light);
            faceVertex(v, m, -h, 0, 1, 1, light);
            faceVertex(v, m, -h, 2 * h, 1, 0, light);
            pose.popPose();
        }

        private static void faceVertex(com.mojang.blaze3d.vertex.VertexConsumer v, com.mojang.blaze3d.vertex.PoseStack.Pose m, float x, float y, float u, float vv, int light) {
            v.addVertex(m, x, y, 0).setColor(255, 255, 255, 255).setUv(u, vv).setOverlay(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                    .setLight(light).setNormal(m, 0, 0, -1);
        }

        /** As modelled: the mob's head part itself, scaled to about 2.8 px across. */
        private static void shrunk(com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light,
                                   JarredHeadRenderer.Head head, double x, double y, double z, float sag) {
            var part = head.part();
            var saved = part.storePose();
            part.setPos(0, 0, 0);
            part.setRotation((float) Math.toRadians(sag), 0, 0);
            pose.pushPose();
            pose.translate(x / 16, (y + 0.8) / 16, z / 16);
            pose.scale(-0.35f, -0.35f, 0.35f);
            part.render(pose, buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(head.texture())), light,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            pose.popPose();
            part.loadPose(saved);
        }
    }

    // --- Employee Mount ---------------------------------------------------------------------------------------

    static class Mount extends MachineRenderers.Base<com.coolfreeze23.headinajar.block.EmployeeMountBlockEntity> {
        Mount() {
            super("employee_mount", -9, 0, -9, 10, 20, 8);
        }

        @Override
        protected boolean awake(com.coolfreeze23.headinajar.block.EmployeeMountBlockEntity m, double now) {
            return MachineAnimations3.awake(m, now);
        }

        /** The villager's head in its jar (the head part alone, 7/16), the day's emerald on the tray while paid. */
        @Override
        protected void contents(com.coolfreeze23.headinajar.block.EmployeeMountBlockEntity m, com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light, float partial) {
            if (m.head().isEmpty() || m.getLevel() == null) return;
            if (m.paid()) item(pose, buffers, light, m, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.EMERALD), -5, 7, -5.5, 0.25f, 0, 0);
            var info = m.headInfo();
            if (info.type() == null) return;
            var head = JarredHeadRenderer.head(info.type(), info.villagerData());
            if (head == null) return;
            float age = m.getLevel().getGameTime() + partial;
            boolean paid = m.paid();
            long missed = m.daysMissed();
            // paid: a small glance now and then; unpaid: head down; three days or more: turned to the wall
            float yaw = missed >= 3 && !paid ? 150 : (float) (8 * Math.sin(age * 0.02 + m.getBlockPos().asLong()));
            float pitch = paid ? 0 : 10;
            var part = head.part();
            var saved = part.storePose();
            part.setPos(0, 0, 0);
            part.setRotation((float) Math.toRadians(pitch), (float) Math.toRadians(yaw), 0);
            pose.pushPose();
            // the neck on the jar's foot (9.5 px); the head part grows up from its pivot
            pose.translate(0, 9.5 / 16.0, 0);
            pose.scale(-7 / 16f, -7 / 16f, 7 / 16f);
            part.render(pose, buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(head.texture())), light,
                    net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            pose.popPose();
            part.loadPose(saved);
        }
    }
}
