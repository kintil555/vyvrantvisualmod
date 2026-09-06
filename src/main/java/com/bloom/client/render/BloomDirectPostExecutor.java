package com.bloom.client.render;

import com.bloom.BloomMod;
import com.bloom.client.diagnostics.ShineFpsDiagnostics;
import com.bloom.mixin.client.accessor.PostChainAccessor;
import com.bloom.mixin.client.accessor.PostPassAccessor;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostChainConfig;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryStack;

final class BloomDirectPostExecutor implements AutoCloseable {
   private static final Map<PostChain, BloomDirectPostExecutor> EXECUTORS = new IdentityHashMap();
   private static final boolean ENABLE_DIRECT_POST = Boolean.getBoolean("shine.bloom.directPost");
   private static boolean disabledAfterFailure;
   private static boolean warnedBuildFailure;
   private static boolean warnedRenderFailure;
   private final PostChain chain;
   private final int mainWidth;
   private final int mainHeight;
   private final Map<Identifier, RenderTarget> targets;
   private final RenderTarget[] ownedTargets;
   private final RenderTarget compositeTarget;
   private final DirectPass[] passes;
   private boolean closed;

   private BloomDirectPostExecutor(PostChain chain, RenderTarget mainTarget, RenderTarget sourceTarget, RenderTarget terrainDepthTarget, RenderTarget occluderDepthTarget) {
      this.chain = chain;
      this.mainWidth = mainTarget.width;
      this.mainHeight = mainTarget.height;
      this.targets = new HashMap();
      this.updateExternalTargets(mainTarget, sourceTarget, terrainDepthTarget, occluderDepthTarget);
      this.compositeTarget = new TextureTarget("Shine persistent Bloom composite", this.mainWidth, this.mainHeight, false, GpuFormat.RGBA8_UNORM);
      Map<Identifier, PostChainConfig.InternalTarget> descriptors = ((PostChainAccessor)chain).bloom$getInternalTargets();
      this.ownedTargets = new RenderTarget[descriptors.size()];
      int targetIndex = 0;

      try {
         for(Map.Entry<Identifier, PostChainConfig.InternalTarget> entry : descriptors.entrySet()) {
            PostChainConfig.InternalTarget descriptor = (PostChainConfig.InternalTarget)entry.getValue();
            int width = (Integer)descriptor.width().orElse(this.mainWidth);
            int height = (Integer)descriptor.height().orElse(this.mainHeight);
            TextureTarget target = new TextureTarget("Shine persistent Bloom " + String.valueOf(entry.getKey()), width, height, false, GpuFormat.RGBA8_UNORM);
            this.ownedTargets[targetIndex++] = target;
            this.targets.put((Identifier)entry.getKey(), target);
         }

         List<PostPass> chainPasses = ((PostChainAccessor)chain).bloom$getPasses();
         this.passes = new DirectPass[chainPasses.size()];

         for(int index = 0; index < chainPasses.size(); ++index) {
            this.passes[index] = new DirectPass(index, (PostPass)chainPasses.get(index), this.targets);
         }

      } catch (LinkageError | RuntimeException exception) {
         for(DirectPass pass : this.passesOrEmpty()) {
            if (pass != null) {
               pass.close();
            }
         }

         for(RenderTarget target : this.ownedTargets) {
            if (target != null) {
               target.destroyBuffers();
            }
         }

         this.compositeTarget.destroyBuffers();
         throw exception;
      }
   }

