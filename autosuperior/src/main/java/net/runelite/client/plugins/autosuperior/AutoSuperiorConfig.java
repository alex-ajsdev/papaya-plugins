package net.runelite.client.plugins.autosuperior;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("autosuperiorpotion")
public interface AutoSuperiorConfig extends Config {

    @ConfigItem(
            keyName = "rangedLevel",
            name = "Ranged Level (before potting)",
            description = "Minimum Ranged level before drinking the Superior potion",
            position = 1
    )
    default int rangedLevel() {
        return 125; // Default threshold for Ranged
    }
}