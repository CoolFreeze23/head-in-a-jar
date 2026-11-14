package com.coolfreeze23.headinajar.mecha;

import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.feedback.Refuser;
import com.coolfreeze23.headinajar.genetics.BloodType;
import com.coolfreeze23.headinajar.genetics.Gene;
import com.coolfreeze23.headinajar.genetics.GeneSet;
import com.coolfreeze23.headinajar.genetics.Genetics;
import com.coolfreeze23.headinajar.registry.ModRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;

import java.util.List;

/**
 * The Chimera's own: the Graft on the drive key. Its arms remember the last creature they hit, and which arm hit it,
 * for five seconds; a press in that window plays {@code graft_l} or {@code graft_r} (the arm comes up to its face, it
 * sniffs, gulps and swells), and at the clip's {@code graft:take} (tick 14) the creature's gene is taken as a
 * temporary Gene Port for sixty seconds: one Bio-Gel is spent from the hold or the pilot's pockets (it runs in the
 * veins, healing as Bio-Gel always has), the gene is named, and the veins run in the creature's blood colour for the
 * minute. A press with nothing tasted, a Legendary gene, no Bio-Gel or a graft still taking is refused: the
 * {@code graft_refused} recoil and a line. The three ordinary Gene Ports are untouched (never rejected, as ever); the
 * grafted gene is not one of them and is never saved.
 */
public final class Chimera {
    /** The Graft's moment kinds (the low bits of the synced verb time): by the left arm, by the right, refused. */
    public static final int GRAFT_L = 0, GRAFT_R = 1, REFUSED = 2;
    public static final int GRAFT_TICKS = 40, TAKE = 14, REFUSED_TICKS = 26;
    /** How long the arms remember a taste, and how long a grafted gene lasts (ticks). */
    public static final int TASTE = 100, LASTS = 1200;
    /**
     * FE a Graft costs at the press: about 1% of the Chimera's 200 kFE battery (a full-strength use of the frame's verb),
     * and its own idle drain (3 FE a tick) while the grafted gene lasts.
     */
    public static final int GRAFT_FE = 2_000, HOLD_FE = 3;
    /** The arm whose tick is running now (-1 between arm ticks): what a blow dealt now was dealt with. */
    static final String ARM_TAG = "headinajar_arm";

    private Chimera() {
    }

    public static boolean grafts(MechaEntity m) {
        MechaRig rig = MechaRig.of(m.frame());
        return rig != null && rig.verb == MechaRig.Verb.GRAFT;
    }

    static int kind(MechaEntity m) {
        return (int) (m.verbAt() & 7);
    }

    /** Ticks since the last Graft moment, or -1 for none. */
    public static double since(MechaEntity m, double now) {
        long at = m.verbAt();
        if (!grafts(m) || at < 0) return -1;
        double t = now - at / 8;
        return t < 0 ? -1 : t;
    }

    /** The Graft's clip (either arm's, or the refusal) at {@code now}, or null when none is playing. */
    static HopperAnimation.Choice clip(MechaEntity m, MechaRig rig, double now) {
        double t = since(m, now);
        if (t < 0) return null;
        return switch (kind(m)) {
            case GRAFT_L -> t < GRAFT_TICKS ? new HopperAnimation.Choice(rig.clip("graft_l"), t, 3) : null;
            case GRAFT_R -> t < GRAFT_TICKS ? new HopperAnimation.Choice(rig.clip("graft_r"), t, 3) : null;
            case REFUSED -> t < REFUSED_TICKS ? new HopperAnimation.Choice(rig.clip("graft_refused"), t, 2) : null;
            default -> null;
        };
    }

    /** It stands still while it tastes and gulps (the forearm is at its face). */
    public static boolean rooted(MechaEntity m) {
        double t = since(m, m.level().getGameTime());
        return t >= 0 && (kind(m) == GRAFT_L || kind(m) == GRAFT_R) && t < GRAFT_TICKS;
    }

    // --- what is grafted (synced: every client shows the veins and the pilot's HUD names it) ----------------------

