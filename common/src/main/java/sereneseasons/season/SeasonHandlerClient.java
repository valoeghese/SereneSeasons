/*******************************************************************************
 * Copyright 2024, the Glitchfiend Team.
 * All rights reserved.
 ******************************************************************************/
package sereneseasons.season;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.*;
import glitchcore.event.TickEvent;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.lwjgl.system.MemoryUtil;
import sereneseasons.api.season.Season;
import sereneseasons.client.item.ExtendedSpriteContents;
import sereneseasons.init.ModConfig;
import sereneseasons.mixin.client.AccessorTextureAtlas;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.OptionalInt;

public class SeasonHandlerClient
{
    static Season.SubSeason lastSeason = null;
    public static final HashMap<ResourceKey<Level>, Integer> clientSeasonCycleTicks = new HashMap<>();

    public static void onClientTick(TickEvent.Client event)
    {
        Player player = (Player) Minecraft.getInstance().player;

        //Only do this when in the world
        if (player == null) return;
        ResourceKey<Level> dimension = player.level().dimension();

        if (event.getPhase() == TickEvent.Phase.END && ModConfig.seasons.isDimensionWhitelisted(dimension))
        {
            //Keep ticking as we're synchronized with the server only every second
            clientSeasonCycleTicks.compute(dimension, (k, v) -> v == null ? 0 : (v + 1) % SeasonTime.ZERO.getCycleDuration());

            SeasonTime calendar = new SeasonTime(clientSeasonCycleTicks.get(dimension));
            if (calendar.getSubSeason() != lastSeason)
            {
                Minecraft.getInstance().levelRenderer.allChanged();
                lastSeason = calendar.getSubSeason();

                // Update textures
                Minecraft.getInstance().schedule(() -> {
                    updateSeasonTextures(calendar);
                });
            }
        }
    }

    // Reference: TextureAtlas#uploadInitialFrame; SpriteContents$AnimationState#drawToTextureAtlas

    private static void updateSeasonTextures(SeasonTime calendar) {
        TextureAtlas blockAtlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
        TextureAtlasSprite spriteCherry = blockAtlas.getSprite(Identifier.parse("minecraft:block/cherry_leaves"));
        TextureAtlasSprite spriteOak = blockAtlas.getSprite(Identifier.parse("minecraft:block/oak_leaves"));
        TextureAtlasSprite spriteCherryEarly = blockAtlas.getSprite(Identifier.parse("sereneseasons:block/cherry_leaves_early_spring"));
        TextureAtlasSprite spriteCherryLate = blockAtlas.getSprite(Identifier.parse("sereneseasons:block/cherry_leaves_late_spring"));

        AccessorTextureAtlas accessorBlockAtlas = (AccessorTextureAtlas) blockAtlas;
        final int mipCount = accessorBlockAtlas.getMipLevelCount();

        Object2IntOpenHashMap<TextureAtlasSprite> notAnimated = new Object2IntOpenHashMap<>();
        notAnimated.defaultReturnValue(-1);

        List<TextureAtlasSprite> notAnimatedList = accessorBlockAtlas.getSprites().stream().filter(p_460298_ -> !p_460298_.contents().isAnimated()).toList();
        for (int i = 0; i < notAnimatedList.size(); i++) {
            notAnimated.put(notAnimatedList.get(i), i);

            if (notAnimatedList.get(i) == spriteCherry) {
                System.out.println("Cherry is " + i);
            }
            if (notAnimatedList.get(i) == spriteCherryEarly) {
                System.out.println("Cherry Early is " + i);
            }
            if (notAnimatedList.get(i) == spriteOak) {
                System.out.println("Oak is" + i);
            }
            if (notAnimatedList.get(i) == blockAtlas.missingSprite()) {
                System.out.println("Missing is " + i);
            }
        }

        int alignedUBOSize = Mth.roundToward(SpriteContents.UBO_SIZE, RenderSystem.getDevice().getUniformOffsetAlignment());
        int stride = alignedUBOSize * mipCount;
        ByteBuffer nonAnimatedByteBuffer = MemoryUtil.memAlloc(notAnimated.size() * stride);

        // Dest Prepare
        int cId = notAnimated.getInt(spriteCherry);
        int oId = notAnimated.getInt(spriteOak);
        if (cId != -1) {
            System.out.println("Uploading sprite ubo for cherry at " +cId);
            spriteCherry.uploadSpriteUbo(nonAnimatedByteBuffer, cId * stride, mipCount - 1, accessorBlockAtlas.getWidth(), accessorBlockAtlas.getHeight(), alignedUBOSize);
        }
        // probably not necessary for src but
        if (oId != -1) {
            spriteOak.uploadSpriteUbo(nonAnimatedByteBuffer, oId * stride, mipCount - 1, accessorBlockAtlas.getWidth(), accessorBlockAtlas.getHeight(), alignedUBOSize);
        }

        // Draw
        GpuTextureView[] oakSrc = createTextureView(spriteOak, mipCount);
        GpuTextureView[] cherrySrc = createTextureView(spriteCherry, mipCount);
        GpuTextureView[] cherryEarlySrc = createTextureView(spriteCherryEarly, mipCount);
        GpuTextureView[] cherryLateSrc = createTextureView(spriteCherryLate, mipCount);

        List<GpuTextureView[]> viewsCreated = new ArrayList<>();
        viewsCreated.add(oakSrc);
        viewsCreated.add(cherrySrc);
        viewsCreated.add(cherryEarlySrc);
        viewsCreated.add(cherryLateSrc);

        // Render
        GpuSampler gpusampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST, true);