   static boolean tryExecute(PostChain chain, RenderTarget mainTarget, RenderTarget sourceTarget, RenderTarget terrainDepthTarget, RenderTarget occluderDepthTarget, Projection projection, ProjectionMatrixBuffer projectionBuffer) {
      if (ENABLE_DIRECT_POST && !disabledAfterFailure && chain != null && !ShineFpsDiagnostics.useLegacyBloomPostForArchitecture()) {
         BloomDirectPostExecutor executor = (BloomDirectPostExecutor)EXECUTORS.get(chain);
         if (executor != null && (executor.mainWidth != mainTarget.width || executor.mainHeight != mainTarget.height)) {
            EXECUTORS.remove(chain);
            executor.close();
            executor = null;
         }

         if (executor == null) {
            try {
               executor = new BloomDirectPostExecutor(chain, mainTarget, sourceTarget, terrainDepthTarget, occluderDepthTarget);
               EXECUTORS.put(chain, executor);
               warnedBuildFailure = false;
               BloomMod.LOGGER.info("Shine persistent Bloom executor active with {} passes and {} retained targets at {}x{}.", new Object[]{executor.passes.length, executor.ownedTargets.length, executor.mainWidth, executor.mainHeight});
            } catch (LinkageError | RuntimeException exception) {
               disabledAfterFailure = true;
               if (!warnedBuildFailure) {
                  BloomMod.LOGGER.warn("Shine could not build the persistent Bloom executor; using Minecraft's post runner.", exception);
                  warnedBuildFailure = true;
               }

               return false;
            }
         }

         try {
            executor.render(mainTarget, sourceTarget, terrainDepthTarget, occluderDepthTarget, projection, projectionBuffer);
            warnedRenderFailure = false;
            return true;
         } catch (LinkageError | RuntimeException exception) {
            EXECUTORS.remove(chain);
            executor.close();
            disabledAfterFailure = true;
            if (!warnedRenderFailure) {
               BloomMod.LOGGER.warn("Shine's persistent Bloom executor failed; the legacy runner will resume next frame.", exception);
               warnedRenderFailure = true;
            }

            return true;
         }
      } else {
         return false;
      }
   }

   static void close(PostChain chain) {
      BloomDirectPostExecutor executor = (BloomDirectPostExecutor)EXECUTORS.remove(chain);
      if (executor != null) {
         executor.close();
      }

   }

   static void resetFailureState() {
      disabledAfterFailure = false;
      warnedBuildFailure = false;
      warnedRenderFailure = false;
   }

   private void render(RenderTarget mainTarget, RenderTarget sourceTarget, RenderTarget terrainDepthTarget, RenderTarget occluderDepthTarget, Projection projection, ProjectionMatrixBuffer projectionBuffer) {
      this.updateExternalTargets(mainTarget, sourceTarget, terrainDepthTarget, occluderDepthTarget);
      GpuBufferSlice projectionSlice = projectionBuffer.getBuffer(projection);
      CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
      long directCpu = ShineFpsDiagnostics.beginCpu("Bloom direct post CPU");
      RenderSystem.backupProjectionMatrix();

      try {
         RenderSystem.setProjectionMatrix(projectionSlice, ProjectionType.ORTHOGRAPHIC);

         for(int index = 0; index < this.passes.length; ++index) {
            DirectPass pass = this.passes[index];
            boolean finalMainComposite = index == this.passes.length - 1 && pass.outputsTo(LevelTargetBundle.MAIN_TARGET_ID);
            pass.render(encoder, this.targets, finalMainComposite ? this.compositeTarget : null);
            if (finalMainComposite) {
               GpuTexture compositeColor = this.compositeTarget.getColorTexture();
               GpuTexture mainColor = mainTarget.getColorTexture();
               if (compositeColor == null || mainColor == null) {
                  throw new IllegalStateException("Bloom composite copy requires color textures");
               }

               encoder.copyTextureToTexture(compositeColor, mainColor, 0, 0, 0, 0, 0, mainTarget.width, mainTarget.height);
            }
         }
      } finally {
         RenderSystem.restoreProjectionMatrix();
         ShineFpsDiagnostics.endCpu("Bloom direct post CPU", directCpu);
      }

   }

   private void updateExternalTargets(RenderTarget mainTarget, RenderTarget sourceTarget, RenderTarget terrainDepthTarget, RenderTarget occluderDepthTarget) {
      this.targets.put(LevelTargetBundle.MAIN_TARGET_ID, mainTarget);
      this.targets.put(BloomSourceRenderer.SOURCE_TARGET_ID, sourceTarget);
      this.targets.put(BloomSourceRenderer.TERRAIN_DEPTH_TARGET_ID, terrainDepthTarget);
      this.targets.put(BloomSourceRenderer.OCCLUDER_DEPTH_TARGET_ID, occluderDepthTarget);
   }

