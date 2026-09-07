package com.bloom.client.experimental.config;

import com.bloom.BloomMod;
import com.bloom.client.config.ShineAtomicFiles;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

public final class ShoreFoamConfigManager {
   private static final Gson GSON = (new GsonBuilder()).setPrettyPrinting().create();
   private static ShoreFoamConfig data = new ShoreFoamConfig();
   private static volatile long version = 0L;

   private ShoreFoamConfigManager() {
   }

   public static ShoreFoamConfig get() {
      return data;
   }

   /**
    * Alias of {@link #get()} for callers on the render thread that want the
    * currently active config without allocating.
    */
   public static ShoreFoamConfig fastConfig() {
      return data;
   }

   /**
    * Monotonically increasing counter bumped whenever the config is loaded or
    * saved, so renderers can cheaply detect changes and skip redundant GPU
    * uploads.
    */
   public static long version() {
      return version;
   }

   public static ShoreFoamConfig defaults() {
      return new ShoreFoamConfig();
   }

   public static void load() {
      Path path = configPath();
      if (!Files.exists(path, new LinkOption[0])) {
         data = new ShoreFoamConfig();
         save();
      } else {
         try {
            ShoreFoamConfig loaded = ShineAtomicFiles.readUtf8WithBackup(path, (reader) -> GSON.fromJson(reader, ShoreFoamConfig.class));
            data = loaded == null ? new ShoreFoamConfig() : loaded;
         } catch (Exception e) {
            BloomMod.LOGGER.error("Failed to read shore foam config, using defaults.", e);
            data = new ShoreFoamConfig();
         }
      }
      version++;
   }

   public static void save() {
      try {
         ShineAtomicFiles.writeUtf8(configPath(), (writer) -> GSON.toJson(data, writer));
      } catch (IOException e) {
         BloomMod.LOGGER.error("Failed to write shore foam config.", e);
      }
      version++;
   }

   private static Path configPath() {
      return FabricLoader.getInstance().getConfigDir().resolve("vybrantvisual_shorefoam.json");
   }
}
