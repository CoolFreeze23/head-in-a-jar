package com.coolfreeze23.headinajar.mecha;

import com.coolfreeze23.headinajar.entity.MechaEntity;
import com.coolfreeze23.headinajar.feedback.Refusals;
import com.coolfreeze23.headinajar.feedback.Refuser;
import com.coolfreeze23.headinajar.genetics.Gene;
import com.coolfreeze23.headinajar.genetics.GeneEffects;
import com.coolfreeze23.headinajar.genetics.Genetics;
import com.coolfreeze23.headinajar.genetics.Serum;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * The Executive's own: the premium jingle on the drive key (it does not lift, it delegates), and what its four Gene Port
 * vials show. A press plays {@code drive_jingle} (both gloves tug the lapels, a nod, the vials pulse in turn) with the
 * jingle, and the machine stands still for it; a press while it plays is answered with a line. The vials show each
 * port's serum: filled by its load, coloured by its genes, dimmed while its active gene rests after firing.
 */
public final class Executive {
    /** The jingle's moment kind (the low bits of the synced verb time). */
    public static final int JINGLE = 3;
    public static final int JINGLE_TICKS = 30;
    /**
     * FE a jingle costs: about 1% of the Executive's 2,000 kFE battery (a full-strength use of the frame's own verb).
     * With less than that it still plays, on what is left.
     */
    public static final int JINGLE_FE = 20_000;
    /** A port holds up to this much load (a mecha's Gene Port, {@link Genetics.Target#MECHA}). */
    public static final int PORT_LOAD = Genetics.Target.MECHA.loadCap;

    private Executive() {
    }

    public static boolean jingles(MechaEntity m) {
        MechaRig rig = MechaRig.of(m.frame());
        return rig != null && rig.verb == MechaRig.Verb.JINGLE;
    }

    /** Ticks into the jingle, or -1 when none is playing. */
    public static double jingle(MechaEntity m, double now) {
        long at = m.verbAt();
        if (!jingles(m) || at < 0 || (at & 7) != JINGLE) return -1;
        double t = now - at / 8;
        return t >= 0 && t < JINGLE_TICKS ? t : -1;
    }

    /** It stands still for its jingle. */
    public static boolean rooted(MechaEntity m) {
        return jingle(m, m.level().getGameTime()) >= 0;
    }

    /** The pilot pressed (true) or let go of the drive key: a press plays the jingle (only the press acts). */
    public static void input(ServerPlayer pilot, boolean held) {
        if (!held || !(pilot.getVehicle() instanceof MechaEntity m) || m.getFirstPassenger() != pilot || !jingles(m)) return;
        long now = m.level().getGameTime();
        if (jingle(m, now) >= 0) {
            Refusals.deny(pilot, Refuser.at(m), "mecha.jingle.busy");
            return;
        }
        m.setBattery(m.battery() - JINGLE_FE);
        m.setVerbAt(now, JINGLE);
        // the jingle is the press's answer: played by the server, so everyone near hears it, a late arrival too
        Hopper.play(m, "premium_jingle");
    }

    public static void serverTick(MechaEntity m) {
        if (m.genePorts() <= 0) return;
        // the vials whose active gene rests after firing (the client dims them; the rest of a gene's state is the server's)
        List<Gene> actives = GeneEffects.actives(m);
        List<Serum> serums = Genetics.genes(m).serums;
        int mask = 0;
        for (int which = 0; which < actives.size() && which < 2; which++) {
            if (GeneEffects.cooldown(m, which) <= 0) continue;
            Gene gene = actives.get(which);
            for (int n = 0; n < serums.size() && n < 8; n++) {
                if (serums.get(n).genes().stream().anyMatch(e -> e.gene() == gene)) mask |= 1 << n;
            }
        }
        m.setPortsResting(mask);
    }

    // --- what a vial shows (both sides; the client draws it) ------------------------------------------------------

    /** How full port n's vial is, 0 (empty) to 1: its serum's load over a port's capacity. */
    public static float fill(MechaEntity m, int port) {
        List<Serum> serums = Genetics.genes(m).serums;
        if (port < 0 || port >= serums.size()) return 0;
        return Math.max(0.125f, Math.min(1, serums.get(port).load() / (float) PORT_LOAD));
    }

    /**
     * Port n's serum colour (0xRRGGBB): the colour of the kind of gene that takes most of its load (movement green,
     * defence steel blue, senses amber, offence red, metabolism pink, legendary violet, a mutation sickly yellow).
     */
    public static int colour(MechaEntity m, int port) {
        List<Serum> serums = Genetics.genes(m).serums;
        if (port < 0 || port >= serums.size()) return 0xFFFFFF;
        java.util.Map<Gene.Category, Integer> load = new java.util.EnumMap<>(Gene.Category.class);
        for (Serum.Entry e : serums.get(port).genes()) load.merge(e.gene().category, e.gene().load, Integer::sum);
        Gene.Category top = load.entrySet().stream().max(java.util.Map.Entry.comparingByValue()).map(java.util.Map.Entry::getKey).orElse(Gene.Category.MUTATION);
        return switch (top) {
            case MOVEMENT -> 0x58D86A;
            case DEFENCE -> 0x6A9CD8;
            case SENSES -> 0xF0B848;
            case OFFENCE -> 0xE0483C;
            case METABOLISM -> 0xF078B0;
            case LEGENDARY -> 0xA45CF0;
            case MUTATION -> 0xC8D040;
        };
    }

    /** Port n rests (its active gene fired and is cooling down). */
    public static boolean resting(MechaEntity m, int port) {
        return (m.portsResting() & (1 << port)) != 0;
    }
}
