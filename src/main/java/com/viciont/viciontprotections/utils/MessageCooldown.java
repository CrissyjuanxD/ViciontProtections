package com.viciont.viciontprotections.utils;

import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MessageCooldown {
    private final Map<UUID, Long> lastMessageTimes = new HashMap<>();
    private final long cooldownMillis;

    public MessageCooldown(long cooldownSeconds) {
        this.cooldownMillis = cooldownSeconds * 1000;
    }

    public boolean canSendMessage(Player player) {
        UUID uuid = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        if (!lastMessageTimes.containsKey(uuid)) {
            lastMessageTimes.put(uuid, currentTime);
            return true;
        }

        long lastTime = lastMessageTimes.get(uuid);
        if (currentTime - lastTime >= cooldownMillis) {
            lastMessageTimes.put(uuid, currentTime);
            return true;
        }

        return false;
    }
}