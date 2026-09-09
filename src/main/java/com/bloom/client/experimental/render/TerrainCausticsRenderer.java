package com.bloom.client.experimental.render;

import com.bloom.BloomMod;
import com.bloom.client.experimental.config.ShoreFoamConfig;
import com.bloom.client.experimental.config.ShoreFoamConfigManager;
import com.bloom.client.render.ShineRenderBackend;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL33C;

/**
 * Uploads shore foam edge textures and shader uniforms for both the vanilla
 * and Sodium terrain render paths. Terrain water caustics were removed along
 * with {@code ExperimentalConfig}; only the shore foam feature remains, and
 * it is driven entirely by the standalone {@link ShoreFoamConfig}.
 */
public final class TerrainCausticsRenderer {
   private static final String EDGE_FOAM_VANILLA_SAMPLER = "ShineWaterEdgeFoamSampler";
   private static final String EDGE_FOAM_SODIUM_SAMPLER = "u_ShineWaterEdgeFoamTex";
   private static final String EDGE_FOAM_TEXTURE_RESOURCE = "/assets/shine/textures/effects/water_edge_foam.png";
   private static final int EDGE_FOAM_FALLBACK_WIDTH = 240;
   private static final int EDGE_FOAM_FALLBACK_HEIGHT = 1024;
   private static final int EDGE_FOAM_SODIUM_TEXTURE_UNIT_INDEX = 13;
   private static final int EDGE_FOAM_SODIUM_TEXTURE_UNIT = 33997;
   private static final Identifier WATER_STILL = Identifier.withDefaultNamespace("block/water_still");
   private static final Identifier WATER_FLOW = Identifier.withDefaultNamespace("block/water_flow");
   private static final int SHORE_FOAM_PROFILE_TEXTURE_WIDTH = 128;
   private static final int SHORE_FOAM_PROFILE_ROWS = 3;
   private static final int UPLOAD_TEXTURE_UNIT = 33994;
   private static final Map<Integer, UniformLocations> UNIFORM_LOCATIONS = new HashMap<>();
   private static DynamicTexture edgeFoamTexture;
   private static long uploadedShoreFoamConfigVersion = Long.MIN_VALUE;
   private static long uploadedShoreFoamMappingVersion = Long.MIN_VALUE;
   private static boolean loggedEdgeFoamTextureLoaded;
   private static boolean loggedEdgeFoamTextureFallback;
   private static boolean loggedShoreFoamDiagnostics;
   private static boolean loggedSamplerBindDiagnostics;

   private TerrainCausticsRenderer() {
   }

