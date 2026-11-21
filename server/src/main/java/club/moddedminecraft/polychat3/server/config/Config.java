package club.moddedminecraft.polychat3.server.config;

import java.util.Collections;
import java.util.List;

public class Config {


    //These match the exact names in the jsonc config
    private String server_token;
    private int server_port;
    private List<String> modules;
    private int command_timeout_s = 10;


    public String getToken() {
        return server_token;
    }
    public int getPort() {
        return server_port;
    }

    /** The modules to enable, in order; none if the key is missing, as in a config file from before modules. */
    public List<String> getModules() {
        if (modules == null) {
            return Collections.emptyList();
        }
        return modules;
    }

    /** How many seconds a game server has to answer a runCommand before the hub fails it. */
    public int getCommandTimeoutSeconds() {
        return command_timeout_s;
    }

    /**
     * @throws IllegalArgumentException naming the first setting that is missing or invalid
     */
    public void validate() {
        if (server_token == null || server_token.trim().isEmpty()) {
            throw new IllegalArgumentException("server_token must be set");
        }
        if (server_port < 1 || server_port > 65535) {
            throw new IllegalArgumentException("server_port must be 1 to 65535, is " + server_port);
        }
        if (command_timeout_s < 1) {
            throw new IllegalArgumentException("command_timeout_s must be at least 1, is " + command_timeout_s);
        }
    }


}
