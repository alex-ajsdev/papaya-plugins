package net.runelite.client.plugins.autoprayerpot;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("autoprayerpot") // Unique identifier for this plugin's configuration
public interface AutoPrayerPotConfig extends Config {

    @ConfigItem(
            keyName = "prayerThreshold",
            name = "Prayer Threshold (%)",
            description = "Set the percentage of prayer points below which a potion will be consumed",
            position = 1 // Determines the order in the settings menu
    )
    default int prayerThreshold() {
        return 20; // Default threshold is 20%
    }
}
