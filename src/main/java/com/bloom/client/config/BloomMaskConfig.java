package com.bloom.client.config;

import com.bloom.BloomMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Direction;

public final class BloomMaskConfig {
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();
   private static final String DEFAULT_MASK_CONFIG_RESOURCE = "/assets/shine/defaults/shine_masks.json";
   private static final String OPEN_EYEBLOSSOM_BASE_SPRITE = "minecraft:block/open_eyeblossom";
   private static final String OPEN_EYEBLOSSOM_EMISSIVE_SPRITE = "minecraft:block/open_eyeblossom_emissive";
   private static final String EMPTY_16_MASK_BITS = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
   private static final String OPEN_EYEBLOSSOM_EMISSIVE_MASK_BITS = "//////////////////////////////////////////8=";
   private static Data data = BloomMaskConfig.Data.defaults();

   private BloomMaskConfig() {
   }

   public static void load() {
      Path maskConfigPath = maskConfigPath();
      if (!Files.exists(maskConfigPath, new LinkOption[0])) {
         data = BloomMaskConfig.Data.defaults();
         save();
      } else {
         try {
            Data loaded = (Data)ShineAtomicFiles.readUtf8WithBackup(maskConfigPath, (reader) -> (Data)GSON.fromJson(reader, Data.class));
            data = sanitize(loaded);
         } catch (Exception e) {
            BloomMod.LOGGER.error("Failed to read Shine mask config, using defaults.", e);
            data = BloomMaskConfig.Data.defaults();
         }

      }
   }

   public static void save() {
      try {
         ShineAtomicFiles.writeUtf8(maskConfigPath(), (writer) -> GSON.toJson(data, writer));
         ShinePresetStateManager.captureLocalOverridesAfterUserSave();
      } catch (IOException e) {
         BloomMod.LOGGER.error("Failed to write Shine mask config.", e);
      }

   }

   private static Path maskConfigPath() {
      return FabricLoader.getInstance().getConfigDir().resolve("shine_masks.json");
   }

   public static Data copy() {
      return data.copy();
   }

   public static Data defaults() {
      return BloomMaskConfig.Data.defaults();
   }

   public static void set(Data newData) {
      data = sanitize(newData);
   }

   public static boolean hasCustomMasks() {
      return data.spriteMasks != null && !data.spriteMasks.isEmpty() || data.sourceMasks != null && !data.sourceMasks.isEmpty() || data.entityTextureMasks != null && !data.entityTextureMasks.isEmpty();
   }

   public static boolean[] readMask(String spriteId, int width, int height) {
      return readMask(spriteId, width, height, (MaskEntry)null);
   }

   public static boolean[] readMask(String spriteId, int width, int height, MaskEntry sourceFallback) {
      if (sourceFallback != null) {
         boolean[] decoded = decodeMask(sourceFallback, width, height);
         if (decoded != null) {
            return decoded;
         }
      }

      for(String candidate : linkedSpriteIds(spriteId)) {
         MaskEntry entry = (MaskEntry)data.spriteMasks.get(candidate);
         if (entry != null) {
            boolean[] decoded = decodeMask(entry, width, height);
            if (decoded != null) {
               return decoded;
            }
         }
      }

      MaskEntry familyFallback = findFamilyFallbackEntry(spriteId);
      if (familyFallback != null) {
         boolean[] decoded = decodeMask(familyFallback, width, height);
         if (decoded != null) {
            return decoded;
         }
      }

      return filledMask(width, height, true);
   }

   public static void writeMask(String spriteId, int width, int height, boolean[] mask) {
      if (spriteId != null && width > 0 && height > 0 && mask != null && mask.length == width * height) {
         if (isAllTrue(mask)) {
            data.spriteMasks.remove(spriteId);
         } else {
            MaskEntry entry = new MaskEntry();
            entry.width = width;
            entry.height = height;
            entry.bits = encodeMask(mask);
            data.spriteMasks.put(spriteId, entry);
         }
      }
   }

