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


}
