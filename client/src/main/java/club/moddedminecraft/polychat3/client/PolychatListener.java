package club.moddedminecraft.polychat3.client;

import club.moddedminecraft.polychat3.core.client.MessageListener;

import computer.heather.goagnostic.api.baseclasses.GoAgnosticMod;

/**
 * What the hub sends, handed on to the game, and the session's warnings and errors, sent to the mod's log. Called on
 * the session's thread, so anything that touches the game must be passed to the game thread.
 */
final class PolychatListener implements MessageListener {
    private final GoAgnosticMod.Logger logger;

    public PolychatListener(GoAgnosticMod.Logger logger) {
        this.logger = logger;
    }

    @Override
    public void warn(String message) {
        logger.warn(message);
    }

    @Override
    public void error(String message) {
        logger.error(message);
    }
}
