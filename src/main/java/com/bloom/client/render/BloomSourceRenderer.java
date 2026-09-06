package com.bloom.client.render;

import com.bloom.BloomMod;
import com.bloom.client.selection.BloomSelection;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelTerrainRenderContext;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayerGroup;
import net.minecraft.client.renderer.rendertype.PreparedRenderType;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

public final class BloomSourceRenderer {
   public static final Identifier SOURCE_TARGET_ID = Identifier.fromNamespaceAndPath("vybrantvisual", "source");
   public static final Identifier TERRAIN_DEPTH_TARGET_ID = Identifier.fromNamespaceAndPath("vybrantvisual", "terrain_depth");
   public static final Identifier OPAQUE_COLOR_TARGET_ID = Identifier.fromNamespaceAndPath("vybrantvisual", "opaque_color");
   public static final Identifier OCCLUDER_DEPTH_TARGET_ID = Identifier.fromNamespaceAndPath("vybrantvisual", "occluder_depth");
   private static final Identifier TERRAIN_VERTEX_SHADER_ID = Identifier.withDefaultNamespace("core/terrain");
   private static final Identifier BLOOM_TERRAIN_SOURCE_FRAGMENT_SHADER_ID = Identifier.fromNamespaceAndPath("vybrantvisual", "core/terrain_bloom_source");
   private static final Identifier ENTITY_SOURCE_SHADER_ID = Identifier.fromNamespaceAndPath("vybrantvisual", "core/entity_bloom_source");
   private static final Identifier PARTICLE_SOURCE_SHADER_ID = Identifier.fromNamespaceAndPath("vybrantvisual", "core/particle_bloom_source");
   private static final Identifier BLOOM_TERRAIN_SOLID_PIPELINE_ID = Identifier.fromNamespaceAndPath("vybrantvisual", "pipeline/bloom_terrain_solid");
   private static final Identifier BLOOM_TERRAIN_CUTOUT_PIPELINE_ID = Identifier.fromNamespaceAndPath("vybrantvisual", "pipeline/bloom_terrain_cutout");
   private static final Identifier BLOOM_ENTITY_PIPELINE_ID = Identifier.fromNamespaceAndPath("vybrantvisual", "pipeline/bloom_entity_source");
   private static final Identifier BLOOM_PARTICLE_PIPELINE_ID = Identifier.fromNamespaceAndPath("vybrantvisual", "pipeline/bloom_particle_source");
   private static final BindGroupLayout MASK_SAMPLER_LAYOUT = BindGroupLayout.builder().withSampler("MaskSampler").build();
   private static final List<String> CHUNK_SECTION_UNIFORMS = List.of("ChunkSection");
   private static final float INTERNAL_POST_SCALE = 0.5F;
   private static final int BLOOM_ATTACHMENT_USAGE = 13;
   private static final int TERRAIN_DEPTH_USAGE = 15;
   private static final RenderPipeline BLOOM_TERRAIN_SOLID_PIPELINE;
   private static final RenderPipeline BLOOM_TERRAIN_CUTOUT_PIPELINE;
   private static final RenderPipeline BLOOM_TERRAIN_TRANSLUCENT_PIPELINE;
   private static final RenderPipeline BLOOM_ENTITY_PIPELINE;
   private static final RenderPipeline BLOOM_PARTICLE_PIPELINE;
   private static GpuTexture bloomAttachmentTexture;
   private static GpuTextureView bloomAttachmentView;
   private static GpuTexture terrainDepthTexture;
   private static GpuTextureView terrainDepthView;
   private static GpuTexture opaqueColorTexture;
   private static GpuTextureView opaqueColorView;
   private static GpuTexture occluderDepthTexture;
   private static GpuTextureView occluderDepthView;
   private static AttachedBloomRenderTarget sourceTarget;
   private static AttachedTerrainDepthRenderTarget terrainDepthTarget;
   private static AttachedOpaqueColorRenderTarget opaqueColorTarget;
   private static AttachedOccluderDepthRenderTarget occluderDepthTarget;
   private static final Map<PreparedRenderType, RenderType> PREPARED_RENDER_TYPES;
   private static boolean preparedThisFrame;
   private static boolean terrainDepthCapturedThisFrame;
   private static boolean opaqueColorCapturedThisFrame;
   private static boolean occluderDepthCapturedThisFrame;
   private static boolean loggedAttachmentFailure;
   private static boolean loggedTerrainDepthFailure;
   private static boolean loggedOpaqueColorFailure;
   private static boolean loggedOccluderDepthFailure;
   private static boolean loggedSourcePrepared;
   private static boolean loggedSodiumDrawBuffers;
   private static boolean loggedVanillaReplayInvocation;
   private static boolean loggedVanillaReplayFailure;
   private static boolean loggedEntityReplayFailure;
   private static boolean loggedEntityReplaySuccess;
   private static boolean loggedParticleReplayFailure;
   private static boolean loggedParticleReplaySuccess;

   private BloomSourceRenderer() {
   }

