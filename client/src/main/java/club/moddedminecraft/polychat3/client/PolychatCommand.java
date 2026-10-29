package club.moddedminecraft.polychat3.client;

import computer.heather.goagnostic.api.annotations.Command;
import computer.heather.goagnostic.api.annotations.Command.Argument;
import computer.heather.goagnostic.api.annotations.Command.Executes;
import computer.heather.goagnostic.api.annotations.Command.Require;
import computer.heather.goagnostic.api.command.CommandContext;
import computer.heather.goagnostic.api.command.args.ArgumentType;
import computer.heather.goagnostic.api.util.player.Player;

@Command(name = "polychat") 
public class PolychatCommand {

    //Command players use to silence Polychat messages
    @Command(name = "silence")
    public static class Silence {

        @Require
        public static boolean checkPermission(CommandContext ctx) {
            //Only players can use this
            return ctx.source.isPlayer();
        }

        @Executes
        //No args for this one, it's just a toggle
        public static void execute(CommandContext ctx) {
            ctx.source.sendMessage("Boo!");
        }

    }


    @Command(name = "mute")
    public static class Mute {

        @Require
        public static boolean checkPermission(CommandContext ctx) {
            //2 is default op level
            return ctx.source.permissionLevel() >= 2;
        }

        //Mute command takes one arg (unless we have temp mutes added?)
        @Argument(name = "player", type = ArgumentType.PLAYER)
        @Executes
        public static void execute(CommandContext ctx) {
            
            //Here's how we'd retrieve that!
            Player player = ctx.getArgument("player");

        }

    }

}