    /** The grafted gene now, or null. */
    public static Gene grafted(MechaEntity m) {
        long g = m.graftAt();
        if (g < 0 || m.level().getGameTime() - g / 256 >= LASTS) return null;
        int ordinal = (int) (g & 255);
        return ordinal < Gene.values().length ? Gene.values()[ordinal] : null;
    }

    /** Seconds left of the grafted gene (0 when none). */
    public static int secondsLeft(MechaEntity m) {
        if (grafted(m) == null) return 0;
        return (int) Math.ceil((LASTS - (m.level().getGameTime() - m.graftAt() / 256)) / 20.0);
    }

    /** The grafted creature's blood colour (0xRRGGBB) the veins run in while the gene lasts. */
    public static int tint(MechaEntity m) {
        return m.graftTint();
    }

    /** A blood type's colour, for the veins. */
    public static int colour(BloodType blood) {
        return switch (blood) {
            case WARM -> 0xC0242A;
            case COLD -> 0x3A6FC8;
            case ICHOR -> 0xD8C030;
            case ENDER -> 0x9A48D8;
            case BRIMSTONE -> 0xF07018;
            case GRAVE -> 0x6A8040;
            case ARCANE -> 0x40D8D0;
            case MARROW -> 0xE8E0C8;
            case OIL -> 0x3A3028;
            case ECTO -> 0x90F0B0;
        };
    }

