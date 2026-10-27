# Polychat3 protocol, version 1

Each game server (the client) keeps one WebSocket open to the Polychat3 server
(the hub). This file and the payload classes in `core` change in the same commit.

The protocol is strict: frames are strict JSON, every field is required at every
depth and has exactly its listed type, unknown types and fields are errors, and
both ends must use the same version.

## Transport and framing

- WebSocket (RFC 6455) over TCP: `ws://host:port/`. The port is `server_port` in
  the hub config, 5005 by default. The path is ignored.
- No TLS: the `handshake` token travels in clear text. Only expose the hub on a
  private network the game servers share, never on the public internet.
- One text frame is one UTF-8 JSON object with a string `type`. No batching, no
  binary frames.
- Field names are camelCase, the same as the Java fields. `type` is the class
  name in camelCase: `PlayerJoin` is `playerJoin`.
- `Protocol.MAX_FRAME_BYTES` (65 536) is the largest encoded frame, in both
  directions. Both ends pass it to Java-WebSocket's `Draft_6455`, which closes a
  connection that receives a bigger frame with 1009. The library only checks
  what it receives, so the sender keeps its own frames under the limit: the
  client cuts `commandResult.output` and sets `truncated: true`.
- One connection per game server for the life of its process. The client sends
  from one queue, so `seq` is monotonic on the wire.

## Text

Every text field is plain text: no `§` colour codes, no Discord markup, no
prefixes such as `[A5]`. The sender strips formatting and the receiver adds its
own.

## Server ids

A server id is 1 to 16 characters, each a lowercase letter, a digit, `_` or `-`
(regex `^[a-z0-9_-]{1,16}$`). For example `a5`, `gtnh` or `atm-9`. A display
prefix such as `[A5]` is the id in upper case.

Hub modules that send chat, such as Discord, claim a server id when they load.
The hub loads every module before it accepts connections, and refuses a
`handshake` that uses a claimed id with 4003. Nothing is hardcoded: with the
Discord module turned off, `discord` is a normal id.

## Code layout

Everything below is in `core`, package `club.moddedminecraft.polychat3.core`.

| Class | Purpose |
|---|---|
| `Protocol` | `VERSION`, `MAX_FRAME_BYTES` and the close codes |
| `payloads.PayloadCodec` | Encodes and decodes frames |
| `payloads.Payload` | Base class of every frame |
| `payloads.ProtocolError` | The `protocolError` frame, used in both directions |
| `payloads.PlayerInfo`, `payloads.ServerState` | Shared by both directions |
| `payloads.ProtocolException` | Thrown for an invalid frame |
| `payloads.c2s.*` | Frames the client sends; events extend `c2s.Event` (adds `seq`) |
| `payloads.s2c.*` | Frames the hub sends; relayed frames extend `s2c.Relayed` (adds `server`, `serverName`) |

Every payload class and getter has Javadoc.

Using the codec:

```java
String frame = PayloadCodec.encode(payload);     // any payload
Payload in = PayloadCodec.C2S.decode(frame);      // hub side
Payload in = PayloadCodec.S2C.decode(frame);      // client side
```

`decode` throws `ProtocolException` for a frame that is not a strict JSON object
(no comments, single quotes, unquoted names or trailing data), or has a missing or
unknown `type`. It also throws for a field, at any depth, that is missing,
unknown, `null`, of the wrong JSON type, or an unknown enum value. The receiver
logs a warning and answers with `protocolError`; see below. `encode` throws
`IllegalArgumentException` for a payload with a `null` field, since that frame
would break the protocol.

## Field types

`seq`, `lastSeq` and `session` are 64-bit integers. `version`, `online` and
`maxPlayers` are integers. `ok` and `truncated` are booleans. `uuid` is a dashed
Minecraft UUID. Everything else is a string or an array.

`state` is one of `starting`, `started`, `stopping`, `stopped`, `crashed`,
`offline`, `unresponsive`. A client only sends the first five.

## Messages, client to hub (`c2s`)

| Type | Fields |
|---|---|
| `handshake` | `version`, `server`, `name`, `token`, `session`, `state`, `colour`, `address`, `maxPlayers` |
| `heartbeat` | none |
| `chatMessage` | `seq`, `player`, `senderId`, `message` |
| `playerJoin` | `seq`, `player`, `uuid`, `online` |
| `playerLeave` | `seq`, `player`, `uuid`, `online` |
| `playerList` | `seq`, `players` (array of `{name, uuid}`) |
| `serverStatus` | `seq`, `state` |
| `commandResult` | `seq`, `id`, `ok`, `output`, `truncated` |
| `protocolError` | `code`, `message` |

