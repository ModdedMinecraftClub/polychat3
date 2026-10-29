package club.moddedminecraft.polychat3.core.client;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import club.moddedminecraft.polychat3.core.Protocol;
import club.moddedminecraft.polychat3.core.payloads.Payload;
import club.moddedminecraft.polychat3.core.payloads.PayloadCodec;
import club.moddedminecraft.polychat3.core.payloads.ProtocolException;
import club.moddedminecraft.polychat3.core.payloads.ServerState;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SHandshake;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CHandshakeAccepted;

/**
 * The client side of the protocol, between the mod's {@link Transport} and its {@link MessageListener}, as in
 * PROTOCOL.md. Every public method only posts to the session's own thread, so its state needs no locks; listener
 * callbacks run on that thread too.
 */
public final class ClientSession {
    // From the mod's config; the same for every connection
    private final String server;
    private final String name;
    private final String token;
    private final String colour; // lowercase Minecraft name, e.g. "yellow"
    private final String address;
    private final int maxPlayers;

    private final Transport transport;
    private final MessageListener listener;
    private final ScheduledExecutorService thread;

    // This process's session: its start time in ms, like the hub's
    private final long session = System.currentTimeMillis();
    // The last state sent; goes in every handshake
    private ServerState state = ServerState.STARTING;
    // True once this connection's handshakeAccepted arrived
    private boolean accepted;
    // From the last handshakeAccepted; sent back on reconnect (replay comes in step 4)
    private long hubSession;
    private long lastRelayed;

    /**
     * @param server this game server's id.
     * @param colour one of the 16 Minecraft colour names, lowercase.
     * @param transport the mod's socket; it calls {@link #onOpen}, {@link #onFrame} and {@link #onClose}.
     * @param listener gets the hub's frames, on the session's thread.
     */
    public ClientSession(String server, String name, String token, String colour, String address, int maxPlayers,
            Transport transport, MessageListener listener) {
        this(server, name, token, colour, address, maxPlayers, transport, listener,
                Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread sessionThread = new Thread(runnable, "polychat-session");
                    // The game's threads keep the process alive; this one shouldn't block exit
                    sessionThread.setDaemon(true);
                    return sessionThread;
                }));
    }

    /** For tests: they pass their own executor and wait on it. */
    ClientSession(String server, String name, String token, String colour, String address, int maxPlayers,
            Transport transport, MessageListener listener, ScheduledExecutorService thread) {
        this.server = server;
        this.name = name;
        this.token = token;
        this.colour = colour;
        this.address = address;
        this.maxPlayers = maxPlayers;
        this.transport = transport;
        this.listener = listener;
        this.thread = thread;
    }

    /** Opens the first connection. */
    public void start() {
        thread.execute(transport::connect);
    }

    /** The transport's socket opened. Sends {@code handshake}. */
    public void onOpen() {
        thread.execute(() -> {
            accepted = false;
            transport.send(PayloadCodec.encode(new C2SHandshake(Protocol.VERSION, server, name, token, session, state,
                    colour, address, maxPlayers, hubSession, lastRelayed)));
        });
    }

    /** The transport received a text frame from the hub. */
    public void onFrame(String frame) {
        thread.execute(() -> {
            Payload payload;
            try {
                payload = PayloadCodec.S2C.decode(frame);
            } catch (ProtocolException e) {
                // Invalid frames are counted and answered in step 4
                return;
            }
            if (payload instanceof S2CHandshakeAccepted) {
                S2CHandshakeAccepted handshakeAccepted = (S2CHandshakeAccepted) payload;
                accepted = true;
                hubSession = handshakeAccepted.getSession();
                listener.servers(handshakeAccepted.getServers());
            }
        });
    }

    /** The transport's socket closed, or failed to open. */
    public void onClose(int code, String reason) {
        thread.execute(() -> {
            accepted = false;
            // Reconnect policy comes in step 5
        });
    }
}