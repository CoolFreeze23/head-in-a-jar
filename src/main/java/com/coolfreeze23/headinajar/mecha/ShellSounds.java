package com.coolfreeze23.headinajar.mecha;

import java.util.Map;

/**
 * The shells' clip sounds (the Rover, the Spider Walker): neither has a sound set of its own, so each clip sound plays
 * the nearest of the machines' events, or an alias of one under a subtitle of its own (HopperSounds.ALIASES). Kept apart
 * from the client that plays them so the table can be checked on the server.
 */
public final class ShellSounds {
    /** The Rover's clip sounds, by what each borrows from the Hopper's and the Wheelhouse's sets. */
    public static final Map<String, String> BORROWED = Map.ofEntries(
            Map.entry("bell_ding", "rover.bell"), Map.entry("bell_tick", "relay_tick"), Map.entry("buckle_pop", "wheelhouse.catch_release"),
            Map.entry("buckle_click", "bar_latch"), Map.entry("buckle_burst", "bar_blow"), Map.entry("castor_squeak", "rover.castor_squeak"),
            Map.entry("castor_squeal", "wheelhouse.tyre_scrub"), Map.entry("castor_rattle", "rattle"), Map.entry("castor_snap", "wheelhouse.wheel_pop"),
            Map.entry("castor_clatter", "wheelhouse.wheel_clatter"), Map.entry("drawer_knock", "rover.drawer_knock"), Map.entry("drawer_slam", "rover.drawer_slam"),
            Map.entry("drawer_slide", "rover.drawer_slide"), Map.entry("jar_hop", "rover.jar_hop"), Map.entry("jar_land", "land_light"), Map.entry("jar_seat", "jar_seat"),
            Map.entry("motor_rev", "rover.motor_rev"), Map.entry("motor_stutter", "servo_fail"), Map.entry("motor_whine", "servo"),
            Map.entry("reverse_beep", "rover.reverse_beep"), Map.entry("strap_slap", "rover.strap_slap"), Map.entry("tray_spring", "wheelhouse.buffer_spring"),
            Map.entry("tray_tip", "rattle"),
            // the Spider Walker's: claws, tin, clockwork key, legs, the web gland
            Map.entry("claw_release", "bulwark.clamp_release"), Map.entry("claw_open", "bar_unlatch"), Map.entry("claw_click", "bar_latch"),
            Map.entry("claw_rattle", "rattle"), Map.entry("tin_creak", "spider_walker.tin_creak"), Map.entry("tin_rattle", "rattle"),
            Map.entry("tin_boing", "wheelhouse.buffer_spring"), Map.entry("tin_scrape", "spider_walker.tin_scrape"), Map.entry("tin_clatter", "spider_walker.tin_clatter"),
            Map.entry("key_tick", "relay_tick"), Map.entry("key_wind", "spider_walker.key_wind"), Map.entry("key_rundown", "spider_walker.key_rundown"),
            Map.entry("leg_tick", "servo_tick"), Map.entry("leg_rub", "servo_tick"), Map.entry("leg_hook", "bar_latch"), Map.entry("leg_curl", "servo_sag"),
            Map.entry("point_drag", "step_drag"), Map.entry("gland_squeeze", "spider_walker.gland_squeeze"), Map.entry("nozzle_cough", "spider_walker.nozzle_cough"),
            Map.entry("nozzle_tap", "spider_walker.nozzle_tap"), Map.entry("spring_twang", "wheelhouse.buffer_spring"));

    /** The event name a Rover or Spider Walker clip sound plays ("mecha." left off). */
    public static String resolve(String id) {
        String name = id.startsWith("headinajar:") ? id.substring(11) : id;
        if (name.startsWith("mecha.")) name = name.substring(6);
        return BORROWED.getOrDefault(name, name);
    }

    private ShellSounds() {
    }
}
