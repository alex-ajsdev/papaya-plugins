package net.runelite.client.plugins.autoprayer;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import org.pf4j.Extension;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Extension
@PluginDescriptor(
        name = "Auto Prayer",
        enabledByDefault = false,
        description = "Papaya - Auto Prayer",
        tags = {"papaya"}
)
@Slf4j
public class AutoPrayerPlugin extends Plugin {
    @Inject
    private Client client;

    @Subscribe
    public void onGameTick(GameTick event) {
        // Check if inventory contains dragon bones
        int boneCount = getInventoryCount("dragon bone");

        if ( boneCount == 0) {
            // If no bones are present, withdraw from the bank
            Widget bankItemContainer = client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER.getId());
            if (bankItemContainer == null) {
                interactWithClosestGameObject("Bank", "Use", MenuAction.GAME_OBJECT_FIRST_OPTION);
                log.info("Opening Bank");
                return;
            }

            withdrawAllItemFromBank("dragon bone");
            log.info("Withdrawing bones");
            return;
        }

        // Find the nearest altar
        Optional<GameObject> nearestAltar = getNearbyGameObjects().stream()
                .filter(obj -> obj.getName() != null && obj.getName().toLowerCase().contains("altar"))
                .min(Comparator.comparingInt(obj -> {
                    WorldPoint playerLocation = client.getLocalPlayer().getWorldLocation();
                    return playerLocation.distanceTo(obj.getWorldLocation());
                }));

        if (nearestAltar.isEmpty()) {
            log.info("No altar nearby to use bones.");
            return;
        }

        // Use the first dragon bone on the altar
        GameObject altar = nearestAltar.get();
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory != null) {
            Item[] items = inventory.getItems();
            for (int i = 0; i < items.length; i++) {
                Item item = items[i];
                if (item != null) {

                    String itemName = client.getItemDefinition(item.getId()).getName();
                    if (itemName.toLowerCase().contains("dragon bone")) {
                        // Use the item in the inventory
                        client.invokeMenuAction(
                                "Use",
                                itemName,
                                0, // 'Id' as 0 per your logs
                                MenuAction.WIDGET_TARGET.getId(),
                                i, // Index of the item in the inventory
                                WidgetInfo.INVENTORY.getId()
                        );

                        // Use the item on the altar
                        client.invokeMenuAction(
                                "Use",
                                itemName,
                                411, // 'Id' for the game object interaction
                                MenuAction.WIDGET_TARGET_ON_GAME_OBJECT.getId(),
                                altar.getSceneMinLocation().getX(), // Param 0
                                altar.getSceneMinLocation().getY()  // Param 1
                        );
                        return;
                    }
                }
            }

        }
    }




    private int getInventoryCount(String itemName) {
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null) {
            log.warn("Inventory is not accessible.");
            return -1;
        }

        Item[] items = inventory.getItems();
        int count = 0;
        for (Item item : items) {
            if (item != null) {
                String itemInInventoryName = client.getItemDefinition(item.getId()).getName();
                if (itemInInventoryName.toLowerCase().contains(itemName.toLowerCase())) {
                    count += item.getQuantity();
                }
            }
        }
        return count;
    }

    private List<GameObject> getNearbyGameObjects() {
        List<GameObject> gameObjects = new ArrayList<>();
        Scene scene = client.getScene();
        if (scene == null) {
            log.error("Scene is null. Cannot retrieve game objects.");
            return gameObjects;
        }

        Tile[][][] tiles = scene.getTiles();
        if (tiles == null) {
            log.error("Tiles are null. Cannot retrieve game objects.");
            return gameObjects;
        }

        int plane = client.getPlane();
        for (int x = 0; x < tiles[plane].length; x++) {
            for (int y = 0; y < tiles[plane][x].length; y++) {
                Tile tile = tiles[plane][x][y];
                if (tile == null) continue;

                GameObject[] objects = tile.getGameObjects();
                if (objects != null) {
                    for (GameObject object : objects) {
                        if (object != null) {
                            gameObjects.add(object);
                        }
                    }
                }
            }
        }

        return gameObjects;
    }

    private boolean interactWithClosestGameObject(String objectName, String actionName, MenuAction menuAction) {
        Optional<GameObject> optionalGameObject = getNearbyGameObjects().stream()
                .filter(obj -> obj.getName().toLowerCase().contains(objectName.toLowerCase()))
                .min(Comparator.comparingInt(obj -> {
                    WorldPoint playerLocation = client.getLocalPlayer().getWorldLocation();
                    return playerLocation.distanceTo(obj.getWorldLocation());
                }));

        if (optionalGameObject.isEmpty()) {
            log.info("GameObject with name {} not found.", objectName);
            return false;
        }

        GameObject object = optionalGameObject.get();

        client.invokeMenuAction(
                actionName,
                object.getName(),
                object.getId(),
                menuAction.getId(),
                object.getSceneMinLocation().getX(),
                object.getSceneMinLocation().getY()
        );
        log.info("Interacting with GameObject '{}' using action '{}'", objectName, actionName);
        return true;

    }

    private void withdrawAllItemFromBank(String itemName) {
        Widget bankItemContainer = client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER.getId());
        if (bankItemContainer == null || bankItemContainer.getDynamicChildren() == null) {
            log.error("Bank is not open or item container is inaccessible.");
            return;
        }
        Widget[] items = bankItemContainer.getDynamicChildren();

        for (int i = 0; i < items.length; i++) {
            if (items[i] != null && items[i].getName().toLowerCase().contains(itemName.toLowerCase())) {
                client.invokeMenuAction(
                        "Withdraw-All",
                        items[i].getName(),
                        1,
                        MenuAction.CC_OP.getId(),
                        i,
                        bankItemContainer.getId()
                );
                log.info("Withdrew all of '{}'", itemName);
                return;
            }
        }
        log.info("Item {} not found", itemName);
    }
}