        try (GpuBuffer nonAnimatedBuffer = RenderSystem.getDevice().createBuffer(() -> "SpriteAnimationInfo", 128, nonAnimatedByteBuffer)) {
            for (int mip = 0; mip < mipCount; mip++) {
                try (RenderPass renderpass = RenderSystem.getDevice()
                        .createCommandEncoder()
                        .createRenderPass(() -> "Seasonal Texture Change", accessorBlockAtlas.getMipViews()[mip], OptionalInt.empty())) {
                    renderpass.setPipeline(RenderPipelines.ANIMATE_SPRITE_BLIT);

                    // get slice
                    int index = notAnimated.getInt(spriteCherry);
                    GpuBufferSlice cherryDest;
                    if (index == -1) {
                        ExtendedSpriteContents contentsDest = (ExtendedSpriteContents) spriteCherry.contents();
                        cherryDest = contentsDest.sereneseasons$getAnimatedGpubufferSlices()[mip];
                    } else {
                        cherryDest = nonAnimatedBuffer.slice(index * stride + mip * alignedUBOSize, SpriteContents.UBO_SIZE);
                    }

                    if (calendar.getSeason() == Season.SPRING) {
                        System.out.println("Setting texture to CHERRY");
                        if (calendar.getSubSeason() == Season.SubSeason.EARLY_SPRING) {
                            drawTexture(cherryDest, gpusampler, renderpass, mip, cherryEarlySrc);
                        } else if (calendar.getSubSeason() == Season.SubSeason.MID_SPRING) {
                            drawTexture(cherryDest, gpusampler, renderpass, mip, cherrySrc);
                        } else {
                            drawTexture(cherryDest, gpusampler, renderpass, mip, cherryLateSrc);
                        }

                        // Render
//                        renderpass.bindTexture("Sprite", cherrySrc[mip], gpusampler);
//                        renderpass.setUniform("SpriteAnimationInfo", cherryDest);
//                        // param 0: progress towards next frame in blending textures
//                        renderpass.draw(0, 6);
                    } else {
                        System.out.println("Setting texture to OAK");
                        drawTexture(cherryDest, gpusampler, renderpass, mip, oakSrc);

                        // Render
//                        renderpass.bindTexture("Sprite", oakSrc[mip], gpusampler);
//                        renderpass.setUniform("SpriteAnimationInfo", cherryDest);
//                        // param 0: progress towards next frame in blending textures
//                        renderpass.draw(0, 6);
                    }
                }
            }
        }

        for (GpuTextureView[] viewSet : viewsCreated) {
            for (GpuTextureView view : viewSet) {
                view.close();
                view.texture().close();
            }
        }

        MemoryUtil.memFree(nonAnimatedByteBuffer);

//       try {
//           blockAtlas.dumpContents(AtlasIds.BLOCKS, Path.of("C:\\Users\\Mekal Covic\\Downloads\\atlas.png"));
//       } catch (Exception e) {e.printStackTrace();}
    }

    private static GpuTextureView[] createTextureView(TextureAtlasSprite spriteSrc, int mipCount) {
        // see TextureAtlas#uploadInitialContents
        GpuTexture gputexture = RenderSystem.getDevice().createTexture(
                () -> spriteSrc.contents().name().toString(),
                5,
                TextureFormat.RGBA8,
                spriteSrc.contents().width(),
                spriteSrc.contents().height(),
                1,
                mipCount
        );
        GpuTextureView[] views = new GpuTextureView[mipCount];

        for (int l = 0; l < mipCount; l++) {
            spriteSrc.uploadFirstFrame(gputexture, l);
            // RenderSystem.getDevice().createCommandEncoder().writeToTexture($$0, /*contentsSrc*/ this.byMipLevel[$$1], $$1, 0, 0, 0, this.width >> $$1, this.height >> $$1, 0, 0);
            views[l] = RenderSystem.getDevice().createTextureView(gputexture);
        }

        return views;
    }

    private static void drawTexture(GpuBufferSlice sliceDest, GpuSampler sampler, RenderPass pass, int mip, GpuTextureView[] src) {
        // Render
        pass.bindTexture("Sprite", src[mip], sampler);
        pass.setUniform("SpriteAnimationInfo", sliceDest);
        // param 0: progress towards next frame in blending textures
        pass.draw(0, 6);
    }
}
