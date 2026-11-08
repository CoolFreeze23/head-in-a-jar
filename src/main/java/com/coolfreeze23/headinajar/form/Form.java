package com.coolfreeze23.headinajar.form;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** The shape a player is in. Every form but BODY is reset by a real death. */
public enum Form implements StringRepresentable {
    /** A normal player. */
    BODY,
    /** A head in a life-support jar. */
    JAR,
    /** The jar riding a mob host or Utility Shell. */
    HOSTED,
    /** The jar plugged into a Neural Socket. */
    SOCKET,
    /** The player's own headless body, driven over the neuro-link while the head is elsewhere. */
    LINKED,
    /** The jar seated in a mecha cockpit. */
    PILOT;

    public static final Codec<Form> CODEC = StringRepresentable.fromEnum(Form::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** The player entity is the jar itself: jar modules work, food is frozen. */
    public boolean isJarred() {
        return this == JAR || this == HOSTED || this == SOCKET || this == PILOT;
    }

    /** The head is not on the neck. */
    public boolean isDecapitated() {
        return this != BODY;
    }
}
