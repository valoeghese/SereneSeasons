package sereneseasons.mixin.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(TextureAtlas.class)
public interface AccessorTextureAtlas {
    @Accessor GpuTextureView[] getMipViews();
    @Accessor int getMaxMipLevel();
    @Accessor @Nullable GpuBuffer getSpriteUbos();
    @Accessor List<TextureAtlasSprite> getSprites();
    @Accessor int getWidth();
    @Accessor int getHeight();
}
