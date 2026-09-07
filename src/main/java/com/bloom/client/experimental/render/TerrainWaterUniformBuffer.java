package com.bloom.client.experimental.render;

import com.bloom.BloomMod;
import com.bloom.client.experimental.render.sodium.WaterReflectionShaderBridge;
import com.bloom.client.render.ShineRenderBackend;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderPass;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.DynamicUniformStorage;
import org.joml.Matrix4f;

public final class TerrainWaterUniformBuffer {
   public static final String UNIFORM_BLOCK_NAME = "ShineWater";
   public static final int STD140_CAPACITY = 1024;
   public static final String GLSL_BLOCK_DECLARATION = "layout(std140) uniform ShineWater {\n    int u_ShineSkyLightCapture;\n    int u_ShineTerrainCaptureMode;\n    int u_ShineWaterReflectionCapture;\n    int u_ShineWaterReflectionPass;\n    int u_ShineWaterReflectionsEnabled;\n    int u_ShineCloudTextureAvailable;\n    int u_ShineDimensionHasSky;\n    float u_ShineCloudTickTime;\n    vec2 u_ShineCloudUpperSeedOffset;\n    vec2 u_ShineCloudHighSeedOffset;\n    float u_ShineCloudVeilSeed;\n    vec4 u_ShineCloudMain;\n    vec4 u_ShineCloudUpper;\n    vec4 u_ShineCloudHigh;\n    vec4 u_ShineCloudLayerEnabled;\n    vec4 u_ShineCloudVeil;\n    vec4 u_ShineCloudVeilShape;\n    vec4 u_ShineWaterStillUv;\n    vec4 u_ShineWaterFlowUv;\n    mat4 u_ShineWaterReflectionViewProjection;\n    vec3 u_ShineWaterReflectionCameraPos;\n    vec3 u_ShineWaterCurrentCameraPos;\n    vec2 u_ShineWaterViewportSize;\n    float u_ShineWaterReflectionPlaneY;\n    float u_ShineWaterReflectionTerrainFade;\n    float u_ShineWaterReflectionRange;\n    float u_ShineWaterReflectionStrength;\n    float u_ShineWaterReflectionTerrainStrength;\n    float u_ShineWaterReflectionSkyStrength;\n    float u_ShineWaterReflectionGrazingBoost;\n    float u_ShineWaterReflectionPixelResolution;\n    float u_ShineWaterReflectionMotionStrength;\n    float u_ShineWaterReflectionMotionScale;\n    float u_ShineWaterReflectionMotionSpeed;\n    float u_ShineWaterReflectionMotionDirection;\n    float u_ShineWaterReflectionWakeInfluence;\n    float u_ShineWaterReflectionFoamSuppression;\n    float u_ShineWaterReflectionHeightTolerance;\n    float u_ShineWaterReflectionTime;\n    float u_ShineWaterReflectionDayCycle;\n    float u_ShineWaterReflectionRain;\n    float u_ShineWaterReflectionThunder;\n    int u_ShineWaterReflectionStyle;\n    float u_ShineWaterReflectionColorSaturation;\n    float u_ShineWaterReflectionContrast;\n    float u_ShineWaterReflectionSurfaceRoughness;\n    float u_ShineWaterReflectionRippleStretch;\n    float u_ShineWaterReflectionSunGlintStrength;\n    float u_ShineWaterReflectionSunGlintSharpness;\n    float u_ShineWaterReflectionDepthAbsorption;\n    float u_ShineWaterReflectionShallowClarity;\n    float u_ShineWaterReflectionBodySaturation;\n    float u_ShineWaterReflectionBodyDarkness;\n    int u_ShineWaterOpaqueDepthValid;\n    float u_ShineWaterNearPlane;\n    float u_ShineWaterFarPlane;\n    vec3 u_ShineSkyColor;\n    vec4 u_ShineCloudColor;\n    float u_ShineSunAngle;\n    float u_ShineMoonAngle;\n    float u_ShineStarBrightness;\n    float u_ShineMoonPhase;\n    int u_ShineWaterReflectionCloudsEnabled;\n    int u_ShineTerrainCausticsEnabled;\n    int u_ShineTerrainCausticsTopFacesOnly;\n    int u_ShineTerrainCausticsRequireSunlight;\n    float u_ShineTerrainCausticsSunlightThreshold;\n    float u_ShineTerrainCausticsShadeFade;\n    float u_ShineTerrainCausticsDayFactor;\n    float u_ShineTerrainCausticsStrength;\n    float u_ShineTerrainCausticsScale;\n    float u_ShineTerrainCausticsSpeed;\n    float u_ShineTerrainCausticsMinY;\n    float u_ShineTerrainCausticsMaxY;\n    float u_ShineTerrainCausticsTime;\n    int u_ShineShoreFoamEnabled;\n    float u_ShineShoreFoamOpacity;\n    float u_ShineShoreFoamThickness;\n    float u_ShineShoreFoamSpeed;\n    float u_ShineShoreFoamScale;\n    float u_ShineShoreFoamBreakup;\n    vec3 u_ShineShoreFoamColor;\n    vec3 u_ShineTerrainCausticsCameraPos;\n    vec3 u_ShineTerrainRegionOrigin;\n};\n";
   private static DynamicUniformStorage<TerrainUniform> storage;
   private static UniformValues activePass;
   private static TerrainCaptureState.Mode activeCaptureMode;
   private static boolean warnedWriteFailure;

