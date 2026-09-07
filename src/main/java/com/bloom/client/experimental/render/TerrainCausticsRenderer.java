package com.bloom.client.experimental.render;

import com.bloom.BloomMod;
import com.bloom.client.experimental.config.ExperimentalConfig;
import com.bloom.client.experimental.config.ExperimentalConfigManager;
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
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL33C;

public final class TerrainCausticsRenderer {
   private static final String VANILLA_SAMPLER = "ShineWaterCausticsSampler";
   private static final String SODIUM_SAMPLER = "u_ShineCausticsTex";
   private static final String TEXTURE_RESOURCE = "/assets/shine/textures/effects/water_caustics.png";
   private static final String EDGE_FOAM_VANILLA_SAMPLER = "ShineWaterEdgeFoamSampler";
   private static final String EDGE_FOAM_SODIUM_SAMPLER = "u_ShineWaterEdgeFoamTex";
   private static final String EDGE_FOAM_TEXTURE_RESOURCE = "/assets/shine/textures/effects/water_edge_foam.png";
   private static final int EDGE_FOAM_FALLBACK_WIDTH = 240;
   private static final int EDGE_FOAM_FALLBACK_HEIGHT = 1024;
   private static final int EDGE_FOAM_SODIUM_TEXTURE_UNIT_INDEX = 13;
   private static final int EDGE_FOAM_SODIUM_TEXTURE_UNIT = 33997;
   private static final int SODIUM_TEXTURE_UNIT_INDEX = 14;
   private static final int SODIUM_TEXTURE_UNIT = 33998;
   private static final int SHORE_FOAM_PROFILE_TEXTURE_WIDTH = 128;
   private static final int SHORE_FOAM_PROFILE_ROWS = 3;
   private static final int UPLOAD_TEXTURE_UNIT = 33994;
   private static final Map<Integer, UniformLocations> UNIFORM_LOCATIONS = new HashMap();
   private static DynamicTexture causticsTexture;
   private static DynamicTexture edgeFoamTexture;
   private static long uploadedShoreFoamConfigVersion = Long.MIN_VALUE;
   private static long uploadedShoreFoamMappingVersion = Long.MIN_VALUE;
   private static boolean loggedTextureLoaded;
   private static boolean loggedTextureFallback;
   private static boolean loggedEdgeFoamTextureLoaded;
   private static boolean loggedEdgeFoamTextureFallback;

   private TerrainCausticsRenderer() {
   }

