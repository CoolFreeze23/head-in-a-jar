package com.coolfreeze23.headinajar.host;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * What the mod keeps on a mob whose head was taken off with the Surgical Saw: it is a host body now, with a freshness
 * (decay) and a sync (how well a jar pilots it). Saved with the mob.
 */
public final class HostData {
    public static final Codec<HostData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.BOOL.optionalFieldOf("host", false).forGetter(d -> d.host),
            Codec.FLOAT.optionalFieldOf("freshness", 100f).forGetter(d -> d.freshness),
            Codec.FLOAT.optionalFieldOf("sync", 0f).forGetter(d -> d.sync),
            Codec.LONG.optionalFieldOf("embalmed_until", 0L).forGetter(d -> d.embalmedUntil),
            Codec.BOOL.optionalFieldOf("laced", false).forGetter(d -> d.laced),
            Codec.BOOL.optionalFieldOf("handshaken", false).forGetter(d -> d.handshaken),
            Codec.BOOL.optionalFieldOf("shell", false).forGetter(d -> d.shell),
            Codec.BOOL.optionalFieldOf("printed", false).forGetter(d -> d.printed),
            Codec.LONG.optionalFieldOf("run_anchor").forGetter(d -> d.runAnchor == HostData.NO_ANCHOR ? java.util.Optional.empty() : java.util.Optional.of(d.runAnchor)),
            Codec.LONG.optionalFieldOf("barter_due", 0L).forGetter(d -> d.barterDue),
            Codec.BOOL.optionalFieldOf("warming", false).forGetter(d -> d.warming)
    ).apply(i, HostData::new));

    /** {@link #runAnchor} when none is set. */
    public static final long NO_ANCHOR = Long.MIN_VALUE;

    public boolean host;
    public float freshness;
    public float sync;
    public long embalmedUntil;
    public boolean laced;
    public boolean handshaken;
    /** A Utility Shell: never decays, always fully synced. */
    public boolean shell;
    /** Printed by the Body Printer from a template: drops no loot and no experience when it dies (older saves: false). */
    public boolean printed;
    /** Ability cooldowns in ticks (not saved). */
    public int primaryCooldown, secondaryCooldown;
    /** Stored in a Body Locker (not saved: the locker holds the entity as NBT). */
    public boolean dirty;
    /**
     * A headless chicken's run: the spot it runs round ({@code BlockPos.asLong}), set where it was cut, or where it
     * stands on its first run if it has none (older saves, printed chickens). {@link #NO_ANCHOR} when unset.
     */
    public long runAnchor = NO_ANCHOR;
    /** A piglin host's barter: the game time its loot is due (0: none waiting). Saved, so an unload loses nothing. */
    public long barterDue;
    /** A strider host's Warm Up is on: standing still on lava heals it. */
    public boolean warming;
    /** A turtle host's Withdraw lasts until this game time (not saved). */
    public long withdrawnUntil;
    /** A frog host is in its Long Jump until it lands (not saved). */
    public boolean leaping;
    /** The last game time anyone rode it (not saved): a headless chicken waits a moment after a ride. */
    public long lastRidden = Long.MIN_VALUE / 2;

    public HostData() {
        this(false, 100f, 0f, 0L, false, false, false, false, java.util.Optional.empty(), 0L, false);
    }

    private HostData(boolean host, float freshness, float sync, long embalmedUntil, boolean laced, boolean handshaken, boolean shell, boolean printed,
                     java.util.Optional<Long> runAnchor, long barterDue, boolean warming) {
        this.host = host;
        this.freshness = freshness;
        this.sync = sync;
        this.embalmedUntil = embalmedUntil;
        this.laced = laced;
        this.handshaken = handshaken;
        this.shell = shell;
        this.printed = printed;
        this.runAnchor = runAnchor.orElse(NO_ANCHOR);
        this.barterDue = barterDue;
        this.warming = warming;
    }

    /** Whether a turtle host is withdrawn into its shell at {@code time}. */
    public boolean withdrawn(long time) {
        return time < withdrawnUntil;
    }

    public float effectiveSync() {
        return shell ? 100 : sync;
    }
}
