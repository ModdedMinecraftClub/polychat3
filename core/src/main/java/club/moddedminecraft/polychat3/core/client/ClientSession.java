package club.moddedminecraft.polychat3.core.client;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Supplier;

import club.moddedminecraft.polychat3.core.Protocol;
import club.moddedminecraft.polychat3.core.payloads.Payload;
import club.moddedminecraft.polychat3.core.payloads.PayloadCodec;
import club.moddedminecraft.polychat3.core.payloads.PlayerInfo;
import club.moddedminecraft.polychat3.core.payloads.ProtocolException;
import club.moddedminecraft.polychat3.core.payloads.ServerState;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SChatMessage;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SCommandResult;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SEvent;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SHandshake;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SMute;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SPlayerJoin;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SPlayerLeave;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SPlayerList;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SServerStatus;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CAcknowledgement;
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
    // Asked for this server's players after every handshakeAccepted, on the session's thread
    private final Supplier<List<PlayerInfo>> players;
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

    // Events the hub hasn't acknowledged yet, oldest first, as in PROTOCOL.md "Sequence numbers, acknowledgements
    // and replay". The oldest is dropped past MAX_UNACKED
    private static final int MAX_UNACKED = 200;
    private final ArrayDeque<C2SEvent> unacked = new ArrayDeque<>();
    // The seq of the last event built, 0 before the first
    private long seq;

    /**
     * @param server this game server's id.
     * @param colour one of the 16 Minecraft colour names, lowercase.
     * @param transport the mod's socket; it calls {@link #onOpen}, {@link #onFrame} and {@link #onClose}.
     * @param listener gets the hub's frames, on the session's thread.
     * @param players this server's players, asked for after every {@code handshakeAccepted}. Called on the session's
     * thread, so it must be safe to call from there.
     */
    public ClientSession(String server, String name, String token, String colour, String address, int maxPlayers,
            Transport transport, MessageListener listener, Supplier<List<PlayerInfo>> players) {
                this(server, name, token, colour, address, maxPlayers, transport, listener, players,
                Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread sessionThread = new Thread(runnable, "polychat-session");
                    // The game's threads keep the process alive; this one shouldn't block exit
                    sessionThread.setDaemon(true);
                    return sessionThread;
                }));
    }

    /** For tests: they pass their own executor and wait on it. */
    ClientSession(String server, String name, String token, String colour, String address, int maxPlayers,
            Transport transport, MessageListener listener, Supplier<List<PlayerInfo>> players,
            ScheduledExecutorService thread) {
        this.server = server;
        this.name = name;
        this.token = token;
        this.colour = colour;
        this.address = address;
        this.maxPlayers = maxPlayers;
        this.transport = transport;
        this.listener = listener;
        this.players = players;
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
                // Resend what the hub missed, oldest first, then the players, as in PROTOCOL.md "Handshake"
                acknowledged(handshakeAccepted.getLastSeq());
                for (C2SEvent event : unacked){
                    transport.send(PayloadCodec.encode(event));
                }
                send(new C2SPlayerList(++seq, players.get()));
                listener.servers(handshakeAccepted.getServers());
            } else if (payload instanceof S2CAcknowledgement) {
                acknowledged(((S2CAcknowledgement) payload).getSeq());
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

    /** A player on this server said something in chat. */
    public void chatMessage(String player, String senderId, String message) {
        thread.execute(() -> send(new C2SChatMessage(++seq, player, senderId, message)));
    }

    /** A player joined this server; online counts them. */
    public void playerJoin(String player, String uuid, int online) {
        thread.execute(() -> send(new C2SPlayerJoin(++seq, player, uuid, online)));
    }

    /** A player left this server; online no longer counts them. */
    public void playerLeave(String player, String uuid, int online) {
        thread.execute(() -> send(new C2SPlayerLeave(++seq, player, uuid, online)));
    }

    /** The answer to a {@code runCommand}. Never buffered, as PROTOCOL.md says. */
    public void commandResult(String id, boolean ok, String output, boolean truncated) {
        thread.execute(() -> send(new C2SCommandResult(++seq, id, ok, output, truncated)));
    }

    /** Staff on this server muted or unmuted a player. */
    public void mute(String target, boolean muted, String by) {
        thread.execute(() -> send(new C2SMute(++seq, target, muted, by)));
    }

    /** This server's state changed. Also the state the next {@code handshake} carries. */
    public void serverStatus(ServerState state) {
        thread.execute(() -> {
            this.state = state;
            send(new C2SServerStatus(++seq, state));
        });
    }

    /**
     * Buffers the event unless the spec says not to ({@code playerList}, {@code commandResult}), and sends it if this
     * connection was accepted. Otherwise it waits in the buffer for the resend after the next
     * {@code handshakeAccepted}.
     */
    private void send(C2SEvent event) {
        if (!(event instanceof C2SPlayerList || event instanceof C2SCommandResult)) {
            unacked.addLast(event);
            if (unacked.size() > MAX_UNACKED) {
                unacked.removeFirst();
            }
        }
        if (accepted) {
            transport.send(PayloadCodec.encode(event));
        }
    }

    /** Drops every buffered event with seq at or below upTo: the hub has it. */
    private void acknowledged(long upTo){
        while(!(unacked.isEmpty()) && unacked.peekFirst().getSeq() <= upTo){
            unacked.removeFirst();
        }
    }

}