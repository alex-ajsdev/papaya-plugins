package net.runelite.client.plugins.autozulrah;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import org.pf4j.Extension;

import javax.inject.Inject;
import java.util.*;

@Extension
@PluginDescriptor(
        name = "Auto Zulrah",
        enabledByDefault = false,
        description = "Papaya - Zulrah",
        tags = {"zulrah", "papaya"}
)
@Slf4j
public class AutoZulrahPlugin extends Plugin {

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    private final List<TileItem> loot = new ArrayList<>();
    private final Set<String> lootWhitelist = Set.of("Coins", "Jar of swamp", "Tanzanite mutagen", "Magma mutagen", "Tanzanite fang", "Magic fang", "Serpentine visage", "mahogany logs", "coal", "battlestaff", "antidote++", "manta ray", "crushed nest", "coconut", "zulrah's scales", "magic seed", "grimy snapdragon", "grimy dwarf weed", "grimy torstol", "yew logs", "grimy toadflax");

    private State state = State.IDLE;
    private int timeout = 0;

    private boolean lootSpawned = false;

    // States of the automation process
    private enum State {
        IDLE,
        BOARDING,
        CONFIRM_BOARDING,
        WAITING_FOR_ZULRAH,
        COMBAT,
        LOOTING,
        TELEPORTING
    }


    @Override
    protected void startUp() {
        log.info("Auto Zulrah started!");
    }

    @Override
    protected void shutDown() {
        log.info("Auto Zulrah stopped.");
        state = State.IDLE;
        loot.clear();
    }

    @Subscribe
    private void onGameTick(GameTick event) {
        if (timeout > 0) {
            timeout--;
            return;
        }

        switch (state) {
            case IDLE:
                handleIdle();
                break;
            case BOARDING:
                handleBoarding();
                break;
            case CONFIRM_BOARDING:
                confirmBoarding();
                break;
            case WAITING_FOR_ZULRAH:
                handleWaitingForZulrah();
                break;
            case COMBAT:
                handleCombat();
                break;
            case LOOTING:
                handleLooting();
                break;
            case TELEPORTING:
                handleTeleporting();
                break;
            default:
                log.warn("Unknown state: {}", state);
        }

    }


    private void handleIdle() {
        log.info("State: IDLE - Starting sequence...");
        state = State.BOARDING;
    }

    private void handleBoarding() {
        log.info("State: BOARDING - Looking for Sacrificial Boat...");
        Optional<GameObject> boat = findSacrificialBoat();
        if (boat.isPresent()) {
            log.info("Boarding Sacrificial Boat...");
            interactWithGameObject(boat.get(), "Board");
            timeout = 9; // Wait for the player to reach the boat
            state = State.CONFIRM_BOARDING; // Transition to the confirmation sub-state
        } else {
            log.warn("Sacrificial Boat not found. Retrying...");
            timeout = 5; // Retry after a short delay
        }
    }

    private void confirmBoarding() {
        Widget boardingWidget = client.getWidget(14352385); // Widget for Yes/No dialog
        if (boardingWidget != null && !boardingWidget.isHidden()) {
            log.info("Boarding confirmation widget found. Inspecting children...");
            Widget[] children = boardingWidget.getDynamicChildren();
            for (int i = 0; i < children.length; i++) {
                Widget child = children[i];
                log.info("Child widget ID: {}, Text: {}", child.getId(), child.getText());
                if (child.getText().equalsIgnoreCase("Yes")) { // Find the "Yes" option
                    log.info("Interacting with 'Yes' option at index {}", i);
                    client.invokeMenuAction(
                            "Continue",
                            "",
                            child.getId(),
                            MenuAction.WIDGET_CONTINUE.getId(),
                            i, // Use the index of the "Yes" child widget
                            14352385
                    );
                    timeout = 5; // Allow time to transition to Zulrah's instance
                    state = State.WAITING_FOR_ZULRAH; // Proceed to wait for Zulrah
                    return;
                }
            }
            log.warn("'Yes' option not found in widget children.");
        } else {
            log.warn("Boarding confirmation widget not found. Retrying...");
            timeout = 5;
            state = State.BOARDING; // Retry boarding
        }
    }



    private void handleWaitingForZulrah() {
        log.info("State: WAITING_FOR_ZULRAH - Checking for Zulrah...");
        if (isZulrahAlive()) {
            log.info("Zulrah has spawned. Transitioning to COMBAT...");
            state = State.COMBAT;
        }
    }

