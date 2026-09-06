package com.bloom.client.config;

import com.bloom.BloomMod;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public final class ShineAtomicFiles {
   private ShineAtomicFiles() {
   }

   public static <T> T readUtf8WithBackup(Path target, ReaderAction<T> action) throws Exception {
      try {
         return (T)readUtf8(target, action);
      } catch (Exception var8) {
         Path backup = backupPath(target);
         if (Files.isRegularFile(backup, new LinkOption[0])) {
            try {
               T recovered = (T)readUtf8(backup, action);
               Path preserved = preserveBrokenFile(target);
               restoreBackup(backup, target);
               BloomMod.LOGGER.warn("Recovered Shine configuration {} from {}; preserved the unreadable file as {}", new Object[]{target, backup, preserved});
               return recovered;
            } catch (Exception backupFailure) {
               var8.addSuppressed(backupFailure);
            }
         }

         try {
            Path preserved = preserveBrokenFile(target);
            if (preserved != null) {
               BloomMod.LOGGER.error("Preserved unreadable Shine configuration as {}", preserved);
            }
         } catch (IOException preserveFailure) {
            var8.addSuppressed(preserveFailure);
         }

         throw var8;
      }
   }

   public static void writeUtf8(Path target, WriterAction action) throws IOException {
      Path absolute = target.toAbsolutePath().normalize();
      Path parent = absolute.getParent();
      if (parent == null) {
         throw new IOException("Configuration path has no parent: " + String.valueOf(target));
      } else {
         Files.createDirectories(parent);
         Path temporary = Files.createTempFile(parent, "." + String.valueOf(absolute.getFileName()) + "-", ".tmp");
         Path backupTemporary = null;

         try {
            Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);

            try {
               action.write(writer);
            } catch (Throwable var14) {
               if (writer != null) {
                  try {
                     writer.close();
                  } catch (Throwable var13) {
                     var14.addSuppressed(var13);
                  }
               }

               throw var14;
            }

            if (writer != null) {
               writer.close();
            }

            forceFile(temporary);
            if (Files.isRegularFile(absolute, new LinkOption[0])) {
               Path backup = backupPath(absolute);
               backupTemporary = Files.createTempFile(parent, "." + String.valueOf(absolute.getFileName()) + "-", ".bak.tmp");
               Files.copy(absolute, backupTemporary, StandardCopyOption.REPLACE_EXISTING);
               forceFile(backupTemporary);
               moveReplacing(backupTemporary, backup);
               backupTemporary = null;
            }

            moveReplacing(temporary, absolute);
            temporary = null;
         } finally {
            if (temporary != null) {
               Files.deleteIfExists(temporary);
            }

            if (backupTemporary != null) {
               Files.deleteIfExists(backupTemporary);
            }

         }

      }
   }

   public static Path backupPath(Path target) {
      Path absolute = target.toAbsolutePath().normalize();
      return absolute.resolveSibling(String.valueOf(absolute.getFileName()) + ".bak");
   }

   public static void restoreFromBackup(Path target) throws IOException {
      Path absolute = target.toAbsolutePath().normalize();
      Path backup = backupPath(absolute);
      if (!Files.isRegularFile(backup, new LinkOption[0])) {
         throw new IOException("Missing configuration backup: " + String.valueOf(backup));
      } else {
         restoreBackup(backup, absolute);
      }
   }

   private static <T> T readUtf8(Path source, ReaderAction<T> action) throws Exception {
      Reader reader = Files.newBufferedReader(source, StandardCharsets.UTF_8);

      Object var3;
      try {
         var3 = action.read(reader);
      } catch (Throwable var6) {
         if (reader != null) {
            try {
               reader.close();
            } catch (Throwable var5) {
               var6.addSuppressed(var5);
            }
         }

         throw var6;
      }

      if (reader != null) {
         reader.close();
      }

      return (T)var3;
   }

   private static Path preserveBrokenFile(Path target) throws IOException {
      Path absolute = target.toAbsolutePath().normalize();
      if (!Files.exists(absolute, new LinkOption[0])) {
         return null;
      } else {
         String var10000 = String.valueOf(absolute.getFileName());
         String baseName = var10000 + ".broken-" + System.currentTimeMillis();
         Path preserved = absolute.resolveSibling(baseName);

         for(int suffix = 2; Files.exists(preserved, new LinkOption[0]); preserved = absolute.resolveSibling(baseName + "-" + suffix++)) {
         }

         try {
            return Files.move(absolute, preserved, StandardCopyOption.ATOMIC_MOVE);
         } catch (AtomicMoveNotSupportedException var6) {
            return Files.move(absolute, preserved);
         }
      }
   }

   private static void restoreBackup(Path backup, Path target) throws IOException {
      Path absolute = target.toAbsolutePath().normalize();
      Path parent = absolute.getParent();
      if (parent == null) {
         throw new IOException("Configuration path has no parent: " + String.valueOf(target));
      } else {
         Path temporary = Files.createTempFile(parent, "." + String.valueOf(absolute.getFileName()) + "-", ".recovery.tmp");

         try {
            Files.copy(backup, temporary, StandardCopyOption.REPLACE_EXISTING);
            forceFile(temporary);
            moveReplacing(temporary, absolute);
            temporary = null;
         } finally {
            if (temporary != null) {
               Files.deleteIfExists(temporary);
            }

         }

      }
   }

   private static void moveReplacing(Path source, Path target) throws IOException {
      try {
         Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException var3) {
         Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
      }

   }

   private static void forceFile(Path path) throws IOException {
      FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE);

      try {
         channel.force(true);
      } catch (Throwable var5) {
         if (channel != null) {
            try {
               channel.close();
            } catch (Throwable var4) {
               var5.addSuppressed(var4);
            }
         }

         throw var5;
      }

      if (channel != null) {
         channel.close();
      }

   }

   @FunctionalInterface
   public interface ReaderAction<T> {
      T read(Reader var1) throws Exception;
   }

   @FunctionalInterface
   public interface WriterAction {
      void write(Writer var1) throws IOException;
   }
}
