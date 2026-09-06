package com.bloom.client.render;

import com.bloom.client.diagnostics.ShineFpsDiagnostics;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderSystem;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class TransientMeshArena {
   private static final int FRAME_RING_SIZE = 3;
   private static final int IDLE_RELEASE_FRAMES = 600;
   private static final int MINIMUM_BUFFER_BYTES = 4096;
   private static final int BUFFER_USAGE = 40;
   private static final Map<String, Slot> SLOTS = new HashMap();
   private static final ArrayDeque<RetiredBuffer> RETIRED = new ArrayDeque();
   private static long frameIndex;
   private static int frameUploads;
   private static long frameUploadBytes;
   private static int previousFrameUploads;
   private static long previousFrameUploadBytes;

   private TransientMeshArena() {
   }

   public static void beginFrame() {
      RenderSystem.assertOnRenderThread();
      previousFrameUploads = frameUploads;
      previousFrameUploadBytes = frameUploadBytes;
      frameUploads = 0;
      frameUploadBytes = 0L;
      ++frameIndex;
      releaseRetiredBuffers();
      releaseIdleSlots();
   }

   public static GpuBuffer upload(String key, ByteBuffer source) {
      RenderSystem.assertOnRenderThread();
      if (key != null && !key.isBlank()) {
         if (source == null) {
            throw new IllegalArgumentException("Transient mesh source must not be null");
         } else {
            int byteCount = source.remaining();
            if (byteCount <= 0) {
               throw new IllegalArgumentException("Transient mesh source must contain vertex data");
            } else {
               Slot slot = (Slot)SLOTS.computeIfAbsent(key, (ignored) -> new Slot());
               slot.lastUsedFrame = frameIndex;
               int ringIndex = Math.floorMod(frameIndex, 3);
               GpuBuffer buffer = slot.buffers[ringIndex];
               if (buffer == null || buffer.isClosed() || buffer.size() < (long)byteCount) {
                  if (buffer != null && !buffer.isClosed()) {
                     RETIRED.addLast(new RetiredBuffer(buffer, frameIndex + 3L));
                  }

                  long capacity = growCapacity(byteCount);
                  buffer = RenderSystem.getDevice().createBuffer(() -> "Shine transient mesh: " + key, 40, capacity);
                  slot.buffers[ringIndex] = buffer;
               }

               ByteBuffer uploadView = source.duplicate();
               CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
               encoder.writeToBuffer(buffer.slice(0L, (long)byteCount), uploadView);
               ++frameUploads;
               frameUploadBytes += (long)byteCount;
               ShineFpsDiagnostics.recordTransientMeshUpload(key, (long)byteCount);
               return buffer;
            }
         }
      } else {
         throw new IllegalArgumentException("Transient mesh key must not be blank");
      }
   }

   public static Metrics metrics() {
      long capacityBytes = 0L;
      int activeBuffers = 0;

      for(Slot slot : SLOTS.values()) {
         for(GpuBuffer buffer : slot.buffers) {
            if (buffer != null && !buffer.isClosed()) {
               ++activeBuffers;
               capacityBytes += buffer.size();
            }
         }
      }

      for(RetiredBuffer retired : RETIRED) {
         GpuBuffer buffer = retired.buffer();
         if (!buffer.isClosed()) {
            ++activeBuffers;
            capacityBytes += buffer.size();
         }
      }

      return new Metrics(previousFrameUploads, previousFrameUploadBytes, activeBuffers, capacityBytes);
   }

   public static void close() {
      RenderSystem.assertOnRenderThread();

      for(Slot slot : SLOTS.values()) {
         for(GpuBuffer buffer : slot.buffers) {
            if (buffer != null && !buffer.isClosed()) {
               buffer.close();
            }
         }
      }

      SLOTS.clear();

      while(!RETIRED.isEmpty()) {
         GpuBuffer buffer = ((RetiredBuffer)RETIRED.removeFirst()).buffer();
         if (!buffer.isClosed()) {
            buffer.close();
         }
      }

      frameUploads = 0;
      frameUploadBytes = 0L;
      previousFrameUploads = 0;
      previousFrameUploadBytes = 0L;
   }

   private static long growCapacity(int requiredBytes) {
      long capacity;
      for(capacity = 4096L; capacity < (long)requiredBytes; capacity <<= 1) {
      }

      return capacity;
   }

   private static void releaseRetiredBuffers() {
      while(!RETIRED.isEmpty() && ((RetiredBuffer)RETIRED.peekFirst()).releaseFrame() <= frameIndex) {
         GpuBuffer buffer = ((RetiredBuffer)RETIRED.removeFirst()).buffer();
         if (!buffer.isClosed()) {
            buffer.close();
         }
      }

   }

   private static void releaseIdleSlots() {
      Iterator<Map.Entry<String, Slot>> iterator = SLOTS.entrySet().iterator();

      while(iterator.hasNext()) {
         Slot slot = (Slot)((Map.Entry)iterator.next()).getValue();
         if (frameIndex - slot.lastUsedFrame > 600L) {
            for(GpuBuffer buffer : slot.buffers) {
               if (buffer != null && !buffer.isClosed()) {
                  buffer.close();
               }
            }

            iterator.remove();
         }
      }

   }

   private static final class Slot {
      private final GpuBuffer[] buffers = new GpuBuffer[3];
      private long lastUsedFrame;
   }

   private static record RetiredBuffer(GpuBuffer buffer, long releaseFrame) {
   }

   public static record Metrics(int uploads, long uploadedBytes, int activeBuffers, long capacityBytes) {
   }
}
