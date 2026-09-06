package com.bloom.client.config;

import com.bloom.client.BloomClient;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * ModMenu only supports a single config-screen entrypoint per mod
 * (ModMenuApi#getModConfigScreenFactory), so this screen acts as a small
 * chooser with two tabs: Bloom and Water Foam. Each button opens the
 * mod's existing full-featured screen for that section.
 */
public final class VybrantVisualConfigScreen extends Screen {
   private final Screen parent;

   private VybrantVisualConfigScreen(Screen parent) {
      super(Component.literal("Vybrant Visual"));
      this.parent = parent;
   }

   public static Screen create(Screen parent) {
      return new VybrantVisualConfigScreen(parent);
   }

   protected void init() {
      int buttonWidth = 200;
      int buttonHeight = 20;
      int gap = 8;
      int totalHeight = buttonHeight * 2 + gap;
      int x = (this.width - buttonWidth) / 2;
      int y = (this.height - totalHeight) / 2;

      this.addRenderableWidget(Button.builder(Component.literal("Bloom"), (button) ->
         this.minecraft.setScreen(BloomConfigScreen.create(this))
      ).bounds(x, y, buttonWidth, buttonHeight).build());

      this.addRenderableWidget(Button.builder(Component.literal("Water Foam"), (button) ->
         BloomClient.openShoreFoamScreen(this)
      ).bounds(x, y + buttonHeight + gap, buttonWidth, buttonHeight).build());

      this.addRenderableWidget(Button.builder(Component.translatable("gui.back"), (button) -> this.onClose())
         .bounds(x, y + (buttonHeight + gap) * 2, buttonWidth, buttonHeight).build());
   }

   public void onClose() {
      this.minecraft.setScreen(this.parent);
   }
}
