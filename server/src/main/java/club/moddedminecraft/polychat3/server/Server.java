package club.moddedminecraft.polychat3.server;

import club.moddedminecraft.polychat3.core.config.ConfigLoader;

public class Server {
    public String getGreeting() {
        return "Hello World!";
    }

    public static void main(String[] args) {
        System.out.println(new Server().getGreeting());
    }
}
