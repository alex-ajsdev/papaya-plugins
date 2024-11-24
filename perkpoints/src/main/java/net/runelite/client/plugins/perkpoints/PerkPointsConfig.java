package net.runelite.client.plugins.perkpoints;

import net.runelite.client.config.*;

@ConfigGroup("PerkPoints")
public interface PerkPointsConfig extends Config {

    @Range(min = 1)
    @ConfigItem(
            keyName = "tickDelay",
            name = "Tick Delay",
            description = "How many ticks should pass before calculating next action",
            position = 1
    )
    default int tickDelay()
    {
        return 2;
    }


    @Range(min = 2)
    @ConfigItem(
            keyName = "minBars",
            name = "Minimum Bars",
            description = "The minimum amount of adamant bars you need before restock",
            position = 2
    )
    default int minBars()
    {
        return 2;
    }


    @ConfigItem(
            keyName = "startTask",
            name = "Start With Task",
            description = "Enable if you already have a perk task when starting the plugin",
            position = 3
    )
    default boolean startTask()
    {
        return false;
    }


    @ConfigItem(
            keyName = "enableOverlay",
            name = "Enable Overlay",
            description = "Enable to turn on in game overlay",
            position = 4
    )
    default boolean enableOverlay()
    {
        return true;
    }


    @ConfigItem(
            keyName = "startButton",
            name = "Start/Stop",
            description = "Starts / Stops the plugin",
            position = 5
    )
    default Button startButton()
    {
        return new Button();
    }

}
