package net.runelite.client.plugins.debugger;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Projectile;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ProjectileSpawned;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import org.pf4j.Extension;


@Extension
@PluginDescriptor(
        name = "Debugger",
        enabledByDefault = false,
        description = "Papaya - Debugger",
        tags = {"papaya"}
)
@Slf4j
public class DebuggerPlugin extends Plugin {

    @Subscribe
    void onMenuOptionClicked(MenuOptionClicked event) {
        log.info("==== Menu Option Clicked ====");
        log.info("Menu Option: {}", event.getMenuOption());
        log.info("Menu Target: {}", event.getMenuTarget());
        log.info("Id: {} ItemId: {} ", event.getId(), event.getItemId());
        log.info("Menu Action: {}", event.getMenuAction());
        log.info("Param 0: {}", event.getParam0());
        log.info("Param 1: {}", event.getParam1());
        log.info("========================");
    }

    @Subscribe
    void onProjectileSpawned(ProjectileSpawned event) {
        Projectile projectile =  event.getProjectile();
        String interactingName = projectile.getInteracting().getName();
        String className = projectile.getModel().getClass().toString();
        log.info("==== Projectile Spawned ====");
        log.debug("Projectile ID: {}, X: {}, Y: {}, Speed: {}, Height: {}, Interacting Name: {}. Class Name: {}",
                projectile.getId(), projectile.getX(), projectile.getY(),
                projectile.getVelocityX(), projectile.getVelocityY(), interactingName, className);


    }
}
