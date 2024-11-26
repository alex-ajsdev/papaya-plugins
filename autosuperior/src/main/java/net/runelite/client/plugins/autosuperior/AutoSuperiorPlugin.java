package net.runelite.client.plugins.autosuperior;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import org.pf4j.Extension;

import javax.inject.Inject;

@Extension
@PluginDescriptor(
        name = "111 Auto Superior Pot",
        enabledByDefault = false,
        description = "Automatically drinks the Superior potion when stats drop below a configurable threshold.",
        tags = {"superior", "potion", "111", "papaya"}
)
@Slf4j

public class AutoSuperiorPlugin extends Plugin {
    @Inject
    private Client client;

    @Inject
    private AutoSuperiorConfig config;

    @Inject
    private ConfigManager configManager;

    @Provides
    AutoSuperiorConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(AutoSuperiorConfig.class);
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        // Get current and desired Ranged levels
        int currentRangedLevel = client.getBoostedSkillLevel(Skill.RANGED);
        int desiredRangedLevel = config.rangedLevel();

        // Check if current level is below desired level
        if (currentRangedLevel < desiredRangedLevel) {
            log.info("Ranged level below threshold ({} < {}). Attempting to drink Superior potion.", currentRangedLevel, desiredRangedLevel);
            drinkSuperiorPotion();
        }
    }

    private void drinkSuperiorPotion() {
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null) {
            log.warn("Inventory is not accessible.");
            return;
        }

        Item[] items = inventory.getItems();
        for (int i = 0; i < items.length; i++) {
            Item item = items[i];
            if (item != null) {
                String itemName = client.getItemDefinition(item.getId()).getName().toLowerCase();

                // Check for the "Superior potion"
                if (itemName.contains("superior potion")) {
                    log.info("Found {} in inventory. Drinking...", itemName);

                    // Simulate drinking the potion
                    client.invokeMenuAction(
                            "Drink",
                            itemName,
                            2,
                            MenuAction.CC_OP.getId(),
                            i, // Item index in inventory
                            WidgetInfo.INVENTORY.getId()
                    );

                    log.info("Drank {}.", itemName);
                    return;
                }
            }
        }

        log.warn("No Superior potion found in inventory.");
    }
}