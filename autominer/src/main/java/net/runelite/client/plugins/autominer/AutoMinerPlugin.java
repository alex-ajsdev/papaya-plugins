package net.runelite.client.plugins.autominer;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*; // For accessing game objects, NPCs, widgets, skills, etc.
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick; // For game tick event handling
import net.runelite.api.widgets.Widget; // For interacting with widgets
import net.runelite.api.widgets.WidgetInfo; // For widget identifiers like BANK_DEPOSIT_INVENTORY
import net.runelite.client.callback.ClientThread; // For executing tasks on the RuneLite client thread
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe; // To listen for RuneLite events
import net.runelite.client.plugins.Plugin; // The base class for all plugins
import net.runelite.client.plugins.PluginDescriptor; // For metadata about the plugin
import org.pf4j.Extension; // For enabling the plugin in RuneLite

import javax.inject.Inject; // For dependency injection
import java.util.*; // For collections like List, Set, Optional, etc.

@Extension
@PluginDescriptor(
        name = "111 Auto Miner",
        enabledByDefault = false,
        description = "Mines at dz for you!",
        tags = {"miner", "auto", "111", "papaya"}
)

@Slf4j

public class AutoMinerPlugin extends Plugin {

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private AutoMinerConfig config;

    @Inject
    private ConfigManager configManager;

    @Provides
    AutoMinerConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(AutoMinerConfig.class);
    }

    private boolean banking = false;

    @Subscribe
    public void onGameTick(GameTick event) {
        if (banking) {
            handleBanking();
        } else if (isInventoryFull()) {
            log.info("Inventory is full. Switching to banking.");
            banking = true;
        } else {
            mineOre();
        }
    }

    private void mineOre() {
        OreType targetOre = config.targetOre();
        String oreName = targetOre.toString().toLowerCase(); // Convert to lowercase for matching
        Optional<GameObject> rock = findNearestRock(oreName);

        if (rock.isPresent()) {
            log.info("Mining {}", oreName);
            interactWithGameObject(rock.get(), "Mine");
        } else {
            log.info("No {} rock found nearby.", oreName);
        }
    }


    private Optional<GameObject> findNearestRock(String oreName) {
        Scene scene = client.getScene();
        Tile[][][] tiles = scene.getTiles();
        Player player = client.getLocalPlayer();

        if (tiles == null || player == null) {
            return Optional.empty();
        }

        int plane = client.getPlane();
        WorldPoint playerLocation = player.getWorldLocation();
        GameObject nearestRock = null;
        int closestDistance = Integer.MAX_VALUE;

        for (Tile[] row : tiles[plane]) {
            if (row == null) continue;
            for (Tile tile : row) {
                if (tile == null) continue;

                for (GameObject obj : tile.getGameObjects()) {
                    if (obj != null && obj.getName() != null && obj.getName().toLowerCase().contains(oreName)) {
                        int distance = playerLocation.distanceTo(obj.getWorldLocation());
                        if (distance < closestDistance) {
                            closestDistance = distance;
                            nearestRock = obj;
                        }
                    }
                }
            }
        }

        return Optional.ofNullable(nearestRock);
    }


    private boolean isInventoryFull() {
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null) {
            return false; // Inventory is not accessible, so it's not full
        }

        // Count the number of non-null items in the inventory
        long filledSlots = Arrays.stream(inventory.getItems())
                .filter(Objects::nonNull)
                .count();
        return filledSlots == 28;
    }

    private void handleBanking() {
        Optional<GameObject> bankChest = findNearestBankChest();

        if (bankChest.isPresent()) {
            log.info("Opening bank chest.");
            interactWithGameObject(bankChest.get(), "Use");
        } else {
            log.warn("No bank chest found nearby.");
            return;
        }

        // Wait for the bank interface to open and deposit all
        clientThread.invokeLater(() -> {
            Widget depositAllButton = client.getWidget(WidgetInfo.BANK_DEPOSIT_INVENTORY);
            if (depositAllButton != null && !depositAllButton.isHidden()) {
                log.info("Clicking 'Deposit All' button.");
                client.invokeMenuAction(
                        "Deposit-All",
                        "",
                        -1,
                        MenuAction.CC_OP.getId(),
                        -1,
                        786474
                );
            } else {
                log.warn("Bank interface not open or 'Deposit All' button not found.");
            }
        });

        banking = false;
    }

    private Optional<GameObject> findNearestBankChest() {
        Scene scene = client.getScene();
        Tile[][][] tiles = scene.getTiles();
        Player player = client.getLocalPlayer();

        if (tiles == null || player == null) {
            return Optional.empty();
        }

        int plane = client.getPlane();
        WorldPoint playerLocation = player.getWorldLocation();
        GameObject nearestChest = null;
        int closestDistance = Integer.MAX_VALUE;

        // Traverse all tiles on the current plane
        for (Tile[] row : tiles[plane]) {
            if (row == null) continue;
            for (Tile tile : row) {
                if (tile == null) continue;

                // Check for game objects on the tile
                for (GameObject obj : tile.getGameObjects()) {
                    if (obj != null && obj.getName() != null && obj.getName().equalsIgnoreCase("Bank chest")) {
                        int distance = playerLocation.distanceTo(obj.getWorldLocation());
                        if (distance < closestDistance) {
                            closestDistance = distance;
                            nearestChest = obj;
                        }
                    }
                }
            }
        }

        return Optional.ofNullable(nearestChest);
    }

    private void interactWithGameObject(GameObject obj, String action) {
        client.invokeMenuAction(
                action,
                obj.getName(),
                obj.getId(),
                MenuAction.GAME_OBJECT_FIRST_OPTION.getId(),
                obj.getSceneMinLocation().getX(),
                obj.getSceneMinLocation().getY()
        );
    }
}
