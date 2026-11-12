package com.coolfreeze23.headinajar.client;

import com.coolfreeze23.headinajar.HeadInAJar;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSourceType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * The Safety Placard's twelve notices in the player's language. Each is a 48 x 48 texture drawn from the language's
 * lines (textures/placard/&lt;language&gt;/notice_&lt;n&gt;.png); this source stitches the set for the language in use
 * into the block atlas as {@code headinajar:block/placard/notice_<n>}, English where a language has none. Changing the
 * language reloads the resources, so the boards change with it.
 */
public final class PlacardNotices implements SpriteSource {
    public static final MapCodec<PlacardNotices> CODEC = MapCodec.unit(PlacardNotices::new);
    public static final SpriteSourceType TYPE = new SpriteSourceType(CODEC);
    public static final int NOTICES = 12;

    @Override
    public void run(ResourceManager resources, Output output) {
        var mc = Minecraft.getInstance();
        String language = mc == null || mc.getLanguageManager() == null ? "en_us" : mc.getLanguageManager().getSelected();
        for (int n = 0; n < NOTICES; n++) {
            ResourceLocation id = HeadInAJar.id("block/placard/notice_" + n);
            var own = resources.getResource(HeadInAJar.id("textures/placard/" + language + "/notice_" + n + ".png"));
            var resource = own.isPresent() ? own : resources.getResource(HeadInAJar.id("textures/placard/en_us/notice_" + n + ".png"));
            resource.ifPresent(r -> output.add(id, r));
        }
    }

    @Override
    public SpriteSourceType type() {
        return TYPE;
    }
}
