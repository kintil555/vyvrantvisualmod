package com.bloom.client.config;

import com.bloom.BloomMod;
import com.bloom.client.resource.ShineResourceDefaults;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

public final class BloomConfig {
   public static final int MAX_BLUR_PASSES = 4;
   public static final double MAX_STRENGTH = (double)10.0F;
   public static final double LEGACY_MAX_RADIUS = (double)500.0F;
   public static final double MAX_RADIUS = (double)700.0F;
   public static final int RADIUS_SCALE_VERSION = 2;
   public static final double MIN_BLOOM_DISTANCE = (double)1.0F;
   public static final double MAX_BLOOM_DISTANCE = (double)256.0F;
   public static final double MIN_HIGHLIGHT_CLAMP = 0.01;
   public static final double MAX_HIGHLIGHT_CLAMP = (double)4.0F;
   public static final double MIN_SOFT_KNEE = 0.01;
   public static final double MAX_SOFT_KNEE = (double)1.0F;
   public static final double MIN_SOURCE_STRENGTH = (double)0.0F;
   public static final double MAX_SOURCE_STRENGTH = (double)500.0F;
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();
   private static final String DEFAULT_CONFIG_RESOURCE = "/assets/shine/defaults/shine.json";
   private static Data data = BloomConfig.Data.defaults();
   private static long version;

   private BloomConfig() {
   }

   public static Data get() {
      return data;
   }

   public static Data copy() {
      return data.copy();
   }

   public static Data defaults() {
      return BloomConfig.Data.defaults();
   }

   public static void set(Data newData) {
      data = sanitize(newData, (JsonObject)null);
      ++version;
   }

   public static void load() {
      Path configPath = configPath();
      Path legacyConfigPath = legacyConfigPath();
      Path pathToLoad = Files.exists(configPath, new LinkOption[0]) ? configPath : (Files.exists(legacyConfigPath, new LinkOption[0]) ? legacyConfigPath : null);
      if (pathToLoad == null) {
         data = BloomConfig.Data.defaults();
         ++version;
         save();
      } else {
         try {
            JsonObject root = (JsonObject)ShineAtomicFiles.readUtf8WithBackup(pathToLoad, (reader) -> JsonParser.parseReader(reader).getAsJsonObject());
            boolean shouldSaveMigratedConfig = shouldMigrateBrokenExtendedRadiusScale(root);
            boolean shouldRemoveDistanceStability = root.has("distanceStability");
            Data loaded = (Data)GSON.fromJson(root, Data.class);
            data = sanitize(loaded, root);
            ++version;
            if (!pathToLoad.equals(configPath) || shouldSaveMigratedConfig || shouldRemoveDistanceStability) {
               save();
               if (!pathToLoad.equals(configPath)) {
                  BloomMod.LOGGER.info("Migrated legacy bloom.json config to shine.json.");
               }

               if (shouldSaveMigratedConfig) {
                  BloomMod.LOGGER.info("Migrated Shine bloom radius to the current extended-radius scale.");
               }

               if (shouldRemoveDistanceStability) {
                  BloomMod.LOGGER.info("Removed the retired Shine bloom distance-stability setting.");
               }
            }
         } catch (Exception e) {
            BloomMod.LOGGER.error("Failed to read Shine config, using defaults.", e);
            data = BloomConfig.Data.defaults();
            ++version;
         }

      }
   }

   public static long version() {
      return version * 31L + ShineResourceDefaults.version();
   }

   public static void save() {
      try {
         ShineAtomicFiles.writeUtf8(configPath(), (writer) -> GSON.toJson(data, writer));
         ShinePresetStateManager.captureLocalOverridesAfterUserSave();
      } catch (IOException e) {
         BloomMod.LOGGER.error("Failed to write Shine config.", e);
      }

   }

   private static Path configPath() {
      return FabricLoader.getInstance().getConfigDir().resolve("shine.json");
   }

   private static Path legacyConfigPath() {
      return FabricLoader.getInstance().getConfigDir().resolve("bloom.json");
   }

