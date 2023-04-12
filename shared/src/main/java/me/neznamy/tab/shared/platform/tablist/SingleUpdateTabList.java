package me.neznamy.tab.shared.platform.tablist;

import lombok.NonNull;
import me.neznamy.tab.shared.chat.IChatBaseComponent;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * TabList for platforms that use the APIs, which don't offer changing
 * many entries at once. Overriding bulk methods and forwarding them
 * as single methods.
 */
public abstract class SingleUpdateTabList implements TabList {

    public void removeEntries(@NonNull Collection<UUID> entries) {
        entries.forEach(this::removeEntry);
    }

    public void updateDisplayNames(@NonNull Map<UUID, IChatBaseComponent> entries) {
        entries.forEach(this::updateDisplayName);
    }

    public void updateLatencies(@NonNull Map<UUID, Integer> entries) {
        entries.forEach(this::updateLatency);
    }

    public void updateGameModes(@NonNull Map<UUID, Integer> entries) {
        entries.forEach(this::updateGameMode);
    }

    public void addEntries(@NonNull Collection<Entry> entries) {
        entries.forEach(this::addEntry);
    }
}