   public static void reset() {
      if (ShineRenderBackend.canUseRawOpenGl()) {
         OpenGlBloomFramebufferBridge.reset();
      }

      if (bloomAttachmentView != null) {
         bloomAttachmentView.close();
         bloomAttachmentView = null;
      }

      if (bloomAttachmentTexture != null) {
         bloomAttachmentTexture.close();
         bloomAttachmentTexture = null;
      }

      if (terrainDepthView != null) {
         terrainDepthView.close();
         terrainDepthView = null;
      }

      if (terrainDepthTexture != null) {
         terrainDepthTexture.close();
         terrainDepthTexture = null;
      }

      if (opaqueColorView != null) {
         opaqueColorView.close();
         opaqueColorView = null;
      }

      if (opaqueColorTexture != null) {
         opaqueColorTexture.close();
         opaqueColorTexture = null;
      }

      if (occluderDepthView != null) {
         occluderDepthView.close();
         occluderDepthView = null;
      }

      if (occluderDepthTexture != null) {
         occluderDepthTexture.close();
         occluderDepthTexture = null;
      }

      sourceTarget = null;
      terrainDepthTarget = null;
      opaqueColorTarget = null;
      occluderDepthTarget = null;
      PREPARED_RENDER_TYPES.clear();
      preparedThisFrame = false;
      terrainDepthCapturedThisFrame = false;
      opaqueColorCapturedThisFrame = false;
      occluderDepthCapturedThisFrame = false;
      loggedAttachmentFailure = false;
      loggedTerrainDepthFailure = false;
      loggedOpaqueColorFailure = false;
      loggedOccluderDepthFailure = false;
      loggedEntityReplayFailure = false;
      loggedEntityReplaySuccess = false;
      loggedParticleReplayFailure = false;
      loggedParticleReplaySuccess = false;
      loggedVanillaReplayFailure = false;
      loggedSodiumDrawBuffers = false;
      BloomEntityMaskTextures.clear();
   }

   public static void prepareSource(LevelTerrainRenderContext context) {
      preparedThisFrame = false;
      terrainDepthCapturedThisFrame = false;
      opaqueColorCapturedThisFrame = false;
      occluderDepthCapturedThisFrame = false;
      if (shine$isBloomSourceAllowed()) {
         BloomMaskAtlas.ensureReady();
         Minecraft minecraft = Minecraft.getInstance();
         RenderTarget mainTarget = minecraft.gameRenderer.mainRenderTarget();
         if (mainTarget != null && mainTarget.width > 0 && mainTarget.height > 0) {
            if (ensureAttachment(mainTarget.width, mainTarget.height)) {
               if (clearAttachment()) {
                  if (sourceTarget == null) {
                     sourceTarget = new AttachedBloomRenderTarget();
                  }

                  sourceTarget.setAttachment(mainTarget, bloomAttachmentTexture, bloomAttachmentView);
                  preparedThisFrame = true;
                  if (!loggedSourcePrepared) {
                     BloomMod.LOGGER.debug("Shine bloom source target prepared at {}x{}.", mainTarget.width, mainTarget.height);
                     loggedSourcePrepared = true;
                  }

               }
            }
         }
      }
   }

   public static RenderTarget getSourceTarget() {
      return preparedThisFrame && shine$isBloomSourceAllowed() ? sourceTarget : null;
   }

   public static GpuTexture getSodiumBloomAttachmentTexture() {
      return hasPreparedSourceThisFrame() ? bloomAttachmentTexture : null;
   }

   public static GpuTextureView getSodiumBloomAttachmentView() {
      return hasPreparedSourceThisFrame() ? bloomAttachmentView : null;
   }

   public static boolean hasPreparedSourceThisFrame() {
      return preparedThisFrame && sourceTarget != null && shine$isBloomSourceAllowed();
   }

   public static void resetTerrainDepthCapture() {
      terrainDepthCapturedThisFrame = false;
      opaqueColorCapturedThisFrame = false;
   }

   public static void captureTerrainDepth() {
      captureTerrainDepth(false);
   }

   public static void captureTerrainDepth(boolean captureOpaqueColor) {
      terrainDepthCapturedThisFrame = false;
      opaqueColorCapturedThisFrame = false;
      Minecraft minecraft = Minecraft.getInstance();
      RenderTarget mainTarget = minecraft.gameRenderer.mainRenderTarget();
      if (mainTarget != null && mainTarget.width > 0 && mainTarget.height > 0) {
         GpuTexture mainDepth = mainTarget.getDepthTexture();
         if (mainDepth != null && mainDepth.getFormat().hasDepthAspect()) {
            if (ensureTerrainDepthSnapshot(mainTarget.width, mainTarget.height, mainDepth.getFormat())) {
               label137: {
                  try {
                     RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(mainDepth, terrainDepthTexture, 0, 0, 0, 0, 0, mainTarget.width, mainTarget.height);
                     break label137;
                  } catch (RuntimeException e) {
                     if (!loggedTerrainDepthFailure) {
                        BloomMod.LOGGER.warn("Shine could not snapshot terrain depth for bloom/entity occlusion.", e);
                        loggedTerrainDepthFailure = true;
                     }
                  }

                  return;
               }

               if (terrainDepthTarget == null) {
                  terrainDepthTarget = new AttachedTerrainDepthRenderTarget();
               }

               terrainDepthTarget.setAttachment(mainTarget, terrainDepthTexture, terrainDepthView);
               terrainDepthCapturedThisFrame = true;
               if (captureOpaqueColor) {
                  GpuTexture mainColor = mainTarget.getColorTexture();
                  if (mainColor != null && mainColor.getFormat().hasColorAspect()) {
                     if (ensureOpaqueColorSnapshot(mainTarget.width, mainTarget.height, mainColor.getFormat())) {
                        try {
                           RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(mainColor, opaqueColorTexture, 0, 0, 0, 0, 0, mainTarget.width, mainTarget.height);
                        } catch (RuntimeException e) {
                           if (!loggedOpaqueColorFailure) {
                              BloomMod.LOGGER.warn("Shine could not snapshot opaque scene color for volumetric compositing.", e);
                              loggedOpaqueColorFailure = true;
                           }

                           return;
                        }

                        if (opaqueColorTarget == null) {
                           opaqueColorTarget = new AttachedOpaqueColorRenderTarget();
                        }

                        opaqueColorTarget.setAttachment(mainTarget, opaqueColorTexture, opaqueColorView);
                        opaqueColorCapturedThisFrame = true;
                     }
                  }
               }
            }
         }
      }
   }