   public static void bindVanillaTerrainSampler(RenderPass renderPass) {
      if (renderPass != null) {
         DynamicTexture edgeFoam = ensureEdgeFoamTexture();
         if (edgeFoam != null && edgeFoam.getTextureView() != null) {
            renderPass.bindTexture(EDGE_FOAM_VANILLA_SAMPLER, edgeFoam.getTextureView(), RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST));
         }
      }
   }

   public static boolean bindSodiumTerrainSamplers(RenderPass renderPass) {
      if (renderPass == null) {
         return false;
      } else {
         DynamicTexture edgeFoam = edgeFoamTexture;
         if (edgeFoam != null && edgeFoam.getTextureView() != null) {
            renderPass.bindTexture(EDGE_FOAM_SODIUM_SAMPLER, edgeFoam.getTextureView(), RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.REPEAT, FilterMode.NEAREST, FilterMode.NEAREST, false));
            return true;
         } else {
            return false;
         }
      }
   }

   public static void uploadVanillaTerrainUniforms(int programId) {
      if (ShineRenderBackend.canUseRawOpenGl()) {
         uploadUniforms(programId);
      }
   }

   public static void uploadSodiumTerrainUniforms(int programId) {
      if (ShineRenderBackend.canUseRawOpenGl()) {
         uploadUniforms(programId);
         bindSodiumEdgeFoamSampler(programId);
      }
   }
   public static void uploadSodiumRegionOrigin(int programId, int originX, int originY, int originZ) {
      if (programId != 0 && ShineRenderBackend.canUseRawOpenGl()) {
         UniformLocations uniforms = resolveUniformLocations(programId);
         if (uniforms.regionOrigin >= 0) {
            GL20.glUniform3f(uniforms.regionOrigin, (float) originX, (float) originY, (float) originZ);
         }
      }
   }

   public static void prepareSodiumTerrainTextures() {
      ensureEdgeFoamTexture();
      updateShoreFoamProfileTexture(ShoreFoamConfigManager.fastConfig());
   }

   /**
    * GL implementations commonly recycle a deleted program's integer ID for
    * the next linked program. Sodium relinks its terrain shader whenever the
    * source is reloaded (initial load, resource pack swap, render-distance
    * changes that touch the pipeline), so a raw {@code Map#computeIfAbsent}
    * keyed only by that ID can silently hand back uniform locations that
    * belonged to a completely different, now-deleted program - explaining
    * foam/bloom uniforms that work right after loading and then stop.
    */
   private static UniformLocations resolveUniformLocations(int programId) {
      UniformLocations cached = UNIFORM_LOCATIONS.get(programId);
      if (cached != null && GL20.glIsProgram(programId)) {
         return cached;
      } else {
         UniformLocations resolved = new UniformLocations(programId);
         UNIFORM_LOCATIONS.put(programId, resolved);
         return resolved;
      }
   }

   private static void uploadUniforms(int programId) {
      if (programId != 0) {
         ShoreFoamConfig config = ShoreFoamConfigManager.fastConfig();
         if (config == null) {
            config = ShoreFoamConfigManager.defaults();
         }

         updateShoreFoamProfileTexture(config);
         UniformLocations uniforms = resolveUniformLocations(programId);
         uploadWaterSpriteRects(programId);
         ShoreFoamConfig.BiomeProfile shoreFoam = resolveShoreFoamProfile(config);
         boolean shoreFoamEnabled = config.enabled && shoreFoam.enabled && shoreFoam.opacity > 1.0E-5 && shoreFoam.thickness > 1.0E-5;
         if (!loggedShoreFoamDiagnostics) {
            BloomMod.LOGGER.info("Shine shore foam diagnostics: configEnabled={} profileEnabled={} opacity={} thickness={} uniformLoc(enabled={}, opacity={}, thickness={}).", config.enabled, shoreFoam.enabled, shoreFoam.opacity, shoreFoam.thickness, uniforms.shoreFoamEnabled, uniforms.shoreFoamOpacity, uniforms.shoreFoamThickness);
            loggedShoreFoamDiagnostics = true;
         }

         if (uniforms.shoreFoamEnabled >= 0) {
            GL20.glUniform1i(uniforms.shoreFoamEnabled, shoreFoamEnabled ? 1 : 0);
         }

         if (uniforms.shoreFoamOpacity >= 0) {
            GL20.glUniform1f(uniforms.shoreFoamOpacity, (float) shoreFoam.opacity);
         }

         if (uniforms.shoreFoamThickness >= 0) {
            GL20.glUniform1f(uniforms.shoreFoamThickness, (float) shoreFoam.thickness);
         }

         if (uniforms.shoreFoamSpeed >= 0) {
            GL20.glUniform1f(uniforms.shoreFoamSpeed, (float) shoreFoam.speed);
         }

         if (uniforms.shoreFoamScale >= 0) {
            GL20.glUniform1f(uniforms.shoreFoamScale, (float) shoreFoam.scale);
         }

         if (uniforms.shoreFoamBreakup >= 0) {
            GL20.glUniform1f(uniforms.shoreFoamBreakup, (float) shoreFoam.breakup);
         }

         if (uniforms.shoreFoamColor >= 0) {
            int color = shoreFoam.color & 0xFFFFFF;
            GL20.glUniform3f(uniforms.shoreFoamColor, (float) (color >> 16 & 255) / 255.0F, (float) (color >> 8 & 255) / 255.0F, (float) (color & 255) / 255.0F);
         }
      }
   }

   /**
    * The shore foam shader only paints foam on the still-water sprite (see
    * shine_sample_shore_foam's stillWaterFace check), so it needs the UV
    * rect of that sprite within the block atlas every frame. Without this,
    * u_ShineWaterStillUv stays at its GLSL zero-default and no fragment's
    * texcoord can ever fall inside it, silently suppressing all foam.
    *
    * Deliberately queries glGetUniformLocation fresh every call instead of
    * going through the UniformLocations cache: this matches the reference
    * WaterReflectionShaderBridge.uploadWaterSpriteRects, which re-resolves
    * every uniform on every upload rather than caching by program ID.
    */
   private static void uploadWaterSpriteRects(int programId) {
      int stillLocation = GL20.glGetUniformLocation(programId, "u_ShineWaterStillUv");
      int flowLocation = GL20.glGetUniformLocation(programId, "u_ShineWaterFlowUv");
      if (stillLocation >= 0 || flowLocation >= 0) {
         Minecraft minecraft = Minecraft.getInstance();
         TextureAtlas atlas = null;
         if (minecraft != null && minecraft.getTextureManager() != null) {
            AbstractTexture texture = minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
            if (texture instanceof TextureAtlas blockAtlas) {
               atlas = blockAtlas;
            }
         }

         TextureAtlasSprite still = atlas == null ? null : atlas.getSprite(WATER_STILL);
         TextureAtlasSprite flow = atlas == null ? null : atlas.getSprite(WATER_FLOW);
         setSpriteRect(stillLocation, still, atlas);
         setSpriteRect(flowLocation, flow, atlas);
      }
   }

   private static void setSpriteRect(int location, TextureAtlasSprite sprite, TextureAtlas atlas) {
      if (location >= 0) {
         if (sprite != null && atlas != null && sprite != atlas.missingSprite()) {
            GL20.glUniform4f(location, sprite.getU0(), sprite.getV0(), sprite.getU1(), sprite.getV1());
         } else {
            GL20.glUniform4f(location, -2.0F, -2.0F, -1.0F, -1.0F);
         }
      }
   }

   private static ShoreFoamConfig.BiomeProfile resolveShoreFoamProfile(ShoreFoamConfig config) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft != null && minecraft.level != null) {
         Entity camera = minecraft.getCameraEntity();
         if (camera == null) {
            camera = minecraft.player;
         }

         if (camera == null) {
            return config.defaultProfile();
         } else {
            String biomeId = minecraft.level.getBiome(camera.blockPosition()).unwrapKey().map((key) -> key.identifier().toString()).orElse("");
            return config.resolvedProfile(biomeId);
         }
      } else {
         return config.defaultProfile();
      }
   }

   private static void bindSodiumEdgeFoamSampler(int programId) {
      if (ShineRenderBackend.canUseRawOpenGl()) {
         int samplerUniform = GL20.glGetUniformLocation(programId, EDGE_FOAM_SODIUM_SAMPLER);
         int textureId = getEdgeFoamTextureId();
         if (!loggedSamplerBindDiagnostics) {
            BloomMod.LOGGER.info("Shine shore foam sampler diagnostics: programId={} samplerUniformLoc={} textureId={}.", programId, samplerUniform, textureId);
            loggedSamplerBindDiagnostics = true;
         }

         if (samplerUniform >= 0) {
            if (textureId > 0) {
               int previousActiveTexture = GL11.glGetInteger(34016);
               GL13.glActiveTexture(EDGE_FOAM_SODIUM_TEXTURE_UNIT);
               GL11.glBindTexture(3553, textureId);
               GL11.glTexParameteri(3553, 33084, 0);
               GL11.glTexParameteri(3553, 33085, 0);
               GL11.glTexParameteri(3553, 10242, 33071);
               GL11.glTexParameteri(3553, 10243, 10497);
               GL11.glTexParameteri(3553, 10241, 9728);
               GL11.glTexParameteri(3553, 10240, 9728);
               GL33C.glBindSampler(EDGE_FOAM_SODIUM_TEXTURE_UNIT_INDEX, 0);
               GL20.glUniform1i(samplerUniform, EDGE_FOAM_SODIUM_TEXTURE_UNIT_INDEX);
               GL13.glActiveTexture(previousActiveTexture);
            }
         }
      }
   }

   private static int getEdgeFoamTextureId() {
      if (!ShineRenderBackend.canUseRawOpenGl()) {
         return 0;
      } else {
         DynamicTexture texture = edgeFoamTexture;
         if (texture != null) {
            GpuTexture gpuTexture = texture.getTexture();
            if (gpuTexture instanceof GlTexture glTexture) {
               return glTexture.glId();
            }
         }

         return 0;
      }
   }

   private static DynamicTexture ensureEdgeFoamTexture() {
      if (edgeFoamTexture != null) {
         return edgeFoamTexture;
      } else {
         NativeImage image = null;

         try (InputStream stream = TerrainCausticsRenderer.class.getResourceAsStream(EDGE_FOAM_TEXTURE_RESOURCE)) {
            if (stream != null) {
               image = NativeImage.read(stream);
            }
         } catch (IOException exception) {
            BloomMod.LOGGER.warn("Shine could not load water edge foam texture; using a transparent fallback.", exception);
         }

         if (image == null) {
            image = new NativeImage(EDGE_FOAM_FALLBACK_WIDTH, EDGE_FOAM_FALLBACK_HEIGHT, false);
            if (!loggedEdgeFoamTextureFallback) {
               BloomMod.LOGGER.warn("Shine water edge foam texture '{}' was not found; using a transparent fallback.", EDGE_FOAM_TEXTURE_RESOURCE);
               loggedEdgeFoamTextureFallback = true;
            }
         }

         image = appendShoreFoamProfileRows(image);
         edgeFoamTexture = createTextureSafely("Shine water edge foam", image);
         uploadedShoreFoamConfigVersion = Long.MIN_VALUE;
         uploadedShoreFoamMappingVersion = Long.MIN_VALUE;
         if (!loggedEdgeFoamTextureLoaded && !loggedEdgeFoamTextureFallback) {
            BloomMod.LOGGER.debug("Shine water edge foam texture loaded from '{}'.", EDGE_FOAM_TEXTURE_RESOURCE);
            loggedEdgeFoamTextureLoaded = true;
         }

         return edgeFoamTexture;
      }
   }

   private static void updateShoreFoamProfileTexture(ShoreFoamConfig config) {
      if (edgeFoamTexture != null && config != null) {
         long configVersion = ShoreFoamConfigManager.version();
         long mappingVersion = ShoreFoamBiomeProfiles.mappingVersion();
         if (uploadedShoreFoamConfigVersion != configVersion || uploadedShoreFoamMappingVersion != mappingVersion) {
            NativeImage image = edgeFoamTexture.getPixels();
            if (image != null) {
               int profileRow = image.getHeight() - SHORE_FOAM_PROFILE_ROWS;

               for (int profileIndex = 0; profileIndex < SHORE_FOAM_PROFILE_TEXTURE_WIDTH; ++profileIndex) {
                  String biomeId = ShoreFoamBiomeProfiles.biomeId(profileIndex);
                  ShoreFoamConfig.BiomeProfile profile = profileIndex != 0 && !biomeId.isBlank() ? config.resolvedProfile(biomeId) : config.defaultProfile();
                  writeShoreFoamProfile(image, profileIndex, profileRow, config.enabled, profile);
               }

               edgeFoamTexture.upload();
               uploadedShoreFoamConfigVersion = configVersion;
               uploadedShoreFoamMappingVersion = mappingVersion;
            }
         }
      }
   }

   private static void writeShoreFoamProfile(NativeImage image, int profileIndex, int profileRow, boolean masterEnabled, ShoreFoamConfig.BiomeProfile profile) {
      int color = profile.color & 0xFFFFFF;
      int enabled = masterEnabled && profile.enabled && profile.opacity > 1.0E-5 && profile.thickness > 1.0E-5 ? 255 : 0;
      image.setPixel(profileIndex, profileRow, enabled << 24 | color);
      int opacity = normalizedByte(profile.opacity, 0.0, 1.0);
      int thickness = normalizedByte(profile.thickness, 0.01, 0.35);
      int speed = normalizedByte(profile.speed, 0.0, 4.0);
      int scale = normalizedByte(profile.scale, 0.5, 12.0);
      image.setPixel(profileIndex, profileRow + 1, scale << 24 | opacity << 16 | thickness << 8 | speed);
      int breakup = normalizedByte(profile.breakup, 0.0, 1.0);
      int sourceCode = ShoreFoamBiomeProfiles.sourceCode(profileIndex);
      int baseMaterial = ShoreFoamBiomeProfiles.baseMaterial(profileIndex);
      image.setPixel(profileIndex, profileRow + 2, 0xFF000000 | breakup << 16 | sourceCode << 8 | baseMaterial);
   }

   private static NativeImage appendShoreFoamProfileRows(NativeImage source) {
      int width = Math.max(source.getWidth(), SHORE_FOAM_PROFILE_TEXTURE_WIDTH);
      int sourceHeight = source.getHeight();
      NativeImage expanded = new NativeImage(width, sourceHeight + SHORE_FOAM_PROFILE_ROWS, false);

      for (int y = 0; y < sourceHeight; ++y) {
         for (int x = 0; x < source.getWidth(); ++x) {
            expanded.setPixel(x, y, source.getPixel(x, y));
         }
      }

      source.close();
      return expanded;
   }

   private static int normalizedByte(double value, double min, double max) {
      if (max <= min) {
         return 0;
      } else {
         double normalized = Math.max(0.0, Math.min(1.0, (value - min) / (max - min)));
         return Math.max(0, Math.min(255, (int) Math.round(normalized * 255.0)));
      }
   }

   private static DynamicTexture createTextureSafely(String label, NativeImage image) {
      if (!ShineRenderBackend.canUseRawOpenGl()) {
         return new DynamicTexture(() -> label, image);
      } else {
         int previousActiveTexture = GL11.glGetInteger(34016);
         GlStateManager._activeTexture(UPLOAD_TEXTURE_UNIT);
         int previousUploadTexture = GL11.glGetInteger(32873);

         DynamicTexture texture;
         try {
            texture = new DynamicTexture(() -> label, image);
         } finally {
            GlStateManager._bindTexture(previousUploadTexture);
            GlStateManager._activeTexture(previousActiveTexture);
         }

         return texture;
      }
   }

   private static record UniformLocations(int regionOrigin, int shoreFoamEnabled, int shoreFoamOpacity, int shoreFoamThickness, int shoreFoamSpeed, int shoreFoamScale, int shoreFoamBreakup, int shoreFoamColor) {
      private UniformLocations(int programId) {
         this(
            GL20.glGetUniformLocation(programId, "u_ShineTerrainRegionOrigin"),
            GL20.glGetUniformLocation(programId, "u_ShineShoreFoamEnabled"),
            GL20.glGetUniformLocation(programId, "u_ShineShoreFoamOpacity"),
            GL20.glGetUniformLocation(programId, "u_ShineShoreFoamThickness"),
            GL20.glGetUniformLocation(programId, "u_ShineShoreFoamSpeed"),
            GL20.glGetUniformLocation(programId, "u_ShineShoreFoamScale"),
            GL20.glGetUniformLocation(programId, "u_ShineShoreFoamBreakup"),
            GL20.glGetUniformLocation(programId, "u_ShineShoreFoamColor")
         );
      }
   }
}
