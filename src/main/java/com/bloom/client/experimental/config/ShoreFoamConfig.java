package com.bloom.client.experimental.config;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Standalone shore foam settings, extracted from the mod's larger (unused) experimental
 * config system so the shore foam feature has no dependency on it.
 */
public final class ShoreFoamConfig {
   public boolean enabled = false;
   public double opacity = 0.65;
   public double thickness = 0.08;
   public double speed = 1.0;
   public double scale = 3.0;
   public double breakup = 0.55;
   public int color = 16777215;
   public Map<String, BiomeProfile> biomes = new LinkedHashMap<>();
   public java.util.List<Integer> savedPickerColors = new java.util.ArrayList<>();

   public ShoreFoamConfig copy() {
      ShoreFoamConfig copy = new ShoreFoamConfig();
      copy.enabled = this.enabled;
      copy.opacity = this.opacity;
      copy.thickness = this.thickness;
      copy.speed = this.speed;
      copy.scale = this.scale;
      copy.breakup = this.breakup;
      copy.color = this.color;
      copy.biomes = new LinkedHashMap<>();
      for (Map.Entry<String, BiomeProfile> entry : this.biomes.entrySet()) {
         copy.biomes.put(entry.getKey(), entry.getValue().copy());
      }
      return copy;
   }

   public BiomeProfile defaultProfile() {
      return BiomeProfile.fromGlobal(this).sanitized();
   }

   public BiomeProfile resolvedProfile(String biomeId) {
      BiomeProfile fallback = this.defaultProfile();
      String key = normalizeBiomeId(biomeId);
      if (key.isBlank() || this.biomes == null) {
         return fallback;
      }
      BiomeProfile override = this.biomes.get(key);
      return override == null ? fallback : override.copy().sanitized();
   }

   public static String normalizeBiomeId(String raw) {
      if (raw == null || raw.isBlank()) {
         return "";
      }
      return raw.trim().toLowerCase(Locale.ROOT);
   }

   private static double clamp(double value, double min, double max) {
      return Math.max(min, Math.min(max, value));
   }

   public static final class BiomeProfile {
      public boolean enabled = false;
      public double opacity = 0.65;
      public double thickness = 0.08;
      public double speed = 1.0;
      public double scale = 3.0;
      public double breakup = 0.55;
      public int color = 16777215;

      public static BiomeProfile defaults() {
         return new BiomeProfile();
      }

      public static BiomeProfile fromGlobal(ShoreFoamConfig config) {
         BiomeProfile profile = new BiomeProfile();
         if (config != null) {
            profile.enabled = config.enabled;
            profile.opacity = config.opacity;
            profile.thickness = config.thickness;
            profile.speed = config.speed;
            profile.scale = config.scale;
            profile.breakup = config.breakup;
            profile.color = config.color;
         }
         return profile;
      }

      public BiomeProfile copy() {
         BiomeProfile copy = new BiomeProfile();
         copy.enabled = this.enabled;
         copy.opacity = this.opacity;
         copy.thickness = this.thickness;
         copy.speed = this.speed;
         copy.scale = this.scale;
         copy.breakup = this.breakup;
         copy.color = this.color;
         return copy;
      }

      public BiomeProfile sanitized() {
         BiomeProfile safe = this.copy();
         safe.opacity = ShoreFoamConfig.clamp(safe.opacity, 0.0, 1.0);
         safe.thickness = ShoreFoamConfig.clamp(safe.thickness, 0.01, 0.35);
         safe.speed = ShoreFoamConfig.clamp(safe.speed, 0.0, 4.0);
         safe.scale = ShoreFoamConfig.clamp(safe.scale, 0.5, 12.0);
         safe.breakup = ShoreFoamConfig.clamp(safe.breakup, 0.0, 1.0);
         safe.color &= 16777215;
         return safe;
      }
   }
}
