package com.coolfreeze23.headinajar.supplier;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * A line the server sends with its English beside the key, so a game that has never heard of the key (an older Head in
 * a Jar joined to a newer server) shows the English, not the key's name. A game that has the key reads it in its own
 * language, as ever.
 */
public final class Lines {
    private Lines() {
    }

    public static MutableComponent of(String key, Object... args) {
        Language language = Language.getInstance();
        return language.has(key) ? Component.translatableWithFallback(key, language.getOrDefault(key), args) : Component.translatable(key, args);
    }
}
