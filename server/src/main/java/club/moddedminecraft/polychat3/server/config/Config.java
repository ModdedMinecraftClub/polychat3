package club.moddedminecraft.polychat3.server.config;

public class Config {


    //These match the exact names in the jsonc config
    private String server_token;
    private int server_port;


    public String getToken() {
        return server_token;
    }
    public int getPort() {
        return server_port;
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
    }


}
