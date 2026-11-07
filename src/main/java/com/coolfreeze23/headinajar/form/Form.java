package com.coolfreeze23.headinajar.form;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/** The shape a player is in. Every form but BODY is reset by death. */
public enum Form implements StringRepresentable {
    /** A normal player. */
    BODY,
    /** A head in a life-support jar. */
    JAR,
    /** The jar steering a robot or mob body. */
    SHELL,
    /** The jar plugged into a Neural Socket. */
    SOCKET;

    public static final Codec<Form> CODEC = StringRepresentable.fromEnum(Form::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public boolean isJarred() {
        return this != BODY;
    }
}
