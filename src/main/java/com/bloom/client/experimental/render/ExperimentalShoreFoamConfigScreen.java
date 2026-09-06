package com.bloom.client.config;

import com.bloom.client.experimental.config.ShoreFoamConfig;
import com.bloom.client.experimental.config.ShoreFoamConfigManager;
import com.mojang.blaze3d.platform.NativeImage;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

public final class ExperimentalShoreFoamConfigScreen extends FixedScaleScreen {
   private static final int PANEL_MARGIN = 16;
   private static final int ROW_HEIGHT = 18;
   private static final int ROW_GAP = 24;
   private static final int SCROLLBAR_WIDTH = 8;
   private static final int MAX_SAVED_WORLD_COLORS = 10;
   private final Screen parent;
   private final ShoreFoamConfig editing;
   private final ShoreFoamConfig defaults;
   private final List<BiomeEntry> allBiomes = new ArrayList();
   private final List<BiomeEntry> filteredBiomes = new ArrayList();
   private final List<DescribedWidget> describedWidgets = new ArrayList();
   private EditBox searchBox;
   private Button enabledButton;
   private Button resetSelectedButton;
   private String searchText = "";
   private int listScroll;
   private int selectedIndex;
   private String selectedBiomeId;
   private boolean selectedGlobal;
   private boolean draggingListScrollbar;
   private boolean draggingColorSquare;
   private boolean draggingColorHue;
   private int listWidth;
   private int controlsX;
   private int controlsWidth;
   private int wheelX;
   private int wheelY;
   private int wheelSize;
   private int hueBarX;
   private int hueBarY;
   private int hueBarWidth;
   private int hueBarHeight;
   private int colorInfoX;
   private int colorInfoY;
   private int colorInfoWidth;
   private int savedColorsX;
   private int savedColorsY;
   private int savedColorSize;
   private int savedColorGap;
   private DynamicTexture colorWheelTexture;
   private Identifier colorWheelTextureId;
   private int cachedWheelSize = -1;
   private float cachedWheelHue = -1.0F;

   private ExperimentalShoreFoamConfigScreen(Screen parent, ShoreFoamConfig editing, ShoreFoamConfig defaults) {
      super(Component.literal("Shore Foam"));
      this.parent = parent;
      this.editing = editing;
      this.defaults = defaults;
      this.selectedBiomeId = this.resolveCurrentBiomeId();
      this.selectedGlobal = this.selectedBiomeId.isBlank();
      this.rebuildBiomeEntries();
      this.applyFilter("");
   }

   public static Screen create(Screen parent, ShoreFoamConfig editing, ShoreFoamConfig defaults) {
      return new ExperimentalShoreFoamConfigScreen(parent, editing, defaults);
   }

   protected void init() {
      this.applyFixedScaleDimensions();
      this.clearWidgets();
      this.describedWidgets.clear();
      this.enabledButton = null;
      this.resetSelectedButton = null;
      this.draggingListScrollbar = false;
      this.draggingColorSquare = false;
      this.draggingColorHue = false;
      this.rebuildBiomeEntries();
      this.listWidth = Math.min(280, Math.max(220, this.width / 3));
      this.controlsX = 16 + this.listWidth + 16;
      this.controlsWidth = Math.max(360, this.width - this.controlsX - 16);
      this.searchBox = (EditBox)this.addRenderableWidget(new EditBox(this.font, 16, 48, this.listWidth, 20, Component.literal("Search biomes")));
      this.searchBox.setMaxLength(160);
      this.searchBox.setValue(this.searchText);
      this.searchBox.setResponder((value) -> {
         this.searchText = value == null ? "" : value;
         this.applyFilter(this.searchText);
      });
      this.enabledButton = (Button)this.addDescribedWidget(Button.builder(Component.empty(), (button) -> this.updateSelectedProfile((profile) -> profile.enabled = !profile.enabled)).bounds(this.controlsX, 48, this.controlsWidth, 20).build(), "Enable or disable Shore Foam in the selected biome. Default / Global is used by biomes without an override.");
      int columnGap = 10;
      int columnWidth = (this.controlsWidth - columnGap) / 2;
      int rightX = this.controlsX + columnWidth + columnGap;
      int sliderY = 80;
      this.addSlider(this.controlsX, sliderY, columnWidth, "Opacity", (double)0.0F, (double)1.0F, 0.05, () -> this.selectedProfileView().opacity, (value) -> this.updateSelectedProfile((profile) -> profile.opacity = value), "%.2f", "How strongly the selected biome's foam mixes over the water.");
      this.addSlider(rightX, sliderY, columnWidth, "Thickness", 0.01, 0.35, 0.01, () -> this.selectedProfileView().thickness, (value) -> this.updateSelectedProfile((profile) -> profile.thickness = value), "%.2f", "Width of the selected biome's foam strip.");
      sliderY += 24;
      this.addSlider(this.controlsX, sliderY, columnWidth, "Speed", (double)0.0F, (double)4.0F, 0.05, () -> this.selectedProfileView().speed, (value) -> this.updateSelectedProfile((profile) -> profile.speed = value), "%.2f", "Animation speed for the selected biome.");
      this.addSlider(rightX, sliderY, columnWidth, "Scale", (double)0.5F, (double)12.0F, (double)0.25F, () -> this.selectedProfileView().scale, (value) -> this.updateSelectedProfile((profile) -> profile.scale = value), "%.2f", "Pattern scale for the selected biome.");
      sliderY += 24;
      this.addSlider(this.controlsX, sliderY, this.controlsWidth, "Breakup", (double)0.0F, (double)1.0F, 0.05, () -> this.selectedProfileView().breakup, (value) -> this.updateSelectedProfile((profile) -> profile.breakup = value), "%.2f", "Animated breakup for the selected biome's shoreline.");
      this.layoutWorldColorPicker(this.controlsX, this.controlsWidth, 174);
      int buttonY = this.height - 30;
      int buttonGap = 8;
      int buttonWidth = (this.controlsWidth - buttonGap * 2) / 3;
      this.resetSelectedButton = (Button)this.addDescribedWidget(Button.builder(Component.empty(), (button) -> this.resetSelected()).bounds(this.controlsX, buttonY, buttonWidth, 20).build(), "Reset the selected biome. A biome without an override uses Default / Global.");
      this.addRenderableWidget(Button.builder(Component.literal("Reset All Foam"), (button) -> this.resetAll()).bounds(this.controlsX + buttonWidth + buttonGap, buttonY, buttonWidth, 20).build());
      this.addRenderableWidget(Button.builder(Component.literal("Back"), (button) -> this.onClose()).bounds(this.controlsX + (buttonWidth + buttonGap) * 2, buttonY, this.controlsWidth - (buttonWidth + buttonGap) * 2, 20).build());
      this.applyFilter(this.searchText);
      this.keepSelectedBiomeVisible();
      this.refreshLabels();
   }

