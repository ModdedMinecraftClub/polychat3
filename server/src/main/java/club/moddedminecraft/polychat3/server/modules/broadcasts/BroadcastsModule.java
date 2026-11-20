package club.moddedminecraft.polychat3.server.modules.broadcasts;

import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import club.moddedminecraft.polychat3.core.payloads.s2c.S2CSystemMessage;
import club.moddedminecraft.polychat3.server.module.Module;
import club.moddedminecraft.polychat3.server.module.ModuleContext;

/** Sends each message in config/broadcasts.jsonc in turn, one every interval, to every game server. */
public class BroadcastsModule implements Module {
    private ModuleContext context;
    private List<String> messages;
    /** Index of the next message to send. Hub thread only. */
    private int next;
    /** The repeating timer, so disable can stop it; null until enable starts it. */
    private ScheduledFuture<?> timer;

    @Override
    public String id() {
        return "broadcasts";
    }

    @Override
    public void enable(ModuleContext context) throws Exception {
        BroadcastsConfig config = context.config(BroadcastsConfig.class);
        config.validate();
        this.context = context;
        this.messages = config.getMessages();
        timer = context.every(config.getIntervalMinutes(), TimeUnit.MINUTES, this::broadcast);
    }

    @Override
    public void disable() {
        if (timer != null) {
            timer.cancel(false);
        }
    }

    /** Sends the next message, then moves on; after the last, back to the first. Runs on the hub thread. */
    private void broadcast() {
        context.systemMessage(messages.get(next), S2CSystemMessage.Kind.BROADCAST);
        next = (next + 1) % messages.size();
    }
}