- `handshake`:
  - `session` is the process start time in ms, set once when the mod loads. A
    new value tells the hub the server restarted; the same value means a
    reconnect.
  - `state` is `starting` or `started`.
  - `colour` is one of the 16 Minecraft colour names.
  - `address` and `maxPlayers` are shown by `/online`.
- `heartbeat`: sent from the game's server tick every 15 s. No `seq`, never
  buffered or replayed.
- `chatMessage`: `senderId` is the player's Minecraft UUID.
- `playerJoin` and `playerLeave`: `online` is the player count afterwards.
- `playerList`: the full list, sent after every `handshakeAccepted` and every
  5 minutes.
- `commandResult`: `id` is the `id` of the `runCommand` it answers.

## Messages, hub to client (`s2c`)

| Type | Fields |
|---|---|
| `handshakeAccepted` | `version`, `lastSeq`, `servers` (array of `ServerEntry`) |
| `acknowledgement` | `seq` |
| `chatMessage` | `server`, `serverName`, `player`, `senderId`, `message` |
| `playerJoin` | `server`, `serverName`, `player`, `uuid`, `online` |
| `playerLeave` | `server`, `serverName`, `player`, `uuid`, `online` |
| `playerList` | `server`, `serverName`, `players` |
| `serverStatus` | `server`, `serverName`, `state`, `colour`, `address`, `maxPlayers`, `players` |
| `runCommand` | `id`, `name`, `default`, `args` |
| `systemMessage` | `message`, `kind` |
| `protocolError` | `code`, `message` |

- `handshakeAccepted`: `lastSeq` is the highest `seq` accepted from this server
  and session, `0` if none. A `ServerEntry` has `id`, `name`, `colour`,
  `address`, `maxPlayers`, `state` and `players`.
- `acknowledgement`: every event up to and including `seq` is accepted.
- `chatMessage`, `playerJoin`, `playerLeave`, `playerList`, `serverStatus`:
  relayed from another server or a module, with `server` and `serverName` naming
  where it came from.
  - `chatMessage.senderId` is a Minecraft UUID for game chat, and the Discord user
    id for Discord chat.
  - `serverStatus` is the origin's `ServerEntry` flattened onto the frame.
- `runCommand`: the client joins `args` with single spaces and substitutes the
  result for `$args` once, in its own override for `name` if it has one, else in
  `default` (`defaultCommand` in Java). It runs that console command and answers
  with `commandResult`.
- `systemMessage`: `kind` is `broadcast`, `restart` or `info`. The hub only sends
  it to the servers it is meant for.

## Errors

`protocolError` has `code` (machine-readable, for example `unsupported_version`)
and `message` (human-readable).

When a frame fails to decode, the receiver logs a warning and sends
`protocolError`; the connection stays open. Three invalid frames in a row close it
with 4005. Before any protocol close, the hub sends `protocolError` so the
client's log says why.

## Handshake

1. The client opens the socket and sends `handshake` at once, then nothing until
   `handshakeAccepted`.
2. A first frame that is not a valid `handshake`, or no `handshake` within 5 s,
   closes the connection with 4000.
3. The hub checks, in this order, and closes on the first failure:
   1. the id is valid and not claimed by a module (4003);
   2. the token matches the hub's `server_token`, which every server shares (4001);
   3. the version equals `Protocol.VERSION` (4002).
4. If a live connection already has that id, the hub closes the old one with 4004.
5. The hub answers `handshakeAccepted`.
6. The client resends its buffered events with `seq` above `lastSeq`, oldest
   first, then sends a fresh `playerList`.

## Versioning

`version` is one integer, `Protocol.VERSION`. Both ends must use exactly the
same version.

It stays `1` until the first release. From then on, any change to the protocol,
including a new type, bumps it by one in the next release.

## Sequence numbers, acknowledgements and replay

- Events are `chatMessage`, `playerJoin`, `playerLeave`, `playerList`,
  `serverStatus` and `commandResult`. Each carries `seq`: 1 for the first event a
  client process sends, plus one for every event after it.
- `seq` belongs to the session. After a restart the client has a new `session`
  and starts again at 1, and the hub starts counting again from 0.
- The hub sends `acknowledgement` after it has processed an event. One
  `acknowledgement` may cover several events.
- The client buffers every `chatMessage`, `playerJoin`, `playerLeave` and
  `serverStatus` until an `acknowledgement` covers it, up to 200 events, dropping
  the oldest when full. `playerList` and `commandResult` are not buffered.