   private static Data sanitize(Data input, JsonObject rawRoot) {
      Data safe = input == null ? BloomConfig.Data.defaults() : input.copy();
      safe.threshold = clamp(safe.threshold, (double)0.0F, (double)1.0F);
      safe.strength = clamp(safe.strength, (double)0.0F, (double)10.0F);
      if (shouldMigrateBrokenExtendedRadiusScale(rawRoot)) {
         safe.radius *= 0.7142857142857143;
      }

      safe.radius = clamp(safe.radius, (double)0.0F, (double)700.0F);
      safe.tinyRadius = clamp(safe.tinyRadius, (double)0.0F, (double)700.0F);
      safe.broadRadius = clamp(safe.broadRadius, (double)0.0F, (double)700.0F);
      safe.radiusScaleVersion = 2;
      safe.blurPassCount = (int)clamp((long)safe.blurPassCount, 1L, 4L);
      if (safe.bloomDistance <= (double)0.0F) {
         safe.bloomDistance = BloomConfig.Data.defaults().bloomDistance;
      }

      safe.bloomDistance = clamp(safe.bloomDistance, (double)1.0F, (double)256.0F);
      if (safe.highlightClamp <= (double)0.0F) {
         safe.highlightClamp = BloomConfig.Data.defaults().highlightClamp;
      }

      safe.highlightClamp = clamp(safe.highlightClamp, 0.01, (double)4.0F);
      if (safe.softKnee <= (double)0.0F) {
         safe.softKnee = BloomConfig.Data.defaults().softKnee;
      }

      safe.softKnee = clamp(safe.softKnee, 0.01, (double)1.0F);
      safe.defaultLightSourceStrength = clamp(safe.defaultLightSourceStrength, (double)0.0F, (double)500.0F);
      safe.defaultNonLightStrength = clamp(safe.defaultNonLightStrength, (double)0.0F, (double)500.0F);
      safe.defaultEntityTextureStrength = clamp(safe.defaultEntityTextureStrength, (double)0.0F, (double)500.0F);
      safe.defaultParticleStrength = clamp(safe.defaultParticleStrength, (double)0.0F, (double)500.0F);
      Map<String, Double> sanitizedOverrides = new LinkedHashMap();
      if (safe.sourceStrengthOverrides != null) {
         for(Map.Entry<String, Double> entry : safe.sourceStrengthOverrides.entrySet()) {
            sanitizeOverrideEntry(entry, sanitizedOverrides);
         }
      }

      if (sanitizedOverrides.isEmpty() && rawRoot != null && rawRoot.has("blockStrengthOverrides") && rawRoot.get("blockStrengthOverrides").isJsonObject()) {
         for(Map.Entry<String, JsonElement> legacyEntry : rawRoot.getAsJsonObject("blockStrengthOverrides").entrySet()) {
            if (legacyEntry != null && legacyEntry.getKey() != null && legacyEntry.getValue() != null && ((JsonElement)legacyEntry.getValue()).isJsonPrimitive()) {
               try {
                  double value = ((JsonElement)legacyEntry.getValue()).getAsDouble();
                  sanitizeOverrideEntry(Map.entry((String)legacyEntry.getKey(), value), sanitizedOverrides);
               } catch (Exception var8) {
               }
            }
         }

         BloomMod.LOGGER.info("Migrated legacy blockStrengthOverrides to sourceStrengthOverrides in shine.json.");
      }

      for(Map.Entry<String, Double> baseline : BloomConfig.Data.defaultSourceStrengthOverrides().entrySet()) {
         sanitizedOverrides.putIfAbsent((String)baseline.getKey(), clamp((Double)baseline.getValue(), (double)0.0F, (double)500.0F));
      }

      safe.sourceStrengthOverrides = sanitizedOverrides;
      safe.stateSourceStrengthOverrides = sanitizeStateStrengthMap(safe.stateSourceStrengthOverrides);
      safe.entityTextureStrengthOverrides = sanitizeStrengthMap(safe.entityTextureStrengthOverrides);
      safe.particleStrengthOverrides = sanitizeStrengthMap(safe.particleStrengthOverrides);
      safe.sourceRadiusProfiles = sanitizeRadiusProfileMap(safe.sourceRadiusProfiles);
      return safe;
   }

   private static Map<String, Integer> sanitizeRadiusProfileMap(Map<String, Integer> input) {
      Map<String, Integer> sanitized = new LinkedHashMap();
      if (input == null) {
         return sanitized;
      } else {
         for(Map.Entry<String, Integer> entry : input.entrySet()) {
            if (entry != null && entry.getKey() != null && entry.getValue() != null) {
               try {
                  Identifier.parse((String)entry.getKey());
               } catch (Exception var5) {
                  continue;
               }

               int profile = Math.max(0, Math.min(2, (Integer)entry.getValue()));
               if (profile != 0) {
                  sanitized.put((String)entry.getKey(), profile);
               }
            }
         }

         return sanitized;
      }
   }