   private TerrainWaterUniformBuffer() {
   }

   public static boolean beginTerrainPass(boolean translucentPass, TerrainCaptureState.Mode captureMode) {
      if (!ShineRenderBackend.isVulkan()) {
         activePass = null;
         activeCaptureMode = TerrainCaptureState.Mode.NONE;
         return false;
      } else {
         TerrainCausticsRenderer.prepareSodiumTerrainTextures();
         Collector collector = new Collector();
         TerrainCausticsRenderer.populateVulkanUniforms(collector);
         activeCaptureMode = captureMode == null ? TerrainCaptureState.Mode.NONE : captureMode;
         WaterReflectionShaderBridge.populateTerrainUniforms(collector, translucentPass, activeCaptureMode);
         activePass = collector.freeze();
         return true;
      }
   }

   public static boolean bindForRegion(RenderPass renderPass, int originX, int originY, int originZ) {
      if (renderPass != null && activePass != null && ShineRenderBackend.isVulkan()) {
         try {
            GpuBufferSlice slice = storage().writeUniform(new TerrainUniform(activePass, originX, originY, originZ));
            renderPass.setUniform("ShineWater", slice);
            warnedWriteFailure = false;
            return true;
         } catch (RuntimeException exception) {
            if (!warnedWriteFailure) {
               BloomMod.LOGGER.warn("Shine could not update the Vulkan water terrain uniform block; water terrain effects are skipped.", exception);
               warnedWriteFailure = true;
            }

            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean bindTextures(RenderPass renderPass) {
      if (renderPass != null && ShineRenderBackend.isVulkan()) {
         boolean water = WaterReflectionShaderBridge.bindVulkanTerrainTextures(renderPass, activeCaptureMode);
         boolean caustics = TerrainCausticsRenderer.bindSodiumTerrainSamplers(renderPass);
         return water && caustics;
      } else {
         return false;
      }
   }

   public static void endTerrainPass() {
      activePass = null;
      activeCaptureMode = TerrainCaptureState.Mode.NONE;
   }

   public static void endFrame() {
      if (storage != null) {
         storage.endFrame();
      }

   }

   public static void shutdown() {
      activePass = null;
      activeCaptureMode = TerrainCaptureState.Mode.NONE;
      if (storage != null) {
         storage.close();
         storage = null;
      }

      warnedWriteFailure = false;
   }

   private static DynamicUniformStorage<TerrainUniform> storage() {
      if (storage == null) {
         storage = new DynamicUniformStorage("Shine Vulkan water terrain uniforms", 1024, 512);
      }

      return storage;
   }

   private static void putScalars(Std140Builder out, UniformValues values, String... names) {
      for(String name : names) {
         out.putFloat(values.scalar(name));
      }

   }

   private static void putVec2(Std140Builder out, UniformValues values, String name) {
      float[] value = values.vector(name, 2);
      out.putVec2(value[0], value[1]);
   }

   private static void putVec3(Std140Builder out, UniformValues values, String name) {
      float[] value = values.vector(name, 3);
      out.putVec3(value[0], value[1], value[2]);
   }

   private static void putVec4(Std140Builder out, UniformValues values, String name) {
      float[] value = values.vector(name, 4);
      out.putVec4(value[0], value[1], value[2], value[3]);
   }

   static {
      activeCaptureMode = TerrainCaptureState.Mode.NONE;
   }

   private static final class Collector implements UniformWriter {
      private final Map<String, Object> values = new HashMap();

      public void setInt(String name, int value) {
         this.values.put(name, value);
      }

      public void setFloat(String name, float value) {
         this.values.put(name, Float.isFinite(value) ? value : 0.0F);
      }

      public void setVec2(String name, float x, float y) {
         this.values.put(name, new float[]{finite(x), finite(y)});
      }

      public void setVec3(String name, float x, float y, float z) {
         this.values.put(name, new float[]{finite(x), finite(y), finite(z)});
      }

      public void setVec4(String name, float x, float y, float z, float w) {
         this.values.put(name, new float[]{finite(x), finite(y), finite(z), finite(w)});
      }

      public void setMatrix(String name, Matrix4f matrix) {
         this.values.put(name, matrix != null && matrix.isFinite() ? new Matrix4f(matrix) : new Matrix4f());
      }

      private UniformValues freeze() {
         return new UniformValues(Map.copyOf(this.values));
      }

      private static float finite(float value) {
         return Float.isFinite(value) ? value : 0.0F;
      }
   }

   private static record UniformValues(Map<String, Object> values) {
      private int integer(String name) {
         Object value = this.values.get(name);
         int var10000;
         if (value instanceof Number number) {
            var10000 = number.intValue();
         } else {
            var10000 = 0;
         }

         return var10000;
      }

      private float scalar(String name) {
         Object value = this.values.get(name);
         float var10000;
         if (value instanceof Number number) {
            if (Float.isFinite(number.floatValue())) {
               var10000 = number.floatValue();
               return var10000;
            }
         }

         var10000 = 0.0F;
         return var10000;
      }

      private float[] vector(String name, int length) {
         Object value = this.values.get(name);
         if (value instanceof float[] vector) {
            if (vector.length >= length) {
               return vector;
            }
         }

         return new float[length];
      }

      private Matrix4f matrix(String name) {
         Object value = this.values.get(name);
         Matrix4f var10000;
         if (value instanceof Matrix4f matrix) {
            var10000 = matrix;
         } else {
            var10000 = new Matrix4f();
         }

         return var10000;
      }
   }

   private static record TerrainUniform(UniformValues values, int originX, int originY, int originZ) implements DynamicUniformStorage.DynamicUniform {
      public void write(ByteBuffer buffer) {
         buffer.clear();
         Std140Builder out = Std140Builder.intoBuffer(buffer);
         out.putInt(this.values.integer("u_ShineSkyLightCapture"));
         out.putInt(this.values.integer("u_ShineTerrainCaptureMode"));
         out.putInt(this.values.integer("u_ShineWaterReflectionCapture"));
         out.putInt(this.values.integer("u_ShineWaterReflectionPass"));
         out.putInt(this.values.integer("u_ShineWaterReflectionsEnabled"));
         out.putInt(this.values.integer("u_ShineCloudTextureAvailable"));
         out.putInt(this.values.integer("u_ShineDimensionHasSky"));
         out.putFloat(this.values.scalar("u_ShineCloudTickTime"));
         TerrainWaterUniformBuffer.putVec2(out, this.values, "u_ShineCloudUpperSeedOffset");
         TerrainWaterUniformBuffer.putVec2(out, this.values, "u_ShineCloudHighSeedOffset");
         out.putFloat(this.values.scalar("u_ShineCloudVeilSeed"));
         TerrainWaterUniformBuffer.putVec4(out, this.values, "u_ShineCloudMain");
         TerrainWaterUniformBuffer.putVec4(out, this.values, "u_ShineCloudUpper");
         TerrainWaterUniformBuffer.putVec4(out, this.values, "u_ShineCloudHigh");
         TerrainWaterUniformBuffer.putVec4(out, this.values, "u_ShineCloudLayerEnabled");
         TerrainWaterUniformBuffer.putVec4(out, this.values, "u_ShineCloudVeil");
         TerrainWaterUniformBuffer.putVec4(out, this.values, "u_ShineCloudVeilShape");
         TerrainWaterUniformBuffer.putVec4(out, this.values, "u_ShineWaterStillUv");
         TerrainWaterUniformBuffer.putVec4(out, this.values, "u_ShineWaterFlowUv");
         out.putMat4f(this.values.matrix("u_ShineWaterReflectionViewProjection"));
         TerrainWaterUniformBuffer.putVec3(out, this.values, "u_ShineWaterReflectionCameraPos");
         TerrainWaterUniformBuffer.putVec3(out, this.values, "u_ShineWaterCurrentCameraPos");
         TerrainWaterUniformBuffer.putVec2(out, this.values, "u_ShineWaterViewportSize");
         TerrainWaterUniformBuffer.putScalars(out, this.values, "u_ShineWaterReflectionPlaneY", "u_ShineWaterReflectionTerrainFade", "u_ShineWaterReflectionRange", "u_ShineWaterReflectionStrength", "u_ShineWaterReflectionTerrainStrength", "u_ShineWaterReflectionSkyStrength", "u_ShineWaterReflectionGrazingBoost", "u_ShineWaterReflectionPixelResolution", "u_ShineWaterReflectionMotionStrength", "u_ShineWaterReflectionMotionScale", "u_ShineWaterReflectionMotionSpeed", "u_ShineWaterReflectionMotionDirection", "u_ShineWaterReflectionWakeInfluence", "u_ShineWaterReflectionFoamSuppression", "u_ShineWaterReflectionHeightTolerance", "u_ShineWaterReflectionTime", "u_ShineWaterReflectionDayCycle", "u_ShineWaterReflectionRain", "u_ShineWaterReflectionThunder");
         out.putInt(this.values.integer("u_ShineWaterReflectionStyle"));
         TerrainWaterUniformBuffer.putScalars(out, this.values, "u_ShineWaterReflectionColorSaturation", "u_ShineWaterReflectionContrast", "u_ShineWaterReflectionSurfaceRoughness", "u_ShineWaterReflectionRippleStretch", "u_ShineWaterReflectionSunGlintStrength", "u_ShineWaterReflectionSunGlintSharpness", "u_ShineWaterReflectionDepthAbsorption", "u_ShineWaterReflectionShallowClarity", "u_ShineWaterReflectionBodySaturation", "u_ShineWaterReflectionBodyDarkness");
         out.putInt(this.values.integer("u_ShineWaterOpaqueDepthValid"));
         out.putFloat(this.values.scalar("u_ShineWaterNearPlane"));
         out.putFloat(this.values.scalar("u_ShineWaterFarPlane"));
         TerrainWaterUniformBuffer.putVec3(out, this.values, "u_ShineSkyColor");
         TerrainWaterUniformBuffer.putVec4(out, this.values, "u_ShineCloudColor");
         TerrainWaterUniformBuffer.putScalars(out, this.values, "u_ShineSunAngle", "u_ShineMoonAngle", "u_ShineStarBrightness", "u_ShineMoonPhase");
         out.putInt(this.values.integer("u_ShineWaterReflectionCloudsEnabled"));
         out.putInt(this.values.integer("u_ShineTerrainCausticsEnabled"));
         out.putInt(this.values.integer("u_ShineTerrainCausticsTopFacesOnly"));
         out.putInt(this.values.integer("u_ShineTerrainCausticsRequireSunlight"));
         TerrainWaterUniformBuffer.putScalars(out, this.values, "u_ShineTerrainCausticsSunlightThreshold", "u_ShineTerrainCausticsShadeFade", "u_ShineTerrainCausticsDayFactor", "u_ShineTerrainCausticsStrength", "u_ShineTerrainCausticsScale", "u_ShineTerrainCausticsSpeed", "u_ShineTerrainCausticsMinY", "u_ShineTerrainCausticsMaxY", "u_ShineTerrainCausticsTime");
         out.putInt(this.values.integer("u_ShineShoreFoamEnabled"));
         TerrainWaterUniformBuffer.putScalars(out, this.values, "u_ShineShoreFoamOpacity", "u_ShineShoreFoamThickness", "u_ShineShoreFoamSpeed", "u_ShineShoreFoamScale", "u_ShineShoreFoamBreakup");
         TerrainWaterUniformBuffer.putVec3(out, this.values, "u_ShineShoreFoamColor");
         TerrainWaterUniformBuffer.putVec3(out, this.values, "u_ShineTerrainCausticsCameraPos");
         out.putVec3((float)this.originX, (float)this.originY, (float)this.originZ);
         out.align(16);
      }
   }

   public interface UniformWriter {
      void setInt(String var1, int var2);

      void setFloat(String var1, float var2);

      void setVec2(String var1, float var2, float var3);

      void setVec3(String var1, float var2, float var3, float var4);

      void setVec4(String var1, float var2, float var3, float var4, float var5);

      void setMatrix(String var1, Matrix4f var2);
   }
}
