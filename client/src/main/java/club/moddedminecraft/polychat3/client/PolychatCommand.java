package club.moddedminecraft.polychat3.client;

import computer.heather.goagnostic.api.annotations.Command;
import computer.heather.goagnostic.api.annotations.Command.Argument;
import computer.heather.goagnostic.api.annotations.Command.Executes;
import computer.heather.goagnostic.api.annotations.Command.Require;
import computer.heather.goagnostic.api.command.CommandContext;
import computer.heather.goagnostic.api.command.CommandSource;
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
            if (!(ctx.source instanceof Player)) {
                ctx.source.sendMessage("Only players can use this command.");
                return;
            }
            Player player = (Player) ctx.source;
            PolychatClient mod = PolychatClient.instance();
            boolean silenced = mod.audience().toggle(player.getUUID());
            if (silenced) {
                ctx.source.sendMessage("Polychat messages silenced. Use /polychat silence again to show them again.");
            } else {
                ctx.source.sendMessage("Polychat messages shown again.");
            }
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
            PolychatClient mod = PolychatClient.instance();
            if (mod.mutes().isMuted(player.getUUID())) {
                ctx.source.sendMessage("Player " + player.getName() + " is already muted.");
                return;
            }
            mod.session().mute(player.getUUID(), true, by(ctx.source));
            ctx.source.sendMessage("Player " + player.getName() + " has been muted.");
        }

    }

    @Command(name = "unmute")
    public static class Unmute {

        @Require
        public static boolean checkPermission(CommandContext ctx) {
            //2 is default op level
            return ctx.source.permissionLevel() >= 2;
        }

        //Unmute command takes one arg (unless we have temp mutes added?)
        @Argument(name = "player", type = ArgumentType.PLAYER)
        @Executes
        public static void execute(CommandContext ctx) {
            Player player = ctx.getArgument("player");
            PolychatClient mod = PolychatClient.instance();
            if (!mod.mutes().isMuted(player.getUUID())) {
                ctx.source.sendMessage("Player " + player.getName() + " is not muted.");
                return;
            }
            mod.session().mute(player.getUUID(), false, by(ctx.source));
            ctx.source.sendMessage("Player " + player.getName() + " has been unmuted.");
        }

    }

    /** Who ran a command, as the protocol's by: the staff member's UUID, or "console" for the console or RCON. */
    private static String by(CommandSource source) {
        if (source instanceof Player) {
            return ((Player) source).getUUID();
        } else {
            return "console";
        }
    }

}
