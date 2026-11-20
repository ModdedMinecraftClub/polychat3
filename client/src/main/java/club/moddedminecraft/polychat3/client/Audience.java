package club.moddedminecraft.polychat3.client;

import java.util.HashSet;
import java.util.Set;

import computer.heather.goagnostic.api.util.player.Player;

/**
 * The players who see Polychat lines: everyone on this server except those who ran /polychat silence. In memory
 * only, so a restart shows Polychat to everyone again. Game thread only.
 */
final class Audience {
    /** UUIDs of the players who silenced Polychat. */
    private final Set<String> silenced = new HashSet<>();

    /** Silences Polychat for this player, or shows it again if already silenced. Returns true if now silenced. */
    boolean toggle(String uuid) {
        if (silenced.contains(uuid)) {
            silenced.remove(uuid);
            return false;
        } else {
            silenced.add(uuid);
            return true;
        }
    }

    /** Sends a line to every online player who hasn't silenced Polychat. */
    void show(String line) {
        for (Player player : PolychatClient.instance().getServer().getPlayers()) {
            if (!silenced.contains(player.getUUID())) {
                player.sendMessage(line);
            }
        }
    }
}