   private DirectPass[] passesOrEmpty() {
      return this.passes == null ? new DirectPass[0] : this.passes;
   }

   public void close() {
      if (!this.closed) {
         this.closed = true;

         for(DirectPass pass : this.passes) {
            pass.close();
         }

         for(RenderTarget target : this.ownedTargets) {
            target.destroyBuffers();
         }

         this.compositeTarget.destroyBuffers();
         this.targets.clear();
      }
   }

   private static RenderTarget requireTarget(Map<Identifier, RenderTarget> targets, Identifier id) {
      RenderTarget target = (RenderTarget)targets.get(id);
      if (target == null) {
         throw new IllegalStateException("Missing Bloom post target: " + String.valueOf(id));
      } else {
         return target;
      }
   }

   private static final class DirectPass implements AutoCloseable {
      private final String name;
      private final String diagnosticName;
      private final Supplier<String> debugName;
      private final RenderPipeline pipeline;
      private final Identifier outputTargetId;
      private final Map<String, GpuBuffer> customUniforms;
      private final String[] customUniformNames;
      private final DirectInput[] inputs;
      private final GpuBuffer samplerInfo;

      private DirectPass(int index, PostPass pass, Map<Identifier, RenderTarget> targets) {
         PostPassAccessor accessor = (PostPassAccessor)pass;
         this.name = accessor.bloom$getName();
         this.diagnosticName = "Post pass: " + this.name;
         this.debugName = () -> "Shine persistent Bloom pass " + index + " (" + this.name + ")";
         this.pipeline = accessor.bloom$getPipeline();
         this.outputTargetId = accessor.bloom$getOutputTargetId();
         this.customUniforms = accessor.bloom$getCustomUniforms();
         this.customUniformNames = (String[])this.customUniforms.keySet().toArray((x$0) -> new String[x$0]);
         List<PostPass.Input> postInputs = accessor.bloom$getInputs();
         this.inputs = new DirectInput[postInputs.size()];

         for(int inputIndex = 0; inputIndex < postInputs.size(); ++inputIndex) {
            this.inputs[inputIndex] = BloomDirectPostExecutor.DirectInput.create((PostPass.Input)postInputs.get(inputIndex), targets);
         }

         RenderTarget output = BloomDirectPostExecutor.requireTarget(targets, this.outputTargetId);
         Std140SizeCalculator size = (new Std140SizeCalculator()).putVec2();

         for(int inputIndex = 0; inputIndex < this.inputs.length; ++inputIndex) {
            size.putVec2();
         }

         MemoryStack stack = MemoryStack.stackPush();

         try {
            Std140Builder builder = Std140Builder.onStack(stack, size.get());
            builder.putVec2((float)output.width, (float)output.height);

            for(DirectInput input : this.inputs) {
               GpuTextureView view = input.view(targets);
               builder.putVec2((float)view.getWidth(0), (float)view.getHeight(0));
            }

            this.samplerInfo = RenderSystem.getDevice().createBuffer(() -> "Shine persistent Bloom sampler info " + index, 128, builder.get());
         } catch (Throwable var16) {
            if (stack != null) {
               try {
                  stack.close();
               } catch (Throwable var15) {
                  var16.addSuppressed(var15);
               }
            }

            throw var16;
         }

         if (stack != null) {
            stack.close();
         }

      }

      private boolean outputsTo(Identifier targetId) {
         return this.outputTargetId.equals(targetId);
      }