   public static boolean[] readEntityTextureMask(String textureId, int width, int height) {
      if (textureId != null && !textureId.isBlank()) {
         MaskEntry entry = (MaskEntry)data.entityTextureMasks.get(textureId);
         if (entry != null) {
            boolean[] decoded = decodeMask(entry, width, height);
            if (decoded != null) {
               return decoded;
            }
         }
      }

      return filledMask(width, height, true);
   }

   public static void writeEntityTextureMask(String textureId, int width, int height, boolean[] mask) {
      if (textureId != null && !textureId.isBlank() && width > 0 && height > 0 && mask != null && mask.length == width * height) {
         if (isAllTrue(mask)) {
            data.entityTextureMasks.remove(textureId);
         } else {
            MaskEntry entry = new MaskEntry();
            entry.width = width;
            entry.height = height;
            entry.bits = encodeMask(mask);
            data.entityTextureMasks.put(textureId, entry);
         }
      }
   }

   public static void writeSourceMask(String sourceId, boolean fluid, Direction face, String spriteId, int width, int height, boolean[] mask) {
      writeSourceMask(sourceId, fluid, face, spriteId, width, height, mask, false);
   }

   public static void writeSourceMask(String sourceId, boolean fluid, Direction face, String spriteId, int width, int height, boolean[] mask, boolean emissive) {
      writeSourceMask(sourceId, fluid, face, "", spriteId, width, height, mask, emissive);
   }

   public static void writeSourceMask(String sourceId, boolean fluid, Direction face, String stateKey, String spriteId, int width, int height, boolean[] mask, boolean emissive) {
      writeSourceMask(sourceId, fluid, face, stateKey, "", spriteId, width, height, mask, emissive);
   }

   public static void writeSourceMask(String sourceId, boolean fluid, Direction face, String stateKey, String layerSpriteId, String spriteId, int width, int height, boolean[] mask, boolean emissive) {
      if (sourceId != null && !sourceId.isBlank() && face != null && spriteId != null && !spriteId.isBlank() && width > 0 && height > 0 && mask != null && mask.length == width * height) {
         String normalizedStateKey = normalizeStateKey(stateKey);
         String normalizedLayerSpriteId = normalizeLayerSpriteId(layerSpriteId);
         String key = sourceMaskKey(sourceId, fluid, face.getName(), normalizedStateKey, normalizedLayerSpriteId);
         if (isAllTrue(mask) && !emissive && normalizedLayerSpriteId.isBlank()) {
            data.sourceMasks.remove(key);
         } else {
            SourceMaskEntry entry = new SourceMaskEntry();
            entry.sourceId = sourceId;
            entry.fluid = fluid;
            entry.face = face.getName();
            entry.stateKey = normalizedStateKey;
            entry.layerSpriteId = normalizedLayerSpriteId;
            entry.spriteId = spriteId;
            entry.width = width;
            entry.height = height;
            entry.bits = encodeMask(mask);
            entry.emissive = emissive;
            data.sourceMasks.put(key, entry);
         }
      }
   }

   public static void removeSourceMasks(String sourceId, boolean fluid) {
      if (sourceId != null && !sourceId.isBlank() && data.sourceMasks != null && !data.sourceMasks.isEmpty()) {
         data.sourceMasks.entrySet().removeIf((entry) -> {
            SourceMaskEntry mask = (SourceMaskEntry)entry.getValue();
            return mask != null && mask.fluid == fluid && sourceId.equals(mask.sourceId);
         });
      }
   }

   public static boolean[] readSourceMask(String sourceId, boolean fluid, Direction face, String stateKey, String spriteId, int width, int height) {
      return readSourceMask(sourceId, fluid, face, stateKey, "", spriteId, width, height);
   }

   public static boolean[] readSourceMask(String sourceId, boolean fluid, Direction face, String stateKey, String layerSpriteId, String spriteId, int width, int height) {
      if (sourceId != null && !sourceId.isBlank() && face != null) {
         String normalizedStateKey = normalizeStateKey(stateKey);
         String normalizedLayerSpriteId = normalizeLayerSpriteId(layerSpriteId);
         SourceMaskEntry entry = (SourceMaskEntry)data.sourceMasks.get(sourceMaskKey(sourceId, fluid, face.getName(), normalizedStateKey, normalizedLayerSpriteId));
         if (entry != null) {
            boolean[] decoded = decodeSourceMask(entry, width, height);
            if (decoded != null) {
               return decoded;
            }
         }
      }

      return readMask(spriteId, width, height);
   }

