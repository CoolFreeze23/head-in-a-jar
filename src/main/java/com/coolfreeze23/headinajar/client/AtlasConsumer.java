package com.coolfreeze23.headinajar.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * Draws a model painted on its own sheet into the block atlas's shared buffers, so every machine's moving parts (and
 * a jar's glass and frame) go to the GPU together at the end of the frame instead of one draw call each: a buffer
 * source draws the last batch every time a different texture asks for a buffer, and those fixed block-atlas buffers
 * are the ones it keeps open. The sheet must be in the block atlas (the machines' sheets are, being their baked
 * models' too; the jar's, the goggles' and the collar's are added by {@code atlases/blocks.json}); UVs over the sheet
 * are moved onto its place in the atlas.
 */
public final class AtlasConsumer implements VertexConsumer {
    private final VertexConsumer out;
    private final float u0, du, v0, dv;

    private AtlasConsumer(VertexConsumer out, TextureAtlasSprite sprite) {
        this.out = out;
        this.u0 = sprite.getU0();
        this.du = sprite.getU1() - sprite.getU0();
        this.v0 = sprite.getV0();
        this.dv = sprite.getV1() - sprite.getV0();
    }

    /** The block-atlas sprite of a texture file ({@code ns:textures/x/y.png} is {@code ns:x/y}). */
    public static TextureAtlasSprite sprite(ResourceLocation texture) {
        String path = texture.getPath();
        if (path.startsWith("textures/")) path = path.substring("textures/".length());
        if (path.endsWith(".png")) path = path.substring(0, path.length() - 4);
        return Minecraft.getInstance().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS)
                .getSprite(ResourceLocation.fromNamespaceAndPath(texture.getNamespace(), path));
    }

    public static VertexConsumer of(VertexConsumer out, ResourceLocation texture) {
        return new AtlasConsumer(out, sprite(texture));
    }

    /**
     * A buffer source that sends {@code texture}'s cutout and translucent layers to the block atlas's fixed buffers
     * (cut out, or translucent with back faces culled), and everything else on as asked.
     */
    public static MultiBufferSource onAtlas(MultiBufferSource buffers, ResourceLocation... textures) {
        RenderType[] cutout = new RenderType[textures.length], translucent = new RenderType[textures.length];
        for (int i = 0; i < textures.length; i++) {
            cutout[i] = RenderType.entityCutoutNoCull(textures[i]);
            translucent[i] = RenderType.entityTranslucent(textures[i]);
        }
        return type -> {
            for (int i = 0; i < textures.length; i++) {
                if (type == cutout[i]) return of(buffers.getBuffer(Sheets.cutoutBlockSheet()), textures[i]);
                if (type == translucent[i]) return of(buffers.getBuffer(Sheets.translucentCullBlockSheet()), textures[i]);
            }
            return buffers.getBuffer(type);
        };
    }

    @Override
    public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
        out.addVertex(x, y, z, color, u0 + u * du, v0 + v * dv, overlay, light, nx, ny, nz);
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        out.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        out.setColor(r, g, b, a);
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        out.setUv(u0 + u * du, v0 + v * dv);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        out.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        out.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        out.setNormal(x, y, z);
        return this;
    }
}
