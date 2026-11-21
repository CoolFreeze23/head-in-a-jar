package com.coolfreeze23.headinajar.staff.second;

import com.coolfreeze23.headinajar.Advance;
import com.coolfreeze23.headinajar.fx.Fx;
import com.coolfreeze23.headinajar.genetics.BloodType;
import com.coolfreeze23.headinajar.host.HostData;
import com.coolfreeze23.headinajar.item.JarredHeadItem;
import com.coolfreeze23.headinajar.staff.work.WorkerData;
import com.coolfreeze23.headinajar.staff.work.Workforce;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Team sync: a seconded head and its body bond as they work (1% every 24 seconds on shift with a card, half that when
 * their blood does not mix; an oil body at full rate), and the body's traits (25%), Ability (50%), Secondary (75%) and
 * the 100% bonus open with it. The job brain uses the body's abilities on their cooldowns ({@link BodyWork}).
 * <p>
 * Only a tick on the job with a card counts: not awaiting, not in the line, not on a detail, not off site, not on an
 * operator's break, and not dry (a head with no broth does nothing). Each quarter is a pulse down the body for whoever
 * watches (an {@code Fx} of kind {@link Fx#TEAM_SYNC_PULSE}) and a change the collar's watchers are sent; at 100 the
 * employer earns Team Spirit (when they next come by, if they are away).
 */
public final class TeamSync {
    /** Team sync earned a tick of work at the full rate (%): 1% every 480 ticks, 100% in 40 minutes. */
    public static final float RATE = 1f / 480f;
    /** Where each thing opens. */
    public static final int TRAITS = 25, ABILITY = 50, SECONDARY = 75, FULL = 100;
    /** Brain flag bit 0 (saved): the pair reached 100% while its employer was away, and Team Spirit is still owed. */
    public static final int FLAG_SPIRIT_DUE = 1;

    private TeamSync() {
    }

    static void register() {
        BodyWork.register();
    }

    /** Each server tick of a collared host. */
    static void tick(Mob host, SecondeeData d, HostData data, long time) {
        if (!d.seated()) return;
        if ((d.flags & FLAG_SPIRIT_DUE) != 0 && (time + host.getId()) % 100 == 0) spirit(host, d);
        BodyWork.tick(host, d, time);
        if (d.teamSync >= FULL) return;
        float rate = rate(host, d);
        if (rate <= 0) return;
        add(host, d, rate);
    }

    /** Adds {@code amount} team sync to a seated pair (at most to 100): the quarter's pulse and Team Spirit as they come. */
    public static void add(Mob host, SecondeeData d, float amount) {
        int was = quarter(d.teamSync);
        d.teamSync = Math.min(FULL, d.teamSync + amount);
        int now = quarter(d.teamSync);
        if (now > was) quarter(host, d, now);
    }

    /** 0 below 25, 1 from 25, ... 4 at 100. */
    public static int quarter(float teamSync) {
        return Math.min(4, (int) Math.floor(teamSync / 25f));
    }

    /** Team sync this tick earns (0 if it does not count). */
    public static float rate(Mob host, SecondeeData d) {
        return counts(host, d) ? RATE * blood(host, d) : 0;
    }

    /**
     * Whether this tick is work that bonds: on the job with a card (a personal one or a rack posting), the brain running
     * (no operator's break, staff and secondments on), and broth in the canister.
     */
    public static boolean counts(Mob host, SecondeeData d) {
        WorkerData w = Workforce.data(host);
        if (w == null || w.state != WorkerData.State.ON_JOB || w.card.isEmpty() && w.rackSlot < 0) return false;
        if (Workforce.paused || !Workforce.enabled() || !Secondees.enabled()) return false;
        return d.canister > 0;
    }

    /** The blood chart's rate: 1 when the head's blood and the body's mix (or the body is oil, equipment), else 0.5. */
    public static float blood(Mob host, SecondeeData d) {
        Blood b = BLOOD.get(host);
        if (b != null && b.head == d.head) return b.factor;
        EntityType<?> type = JarredHeadItem.type(d.head);
        BloodType head = type == null ? BloodType.WARM : BloodType.of(type), body = BloodType.of(host);
        float factor = body == BloodType.OIL || head.compatible(body) ? 1f : 0.5f;
        BLOOD.put(host, new Blood(d.head, factor));
        return factor;
    }

    /** The blood factor per host, for the head it was worked out for (a new head is worked out again). */
    private record Blood(ItemStack head, float factor) {
    }

    private static final Map<Mob, Blood> BLOOD = new WeakHashMap<>();

    /** A quarter reached: the pulse for whoever watches, the collar's watchers told, and at 100 Team Spirit. */
    private static void quarter(Mob host, SecondeeData d, int quarter) {
        if (!(host.level() instanceof ServerLevel level)) return;
        Fx.send(level, Fx.of(Fx.TEAM_SYNC_PULSE, host.position(), null, -1, host.getId(), quarter));
        SecondeeSync.changed(host);
        if (quarter >= 4) {
            d.flags |= FLAG_SPIRIT_DUE;
            spirit(host, d);
        }
    }

    /** Team Spirit to the employer, if they are on the server (else it stays owed, and is given when they are). */
    private static void spirit(Mob host, SecondeeData d) {
        if (d.employer == null || !(host.level() instanceof ServerLevel level)) return;
        ServerPlayer boss = level.getServer().getPlayerList().getPlayer(d.employer);
        if (boss == null) return;
        Advance.grant(boss, "team_spirit");
        d.flags &= ~FLAG_SPIRIT_DUE;
    }
}
