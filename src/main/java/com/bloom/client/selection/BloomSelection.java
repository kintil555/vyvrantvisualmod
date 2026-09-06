package com.bloom.client.selection;

import com.bloom.client.config.BloomConfig;
import com.google.common.collect.UnmodifiableIterator;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;

public final class BloomSelection {
   private static final Set<Identifier> SELECTED_FLUID_IDS = Set.of(Identifier.parse("minecraft:lava"), Identifier.parse("minecraft:flowing_lava"), Identifier.parse("minecraft:water"), Identifier.parse("minecraft:flowing_water"));
   private static final ConcurrentMap<Block, Identifier> BLOCK_ID_CACHE = new ConcurrentHashMap();
   private static final ConcurrentMap<Fluid, Identifier> FLUID_ID_CACHE = new ConcurrentHashMap();
   private static final ConcurrentMap<Block, Boolean> LIGHT_SOURCE_LOOK_CACHE = new ConcurrentHashMap();
   private static final ConcurrentMap<String, Double> BLOCK_STRENGTH_CACHE = new ConcurrentHashMap();
   private static final ConcurrentMap<Fluid, Double> FLUID_STRENGTH_CACHE = new ConcurrentHashMap();
   private static final ConcurrentMap<String, Double> ENTITY_STRENGTH_CACHE = new ConcurrentHashMap();
   private static final ConcurrentMap<String, Double> PARTICLE_STRENGTH_CACHE = new ConcurrentHashMap();
   private static volatile long cachedConfigVersion = Long.MIN_VALUE;
   private static volatile long cachedParticleEnabledVersion = Long.MIN_VALUE;
   private static volatile boolean cachedParticleEnabled;

   private BloomSelection() {
   }

   public static double getBlockSourceStrength(BlockState blockState) {
      refreshCachesIfNeeded();
      return (Double)BLOCK_STRENGTH_CACHE.computeIfAbsent(blockStateKey(blockState), (ignored) -> computeBlockSourceStrength(blockState));
   }

   private static double computeBlockSourceStrength(BlockState blockState) {
      BloomConfig.Data config = BloomConfig.get();
      Block block = blockState.getBlock();
      Identifier blockId = blockId(block);
      String id = blockId.toString();
      Double stateOverride = config.stateSourceStrengthOverrides == null ? null : (Double)config.stateSourceStrengthOverrides.get(blockStateKey(blockState));
      double strength;
      if (stateOverride != null) {
         strength = clampSourceStrength(stateOverride);
      } else {
         Double override = resolveLinkedSourceOverride(config, id);
         if (override != null) {
            strength = clampSourceStrength(override);
         } else {
            double fallback = blockLooksLikeLightSource(blockState.getBlock(), blockState) ? config.defaultLightSourceStrength : config.defaultNonLightStrength;
            strength = clampSourceStrength(fallback);
         }
      }

      return strength;
   }

   public static double getFluidSourceStrength(FluidState fluidState) {
      if (fluidState.isEmpty()) {
         return (double)0.0F;
      } else {
         refreshCachesIfNeeded();
         return (Double)FLUID_STRENGTH_CACHE.computeIfAbsent(fluidState.getType(), (ignored) -> computeFluidSourceStrength(fluidState));
      }
   }

   private static double computeFluidSourceStrength(FluidState fluidState) {
      BloomConfig.Data config = BloomConfig.get();
      Identifier fluidId = fluidId(fluidState.getType());
      Double override = sourceOverride(config, fluidId.toString());
      if (override != null) {
         return clampSourceStrength(override);
      } else {
         Block legacyFluidBlock = fluidState.createLegacyBlock().getBlock();
         Identifier legacyBlockId = BuiltInRegistries.BLOCK.getKey(legacyFluidBlock);
         if (legacyBlockId != null) {
            Double legacyOverride = sourceOverride(config, legacyBlockId.toString());
            if (legacyOverride != null) {
               return clampSourceStrength(legacyOverride);
            }
         }

         double fallback = SELECTED_FLUID_IDS.contains(fluidId) ? config.defaultLightSourceStrength : config.defaultNonLightStrength;
         return clampSourceStrength(fallback);
      }
   }

   public static double getEntityTextureSourceStrength(String textureId) {
      if (textureId != null && !textureId.isBlank()) {
         refreshCachesIfNeeded();
         return (Double)ENTITY_STRENGTH_CACHE.computeIfAbsent(textureId, BloomSelection::computeEntityTextureSourceStrength);
      } else {
         return (double)0.0F;
      }
   }

   public static double getParticleSourceStrength(String particleId) {
      if (particleId != null && !particleId.isBlank()) {
         refreshCachesIfNeeded();
         return (Double)PARTICLE_STRENGTH_CACHE.computeIfAbsent(particleId, BloomSelection::computeParticleSourceStrength);
      } else {
         return (double)0.0F;
      }
   }

   public static boolean hasEnabledParticleSources() {
      refreshCachesIfNeeded();
      long version = BloomConfig.version();
      if (cachedParticleEnabledVersion == version) {
         return cachedParticleEnabled;
      } else {
         BloomConfig.Data config = BloomConfig.get();
         if (clampSourceStrength(config.defaultParticleStrength) > 1.0E-5) {
            cachedParticleEnabled = true;
            cachedParticleEnabledVersion = version;
            return true;
         } else {
            for(Double value : config.particleStrengthOverrides.values()) {
               if (value != null && clampSourceStrength(value) > 1.0E-5) {
                  cachedParticleEnabled = true;
                  cachedParticleEnabledVersion = version;
                  return true;
               }
            }

            cachedParticleEnabled = false;
            cachedParticleEnabledVersion = version;
            return false;
         }
      }
   }