   public static RenderTarget getTerrainDepthTarget() {
      return terrainDepthCapturedThisFrame ? terrainDepthTarget : null;
   }

   public static boolean hasCapturedTerrainDepthThisFrame() {
      return terrainDepthCapturedThisFrame && terrainDepthTarget != null;
   }

   public static RenderTarget getOpaqueColorTarget() {
      return opaqueColorCapturedThisFrame ? opaqueColorTarget : null;
   }

   public static boolean hasCapturedOpaqueColorThisFrame() {
      return opaqueColorCapturedThisFrame && opaqueColorTarget != null;
   }

   public static void captureOccluderDepth() {
      occluderDepthCapturedThisFrame = false;
      if (preparedThisFrame) {
         Minecraft minecraft = Minecraft.getInstance();
         RenderTarget mainTarget = minecraft.gameRenderer.mainRenderTarget();
         if (mainTarget != null && mainTarget.width > 0 && mainTarget.height > 0) {
            GpuTexture mainDepth = mainTarget.getDepthTexture();
            if (mainDepth != null && mainDepth.getFormat().hasDepthAspect()) {
               if (ensureOccluderDepthSnapshot(mainTarget.width, mainTarget.height, mainDepth.getFormat())) {
                  try {
                     RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(mainDepth, occluderDepthTexture, 0, 0, 0, 0, 0, mainTarget.width, mainTarget.height);
                  } catch (RuntimeException e) {
                     if (!loggedOccluderDepthFailure) {
                        BloomMod.LOGGER.warn("Shine could not snapshot pre-translucent occluder depth for bloom occlusion.", e);
                        loggedOccluderDepthFailure = true;
                     }

                     return;
                  }

                  if (occluderDepthTarget == null) {
                     occluderDepthTarget = new AttachedOccluderDepthRenderTarget();
                  }

                  occluderDepthTarget.setAttachment(mainTarget, occluderDepthTexture, occluderDepthView);
                  occluderDepthCapturedThisFrame = true;
               }
            }
         }
      }
   }

   public static RenderTarget getOccluderDepthTarget() {
      return occluderDepthCapturedThisFrame ? occluderDepthTarget : null;
   }

   public static boolean hasCapturedOccluderDepthThisFrame() {
      return occluderDepthCapturedThisFrame && occluderDepthTarget != null;
   }

