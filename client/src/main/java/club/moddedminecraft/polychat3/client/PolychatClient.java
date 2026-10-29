package club.moddedminecraft.polychat3.client;

import computer.heather.goagnostic.api.annotations.Mod;
import computer.heather.goagnostic.api.annotations.Mod.Target;
import computer.heather.goagnostic.api.baseclasses.GoAgnosticMod;

@Mod(modName = "Polychat3", modid = "polychat3", targets = { Target.NEO_1_21, Target.FORGE_1_12 }, 
    version = "1", group = "club moddedminecraft", authors = { "Heather White", "Edward Pratt" }, 
    description = "The third iteration of Polychat!", license = "BSD-3-Clause")
public class PolychatClient extends GoAgnosticMod{

    public PolychatClient(String modName, String modId, Logger logger) throws Exception {
        super(modName, modId, logger);
        //TODO Auto-generated constructor stub
    }

    
}