   private static boolean shouldMigrateBrokenExtendedRadiusScale(JsonObject rawRoot) {
      if (rawRoot != null && !rawRoot.has("radiusScaleVersion")) {
         return rawRoot.has("distanceStability") || rawRoot.has("defaultEntityTextureStrength") || rawRoot.has("defaultParticleStrength") || rawRoot.has("entityTextureStrengthOverrides") || rawRoot.has("particleStrengthOverrides");
      } else {
         return false;
      }
   }

   private static Map<String, Double> sanitizeStrengthMap(Map<String, Double> input) {
      Map<String, Double> sanitized = new LinkedHashMap();
      if (input == null) {
         return sanitized;
      } else {
         for(Map.Entry<String, Double> entry : input.entrySet()) {
            sanitizeOverrideEntry(entry, sanitized);
         }

         return sanitized;
      }
   }

   private static Map<String, Double> sanitizeStateStrengthMap(Map<String, Double> input) {
      Map<String, Double> sanitized = new LinkedHashMap();
      if (input == null) {
         return sanitized;
      } else {
         for(Map.Entry<String, Double> entry : input.entrySet()) {
            if (entry != null && entry.getKey() != null && entry.getValue() != null) {
               String key = (String)entry.getKey();
               int bracket = key.indexOf(91);
               String blockId = bracket < 0 ? key : key.substring(0, bracket);

               try {
                  Identifier.parse(blockId);
               } catch (Exception var8) {
                  continue;
               }

               sanitized.put(key, clamp((Double)entry.getValue(), (double)0.0F, (double)500.0F));
            }
         }

         return sanitized;
      }
   }

   private static void sanitizeOverrideEntry(Map.Entry<String, Double> entry, Map<String, Double> out) {
      if (entry != null && entry.getKey() != null && entry.getValue() != null) {
         try {
            Identifier.parse((String)entry.getKey());
         } catch (Exception var3) {
            return;
         }

         out.put((String)entry.getKey(), clamp((Double)entry.getValue(), (double)0.0F, (double)500.0F));
      }
   }

   private static double clamp(double value, double min, double max) {
      return Math.max(min, Math.min(max, value));
   }

   private static long clamp(long value, long min, long max) {
      return Math.max(min, Math.min(max, value));
   }

   private static Data loadBundledDefaults() {
      JsonObject canonical = ShineDefaultBaseline.section("bloom");
      if (canonical != null) {
         try {
            Data loaded = (Data)GSON.fromJson(canonical, Data.class);
            if (loaded != null) {
               return sanitize(loaded, (JsonObject)null);
            }
         } catch (Exception exception) {
            BloomMod.LOGGER.error("Failed to read bloom defaults from the canonical Shine Default baseline.", exception);
         }
      }

      try {
         InputStream stream = BloomConfig.class.getResourceAsStream("/assets/shine/defaults/shine.json");

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

                        var4 = sanitize(loaded, (JsonObject)null);
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
         BloomMod.LOGGER.error("Failed to read bundled Shine defaults, using hardcoded defaults.", e);
         return new Data();
      }
   }

   public static final class Data {
      public boolean enabled = true;
      public double strength = (double)8.0F;
      public double threshold = 0.15;
      public double radius = (double)500.0F;
      public double tinyRadius = (double)90.0F;
      public double broadRadius = (double)700.0F;
      public int radiusScaleVersion = 2;
      public int blurPassCount = 2;
      public double bloomDistance = (double)256.0F;
      public double highlightClamp = 0.28;
      public double softKnee = 0.2;
      public double defaultLightSourceStrength = (double)50.0F;
      public double defaultNonLightStrength = (double)0.0F;
      public double defaultEntityTextureStrength = (double)0.0F;
      public double defaultParticleStrength = (double)0.0F;
      public Map<String, Double> sourceStrengthOverrides = defaultSourceStrengthOverrides();
      public Map<String, Double> stateSourceStrengthOverrides = new LinkedHashMap();
      public Map<String, Double> entityTextureStrengthOverrides = new LinkedHashMap();
      public Map<String, Double> particleStrengthOverrides = new LinkedHashMap();
      public Map<String, Integer> sourceRadiusProfiles = new LinkedHashMap();

      public static Data defaults() {
         return BloomConfig.loadBundledDefaults();
      }