    /** The gene a creature gives up to a Graft: its first that is not Legendary; null if it carries none at all. */
    static Gene geneOf(EntityType<?> type, boolean[] legendary) {
        List<Gene> genes = Gene.of(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
        legendary[0] = !genes.isEmpty();
        for (Gene g : genes) {
            if (g.category != Gene.Category.LEGENDARY) {
                legendary[0] = false;
                return g;
            }
        }
        return null;
    }

    // --- the taste: the last creature an arm hit ------------------------------------------------------------

    /** A blow was dealt: if a Chimera's arm dealt it, the machine remembers what and with which arm. */
    public static void dealt(LivingEntity victim, DamageSource source) {
        Entity direct = source.getDirectEntity(), cause = source.getEntity();
        MechaEntity m = direct instanceof MechaEntity a ? a : cause instanceof MechaEntity b ? b
                : cause instanceof Player p && p.getVehicle() instanceof MechaEntity c ? c : null;
        if (m == null || victim == m || victim.getVehicle() == m || !grafts(m) || m.level().isClientSide) return;
        int side = m.armActing;
        if (direct instanceof Projectile shot && shot.getPersistentData().contains(ARM_TAG)) side = shot.getPersistentData().getInt(ARM_TAG);
        if (side < 0) side = m.arm(0) != null ? 0 : 1;
        m.tasted = victim.getType();
        m.tastedBlood = colour(BloodType.of(victim));
        m.tastedSide = side & 1;
        m.tastedAt = m.level().getGameTime();
    }

    /** A rivet fired by this arm carries its side, so the blow it lands later is remembered against that arm. */
    public static void tag(Entity shot, int side) {
        shot.getPersistentData().putInt(ARM_TAG, side);
    }

    /** Every blow dealt anywhere: a Chimera's arm remembers what it hit. */
    @net.neoforged.fml.common.EventBusSubscriber(modid = com.coolfreeze23.headinajar.HeadInAJar.MOD_ID)
    public static final class Events {
        private Events() {
        }

        @net.neoforged.bus.api.SubscribeEvent
        public static void damaged(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) {
            dealt(event.getEntity(), event.getSource());
        }
    }

    // --- the drive key --------------------------------------------------------------------------------------

    /** The pilot pressed (true) or let go of the drive key: a press grafts or says why not (only the press acts). */
    public static void input(ServerPlayer pilot, boolean held) {
        if (!held || !(pilot.getVehicle() instanceof MechaEntity m) || m.getFirstPassenger() != pilot || !grafts(m)) return;
        long now = m.level().getGameTime();
        double t = since(m, now);
        // mid-gulp: replaying the recoil now would break the clip, so the answer is a line
        if (t >= 0 && (kind(m) == GRAFT_L || kind(m) == GRAFT_R) && t < GRAFT_TICKS) {
            Refusals.deny(pilot, Refuser.at(m), "mecha.graft.busy");
            return;
        }
        String reason = null;
        Object[] args = {};
        boolean[] legendary = {false};
        Gene gene = null;
        if (m.vent() > 0) reason = "mecha.graft.venting";
        else if (grafted(m) != null) {
            reason = "mecha.graft.running";
            args = new Object[]{secondsLeft(m)};
        } else if (m.tasted == null || now - m.tastedAt > TASTE) reason = "mecha.graft.nothing";
        else {
            gene = geneOf(m.tasted, legendary);
            Component name = m.tasted.getDescription();
            if (gene == null) {
                reason = legendary[0] ? "mecha.graft.legendary" : "mecha.graft.no_genes";
                args = new Object[]{name};
            } else if (m.battery() < GRAFT_FE) {
                reason = "mecha.graft.no_power";
                args = new Object[]{Component.translatable("block.headinajar.assembly_gantry")};
            } else if (!MechaSystems.hasInCargo(m, pilot, ModRegistry.BIO_GEL.get())) {
                reason = "mecha.graft.no_gel";
                args = new Object[]{Component.translatable("item.headinajar.bio_gel")};
            }
        }
        if (reason != null) {
            refuse(m, pilot, reason, args);
            return;
        }
        m.spend(GRAFT_FE);
        m.setVerbAt(now, m.tastedSide == 1 ? GRAFT_R : GRAFT_L);
        m.graftPending = gene;
        m.graftPendingTint = m.tastedBlood;
        // the taste is used up: the next graft needs a new blow
        m.tasted = null;
    }

    private static void refuse(MechaEntity m, ServerPlayer pilot, String reason, Object... args) {
        m.setVerbAt(m.level().getGameTime(), REFUSED);
        Refusals.deny(pilot, Refuser.at(m), reason, args);
    }

    /** Server, every tick: the take at the clip's tick 14, the gene's minute (and its drain), its end. */
    public static void serverTick(MechaEntity m) {
        if (!grafts(m)) return;
        long now = m.level().getGameTime();
        double t = since(m, now);
        if (m.graftPending != null && t >= TAKE && (kind(m) == GRAFT_L || kind(m) == GRAFT_R)) {
            Gene gene = m.graftPending;
            m.graftPending = null;
            ServerPlayer pilot = m.getFirstPassenger() instanceof ServerPlayer p ? p : null;
            if (!MechaSystems.takeFromCargo(m, pilot, ModRegistry.BIO_GEL.get())) {
                if (pilot != null) Refusals.deny(pilot, Refuser.at(m), "mecha.graft.ran_out");
            } else {
                m.setGraft(now, gene, m.graftPendingTint);
                // the Bio-Gel runs in the veins: a minute of its healing, as Bio-Gel always gives a Chimera
                var data = m.getPersistentData();
                data.putInt(MechaLogic.VEINS, data.getInt(MechaLogic.VEINS) + MechaLogic.VEINS_PER_GEL);
                if (pilot != null) {
                    pilot.displayClientMessage(Component.translatable("hud.headinajar.graft_taken", Component.translatable("gene.headinajar." + gene.id())), true);
                }
            }
        }
        long g = m.graftAt();
        if (g >= 0) {
            if (now - g / 256 >= LASTS) {
                Gene gone = Gene.values()[(int) (g & 255) % Gene.values().length];
                m.setGraft(-1, null, 0);
                if (m.getFirstPassenger() instanceof ServerPlayer p) {
                    p.displayClientMessage(Component.translatable("hud.headinajar.graft_off", Component.translatable("gene.headinajar." + gone.id())), true);
                }
            } else if (m.battery() > 0) {
                m.setBattery(m.battery() - HOLD_FE);
            }
        }
    }

    /** Both sides, every tick: the body's gene set carries the grafted gene while it lasts (never saved). */
    public static void genes(MechaEntity m) {
        GeneSet set = Genetics.genes(m);
        Gene now = grafts(m) ? grafted(m) : null;
        if (set.graft != now) {
            set.graft = now;
            set.changed();
        }
    }
}
