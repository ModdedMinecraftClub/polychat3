package club.moddedminecraft.polychat3.client.config;

import club.moddedminecraft.polychat3.core.Protocol;

import computer.heather.goagnostic.api.util.chat.ChatSymbols.Colour;

public class PolychatClientConfig {

    private String name;
    private String address;
    private String serverId;
    private int maxPlayers;

    private Colour colour;

    private String serverAddress;
    private int serverPort;
    private String token;



    public String getName() {
        return name;
    }
    public String getAddress() {
        return address;
    }
    public String getServerId() {
        return serverId;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }
    public Colour getColour() {
        return colour;
    }
    public String getServerAddress() {
        return serverAddress;
    }
    public int getServerPort() {
        return serverPort;
    }
    public String getToken() {
        return token;
    }


    public void validate() {
        //Validate everything
        if (getName() == null || getName().isEmpty()) throw new IllegalArgumentException("Server name must be set!");
        if (getAddress() == null || getAddress().isEmpty()) throw new IllegalArgumentException("Server address must be set!");
        if (getServerId() == null || !Protocol.SERVER_ID.matcher(getServerId()).matches())
            throw new IllegalArgumentException(
                    "Server ID must be 1 to 16 lowercase letters, digits, _ or -, is " + getServerId());
        if (getMaxPlayers() < 1)
            throw new IllegalArgumentException("Max players must be at least 1!");
        if (getColour() == null) throw new IllegalArgumentException("Colour must be set!");
        
        if (getServerAddress() == null || getServerAddress().isEmpty()) throw new IllegalArgumentException("Server/hub address must be set!");
        if (getServerPort() < 1 || getServerPort() > 65535) throw new IllegalArgumentException("Server port must be between 1 and 65535, is " + getServerPort());
        if (getToken() == null || getToken().isEmpty()) throw new IllegalArgumentException("Token must be set!");
    }
    
}
