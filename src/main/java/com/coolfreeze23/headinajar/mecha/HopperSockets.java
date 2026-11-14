package com.coolfreeze23.headinajar.mecha;

import java.util.ArrayList;
import java.util.List;

/**
 * Where a module hangs on an animated frame: the socket bones for each slot, by the model's convention. A slot's socket is {@code socket_<part>} (or {@code socket_<part>_l} and {@code _r} for a part on both
 * sides); a module model's top bones are named for where they hang ({@code mount}, or {@code mount_<part>}).
 */
public final class HopperSockets {
    private HopperSockets() {
    }

    /** The socket bones a slot's module is drawn at. */
    public static List<String> sockets(MechaFrame frame, int flatIndex) {
        MechaModule.Slot slot = frame.slotAt(flatIndex);
        List<String> out = new ArrayList<>();
        if (slot == null) return out;
        int nth = flatIndex - frame.firstIndex(slot);
        switch (slot) {
            case LEGS -> out.addAll(List.of("socket_thigh_l", "socket_thigh_r", "socket_shin_l", "socket_shin_r"));
            case BACK -> out.add(nth == 0 ? "socket_back" : "socket_back_" + nth);
            case CORE -> out.add(nth == 0 ? "socket_core" : "socket_core_" + nth);
            case TORSO -> out.add("socket_torso_" + nth);
            // the first two are the arms; slots 3 and 4 are the shoulder hardpoints (the Colossus's cradles, the Executive's pads)
            case ARM -> out.add(nth == 0 ? "socket_arm_l" : nth == 1 ? "socket_arm_r" : "socket_arm_" + nth);
            case SENSOR -> out.add(nth == 0 ? "socket_sensor" : "socket_sensor_" + nth);
        }
        return out;
    }

    /** The flat slot a socket bone serves, or -1. */
    public static int slotOf(MechaFrame frame, String bone) {
        for (int i = 0; i < frame.totalSlots(); i++) if (sockets(frame, i).contains(bone)) return i;
        return -1;
    }

    /** The module model's top bone drawn at this socket: {@code mount}, or {@code mount_<part>} for a part on both sides. */
    public static String mountOf(String socket) {
        String part = socket.substring("socket_".length());
        if (part.startsWith("thigh") || part.startsWith("shin")) return "mount_" + part.substring(0, part.indexOf('_'));
        return "mount";
    }
}
