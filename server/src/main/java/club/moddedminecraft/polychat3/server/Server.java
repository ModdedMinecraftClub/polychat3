package club.moddedminecraft.polychat3.server;

import java.io.IOException;
import java.nio.file.Paths;

import club.moddedminecraft.polychat3.core.config.ConfigLoader;
import club.moddedminecraft.polychat3.server.config.Config;
import club.moddedminecraft.polychat3.server.hub.Hub;
import club.moddedminecraft.polychat3.server.net.WebSocketEndpoint;

public class Server {

    private static Config config;

    public static void main(String[] args) {
        try {
            config = loadConfig();
            config.validate();
        } catch (IOException | IllegalArgumentException e) {
            //can't run without config
            System.err.println("Can't load config: " + e.getMessage());
            System.exit(1);
        }

        Hub hub;
        try {
            hub = new Hub(config.getToken(), Paths.get("config/polychat-mutes.json"));
        } catch (IOException e) {
            // A broken mute list would be wiped by the next save, so don't start without it
            System.err.println("Can't load mutes: " + e.getMessage());
            System.exit(1);
            return;
        }

        WebSocketEndpoint endpoint = new WebSocketEndpoint(config.getPort(), hub);
        // Closes every connection with 1001, so game servers know to reconnect
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                endpoint.stop(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
        endpoint.start();
    }

    public static Config loadConfig() throws IOException {
        return ConfigLoader.load(Paths.get("config/polychat-server.jsonc"), Config.class, "/defaults/polychat-server.jsonc");
    }
}