    private void handleCombat() {
        log.info("State: COMBAT - Attacking Zulrah...");
        Optional<NPC> zulrah = findZulrah();
        if (zulrah.isPresent()) {
            interactWithNpc(zulrah.get(), "Attack");
            lootSpawned = false; // Reset loot detection for the next cycle
            timeout = 2; // Allow time for combat
            state = State.LOOTING;
        } else {
            log.warn("Zulrah not found. Returning to WAITING_FOR_ZULRAH...");
            state = State.WAITING_FOR_ZULRAH;
        }
    }


    private void handleLooting() {
        log.info("State: LOOTING - Checking for loot...");

        if (!lootSpawned) {
            log.info("No loot detected yet. Waiting...");
            timeout = 1; // Wait and check again
            return;
        }

        TileItem lootItem = getNearestLootItem();
        if (lootItem != null) {
            log.info("Looting {}", client.getItemDefinition(lootItem.getId()).getName());
            client.invokeMenuAction(
                    "Take",
                    client.getItemDefinition(lootItem.getId()).getName(),
                    lootItem.getId(),
                    MenuAction.GROUND_ITEM_THIRD_OPTION.getId(),
                    lootItem.getTile().getSceneLocation().getX(),
                    lootItem.getTile().getSceneLocation().getY()
            );
            timeout = 1; // Wait for the loot action to complete
        } else {
            log.info("No more loot available. Transitioning to TELEPORTING...");
            state = State.TELEPORTING;
        }
    }


    private void handleTeleporting() {
        log.info("State: TELEPORTING - Casting previous teleport...");
        client.invokeMenuAction(
                "Previous-teleport",
                "<col=00ff00>Lumbridge Home Teleport</col>",
                2,
                MenuAction.CC_OP.getId(),
                -1,
                14286948
        );
        clientThread.invokeLater(this::widgetContinue);
        timeout = 2;
        state = State.IDLE;
    }

    private void widgetContinue() {
        client.invokeMenuAction(
                "Continue",
                "",
                0,
                30,
                -1,
                14352385
        );
    }

    private Optional<GameObject> findSacrificialBoat() {
        Scene scene = client.getScene();
        Tile[][][] tiles = scene.getTiles();
        Player player = client.getLocalPlayer();

        if (tiles == null || player == null) {
            return Optional.empty();
        }

        int plane = client.getPlane();
        WorldPoint playerLocation = player.getWorldLocation();
        GameObject nearestBoat = null;
        int closestDistance = Integer.MAX_VALUE;

        for (Tile[] row : tiles[plane]) {
            if (row == null) continue;
            for (Tile tile : row) {
                if (tile == null) continue;

                for (GameObject obj : tile.getGameObjects()) {
                    if (obj != null && obj.getId() == 10068) { // Sacrificial Boat ID
                        int distance = playerLocation.distanceTo(obj.getWorldLocation());
                        if (distance < closestDistance) {
                            closestDistance = distance;
                            nearestBoat = obj;
                        }
                    }
                }
            }
        }

        return Optional.ofNullable(nearestBoat);
    }


    private Optional<NPC> findZulrah() {
        return client.getNpcs().stream()
                .filter(npc -> npc.getName() != null && npc.getName().equalsIgnoreCase("Zulrah"))
                .findFirst();
    }

    private TileItem getNearestLootItem() {
        Player player = client.getLocalPlayer();
        return loot.stream()
                .min(Comparator.comparingInt(item -> item.getTile().getWorldLocation().distanceTo(player.getWorldLocation())))
                .orElse(null);
    }

    @Subscribe
    private void onItemSpawned(ItemSpawned event) {
        TileItem item = event.getItem();
        String itemName = client.getItemDefinition(item.getId()).getName().toLowerCase();

        if (lootWhitelist.contains(itemName)) {
            loot.add(item);
            lootSpawned = true; // Loot has spawned
            log.info("Loot spawned: {}", itemName);
        }
    }


    @Subscribe
    private void onItemDespawned(ItemDespawned event) {
        loot.remove(event.getItem());
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

    private void interactWithNpc(NPC npc, String action) {
        client.invokeMenuAction(
                action,
                npc.getName(),
                npc.getIndex(),
                MenuAction.NPC_SECOND_OPTION.getId(),
                0,
                0
        );
    }

    private boolean isZulrahAlive() {
        return client.getNpcs().stream()
                .anyMatch(npc -> npc.getName() != null && npc.getName().equalsIgnoreCase("Zulrah"));
    }
}
