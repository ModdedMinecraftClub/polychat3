package club.moddedminecraft.polychat3.core.payloads;

import com.google.gson.annotations.SerializedName;

/**
 * A server's lifecycle state.
 */
public enum ServerState {
    @SerializedName("starting") STARTING,
    @SerializedName("started") STARTED,
    @SerializedName("stopping") STOPPING,
    @SerializedName("stopped") STOPPED,
    @SerializedName("crashed") CRASHED,
    /** Set by the hub when the server's connection closes; a client never sends it. */
    @SerializedName("offline") OFFLINE,
    /** Set by the hub when heartbeats stop; a client never sends it. */
    @SerializedName("unresponsive") UNRESPONSIVE
}