   public static Map<String, MaskEntry> resolveSourceMasks(SourceMaskSpriteResolver resolver) {
      Map<String, ResolvedMaskEntry> sourceEntries = resolveSourceMaskEntries(resolver);
      Map<String, MaskEntry> resolved = new LinkedHashMap();

      for(Map.Entry<String, ResolvedMaskEntry> entry : sourceEntries.entrySet()) {
         resolved.put((String)entry.getKey(), ((ResolvedMaskEntry)entry.getValue()).mask());
      }

      return resolved;
   }

   public static Map<String, ResolvedMaskEntry> resolveSourceMaskEntries(SourceMaskSpriteResolver resolver) {
      Map<String, ResolvedMaskEntry> resolved = new LinkedHashMap();
      if (resolver != null && data.sourceMasks != null && !data.sourceMasks.isEmpty()) {
         LinkedHashSet<String> explicitLayerSprites = new LinkedHashSet();

         for(SourceMaskEntry sourceEntry : data.sourceMasks.values()) {
            if (sourceEntry != null) {
               String layerSpriteId = normalizeLayerSpriteId(sourceEntry.layerSpriteId);
               if (!layerSpriteId.isBlank()) {
                  explicitLayerSprites.add(layerSpriteId);
               }
            }
         }

         for(SourceMaskEntry sourceEntry : data.sourceMasks.values()) {
            if (sourceEntry != null) {
               String layerSpriteId = normalizeLayerSpriteId(sourceEntry.layerSpriteId);
               String spriteId = layerSpriteId.isBlank() ? resolver.resolve(sourceEntry.sourceId, sourceEntry.fluid, sourceEntry.face, sourceEntry.stateKey) : layerSpriteId;
               if (spriteId == null || spriteId.isBlank()) {
                  spriteId = sourceEntry.spriteId;
               }

               if (spriteId != null && !spriteId.isBlank() && (!layerSpriteId.isBlank() || !explicitLayerSprites.contains(spriteId))) {
                  MaskEntry maskEntry = new MaskEntry();
                  maskEntry.width = sourceEntry.width;
                  maskEntry.height = sourceEntry.height;
                  maskEntry.bits = sourceEntry.bits;
                  ResolvedMaskEntry existing = (ResolvedMaskEntry)resolved.get(spriteId);
                  if (existing != null && layerSpriteId.isBlank()) {
                     if (sourceEntry.emissive && !existing.emissive()) {
                        resolved.put(spriteId, new ResolvedMaskEntry(existing.mask(), true));
                     }
                  } else {
                     resolved.put(spriteId, new ResolvedMaskEntry(maskEntry, sourceEntry.emissive));
                  }
               }
            }
         }

         return resolved;
      } else {
         return resolved;
      }
   }

   public static boolean isSourceMaskEmissive(String sourceId, boolean fluid, Direction face) {
      return isSourceMaskEmissive(sourceId, fluid, face, "");
   }

   public static boolean isSourceMaskEmissive(String sourceId, boolean fluid, Direction face, String stateKey) {
      return isSourceMaskEmissive(sourceId, fluid, face, stateKey, "");
   }

   public static boolean isSourceMaskEmissive(String sourceId, boolean fluid, Direction face, String stateKey, String layerSpriteId) {
      if (sourceId != null && !sourceId.isBlank() && face != null) {
         String normalizedStateKey = normalizeStateKey(stateKey);
         String normalizedLayerSpriteId = normalizeLayerSpriteId(layerSpriteId);
         SourceMaskEntry entry = (SourceMaskEntry)data.sourceMasks.get(sourceMaskKey(sourceId, fluid, face.getName(), normalizedStateKey, normalizedLayerSpriteId));
         if (entry != null) {
            return entry.emissive;
         } else if (normalizedLayerSpriteId.isBlank()) {
            return false;
         } else {
            entry = (SourceMaskEntry)data.sourceMasks.get(sourceMaskKey(sourceId, fluid, face.getName(), normalizedStateKey, ""));
            return entry != null && entry.emissive;
         }
      } else {
         return false;
      }
   }

