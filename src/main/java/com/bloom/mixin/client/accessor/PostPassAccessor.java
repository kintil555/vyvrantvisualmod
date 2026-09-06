package com.bloom.mixin.client.accessor;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({PostPass.class})
public interface PostPassAccessor {
   @Accessor("name")
   String bloom$getName();

   @Accessor("customUniforms")
   Map<String, GpuBuffer> bloom$getCustomUniforms();

   @Accessor("pipeline")
   RenderPipeline bloom$getPipeline();

   @Accessor("outputTargetId")
   Identifier bloom$getOutputTargetId();

   @Accessor("inputs")
   List<PostPass.Input> bloom$getInputs();
}