   public void extractRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
      super.extractRenderState(guiGraphics, mouseX, mouseY, partialTick);
      guiGraphics.centeredText(this.font, this.title, this.width / 2, 18, -1);
      String description = this.hoverDescription(mouseX, mouseY);
      if (description.isBlank()) {
         description = this.selectedGlobal ? "Default settings used by every biome without its own Shore Foam override." : "Editing " + this.selectedBiomeId + (this.hasSelectedOverride() ? " (custom override)" : " (currently using Default / Global)");
      }

      guiGraphics.centeredText(this.font, this.trimToWidth(description, this.width - 40), this.width / 2, 32, -4342339);
      this.renderBiomeList(guiGraphics);
      this.renderColorPickerPanelBackground(guiGraphics);
      this.renderWorldColorPicker(guiGraphics);
   }

   public boolean mouseClicked(MouseButtonEvent event, boolean playSound) {
      double mouseX = this.fixedMouseX(event.x());
      double mouseY = this.fixedMouseY(event.y());
      if (event.button() == 0) {
         if (this.handleListScrollbarClick(mouseX, mouseY)) {
            return true;
         }

         if (this.trySavedColorClick(mouseX, mouseY)) {
            return true;
         }

         if (this.tryColorSquarePick(mouseX, mouseY)) {
            this.draggingColorSquare = true;
            return true;
         }

         if (this.tryHueBarPick(mouseX, mouseY)) {
            this.draggingColorHue = true;
            return true;
         }

         if (this.clickBiomeList(mouseX, mouseY)) {
            return true;
         }
      }

      return super.mouseClicked(event, playSound);
   }

   public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
      if (this.draggingListScrollbar) {
         this.setListScrollFromMouse(this.fixedMouseY(event.y()));
         return true;
      } else if (this.draggingColorSquare) {
         this.tryColorSquarePick(this.fixedMouseX(event.x()), this.fixedMouseY(event.y()));
         return true;
      } else if (this.draggingColorHue) {
         this.tryHueBarPick(this.fixedMouseX(event.x()), this.fixedMouseY(event.y()));
         return true;
      } else {
         return super.mouseDragged(event, dragX, dragY);
      }
   }

   public boolean mouseReleased(MouseButtonEvent event) {
      this.draggingListScrollbar = false;
      this.draggingColorSquare = false;
      this.draggingColorHue = false;
      return super.mouseReleased(event);
   }

   public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
      double fixedX = this.fixedMouseX(mouseX);
      double fixedY = this.fixedMouseY(mouseY);
      if (fixedX >= (double)16.0F && fixedX <= (double)(16 + this.listWidth) && fixedY >= (double)this.listTop() && fixedY <= (double)this.listBottom()) {
         this.listScroll = this.clampListScroll(this.listScroll - (int)Math.signum(verticalAmount));
         return true;
      } else {
         return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
      }
   }

   public void onClose() {
      ShoreFoamConfigManager.get().enabled = this.editing.enabled;
      ShoreFoamConfigManager.get().opacity = this.editing.opacity;
      ShoreFoamConfigManager.get().thickness = this.editing.thickness;
      ShoreFoamConfigManager.get().speed = this.editing.speed;
      ShoreFoamConfigManager.get().scale = this.editing.scale;
      ShoreFoamConfigManager.get().breakup = this.editing.breakup;
      ShoreFoamConfigManager.get().color = this.editing.color;
      ShoreFoamConfigManager.get().biomes = this.editing.biomes;
      ShoreFoamConfigManager.get().savedPickerColors = this.editing.savedPickerColors;
      ShoreFoamConfigManager.save();
      Minecraft.getInstance().gui.setScreen(this.parent);
   }

   public void removed() {
      super.removed();
      this.releaseColorWheelTexture();
   }

   private void addSlider(int x, int y, int width, String label, double min, double max, double step, DoubleSupplier getter, DoubleConsumer setter, String format, String description) {
      this.addDescribedWidget(new LabeledSlider(x, y, width, Component.literal(label), min, max, step, getter, setter, (value) -> String.format(Locale.ROOT, format, value)), description);
   }

   private void updateSelectedProfile(Consumer<ShoreFoamConfig.BiomeProfile> update) {
      ShoreFoamConfig.BiomeProfile profile = this.selectedProfileForEdit();
      update.accept(profile);
      if (this.selectedGlobal) {
         this.applyProfileToGlobal(profile);
      }

      this.refreshLabels();
      this.applyPreview();
   }

   private ShoreFoamConfig.BiomeProfile selectedProfileView() {
      if (!this.selectedGlobal && !this.selectedBiomeId.isBlank()) {
         if (this.editing.biomes != null) {
            ShoreFoamConfig.BiomeProfile override = this.editing.biomes.get(this.selectedBiomeId);
            if (override != null) {
               return override;
            }
         }

         return this.editing.defaultProfile();
      } else {
         return this.editing.defaultProfile();
      }
   }

   private ShoreFoamConfig.BiomeProfile selectedProfileForEdit() {
      if (!this.selectedGlobal && !this.selectedBiomeId.isBlank()) {
         if (this.editing.biomes == null) {
            this.editing.biomes = new LinkedHashMap();
         }

         return this.editing.biomes.computeIfAbsent(this.selectedBiomeId, (ignored) -> this.editing.defaultProfile());
      } else {
         return this.editing.defaultProfile();
      }
   }

   private void applyProfileToGlobal(ShoreFoamConfig.BiomeProfile profile) {
      this.editing.enabled = profile.enabled;
      this.editing.opacity = profile.opacity;
      this.editing.thickness = profile.thickness;
      this.editing.speed = profile.speed;
      this.editing.scale = profile.scale;
      this.editing.breakup = profile.breakup;
      this.editing.color = profile.color & 16777215;
   }

   private void resetSelected() {
      if (this.selectedGlobal) {
         this.applyProfileToGlobal(this.defaults.defaultProfile());
      } else {
         if (this.editing.biomes == null) {
            this.editing.biomes = new LinkedHashMap();
         }

         ShoreFoamConfig.BiomeProfile defaultOverride = this.defaults.biomes == null ? null : this.defaults.biomes.get(this.selectedBiomeId);
         if (defaultOverride == null) {
            this.editing.biomes.remove(this.selectedBiomeId);
         } else {
            this.editing.biomes.put(this.selectedBiomeId, defaultOverride.copy());
         }
      }

      this.refreshLabels();
      this.applyPreview();
   }

   private void resetAll() {
      this.applyProfileToGlobal(this.defaults.defaultProfile());
      this.editing.biomes = new LinkedHashMap();
      if (this.defaults.biomes != null) {
         for(Map.Entry<String, ShoreFoamConfig.BiomeProfile> entry : this.defaults.biomes.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
               this.editing.biomes.put((String)entry.getKey(), entry.getValue().copy());
            }
         }
      }

      this.rebuildBiomeEntries();
      this.applyFilter(this.searchText);
      this.keepSelectedBiomeVisible();
      this.refreshLabels();
      this.applyPreview();
   }

   private void refreshLabels() {
      ShoreFoamConfig.BiomeProfile profile = this.selectedProfileView();
      if (this.enabledButton != null) {
         String prefix = this.selectedGlobal ? "Default Shore Foam" : shortBiomeLabel(this.selectedBiomeId) + " Shore Foam";
         this.enabledButton.setMessage(Component.literal(prefix + (profile.enabled ? ": ON" : ": OFF")));
      }

      if (this.resetSelectedButton != null) {
         this.resetSelectedButton.setMessage(Component.literal(this.selectedGlobal ? "Reset Global Foam" : (this.hasSelectedOverride() ? "Reset Biome Override" : "Using Global Defaults")));
         this.resetSelectedButton.active = this.selectedGlobal || this.hasSelectedOverride();
      }

   }

   private boolean hasSelectedOverride() {
      return !this.selectedGlobal && this.editing.biomes != null && this.editing.biomes.containsKey(this.selectedBiomeId);
   }

   private static final String[] VANILLA_BIOME_IDS = new String[]{"minecraft:plains", "minecraft:sunflower_plains", "minecraft:forest", "minecraft:flower_forest", "minecraft:birch_forest", "minecraft:old_growth_birch_forest", "minecraft:dark_forest", "minecraft:taiga", "minecraft:snowy_taiga", "minecraft:old_growth_pine_taiga", "minecraft:old_growth_spruce_taiga", "minecraft:jungle", "minecraft:bamboo_jungle", "minecraft:sparse_jungle", "minecraft:cherry_grove", "minecraft:meadow", "minecraft:grove", "minecraft:snowy_slopes", "minecraft:jagged_peaks", "minecraft:frozen_peaks", "minecraft:stony_peaks", "minecraft:savanna", "minecraft:savanna_plateau", "minecraft:windswept_hills", "minecraft:windswept_gravelly_hills", "minecraft:windswept_forest", "minecraft:windswept_savanna", "minecraft:desert", "minecraft:swamp", "minecraft:mangrove_swamp", "minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands", "minecraft:mushroom_fields", "minecraft:beach", "minecraft:snowy_beach", "minecraft:stony_shore", "minecraft:river", "minecraft:frozen_river", "minecraft:ocean", "minecraft:deep_ocean", "minecraft:cold_ocean", "minecraft:deep_cold_ocean", "minecraft:frozen_ocean", "minecraft:deep_frozen_ocean", "minecraft:lukewarm_ocean", "minecraft:deep_lukewarm_ocean", "minecraft:warm_ocean", "minecraft:dripstone_caves", "minecraft:lush_caves", "minecraft:deep_dark", "minecraft:nether_wastes", "minecraft:crimson_forest", "minecraft:warped_forest", "minecraft:soul_sand_valley", "minecraft:basalt_deltas", "minecraft:the_end", "minecraft:end_highlands", "minecraft:end_midlands", "minecraft:small_end_islands", "minecraft:end_barrens"};

   private void rebuildBiomeEntries() {
      Set<String> ids = new LinkedHashSet(List.of(VANILLA_BIOME_IDS));
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft != null && minecraft.level != null) {
         try {
            minecraft.level.registryAccess().lookupOrThrow(Registries.BIOME).keySet().forEach((idx) -> ids.add(idx.toString()));
         } catch (RuntimeException var7) {
         }
      }

      if (this.defaults.biomes != null) {
         ids.addAll(this.defaults.biomes.keySet());
      }

      if (this.editing.biomes != null) {
         ids.addAll(this.editing.biomes.keySet());
      }

      String current = this.resolveCurrentBiomeId();
      if (!current.isBlank()) {
         ids.add(current);
      }

      this.allBiomes.clear();
      this.allBiomes.add(new BiomeEntry("", true));
      List<String> sorted = new ArrayList(ids);
      sorted.removeIf((idx) -> idx == null || idx.isBlank());
      sorted.sort(Comparator.naturalOrder());

      for(String id : sorted) {
         this.allBiomes.add(new BiomeEntry(id, false));
      }

   }

   private void applyFilter(String rawQuery) {
      String query = rawQuery == null ? "" : rawQuery.trim().toLowerCase(Locale.ROOT);
      String previousId = this.selectedBiomeId;
      boolean previousGlobal = this.selectedGlobal;
      this.filteredBiomes.clear();

      for(BiomeEntry entry : this.allBiomes) {
         String label = entry.global ? "default global all biomes" : entry.id.toLowerCase(Locale.ROOT);
         if (query.isEmpty() || label.contains(query)) {
            this.filteredBiomes.add(entry);
         }
      }

      this.selectedIndex = this.filteredBiomes.isEmpty() ? -1 : 0;

      for(int i = 0; i < this.filteredBiomes.size(); ++i) {
         BiomeEntry entry = (BiomeEntry)this.filteredBiomes.get(i);
         if (entry.global == previousGlobal && (entry.global || entry.id.equals(previousId))) {
            this.selectedIndex = i;
            break;
         }
      }

      BiomeEntry selected = this.selectedBiome();
      this.selectedGlobal = selected == null || selected.global;
      this.selectedBiomeId = selected != null && !selected.global ? selected.id : "";
      this.listScroll = this.clampListScroll(this.listScroll);
      this.refreshLabels();
   }

   private void renderBiomeList(GuiGraphicsExtractor guiGraphics) {
      int x = 16;
      int top = this.listTop();
      int bottom = this.listBottom();
      int contentRight = x + this.listWidth - 8 - 2;
      guiGraphics.fill(x, top, x + this.listWidth, bottom, 1996488704);
      this.listScroll = this.clampListScroll(this.listScroll);
      int end = Math.min(this.filteredBiomes.size(), this.listScroll + this.visibleRows());
      if (this.filteredBiomes.isEmpty()) {
         guiGraphics.text(this.font, "No matches", x + 6, top + 6, -5592406);
      }

      for(int i = this.listScroll; i < end; ++i) {
         BiomeEntry entry = (BiomeEntry)this.filteredBiomes.get(i);
         int rowY = top + 4 + (i - this.listScroll) * 18;
         if (i == this.selectedIndex) {
            guiGraphics.fill(x + 2, rowY - 1, contentRight, rowY + 18 - 2, -2006555034);
         }

         boolean modified = !entry.global && this.editing.biomes.containsKey(entry.id);
         String label = entry.global ? "Default / Global" : entry.id + (modified ? " *" : "");
         int color = entry.global ? -86 : (modified ? -1 : -4342339);
         guiGraphics.enableScissor(x + 4, rowY - 2, contentRight - 2, rowY + 18);
         guiGraphics.text(this.font, label, x + 6, rowY + 3, color);
         guiGraphics.disableScissor();
      }

      this.renderScrollbar(guiGraphics, x, top, bottom);
   }

   private void renderScrollbar(GuiGraphicsExtractor guiGraphics, int x, int top, int bottom) {
      int scrollbarX = x + this.listWidth - 8;
      guiGraphics.fill(scrollbarX, top, x + this.listWidth, bottom, -872415232);
      int rows = this.visibleRows();
      int maxScroll = Math.max(0, this.filteredBiomes.size() - rows);
      int trackHeight = Math.max(1, bottom - top - 4);
      int thumbHeight = Math.max(12, Math.round((float)trackHeight * ((float)rows / (float)Math.max(rows, this.filteredBiomes.size()))));
      int travel = Math.max(0, trackHeight - thumbHeight);
      int thumbY = top + 2 + (maxScroll == 0 ? 0 : Math.round((float)travel * ((float)this.listScroll / (float)maxScroll)));
      guiGraphics.fill(scrollbarX + 1, thumbY, x + this.listWidth - 1, thumbY + thumbHeight, -4473925);
   }

   private boolean clickBiomeList(double mouseX, double mouseY) {
      int top = this.listTop();
      int right = 16 + this.listWidth - 8;
      if (!(mouseX < (double)16.0F) && !(mouseX > (double)right) && !(mouseY < (double)top) && !(mouseY > (double)this.listBottom())) {
         int row = ((int)mouseY - top - 4) / 18;
         if (row >= 0 && row < this.visibleRows()) {
            int index = this.listScroll + row;
            if (index >= 0 && index < this.filteredBiomes.size()) {
               this.selectedIndex = index;
               BiomeEntry selected = (BiomeEntry)this.filteredBiomes.get(index);
               this.selectedGlobal = selected.global;
               this.selectedBiomeId = selected.global ? "" : selected.id;
               this.refreshLabels();
               return true;
            } else {
               return false;
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   private boolean handleListScrollbarClick(double mouseX, double mouseY) {
      int scrollbarX = 16 + this.listWidth - 8;
      if (mouseX >= (double)scrollbarX && mouseX <= (double)(16 + this.listWidth) && mouseY >= (double)this.listTop() && mouseY <= (double)this.listBottom()) {
         this.draggingListScrollbar = true;
         this.setListScrollFromMouse(mouseY);
         return true;
      } else {
         return false;
      }
   }

   private void setListScrollFromMouse(double mouseY) {
      int maxScroll = Math.max(0, this.filteredBiomes.size() - this.visibleRows());
      if (maxScroll <= 0) {
         this.listScroll = 0;
      } else {
         int trackHeight = Math.max(1, this.listBottom() - this.listTop() - 4);
         double normalized = Mth.clamp((mouseY - (double)this.listTop() - (double)2.0F) / Math.max((double)1.0F, (double)trackHeight), (double)0.0F, (double)1.0F);
         this.listScroll = this.clampListScroll((int)Math.round(normalized * (double)maxScroll));
      }
   }

   private void keepSelectedBiomeVisible() {
      if (this.selectedIndex >= 0) {
         if (this.selectedIndex < this.listScroll) {
            this.listScroll = this.selectedIndex;
         } else if (this.selectedIndex >= this.listScroll + this.visibleRows()) {
            this.listScroll = this.selectedIndex - this.visibleRows() + 1;
         }

         this.listScroll = this.clampListScroll(this.listScroll);
      }
   }

   private BiomeEntry selectedBiome() {
      return this.selectedIndex >= 0 && this.selectedIndex < this.filteredBiomes.size() ? (BiomeEntry)this.filteredBiomes.get(this.selectedIndex) : null;
   }

   private int listTop() {
      return 76;
   }

   private int listBottom() {
      return Math.max(this.listTop() + 18 + 4, this.height - 44);
   }

   private int visibleRows() {
      return Math.max(1, (this.listBottom() - this.listTop() - 6) / 18);
   }

   private int clampListScroll(int scroll) {
      return Math.max(0, Math.min(scroll, Math.max(0, this.filteredBiomes.size() - this.visibleRows())));
   }

   private void layoutWorldColorPicker(int x, int width, int y) {
      this.wheelSize = Math.min(142, Math.max(112, width - 210));
      this.wheelX = x;
      this.wheelY = y;
      this.hueBarWidth = 16;
      this.hueBarHeight = this.wheelSize;
      this.hueBarX = this.wheelX + this.wheelSize + 8;
      this.hueBarY = this.wheelY;
      this.colorInfoX = this.hueBarX + this.hueBarWidth + 12;
      this.colorInfoY = y;
      this.colorInfoWidth = Math.max(92, x + width - this.colorInfoX);
      this.savedColorSize = 16;
      this.savedColorGap = 5;
      this.savedColorsX = this.colorInfoX;
      this.savedColorsY = this.colorInfoY + 84;
   }

   private void renderColorPickerPanelBackground(GuiGraphicsExtractor guiGraphics) {
      if (this.wheelSize > 0) {
         int left = this.wheelX - 6;
         int top = this.wheelY - 6;
         int right = Math.max(this.colorInfoX + this.colorInfoWidth, this.hueBarX + this.hueBarWidth) + 6;
         int bottom = Math.max(this.wheelY + this.wheelSize, this.savedColorsBottom()) + 6;
         guiGraphics.fill(left, top, right, bottom, -1442379246);
         guiGraphics.fill(left, top, right, top + 1, -1998659874);
         guiGraphics.fill(left, bottom - 1, right, bottom, 1717200244);
         guiGraphics.fill(left, top, left + 1, bottom, -1998659874);
         guiGraphics.fill(right - 1, top, right, bottom, 1717200244);
      }
   }

   private void renderWorldColorPicker(GuiGraphicsExtractor guiGraphics) {
      if (this.wheelSize > 0) {
         float[] hsb = this.selectedHsb();
         float hue = hsb[0];
         float saturation = hsb[1];
         float brightness = hsb[2];
         int rgb = this.selectedProfileView().color & 16777215;
         guiGraphics.fill(this.wheelX - 2, this.wheelY - 2, this.wheelX + this.wheelSize + 2, this.wheelY + this.wheelSize + 2, -1441524716);
         if (this.ensureColorWheelTexture(hue)) {
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, this.colorWheelTextureId, this.wheelX, this.wheelY, 0.0F, 0.0F, this.wheelSize, this.wheelSize, this.wheelSize, this.wheelSize, this.wheelSize, this.wheelSize);
         }

         int markerX = this.wheelX + Math.round(saturation * (float)(this.wheelSize - 1));
         int markerY = this.wheelY + Math.round((1.0F - brightness) * (float)(this.wheelSize - 1));
         guiGraphics.fill(markerX - 4, markerY, markerX - 1, markerY + 1, -1);
         guiGraphics.fill(markerX + 2, markerY, markerX + 5, markerY + 1, -1);
         guiGraphics.fill(markerX, markerY - 4, markerX + 1, markerY - 1, -1);
         guiGraphics.fill(markerX, markerY + 2, markerX + 1, markerY + 5, -1);
         guiGraphics.fill(markerX - 1, markerY - 1, markerX + 2, markerY + 2, -16777216);
         this.renderHueBar(guiGraphics, hue);
         this.renderSelectedColorPanel(guiGraphics, rgb);
         this.renderSavedColorSwatches(guiGraphics);
      }
   }

   private void renderHueBar(GuiGraphicsExtractor guiGraphics, float hue) {
      guiGraphics.fill(this.hueBarX - 1, this.hueBarY - 1, this.hueBarX + this.hueBarWidth + 1, this.hueBarY + this.hueBarHeight + 1, -8355712);

      for(int py = 0; py < this.hueBarHeight; ++py) {
         float rowHue = (float)py / (float)Math.max(1, this.hueBarHeight - 1);
         int color = -16777216 | Color.HSBtoRGB(rowHue, 1.0F, 1.0F) & 16777215;
         guiGraphics.fill(this.hueBarX, this.hueBarY + py, this.hueBarX + this.hueBarWidth, this.hueBarY + py + 1, color);
      }

      int markerY = this.hueBarY + Math.round(hue * (float)(this.hueBarHeight - 1));
      guiGraphics.fill(this.hueBarX - 3, markerY - 1, this.hueBarX + this.hueBarWidth + 3, markerY + 2, -1);
      guiGraphics.fill(this.hueBarX - 2, markerY, this.hueBarX + this.hueBarWidth + 2, markerY + 1, -16777216);
   }

   private void renderSelectedColorPanel(GuiGraphicsExtractor guiGraphics, int rgb) {
      String hex = String.format(Locale.ROOT, "#%06X", rgb);
      int red = rgb >> 16 & 255;
      int green = rgb >> 8 & 255;
      int blue = rgb & 255;
      guiGraphics.text(this.font, "Shore Foam Color", this.colorInfoX, this.colorInfoY, -1);
      guiGraphics.text(this.font, hex, this.colorInfoX, this.colorInfoY + 12, -2236963);
      guiGraphics.text(this.font, "RGB " + red + ", " + green + ", " + blue, this.colorInfoX, this.colorInfoY + 24, -4342339);
      int previewY = this.colorInfoY + 40;
      guiGraphics.fill(this.colorInfoX - 1, previewY - 1, this.colorInfoX + this.colorInfoWidth + 1, previewY + 19, -16777216);
      guiGraphics.fill(this.colorInfoX, previewY, this.colorInfoX + this.colorInfoWidth, previewY + 18, -16777216 | rgb);
   }

   private void renderSavedColorSwatches(GuiGraphicsExtractor guiGraphics) {
      List<Integer> colors = this.savedWorldColors();
      guiGraphics.text(this.font, "Saved Colors", this.savedColorsX, this.savedColorsY - 12, -4342339);
      int visible = this.visibleSavedColorCount(colors.size());

      for(int i = 0; i < visible; ++i) {
         int swatchX = this.savedColorCellX(i);
         int swatchY = this.savedColorCellY(i);
         int color = -16777216 | (colors.get(i) == null ? 16777215 : (Integer)colors.get(i) & 16777215);
         guiGraphics.fill(swatchX - 1, swatchY - 1, swatchX + this.savedColorSize + 1, swatchY + this.savedColorSize + 1, -16777216);
         guiGraphics.fill(swatchX, swatchY, swatchX + this.savedColorSize, swatchY + this.savedColorSize, color);
      }

      int addX = this.savedColorCellX(visible);
      int addY = this.savedColorCellY(visible);
      guiGraphics.fill(addX - 1, addY - 1, addX + this.savedColorSize + 1, addY + this.savedColorSize + 1, -16777216);
      guiGraphics.fill(addX, addY, addX + this.savedColorSize, addY + this.savedColorSize, -13619152);
      int cx = addX + this.savedColorSize / 2;
      int cy = addY + this.savedColorSize / 2;
      guiGraphics.fill(cx - 4, cy, cx + 5, cy + 1, -1);
      guiGraphics.fill(cx, cy - 4, cx + 1, cy + 5, -1);
   }

   private boolean tryColorSquarePick(double mouseX, double mouseY) {
      if (!inside(mouseX, mouseY, this.wheelX, this.wheelY, this.wheelSize, this.wheelSize)) {
         return false;
      } else {
         float[] hsb = this.selectedHsb();
         float saturation = (float)((mouseX - (double)this.wheelX) / (double)Math.max(1.0F, (float)(this.wheelSize - 1)));
         float brightness = 1.0F - (float)((mouseY - (double)this.wheelY) / (double)Math.max(1.0F, (float)(this.wheelSize - 1)));
         this.setSelectedColor(Color.HSBtoRGB(hsb[0], Mth.clamp(saturation, 0.0F, 1.0F), Mth.clamp(brightness, 0.0F, 1.0F)) & 16777215);
         return true;
      }
   }

   private boolean tryHueBarPick(double mouseX, double mouseY) {
      if (!inside(mouseX, mouseY, this.hueBarX, this.hueBarY, this.hueBarWidth, this.hueBarHeight)) {
         return false;
      } else {
         float[] hsb = this.selectedHsb();
         float hue = (float)((mouseY - (double)this.hueBarY) / (double)Math.max(1.0F, (float)(this.hueBarHeight - 1)));
         this.setSelectedColor(Color.HSBtoRGB(Mth.clamp(hue, 0.0F, 1.0F), hsb[1], hsb[2]) & 16777215);
         return true;
      }
   }

   private boolean trySavedColorClick(double mouseX, double mouseY) {
      List<Integer> colors = this.savedWorldColors();
      int visible = this.visibleSavedColorCount(colors.size());

      for(int i = 0; i < visible; ++i) {
         int swatchX = this.savedColorCellX(i);
         int swatchY = this.savedColorCellY(i);
         if (inside(mouseX, mouseY, swatchX, swatchY, this.savedColorSize, this.savedColorSize)) {
            Integer color = (Integer)colors.get(i);
            if (color != null) {
               this.setSelectedColor(color & 16777215);
            }

            return true;
         }
      }

      int addX = this.savedColorCellX(visible);
      int addY = this.savedColorCellY(visible);
      if (inside(mouseX, mouseY, addX, addY, this.savedColorSize, this.savedColorSize)) {
         this.rememberCurrentColor();
         return true;
      } else {
         return false;
      }
   }

   private void setSelectedColor(int color) {
      this.updateSelectedProfile((profile) -> profile.color = color & 16777215);
   }

   private float[] selectedHsb() {
      int color = this.selectedProfileView().color & 16777215;
      return Color.RGBtoHSB(color >> 16 & 255, color >> 8 & 255, color & 255, (float[])null);
   }

   private List<Integer> savedWorldColors() {
      if (this.editing.savedPickerColors == null) {
         this.editing.savedPickerColors = new ArrayList();
      }

      return this.editing.savedPickerColors;
   }

   private void rememberCurrentColor() {
      int rgb = this.selectedProfileView().color & 16777215;
      List<Integer> colors = this.savedWorldColors();
      colors.removeIf((saved) -> saved != null && (saved & 16777215) == rgb);
      colors.add(0, rgb);

      while(colors.size() > 10) {
         colors.remove(colors.size() - 1);
      }

   }

   private int savedColorColumns() {
      int cell = Math.max(1, this.savedColorSize + this.savedColorGap);
      return Math.max(1, (this.colorInfoWidth + this.savedColorGap) / cell);
   }

   private int visibleSavedColorCount(int savedCount) {
      int cells = Math.max(1, this.savedColorColumns() * 2);
      return Math.min(Math.min(10, savedCount), Math.max(0, cells - 1));
   }

   private int savedColorsBottom() {
      int usedCells = this.visibleSavedColorCount(this.savedWorldColors().size()) + 1;
      int rows = Math.max(1, (usedCells + this.savedColorColumns() - 1) / this.savedColorColumns());
      return this.savedColorsY + rows * this.savedColorSize + (rows - 1) * this.savedColorGap;
   }

   private int savedColorCellX(int index) {
      return this.savedColorsX + index % this.savedColorColumns() * (this.savedColorSize + this.savedColorGap);
   }

   private int savedColorCellY(int index) {
      return this.savedColorsY + index / this.savedColorColumns() * (this.savedColorSize + this.savedColorGap);
   }

   private boolean ensureColorWheelTexture(float hue) {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft != null && minecraft.getTextureManager() != null && this.wheelSize > 0) {
         boolean sizeChanged = this.cachedWheelSize != this.wheelSize;
         boolean hueChanged = Math.abs(this.cachedWheelHue - hue) > 1.0E-4F;
         if (this.colorWheelTexture == null || this.colorWheelTextureId == null || sizeChanged) {
            this.releaseColorWheelTexture();
            NativeImage image = new NativeImage(this.wheelSize, this.wheelSize, false);
            this.colorWheelTexture = new DynamicTexture(() -> "Shine shore foam color picker", image);
            this.colorWheelTextureId = Identifier.fromNamespaceAndPath("vybrantvisual", "ui/shore_foam_color_picker");
            minecraft.getTextureManager().register(this.colorWheelTextureId, this.colorWheelTexture);
            sizeChanged = true;
         }

         if (sizeChanged || hueChanged) {
            NativeImage image = this.colorWheelTexture.getPixels();
            if (image == null) {
               return false;
            }

            for(int py = 0; py < this.wheelSize; ++py) {
               for(int px = 0; px < this.wheelSize; ++px) {
                  float saturation = (float)px / (float)Math.max(1, this.wheelSize - 1);
                  float brightness = 1.0F - (float)py / (float)Math.max(1, this.wheelSize - 1);
                  image.setPixel(px, py, -16777216 | Color.HSBtoRGB(hue, saturation, brightness) & 16777215);
               }
            }

            this.colorWheelTexture.upload();
            this.cachedWheelSize = this.wheelSize;
            this.cachedWheelHue = hue;
         }

         return true;
      } else {
         return false;
      }
   }

   private void releaseColorWheelTexture() {
      if (this.colorWheelTexture != null) {
         this.colorWheelTexture.close();
         this.colorWheelTexture = null;
      }

      this.colorWheelTextureId = null;
      this.cachedWheelSize = -1;
      this.cachedWheelHue = -1.0F;
   }

   private String resolveCurrentBiomeId() {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft != null && minecraft.level != null) {
         Entity camera = minecraft.getCameraEntity();
         if (camera == null) {
            camera = minecraft.player;
         }

         return camera == null ? "" : (String)minecraft.level.getBiome(camera.blockPosition()).unwrapKey().map((key) -> key.identifier().toString()).orElse("");
      } else {
         return "";
      }
   }

   private static String shortBiomeLabel(String biomeId) {
      if (biomeId != null && !biomeId.isBlank()) {
         String path = biomeId.substring(biomeId.indexOf(58) + 1).replace('_', ' ');
         StringBuilder label = new StringBuilder(path.length());
         boolean upper = true;

         for(int i = 0; i < path.length(); ++i) {
            char c = path.charAt(i);
            label.append(upper ? Character.toUpperCase(c) : c);
            upper = c == ' ';
         }

         return label.toString();
      } else {
         return "Selected Biome";
      }
   }

   private void applyPreview() {
   }

   private <T extends AbstractWidget> T addDescribedWidget(T widget, String description) {
      this.describedWidgets.add(new DescribedWidget(widget, description));
      return (T)(this.addRenderableWidget(widget));
   }

   private String hoverDescription(int mouseX, int mouseY) {
      for(DescribedWidget described : this.describedWidgets) {
         AbstractWidget widget = described.widget();
         if (widget.visible && mouseX >= widget.getX() && mouseX < widget.getX() + widget.getWidth() && mouseY >= widget.getY() && mouseY < widget.getY() + widget.getHeight()) {
            return described.description();
         }
      }

      return "";
   }

   private String trimToWidth(String text, int width) {
      if (this.font.width(text) <= width) {
         return text;
      } else {
         String var10000 = this.font.plainSubstrByWidth(text, Math.max(0, width - this.font.width("...")));
         return var10000 + "...";
      }
   }

   private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
      return mouseX >= (double)x && mouseX < (double)(x + width) && mouseY >= (double)y && mouseY < (double)(y + height);
   }

   private static record BiomeEntry(String id, boolean global) {
   }

   private static record DescribedWidget(AbstractWidget widget, String description) {
   }

   private final class LabeledSlider extends AbstractSliderButton {
      private final Component label;
      private final double min;
      private final double max;
      private final double step;
      private final DoubleSupplier getter;
      private final DoubleConsumer setter;
      private final DoubleFunction<String> formatter;

      private LabeledSlider(int x, int y, int width, Component label, double min, double max, double step, DoubleSupplier getter, DoubleConsumer setter, DoubleFunction<String> formatter) {
         Objects.requireNonNull(ExperimentalShoreFoamConfigScreen.this);
         super(x, y, width, 20, Component.empty(), (double)0.0F);
         this.label = label;
         this.min = min;
         this.max = max;
         this.step = step;
         this.getter = getter;
         this.setter = setter;
         this.formatter = formatter;
         this.syncFromValue();
      }

      public void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
         this.syncFromValue();
         super.extractWidgetRenderState(guiGraphics, mouseX, mouseY, partialTick);
      }

      private void syncFromValue() {
         double current = Mth.clamp(this.getter.getAsDouble(), this.min, this.max);
         this.value = (current - this.min) / (this.max - this.min);
         this.updateMessage();
      }

      protected void updateMessage() {
         double resolved = this.min + this.value * (this.max - this.min);
         resolved = (double)Math.round(resolved / this.step) * this.step;
         resolved = Mth.clamp(resolved, this.min, this.max);
         this.setMessage(this.label.copy().append(": ").append((String)this.formatter.apply(resolved)));
      }

      protected void applyValue() {
         double resolved = this.min + this.value * (this.max - this.min);
         resolved = (double)Math.round(resolved / this.step) * this.step;
         this.setter.accept(Mth.clamp(resolved, this.min, this.max));
      }
   }
}
