package com.bloom.client.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

public final class BloomRenderTypeUtil {
   private static Field renderTypeStateField;
   private static Field renderSetupTexturesField;
   private static Method textureBindingLocationMethod;
   private static boolean reflectionFailed;

   private BloomRenderTypeUtil() {
   }

   public static RenderSetup setup(RenderType renderType) {
      if (renderType != null && !reflectionFailed) {
         try {
            if (renderTypeStateField == null) {
               renderTypeStateField = findField(RenderType.class, "state", "field_64013");
            }

            return (RenderSetup)renderTypeStateField.get(renderType);
         } catch (ClassCastException | ReflectiveOperationException var2) {
            reflectionFailed = true;
            return null;
         }
      } else {
         return null;
      }
   }

   public static Identifier sampler0Texture(RenderType renderType) {
      RenderSetup setup = setup(renderType);
      if (setup != null && !reflectionFailed) {
         try {
            if (renderSetupTexturesField == null) {
               renderSetupTexturesField = findField(RenderSetup.class, "textures", "field_63987");
            }

            Object textures = renderSetupTexturesField.get(setup);
            if (textures instanceof Map) {
               Map<?, ?> textureMap = (Map)textures;
               Object binding = textureMap.get("Sampler0");
               if (binding == null) {
                  return null;
               } else {
                  if (textureBindingLocationMethod == null) {
                     textureBindingLocationMethod = findMethod(binding.getClass(), "location", "comp_5228");
                  }

                  Object location = textureBindingLocationMethod.invoke(binding);
                  Identifier var10000;
                  if (location instanceof Identifier) {
                     Identifier id = (Identifier)location;
                     var10000 = id;
                  } else {
                     var10000 = null;
                  }

                  return var10000;
               }
            } else {
               return null;
            }
         } catch (ClassCastException | ReflectiveOperationException var7) {
            reflectionFailed = true;
            return null;
         }
      } else {
         return null;
      }
   }

   private static Field findField(Class<?> owner, String... names) throws NoSuchFieldException {
      for(String name : names) {
         try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
         } catch (NoSuchFieldException var7) {
         }
      }

      throw new NoSuchFieldException(String.join("/", names));
   }

   private static Method findMethod(Class<?> owner, String... names) throws NoSuchMethodException {
      for(String name : names) {
         try {
            Method method = owner.getDeclaredMethod(name);
            method.setAccessible(true);
            return method;
         } catch (NoSuchMethodException var7) {
         }
      }

      throw new NoSuchMethodException(String.join("/", names));
   }

   public static boolean isEntityPipeline(RenderPipeline pipeline) {
      return pipeline == RenderPipelines.ENTITY_SOLID || pipeline == RenderPipelines.ENTITY_SOLID_Z_OFFSET_FORWARD || pipeline == RenderPipelines.ENTITY_CUTOUT_CULL || pipeline == RenderPipelines.ENTITY_CUTOUT || pipeline == RenderPipelines.ENTITY_CUTOUT_Z_OFFSET || pipeline == RenderPipelines.ENTITY_CUTOUT_DISSOLVE || pipeline == RenderPipelines.ENTITY_TRANSLUCENT || pipeline == RenderPipelines.ENTITY_TRANSLUCENT_EMISSIVE || pipeline == RenderPipelines.ENTITY_TRANSLUCENT_CULL || pipeline == RenderPipelines.ARMOR_CUTOUT_NO_CULL || pipeline == RenderPipelines.ARMOR_DECAL_CUTOUT_NO_CULL || pipeline == RenderPipelines.ARMOR_TRANSLUCENT || pipeline == RenderPipelines.ITEM_CUTOUT || pipeline == RenderPipelines.ITEM_TRANSLUCENT || pipeline == RenderPipelines.BREEZE_WIND || pipeline == RenderPipelines.ENERGY_SWIRL || pipeline == RenderPipelines.EYES;
   }

   public static boolean isParticlePipeline(RenderPipeline pipeline) {
      return pipeline == RenderPipelines.OPAQUE_PARTICLE || pipeline == RenderPipelines.TRANSLUCENT_PARTICLE;
   }
}
