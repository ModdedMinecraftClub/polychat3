package club.moddedminecraft.polychat3.server;

import java.io.IOException;
import java.nio.file.Paths;

import club.moddedminecraft.polychat3.core.config.ConfigLoader;
import club.moddedminecraft.polychat3.server.config.Config;

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
    }

    public static Config loadConfig() throws IOException {
        return ConfigLoader.load(Paths.get("config/polychat-server.jsonc"), Config.class, "/defaults/polychat-server.jsonc");
    }
}
