package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * The picket signs' six slogans in the player's language (0.7.2). Each is a 48 x 32 texture of lettering
 * (textures/picket/&lt;language&gt;/sign_&lt;n&gt;.png); this source stitches the set for the language in use into the
 * block atlas as {@code headinajar:block/picket/sign_<n>}, where the sign models find them, English where a language has
 * none and nothing where neither is there. Changing the language reloads the resources, so the signs change with it.
 */
public final class PicketSigns implements SpriteSource {
    public static final MapCodec<PicketSigns> CODEC = MapCodec.unit(PicketSigns::new);
    public static final SpriteSourceType TYPE = new SpriteSourceType(CODEC);

    @Override
    public void run(ResourceManager resources, Output output) {
        var mc = Minecraft.getInstance();
        String language = mc == null || mc.getLanguageManager() == null ? "en_us" : mc.getLanguageManager().getSelected();
        for (int n = 0; n < StrikeClient.SLOGANS; n++) {
            ResourceLocation id = HeadInAJar.id("block/picket/sign_" + n);
            var own = resources.getResource(HeadInAJar.id("textures/picket/" + language + "/sign_" + n + ".png"));
            var resource = own.isPresent() ? own : resources.getResource(HeadInAJar.id("textures/picket/en_us/sign_" + n + ".png"));
            resource.ifPresent(r -> output.add(id, r));
        }
    }

    @Override
    public SpriteSourceType type() {
        return TYPE;
    }
}