   private static Data sanitize(Data input) {
      Data safe = input == null ? BloomMaskConfig.Data.defaults() : input.copy();
      Map<String, MaskEntry> sanitized = new LinkedHashMap();
      if (safe.spriteMasks != null) {
         for(Map.Entry<String, MaskEntry> entry : safe.spriteMasks.entrySet()) {
            if (entry != null && entry.getKey() != null && entry.getValue() != null) {
               MaskEntry maskEntry = (MaskEntry)entry.getValue();
               if (maskEntry.width > 0 && maskEntry.height > 0 && maskEntry.bits != null && !maskEntry.bits.isBlank()) {
                  sanitized.put((String)entry.getKey(), maskEntry.copy());
               }
            }
         }
      }

      safe.spriteMasks = sanitized;
      Map<String, MaskEntry> entitySanitized = new LinkedHashMap();
      if (safe.entityTextureMasks != null) {
         for(Map.Entry<String, MaskEntry> entry : safe.entityTextureMasks.entrySet()) {
            if (entry != null && entry.getKey() != null && entry.getValue() != null) {
               MaskEntry maskEntry = (MaskEntry)entry.getValue();
               if (maskEntry.width > 0 && maskEntry.height > 0 && maskEntry.bits != null && !maskEntry.bits.isBlank()) {
                  entitySanitized.put((String)entry.getKey(), maskEntry.copy());
               }
            }
         }
      }

      safe.entityTextureMasks = entitySanitized;
      Map<String, SourceMaskEntry> sourceSanitized = new LinkedHashMap();
      if (safe.sourceMasks != null) {
         for(Map.Entry<String, SourceMaskEntry> entry : safe.sourceMasks.entrySet()) {
            if (entry != null && entry.getValue() != null) {
               SourceMaskEntry maskEntry = (SourceMaskEntry)entry.getValue();
               if (maskEntry.sourceId != null && !maskEntry.sourceId.isBlank() && maskEntry.face != null && !maskEntry.face.isBlank() && maskEntry.spriteId != null && !maskEntry.spriteId.isBlank() && maskEntry.width > 0 && maskEntry.height > 0 && maskEntry.bits != null && !maskEntry.bits.isBlank()) {
                  maskEntry.stateKey = normalizeStateKey(maskEntry.stateKey);
                  maskEntry.layerSpriteId = normalizeLayerSpriteId(maskEntry.layerSpriteId);
                  sourceSanitized.put(sourceMaskKey(maskEntry.sourceId, maskEntry.fluid, maskEntry.face, maskEntry.stateKey, maskEntry.layerSpriteId), maskEntry.copy());
               }
            }
         }
      }

      safe.sourceMasks = sourceSanitized;
      if (safe.sourceMasks.isEmpty() && !safe.spriteMasks.isEmpty()) {
         addInferredSourceMasks(safe.sourceMasks, safe.spriteMasks);
      }

      addOpenEyeblossomBaseDefault(safe.sourceMasks);
      addOpenEyeblossomEmissiveDefault(safe.sourceMasks);
      return safe;
   }

   private static void addOpenEyeblossomBaseDefault(Map<String, SourceMaskEntry> target) {
      if (target != null) {
         for(SourceMaskEntry entry : target.values()) {
            if (entry != null && "minecraft:open_eyeblossom".equals(entry.sourceId) && "minecraft:block/open_eyeblossom".equals(normalizeLayerSpriteId(entry.layerSpriteId))) {
               return;
            }
         }

         SourceMaskEntry sourceEntry = new SourceMaskEntry();
         sourceEntry.sourceId = "minecraft:open_eyeblossom";
         sourceEntry.fluid = false;
         sourceEntry.face = "north";
         sourceEntry.stateKey = "";
         sourceEntry.layerSpriteId = "minecraft:block/open_eyeblossom";
         sourceEntry.spriteId = "minecraft:block/open_eyeblossom";
         sourceEntry.width = 16;
         sourceEntry.height = 16;
         sourceEntry.bits = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
         sourceEntry.emissive = false;
         target.put(sourceMaskKey("minecraft:open_eyeblossom", false, "north", "", "minecraft:block/open_eyeblossom"), sourceEntry);
      }
   }

