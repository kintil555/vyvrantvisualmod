package com.bloom.client.render;

import com.bloom.client.config.BloomMaskConfig;
import com.bloom.client.selection.BloomSelection;
import com.bloom.mixin.client.accessor.TextureAtlasAccessor;
import com.google.common.collect.UnmodifiableIterator;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class BloomMaskAtlas {
   public static final Identifier MASK_TEXTURE_ID = Identifier.fromNamespaceAndPath("vybrantvisual", "runtime/mask_atlas");
   private static final int EDGE_EXPANSION_PIXELS = 1;
   private static DynamicTexture maskTexture;
   private static int maskTextureWidth;
   private static int maskTextureHeight;
   private static boolean dirty = true;
   private static final Map<String, SpriteInfo> spriteInfoById = new LinkedHashMap();

   private BloomMaskAtlas() {
   }

   public static void markDirty() {
      dirty = true;
   }

   public static void ensureReady() {
      if (dirty) {
         rebuild();
      }
   }

   public static int getMaskTextureId() {
      if (!ShineRenderBackend.canUseRawOpenGl()) {
         return 0;
      } else {
         ensureReady();
         if (maskTexture != null) {
            GpuTexture var1 = maskTexture.getTexture();
            if (var1 instanceof GlTexture) {
               GlTexture glTexture = (GlTexture)var1;
               return glTexture.glId();
            }
         }

         return 0;
      }
   }

   public static GpuTextureView getMaskTextureView() {
      ensureReady();
      return maskTexture == null ? null : maskTexture.getTextureView();
   }

   public static Map<String, SpriteInfo> spriteInfoMap() {
      ensureReady();
      return Collections.unmodifiableMap(spriteInfoById);
   }

   public static SpriteInfo getSpriteInfo(String spriteId) {
      ensureReady();
      return (SpriteInfo)spriteInfoById.get(spriteId);
   }

   private static void rebuild() {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft != null && minecraft.getTextureManager() != null) {
         AbstractTexture var2 = minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
         if (var2 instanceof TextureAtlas) {
            TextureAtlas atlas = (TextureAtlas)var2;
            Map<Identifier, TextureAtlasSprite> textures = ((TextureAtlasAccessor)atlas).bloom$getTexturesByName();
            if (!textures.isEmpty()) {
               spriteInfoById.clear();
               int atlasWidth = Math.max(1, ((TextureAtlasAccessor)atlas).bloom$getWidth());
               int atlasHeight = Math.max(1, ((TextureAtlasAccessor)atlas).bloom$getHeight());

               for(Map.Entry<Identifier, TextureAtlasSprite> entry : textures.entrySet()) {
                  TextureAtlasSprite sprite = (TextureAtlasSprite)entry.getValue();
                  SpriteContents contents = sprite.contents();
                  int w = Math.max(1, contents.width());
                  int h = Math.max(1, contents.height());
                  int x = Math.max(0, Math.min(atlasWidth - 1, Math.round(sprite.getU0() * (float)atlasWidth)));
                  int y = Math.max(0, Math.min(atlasHeight - 1, Math.round(sprite.getV0() * (float)atlasHeight)));
                  atlasWidth = Math.max(atlasWidth, x + w);
                  atlasHeight = Math.max(atlasHeight, y + h);
                  spriteInfoById.put(((Identifier)entry.getKey()).toString(), new SpriteInfo(((Identifier)entry.getKey()).toString(), x, y, w, h));
               }

               List<SpriteInfo> spriteInfos = new ArrayList(spriteInfoById.values());
               Map<String, BloomMaskConfig.ResolvedMaskEntry> sourceMasksBySprite = BloomMaskConfig.resolveSourceMaskEntries(BloomMaskAtlas::resolveCurrentSourceSpriteId);
               NativeImage image = new NativeImage(atlasWidth, atlasHeight, false);

               for(int y = 0; y < atlasHeight; ++y) {
                  for(int x = 0; x < atlasWidth; ++x) {
                     image.setPixel(x, y, maskPixel(true, false));
                  }
               }

               boolean[] occupiedCorePixels = new boolean[atlasWidth * atlasHeight];

               for(SpriteInfo info : spriteInfos) {
                  for(int py = 0; py < info.height(); ++py) {
                     for(int px = 0; px < info.width(); ++px) {
                        int x = info.x() + px;
                        int y = info.y() + py;
                        if (x >= 0 && y >= 0 && x < atlasWidth && y < atlasHeight) {
                           occupiedCorePixels[y * atlasWidth + x] = true;
                        }
                     }
                  }
               }

               for(SpriteInfo info : spriteInfos) {
                  BloomMaskConfig.ResolvedMaskEntry sourceEntry = (BloomMaskConfig.ResolvedMaskEntry)sourceMasksBySprite.get(info.spriteId());
                  boolean[] mask = BloomMaskConfig.readMask(info.spriteId(), info.width(), info.height(), sourceEntry == null ? null : sourceEntry.mask());
                  writeMaskWithEdgeExpansion(image, atlasWidth, atlasHeight, info, mask, sourceEntry != null && sourceEntry.emissive(), occupiedCorePixels);
               }

               if (maskTexture != null && maskTextureWidth == atlasWidth && maskTextureHeight == atlasHeight) {
                  maskTexture.setPixels(image);
                  maskTexture.upload();
               } else {
                  if (maskTexture != null) {
                     shine$releaseTexture(minecraft, MASK_TEXTURE_ID);
                  }

                  maskTexture = new DynamicTexture(() -> "Shine mask atlas", image);
                  minecraft.getTextureManager().register(MASK_TEXTURE_ID, maskTexture);
                  maskTextureWidth = atlasWidth;
                  maskTextureHeight = atlasHeight;
               }

               dirty = false;
            }
         }
      }
   }

   private static void shine$releaseTexture(Minecraft minecraft, Identifier textureId) {
      Object textureManager = minecraft.getTextureManager();
      if (textureManager != null) {
         for(String methodName : new String[]{"release", "destroyTexture"}) {
            for(Method method : textureManager.getClass().getMethods()) {
               if (method.getName().equals(methodName) && method.getParameterCount() == 1) {
                  Class<?> parameterType = method.getParameterTypes()[0];
                  if (parameterType.isInstance(textureId)) {
                     try {
                        method.invoke(textureManager, textureId);
                        return;
                     } catch (Throwable var13) {
                     }
                  }
               }
            }
         }

      }
   }

   private static String resolveCurrentSourceSpriteId(String sourceId, boolean fluid, String faceName, String stateKey) {
      Direction face = Direction.byName(faceName);
      if (face == null) {
         face = Direction.NORTH;
      }

      ResolvedSprite sprite = resolveSprite(sourceId, fluid, face, stateKey);
      return sprite == null ? null : sprite.spriteId();
   }

   private static ResolvedSprite resolveSprite(String sourceId, boolean fluid, Direction face, String stateKey) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft != null && minecraft.getTextureManager() != null) {
         AbstractTexture var6 = minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
         if (!(var6 instanceof TextureAtlas)) {
            return null;
         } else {
            TextureAtlas atlas = (TextureAtlas)var6;
            if (fluid) {
               String spriteId = guessSpriteId(sourceId, true, face);
               TextureAtlasSprite sprite = atlas.getSprite(Identifier.parse(spriteId));
               return sprite != null && sprite != atlas.missingSprite() ? new ResolvedSprite(sprite.contents().name().toString()) : new ResolvedSprite(spriteId);
            } else {
               Block block = (Block)BuiltInRegistries.BLOCK.getValue(Identifier.parse(sourceId));
               if (block == null) {
                  return null;
               } else {
                  BlockState state = resolveBlockState(block, stateKey);
                  BlockStateModel model = minecraft.getModelManager().getBlockStateModelSet().get(state);
                  TextureAtlasSprite sprite = resolveModelSprite(model, face);
                  if (sprite == null) {
                     sprite = model != null && model.particleMaterial() != null ? model.particleMaterial().sprite() : null;
                  }

                  return sprite == null ? null : new ResolvedSprite(sprite.contents().name().toString());
               }
            }
         }
      } else {
         return null;
      }
   }

   private static BlockState resolveBlockState(Block block, String stateKey) {
      if (block != null && stateKey != null && !stateKey.isBlank()) {
         UnmodifiableIterator var2 = block.getStateDefinition().getPossibleStates().iterator();

         while(var2.hasNext()) {
            BlockState state = (BlockState)var2.next();
            if (state != null && stateKey.equals(BloomSelection.blockStateKey(state))) {
               return state;
            }
         }

         return block.defaultBlockState();
      } else {
         return block == null ? null : block.defaultBlockState();
      }
   }

   private static TextureAtlasSprite resolveModelSprite(BlockStateModel model, Direction face) {
      if (model == null) {
         return null;
      } else {
         List<BlockStateModelPart> parts = new ArrayList();
         model.collectParts(RandomSource.create(42L), parts);
         TextureAtlasSprite sprite = resolveModelSpriteForFace(parts, face);
         if (sprite != null) {
            return sprite;
         } else {
            for(Direction direction : Direction.values()) {
               sprite = resolveModelSpriteForFace(parts, direction);
               if (sprite != null) {
                  return sprite;
               }
            }

            for(BlockStateModelPart part : parts) {
               if (part != null && part.particleMaterial() != null) {
                  return part.particleMaterial().sprite();
               }
            }

            return null;
         }
      }
   }

   private static TextureAtlasSprite resolveModelSpriteForFace(List<BlockStateModelPart> parts, Direction face) {
      if (parts == null) {
         return null;
      } else {
         for(BlockStateModelPart part : parts) {
            if (part != null) {
               List<BakedQuad> quads = part.getQuads(face);
               if (quads != null && !quads.isEmpty()) {
                  TextureAtlasSprite sprite = ((BakedQuad)quads.get(0)).materialInfo().sprite();
                  if (sprite != null) {
                     return sprite;
                  }
               }
            }
         }

         return null;
      }
   }

   private static String guessSpriteId(String sourceId, boolean fluid, Direction face) {
      Identifier id = Identifier.parse(sourceId);
      String namespace = id.getNamespace();
      String path = id.getPath();
      if (fluid) {
         if (path.contains("water")) {
            String suffix = face != Direction.UP && face != Direction.DOWN ? "water_flow" : "water_still";
            return namespace + ":block/" + suffix;
         }

         if (path.contains("lava")) {
            String suffix = face != Direction.UP && face != Direction.DOWN ? "lava_flow" : "lava_still";
            return namespace + ":block/" + suffix;
         }
      }

      return namespace + ":block/" + path;
   }

   private static void writeMaskWithEdgeExpansion(NativeImage image, int atlasWidth, int atlasHeight, SpriteInfo info, boolean[] mask, boolean emissive, boolean[] occupiedCorePixels) {
      int expandedMinX = -1;
      int expandedMinY = -1;
      int expandedMaxX = info.width() + 1 - 1;
      int expandedMaxY = info.height() + 1 - 1;

      for(int py = expandedMinY; py <= expandedMaxY; ++py) {
         for(int px = expandedMinX; px <= expandedMaxX; ++px) {
            int atlasX = info.x() + px;
            int atlasY = info.y() + py;
            if (atlasX >= 0 && atlasY >= 0 && atlasX < atlasWidth && atlasY < atlasHeight) {
               boolean corePixel = px >= 0 && px < info.width() && py >= 0 && py < info.height();
               if (!corePixel) {
                  int occupiedIndex = atlasY * atlasWidth + atlasX;
                  if (occupiedCorePixels[occupiedIndex]) {
                     continue;
                  }
               }

               int sampleX = Math.max(0, Math.min(info.width() - 1, px));
               int sampleY = Math.max(0, Math.min(info.height() - 1, py));
               boolean enabled = mask[sampleY * info.width() + sampleX];
               image.setPixel(atlasX, atlasY, maskPixel(enabled, enabled && emissive));
            }
         }
      }

   }

   private static int maskPixel(boolean bloom, boolean emissive) {
      if (!bloom) {
         return 0;
      } else {
         return emissive ? -1 : 16777215;
      }
   }

   public static record SpriteInfo(String spriteId, int x, int y, int width, int height) {
   }

   private static record ResolvedSprite(String spriteId) {
   }
}
