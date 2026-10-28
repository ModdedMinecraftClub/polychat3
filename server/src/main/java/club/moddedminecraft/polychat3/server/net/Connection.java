package club.moddedminecraft.polychat3.server.net;

import org.java_websocket.WebSocket;
import org.java_websocket.exceptions.WebsocketNotConnectedException;

import club.moddedminecraft.polychat3.core.payloads.Payload;
import club.moddedminecraft.polychat3.core.payloads.PayloadCodec;
import club.moddedminecraft.polychat3.core.payloads.c2s.C2SHandshake;

/** One client's socket and what the hub knows about it. */
public final class Connection {

    /** Where a connection is in its life. Only the hub thread changes it. */
    public enum State {
        /** Open, waiting for the first frame. */
        AWAITING_HANDSHAKE,
        /** Handshake accepted. */
        ACTIVE,
        /** Refused or timed out; frames that still arrive are ignored. */
        CLOSING
    }

    private final WebSocket websocket;
    private State state = State.AWAITING_HANDSHAKE;
    /** Null until the handshake is accepted. Volatile because socket threads read it for logs. */
    private volatile C2SHandshake handshake;
    /** Invalid frames in a row; reset by a valid one. */
    private int invalidFrames;

    public Connection(WebSocket websocket) {
        this.websocket = websocket;
    }

    /** Encodes and sends a frame. Does nothing if the socket has closed. */
    public void send(Payload payload) {
        try {
            websocket.send(PayloadCodec.encode(payload));
        } catch (WebsocketNotConnectedException e) {
            // Closed since caller checked; close callback will handle cleanup
        }
    }

    /** Closes the socket with a WebSocket close code, such as {@code Protocol.CLOSE_REPLACED}. */
    public void close(int code) {
        websocket.close(code);
    }

    /** @return true until the socket starts closing. */
    public boolean isOpen() {
        return websocket.isOpen();
    }

    /** @return the server id once the handshake is accepted, otherwise the remote address. For logs. */
    public String describe() {
        C2SHandshake accepted = this.handshake;
        if (accepted != null) {
            return accepted.getServer();
        }
        return remoteAddress();
    }

    /** @return the client's address, for logs. */
    public String remoteAddress() {
        return String.valueOf(websocket.getRemoteSocketAddress());
    }

    /** @return where this connection is in its life. Hub thread only. */
    public State getState() {
        return state;
    }

    /** Moves this connection to {@code state}. Hub thread only. */
    public void setState(State state) {
        this.state = state;
    }

    /** @return the accepted handshake, or null before it is accepted. */
    public C2SHandshake getHandshake() {
        return handshake;
    }

    /** Stores the handshake once the hub accepts it. */
    public void setHandshake(C2SHandshake handshake) {
        this.handshake = handshake;
    }

    /** Counts an invalid frame in a row and returns how many there have been. */
    public int invalidFrame() {
        return ++invalidFrames;
    }

    /** A valid frame arrived, so the run of invalid ones is over. */
    public void validFrame() {
        invalidFrames = 0;
    }
}