   private static void addOpenEyeblossomEmissiveDefault(Map<String, SourceMaskEntry> target) {
      if (target != null) {
         String key = sourceMaskKey("minecraft:open_eyeblossom", false, "north", "", "minecraft:block/open_eyeblossom_emissive");
         if (!target.containsKey(key)) {
            for(SourceMaskEntry entry : target.values()) {
               if (entry != null && "minecraft:open_eyeblossom".equals(entry.sourceId) && ("minecraft:block/open_eyeblossom_emissive".equals(normalizeLayerSpriteId(entry.layerSpriteId)) || "minecraft:block/open_eyeblossom_emissive".equals(entry.spriteId))) {
                  return;
               }
            }

            SourceMaskEntry sourceEntry = new SourceMaskEntry();
            sourceEntry.sourceId = "minecraft:open_eyeblossom";
            sourceEntry.fluid = false;
            sourceEntry.face = "north";
            sourceEntry.stateKey = "";
            sourceEntry.layerSpriteId = "minecraft:block/open_eyeblossom_emissive";
            sourceEntry.spriteId = "minecraft:block/open_eyeblossom_emissive";
            sourceEntry.width = 16;
            sourceEntry.height = 16;
            sourceEntry.bits = "//////////////////////////////////////////8=";
            sourceEntry.emissive = true;
            target.put(key, sourceEntry);
         }
      }
   }

   private static void addInferredSourceMasks(Map<String, SourceMaskEntry> target, Map<String, MaskEntry> spriteMasks) {
      for(Map.Entry<String, MaskEntry> entry : spriteMasks.entrySet()) {
         if (entry.getKey() != null && entry.getValue() != null) {
            InferredSource inferred = inferSourceFromSprite((String)entry.getKey());
            if (inferred != null) {
               for(Direction face : Direction.values()) {
                  if ((!inferred.stillFluid || face == Direction.UP || face == Direction.DOWN) && (!inferred.flowingFluid || face != Direction.UP && face != Direction.DOWN)) {
                     SourceMaskEntry sourceEntry = new SourceMaskEntry();
                     sourceEntry.sourceId = inferred.sourceId;
                     sourceEntry.fluid = inferred.fluid;
                     sourceEntry.face = face.getName();
                     sourceEntry.stateKey = "";
                     sourceEntry.layerSpriteId = "";
                     sourceEntry.spriteId = (String)entry.getKey();
                     sourceEntry.width = ((MaskEntry)entry.getValue()).width;
                     sourceEntry.height = ((MaskEntry)entry.getValue()).height;
                     sourceEntry.bits = ((MaskEntry)entry.getValue()).bits;
                     target.putIfAbsent(sourceMaskKey(sourceEntry.sourceId, sourceEntry.fluid, sourceEntry.face, sourceEntry.stateKey), sourceEntry);
                  }
               }
            }
         }
      }

   }

   private static InferredSource inferSourceFromSprite(String spriteId) {
      int sep = spriteId.indexOf(58);
      if (sep > 0 && sep < spriteId.length() - 1) {
         String namespace = spriteId.substring(0, sep);
         String path = spriteId.substring(sep + 1);
         if (!path.startsWith("block/")) {
            return null;
         } else {
            InferredSource var10000;
            switch (path.substring("block/".length())) {
               case "water_still" -> var10000 = new InferredSource(namespace + ":water", true, true, false);
               case "water_flow" -> var10000 = new InferredSource(namespace + ":flowing_water", true, false, true);
               case "lava_still" -> var10000 = new InferredSource(namespace + ":lava", true, true, false);
               case "lava_flow" -> var10000 = new InferredSource(namespace + ":flowing_lava", true, false, true);
               default -> var10000 = new InferredSource(namespace + ":" + blockPath, false, false, false);
            }

            return var10000;
         }
      } else {
         return null;
      }
   }