      private void render(CommandEncoder encoder, Map<Identifier, RenderTarget> targets, RenderTarget outputOverride) {
         RenderTarget output = outputOverride != null ? outputOverride : BloomDirectPostExecutor.requireTarget(targets, this.outputTargetId);
         GpuTextureView color = output.getColorTextureView();
         if (color == null) {
            throw new IllegalStateException("Bloom output has no color attachment: " + String.valueOf(this.outputTargetId));
         } else {
            ShineFpsDiagnostics.GpuToken gpu = ShineFpsDiagnostics.beginGpu(this.diagnosticName);

            try {
               RenderPass renderPass = output.useDepth && output.getDepthTextureView() != null ? encoder.createRenderPass(this.debugName, color, Optional.empty(), output.getDepthTextureView(), OptionalDouble.empty()) : encoder.createRenderPass(this.debugName, color, Optional.empty());

               try {
                  renderPass.setPipeline(this.pipeline);
                  RenderSystem.bindDefaultUniforms(renderPass);
                  renderPass.setUniform("SamplerInfo", this.samplerInfo);

                  for(String uniformName : this.customUniformNames) {
                     GpuBuffer buffer = (GpuBuffer)this.customUniforms.get(uniformName);
                     if (buffer != null) {
                        renderPass.setUniform(uniformName, buffer);
                     }
                  }

                  for(DirectInput input : this.inputs) {
                     input.bind(renderPass, targets);
                  }

                  renderPass.draw(0, 3, 1, 0);
               } catch (Throwable var18) {
                  if (renderPass != null) {
                     try {
                        renderPass.close();
                     } catch (Throwable var17) {
                        var18.addSuppressed(var17);
                     }
                  }

                  throw var18;
               }

               if (renderPass != null) {
                  renderPass.close();
               }
            } finally {
               ShineFpsDiagnostics.endGpu(gpu);
            }

         }
      }

      public void close() {
         this.samplerInfo.close();
      }
   }

   private static final class DirectInput {
      private final String shaderSamplerName;
      private final Identifier targetId;
      private final boolean depth;
      private final GpuTextureView fixedView;
      private final GpuSampler sampler;

      private DirectInput(String shaderSamplerName, Identifier targetId, boolean depth, GpuTextureView fixedView, GpuSampler sampler) {
         this.shaderSamplerName = shaderSamplerName;
         this.targetId = targetId;
         this.depth = depth;
         this.fixedView = fixedView;
         this.sampler = sampler;
      }

      private static DirectInput create(PostPass.Input input, Map<Identifier, RenderTarget> targets) {
         FilterMode filter = input.bilinear() ? FilterMode.LINEAR : FilterMode.NEAREST;
         GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(filter);
         String shaderName = input.samplerName() + "Sampler";
         if (input instanceof PostPass.TargetInput targetInput) {
            BloomDirectPostExecutor.requireTarget(targets, targetInput.targetId());
            return new DirectInput(shaderName, targetInput.targetId(), targetInput.depthBuffer(), (GpuTextureView)null, sampler);
         } else if (input instanceof PostPass.TextureInput textureInput) {
            GpuTextureView view = textureInput.texture().getTextureView();
            if (view == null) {
               throw new IllegalStateException("Bloom texture input has no view: " + input.samplerName());
            } else {
               return new DirectInput(shaderName, (Identifier)null, false, view, sampler);
            }
         } else {
            throw new IllegalArgumentException("Unsupported Bloom post input: " + input.getClass().getName());
         }
      }

      private GpuTextureView view(Map<Identifier, RenderTarget> targets) {
         if (this.fixedView != null) {
            return this.fixedView;
         } else {
            RenderTarget target = BloomDirectPostExecutor.requireTarget(targets, this.targetId);
            GpuTextureView view = this.depth ? target.getDepthTextureView() : target.getColorTextureView();
            if (view == null) {
               String var10002 = this.depth ? "depth" : "color";
               throw new IllegalStateException("Bloom input has no " + var10002 + " attachment: " + String.valueOf(this.targetId));
            } else {
               return view;
            }
         }
      }

      private void bind(RenderPass renderPass, Map<Identifier, RenderTarget> targets) {
         renderPass.bindTexture(this.shaderSamplerName, this.view(targets), this.sampler);
      }
   }
}
