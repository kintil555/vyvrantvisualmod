package com.bloom.client.render;

import com.bloom.client.config.BloomMaskConfig;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

public final class BloomEntityMaskTextures {
   private static final int MAX_MASK_TEXTURE_DIMENSION = 128;
   private static final int MAX_MASK_TEXTURE_PIXELS = 16384;
   private static final Map<String, Entry> MASK_TEXTURES = new HashMap();
   private static boolean dirty = true;

   private BloomEntityMaskTextures() {
   }

   public static void markDirty() {
      dirty = true;
   }

   public static GpuTextureView getMaskTextureView(Identifier textureId) {
      if (textureId == null) {
         return null;
      } else {
         if (dirty) {
            clear();
            dirty = false;
         }

         String key = textureId.toString();
         Entry cached = (Entry)MASK_TEXTURES.get(key);
         TextureSize sourceSize = resolveTextureSize(textureId);
         if (sourceSize == null) {
            return null;
         } else {
            TextureSize maskSize = maskTextureSize(sourceSize.width, sourceSize.height);
            if (cached != null && cached.width == maskSize.width && cached.height == maskSize.height) {
               return cached.texture.getTextureView();
            } else {
               NativeImage image = new NativeImage(maskSize.width, maskSize.height, false);
               boolean[] mask = BloomMaskConfig.readEntityTextureMask(key, maskSize.width, maskSize.height);

               for(int y = 0; y < maskSize.height; ++y) {
                  for(int x = 0; x < maskSize.width; ++x) {
                     image.setPixel(x, y, mask[y * maskSize.width + x] ? -1 : -16777216);
                  }
               }

               DynamicTexture texture = new DynamicTexture(() -> "Shine entity mask " + key, image);
               Identifier runtimeId = Identifier.fromNamespaceAndPath("vybrantvisual", "runtime/entity_mask/" + Integer.toUnsignedString(key.hashCode()));
               Minecraft.getInstance().getTextureManager().register(runtimeId, texture);
               MASK_TEXTURES.put(key, new Entry(maskSize.width, maskSize.height, texture));
               return texture.getTextureView();
            }
         }
      }
   }

   public static TextureSize getTextureSize(String textureId) {
      try {
         return resolveTextureSize(Identifier.parse(textureId));
      } catch (Exception var2) {
         return null;
      }
   }

   public static void clear() {
      for(Entry entry : MASK_TEXTURES.values()) {
         entry.texture.close();
      }

      MASK_TEXTURES.clear();
   }

   private static TextureSize resolveTextureSize(Identifier textureId) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft == null) {
         return null;
      } else {
         if (minecraft.getResourceManager() != null) {
            try {
               Resource resource = minecraft.getResourceManager().getResource(textureId).orElse(null);
               if (resource != null) {
                  label75: {
                     InputStream stream = resource.open();

                     TextureSize var5;
                     label65: {
                        try {
                           TextureSize size = readPngSize(stream);
                           if (size != null) {
                              var5 = size;
                              break label65;
                           }
                        } catch (Throwable var8) {
                           if (stream != null) {
                              try {
                                 stream.close();
                              } catch (Throwable var7) {
                                 var8.addSuppressed(var7);
                              }
                           }

                           throw var8;
                        }

                        if (stream != null) {
                           stream.close();
                        }
                        break label75;
                     }

                     if (stream != null) {
                        stream.close();
                     }

                     return var5;
                  }
               }
            } catch (Exception var9) {
            }
         }

         if (minecraft.getTextureManager() == null) {
            return null;
         } else {
            try {
               AbstractTexture texture = minecraft.getTextureManager().getTexture(textureId);
               if (texture != null && texture.getTexture() != null) {
                  int width = Math.max(1, texture.getTexture().getWidth(0));
                  int height = Math.max(1, texture.getTexture().getHeight(0));
                  return new TextureSize(width, height);
               } else {
                  return null;
               }
            } catch (Exception var6) {
               return null;
            }
         }
      }
   }

   private static TextureSize readPngSize(InputStream stream) throws IOException {
      byte[] header = stream.readNBytes(24);
      if (header.length >= 24 && (header[0] & 255) == 137 && header[1] == 80 && header[2] == 78 && header[3] == 71 && header[4] == 13 && header[5] == 10 && header[6] == 26 && header[7] == 10) {
         int width = readInt(header, 16);
         int height = readInt(header, 20);
         return width > 0 && height > 0 ? new TextureSize(width, height) : null;
      } else {
         return null;
      }
   }

   private static int readInt(byte[] bytes, int offset) {
      return (bytes[offset] & 255) << 24 | (bytes[offset + 1] & 255) << 16 | (bytes[offset + 2] & 255) << 8 | bytes[offset + 3] & 255;
   }

   private static TextureSize maskTextureSize(int width, int height) {
      width = Math.max(1, width);
      height = Math.max(1, height);
      long pixels = (long)width * (long)height;
      if (width <= 128 && height <= 128 && pixels <= 16384L) {
         return new TextureSize(width, height);
      } else {
         double dimensionScale = (double)128.0F / (double)Math.max(width, height);
         double pixelScale = Math.sqrt((double)16384.0F / ((double)width * (double)height));
         double scale = Math.min(dimensionScale, pixelScale);
         int maskWidth = Math.max(1, (int)Math.round((double)width * scale));
         int maskHeight = Math.max(1, (int)Math.round((double)height * scale));
         return new TextureSize(maskWidth, maskHeight);
      }
   }

   private static record Entry(int width, int height, DynamicTexture texture) {
   }

   public static record TextureSize(int width, int height) {
   }
}