      public static LinkedHashMap<String, Double> defaultSourceStrengthOverrides() {
         LinkedHashMap<String, Double> defaults = new LinkedHashMap();
         defaults.put("minecraft:water", (double)0.0F);
         defaults.put("minecraft:flowing_water", (double)0.0F);
         defaults.put("minecraft:sculk", (double)500.0F);
         defaults.put("minecraft:sculk_vein", (double)150.0F);
         defaults.put("minecraft:amethyst_cluster", (double)100.0F);
         defaults.put("minecraft:large_amethyst_bud", (double)100.0F);
         defaults.put("minecraft:medium_amethyst_bud", (double)100.0F);
         defaults.put("minecraft:small_amethyst_bud", (double)100.0F);
         defaults.put("minecraft:glow_lichen", (double)400.0F);
         defaults.put("minecraft:warped_stem", (double)100.0F);
         defaults.put("minecraft:warped_fungus", (double)300.0F);
         defaults.put("minecraft:nether_portal", (double)75.0F);
         defaults.put("minecraft:crimson_stem", (double)175.0F);
         defaults.put("minecraft:twisting_vines", (double)25.0F);
         defaults.put("minecraft:twisting_vines_plant", (double)25.0F);
         defaults.put("minecraft:weeping_vines", (double)75.0F);
         defaults.put("minecraft:lava", (double)75.0F);
         defaults.put("minecraft:flowing_lava", (double)75.0F);
         defaults.put("minecraft:crimson_fungus", (double)150.0F);
         defaults.put("minecraft:nether_wart", (double)50.0F);
         defaults.put("minecraft:crying_obsidian", (double)75.0F);
         defaults.put("minecraft:closed_eyeblossom", (double)50.0F);
         defaults.put("minecraft:open_eyeblossom", (double)50.0F);
         defaults.put("minecraft:resin_clump", (double)150.0F);
         defaults.put("minecraft:chorus_flower", (double)150.0F);
         defaults.put("minecraft:powder_snow", (double)25.0F);
         defaults.put("minecraft:snow", (double)25.0F);
         defaults.put("minecraft:snow_block", (double)25.0F);
         defaults.put("minecraft:soul_torch", (double)220.0F);
         defaults.put("minecraft:soul_wall_torch", (double)220.0F);
         defaults.put("minecraft:redstone_torch", (double)120.0F);
         defaults.put("minecraft:redstone_wall_torch", (double)120.0F);
         defaults.put("minecraft:torch", (double)499.0F);
         defaults.put("minecraft:wall_torch", (double)499.0F);
         defaults.put("minecraft:firefly_bush", (double)500.0F);
         defaults.put("minecraft:lantern", (double)200.0F);
         defaults.put("minecraft:weeping_vines_plant", (double)100.0F);
         defaults.put("minecraft:nether_gold_ore", (double)50.0F);
         defaults.put("minecraft:soul_fire", (double)250.0F);
         defaults.put("minecraft:soul_campfire", (double)0.0F);
         defaults.put("minecraft:smoker", (double)0.0F);
         defaults.put("minecraft:blast_furnace", (double)0.0F);
         defaults.put("minecraft:furnace", (double)0.0F);
         return defaults;
      }

      public Data copy() {
         Data copy = new Data();
         copy.enabled = this.enabled;
         copy.strength = this.strength;
         copy.threshold = this.threshold;
         copy.radius = this.radius;
         copy.tinyRadius = this.tinyRadius;
         copy.broadRadius = this.broadRadius;
         copy.radiusScaleVersion = this.radiusScaleVersion;
         copy.blurPassCount = this.blurPassCount;
         copy.bloomDistance = this.bloomDistance;
         copy.highlightClamp = this.highlightClamp;
         copy.softKnee = this.softKnee;
         copy.defaultLightSourceStrength = this.defaultLightSourceStrength;
         copy.defaultNonLightStrength = this.defaultNonLightStrength;
         copy.defaultEntityTextureStrength = this.defaultEntityTextureStrength;
         copy.defaultParticleStrength = this.defaultParticleStrength;
         copy.sourceStrengthOverrides = this.sourceStrengthOverrides == null ? new LinkedHashMap() : new LinkedHashMap(this.sourceStrengthOverrides);
         copy.stateSourceStrengthOverrides = this.stateSourceStrengthOverrides == null ? new LinkedHashMap() : new LinkedHashMap(this.stateSourceStrengthOverrides);
         copy.entityTextureStrengthOverrides = this.entityTextureStrengthOverrides == null ? new LinkedHashMap() : new LinkedHashMap(this.entityTextureStrengthOverrides);
         copy.particleStrengthOverrides = this.particleStrengthOverrides == null ? new LinkedHashMap() : new LinkedHashMap(this.particleStrengthOverrides);
         copy.sourceRadiusProfiles = this.sourceRadiusProfiles == null ? new LinkedHashMap() : new LinkedHashMap(this.sourceRadiusProfiles);
         return copy;
      }
   }
}
