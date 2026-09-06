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

   private ShoreFoamConfigManager() {
   }

   public static ShoreFoamConfig get() {
      return data;
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
   }

   public static void save() {
      try {
         ShineAtomicFiles.writeUtf8(configPath(), (writer) -> GSON.toJson(data, writer));
      } catch (IOException e) {
         BloomMod.LOGGER.error("Failed to write shore foam config.", e);
      }
   }

   private static Path configPath() {
      return FabricLoader.getInstance().getConfigDir().resolve("vybrantvisual_shorefoam.json");
   }
}