   private static String sourceMaskKey(String sourceId, boolean fluid, String face) {
      return sourceMaskKey(sourceId, fluid, face, "");
   }

   private static String sourceMaskKey(String sourceId, boolean fluid, String face, String stateKey) {
      return sourceMaskKey(sourceId, fluid, face, stateKey, "");
   }

   private static String sourceMaskKey(String sourceId, boolean fluid, String face, String stateKey, String layerSpriteId) {
      String normalizedStateKey = normalizeStateKey(stateKey);
      String normalizedLayerSpriteId = normalizeLayerSpriteId(layerSpriteId);
      String sourcePart = normalizedStateKey.isBlank() ? sourceId : normalizedStateKey;
      return (fluid ? "fluid:" : "block:") + sourcePart + "#" + face.toLowerCase(Locale.ROOT) + (normalizedLayerSpriteId.isBlank() ? "" : "@" + normalizedLayerSpriteId);
   }

   private static String normalizeStateKey(String stateKey) {
      return stateKey == null ? "" : stateKey.trim();
   }

   private static String normalizeLayerSpriteId(String layerSpriteId) {
      return layerSpriteId == null ? "" : layerSpriteId.trim();
   }

   private static boolean[] decodeMask(MaskEntry entry, int width, int height) {
      try {
         byte[] raw = Base64.getDecoder().decode(entry.bits);
         boolean[] source = unpackBits(raw, entry.width * entry.height);
         if (source == null) {
            return null;
         } else {
            return entry.width == width && entry.height == height ? source : resizeNearest(source, entry.width, entry.height, width, height);
         }
      } catch (IllegalArgumentException var5) {
         return null;
      }
   }

   private static boolean[] decodeSourceMask(SourceMaskEntry entry, int width, int height) {
      MaskEntry mask = new MaskEntry();
      mask.width = entry.width;
      mask.height = entry.height;
      mask.bits = entry.bits;
      return decodeMask(mask, width, height);
   }

   private static String encodeMask(boolean[] mask) {
      byte[] packed = packBits(mask);
      return Base64.getEncoder().encodeToString(packed);
   }

   private static byte[] packBits(boolean[] mask) {
      byte[] out = new byte[(mask.length + 7) / 8];

      for(int i = 0; i < mask.length; ++i) {
         if (mask[i]) {
            int byteIndex = i >>> 3;
            int bitIndex = i & 7;
            out[byteIndex] = (byte)(out[byteIndex] | 1 << bitIndex);
         }
      }

      return out;
   }

   private static boolean[] unpackBits(byte[] packed, int expectedLength) {
      if (packed == null) {
         return null;
      } else {
         boolean[] out = new boolean[expectedLength];

         for(int i = 0; i < expectedLength; ++i) {
            int byteIndex = i >>> 3;
            if (byteIndex >= packed.length) {
               out[i] = false;
            } else {
               int bitIndex = i & 7;
               out[i] = (packed[byteIndex] & 1 << bitIndex) != 0;
            }
         }

         return out;
      }
   }

   private static boolean[] resizeNearest(boolean[] source, int sourceW, int sourceH, int targetW, int targetH) {
      boolean[] out = new boolean[targetW * targetH];

      for(int y = 0; y < targetH; ++y) {
         int sampleY = Math.min(sourceH - 1, Math.max(0, (int)((long)y * (long)sourceH / (long)targetH)));

         for(int x = 0; x < targetW; ++x) {
            int sampleX = Math.min(sourceW - 1, Math.max(0, (int)((long)x * (long)sourceW / (long)targetW)));
            out[y * targetW + x] = source[sampleY * sourceW + sampleX];
         }
      }

      return out;
   }

   private static boolean[] filledMask(int width, int height, boolean value) {
      boolean[] out = new boolean[Math.max(0, width * height)];
      if (value) {
         Arrays.fill(out, true);
      }

      return out;
   }

