package net.runelite.client.plugins.perkpoints;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ConfigButtonClicked;
import net.runelite.api.events.GameTick;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.perkpoints.enums.FetchTaskStep;
import net.runelite.client.plugins.perkpoints.enums.PerkPointsState;
import net.runelite.client.ui.overlay.OverlayManager;
import org.pf4j.Extension;

import javax.inject.Inject;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;


@Extension
@PluginDescriptor(
        name = "Perk Points",
        enabledByDefault = false,
        description = "Papaya - Perk Points",
        tags = {"papaya"}
)
@Slf4j
public class PerkPointsPlugin extends Plugin {
    @Inject
    private Client client;

    @Inject
    private PerkPointsConfig config;

    @Inject
    private OverlayManager overlayManager;

    @Inject
    private PerkPointsOverlay overlay;

    @Inject
    private ConfigManager configManager;



    private int tickCounter = 0;
    private int tickDelay = 2;
    private int minBars = 2;
    private boolean started = false;

    PerkPointsState currentState = PerkPointsState.FETCH_TASK;
    FetchTaskStep fetchTaskStep = FetchTaskStep.CLICK_NPC;
    Instant startInstant;

    @Provides
    PerkPointsConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(PerkPointsConfig.class);
    }


    @Subscribe
    private void onConfigButtonPressed(ConfigButtonClicked event)
    {
        if (!event.getGroup().equalsIgnoreCase("PerkPoints"))
        {
            return;
        }
        if (event.getKey().equals("startButton"))
        {
            if (!started)
            {
                log.debug("Starting plugin...");
                if (client == null || client.getLocalPlayer() == null || client.getGameState() != GameState.LOGGED_IN)
                {
                    log.info("Startup failed: log-in before starting");
                    return;
                }

                tickDelay = config.tickDelay();
                minBars = config.minBars();
                if(config.startTask()) {
                    currentState = PerkPointsState.SMITHING;
                    fetchTaskStep = FetchTaskStep.SELECTED;
                }
                startInstant = Instant.now();
                overlayManager.add(overlay);
                started = true;
            }
            else
            {
                overlayManager.remove(overlay);
                currentState = PerkPointsState.FETCH_TASK;
                fetchTaskStep = FetchTaskStep.CLICK_NPC;
                startInstant = null;
                started = false;
            }
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if(!started) return;

        tickCounter++;
        if (tickCounter >= tickDelay) {
            switch (currentState) {
                case FETCH_TASK:
                    handleFetchTask();
                    break;
                case SMITHING:
                    handleSmithing();
                    break;
                case RESTOCKING:
                    handleRestocking();
                    break;
            }
            tickCounter = 0;
        }
    }

    @Subscribe
    private void onChatMessage(ChatMessage event) {
        String completionChatMessage = "You have completed your perk task and received";
        if (event.getMessage().contains(completionChatMessage)) {
            log.info("Perk Task complete!");
            currentState = PerkPointsState.FETCH_TASK;
            fetchTaskStep = FetchTaskStep.CLICK_NPC;
        }
    }

    private void handleFetchTask() {
        switch (fetchTaskStep) {
            case CLICK_NPC:
                if (interactWithNpc("Perk Master", "Get-task", MenuAction.NPC_THIRD_OPTION)) {
                    fetchTaskStep = FetchTaskStep.SELECT_TASK_TYPE;
                    log.info("Clicked on NPC, waiting for dialog...");
                }
                break;
            case SELECT_TASK_TYPE:
                if (selectDialogOption("Skilling")) {
                    fetchTaskStep = FetchTaskStep.SELECT_DIFFICULTY;
                    log.info("Selected Skilling");
                }
                break;
            case SELECT_DIFFICULTY:
                if (selectDialogOption("Elite")) {
                    fetchTaskStep = FetchTaskStep.SELECT_TASK;
                    log.info("Selected Elite difficulty");
                }
                break;
            case SELECT_TASK:
                if (selectDialogOption("Adamant full helm")) {
                    fetchTaskStep = FetchTaskStep.SELECTED;
                    log.info("Selected Adamant full helm");
                }
                break;
            case SELECTED:
                currentState = PerkPointsState.SMITHING;
                log.info("Changing state to smithing");
                break;
        }
    }

    private void handleSmithing() {
        if (!isPlayerIdle()) {
            log.info("Player is busy");
            return;
        }

        if (getInventoryCount("Adamantite bar") < minBars) {
            currentState = PerkPointsState.RESTOCKING;
            log.info("Changing state to restocking");
            return;
        }

        Widget smithingWidget = client.getWidget(WidgetInfo.SMITHING_INVENTORY_ITEMS_CONTAINER.getId());

        if (smithingWidget == null) {
            interactWithClosestGameObject("Anvil", "Smith", MenuAction.GAME_OBJECT_FIRST_OPTION);
            log.info("Interacting with anvil");
        } else {
            smithItem("Adamant full helm");

            log.info("Smithing helms");
        }
    }

    private void handleRestocking() {
        if (!isPlayerIdle()) {
            log.info("Player is busy");
            return;
        }

        Widget bankItemContainer = client.getWidget(WidgetInfo.BANK_ITEM_CONTAINER.getId());

        if(bankItemContainer == null) {
            interactWithClosestGameObject("Bank", "Use", MenuAction.GAME_OBJECT_FIRST_OPTION);
            log.info("Opening Bank");
            return;
        }

        int helmCount = getInventoryCount("Adamant full helm");

        if(helmCount != 0) {
            depositAllItemToBank("Adamant full helm");
            log.info("Depositing helms");
            return;
        }

        int barCount = getInventoryCount("Adamantite bar");

        if(barCount != 26) {
            withdrawAllItemFromBank("Adamantite bar");
            log.info("Withdrawing bars");
            return;
        }

        currentState = PerkPointsState.SMITHING;

    }


    private boolean smithItem(String itemName) {
        Widget smithingWidget = client.getWidget(WidgetInfo.SMITHING_INVENTORY_ITEMS_CONTAINER.getId());
        if (smithingWidget == null || smithingWidget.getStaticChildren() == null) {
            log.info("Smithing interface is not open.");
            return false;
        }

        for (Widget itemWidget : smithingWidget.getStaticChildren()) {
            if (itemWidget != null && itemWidget.getName().toLowerCase().contains(itemName.toLowerCase())) {


                client.invokeMenuAction(
                        "Smith",
                        itemWidget.getName(),
                        1,
                        MenuAction.CC_OP.getId(),
                        -1,
                        itemWidget.getId()
                );
                log.info("Selected '{}' to smith.", itemName);
                return true;
            }

        }
        log.error("Item '{}' not found in smithing interface.", itemName);
        return false;
    }

    private boolean isPlayerIdle() {
        Player localPlayer = client.getLocalPlayer();
        return localPlayer.getAnimation() == -1 &&
                localPlayer.getInteracting() == null;
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
                if (itemName.equalsIgnoreCase(itemInInventoryName)) {
                    count += item.getQuantity();
                }
            }
        }
        return count;
    }

    void depositAllItemToBank(String itemName) {
        Widget bankInventory = client.getWidget(WidgetInfo.BANK_INVENTORY_ITEMS_CONTAINER.getId());
        if (bankInventory == null || bankInventory.getDynamicChildren() == null) {
            log.error("Bank inventory is not accessible.");
            return;
        }
        Widget[] items = bankInventory.getDynamicChildren();
        for (int i = 0; i < items.length; i++) {

            if (items[i] != null && items[i].getName().toLowerCase().contains(itemName.toLowerCase())) {
                client.invokeMenuAction(
                        "Deposit-All",
                        items[i].getName(),
                        2,
                        MenuAction.CC_OP.getId(),
                        i,
                        bankInventory.getId()
                );
                log.info("Deposited all of '{}'", itemName);
                return;
            }
        }
    }

    void withdrawAllItemFromBank(String itemName) {
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

    private boolean interactWithNpc(String npcName, String actionName, MenuAction menuAction) {
        Optional<NPC> optionalNPC = client.getNpcs().stream()
                .filter(n -> n.getName() != null && n.getName().toLowerCase().contains(npcName.toLowerCase()))
                .findFirst();

        if (optionalNPC.isEmpty()) {
            log.info("Could not find NPC: {}", npcName);
            return false;
        }

        NPC npc = optionalNPC.get();
        client.invokeMenuAction(
                actionName,
                npc.getName(),
                npc.getIndex(),
                menuAction.getId(),
                0,
                0
        );

        log.info("Interacting with NPC '{}' using action '{}'", npc.getName(), actionName);
        return true;
    }

    private boolean selectDialogOption(String option) {
        Widget dialogOptionsWidget = client.getWidget(WidgetInfo.DIALOG_OPTION_OPTIONS.getId());
        if (dialogOptionsWidget != null && dialogOptionsWidget.getDynamicChildren() != null) {
            Widget[] options = dialogOptionsWidget.getDynamicChildren();

            for (int i = 0; i < options.length; i++) {
                if (options[i].getText() != null
                        && options[i].getText().toLowerCase().contains(option.toLowerCase())) {
                    client.invokeMenuAction(
                            "Continue",
                            "",
                            -1,
                            MenuAction.WIDGET_CONTINUE.getId(),
                            i,
                            14352385
                    );
                    log.info("Selected dialog option {}", option);
                    return true;
                }
            }
            log.info("Could not find dialog option {}", option);
        } else {
            log.error("Dialog options widget not found.");
        }
        return false;
    }
}
