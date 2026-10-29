package club.moddedminecraft.polychat3.core.client;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import club.moddedminecraft.polychat3.core.Protocol;
import club.moddedminecraft.polychat3.core.payloads.Payload;
import club.moddedminecraft.polychat3.core.payloads.PayloadCodec;
import club.moddedminecraft.polychat3.core.payloads.PlayerInfo;
import club.moddedminecraft.polychat3.core.payloads.ProtocolError;
import club.moddedminecraft.polychat3.core.payloads.ProtocolException;
import club.moddedminecraft.polychat3.core.payloads.ServerState;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SChatMessage;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SCommandResult;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SEvent;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SHandshake;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SHeartbeat;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SMute;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SPlayerJoin;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SPlayerLeave;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SPlayerList;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SServerStatus;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CAcknowledgement;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CChatMessage;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CHandshakeAccepted;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CMute;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CMuteList;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CPlayerJoin;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CPlayerLeave;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CPlayerList;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CRelayed;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CRunCommand;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CServerStatus;
import club.moddedminecraft.polychat3.core.payloads.s2c.S2CSystemMessage;

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
    // The hub session from the last handshakeAccepted, and the highest relay seq received since. Sent back in the
    // next handshake so the same hub can replay what this server missed
    private long hubSession;
    private long lastRelayed;

    // Events the hub hasn't acknowledged yet, oldest first, as in PROTOCOL.md "Sequence numbers, acknowledgements
    // and replay". The oldest is dropped past MAX_UNACKED
    private static final int MAX_UNACKED = 200;
    private final ArrayDeque<C2SEvent> unacked = new ArrayDeque<>();
    // The seq of the last event built, 0 before the first
    private long seq;
    // Invalid frames from the hub in a row on this connection; the third closes it, as in PROTOCOL.md "Errors"
    private static final int MAX_INVALID_FRAMES = 3;
    private int invalidFrames;

    // Delays between reconnects; reset by every handshakeAccepted
    private final Backoff backoff = new Backoff();
    // PROTOCOL.md "Close codes": a wrong token or version is retried this often, not backed off
    private static final long RETRY_MILLIS = 60_000;

    // PROTOCOL.md "Shutting down": how long stop waits for the hub to acknowledge the last serverStatus
    private static final long STOP_TIMEOUT_SECONDS = 3;
    // How long stop waits for the session thread to issue the close
    private static final long CLOSE_TIMEOUT_SECONDS = 1;
    // Set by stop: the seq to wait for, and the latch the waiting thread is blocked on
    private long stopSeq;
    private CountDownLatch stopAcked;
    // True after stop; no more reconnects
    private boolean stopped;

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
            invalidFrames = 0;
            transport.send(PayloadCodec.encode(new C2SHandshake(Protocol.VERSION, server, name, token, session, state,
                    colour, address, maxPlayers, hubSession, lastRelayed)));
        });
    }

    /**
     * The transport received a text frame from the hub. An invalid one is logged and answered with
     * {@code protocolError}; the third in a row closes the connection with 4005.
     */
    public void onFrame(String frame) {
        thread.execute(() -> {
            Payload payload;
            try {
                payload = PayloadCodec.S2C.decode(frame);
            } catch (ProtocolException e) {
                if (++invalidFrames >= MAX_INVALID_FRAMES) {
                    listener.error(
                            MAX_INVALID_FRAMES + " invalid frames in a row from hub, closing: " + e.getMessage());
                    transport.send(PayloadCodec.encode(new ProtocolError("protocol_error",
                            MAX_INVALID_FRAMES + " invalid frames in a row, last: " + e.getMessage())));
                    transport.close(Protocol.CLOSE_PROTOCOL_ERROR);
                } else {
                    listener.warn("invalid frame from hub: " + e.getMessage());
                    transport.send(PayloadCodec.encode(new ProtocolError("invalid_frame", e.getMessage())));
                }
                return;
            }
            invalidFrames = 0;
            if (payload instanceof S2CHandshakeAccepted) {
                S2CHandshakeAccepted handshakeAccepted = (S2CHandshakeAccepted) payload;
                accepted = true;
                backoff.reset();
                if (handshakeAccepted.getSession() != hubSession) {
                    // A restarted hub numbers its relayed frames from 1 again
                    lastRelayed = 0;
                }
                hubSession = handshakeAccepted.getSession();
                // Resend what the hub missed, oldest first, then the players, as in PROTOCOL.md "Handshake"
                acknowledged(handshakeAccepted.getLastSeq());
                for (C2SEvent event : unacked) {
                    transport.send(PayloadCodec.encode(event));
                }
                send(new C2SPlayerList(++seq, players.get()));
                listener.servers(handshakeAccepted.getServers());
            } else if (payload instanceof S2CAcknowledgement) {
                acknowledged(((S2CAcknowledgement) payload).getSeq());
            } else if (payload instanceof S2CRelayed) {
                relayed((S2CRelayed) payload);
            } else if (payload instanceof S2CRunCommand) {
                listener.runCommand((S2CRunCommand) payload);
            } else if (payload instanceof S2CSystemMessage) {
                listener.systemMessage((S2CSystemMessage) payload);
            } else if (payload instanceof S2CMuteList) {
                listener.muteList((S2CMuteList) payload);
            } else if (payload instanceof S2CMute) {
                listener.mute((S2CMute) payload);
            } else if (payload instanceof ProtocolError) {
                ProtocolError error = (ProtocolError) payload;
                listener.warn("hub sent " + error.getCode() + ": " + error.getMessage());
            }
        });
    }

    /**
     * The transport's socket closed, or failed to open. Reconnects or not as PROTOCOL.md "Close codes" says: nothing
     * after 1000, never after 4003 or 4004, every 60 s after 4001 or 4002, otherwise with {@link Backoff}.
     *
     * @param code the close code, or -1 if the connection never opened.
     */
    public void onClose(int code, String reason) {
        thread.execute(() -> {
            accepted = false;
            switch (code) {
                case Protocol.CLOSE_NORMAL:
                    // This server closed it after stopped or crashed; nothing to do
                    break;
                case Protocol.CLOSE_BAD_SERVER_ID:
                case Protocol.CLOSE_REPLACED:
                    listener.error("closed with " + code + ", not reconnecting: " + reason);
                    break;
                case Protocol.CLOSE_BAD_TOKEN:
                case Protocol.CLOSE_BAD_VERSION:
                    listener.error("closed with " + code + ", retrying in 60 s: " + reason);
                    reconnectIn(RETRY_MILLIS);
                    break;
                case Protocol.CLOSE_TOO_BIG:
                    listener.error("closed with " + code + ", frame too big: " + reason);
                    reconnectIn(backoff.nextMillis());
                    break;
                default:
                    // 1001, 1006, 4000, 4005, and -1 when a connect fails: try again
                    long delay = backoff.nextMillis();
                    listener.warn("closed with " + code + ", reconnecting in " + delay + " ms");
                    reconnectIn(delay);
                    break;
            }
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
     * Tells the hub the game thread is running. The mod calls it every 15 s from a game-thread timer, as in PROTOCOL.md
     * "Keepalive". Sent only while connected; never numbered or buffered.
     */
    public void heartbeat() {
        thread.execute(() -> {
            if (accepted) {
                transport.send(PayloadCodec.encode(new C2SHeartbeat()));
            }
        });
    }

    /**
     * Shuts down as in PROTOCOL.md "Shutting down": sends {@code serverStatus}, blocks until the hub acknowledges it
     * or 3 s pass, then closes with 1000 and never reconnects. For the mod's shutdown hook. Never call it on the
     * session's thread, such as from a listener callback: it would wait on itself and always time out.
     *
     * @param state {@code stopped} or {@code crashed}.
     */
    public void stop(ServerState state) {
        CountDownLatch acked = new CountDownLatch(1);
        thread.execute(() -> {
            this.state = state;
            send(new C2SServerStatus(++seq, state));
            stopSeq = seq;
            stopAcked = acked;
        });
        try {
            acked.await(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // Keep the interrupt for the caller; close anyway
            Thread.currentThread().interrupt();
        }
        Future<?> closed = thread.submit(() -> {
            stopped = true;
            transport.close(Protocol.CLOSE_NORMAL);
        });
        try {
            // Return only once close was issued, or the JVM can exit before the daemon session thread runs it
            closed.get(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            // close threw, or the session thread is stuck; stop has nothing more it can do
        }
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
        while (!(unacked.isEmpty()) && unacked.peekFirst().getSeq() <= upTo) {
            unacked.removeFirst();
        }
        // stop is waiting for this ack
        if (stopAcked != null && upTo >= stopSeq) {
            stopAcked.countDown();
        }
    }

    /** Opens a new connection after the delay, on the session's thread, unless the session stopped by then. */
    private void reconnectIn(long millis) {
        thread.schedule(() -> {
            if (!stopped) {
                transport.connect();
            }
        }, millis, TimeUnit.MILLISECONDS);
    }

    /** Remembers the frame's relay seq for the next handshake, then hands it to the listener. */
    private void relayed(S2CRelayed frame) {
        lastRelayed = frame.getSeq();
        if (frame instanceof S2CChatMessage) {
            listener.chatMessage((S2CChatMessage) frame);
        } else if (frame instanceof S2CPlayerJoin) {
            listener.playerJoin((S2CPlayerJoin) frame);
        } else if (frame instanceof S2CPlayerLeave) {
            listener.playerLeave((S2CPlayerLeave) frame);
        } else if (frame instanceof S2CPlayerList) {
            listener.playerList((S2CPlayerList) frame);
        } else if (frame instanceof S2CServerStatus) {
            listener.serverStatus((S2CServerStatus) frame);
        }
    }

}