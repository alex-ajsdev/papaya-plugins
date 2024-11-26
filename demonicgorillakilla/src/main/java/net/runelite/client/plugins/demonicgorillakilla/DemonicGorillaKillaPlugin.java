package net.runelite.client.plugins.demonicgorillakilla;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ProjectileSpawned;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import org.pf4j.Extension;

import javax.inject.Inject;
import java.util.*;

@Extension
@PluginDescriptor(
        name = "111 Auto Gorilla Killa",
        enabledByDefault = false,
        description = "Automatically drinks the Superior potion when stats drop below a configurable threshold.",
        tags = {"superior", "potion", "111", "papaya"}
)
@Slf4j
public class DemonicGorillaKillaPlugin extends Plugin {

    @Inject
    private Client client;

    private static final Set<String> LOOT_WHITELIST = Set.of("zenyte shard", "ballista spring", "ballista limbs", "demonic string", "demonic blood", "light frame", "heavy frame", "monkey tail", "coins", "adamantite bar", "runite bar", "crystal key", "dragon javelin heads", "clue reward scroll (elite)", "clue reward scroll (hard)");
    private static final Set<String> LOOT_BLACKLIST = Set.of("bones", "ash", "malicious ashes", "shark");

    private static final List<String> MELEE_GEAR = List.of("Abyssal whip", "Dragon defender", "Fighter torso", "Bandos tassets");
    private static final List<String> RANGED_GEAR = List.of("Dark bow", "Armadyl chestplate", "Armadyl chainskirt");

    private boolean gorillaProtectingMelee = false;
    private boolean gorillaProtectingRanged = false;
    private boolean usingRanged = true; // Start with Ranged by default

    private NPC currentGorilla;
    private boolean usingMagic = true; // Track the current style
    private boolean lootPending = false;

    @Override
    protected void startUp() {
        log.info("Auto Demonic Gorillas plugin started!");
    }

    @Override
    protected void shutDown() {
        log.info("Auto Demonic Gorillas plugin stopped.");
        currentGorilla = null;
        lootPending = false;
    }

    private int styleSwitchCooldown = 0;

    @Subscribe
    public void onGameTick(GameTick event) {
        if (styleSwitchCooldown > 0) {
            styleSwitchCooldown--;
            return;
        }

        if (currentGorilla != null && isPlayerAttacking(currentGorilla)) {
            handleCombat();
        }
    }

    private void switchStyles() {
        if (styleSwitchCooldown > 0) {
            log.info("Style switch on cooldown.");
            return;
        }

        usingRanged = !usingRanged;
        equipGearSet(usingRanged ? "Ranged" : "Melee");

        styleSwitchCooldown = 5; // Cooldown of 5 ticks
    }

    private void handleCombat() {
        if (currentGorilla == null) {
            log.warn("No gorilla is currently being attacked.");
            return;
        }

        // If the gorilla is protecting against the current style, switch styles
        if ((usingRanged && gorillaProtectingRanged) || (!usingRanged && gorillaProtectingMelee)) {
            log.info("Gorilla is protecting against current style. Switching styles...");
            switchStyles();
        }
    }


    private boolean isPlayerAttacking(NPC npc) {
        Actor interacting = client.getLocalPlayer().getInteracting();
        return interacting != null && interacting == npc;
    }

    private boolean isGorillaPrayingAgainst(Prayer prayer) {
        return client.isPrayerActive(prayer);
    }

    private void equipGearSet(String style) {
        List<String> gearToEquip = style.equals("Melee") ? MELEE_GEAR : RANGED_GEAR;

        // Equip each item from the corresponding gear set
        ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
        if (inventory == null) {
            log.warn("Inventory is not accessible.");
            return;
        }

        for (Item item : inventory.getItems()) {
            if (item != null) {
                String itemName = client.getItemDefinition(item.getId()).getName();
                if (gearToEquip.contains(itemName)) {
                    log.info("Equipping {} for {} style.", itemName, style);
                    client.invokeMenuAction(
                            "Wear",
                            itemName,
                            item.getId(),
                            MenuAction.ITEM_FIRST_OPTION.getId(),
                            -1,
                            WidgetInfo.INVENTORY.getId()
                    );
                }
            }
        }
    }

    private void findNewGorilla() {
        currentGorilla = client.getNpcs().stream()
                .filter(npc -> npc.getName() != null && npc.getName().equalsIgnoreCase("Demonic gorilla"))
                .min(Comparator.comparingInt(npc -> client.getLocalPlayer().getWorldLocation().distanceTo(npc.getWorldLocation())))
                .orElse(null);

        if (currentGorilla != null) {
            log.info("Found new Demonic Gorilla to attack: {}", currentGorilla.getName());
            attackGorilla(currentGorilla);
        }
    }

    private void attackGorilla(NPC gorilla) {
        if (gorilla == currentGorilla) {
            log.info("Already attacking this gorilla.");
            return;
        }
        log.info("Attacking {}", gorilla.getName());
        client.invokeMenuAction(
                "Attack",
                gorilla.getName(),
                gorilla.getIndex(),
                MenuAction.NPC_SECOND_OPTION.getId(),
                0,
                0
        );
    }

    private void lootItems() {
        List<TileItem> items = findLoot();
        for (TileItem item : items) {
            int itemId = item.getId();
            String itemName = client.getItemDefinition(itemId).getName();
            if (LOOT_WHITELIST.contains(itemName.toLowerCase())) {
                log.info("Looting {}", itemName);
                client.invokeMenuAction(
                        "Take",
                        itemName,
                        itemId,
                        MenuAction.GROUND_ITEM_THIRD_OPTION.getId(),
                        item.getTile().getSceneLocation().getX(),
                        item.getTile().getSceneLocation().getY()
                );
            }
        }
    }

    private List<TileItem> findLoot() {
        List<TileItem> loot = new ArrayList<>();
        Scene scene = client.getScene();
        Tile[][][] tiles = scene.getTiles();
        Player player = client.getLocalPlayer();

        if (player == null || tiles == null) {
            return loot;
        }

        int plane = client.getPlane();
        for (int x = 0; x < tiles[plane].length; x++) {
            for (int y = 0; y < tiles[plane][x].length; y++) {
                Tile tile = tiles[plane][x][y];
                if (tile == null) {
                    continue;
                }

                for (TileItem item : tile.getGroundItems()) {
                    if (item != null) {
                        loot.add(item);
                    }
                }
            }
        }

        return loot;
    }

    @Subscribe
    public void onHitsplatApplied(HitsplatApplied event) {
        if (event.getActor() instanceof NPC) {
            NPC npc = (NPC) event.getActor();
            if (npc.equals(currentGorilla)) {
                Hitsplat hitsplat = event.getHitsplat();
                if (hitsplat.getAmount() == 0) { // 0 damage indicates a blocked attack
                    if (usingRanged) {
                        log.info("Gorilla blocked ranged attack. Switching to melee.");
                        gorillaProtectingRanged = true;
                        gorillaProtectingMelee = false;
                    } else {
                        log.info("Gorilla blocked melee attack. Switching to ranged.");
                        gorillaProtectingMelee = true;
                        gorillaProtectingRanged = false;
                    }
                    switchStyles();
                }
            }
        }
    }



}