   private static Data loadBundledDefaults() {
      JsonObject canonical = ShineDefaultBaseline.section("bloomMasks");
      if (canonical != null) {
         try {
            Data loaded = (Data)GSON.fromJson(canonical, Data.class);
            if (loaded != null) {
               return sanitize(loaded);
            }
         } catch (Exception exception) {
            BloomMod.LOGGER.error("Failed to read bloom-mask defaults from the canonical Shine Default baseline.", exception);
         }
      }

      try {
         InputStream stream = BloomMaskConfig.class.getResourceAsStream("/assets/shine/defaults/shine_masks.json");

         Data var4;
         label92: {
            Data var12;
            label100: {
               try {
                  if (stream == null) {
                     var12 = new Data();
                     break label100;
                  }

                  Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);

                  label78: {
                     try {
                        Data loaded = (Data)GSON.fromJson(reader, Data.class);
                        if (loaded == null) {
                           break label78;
                        }

                        var4 = sanitize(loaded);
                     } catch (Throwable var8) {
                        try {
                           reader.close();
                        } catch (Throwable var6) {
                           var8.addSuppressed(var6);
                        }

                        throw var8;
                     }

                     reader.close();
                     break label92;
                  }

                  reader.close();
               } catch (Throwable var9) {
                  if (stream != null) {
                     try {
                        stream.close();
                     } catch (Throwable var5) {
                        var9.addSuppressed(var5);
                     }
                  }

                  throw var9;
               }

               if (stream != null) {
                  stream.close();
               }

               return new Data();
            }

            if (stream != null) {
               stream.close();
            }

            return var12;
         }

         if (stream != null) {
            stream.close();
         }

         return var4;
      } catch (Exception e) {
         BloomMod.LOGGER.error("Failed to read bundled Shine mask defaults, using empty mask defaults.", e);
         return new Data();
      }
   }

   private static boolean isAllTrue(boolean[] mask) {
      for(boolean value : mask) {
         if (!value) {
            return false;
         }
      }

      return true;
   }

   private static LinkedHashSet<String> linkedSpriteIds(String spriteId) {
      LinkedHashSet<String> out = new LinkedHashSet();
      if (spriteId != null && !spriteId.isBlank()) {
         out.add(spriteId);
         int sep = spriteId.indexOf(58);
         if (sep > 0 && sep < spriteId.length() - 1) {
            String namespace = spriteId.substring(0, sep);
            String path = spriteId.substring(sep + 1);
            String var10000;
            switch (path.startsWith("block/") ? path.substring("block/".length()) : path) {
               case "wall_torch":
                  var10000 = "torch";
                  break;
               case "torch":
                  var10000 = "wall_torch";
                  break;
               case "copper_torch":
               case "copper_wall_torch":
                  var10000 = "torch";
                  break;
               case "soul_wall_torch":
                  var10000 = "soul_torch";
                  break;
               case "soul_torch":
                  var10000 = "soul_wall_torch";
                  break;
               case "redstone_wall_torch":
                  var10000 = "redstone_torch";
                  break;
               case "redstone_torch":
                  var10000 = "redstone_wall_torch";
                  break;
               case "redstone_wall_torch_off":
                  var10000 = "redstone_torch_off";
                  break;
               case "redstone_torch_off":
                  var10000 = "redstone_wall_torch_off";
                  break;
               default:
                  var10000 = null;
            }

            String mapped = var10000;
            if (mapped != null) {
               String prefix = path.startsWith("block/") ? "block/" : "";
               out.add(namespace + ":" + prefix + mapped);
            }

            if ("torch".equals(unprefixed) || "wall_torch".equals(unprefixed)) {
               String prefix = path.startsWith("block/") ? "block/" : "";
               out.add(namespace + ":" + prefix + "copper_torch");
               out.add(namespace + ":" + prefix + "copper_wall_torch");
            }

            int underscore = unprefixed.lastIndexOf(95);
            if (underscore > 0 && underscore < unprefixed.length() - 1) {
               numericSuffix = (boolean)1;

               for(int i = underscore + 1; i < unprefixed.length(); ++i) {
                  if (!Character.isDigit(unprefixed.charAt(i))) {
                     numericSuffix = (boolean)0;
                     break;
                  }
               }

               if (numericSuffix) {
                  String prefix = path.startsWith("block/") ? "block/" : "";
                  out.add(namespace + ":" + prefix + unprefixed.substring(0, underscore));
               }
            }

            return out;
         } else {
            return out;
         }
      } else {
         return out;
      }
   }

   private static MaskEntry findFamilyFallbackEntry(String spriteId) {
      if (spriteId != null && !spriteId.isBlank() && !data.spriteMasks.isEmpty()) {
         String normalized = spriteId.toLowerCase(Locale.ROOT);
         if (normalized.contains("torch")) {
            for(String preferred : new String[]{"minecraft:block/torch", "minecraft:block/wall_torch", "minecraft:block/copper_torch", "minecraft:block/copper_wall_torch", "minecraft:block/redstone_torch", "minecraft:block/redstone_wall_torch", "minecraft:block/redstone_torch_off", "minecraft:block/redstone_wall_torch_off", "minecraft:block/soul_torch", "minecraft:block/soul_wall_torch"}) {
               MaskEntry entry = (MaskEntry)data.spriteMasks.get(preferred);
               if (entry != null) {
                  return entry;
               }
            }

            for(Map.Entry<String, MaskEntry> entry : data.spriteMasks.entrySet()) {
               if (entry.getKey() != null && ((String)entry.getKey()).toLowerCase(Locale.ROOT).contains("torch")) {
                  return (MaskEntry)entry.getValue();
               }
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public static final class Data {
      public Map<String, MaskEntry> spriteMasks = new LinkedHashMap();
      public Map<String, SourceMaskEntry> sourceMasks = new LinkedHashMap();
      public Map<String, MaskEntry> entityTextureMasks = new LinkedHashMap();

      public static Data defaults() {
         return BloomMaskConfig.loadBundledDefaults();
      }

      public Data copy() {
         Data copy = new Data();
         if (this.spriteMasks != null) {
            for(Map.Entry<String, MaskEntry> entry : this.spriteMasks.entrySet()) {
               if (entry.getValue() != null) {
                  copy.spriteMasks.put((String)entry.getKey(), ((MaskEntry)entry.getValue()).copy());
               }
            }
         }

         if (this.sourceMasks != null) {
            for(Map.Entry<String, SourceMaskEntry> entry : this.sourceMasks.entrySet()) {
               if (entry.getValue() != null) {
                  copy.sourceMasks.put((String)entry.getKey(), ((SourceMaskEntry)entry.getValue()).copy());
               }
            }
         }

         if (this.entityTextureMasks != null) {
            for(Map.Entry<String, MaskEntry> entry : this.entityTextureMasks.entrySet()) {
               if (entry.getValue() != null) {
                  copy.entityTextureMasks.put((String)entry.getKey(), ((MaskEntry)entry.getValue()).copy());
               }
            }
         }

         return copy;
      }
   }

   public static final class MaskEntry {
      public int width;
      public int height;
      public String bits;

      public MaskEntry copy() {
         MaskEntry copy = new MaskEntry();
         copy.width = this.width;
         copy.height = this.height;
         copy.bits = this.bits;
         return copy;
      }
   }

   public static final class SourceMaskEntry {
      public String sourceId;
      public boolean fluid;
      public String face;
      public String stateKey;
      public String layerSpriteId;
      public String spriteId;
      public int width;
      public int height;
      public String bits;
      public boolean emissive;

      public SourceMaskEntry copy() {
         SourceMaskEntry copy = new SourceMaskEntry();
         copy.sourceId = this.sourceId;
         copy.fluid = this.fluid;
         copy.face = this.face;
         copy.stateKey = this.stateKey == null ? "" : this.stateKey;
         copy.layerSpriteId = this.layerSpriteId == null ? "" : this.layerSpriteId;
         copy.spriteId = this.spriteId;
         copy.width = this.width;
         copy.height = this.height;
         copy.bits = this.bits;
         copy.emissive = this.emissive;
         return copy;
      }
   }

   public static record ResolvedMaskEntry(MaskEntry mask, boolean emissive) {
   }

   private static record InferredSource(String sourceId, boolean fluid, boolean stillFluid, boolean flowingFluid) {
   }

   @FunctionalInterface
   public interface SourceMaskSpriteResolver {
      String resolve(String var1, boolean var2, String var3, String var4);
   }
}