   public static void bindVanillaTerrainSampler(RenderPass renderPass) {
      if (renderPass != null) {
         DynamicTexture texture = ensureTexture();
         if (texture != null && texture.getTextureView() != null) {
            renderPass.bindTexture("ShineWaterCausticsSampler", texture.getTextureView(), RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST));
            DynamicTexture edgeFoam = ensureEdgeFoamTexture();
            if (edgeFoam != null && edgeFoam.getTextureView() != null) {
               renderPass.bindTexture("ShineWaterEdgeFoamSampler", edgeFoam.getTextureView(), RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST));
            }
         }
      }
   }

   public static boolean bindSodiumTerrainSamplers(RenderPass renderPass) {
      if (renderPass == null) {
         return false;
      } else {
         DynamicTexture texture = causticsTexture;
         DynamicTexture edgeFoam = edgeFoamTexture;
         if (texture != null && texture.getTextureView() != null && edgeFoam != null && edgeFoam.getTextureView() != null) {
            renderPass.bindTexture("u_ShineCausticsTex", texture.getTextureView(), RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST));
            renderPass.bindTexture("u_ShineWaterEdgeFoamTex", edgeFoam.getTextureView(), RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.REPEAT, FilterMode.NEAREST, FilterMode.NEAREST, false));
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
         bindSodiumSampler(programId);
         bindSodiumEdgeFoamSampler(programId);
      }
   }

   public static void uploadSodiumRegionOrigin(int programId, int originX, int originY, int originZ) {
      if (programId != 0 && ShineRenderBackend.canUseRawOpenGl()) {
         UniformLocations uniforms = (UniformLocations)UNIFORM_LOCATIONS.computeIfAbsent(programId, UniformLocations::new);
         if (uniforms.regionOrigin >= 0) {
            GL20.glUniform3f(uniforms.regionOrigin, (float)originX, (float)originY, (float)originZ);
         }

      }
   }

   public static void prepareSodiumTerrainTextures() {
      ensureTexture();
      ensureEdgeFoamTexture();
      updateShoreFoamProfileTexture(ExperimentalConfigManager.fastConfig());
   }

   public static void populateVulkanUniforms(TerrainWaterUniformBuffer.UniformWriter writer) {
      if (writer != null) {
         ExperimentalConfig config = ExperimentalConfigManager.fastConfig();
         if (config == null) {
            config = ExperimentalConfig.defaults();
         }

         updateShoreFoamProfileTexture(config);
         ExperimentalConfig.ShoreFoamBiomeProfile shoreFoam = resolveShoreFoamProfile(config);
         boolean enabled = config.enabled && config.terrainCausticsEnabled && config.terrainCausticsStrength > 1.0E-5 && config.terrainCausticsScale > 1.0E-5 && config.terrainCausticsMaxY >= config.terrainCausticsMinY;
         boolean shoreFoamEnabled = config.enabled && shoreFoam.enabled && shoreFoam.opacity > 1.0E-5 && shoreFoam.thickness > 1.0E-5;
         Minecraft minecraft = Minecraft.getInstance();
         float dayFactor = minecraft != null && minecraft.level != null ? ExperimentalPostProcessor.computeDayFactor(minecraft, config) : 1.0F;
         Vec3 camera = currentCameraPosition();
         int color = shoreFoam.color & 16777215;
         writer.setInt("u_ShineTerrainCausticsEnabled", enabled ? 1 : 0);
         writer.setInt("u_ShineTerrainCausticsTopFacesOnly", config.terrainCausticsTopFacesOnly ? 1 : 0);
         writer.setInt("u_ShineTerrainCausticsRequireSunlight", config.terrainCausticsRequireSunlight ? 1 : 0);
         writer.setFloat("u_ShineTerrainCausticsSunlightThreshold", (float)config.terrainCausticsSunlightThreshold);
         writer.setFloat("u_ShineTerrainCausticsShadeFade", (float)config.terrainCausticsShadeFade);
         writer.setFloat("u_ShineTerrainCausticsDayFactor", dayFactor);
         writer.setFloat("u_ShineTerrainCausticsStrength", (float)config.terrainCausticsStrength);
         writer.setFloat("u_ShineTerrainCausticsScale", (float)config.terrainCausticsScale);
         writer.setFloat("u_ShineTerrainCausticsSpeed", (float)config.terrainCausticsSpeed);
         writer.setFloat("u_ShineTerrainCausticsMinY", (float)config.terrainCausticsMinY);
         writer.setFloat("u_ShineTerrainCausticsMaxY", (float)config.terrainCausticsMaxY);
         writer.setFloat("u_ShineTerrainCausticsTime", (float)((double)System.nanoTime() * 1.0E-9));
         writer.setVec3("u_ShineTerrainCausticsCameraPos", camera == null ? 0.0F : (float)camera.x, camera == null ? 0.0F : (float)camera.y, camera == null ? 0.0F : (float)camera.z);
         writer.setInt("u_ShineShoreFoamEnabled", shoreFoamEnabled ? 1 : 0);
         writer.setFloat("u_ShineShoreFoamOpacity", (float)shoreFoam.opacity);
         writer.setFloat("u_ShineShoreFoamThickness", (float)shoreFoam.thickness);
         writer.setFloat("u_ShineShoreFoamSpeed", (float)shoreFoam.speed);
         writer.setFloat("u_ShineShoreFoamScale", (float)shoreFoam.scale);
         writer.setFloat("u_ShineShoreFoamBreakup", (float)shoreFoam.breakup);
         writer.setVec3("u_ShineShoreFoamColor", (float)(color >> 16 & 255) / 255.0F, (float)(color >> 8 & 255) / 255.0F, (float)(color & 255) / 255.0F);
      }
   }

   private static void uploadUniforms(int programId) {
      if (programId != 0) {
         ExperimentalConfig config = ExperimentalConfigManager.fastConfig();
         if (config == null) {
            config = ExperimentalConfig.defaults();
         }

         updateShoreFoamProfileTexture(config);
         UniformLocations uniforms = (UniformLocations)UNIFORM_LOCATIONS.computeIfAbsent(programId, UniformLocations::new);
         ExperimentalConfig.ShoreFoamBiomeProfile shoreFoam = resolveShoreFoamProfile(config);
         boolean enabled = config.enabled && config.terrainCausticsEnabled && config.terrainCausticsStrength > 1.0E-5 && config.terrainCausticsScale > 1.0E-5 && config.terrainCausticsMaxY >= config.terrainCausticsMinY;
         boolean shoreFoamEnabled = config.enabled && shoreFoam.enabled && shoreFoam.opacity > 1.0E-5 && shoreFoam.thickness > 1.0E-5;
         if (uniforms.enabled >= 0) {
            GL20.glUniform1i(uniforms.enabled, enabled ? 1 : 0);
         }

         if (uniforms.topFacesOnly >= 0) {
            GL20.glUniform1i(uniforms.topFacesOnly, config.terrainCausticsTopFacesOnly ? 1 : 0);
         }

         if (uniforms.requireSunlight >= 0) {
            GL20.glUniform1i(uniforms.requireSunlight, config.terrainCausticsRequireSunlight ? 1 : 0);
         }

         if (uniforms.sunlightThreshold >= 0) {
            GL20.glUniform1f(uniforms.sunlightThreshold, (float)config.terrainCausticsSunlightThreshold);
         }

         if (uniforms.shadeFade >= 0) {
            GL20.glUniform1f(uniforms.shadeFade, (float)config.terrainCausticsShadeFade);
         }

         if (uniforms.dayFactor >= 0) {
            Minecraft minecraft = Minecraft.getInstance();
            float dayFactor = minecraft != null && minecraft.level != null ? ExperimentalPostProcessor.computeDayFactor(minecraft, config) : 1.0F;
            GL20.glUniform1f(uniforms.dayFactor, dayFactor);
         }

         if (uniforms.strength >= 0) {
            GL20.glUniform1f(uniforms.strength, (float)config.terrainCausticsStrength);
         }

         if (uniforms.scale >= 0) {
            GL20.glUniform1f(uniforms.scale, (float)config.terrainCausticsScale);
         }

         if (uniforms.speed >= 0) {
            GL20.glUniform1f(uniforms.speed, (float)config.terrainCausticsSpeed);
         }

         if (uniforms.minY >= 0) {
            GL20.glUniform1f(uniforms.minY, (float)config.terrainCausticsMinY);
         }

         if (uniforms.maxY >= 0) {
            GL20.glUniform1f(uniforms.maxY, (float)config.terrainCausticsMaxY);
         }

         if (uniforms.time >= 0) {
            GL20.glUniform1f(uniforms.time, (float)((double)System.nanoTime() * 1.0E-9));
         }

         if (uniforms.cameraPos >= 0) {
            Vec3 camera = currentCameraPosition();
            GL20.glUniform3f(uniforms.cameraPos, camera == null ? 0.0F : (float)camera.x, camera == null ? 0.0F : (float)camera.y, camera == null ? 0.0F : (float)camera.z);
         }

         if (uniforms.shoreFoamEnabled >= 0) {
            GL20.glUniform1i(uniforms.shoreFoamEnabled, shoreFoamEnabled ? 1 : 0);
         }

         if (uniforms.shoreFoamOpacity >= 0) {
            GL20.glUniform1f(uniforms.shoreFoamOpacity, (float)shoreFoam.opacity);
         }

         if (uniforms.shoreFoamThickness >= 0) {
            GL20.glUniform1f(uniforms.shoreFoamThickness, (float)shoreFoam.thickness);
         }

         if (uniforms.shoreFoamSpeed >= 0) {
            GL20.glUniform1f(uniforms.shoreFoamSpeed, (float)shoreFoam.speed);
         }

         if (uniforms.shoreFoamScale >= 0) {
            GL20.glUniform1f(uniforms.shoreFoamScale, (float)shoreFoam.scale);
         }

         if (uniforms.shoreFoamBreakup >= 0) {
            GL20.glUniform1f(uniforms.shoreFoamBreakup, (float)shoreFoam.breakup);
         }

         if (uniforms.shoreFoamColor >= 0) {
            int color = shoreFoam.color & 16777215;
            GL20.glUniform3f(uniforms.shoreFoamColor, (float)(color >> 16 & 255) / 255.0F, (float)(color >> 8 & 255) / 255.0F, (float)(color & 255) / 255.0F);
         }

      }
   }

   private static ExperimentalConfig.ShoreFoamBiomeProfile resolveShoreFoamProfile(ExperimentalConfig config) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft != null && minecraft.level != null) {
         Entity camera = minecraft.getCameraEntity();
         if (camera == null) {
            camera = minecraft.player;
         }

         if (camera == null) {
            return config.defaultShoreFoamProfile();
         } else {
            String biomeId = (String)minecraft.level.getBiome(camera.blockPosition()).unwrapKey().map((key) -> key.identifier().toString()).orElse("");
            return config.resolvedShoreFoamProfile(biomeId);
         }
      } else {
         return config.defaultShoreFoamProfile();
      }
   }

   private static void bindSodiumSampler(int programId) {
      if (ShineRenderBackend.canUseRawOpenGl()) {
         int samplerUniform = GL20.glGetUniformLocation(programId, "u_ShineCausticsTex");
         if (samplerUniform >= 0) {
            int textureId = getCausticsTextureId();
            if (textureId > 0) {
               int previousActiveTexture = GL11.glGetInteger(34016);
               GL13.glActiveTexture(33998);
               GL11.glBindTexture(3553, textureId);
               GL11.glTexParameteri(3553, 33084, 0);
               GL11.glTexParameteri(3553, 33085, 0);
               GL11.glTexParameteri(3553, 10242, 10497);
               GL11.glTexParameteri(3553, 10243, 10497);
               GL11.glTexParameteri(3553, 10241, 9728);
               GL11.glTexParameteri(3553, 10240, 9728);
               GL33C.glBindSampler(14, 0);
               GL20.glUniform1i(samplerUniform, 14);
               GL13.glActiveTexture(previousActiveTexture);
            }
         }
      }
   }

   private static void bindSodiumEdgeFoamSampler(int programId) {
      if (ShineRenderBackend.canUseRawOpenGl()) {
         int samplerUniform = GL20.glGetUniformLocation(programId, "u_ShineWaterEdgeFoamTex");
         if (samplerUniform >= 0) {
            int textureId = getEdgeFoamTextureId();
            if (textureId > 0) {
               int previousActiveTexture = GL11.glGetInteger(34016);
               GL13.glActiveTexture(33997);
               GL11.glBindTexture(3553, textureId);
               GL11.glTexParameteri(3553, 33084, 0);
               GL11.glTexParameteri(3553, 33085, 0);
               GL11.glTexParameteri(3553, 10242, 33071);
               GL11.glTexParameteri(3553, 10243, 10497);
               GL11.glTexParameteri(3553, 10241, 9728);
               GL11.glTexParameteri(3553, 10240, 9728);
               GL33C.glBindSampler(13, 0);
               GL20.glUniform1i(samplerUniform, 13);
               GL13.glActiveTexture(previousActiveTexture);
            }
         }
      }
   }

   private static int getCausticsTextureId() {
      if (!ShineRenderBackend.canUseRawOpenGl()) {
         return 0;
      } else {
         DynamicTexture texture = causticsTexture;
         if (texture != null) {
            GpuTexture var2 = texture.getTexture();
            if (var2 instanceof GlTexture) {
               GlTexture glTexture = (GlTexture)var2;
               return glTexture.glId();
            }
         }

         return 0;
      }
   }

   private static int getEdgeFoamTextureId() {
      if (!ShineRenderBackend.canUseRawOpenGl()) {
         return 0;
      } else {
         DynamicTexture texture = edgeFoamTexture;
         if (texture != null) {
            GpuTexture var2 = texture.getTexture();
            if (var2 instanceof GlTexture) {
               GlTexture glTexture = (GlTexture)var2;
               return glTexture.glId();
            }
         }

         return 0;
      }
   }

   private static DynamicTexture ensureTexture() {
      if (causticsTexture != null) {
         return causticsTexture;
      } else {
         NativeImage image = null;

         try {
            InputStream stream = TerrainCausticsRenderer.class.getResourceAsStream("/assets/shine/textures/effects/water_caustics.png");

            try {
               if (stream != null) {
                  image = NativeImage.read(stream);
               }
            } catch (Throwable var5) {
               if (stream != null) {
                  try {
                     stream.close();
                  } catch (Throwable var4) {
                     var5.addSuppressed(var4);
                  }
               }

               throw var5;
            }

            if (stream != null) {
               stream.close();
            }
         } catch (IOException exception) {
            BloomMod.LOGGER.warn("Shine could not load water caustics texture; using a transparent fallback.", exception);
         }

         if (image == null) {
            image = new NativeImage(16, 1024, false);
            if (!loggedTextureFallback) {
               BloomMod.LOGGER.warn("Shine water caustics texture '{}' was not found; using a transparent fallback.", "/assets/shine/textures/effects/water_caustics.png");
               loggedTextureFallback = true;
            }
         }

         causticsTexture = createTextureSafely("Shine water caustics", image);
         if (!loggedTextureLoaded && !loggedTextureFallback) {
            BloomMod.LOGGER.debug("Shine water caustics texture loaded from '{}'.", "/assets/shine/textures/effects/water_caustics.png");
            loggedTextureLoaded = true;
         }

         return causticsTexture;
      }
   }

   private static DynamicTexture ensureEdgeFoamTexture() {
      if (edgeFoamTexture != null) {
         return edgeFoamTexture;
      } else {
         NativeImage image = null;

         try {
            InputStream stream = TerrainCausticsRenderer.class.getResourceAsStream("/assets/shine/textures/effects/water_edge_foam.png");

            try {
               if (stream != null) {
                  image = NativeImage.read(stream);
               }
            } catch (Throwable var5) {
               if (stream != null) {
                  try {
                     stream.close();
                  } catch (Throwable var4) {
                     var5.addSuppressed(var4);
                  }
               }

               throw var5;
            }

            if (stream != null) {
               stream.close();
            }
         } catch (IOException exception) {
            BloomMod.LOGGER.warn("Shine could not load water edge foam texture; using a transparent fallback.", exception);
         }

         if (image == null) {
            image = new NativeImage(240, 1024, false);
            if (!loggedEdgeFoamTextureFallback) {
               BloomMod.LOGGER.warn("Shine water edge foam texture '{}' was not found; using a transparent fallback.", "/assets/shine/textures/effects/water_edge_foam.png");
               loggedEdgeFoamTextureFallback = true;
            }
         }

         image = appendShoreFoamProfileRows(image);
         edgeFoamTexture = createTextureSafely("Shine water edge foam", image);
         uploadedShoreFoamConfigVersion = Long.MIN_VALUE;
         uploadedShoreFoamMappingVersion = Long.MIN_VALUE;
         if (!loggedEdgeFoamTextureLoaded && !loggedEdgeFoamTextureFallback) {
            BloomMod.LOGGER.debug("Shine water edge foam texture loaded from '{}'.", "/assets/shine/textures/effects/water_edge_foam.png");
            loggedEdgeFoamTextureLoaded = true;
         }

         return edgeFoamTexture;
      }
   }

   private static void updateShoreFoamProfileTexture(ExperimentalConfig config) {
      if (edgeFoamTexture != null && config != null) {
         long configVersion = ExperimentalConfigManager.version();
         long mappingVersion = ShoreFoamBiomeProfiles.mappingVersion();
         if (uploadedShoreFoamConfigVersion != configVersion || uploadedShoreFoamMappingVersion != mappingVersion) {
            NativeImage image = edgeFoamTexture.getPixels();
            if (image != null) {
               int profileRow = image.getHeight() - 3;

               for(int profileIndex = 0; profileIndex < 128; ++profileIndex) {
                  String biomeId = ShoreFoamBiomeProfiles.biomeId(profileIndex);
                  ExperimentalConfig.ShoreFoamBiomeProfile profile = profileIndex != 0 && !biomeId.isBlank() ? config.resolvedShoreFoamProfile(biomeId) : config.defaultShoreFoamProfile();
                  writeShoreFoamProfile(image, profileIndex, profileRow, config.enabled, profile);
               }

               edgeFoamTexture.upload();
               uploadedShoreFoamConfigVersion = configVersion;
               uploadedShoreFoamMappingVersion = mappingVersion;
            }
         }
      }
   }

   private static void writeShoreFoamProfile(NativeImage image, int profileIndex, int profileRow, boolean masterEnabled, ExperimentalConfig.ShoreFoamBiomeProfile profile) {
      int color = profile.color & 16777215;
      int enabled = masterEnabled && profile.enabled && profile.opacity > 1.0E-5 && profile.thickness > 1.0E-5 ? 255 : 0;
      image.setPixel(profileIndex, profileRow, enabled << 24 | color);
      int opacity = normalizedByte(profile.opacity, (double)0.0F, (double)1.0F);
      int thickness = normalizedByte(profile.thickness, 0.01, 0.35);
      int speed = normalizedByte(profile.speed, (double)0.0F, (double)4.0F);
      int scale = normalizedByte(profile.scale, (double)0.5F, (double)12.0F);
      image.setPixel(profileIndex, profileRow + 1, scale << 24 | opacity << 16 | thickness << 8 | speed);
      int breakup = normalizedByte(profile.breakup, (double)0.0F, (double)1.0F);
      int sourceCode = ShoreFoamBiomeProfiles.sourceCode(profileIndex);
      int baseMaterial = ShoreFoamBiomeProfiles.baseMaterial(profileIndex);
      image.setPixel(profileIndex, profileRow + 2, -16777216 | breakup << 16 | sourceCode << 8 | baseMaterial);
   }

   private static NativeImage appendShoreFoamProfileRows(NativeImage source) {
      int width = Math.max(source.getWidth(), 128);
      int sourceHeight = source.getHeight();
      NativeImage expanded = new NativeImage(width, sourceHeight + 3, false);

      for(int y = 0; y < sourceHeight; ++y) {
         for(int x = 0; x < source.getWidth(); ++x) {
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
         double normalized = Math.max((double)0.0F, Math.min((double)1.0F, (value - min) / (max - min)));
         return Math.max(0, Math.min(255, (int)Math.round(normalized * (double)255.0F)));
      }
   }

   private static DynamicTexture createTextureSafely(String label, NativeImage image) {
      if (!ShineRenderBackend.canUseRawOpenGl()) {
         return new DynamicTexture(() -> label, image);
      } else {
         int previousActiveTexture = GL11.glGetInteger(34016);
         GlStateManager._activeTexture(33994);
         int previousUploadTexture = GL11.glGetInteger(32873);

         DynamicTexture var4;
         try {
            var4 = new DynamicTexture(() -> label, image);
         } finally {
            GlStateManager._bindTexture(previousUploadTexture);
            GlStateManager._activeTexture(previousActiveTexture);
         }

         return var4;
      }
   }

   private static Vec3 currentCameraPosition() {
      Minecraft minecraft = Minecraft.getInstance();
      return minecraft != null && minecraft.gameRenderer != null && minecraft.gameRenderer.mainCamera() != null ? minecraft.gameRenderer.mainCamera().position() : null;
   }

   private static record UniformLocations(int enabled, int topFacesOnly, int requireSunlight, int sunlightThreshold, int shadeFade, int dayFactor, int strength, int scale, int speed, int minY, int maxY, int time, int cameraPos, int regionOrigin, int shoreFoamEnabled, int shoreFoamOpacity, int shoreFoamThickness, int shoreFoamSpeed, int shoreFoamScale, int shoreFoamBreakup, int shoreFoamColor) {
      private UniformLocations(int programId) {
         this(GL20.glGetUniformLocation(programId, "u_ShineTerrainCausticsEnabled"), GL20.glGetUniformLocation(programId, "u_ShineTerrainCausticsTopFacesOnly"), GL20.glGetUniformLocation(programId, "u_ShineTerrainCausticsRequireSunlight"), GL20.glGetUniformLocation(programId, "u_ShineTerrainCausticsSunlightThreshold"), GL20.glGetUniformLocation(programId, "u_ShineTerrainCausticsShadeFade"), GL20.glGetUniformLocation(programId, "u_ShineTerrainCausticsDayFactor"), GL20.glGetUniformLocation(programId, "u_ShineTerrainCausticsStrength"), GL20.glGetUniformLocation(programId, "u_ShineTerrainCausticsScale"), GL20.glGetUniformLocation(programId, "u_ShineTerrainCausticsSpeed"), GL20.glGetUniformLocation(programId, "u_ShineTerrainCausticsMinY"), GL20.glGetUniformLocation(programId, "u_ShineTerrainCausticsMaxY"), GL20.glGetUniformLocation(programId, "u_ShineTerrainCausticsTime"), GL20.glGetUniformLocation(programId, "u_ShineTerrainCausticsCameraPos"), GL20.glGetUniformLocation(programId, "u_ShineTerrainRegionOrigin"), GL20.glGetUniformLocation(programId, "u_ShineShoreFoamEnabled"), GL20.glGetUniformLocation(programId, "u_ShineShoreFoamOpacity"), GL20.glGetUniformLocation(programId, "u_ShineShoreFoamThickness"), GL20.glGetUniformLocation(programId, "u_ShineShoreFoamSpeed"), GL20.glGetUniformLocation(programId, "u_ShineShoreFoamScale"), GL20.glGetUniformLocation(programId, "u_ShineShoreFoamBreakup"), GL20.glGetUniformLocation(programId, "u_ShineShoreFoamColor"));
      }
   }
}
