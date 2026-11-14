package com.coolfreeze23.headinajar.smoke;

import com.coolfreeze23.headinajar.client.fx.SoundLoops;
import com.coolfreeze23.headinajar.fx.FxSounds;
import com.coolfreeze23.headinajar.fx.HouseSounds;
import com.coolfreeze23.headinajar.machine.MachineSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.Supplier;

/**
 * Dev-only: a room of running machines, as the loop mix sees it, without building one: for a number of ticks, before the
 * mix runs, asks for {@code kinds} different machine loops in a ring 4 blocks round the player and ten identical
 * Centrifuge whines within 2 blocks of each other, through the real loop player and the real sound engine. The
 * {@code loops} verb then says how many play and how many are heard.
 */
final class LoopProbe {
    private static final List<Supplier<SoundEvent>> KINDS = List.of(MachineSounds.MIXER_PADDLE::get, MachineSounds.PRINTER_WHIRR::get,
            MachineSounds.ARRAY_STEAM::get, MachineSounds.TESLA_HUM::get, MachineSounds.JAMMER_WHIRR::get, FxSounds.TESLA_ARC::get,
            FxSounds.TK_HOLD::get, HouseSounds.DRIVE::get, HouseSounds.ROLL::get, HouseSounds.PROPELLER::get);
    static int ticksLeft, kinds;

    static void start(int kinds, int ticks) {
        LoopProbe.kinds = Math.min(kinds, KINDS.size());
        ticksLeft = ticks;
    }

    static void tick() {
        var mc = Minecraft.getInstance();
        if (ticksLeft <= 0 || mc.player == null) return;
        ticksLeft--;
        Vec3 c = mc.player.position();
        for (int i = 0; i < kinds; i++) {
            double a = 2 * Math.PI * i / kinds;
            SoundLoops.want("probe" + i, KINDS.get(i).get(), SoundSource.BLOCKS, c.add(4 * Math.cos(a), 1, 4 * Math.sin(a)), 1, 1);
        }
        for (int i = 0; i < 10; i++) {
            SoundLoops.want("probe_centrifuge" + i, MachineSounds.CENTRIFUGE_WHINE.get(), SoundSource.BLOCKS, c.add(-2 + 0.4 * i, 1, 6), 0.6f, 1.1f);
        }
    }

    private LoopProbe() {
    }
}
