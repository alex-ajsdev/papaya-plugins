package net.runelite.client.plugins.autoprayerpot;

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
        name = "111 Auto Prayer Pot",
        enabledByDefault = false,
        description = "Automatically drinks prayer potions or restore potions when prayer points drop below a configurable threshold",
        tags = {"prayer", "automation", "potion", "111", "papaya"}
)
@Slf4j

public class AutoPrayerPotPlugin extends Plugin {
    @Inject
    private Client client;

    @Inject
    private AutoPrayerPotConfig config;

    @Inject
    private ConfigManager configManager;

    @Provides
    AutoPrayerPotConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(AutoPrayerPotConfig.class);
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        // Get current prayer points
        int currentPrayer = client.getBoostedSkillLevel(Skill.PRAYER);
        int maxPrayer = client.getRealSkillLevel(Skill.PRAYER);

        if (maxPrayer == 0) {
            log.warn("Max prayer level is 0. This should not happen.");
            return;
        }

        double prayerPercent = (double) currentPrayer / maxPrayer;

        // Get threshold from config
        double prayerThreshold = Math.max(1, Math.min(config.prayerThreshold(), 100)) / 100.0;

        // Check if prayer points are below threshold
        if (prayerPercent <= prayerThreshold) {
            log.info("Prayer points below threshold: {}%. Attempting to drink prayer potion.", (int) (prayerPercent * 100));
            drinkPrayerPotion();
        }
    }

    private void drinkPrayerPotion() {
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

                // Check for prayer potion or restore potion
                if (itemName.contains("prayer potion") || itemName.contains("super restore") || itemName.contains("sanfew")) {
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

        log.warn("No prayer or restore potion found in inventory.");
    }
}
