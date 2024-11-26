package net.runelite.client.plugins.autowhisperer;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
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
        name = "Auto Whisperer",
        enabledByDefault = false,
        description = "Papaya - Auto Whisperer",
        tags = {"papaya"}
)
@Slf4j
public class AutoWhispererPlugin extends Plugin {

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    private final List<TileItem> loot = new ArrayList<>();
    private final Set<String> lootBlacklist = Set.of("manta ray", "super combat", "super attack", "bone");

    private final Map<Integer, Prayer> PRAYER_MAP = Map.of(
            2445, Prayer.PROTECT_FROM_MAGIC,
            2444, Prayer.PROTECT_FROM_MISSILES
    );

    private static final int HOME_REGION = 12342;
    private boolean pendingLootClear = false;
    private static final int LOOT_CLEAR_DELAY = 10;
    private int timeout = 0;
    private boolean waitingForLoot = false;
    private boolean teleportInProgress = false;


    @Subscribe
    private void onItemSpawned(ItemSpawned event) {
        int currentRegion = getCurrentRegion();
        if (currentRegion != HOME_REGION) {
            if (!isItemBlacklisted(event.getItem())) {
                loot.add(event.getItem());
            }
        }
    }

    @Subscribe
    private void onItemDespawned(ItemDespawned event) {
        int currentRegion = getCurrentRegion();
        if (currentRegion != HOME_REGION) {
            loot.remove(event.getItem());
        }
    }

    private boolean isItemBlacklisted(TileItem item) {
        String itemName = client.getItemDefinition(item.getId()).getName();
        return lootBlacklist.stream().anyMatch((blItem) -> itemName.toLowerCase().contains(blItem.toLowerCase()));
    }

    @Subscribe
    private void onPlayerDespawned(PlayerDespawned event) {
        Player player = client.getLocalPlayer();

        if (event.getPlayer() == player) {
            if (teleportInProgress) {
                log.info("Player despawned due to teleport.");
                teleportInProgress = false;
                return;
            }

            log.info("Player despawned (likely died). Clearing loot list in {} ticks.", LOOT_CLEAR_DELAY);
            pendingLootClear = true;
            timeout = LOOT_CLEAR_DELAY;
        }
    }


    @Subscribe
    private void onProjectileSpawned(ProjectileSpawned event) {
        Projectile projectile = event.getProjectile();
        Prayer prayer = PRAYER_MAP.get(projectile.getId());
        if (prayer != null) {
            if (!client.isPrayerActive(prayer)) {
                activatePrayer(prayer);
            }
        }
    }

    private void activatePrayer(Prayer prayer) {
        Widget prayerWidget = client.getWidget(prayer.getWidgetInfo().getId());
        if (prayerWidget == null) {
            log.error("Could not find prayer widget");
            return;
        }
        log.info("Activating prayer id: {} name: {}", prayerWidget.getId(), prayerWidget.getName());
        client.invokeMenuAction(
                "Activate",
                prayerWidget.getName(),
                1,
                MenuAction.CC_OP.getId(),
                -1,
                prayerWidget.getId()
        );
    }


    @Subscribe
    private void onGameTick(GameTick event) {
        if (timeout > 0) {
            timeout--;
            return;
        }
        if (pendingLootClear) {
            log.info("Clearing loot list after death.");
            loot.clear();
            pendingLootClear = false;
            return;
        }

        int currentRegion = getCurrentRegion();
        if (currentRegion == HOME_REGION) {
            log.info("Player is at home. Casting previous teleport...");
            previousTeleport();
            timeout = 2;
        } else {
            if (isWhispererAlive() && !isPlayerAttacking()) {
                log.info("Whisperer is alive and not attacking. Attacking...");
                attackWhisperer();
                timeout = 2;
            } else if (!isWhispererAlive()) {
                if (loot.isEmpty() && !waitingForLoot) {
                    log.info("Whisperer is dead. Waiting for loot to spawn...");
                    timeout = 4;
                    waitingForLoot = true;
                } else if (!loot.isEmpty()) {
                    log.info("Whisperer is dead. Looting items...");
                    lootItems();
                    timeout = 1;
                    waitingForLoot = false;
                } else if (isLowOnPrayer() && loot.isEmpty()) {
                    log.info("Low on prayer. Casting previous teleport...");
                    homeTeleport();
                    timeout = 10;
                } else {
                    log.info("Whisperer is dead, no loot available. Starting new instance...");
                    previousTeleport();
                    timeout = 2;
                    waitingForLoot = false;
                }
            }
        }
    }