- An event with `seq` at or below the hub's last accepted `seq` for that session
  is a duplicate. The hub acknowledges it again and does not process it.
- Delivery is at-least-once. Hub to client has no `seq`: a disconnected client
  misses what was said meanwhile.

## Shutting down

- On a stop, the client sends `serverStatus` `stopped`; on a crash, `crashed`,
  from a JVM shutdown hook (it runs on a normal exit, Ctrl+C and SIGTERM).
- The client then holds the JVM's shutdown until an `acknowledgement` covers that
  `serverStatus`, for at most 3 s, and closes the connection with 1000 either way.
  If the hub is unreachable, the JVM exits after 3 s and the hub sees a dropped
  connection, marking the server `offline`.
- After that clean close the hub forgets the server's last `seq`, so the restarted
  server and the hub both start again from the beginning.
- A process killed without shutdown hooks (`kill -9`, the OOM killer, power loss)
  sends nothing and looks like a dropped connection. The new `session` in its next
  `handshake` is what tells the hub it restarted.

## Keepalive

- **Socket.** Both ends ping every 30 s. No pong within 45 s closes the
  connection locally with 1006.
- **Game thread.** If the hub sees no `heartbeat` or event for 45 s, it marks the
  server `unresponsive` and relays that `serverStatus`. The socket stays open.
  The next heartbeat or event restores the last state the client reported.

## Close codes

| Code | Constant | Meaning | Client does |
|---|---|---|---|
| 1000 | `CLOSE_NORMAL` | Client shutting down, after `serverStatus` `stopped` or `crashed` | Nothing |
| 1001 | `CLOSE_GOING_AWAY` | Hub shutting down or restarting | Reconnect with backoff |
| 1006 | | No pong in time; local only | Reconnect with backoff |
| 1009 | `CLOSE_TOO_BIG` | Frame larger than `MAX_FRAME_BYTES` | Log at error, reconnect |
| 4000 | `CLOSE_BAD_HANDSHAKE` | First frame not a valid `handshake`, or none in time | Reconnect with backoff |
| 4001 | `CLOSE_BAD_TOKEN` | Wrong token | Log at error, retry every 60 s |
| 4002 | `CLOSE_BAD_VERSION` | Version differs from the hub's | Log at error, retry every 60 s |
| 4003 | `CLOSE_BAD_SERVER_ID` | Invalid server id, or one a module claimed | Log at error, stop |
| 4004 | `CLOSE_REPLACED` | Replaced by a newer connection with the same id | Log at error, stop |
| 4005 | `CLOSE_PROTOCOL_ERROR` | Binary frame, or three invalid frames in a row | Reconnect with backoff |

Backoff is 1, 2, 4, 8, 16, then 30 s, with 20 % jitter.

## Example frames

`>` is client to hub, `<` is hub to client.

```
> {"type":"handshake","version":1,"server":"a5","name":"All the Mods 5","token":"...","session":1793130000000,"state":"started","colour":"yellow","address":"a5.example.net","maxPlayers":40}
< {"type":"handshakeAccepted","version":1,"lastSeq":0,"servers":[{"id":"gtnh","name":"GTNH","colour":"gold","address":"gtnh.example.net","maxPlayers":30,"state":"started","players":[{"name":"Steve","uuid":"069a79f4-..."}]}]}
> {"type":"playerList","players":[{"name":"Alex","uuid":"ec561538-..."}],"seq":1}
< {"type":"acknowledgement","seq":1}
> {"type":"chatMessage","player":"Alex","senderId":"ec561538-...","message":"hello!","seq":2}
< {"type":"acknowledgement","seq":2}
> {"type":"heartbeat"}
> {"type":"serverStatus","state":"stopping","seq":3}
< {"type":"runCommand","id":"c-7","name":"tps","default":"forge tps","args":[]}
> {"type":"commandResult","id":"c-7","ok":true,"output":"Overall: Mean TPS: 20.0","truncated":false,"seq":4}
< {"type":"systemMessage","message":"Server restarting in 60 s","kind":"restart"}
< {"type":"chatMessage","player":"Alex","senderId":"ec561538-...","message":"hello!","server":"a5","serverName":"All the Mods 5"}
< {"type":"chatMessage","player":"Heather","senderId":"123456789012345678","message":"hi from Discord","server":"discord","serverName":"Discord"}
< {"type":"protocolError","code":"unknown_type","message":"unknown type pong"}
```
