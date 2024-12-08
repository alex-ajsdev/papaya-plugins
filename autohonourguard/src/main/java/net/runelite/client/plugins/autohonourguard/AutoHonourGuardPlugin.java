package net.runelite.client.plugins.autohonourguard;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import org.pf4j.Extension;

import javax.inject.Inject;
import java.util.Comparator;

@Extension
@PluginDescriptor(
        name = "Auto Honour Guard",
        enabledByDefault = false,
        description = "Papaya - Auto Honour Guard",
        tags = {"papaya"}
)
@Slf4j
public class AutoHonourGuardPlugin extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private ChatMessageManager chatMessageManager;

    private int guardId = 1891;
    private boolean guardSpawned = false;
    private int tick = 0;



    @Subscribe
    private void onChatMessage(ChatMessage event) {
        if (!guardSpawned && event.getMessage().contains("A security guard has been spawned")) {
            log.info("Guard spawn detected!");
            guardSpawned = true;
        }
        if (guardSpawned && event.getMessage().contains("You have been awarded")) {
            log.info("Guard has been dealt with!");
            guardSpawned = false;
        }
    }


    @Subscribe
    void handleGameTick(GameTick event) {
        tick++;

        if (!guardSpawned) {
            if (tick >= 7) {
                log.info("Still waiting for the guard to spawn...");
                tick = 0;
            }
        } else {

            if (tick >= 20) {
                NPC guard = client.getNpcs().stream()
                        .filter(npc -> npc.getId() == guardId)
                        .min(Comparator.comparingInt(npc -> client.getLocalPlayer().getWorldLocation().distanceTo(npc.getWorldLocation())))
                        .orElse(null);

                if (guard != null) {
                    log.info("Interacting with guard: {}", guard.getName());
                    client.invokeMenuAction(
                            "Talk-to",
                            guard.getName(),
                            guard.getIndex(),
                            MenuAction.NPC_FIRST_OPTION.getId(),
                            guard.getWorldLocation().getX(),
                            guard.getWorldLocation().getY()
                    );
                } else {
                    log.info("No guard found nearby, waiting...");
                }

                tick = 0;
            }
        }
    }

    public void sendGameMessage(String message, Object... args) {

        String formattedMessage = String.format(message, args);


        String chatMessage = new ChatMessageBuilder()
                .append(ChatColorType.HIGHLIGHT)
                .append(formattedMessage)
                .build();


        chatMessageManager
                .queue(QueuedMessage.builder()
                        .type(ChatMessageType.CONSOLE)
                        .runeLiteFormattedMessage(chatMessage)
                        .build());
    }
}
