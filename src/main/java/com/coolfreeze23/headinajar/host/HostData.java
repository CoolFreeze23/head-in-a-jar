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
            Codec.BOOL.optionalFieldOf("shell", false).forGetter(d -> d.shell)
    ).apply(i, HostData::new));

    public boolean host;
    public float freshness;
    public float sync;
    public long embalmedUntil;
    public boolean laced;
    public boolean handshaken;
    /** A Utility Shell: never decays, always fully synced. */
    public boolean shell;
    /** Ability cooldowns in ticks (not saved). */
    public int primaryCooldown, secondaryCooldown;
    /** Stored in a Body Locker (not saved: the locker holds the entity as NBT). */
    public boolean dirty;

    public HostData() {
        this(false, 100f, 0f, 0L, false, false, false);
    }

    private HostData(boolean host, float freshness, float sync, long embalmedUntil, boolean laced, boolean handshaken, boolean shell) {
        this.host = host;
        this.freshness = freshness;
        this.sync = sync;
        this.embalmedUntil = embalmedUntil;
        this.laced = laced;
        this.handshaken = handshaken;
        this.shell = shell;
    }

    public float effectiveSync() {
        return shell ? 100 : sync;
    }
}
