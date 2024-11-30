package net.runelite.client.plugins.autominer;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("autominer")
public interface AutoMinerConfig extends Config {

    @ConfigItem(
            keyName = "targetOre",
            name = "Target Ore",
            description = "Select the ore type to mine."
    )
    default OreType targetOre() {
        return OreType.ADAMANTITE; // Default
    }
}
