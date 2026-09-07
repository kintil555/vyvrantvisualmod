package com.bloom.mixin.client.sodium;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.caffeinemc.mods.sodium.client.render.chunk.UniformBufferManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(
   value = {UniformBufferManager.class},
   remap = false
)
public interface SodiumUniformBufferManagerAccessor {
   @Accessor("uniformData")
   GpuBufferSlice shine$getUniformData();

   @Accessor("uniformData")
   void shine$setUniformData(GpuBufferSlice var1);

   @Accessor("hasUpdatedThisFrame")
   boolean shine$hasUpdatedThisFrame();

   @Accessor("hasUpdatedThisFrame")
   void shine$setUpdatedThisFrame(boolean var1);
}