   private static double computeEntityTextureSourceStrength(String textureId) {
      BloomConfig.Data config = BloomConfig.get();
      Double override = (Double)config.entityTextureStrengthOverrides.get(textureId);
      return clampSourceStrength(override == null ? config.defaultEntityTextureStrength : override);
   }

   private static double computeParticleSourceStrength(String particleId) {
      BloomConfig.Data config = BloomConfig.get();
      Double override = (Double)config.particleStrengthOverrides.get(particleId);
      return clampSourceStrength(override == null ? config.defaultParticleStrength : override);
   }

   private static double clampSourceStrength(double value) {
      return Math.max((double)0.0F, Math.min((double)500.0F, value));
   }

   private static Double resolveLinkedSourceOverride(BloomConfig.Data config, String id) {
      Double direct = sourceOverride(config, id);
      if (direct != null) {
         return direct;
      } else {
         Double var10000;
         switch (id) {
            case "minecraft:torch" -> var10000 = sourceOverride(config, "minecraft:wall_torch");
            case "minecraft:wall_torch" -> var10000 = sourceOverride(config, "minecraft:torch");
            case "minecraft:soul_torch" -> var10000 = sourceOverride(config, "minecraft:soul_wall_torch");
            case "minecraft:soul_wall_torch" -> var10000 = sourceOverride(config, "minecraft:soul_torch");
            case "minecraft:redstone_torch" -> var10000 = sourceOverride(config, "minecraft:redstone_wall_torch");
            case "minecraft:redstone_wall_torch" -> var10000 = sourceOverride(config, "minecraft:redstone_torch");
            default -> var10000 = null;
         }

         return var10000;
      }
   }

   private static Double sourceOverride(BloomConfig.Data config, String id) {
      return (Double)config.sourceStrengthOverrides.get(id);
   }

   private static boolean blockLooksLikeLightSource(Block block, BlockState state) {
      Boolean cached = (Boolean)LIGHT_SOURCE_LOOK_CACHE.get(block);
      if (cached != null) {
         return cached;
      } else {
         boolean result = computeBlockLooksLikeLightSource(block, state);
         LIGHT_SOURCE_LOOK_CACHE.putIfAbsent(block, result);
         return result;
      }
   }

   private static boolean computeBlockLooksLikeLightSource(Block block, BlockState state) {
      if (state.getLightEmission() > 0) {
         return true;
      } else {
         try {
            UnmodifiableIterator var2 = block.getStateDefinition().getPossibleStates().iterator();

            while(var2.hasNext()) {
               BlockState possible = (BlockState)var2.next();
               if (possible != null && possible.getLightEmission() > 0) {
                  return true;
               }
            }
         } catch (Exception var4) {
         }

         return false;
      }
   }

   private static Identifier blockId(Block block) {
      ConcurrentMap var10000 = BLOCK_ID_CACHE;
      DefaultedRegistry var10002 = BuiltInRegistries.BLOCK;
      Objects.requireNonNull(var10002);
      return (Identifier)var10000.computeIfAbsent(block, var10002::getKey);
   }

   public static int getBlockRadiusProfile(BlockState blockState) {
      if (blockState == null) {
         return 0;
      } else {
         Identifier id = blockId(blockState.getBlock());
         return id == null ? 0 : radiusProfileForId(id.toString());
      }
   }

   public static int getFluidRadiusProfile(FluidState fluidState) {
      if (fluidState != null && !fluidState.isEmpty()) {
         Identifier id = fluidId(fluidState.getType());
         return id == null ? 0 : radiusProfileForId(id.toString());
      } else {
         return 0;
      }
   }

   public static int radiusProfileForId(String id) {
      if (id != null && !id.isBlank()) {
         Integer profile = (Integer)BloomConfig.get().sourceRadiusProfiles.get(id);
         return profile == null ? 0 : Math.max(0, Math.min(2, profile));
      } else {
         return 0;
      }
   }

   public static String blockStateKey(BlockState state) {
      if (state == null) {
         return "";
      } else {
         Identifier id = blockId(state.getBlock());
         if (id == null) {
            return "";
         } else {
            StringBuilder builder = new StringBuilder(id.toString());
            if (!state.getProperties().isEmpty()) {
               builder.append('[');
               boolean first = true;

               for(Property<?> property : state.getProperties()) {
                  if (!first) {
                     builder.append(',');
                  }

                  first = false;
                  builder.append(property.getName()).append('=').append(propertyValueName(state, property));
               }

               builder.append(']');
            }

            return builder.toString();
         }
      }
   }

   private static <T extends Comparable<T>> String propertyValueName(BlockState state, Property<T> property) {
      return property.getName(state.getValue(property));
   }

   private static Identifier fluidId(Fluid fluid) {
      ConcurrentMap var10000 = FLUID_ID_CACHE;
      DefaultedRegistry var10002 = BuiltInRegistries.FLUID;
      Objects.requireNonNull(var10002);
      return (Identifier)var10000.computeIfAbsent(fluid, var10002::getKey);
   }

   private static void refreshCachesIfNeeded() {
      long version = BloomConfig.version();
      if (cachedConfigVersion != version) {
         synchronized(BloomSelection.class) {
            if (cachedConfigVersion != version) {
               BLOCK_STRENGTH_CACHE.clear();
               FLUID_STRENGTH_CACHE.clear();
               ENTITY_STRENGTH_CACHE.clear();
               PARTICLE_STRENGTH_CACHE.clear();
               cachedParticleEnabledVersion = Long.MIN_VALUE;
               cachedConfigVersion = version;
            }
         }
      }
   }
}
