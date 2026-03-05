package sereneseasons.client.item;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.platform.NativeImage;

public interface ExtendedSpriteContents {
    GpuBufferSlice[] sereneseasons$getGpubufferSlices();

    NativeImage[] sereneseasons$getMipmappedImages();
}
