package com.coolfreeze23.headinajar.smoke;

import com.mojang.logging.LogUtils;
import com.coolfreeze23.headinajar.HeadInAJar;
import com.coolfreeze23.headinajar.form.FormActions;
import com.coolfreeze23.headinajar.module.ModuleType;
import com.coolfreeze23.headinajar.module.Telekinesis;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Function;

/**
 * Dev-only client check that runs by itself in a hidden window. It creates a flat creative world,
 * reports missing block and item models and names, then plays a script file:
 * a line is a server command, or "wait N", "shot NAME", "hud on|off", "view 0|1|2", "screen NAME|none", or "report TEXT".
 * a line is a server command, or "wait N", "shot NAME", "hud on|off", "view 0|1|2", "report TEXT",
 * "useblock X Y Z" (the player right-clicks the block), "closegui", "inventory", "book" (toggles the
 * open furnace screen's recipe book), "tooltip on|off" (draws the held item's tooltip over the HUD) or "hitboxes on|off".
 */
@Mod(value = HeadInAJar.MOD_ID, dist = Dist.CLIENT)
public class SmokeClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String WORLD = "smoke";

    private final Deque<String> script = new ArrayDeque<>();
    private final List<String> report = new ArrayList<>();
    private final Observer observer = new Observer(report);
    private final Harness harness = new Harness(report, observer);
    private final Frames frames = new Frames(report);
    // world directives ("@world survival", "@keep NAME", "@seed N", "@gamemode MODE", "@difficulty LEVEL", "@observe N")
    private String worldMode = "flat";
    private String keep;
    private long seed = 12345L;
    private GameType gameMode;
    private Difficulty difficulty;
    private Path dir;
    private int state;
    private int wait = 40;
    private boolean tooltip;

    public SmokeClient(net.neoforged.bus.api.IEventBus modBus) {
        if (Boolean.getBoolean("headinajar.smoke")) {
            GeckoProbe.register(modBus);
            NeoForge.EVENT_BUS.addListener(this::onTick);
            NeoForge.EVENT_BUS.addListener(this::onRenderGui);
            NeoForge.EVENT_BUS.addListener(frames::onFrame);
            NeoForge.EVENT_BUS.register(Perf.class);
            NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.tick.ServerTickEvent.Post e) -> Bystander.serverTick());
        }
    }

    private void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (tooltip && mc.player != null) {
            event.getGuiGraphics().renderTooltip(mc.font, mc.player.getMainHandItem(), 40, 40);
        }
    }

    private void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Perf.tick();
        try {
            tick(mc);
        } catch (Exception e) {
            LOGGER.error("SMOKE: failed", e);
            report.add("EXCEPTION " + e);
            finish(mc);
        }
    }

    private String burstName;
    private int burstLeft;
    private int burstFrame;

    private void tick(Minecraft mc) throws IOException {
        if (state == 2) {
            harness.tick();
            frames.tick();
            observer.tick(mc);
        }
        if (burstLeft > 0) {
            // one frame every other tick (about 10 fps of game time) while a "burst" runs
            if (mc.level != null && mc.level.getGameTime() % 2 == 0) {
                Screenshot.grab(dir.toFile(), String.format("%s_%03d.png", burstName, burstFrame++), mc.getMainRenderTarget(), m -> {
                });
                burstLeft--;
            }
            return;
        }
        if (--wait > 0) return;
        switch (state) {
            case 0 -> {
                if (!(mc.screen instanceof TitleScreen)) {
                    wait = 20;
                    return;
                }
                String out = System.getProperty("headinajar.smoke.out");
                dir = out != null ? Path.of(out) : mc.gameDirectory.toPath().resolve("smoke-out");
                Files.createDirectories(dir);
                mc.options.pauseOnLostFocus = false;
                checkModels(mc);
                Path file = Path.of(System.getProperty("headinajar.smoke.script", "smoke-script.txt"));
                if (Files.exists(file)) {
                    script.addAll(Files.readAllLines(file));
                }
                directives();
                createWorld(mc);
                state = 1;
                wait = 40;
            }
            case 1 -> {
                if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) {
                    wait = 20;
                    return;
                }
                mc.options.pauseOnLostFocus = false;
                Harness.grab();
                report.add("world " + mc.getSingleplayerServer().getWorldData().getLevelName() + ": " + worldMode
                        + ", " + mc.gameMode.getPlayerMode().getName() + ", " + mc.level.getDifficulty().getKey()
                        + ", seed " + mc.getSingleplayerServer().getWorldData().worldGenOptions().seed());
                state = 2;
                wait = 60;
            }
            case 2 -> {
                String line = null;
                while (!script.isEmpty() && (line == null || line.isBlank() || line.startsWith("#") || line.startsWith("@"))) {
                    line = script.poll();
                }
                if (line == null || line.isBlank() || line.startsWith("#") || line.startsWith("@")) {
                    finish(mc);
                    return;
                }
                run(mc, line.strip());
            }
            default -> {
            }
        }
    }

    private void run(Minecraft mc, String line) throws IOException {
        int harnessWait = harness.run(line, script);
        if (harnessWait >= 0) {
            wait = harnessWait;
            return;
        }
        String[] parts = line.split(" ", 2);
        // commands and setup verbs open an unreported observation window, so their effects are not put on the last input
        if (!java.util.Set.of("wait", "shot", "burst", "report", "riding", "clienthosts", "seatprobe", "hud", "tooltip", "hitboxes",
                "frames", "walk", "rightclick", "bystander").contains(parts[0])) {
            observer.setup(line);
        }
        switch (parts[0]) {
            case "frames" -> {
                // frames NAME TICKS: every rendered frame for TICKS ticks, into $SMOKE_FRAMES_DIR; the script carries on
                String[] args = parts[1].split(" ");
                frames.start(args[0], Integer.parseInt(args[1]));
                wait = 1;
            }
            case "bystander" -> {
                // bystander VERB ...: the stand-in second player (Bystander.java); its actions are observed like inputs
                String[] a = line.split("\\s+");
                if (java.util.Set.of("useblock", "useitem", "useentity", "attack", "sneak", "swing", "button", "key", "chair", "operate", "form", "close").contains(a[1])) {
                    observer.begin(line, 0, Bystander.position());
                } else {
                    observer.setup(line);
                }
                var server = mc.getSingleplayerServer();
                server.execute(() -> report.add(Bystander.run(server, a)));
                wait = 2;
            }
            case "wait" -> wait = Integer.parseInt(parts[1]);
            case "shot" -> {
                Screenshot.grab(dir.toFile(), parts[1] + ".png", mc.getMainRenderTarget(), m -> {
                });
                wait = 5;
            }
            case "burst" -> {
                // "burst NAME FRAMES": NAME_000.png ... at about 10 fps, for GIFs
                String[] args = parts[1].split(" ");
                burstName = args[0];
                burstLeft = Integer.parseInt(args[1]);
                burstFrame = 0;
                wait = 1;
            }
            case "walk" -> {
                if (parts[1].trim().equals("1")) observer.begin(line, 0);
                mc.options.keyUp.setDown(parts[1].trim().equals("1"));
            }
            case "hud" -> mc.options.hideGui = !parts[1].equals("on");
            case "clienthosts" -> {
                for (var e : mc.level.entitiesForRendering()) {
                    if (e instanceof net.minecraft.world.entity.Mob) report.add("client " + e.getType().toShortString() + " host=" + com.coolfreeze23.headinajar.host.Hosts.isHost(e)
                            + " model=" + (mc.getEntityRenderDispatcher().getRenderer(e) instanceof net.minecraft.client.renderer.entity.LivingEntityRenderer<?, ?> r ? r.getModel().getClass().getSimpleName() : "-"));
                }
            }
            case "perf" -> {
                // perf NAME TICKS: frame times over TICKS ticks (mean, median, p99); the script carries on
                String[] args = parts[1].split(" ");
                Perf.start(args[0], Integer.parseInt(args[1]), report::add);
            }
            case "camlog" -> {
                // camlog NAME TICKS: every frame's camera kick and field-of-view pull from the effect kit
                String[] args = parts[1].split(" ");
                Perf.camlog(args[0], Integer.parseInt(args[1]), report::add);
            }
            case "fxstate" -> report.add("fxstate " + (parts.length > 1 ? parts[1] : "") + " -> tethers " + com.coolfreeze23.headinajar.client.fx.TetherFx.live()
                    + ", loops " + com.coolfreeze23.headinajar.client.fx.SoundLoops.playing() + ", memos " + java.util.Arrays.toString(com.coolfreeze23.headinajar.client.fx.FxManager.HANDLED)
                    + ", beams drawn/asked " + com.coolfreeze23.headinajar.client.fx.Beams.counts() + ", trauma " + com.coolfreeze23.headinajar.client.fx.CameraDesk.trauma() + ", particles " + mc.particleEngine.countParticles());
            case "view" -> mc.options.setCameraType(net.minecraft.client.CameraType.values()[Integer.parseInt(parts[1])]);
            case "report" -> report.add(parts[1]);
            // seatprobe ID,ID,...: renders each body with and without its head into a recorder and reports, in blocks from its
            // feet (facing +Z): where the head was (centre x/z, lowest y) and the top of the body under a jar placed there
            case "seatprobe" -> {
                for (String id : parts[1].split(",")) report.add("seat " + id + " " + seatProbe(mc, id));
            }
            // a real right-click, through the key binding, exactly as a player's mouse does it
            case "rightclick" -> {
                observer.begin(line, 0);
                net.minecraft.client.KeyMapping.click(mc.options.keyUse.getKey());
            }
            case "riding" -> report.add("riding " + parts[1] + " -> " + (mc.player.getVehicle() == null ? "nothing"
                    : net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mc.player.getVehicle().getType()))
                    + " form=" + mc.player.getData(com.coolfreeze23.headinajar.registry.ModRegistry.JAR_STATE).form()
                    + (mc.player.getVehicle() instanceof net.minecraft.world.entity.LivingEntity v ? String.format(java.util.Locale.ROOT,
                    " jarY-hostY=%.3f seatY=%.3f height=%.2f", mc.player.getY() - v.getY(),
                    com.coolfreeze23.headinajar.host.HostControl.ridingPosition(v).y - v.getY(), mc.player.getBbHeight()) : ""));
            case "screen" -> mc.setScreen(switch (parts[1]) {
                case "pause" -> new net.minecraft.client.gui.screens.PauseScreen(true);
                default -> null;
            });
            case "tooltip" -> tooltip = parts[1].equals("on");
            case "hitboxes" -> mc.getEntityRenderDispatcher().setRenderHitBoxes(parts[1].equals("on"));
            case "closegui" -> mc.player.closeContainer();
            case "inventory" -> mc.setScreen(new InventoryScreen(mc.player));
            case "use" -> mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
            case "look" -> {
                // look YAW PITCH
                String[] a = parts[1].split(" ");
                mc.player.setYRot(Float.parseFloat(a[0]));
                mc.player.setXRot(Float.parseFloat(a[1]));
            }
            case "book" -> {
                if (mc.screen instanceof AbstractFurnaceScreen<?> furnace) {
                    furnace.recipeBookComponent.toggleVisibility();
                    furnace.resize(mc, furnace.width, furnace.height);
                }
            }
            case "jar", "jar5", "body", "grab", "throw", "fit", "charge", "sign", "swap", "separate", "pickup", "chair", "hostify", "mount", "sync", "jarhead", "mecha", "gantry", "arms", "lab", "genes", "headblock", "pilotgenes", "drive", "compact" -> {
                // jar | jar5 (take the head off) | body (put it back on the nearest own body) | fit MODULE,MODULE | charge N | grab | throw
                var server = mc.getSingleplayerServer();
                var uuid = mc.player.getUUID();
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(uuid);
                    var state = FormActions.state(player);
                    switch (parts[0]) {
                        case "jar" -> report.add("jar -> " + FormActions.detach(player, 3));
                        case "compact" -> {
                            // compact 1|0: put the head in a Compact Jar, or back in a wide one
                            state.setCompact(parts[1].equals("1"));
                            player.refreshDimensions();
                            com.coolfreeze23.headinajar.network.Network.sync(player);
                        }
                        case "jar5" -> report.add("jar5 -> " + FormActions.detach(player, 5));
                        case "body" -> {
                            var bodies = player.serverLevel().getEntitiesOfClass(com.coolfreeze23.headinajar.entity.HeadlessBody.class, player.getBoundingBox().inflate(16));
                            report.add("body -> " + (!bodies.isEmpty() && FormActions.reattach(player, bodies.get(0))));
                        }
                        case "fit" -> {
                            String[] names = parts[1].split(",");
                            for (int i = 0; i < names.length; i++) {
                                state.setModule(i, new ItemStack(ModRegistry.module(ModuleType.valueOf(names[i].trim().toUpperCase(java.util.Locale.ROOT)))));
                            }
                        }
                        case "charge" -> state.setCharge(Integer.parseInt(parts[1]));
                        case "grab" -> report.add("grab -> " + Telekinesis.grab(player, state));
                        case "throw" -> Telekinesis.throwHeld(player, state);
                        case "swap" -> report.add("swap -> " + (state.form() == com.coolfreeze23.headinajar.form.Form.LINKED
                                ? FormActions.linkedToJar(player) : FormActions.jarToLinked(player)));
                        case "separate" -> report.add("separate -> " + FormActions.separate(player, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()),
                                new ItemStack(ModRegistry.RELAY_GOGGLES.get()), new ItemStack(ModRegistry.COLLAR_CAM.get()),
                                player.position().add(player.getLookAngle().multiply(1, 0, 1).normalize().scale(1.5)), null));
                        case "pickup" -> {
                            var heads = player.serverLevel().getEntitiesOfClass(com.coolfreeze23.headinajar.entity.HeadJarEntity.class, player.getBoundingBox().inflate(8));
                            report.add("pickup -> " + (!heads.isEmpty() && FormActions.pickUpHead(player, heads.get(0))));
                        }
                        case "hostify", "jarhead" -> {
                            // hostify ENTITY X Y Z: spawn a mob there and saw it into a host; jarhead ENTITY X Y Z: saw off its head into a placed jar
                            String[] a = parts[1].split(" ");
                            var type = net.minecraft.world.entity.EntityType.byString(a[0]).orElseThrow();
                            var at = new net.minecraft.core.BlockPos(Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]));
                            var mob = (net.minecraft.world.entity.Mob) type.spawn(player.serverLevel(), at, net.minecraft.world.entity.MobSpawnType.COMMAND);
                            if (mob instanceof net.minecraft.world.entity.npc.Villager v) {
                                v.setVillagerData(v.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.LIBRARIAN));
                                v.getOffers();
                            }
                            mob.setYRot(180);
                            mob.setYBodyRot(180);
                            mob.setHealth(1);
                            if (parts[0].equals("jarhead")) player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()));
                            var result = com.coolfreeze23.headinajar.item.SurgicalSawItem.saw(player, new ItemStack(ModRegistry.SURGICAL_SAW.get()), mob,
                                    net.minecraft.world.InteractionHand.MAIN_HAND);
                            report.add(parts[0] + " " + a[0] + " -> " + result);
                            if (parts[0].equals("jarhead")) {
                                for (var stack : player.getInventory().items) {
                                    if (stack.is(ModRegistry.JARRED_HEAD.get())) {
                                        player.serverLevel().setBlockAndUpdate(at, ModRegistry.JARRED_HEAD_BLOCK.get().defaultBlockState()
                                                .setValue(com.coolfreeze23.headinajar.block.JarredHeadBlock.FACING, net.minecraft.core.Direction.NORTH));
                                        if (player.serverLevel().getBlockEntity(at) instanceof com.coolfreeze23.headinajar.block.JarredHeadBlockEntity head) {
                                            head.load(stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).copyTag());
                                            head.setChanged();
                                            player.serverLevel().sendBlockUpdated(at, head.getBlockState(), head.getBlockState(), 3);
                                        }
                                        stack.shrink(1);
                                        break;
                                    }
                                }
                            }
                        }
                        case "mecha" -> {
                            // mecha FRAME X Y Z module,module,...: a fitted mecha owned by the player, battery full
                            String[] a = parts[1].split(" ");
                            var frame = com.coolfreeze23.headinajar.mecha.MechaFrame.valueOf(a[0].toUpperCase(java.util.Locale.ROOT));
                            var m = ModRegistry.mechaType(frame).create(player.serverLevel());
                            java.util.List<ItemStack> slots = new java.util.ArrayList<>();
                            for (int i = 0; i < frame.totalSlots(); i++) slots.add(ItemStack.EMPTY);
                            for (String name : a[4].split(",")) {
                                var type = com.coolfreeze23.headinajar.mecha.MechaModule.valueOf(name.toUpperCase(java.util.Locale.ROOT));
                                int first = frame.firstIndex(type.slot);
                                for (int i = first; i < first + frame.slots(type.slot); i++) {
                                    if (slots.get(i).isEmpty()) {
                                        slots.set(i, new ItemStack(ModRegistry.mechaModule(type)));
                                        break;
                                    }
                                }
                            }
                            m.setModules(slots);
                            m.setOwner(player.getUUID());
                            m.setBattery(m.batteryCapacity());
                            m.moveTo(Double.parseDouble(a[1]) + 0.5, Double.parseDouble(a[2]), Double.parseDouble(a[3]) + 0.5, 180, 0);
                            player.serverLevel().addFreshEntity(m);
                        }
                        case "lab" -> {
                            // stocks every lab machine within 10 blocks, and fills the player's gene library
                            String key = com.coolfreeze23.headinajar.genetics.GeneArchive.key(player);
                            var archive = com.coolfreeze23.headinajar.genetics.GeneArchive.get(server);
                            archive.add(key, com.coolfreeze23.headinajar.genetics.Gene.SPIDER_CLIMB, 100);
                            archive.add(key, com.coolfreeze23.headinajar.genetics.Gene.VENOM_SAC, 45);
                            archive.add(key, com.coolfreeze23.headinajar.genetics.Gene.ECHOLOCATION, 70);
                            archive.add(key, com.coolfreeze23.headinajar.genetics.Gene.ENDER_BLINK, 100);
                            archive.add(key, com.coolfreeze23.headinajar.genetics.Gene.GILLS, 18);
                            var base = player.blockPosition();
                            for (var pos : net.minecraft.core.BlockPos.betweenClosed(base.offset(-10, -3, -10), base.offset(10, 3, 10))) {
                                var be = player.serverLevel().getBlockEntity(pos);
                                if (be instanceof com.coolfreeze23.headinajar.block.LabMachine lab) lab.energy().set(lab.energy().getMaxEnergyStored());
                                if (be instanceof com.coolfreeze23.headinajar.block.LabMachines.Centrifuge c) {
                                    c.items().setStackInSlot(0, com.coolfreeze23.headinajar.item.GeneticsItems.sample(ModRegistry.BLOOD_SAMPLE.get(),
                                            net.minecraft.world.entity.EntityType.CAVE_SPIDER, 82));
                                } else if (be instanceof com.coolfreeze23.headinajar.block.LabMachines.Sequencer q) {
                                    q.items().setStackInSlot(0, com.coolfreeze23.headinajar.item.GeneticsItems.sample(ModRegistry.DNA_FRACTION.get(),
                                            net.minecraft.world.entity.EntityType.CAVE_SPIDER, 82));
                                } else if (be instanceof com.coolfreeze23.headinajar.block.LabMachines.Splicer sp) {
                                    sp.pick(player, 0, com.coolfreeze23.headinajar.genetics.Gene.SPIDER_CLIMB);
                                    sp.pick(player, 1, com.coolfreeze23.headinajar.genetics.Gene.VENOM_SAC);
                                    sp.items().setStackInSlot(0, new ItemStack(net.minecraft.world.item.Items.AMETHYST_SHARD));
                                    sp.items().setStackInSlot(1, new ItemStack(ModRegistry.BIO_GEL.get(), 4));
                                    sp.items().setStackInSlot(2, new ItemStack(net.minecraft.world.item.Items.GLASS_BOTTLE, 4));
                                } else if (be instanceof com.coolfreeze23.headinajar.block.LabMachines.InjectionRig rig) {
                                    ItemStack cassette = new ItemStack(ModRegistry.SERUM_CASSETTE.get());
                                    new com.coolfreeze23.headinajar.genetics.Serum(java.util.List.of(new com.coolfreeze23.headinajar.genetics.Serum.Entry(
                                            com.coolfreeze23.headinajar.genetics.Gene.CAT_EYES, 1f)), 85).writeTo(cassette);
                                    rig.items().setStackInSlot(0, cassette);
                                }
                                if (be != null) be.setChanged();
                            }
                            com.coolfreeze23.headinajar.network.Network.syncArchive(player);
                            report.add("lab stocked");
                        }
                        case "genes" -> {
                            // genes TARGET GENE,GENE: TARGET is "me" or "mecha" (the one ridden); one full-strength serum per gene
                            String[] a = parts[1].split(" ");
                            net.minecraft.world.entity.LivingEntity target = a[0].equals("me") ? player : (net.minecraft.world.entity.LivingEntity) player.getVehicle();
                            var set = target.getData(ModRegistry.GENES);
                            set.clear();
                            for (String name : a[1].split(",")) {
                                set.serums.add(new com.coolfreeze23.headinajar.genetics.Serum(java.util.List.of(new com.coolfreeze23.headinajar.genetics.Serum.Entry(
                                        com.coolfreeze23.headinajar.genetics.Gene.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT)), 1f)), 100));
                            }
                            set.changed();
                            com.coolfreeze23.headinajar.network.Network.syncGenes(target);
                            report.add("genes " + a[0] + " -> " + set.strengths().keySet());
                        }
                        case "headblock" -> {
                            // headblock X Y Z ENTITY[,ENTITY...]: stock a turret, array, mount or socket with jarred heads (villagers as librarians)
                            String[] a = parts[1].split(" ");
                            var pos = new net.minecraft.core.BlockPos(Integer.parseInt(a[0]), Integer.parseInt(a[1]), Integer.parseInt(a[2]));
                            java.util.List<ItemStack> heads = new java.util.ArrayList<>();
                            for (String id : a[3].split(",")) {
                                if (id.equals("dragon_head")) {
                                    heads.add(new ItemStack(net.minecraft.world.item.Items.DRAGON_HEAD));
                                    continue;
                                }
                                var type = net.minecraft.world.entity.EntityType.byString(id).orElseThrow();
                                var mob = (net.minecraft.world.entity.Mob) type.create(player.serverLevel());
                                if (mob instanceof net.minecraft.world.entity.npc.Villager v) {
                                    v.setVillagerData(v.getVillagerData().setProfession(net.minecraft.world.entity.npc.VillagerProfession.LIBRARIAN));
                                }
                                heads.add(com.coolfreeze23.headinajar.item.JarredHeadItem.of(mob));
                            }
                            var be = player.serverLevel().getBlockEntity(pos);
                            if (be instanceof com.coolfreeze23.headinajar.block.TurretPedestalBlockEntity t) {
                                t.items().setStackInSlot(0, heads.get(0));
                                t.energy().set(40_000);
                                t.setOwner(player.getUUID());
                            } else if (be instanceof com.coolfreeze23.headinajar.block.CerebralArrayBlockEntity arr) {
                                for (int i = 0; i < heads.size() && i < 9; i++) arr.items().setStackInSlot(i, heads.get(i));
                                arr.items().setStackInSlot(9, new ItemStack(ModRegistry.BOARD_ROOM_CORE.get()));
                                arr.broth().fill(new net.neoforged.neoforge.fluids.FluidStack(com.coolfreeze23.headinajar.registry.ModFluids.BROTH.source.get(), 8000),
                                        net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                            } else if (be instanceof com.coolfreeze23.headinajar.block.EmployeeMountBlockEntity m) {
                                m.items().setStackInSlot(0, heads.get(0));
                            } else if (be instanceof com.coolfreeze23.headinajar.block.NeuralSocketBlockEntity sock) {
                                sock.items().setStackInSlot(0, heads.get(0));
                            }
                            if (be != null) {
                                be.setChanged();
                                player.serverLevel().sendBlockUpdated(pos, be.getBlockState(), be.getBlockState(), 3);
                            }
                            report.add("headblock " + a[3] + " -> " + (be == null ? "none" : be.getClass().getSimpleName()));
                        }
                        case "drive" -> report.add("drive -> " + FormActions.drive(player));
                        case "pilotgenes" -> {
                            // pilotgenes: plug the jar into the socket under it (socket verb)
                            var below = player.blockPosition().below();
                            if (player.serverLevel().getBlockEntity(below) instanceof com.coolfreeze23.headinajar.block.NeuralSocketBlockEntity sock) {
                                report.add("socket -> " + sock.plugIn(player));
                                for (var d : net.minecraft.core.Direction.values()) sock.setOutput(d, 3 + d.get3DDataValue() * 2);
                            }
                        }
                        case "gantry" -> {
                            String[] a = parts[1].split(" ");
                            var pos = new net.minecraft.core.BlockPos(Integer.parseInt(a[0]), Integer.parseInt(a[1]), Integer.parseInt(a[2]));
                            if (player.serverLevel().getBlockEntity(pos) instanceof com.coolfreeze23.headinajar.block.AssemblyGantryBlockEntity g) {
                                var frame = a.length > 3 ? com.coolfreeze23.headinajar.mecha.MechaFrame.valueOf(a[3].toUpperCase(java.util.Locale.ROOT))
                                        : com.coolfreeze23.headinajar.mecha.MechaFrame.BULWARK;
                                g.items().setStackInSlot(0, new ItemStack(ModRegistry.frameItem(frame)));
                                g.items().setStackInSlot(1, new ItemStack(ModRegistry.MECHA_PLATING.get(), 12));
                                String[] mods = {"hydraulic_legs", "jump_jets", "cargo_hold", "galvanic_stack", "armor_plating", "life_support_recycler", "drill", "hydraulic_fist", "night_optics"};
                                for (String name : mods) {
                                    var type = com.coolfreeze23.headinajar.mecha.MechaModule.valueOf(name.toUpperCase(java.util.Locale.ROOT));
                                    int first = frame.firstIndex(type.slot);
                                    for (int i = first; i < first + frame.slots(type.slot); i++) {
                                        if (g.items().getStackInSlot(2 + i).isEmpty()) {
                                            g.items().setStackInSlot(2 + i, new ItemStack(ModRegistry.mechaModule(type)));
                                            break;
                                        }
                                    }
                                }
                                g.energy().set(100_000);
                            }
                        }
                        case "arms" -> {
                            if (player.getVehicle() instanceof com.coolfreeze23.headinajar.entity.MechaEntity m) m.armsHeld = Integer.parseInt(parts[1]);
                        }
                        case "mount" -> {
                            var hosts = player.serverLevel().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, player.getBoundingBox().inflate(6),
                                    com.coolfreeze23.headinajar.host.Hosts::isHost);
                            hosts.sort(java.util.Comparator.comparingDouble(player::distanceToSqr));
                            report.add("mount -> " + (!hosts.isEmpty() && FormActions.mountHost(player, hosts.get(0))));
                        }
                        case "sync" -> {
                            if (player.getVehicle() != null) com.coolfreeze23.headinajar.host.Hosts.data(player.getVehicle()).sync = Float.parseFloat(parts[1]);
                        }
                        case "chair", "sign" -> {
                            // chair X Y Z: fill the chair at X Y Z with parts and 50 kFE; sign X Y Z: sign Form 27-B as the seated player
                            String[] xyz = parts[1].split(" ");
                            var pos = new net.minecraft.core.BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
                            if (player.serverLevel().getBlockEntity(pos) instanceof com.coolfreeze23.headinajar.block.SeparationChairBlockEntity chair) {
                                if (parts[0].equals("chair")) {
                                    chair.items().setStackInSlot(0, new ItemStack(ModRegistry.LIFE_SUPPORT_JAR.get()));
                                    chair.items().setStackInSlot(1, new ItemStack(ModRegistry.RELAY_GOGGLES.get()));
                                    chair.items().setStackInSlot(2, new ItemStack(ModRegistry.COLLAR_CAM.get()));
                                    chair.items().setStackInSlot(3, new ItemStack(ModRegistry.SALINE_BOTTLE.get(), 4));
                                    chair.energy().set(50_000);
                                } else {
                                    report.add("sign -> " + chair.sign(player));
                                }
                            }
                        }
                        default -> {
                        }
                    }
                });
                wait = 2;
            }
            case "menu" -> {
                var server = mc.getSingleplayerServer();
                var uuid = mc.player.getUUID();
                server.execute(() -> FormActions.openMenu(server.getPlayerList().getPlayer(uuid)));
                wait = 10;
            }
            case "useitem" -> {
                // the player uses the held item on the top of a block, on the server
                String[] xyz = parts[1].split(" ");
                BlockPos pos = new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
                var server = mc.getSingleplayerServer();
                var uuid = mc.player.getUUID();
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(uuid);
                    var hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false);
                    var result = player.getMainHandItem().useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
                    report.add("useitem " + parts[1] + " -> " + result);
                });
                wait = 2;
            }
            case "useblock" -> {
                String[] xyz = parts[1].split(" ");
                BlockPos pos = new BlockPos(Integer.parseInt(xyz[0]), Integer.parseInt(xyz[1]), Integer.parseInt(xyz[2]));
                var server = mc.getSingleplayerServer();
                var uuid = mc.player.getUUID();
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(uuid);
                    var level = player.serverLevel();
                    level.getBlockState(pos).useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
                });
                wait = 2;
            }
            default -> {
                var server = mc.getSingleplayerServer();
                String command = line.startsWith("/") ? line.substring(1) : line;
                var uuid = mc.player.getUUID();
                server.execute(() -> {
                    var player = server.getPlayerList().getPlayer(uuid);
                    server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(4).withSuppressedOutput(), command);
                });
                wait = 2;
            }
        }
    }

    /** Reads the "@" lines of the script (world mode, kept world, seed, game mode, difficulty, observe window). */
    private void directives() {
        for (String raw : script) {
            String line = raw.strip();
            if (!line.startsWith("@")) continue;
            String[] a = line.substring(1).split("\\s+");
            switch (a[0]) {
                case "world" -> worldMode = a[1];
                case "keep" -> keep = a[1];
                case "seed" -> seed = Long.parseLong(a[1]);
                case "gamemode" -> gameMode = GameType.byName(a[1]);
                case "difficulty" -> difficulty = Difficulty.byName(a[1]);
                case "observe" -> observer.window = Integer.parseInt(a[1]);
                default -> {
                }
            }
        }
        if (WORLD.equals(keep)) {
            report.add("@keep smoke is not allowed (the default world is deleted every run); using smoke_kept");
            keep = "smoke_kept";
        }
    }

    private void createWorld(Minecraft mc) {
        String name = keep != null ? keep : WORLD;
        if (keep != null && mc.getLevelSource().levelExists(keep)) {
            report.add("kept world " + keep + " opened (its own game mode, difficulty and seed apply)");
            mc.createWorldOpenFlows().openWorld(keep, () -> {
                report.add("EXCEPTION could not open kept world " + keep);
                finish(mc);
            });
            return;
        }
        boolean survival = worldMode.equals("survival");
        GameType type = gameMode != null ? gameMode : survival ? GameType.SURVIVAL : GameType.CREATIVE;
        Difficulty diff = difficulty != null ? difficulty : survival ? Difficulty.EASY : Difficulty.PEACEFUL;
        var settings = new LevelSettings(name, type, false, diff, true, new GameRules(), WorldDataConfiguration.DEFAULT);
        var options = new WorldOptions(seed, survival, false);
        var preset = survival ? WorldPresets.NORMAL : WorldPresets.FLAT;
        Function<net.minecraft.core.RegistryAccess, net.minecraft.world.level.levelgen.WorldDimensions> dimensions =
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(preset).value().createWorldDimensions();
        mc.createWorldOpenFlows().createFreshLevel(name, settings, options, dimensions, mc.screen);
    }

    private void checkModels(Minecraft mc) {
        BakedModel missing = mc.getModelManager().getMissingModel();
        int items = 0;
        int states = 0;
        for (var entry : BuiltInRegistries.ITEM.entrySet()) {
            String ns = entry.getKey().location().getNamespace();
            if (!ns.equals(HeadInAJar.MOD_ID)) continue;
            items++;
            Item item = entry.getValue();
            ItemStack stack = new ItemStack(item);
            if (mc.getItemRenderer().getModel(stack, null, null, 0) == missing) {
                report.add("MISSING ITEM MODEL " + entry.getKey().location());
            }
            if (!I18n.exists(stack.getDescriptionId())) {
                report.add("MISSING ITEM NAME " + entry.getKey().location() + " " + stack.getDescriptionId());
            }
        }
        for (var entry : BuiltInRegistries.BLOCK.entrySet()) {
            String ns = entry.getKey().location().getNamespace();
            if (!ns.equals(HeadInAJar.MOD_ID)) continue;
            Block block = entry.getValue();
            if (!I18n.exists(block.getDescriptionId()) && block.asItem() != net.minecraft.world.item.Items.AIR) {
                report.add("MISSING BLOCK NAME " + entry.getKey().location() + " " + block.getDescriptionId());
            }
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                states++;
                if (state.getRenderShape() != RenderShape.MODEL) continue;
                if (mc.getBlockRenderer().getBlockModel(state) == missing) {
                    report.add("MISSING BLOCK MODEL " + state);
                }
            }
        }
        report.add("checked " + items + " items and " + states + " block states");
    }

    private void finish(Minecraft mc) {
        if (state == 3) return;
        try {
            harness.releaseAll();
            observer.flush(mc);
            frames.stop();
            Frames.drain();
            var server = mc.getSingleplayerServer();
            if (server != null && Bystander.present()) server.executeBlocking(() -> Bystander.remove(server));
        } catch (RuntimeException e) {
            LOGGER.error("SMOKE: finishing", e);
            report.add("EXCEPTION while finishing " + e);
        }
        state = 3;
        try {
            Files.write(dir.resolve("report.txt"), report);
        } catch (IOException e) {
            LOGGER.error("SMOKE: report", e);
        }
        LOGGER.info("SMOKE: finished, {} report lines", report.size());
        mc.stop();
    }

    private static String seatProbe(Minecraft mc, String id) {
        var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.get(net.minecraft.resources.ResourceLocation.parse(id));
        var entity = type.create(mc.level);
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity living)) return "not living";
        entity.setPos(0, 0, 0);
        entity.setYRot(0);
        living.yBodyRot = living.yBodyRotO = 0;
        living.yHeadRot = living.yHeadRotO = 0;
        java.util.List<org.joml.Vector3f> whole = record(mc, entity);
        com.coolfreeze23.headinajar.host.Hosts.data(entity).host = true;
        java.util.List<org.joml.Vector3f> headless = record(mc, entity);
        java.util.Map<String, Integer> left = new java.util.HashMap<>();
        for (var v : headless) left.merge(key(v), 1, Integer::sum);
        java.util.List<org.joml.Vector3f> head = new java.util.ArrayList<>();
        for (var v : whole) {
            Integer n = left.get(key(v));
            if (n != null && n > 0) left.put(key(v), n - 1);
            else head.add(v);
        }
        float hx = 0, hz = 0, hMin = Float.MAX_VALUE;
        for (var v : head) {
            hx += v.x;
            hz += v.z;
            hMin = Math.min(hMin, v.y);
        }
        if (!head.isEmpty()) {
            hx /= head.size();
            hz /= head.size();
        }
        float top = -1, topNear = -1, allTop = -1;
        for (var v : headless) {
            allTop = Math.max(allTop, v.y);
            if (Math.abs(v.x - hx) < 0.3f && Math.abs(v.z - hz) < 0.3f) top = Math.max(top, v.y);
            if (Math.abs(v.x - hx) < 0.15f && Math.abs(v.z - hz) < 0.15f) topNear = Math.max(topNear, v.y);
        }
        return String.format(java.util.Locale.ROOT, "headVerts=%d headX=%.3f headZ=%.3f headMinY=%.3f bodyTop=%.3f bodyTopNear=%.3f allTop=%.3f bb=%.2fx%.2f",
                head.size(), hx, hz, head.isEmpty() ? -1 : hMin, top, topNear, allTop, entity.getBbWidth(), entity.getBbHeight());
    }

    private static String key(org.joml.Vector3f v) {
        return Math.round(v.x * 1000) + "," + Math.round(v.y * 1000) + "," + Math.round(v.z * 1000);
    }

    private static java.util.List<org.joml.Vector3f> record(Minecraft mc, net.minecraft.world.entity.Entity entity) {
        java.util.List<org.joml.Vector3f> out = new java.util.ArrayList<>();
        com.mojang.blaze3d.vertex.VertexConsumer sink = new com.mojang.blaze3d.vertex.VertexConsumer() {
            public com.mojang.blaze3d.vertex.VertexConsumer addVertex(float x, float y, float z) {
                out.add(new org.joml.Vector3f(x, y, z));
                return this;
            }
            public com.mojang.blaze3d.vertex.VertexConsumer setColor(int r, int g, int b, int a) { return this; }
            public com.mojang.blaze3d.vertex.VertexConsumer setUv(float u, float v) { return this; }
            public com.mojang.blaze3d.vertex.VertexConsumer setUv1(int u, int v) { return this; }
            public com.mojang.blaze3d.vertex.VertexConsumer setUv2(int u, int v) { return this; }
            public com.mojang.blaze3d.vertex.VertexConsumer setNormal(float x, float y, float z) { return this; }
        };
        try {
            mc.getEntityRenderDispatcher().getRenderer(entity).render(entity, 0, 0, new com.mojang.blaze3d.vertex.PoseStack(), type -> sink, 0xF000F0);
        } catch (RuntimeException e) {
            LOGGER.warn("seat probe could not render {}: {}", entity, e.toString());
        }
        return out;
    }
}
