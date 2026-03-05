package sereneseasons.mixin.client;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.SpriteContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sereneseasons.client.item.ExtendedSpriteContents;

@Mixin(SpriteContents.class)
public class MixinSpriteContents implements ExtendedSpriteContents {
    @Shadow
    NativeImage[] byMipLevel;
    @Unique
    GpuBufferSlice[] sereneseasons$agpubufferslice;

    @Inject(at = @At("HEAD"), method = "createAnimationState")
    private void onCreateAnimationState(GpuBufferSlice slice, int $$1, CallbackInfoReturnable<SpriteContents.AnimationState> cir) {
        GpuBufferSlice[] agpubufferslice = new GpuBufferSlice[this.byMipLevel.length];

        for (int i1 = 0; i1 < this.byMipLevel.length; i1++) {
            agpubufferslice[i1] = slice.slice(i1 * $$1, $$1);
        }

        this.sereneseasons$agpubufferslice = agpubufferslice;
    }

    @Override
    public GpuBufferSlice[] sereneseasons$getGpubufferSlices() {
        return this.sereneseasons$agpubufferslice;
    }

    @Override
    public NativeImage[] sereneseasons$getMipmappedImages() {
        return this.byMipLevel;
    }
}
