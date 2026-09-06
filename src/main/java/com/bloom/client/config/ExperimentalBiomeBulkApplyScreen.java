package com.bloom.client.config;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Simple checklist screen: pick a subset of candidate ids (blocks/fluids), then apply.
 * Used by BloomConfigScreen's "Copy Bloom To Blocks/Fluids" action.
 */
public final class ExperimentalBiomeBulkApplyScreen extends FixedScaleScreen {
   private static final int ROW_HEIGHT = 18;

   private final Screen parent;
   private final String noun;
   private final List<String> allCandidates;
   private final List<String> filtered = new ArrayList<>();
   private final Set<String> selected = new LinkedHashSet<>();
   private final Consumer<Set<String>> onApply;
   private EditBox searchBox;
   private Button applyButton;
   private String searchText = "";
   private boolean initializing;

   private ExperimentalBiomeBulkApplyScreen(Screen parent, Component title, String sourceId, String noun, List<String> candidates, Consumer<Set<String>> onApply) {
      super(title);
      this.parent = parent;
      this.noun = noun;
      this.allCandidates = new ArrayList<>(candidates);
      this.onApply = onApply;
   }

   public static Screen create(Screen parent, Component title, String sourceId, String noun, List<String> candidates, Consumer<Set<String>> onApply) {
      return new ExperimentalBiomeBulkApplyScreen(parent, title, sourceId, noun, candidates, onApply);
   }

   protected void init() {
      this.initializing = true;
      this.applyFixedScaleDimensions();
      this.clearWidgets();

      int panelWidth = Math.min(360, this.width - 32);
      int x = (this.width - panelWidth) / 2;
      int top = 32;

      this.searchBox = new EditBox(this.font, x, top, panelWidth, 18, Component.literal("Search"));
      this.searchBox.setValue(this.searchText);
      this.searchBox.setHint(Component.literal("Search " + this.noun + "..."));
      this.searchBox.setResponder(this::applyFilter);
      this.addRenderableWidget(this.searchBox);

      this.recomputeFiltered();

      int bottom = this.height - 56;
      int listTop = top + 24;
      int visibleRows = Math.max(1, (bottom - listTop) / ROW_HEIGHT);
      int shown = Math.min(visibleRows, this.filtered.size());
      for (int row = 0; row < shown; row++) {
         String id = this.filtered.get(row);
         int rowY = listTop + row * ROW_HEIGHT;
         Checkbox checkbox = new Checkbox.Builder(Component.literal(id), this.font)
            .pos(x, rowY)
            .selected(this.selected.contains(id))
            .onValueChange((box, value) -> {
               if (value) {
                  this.selected.add(id);
               } else {
                  this.selected.remove(id);
               }
               this.refreshApplyLabel();
            })
            .build();
         this.addRenderableWidget(checkbox);
      }

      this.applyButton = Button.builder(this.applyLabel(), (button) -> {
         if (this.onApply != null) {
            this.onApply.accept(new LinkedHashSet<>(this.selected));
         }
         this.minecraft.setScreen(this.parent);
      }).bounds(x, this.height - 28, panelWidth / 2 - 4, 20).build();
      this.addRenderableWidget(this.applyButton);

      this.addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), (button) -> this.onClose())
         .bounds(x + panelWidth / 2 + 4, this.height - 28, panelWidth / 2 - 4, 20).build());
      this.initializing = false;
   }

   private Component applyLabel() {
      return Component.literal("Apply to " + this.selected.size() + " " + this.noun);
   }

   private void refreshApplyLabel() {
      if (this.applyButton != null) {
         this.applyButton.setMessage(this.applyLabel());
      }
   }

   private void recomputeFiltered() {
      String needle = this.searchText.trim().toLowerCase(Locale.ROOT);
      this.filtered.clear();
      for (String candidate : this.allCandidates) {
         if (needle.isEmpty() || candidate.toLowerCase(Locale.ROOT).contains(needle)) {
            this.filtered.add(candidate);
         }
      }
   }

   private void applyFilter(String text) {
      this.searchText = text == null ? "" : text;
      if (!this.initializing) {
         this.init();
      }
   }

   public void onClose() {
      this.minecraft.setScreen(this.parent);
   }
}
