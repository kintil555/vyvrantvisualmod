package com.bloom.client.render;

import com.bloom.BloomMod;
import com.bloom.mixin.client.accessor.GameRendererAccessor;
import com.bloom.mixin.client.accessor.PostPassAccessor;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostPass;

public final class PostProcessingSupport {
   private static final int DYNAMIC_UNIFORM_USAGE = 136;
   private static final Map<GpuBuffer, byte[]> LAST_UNIFORM_CONTENTS = new WeakHashMap();
   private static ByteBuffer staging = ByteBuffer.allocateDirect(2048).order(ByteOrder.nativeOrder());
   private static UniformUploadBatch activeBatch;
   private static boolean warnedUniformWriteFailure;

   private PostProcessingSupport() {
   }

   public static GraphicsResourceAllocator frameAllocator() {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft != null) {
         GameRenderer var2 = minecraft.gameRenderer;
         if (var2 instanceof GameRendererAccessor) {
            GameRendererAccessor accessor = (GameRendererAccessor)var2;
            return accessor.bloom$getResourcePool();
         }
      }

      throw new IllegalStateException("Shine could not access GameRenderer's cross-frame resource pool");
   }

   public static UniformUploadBatch beginUniformUploads() {
      if (activeBatch != null) {
         return new UniformUploadBatch(activeBatch.encoder, false);
      } else {
         UniformUploadBatch batch = new UniformUploadBatch(RenderSystem.getDevice().createCommandEncoder(), true);
         activeBatch = batch;
         return batch;
      }
   }

   public static void writeUniform(PostPass pass, String uniformName, Consumer<Std140Builder> writer) {
      writeUniform(pass, uniformName, 0L, writer);
   }

   public static void writeUniform(PostPass pass, String uniformName, long minimumSize, Consumer<Std140Builder> writer) {
      if (pass != null && writer != null) {
         UniformUploadBatch batch = activeBatch;
         boolean temporary = batch == null;
         if (temporary) {
            batch = beginUniformUploads();
         }

         try {
            batch.write(pass, uniformName, minimumSize, writer);
         } finally {
            if (temporary) {
               batch.close();
            }

         }

      }
   }

   private static GpuBuffer ensureCopyWritableBuffer(Map<String, GpuBuffer> uniforms, String name, long minimumSize, GpuBuffer buffer) {
      long requiredSize = Math.max(minimumSize, buffer.size());
      if ((buffer.usage() & 8) != 0 && buffer.size() >= requiredSize) {
         return buffer;
      } else {
         GpuBuffer replacement = RenderSystem.getDevice().createBuffer(() -> "Shine dynamic uniform " + name, 136, requiredSize);
         uniforms.put(name, replacement);
         LAST_UNIFORM_CONTENTS.remove(buffer);
         buffer.close();
         return replacement;
      }
   }

   private static ByteBuffer stagingBuffer(int capacity) {
      if (staging.capacity() < capacity) {
         int grown = Math.max(capacity, staging.capacity() << 1);
         staging = ByteBuffer.allocateDirect(grown).order(ByteOrder.nativeOrder());
      }

      staging.clear();
      staging.limit(capacity);

      for(int index = 0; index < capacity; ++index) {
         staging.put(index, (byte)0);
      }

      staging.position(0);
      return staging;
   }

   public static final class UniformUploadBatch implements AutoCloseable {
      private final CommandEncoder encoder;
      private final boolean owner;
      private boolean closed;

      private UniformUploadBatch(CommandEncoder encoder, boolean owner) {
         this.encoder = encoder;
         this.owner = owner;
      }

      private void write(PostPass pass, String uniformName, long minimumSize, Consumer<Std140Builder> writer) {
         Map<String, GpuBuffer> uniforms = ((PostPassAccessor)pass).bloom$getCustomUniforms();
         GpuBuffer buffer = (GpuBuffer)uniforms.get(uniformName);
         if (buffer != null) {
            try {
               GpuBuffer writable = PostProcessingSupport.ensureCopyWritableBuffer(uniforms, uniformName, minimumSize, buffer);
               int capacity = Math.toIntExact(writable.size());
               ByteBuffer data = PostProcessingSupport.stagingBuffer(capacity);
               Std140Builder builder = Std140Builder.intoBuffer(data);
               writer.accept(builder);
               data = builder.get();
               int length = data.remaining();
               byte[] previous = (byte[])PostProcessingSupport.LAST_UNIFORM_CONTENTS.get(writable);
               boolean unchanged = previous != null && previous.length == length;

               for(int index = 0; index < length; ++index) {
                  if (unchanged && previous[index] != data.get(index)) {
                     unchanged = false;
                  }
               }

               if (unchanged) {
                  return;
               }

               this.encoder.writeToBuffer(writable.slice(0L, (long)length), data);
               if (previous == null || previous.length != length) {
                  previous = new byte[length];
                  PostProcessingSupport.LAST_UNIFORM_CONTENTS.put(writable, previous);
               }

               for(int index = 0; index < length; ++index) {
                  previous[index] = data.get(index);
               }

               PostProcessingSupport.warnedUniformWriteFailure = false;
            } catch (RuntimeException exception) {
               if (!PostProcessingSupport.warnedUniformWriteFailure) {
                  BloomMod.LOGGER.warn("Shine uniform update failed; continuing with the previous values.", exception);
                  PostProcessingSupport.warnedUniformWriteFailure = true;
               }
            }

         }
      }

      public void close() {
         if (!this.closed) {
            this.closed = true;
            if (this.owner && PostProcessingSupport.activeBatch == this) {
               PostProcessingSupport.activeBatch = null;
            }

         }
      }
   }
}