   public static void replayVanillaChunkGroup(ChunkSectionLayerGroup group, GpuSampler sampler, GpuTextureView textureView, EnumMap<ChunkSectionLayer, Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>>> drawGroupsPerLayer, int maxIndicesRequired, GpuBufferSlice[] chunkSectionInfos) {
      if (shine$isBloomSourceAllowed() && preparedThisFrame && (group == ChunkSectionLayerGroup.OPAQUE || group == ChunkSectionLayerGroup.TRANSLUCENT) && bloomAttachmentView != null && drawGroupsPerLayer != null) {
         if (!loggedVanillaReplayInvocation) {
            int solid = countDraws((Int2ObjectOpenHashMap)drawGroupsPerLayer.get(ChunkSectionLayer.SOLID));
            int cutout = countDraws((Int2ObjectOpenHashMap)drawGroupsPerLayer.get(ChunkSectionLayer.CUTOUT));
            BloomMod.LOGGER.debug("Shine vanilla replay invoked: solidDraws={} cutoutDraws={}", solid, cutout);
            loggedVanillaReplayInvocation = true;
         }

         Minecraft minecraft = Minecraft.getInstance();
         if (!SharedConstants.DEBUG_HOTKEYS || !minecraft.wireframe) {
            RenderTarget outputTarget = group.outputTarget();
            GpuTextureView depthTextureView = outputTarget.getDepthTextureView();
            if (depthTextureView != null) {
               CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();

               try {
                  RenderPass renderPass = encoder.createRenderPass(() -> "shine_bloom_source_" + group.label(), bloomAttachmentView, Optional.empty(), depthTextureView, OptionalDouble.empty());

                  try {
                     RenderSystem.bindDefaultUniforms(renderPass);
                     GpuTextureView lightmapView = minecraft.gameRenderer.lightmap();
                     if (lightmapView != null) {
                        renderPass.bindTexture("Sampler2", lightmapView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                     }

                     GpuTextureView maskTextureView = BloomMaskAtlas.getMaskTextureView();
                     if (maskTextureView != null) {
                        renderPass.bindTexture("MaskSampler", maskTextureView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                     }

                     RenderSystem.AutoStorageIndexBuffer sequentialBuffer = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
                     GpuBuffer indexBuffer = maxIndicesRequired == 0 ? null : sequentialBuffer.getBuffer(maxIndicesRequired);
                     IndexType indexType = maxIndicesRequired == 0 ? null : sequentialBuffer.type();

                     for(ChunkSectionLayer layer : group.layers()) {
                        Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>> drawGroups = (Int2ObjectOpenHashMap)drawGroupsPerLayer.get(layer);
                        if (drawGroups != null && !drawGroups.isEmpty()) {
                           RenderPipeline var10000;
                           switch (layer) {
                              case SOLID -> var10000 = BLOOM_TERRAIN_SOLID_PIPELINE;
                              case CUTOUT -> var10000 = BLOOM_TERRAIN_CUTOUT_PIPELINE;
                              case TRANSLUCENT -> var10000 = BLOOM_TERRAIN_TRANSLUCENT_PIPELINE;
                              default -> var10000 = null;
                           }

                           RenderPipeline pipeline = var10000;
                           if (pipeline != null) {
                              renderPass.setPipeline(pipeline);
                              renderPass.bindTexture("Sampler0", textureView, sampler);
                              ObjectIterator var22 = drawGroups.values().iterator();

                              while(var22.hasNext()) {
                                 List<RenderPass.Draw<GpuBufferSlice[]>> draws = (List)var22.next();
                                 if (draws != null && !draws.isEmpty()) {
                                    List<RenderPass.Draw<GpuBufferSlice[]>> orderedDraws = layer == ChunkSectionLayer.TRANSLUCENT ? draws.reversed() : draws;
                                    renderPass.drawMultipleIndexed(orderedDraws, indexBuffer, indexType, CHUNK_SECTION_UNIFORMS, chunkSectionInfos);
                                 }
                              }
                           }
                        }
                     }
                  } catch (Throwable var26) {
                     if (renderPass != null) {
                        try {
                           renderPass.close();
                        } catch (Throwable var25) {
                           var26.addSuppressed(var25);
                        }
                     }

                     throw var26;
                  }

                  if (renderPass != null) {
                     renderPass.close();
                  }
               } catch (RuntimeException e) {
                  if (!loggedVanillaReplayFailure) {
                     BloomMod.LOGGER.warn("Shine vanilla terrain bloom replay failed on the {} backend; terrain bloom is skipped for this frame.", ShineRenderBackend.current(), e);
                     loggedVanillaReplayFailure = true;
                  }
               }

            }
         }
      }
   }

   private static int countDraws(Int2ObjectOpenHashMap<List<RenderPass.Draw<GpuBufferSlice[]>>> drawGroups) {
      if (drawGroups != null && !drawGroups.isEmpty()) {
         int count = 0;
         ObjectIterator var2 = drawGroups.values().iterator();

         while(var2.hasNext()) {
            List<RenderPass.Draw<GpuBufferSlice[]>> draws = (List)var2.next();
            if (draws != null) {
               count += draws.size();
            }
         }

         return count;
      } else {
         return 0;
      }
   }

   public static void replayRenderType(RenderType renderType, MeshData meshData) {
      if (!OffscreenRenderTargetGuard.isActive() && shine$isBloomSourceAllowed() && preparedThisFrame && terrainDepthCapturedThisFrame && bloomAttachmentView != null && terrainDepthView != null && renderType != null && meshData != null) {
         RenderPipeline pipeline = renderType.pipeline();
         if (BloomRenderTypeUtil.isEntityPipeline(pipeline)) {
            Identifier textureId = BloomRenderTypeUtil.sampler0Texture(renderType);
            if (BloomEntityTextureCatalog.isEntityTexture(textureId)) {
               BloomEntityTextureCatalog.recordEntityTexture(textureId);
               double strength = BloomSelection.getEntityTextureSourceStrength(textureId.toString());
               if (!(strength <= 1.0E-5)) {
                  GpuTextureView maskView = BloomEntityMaskTextures.getMaskTextureView(textureId);
                  if (maskView != null) {
                     AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(textureId);
                     if (texture != null) {
                        try {
                           GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy(), new Vector4f(1.0F, 1.0F, 1.0F, 1.0F), new Vector3f(), new Matrix4f());
                           UploadedMesh uploadedMesh = uploadImmediateMesh(BLOOM_ENTITY_PIPELINE, meshData);
                           CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
                           RenderPass renderPass = encoder.createRenderPass(() -> "shine_entity_bloom_source", bloomAttachmentView, Optional.empty(), terrainDepthView, OptionalDouble.empty());

                           try {
                              renderPass.setPipeline(BLOOM_ENTITY_PIPELINE);
                              RenderSystem.bindDefaultUniforms(renderPass);
                              renderPass.setUniform("DynamicTransforms", dynamicTransforms);
                              renderPass.bindTexture("Sampler0", texture.getTextureView(), texture.getSampler());
                              renderPass.bindTexture("MaskSampler", maskView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                              if (!loggedEntityReplaySuccess) {
                                 BloomMod.LOGGER.debug("Shine entity bloom replaying texture={} strength={}", textureId, strength);
                                 loggedEntityReplaySuccess = true;
                              }

                              drawUploadedMesh(renderPass, uploadedMesh);
                           } catch (Throwable var15) {
                              if (renderPass != null) {
                                 try {
                                    renderPass.close();
                                 } catch (Throwable var14) {
                                    var15.addSuppressed(var14);
                                 }
                              }

                              throw var15;
                           }

                           if (renderPass != null) {
                              renderPass.close();
                           }
                        } catch (RuntimeException e) {
                           if (!loggedEntityReplayFailure) {
                              BloomMod.LOGGER.warn("Shine entity bloom replay failed; entity bloom is skipped for this frame.", e);
                              loggedEntityReplayFailure = true;
                           }
                        }

                     }
                  }
               }
            }
         }
      }
   }

   public static void trackPreparedRenderType(RenderType renderType, PreparedRenderType preparedRenderType) {
      if (!OffscreenRenderTargetGuard.isActive() && renderType != null && preparedRenderType != null) {
         synchronized(PREPARED_RENDER_TYPES) {
            PREPARED_RENDER_TYPES.put(preparedRenderType, renderType);
         }
      }
   }

   public static void replayPreparedRenderType(PreparedRenderType preparedRenderType, GpuBuffer vertexBuffer, GpuBuffer indexBuffer, IndexType indexType, int baseVertex, int firstIndex, int indexCount) {
      if (!OffscreenRenderTargetGuard.isActive() && shine$isBloomSourceAllowed() && preparedThisFrame && terrainDepthCapturedThisFrame && bloomAttachmentView != null && terrainDepthView != null && preparedRenderType != null && vertexBuffer != null && indexBuffer != null && indexType != null) {
         RenderType renderType;
         synchronized(PREPARED_RENDER_TYPES) {
            renderType = (RenderType)PREPARED_RENDER_TYPES.get(preparedRenderType);
         }

         if (renderType != null && BloomRenderTypeUtil.isEntityPipeline(renderType.pipeline())) {
            Identifier textureId = BloomRenderTypeUtil.sampler0Texture(renderType);
            if (BloomEntityTextureCatalog.isEntityTexture(textureId)) {
               BloomEntityTextureCatalog.recordEntityTexture(textureId);
               double strength = BloomSelection.getEntityTextureSourceStrength(textureId.toString());
               if (!(strength <= 1.0E-5)) {
                  GpuTextureView maskView = BloomEntityMaskTextures.getMaskTextureView(textureId);
                  if (maskView != null) {
                     PreparedRenderType.Texture sampler0 = null;

                     for(PreparedRenderType.Texture texture : preparedRenderType.textures()) {
                        if ("Sampler0".equals(texture.name())) {
                           sampler0 = texture;
                           break;
                        }
                     }

                     if (sampler0 != null) {
                        try {
                           CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
                           RenderPass renderPass = encoder.createRenderPass(() -> "shine_entity_bloom_source", bloomAttachmentView, Optional.empty(), terrainDepthView, OptionalDouble.empty());

                           try {
                              renderPass.setPipeline(BLOOM_ENTITY_PIPELINE);
                              RenderSystem.bindDefaultUniforms(renderPass);
                              renderPass.setUniform("DynamicTransforms", preparedRenderType.dynamicTransforms());
                              renderPass.bindTexture("Sampler0", sampler0.textureView(), sampler0.sampler());
                              renderPass.bindTexture("MaskSampler", maskView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
                              if (!loggedEntityReplaySuccess) {
                                 BloomMod.LOGGER.debug("Shine entity bloom replaying prepared texture={} strength={}", textureId, strength);
                                 loggedEntityReplaySuccess = true;
                              }

                              renderPass.setVertexBuffer(0, vertexBuffer.slice());
                              renderPass.setIndexBuffer(indexBuffer, indexType);
                              renderPass.drawIndexed(indexCount, 1, firstIndex, baseVertex, 0);
                           } catch (Throwable var19) {
                              if (renderPass != null) {
                                 try {
                                    renderPass.close();
                                 } catch (Throwable var17) {
                                    var19.addSuppressed(var17);
                                 }
                              }

                              throw var19;
                           }

                           if (renderPass != null) {
                              renderPass.close();
                           }
                        } catch (RuntimeException e) {
                           if (!loggedEntityReplayFailure) {
                              BloomMod.LOGGER.warn("Shine prepared entity bloom replay failed; entity bloom is skipped for this frame.", e);
                              loggedEntityReplayFailure = true;
                           }
                        }

                     }
                  }
               }
            }
         }
      }
   }

   public static void replayParticleFeatureGroup(StagedVertexBuffer stagedBuffer, Map<SingleQuadParticle.Layer, StagedVertexBuffer.Draw> layers, TextureManager textureManager, GpuTextureView lightmapView, GpuBufferSlice dynamicTransforms, boolean translucent) {
      if (shine$isBloomSourceAllowed() && preparedThisFrame && terrainDepthCapturedThisFrame && bloomAttachmentView != null && terrainDepthView != null && stagedBuffer != null && layers != null && !layers.isEmpty() && textureManager != null && dynamicTransforms != null && BloomSelection.hasEnabledParticleSources()) {
         try {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            RenderPass renderPass = encoder.createRenderPass(() -> "shine_particle_bloom_source", bloomAttachmentView, Optional.empty(), terrainDepthView, OptionalDouble.empty());

            try {
               renderPass.setPipeline(BLOOM_PARTICLE_PIPELINE);
               RenderSystem.bindDefaultUniforms(renderPass);
               renderPass.setUniform("DynamicTransforms", dynamicTransforms);
               if (lightmapView != null) {
                  renderPass.bindTexture("Sampler2", lightmapView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
               }

               for(Map.Entry<SingleQuadParticle.Layer, StagedVertexBuffer.Draw> entry : layers.entrySet()) {
                  SingleQuadParticle.Layer layer = (SingleQuadParticle.Layer)entry.getKey();
                  if (layer != null && layer.translucent() == translucent) {
                     StagedVertexBuffer.ExecuteInfo executeInfo = stagedBuffer.getExecuteInfo((StagedVertexBuffer.Draw)entry.getValue());
                     if (executeInfo != null && executeInfo.indexCount() > 0) {
                        AbstractTexture texture = textureManager.getTexture(layer.textureAtlasLocation());
                        if (texture != null) {
                           renderPass.bindTexture("Sampler0", texture.getTextureView(), texture.getSampler());
                           if (!loggedParticleReplaySuccess) {
                              BloomMod.LOGGER.debug("Shine particle bloom replaying layer={} translucent={} indexCount={}", new Object[]{layer.textureAtlasLocation(), translucent, executeInfo.indexCount()});
                              loggedParticleReplaySuccess = true;
                           }

                           renderPass.setVertexBuffer(0, executeInfo.vertexBuffer().slice());
                           renderPass.setIndexBuffer(executeInfo.indexBuffer(), executeInfo.indexType());
                           renderPass.drawIndexed(executeInfo.indexCount(), 1, executeInfo.firstIndex(), executeInfo.baseVertex(), 0);
                        }
                     }
                  }
               }
            } catch (Throwable var14) {
               if (renderPass != null) {
                  try {
                     renderPass.close();
                  } catch (Throwable var13) {
                     var14.addSuppressed(var13);
                  }
               }

               throw var14;
            }

            if (renderPass != null) {
               renderPass.close();
            }
         } catch (RuntimeException e) {
            if (!loggedParticleReplayFailure) {
               BloomMod.LOGGER.warn("Shine particle bloom replay failed; particle bloom is skipped for this frame.", e);
               loggedParticleReplayFailure = true;
            }
         }

      }
   }

   public static int getInternalRenderWidth(int mainWidth) {
      return getScaledDimension(mainWidth);
   }

   public static int getInternalRenderHeight(int mainHeight) {
      return getScaledDimension(mainHeight);
   }

   public static boolean enableBloomDrawBuffers(RenderTarget target) {
      if (ShineRenderBackend.canUseRawOpenGl() && shine$isBloomSourceAllowed() && preparedThisFrame && target != null && bloomAttachmentTexture != null) {
         boolean enabled = OpenGlBloomFramebufferBridge.enable(target, bloomAttachmentTexture);
         if (enabled && !loggedSodiumDrawBuffers) {
            BloomMod.LOGGER.debug("Shine enabled the legacy OpenGL terrain bloom attachment bridge.");
            loggedSodiumDrawBuffers = true;
         }

         return enabled;
      } else {
         return false;
      }
   }

   private static boolean shine$isBloomSourceAllowed() {
      return true;
   }

   public static void disableBloomDrawBuffers(RenderTarget target) {
      if (ShineRenderBackend.canUseRawOpenGl() && target != null) {
         OpenGlBloomFramebufferBridge.disable();
      }

   }

   private static boolean ensureAttachment(int width, int height) {
      GpuDevice device = RenderSystem.getDevice();
      if (bloomAttachmentTexture != null && bloomAttachmentView != null && !bloomAttachmentTexture.isClosed() && !bloomAttachmentView.isClosed() && bloomAttachmentTexture.getWidth(0) == width && bloomAttachmentTexture.getHeight(0) == height) {
         return true;
      } else {
         if (ShineRenderBackend.canUseRawOpenGl()) {
            OpenGlBloomFramebufferBridge.reset();
         }

         if (bloomAttachmentView != null) {
            bloomAttachmentView.close();
            bloomAttachmentView = null;
         }

         if (bloomAttachmentTexture != null) {
            bloomAttachmentTexture.close();
            bloomAttachmentTexture = null;
         }

         try {
            bloomAttachmentTexture = device.createTexture(() -> "Shine bloom attachment", 13, GpuFormat.RGBA8_UNORM, width, height, 1, 1);
            bloomAttachmentView = device.createTextureView(bloomAttachmentTexture);
            loggedAttachmentFailure = false;
            return true;
         } catch (RuntimeException e) {
            if (bloomAttachmentView != null) {
               bloomAttachmentView.close();
               bloomAttachmentView = null;
            }

            if (bloomAttachmentTexture != null) {
               bloomAttachmentTexture.close();
               bloomAttachmentTexture = null;
            }

            if (!loggedAttachmentFailure) {
               BloomMod.LOGGER.warn("Shine could not allocate the bloom source attachment on the {} backend.", ShineRenderBackend.current(), e);
               loggedAttachmentFailure = true;
            }

            return false;
         }
      }
   }

   private static boolean ensureTerrainDepthSnapshot(int width, int height, GpuFormat format) {
      if (terrainDepthTexture != null && terrainDepthTexture.getWidth(0) == width && terrainDepthTexture.getHeight(0) == height && terrainDepthTexture.getFormat() == format) {
         return true;
      } else {
         if (terrainDepthView != null) {
            terrainDepthView.close();
            terrainDepthView = null;
         }

         if (terrainDepthTexture != null) {
            terrainDepthTexture.close();
            terrainDepthTexture = null;
         }

         terrainDepthTexture = RenderSystem.getDevice().createTexture(() -> "Shine terrain depth snapshot", 15, format, width, height, 1, 1);
         terrainDepthView = RenderSystem.getDevice().createTextureView(terrainDepthTexture);
         loggedTerrainDepthFailure = false;
         return true;
      }
   }

   private static boolean ensureOpaqueColorSnapshot(int width, int height, GpuFormat format) {
      if (opaqueColorTexture != null && opaqueColorTexture.getWidth(0) == width && opaqueColorTexture.getHeight(0) == height && opaqueColorTexture.getFormat() == format) {
         return true;
      } else {
         if (opaqueColorView != null) {
            opaqueColorView.close();
            opaqueColorView = null;
         }

         if (opaqueColorTexture != null) {
            opaqueColorTexture.close();
            opaqueColorTexture = null;
         }

         opaqueColorTexture = RenderSystem.getDevice().createTexture(() -> "Shine opaque color snapshot", 13, format, width, height, 1, 1);
         opaqueColorView = RenderSystem.getDevice().createTextureView(opaqueColorTexture);
         loggedOpaqueColorFailure = false;
         return true;
      }
   }

   private static boolean ensureOccluderDepthSnapshot(int width, int height, GpuFormat format) {
      if (occluderDepthTexture != null && occluderDepthTexture.getWidth(0) == width && occluderDepthTexture.getHeight(0) == height && occluderDepthTexture.getFormat() == format) {
         return true;
      } else {
         if (occluderDepthView != null) {
            occluderDepthView.close();
            occluderDepthView = null;
         }

         if (occluderDepthTexture != null) {
            occluderDepthTexture.close();
            occluderDepthTexture = null;
         }

         occluderDepthTexture = RenderSystem.getDevice().createTexture(() -> "Shine occluder depth snapshot", 15, format, width, height, 1, 1);
         occluderDepthView = RenderSystem.getDevice().createTextureView(occluderDepthTexture);
         loggedOccluderDepthFailure = false;
         return true;
      }
   }

   private static boolean clearAttachment() {
      if (bloomAttachmentTexture == null) {
         return false;
      } else {
         try {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.clearColorTexture(bloomAttachmentTexture, new Vector4f(0.0F, 0.0F, 0.0F, 0.0F));
            return true;
         } catch (RuntimeException e) {
            if (!loggedAttachmentFailure) {
               BloomMod.LOGGER.warn("Shine could not clear the bloom source attachment on the {} backend.", ShineRenderBackend.current(), e);
               loggedAttachmentFailure = true;
            }

            return false;
         }
      }
   }

   private static int getScaledDimension(int size) {
      return Math.max(1, Math.round((float)size * 0.5F));
   }

   private static void bindLightmap(RenderPass renderPass) {
      Minecraft minecraft = Minecraft.getInstance();
      GpuTextureView lightmapView = minecraft != null && minecraft.gameRenderer != null ? minecraft.gameRenderer.lightmap() : null;
      if (lightmapView != null) {
         renderPass.bindTexture("Sampler2", lightmapView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
      }

   }

   private static UploadedMesh uploadImmediateMesh(RenderPipeline pipeline, MeshData meshData) {
      MeshData.DrawState drawState = meshData.drawState();
      GpuDevice device = RenderSystem.getDevice();
      GpuBuffer vertexBuffer = device.createBuffer(() -> "Shine immediate vertex buffer", 32, meshData.vertexBuffer().duplicate());
      GpuBuffer indexBuffer;
      IndexType indexType;
      if (meshData.indexBuffer() == null) {
         RenderSystem.AutoStorageIndexBuffer sequentialBuffer = RenderSystem.getSequentialBuffer(drawState.primitiveTopology());
         indexBuffer = sequentialBuffer.getBuffer(drawState.indexCount());
         indexType = sequentialBuffer.type();
      } else {
         indexBuffer = device.createBuffer(() -> "Shine immediate index buffer", 64, meshData.indexBuffer().duplicate());
         indexType = drawState.indexType();
      }

      return new UploadedMesh(vertexBuffer, indexBuffer, indexType, drawState.indexCount());
   }

   private static void drawUploadedMesh(RenderPass renderPass, UploadedMesh mesh) {
      renderPass.setVertexBuffer(0, mesh.vertexBuffer.slice());
      renderPass.setIndexBuffer(mesh.indexBuffer, mesh.indexType);
      renderPass.drawIndexed(mesh.indexCount, 1, 0, 0, 0);
   }

   private static RenderPipeline buildTerrainSourcePipeline(Identifier location, boolean cutout) {
      RenderPipeline.Snippet genericBlocksSnippet = RenderPipeline.builder(new RenderPipeline.Snippet[]{RenderPipelines.GENERIC_BLOCKS_SNIPPET}).withBindGroupLayout(MASK_SAMPLER_LAYOUT).buildSnippet();
      RenderPipeline.Snippet bloomTerrainSnippet = RenderPipeline.builder(new RenderPipeline.Snippet[]{genericBlocksSnippet}).withBindGroupLayout(BindGroupLayouts.PROJECTION).withBindGroupLayout(BindGroupLayouts.CHUNK_SECTION).withVertexShader(TERRAIN_VERTEX_SHADER_ID).withFragmentShader(BLOOM_TERRAIN_SOURCE_FRAGMENT_SHADER_ID).buildSnippet();
      RenderPipeline.Builder builder = RenderPipeline.builder(new RenderPipeline.Snippet[]{bloomTerrainSnippet}).withLocation(location).withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false));
      if (cutout) {
         builder.withShaderDefine("ALPHA_CUTOUT", 0.5F);
      }

      return builder.build();
   }

   private static RenderPipeline buildEntitySourcePipeline() {
      RenderPipeline.Snippet entitySnippet = RenderPipeline.builder(new RenderPipeline.Snippet[]{RenderPipelines.MATRICES_FOG_SNIPPET}).withBindGroupLayout(BindGroupLayouts.SAMPLER0).withBindGroupLayout(MASK_SAMPLER_LAYOUT).withVertexBinding(0, DefaultVertexFormat.ENTITY).withPrimitiveTopology(PrimitiveTopology.QUADS).withVertexShader(ENTITY_SOURCE_SHADER_ID).withFragmentShader(ENTITY_SOURCE_SHADER_ID).buildSnippet();
      return RenderPipeline.builder(new RenderPipeline.Snippet[]{entitySnippet}).withLocation(BLOOM_ENTITY_PIPELINE_ID).withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true)).withCull(false).build();
   }

   private static RenderPipeline buildParticleSourcePipeline() {
      RenderPipeline.Snippet particleSnippet = RenderPipeline.builder(new RenderPipeline.Snippet[]{RenderPipelines.MATRICES_FOG_SNIPPET}).withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER2).withVertexBinding(0, DefaultVertexFormat.PARTICLE).withPrimitiveTopology(PrimitiveTopology.QUADS).withVertexShader(PARTICLE_SOURCE_SHADER_ID).withFragmentShader(PARTICLE_SOURCE_SHADER_ID).buildSnippet();
      return RenderPipeline.builder(new RenderPipeline.Snippet[]{particleSnippet}).withLocation(BLOOM_PARTICLE_PIPELINE_ID).withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true)).withCull(false).build();
   }

   static {
      BLOOM_TERRAIN_SOLID_PIPELINE = buildTerrainSourcePipeline(BLOOM_TERRAIN_SOLID_PIPELINE_ID, false);
      BLOOM_TERRAIN_CUTOUT_PIPELINE = buildTerrainSourcePipeline(BLOOM_TERRAIN_CUTOUT_PIPELINE_ID, true);
      BLOOM_TERRAIN_TRANSLUCENT_PIPELINE = buildTerrainSourcePipeline(Identifier.fromNamespaceAndPath("vybrantvisual", "pipeline/bloom_terrain_translucent"), false);
      BLOOM_ENTITY_PIPELINE = buildEntitySourcePipeline();
      BLOOM_PARTICLE_PIPELINE = buildParticleSourcePipeline();
      PREPARED_RENDER_TYPES = new WeakHashMap();
   }

   private static record UploadedMesh(GpuBuffer vertexBuffer, GpuBuffer indexBuffer, IndexType indexType, int indexCount) {
   }

   private static final class AttachedBloomRenderTarget extends RenderTarget {
      private AttachedBloomRenderTarget() {
         super("Shine Bloom Source", false, GpuFormat.RGBA8_UNORM);
      }

      private void setAttachment(RenderTarget mainTarget, GpuTexture texture, GpuTextureView textureView) {
         this.width = mainTarget.width;
         this.height = mainTarget.height;
         this.colorTexture = texture;
         this.colorTextureView = textureView;
         this.depthTexture = null;
         this.depthTextureView = null;
      }

      public void resize(int width, int height) {
         this.width = width;
         this.height = height;
      }

      public void destroyBuffers() {
      }

      public void createBuffers(int width, int height) {
         this.width = width;
         this.height = height;
      }

      public void copyDepthFrom(RenderTarget renderTarget) {
      }
   }

   private static final class AttachedTerrainDepthRenderTarget extends RenderTarget {
      private AttachedTerrainDepthRenderTarget() {
         super("Shine Terrain Depth", true, GpuFormat.RGBA8_UNORM);
      }

      private void setAttachment(RenderTarget mainTarget, GpuTexture texture, GpuTextureView textureView) {
         this.width = mainTarget.width;
         this.height = mainTarget.height;
         this.colorTexture = null;
         this.colorTextureView = null;
         this.depthTexture = texture;
         this.depthTextureView = textureView;
      }

      public void resize(int width, int height) {
         this.width = width;
         this.height = height;
      }

      public void destroyBuffers() {
      }

      public void createBuffers(int width, int height) {
         this.width = width;
         this.height = height;
      }

      public void copyDepthFrom(RenderTarget renderTarget) {
      }
   }

   private static final class AttachedOpaqueColorRenderTarget extends RenderTarget {
      private AttachedOpaqueColorRenderTarget() {
         super("Shine Opaque Color", false, GpuFormat.RGBA8_UNORM);
      }

      private void setAttachment(RenderTarget mainTarget, GpuTexture texture, GpuTextureView textureView) {
         this.width = mainTarget.width;
         this.height = mainTarget.height;
         this.colorTexture = texture;
         this.colorTextureView = textureView;
         this.depthTexture = null;
         this.depthTextureView = null;
      }

      public void resize(int width, int height) {
         this.width = width;
         this.height = height;
      }

      public void destroyBuffers() {
      }

      public void createBuffers(int width, int height) {
         this.width = width;
         this.height = height;
      }

      public void copyDepthFrom(RenderTarget renderTarget) {
      }
   }

   private static final class AttachedOccluderDepthRenderTarget extends RenderTarget {
      private AttachedOccluderDepthRenderTarget() {
         super("Shine Occluder Depth", true, GpuFormat.RGBA8_UNORM);
      }

      private void setAttachment(RenderTarget mainTarget, GpuTexture texture, GpuTextureView textureView) {
         this.width = mainTarget.width;
         this.height = mainTarget.height;
         this.colorTexture = null;
         this.colorTextureView = null;
         this.depthTexture = texture;
         this.depthTextureView = textureView;
      }

      public void resize(int width, int height) {
         this.width = width;
         this.height = height;
      }

      public void destroyBuffers() {
      }

      public void createBuffers(int width, int height) {
         this.width = width;
         this.height = height;
      }

      public void copyDepthFrom(RenderTarget renderTarget) {
      }
   }
}
