package club.moddedminecraft.polychat3.client;

import java.nio.file.Paths;

import club.moddedminecraft.polychat3.client.config.PolychatClientConfig;
import club.moddedminecraft.polychat3.core.config.ConfigLoader;
import computer.heather.goagnostic.api.annotations.Mod;
import computer.heather.goagnostic.api.annotations.Mod.Target;
import computer.heather.goagnostic.api.baseclasses.GoAgnosticMod;
import computer.heather.goagnostic.api.util.chat.ChatManager;
import computer.heather.goagnostic.api.util.chat.ChatMessage;

@Mod(modName = "Polychat3", modid = "polychat3", targets = { Target.NEO_1_21, Target.FORGE_1_12 }, 
    version = "1", group = "club moddedminecraft", authors = { "Heather White", "Edward Pratt" }, 
    description = "The third iteration of Polychat!", license = "BSD-3-Clause")
public class PolychatClient extends GoAgnosticMod{

    public static PolychatClientConfig CONFIG;

    public PolychatClient(String modName, String modId, Logger logger) throws Exception {
        super(modName, modId, logger);

        //Init config

        CONFIG = ConfigLoader.load(Paths.get("config/polychat-client.jsonc"), PolychatClientConfig.class, "/defaults/polychat-client.jsonc");
        CONFIG.validate();

        //Listen to chat
        ChatManager.setChatListener(this::onChat);



    }



    public void onChat(ChatMessage message) {

    }


    
}
