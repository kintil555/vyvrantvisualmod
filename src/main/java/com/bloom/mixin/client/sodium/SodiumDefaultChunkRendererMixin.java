package com.bloom.mixin.client.sodium;

import com.bloom.BloomMod;
import com.bloom.client.render.BloomMaskAtlas;
import com.bloom.client.render.BloomSourceRenderer;
import com.bloom.client.render.ShineRenderBackend;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderPassDescriptor;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;
import net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Adds the bloom-mask color attachment (and the shore foam/mask sampler
 * bindings) to Sodium's terrain render pass. The pipeline built in
 * {@link SodiumShaderChunkRendererMixin} declares an extra color target for
 * bloom output, so the render pass created here must attach a matching
 * number of color attachments or Sodium throws
 * "Render pass color attachment count must match pipeline color target
 * state count." Rim-light masking isn't implemented in this build, so that
 * slot is always left unused.
 */
@Mixin(
   value = {DefaultChunkRenderer.class},
   remap = false
)
public abstract class SodiumDefaultChunkRendererMixin {
   @Unique
   private static boolean shine$loggedRenderPassAttachments;

   @WrapOperation(
      method = {"render"},
      at = {@At(
   value = "INVOKE",
   target = "Lcom/mojang/blaze3d/systems/CommandEncoder;createRenderPass(Ljava/util/function/Supplier;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/Optional;Lcom/mojang/blaze3d/textures/GpuTextureView;Ljava/util/OptionalDouble;)Lcom/mojang/blaze3d/systems/RenderPass;"
)},
      require = 1
   )
   private RenderPass shine$createTerrainRenderPassWithShineAttachments(CommandEncoder encoder, Supplier<String> label, GpuTextureView colorTexture, Optional<Vector4fc> colorClear, GpuTextureView depthTexture, OptionalDouble depthClear, Operation<RenderPass> original) {
      GpuTextureView bloomView = BloomSourceRenderer.getSodiumBloomAttachmentView();
      RenderPassDescriptor descriptor = RenderPassDescriptor.create(label);
      descriptor.withColorAttachment(colorTexture, colorClear);
      shine$addOptionalColorAttachment(descriptor, bloomView);
      descriptor.withUnusedColorAttachment();

      if (depthTexture != null) {
         descriptor.withDepthAttachment(depthTexture, depthClear);
      }

      descriptor.withRenderArea(new RenderPass.RenderArea(0, 0, colorTexture.getWidth(0), colorTexture.getHeight(0)));
      if (!shine$loggedRenderPassAttachments && bloomView != null) {
         BloomMod.LOGGER.debug("Shine created Sodium terrain render pass with bloomAttachment={} size={}x{}.", bloomView != null, colorTexture.getWidth(0), colorTexture.getHeight(0));
         shine$loggedRenderPassAttachments = true;
      }

      return encoder.createRenderPass(descriptor);
   }

   @Unique
   private static void shine$addOptionalColorAttachment(RenderPassDescriptor descriptor, GpuTextureView textureView) {
      if (textureView != null) {
         descriptor.withColorAttachment(textureView);
      } else {
         descriptor.withUnusedColorAttachment();
      }
   }

   @WrapOperation(
      method = {"render"},
      at = {@At(
   value = "INVOKE",
   target = "Lcom/mojang/blaze3d/systems/RenderPass;bindTexture(Ljava/lang/String;Lcom/mojang/blaze3d/textures/GpuTextureView;Lcom/mojang/blaze3d/textures/GpuSampler;)V",
   ordinal = 1
)},
      require = 1
   )
   private void shine$bindShineTextures(RenderPass renderPass, String samplerName, GpuTextureView textureView, GpuSampler sampler, Operation<Void> original) {
      original.call(new Object[]{renderPass, samplerName, textureView, sampler});
      GpuTextureView maskTextureView = BloomMaskAtlas.getMaskTextureView();
      renderPass.bindTexture("u_ShineMaskTex", maskTextureView != null ? maskTextureView : textureView, maskTextureView != null ? RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST) : sampler);
      if (!ShineRenderBackend.canUseRawOpenGl()) {
         // On the raw-GL path the foam sampler is bound manually in
         // TerrainCausticsRenderer.bindSodiumEdgeFoamSampler once the real
         // texture is ready. Elsewhere, bind a placeholder so the sampler
         // slot the pipeline declared is never left unbound.
         renderPass.bindTexture("u_ShineWaterEdgeFoamTex", textureView, sampler);
      }

      // The remaining samplers the patched shader declares belong to
      // features not implemented in this build (terrain caustics, clouds,
      // foliage wind, grass-blade interaction, water opaque depth/
      // reflection/wake). Their corresponding *Enabled uniforms all default
      // to 0, so the samples are never used - but GL still requires every
      // declared sampler to be bound to something, or program validation
      // fails with "program texture usage".
      for (String deferredSampler : DEFERRED_SAMPLERS) {
         renderPass.bindTexture(deferredSampler, textureView, sampler);
      }
   }

   @Unique
   private static final String[] DEFERRED_SAMPLERS = {
      "u_ShineCausticsTex",
      "u_ShineCloudTex",
      "u_ShineFoliageWindMaskTex",
      "u_ShineGrassBladeInteractionTex",
      "u_ShineWaterOpaqueDepthTex",
      "u_ShineWaterReflectionTex",
      "u_ShineWaterWakeTex"
   };
}