    private int getCurrentRegion() {
        return client.getLocalPlayer().getWorldLocation().getRegionID();
    }

    private boolean isLowOnPrayer() {
        int currentPrayer = client.getBoostedSkillLevel(Skill.PRAYER);
        int maxPrayer = client.getRealSkillLevel(Skill.PRAYER);
        return currentPrayer < maxPrayer * 0.2; // Less than 20% of max prayer
    }

    private boolean isWhispererAlive() {
        return client.getNpcs().stream()
                .anyMatch(npc -> npc.getName() != null && npc.getName().toLowerCase().contains("whisperer"));
    }

    private boolean isPlayerAttacking() {
        Actor interacting = client.getLocalPlayer().getInteracting();
        if (interacting == null) {
            return false;
        }

        if (!(interacting instanceof NPC)) {
            return false;
        }
        NPC npc = (NPC) interacting;

        return npc.getName() != null && npc.getName().toLowerCase().contains("whisperer");
    }

    private void attackWhisperer() {
        Optional<NPC> npcOptional = client.getNpcs().stream()
                .filter(npc -> npc.getName() != null && npc.getName().toLowerCase().contains("whisperer"))
                .findFirst();

        if (npcOptional.isEmpty()) {
            log.info("Could not find whisperer");
            return;
        }
        NPC whisperer = npcOptional.get();
        client.invokeMenuAction(
                "Attack",
                whisperer.getName(),
                whisperer.getIndex(),
                MenuAction.NPC_SECOND_OPTION.getId(),
                0,
                0
        );
    }


    private void lootItems() {
        if (loot.isEmpty()) {
            log.info("No loot available to pick up.");
            return;
        }

        log.info("Loot available:");
        for (TileItem item : loot) {
            String itemName = client.getItemDefinition(item.getId()).getName();
            log.info("- {}", itemName);
        }

        // Pick up the nearest item
        TileItem lootItem = getNearestTileItem(loot);
        if (lootItem != null) {
            client.invokeMenuAction(
                    "Take",
                    client.getItemDefinition(lootItem.getId()).getName(),
                    lootItem.getId(),
                    MenuAction.GROUND_ITEM_THIRD_OPTION.getId(),
                    lootItem.getTile().getSceneLocation().getX(), lootItem.getTile().getSceneLocation().getY()
            );
        }
    }

    private TileItem getNearestTileItem(List<TileItem> tileItems) {
        Player player = client.getLocalPlayer();

        int currentDistance;
        TileItem closestTileItem = tileItems.get(0);
        int closestDistance = closestTileItem.getTile().getWorldLocation().distanceTo(player.getWorldLocation());
        for (TileItem tileItem : tileItems) {
            currentDistance = tileItem.getTile().getWorldLocation().distanceTo(player.getWorldLocation());
            if (currentDistance < closestDistance) {
                closestTileItem = tileItem;
                closestDistance = currentDistance;
            }
        }
        return closestTileItem;
    }

    private void homeTeleport() {
        teleportInProgress = true;
        client.invokeMenuAction(
                "Cast",
                "<col=00ff00>Edgeville Home Teleport</col>",
                1,
                MenuAction.CC_OP.getId(),
                -1,
                14286948
        );
    }

    private void previousTeleport() {
        client.invokeMenuAction(
                "Previous-teleport",
                "<col=00ff00>Edgeville Home Teleport</col>",
                2,
                MenuAction.CC_OP.getId(),
                -1,
                14286948
        );
        clientThread.invokeLater(this::widgetContinue);
    }

    private void widgetContinue() {
        teleportInProgress = true;
        client.invokeMenuAction(
                "Continue",
                "",
                0,
                MenuAction.WIDGET_CONTINUE.getId(),
                1,
                14352385
        );
    }

}


