package entities;

import exceptions.*;
import entities.games.Game;
import entities.games.GameFactory;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;

/**
 * Core entity which keeps track of all the games which are running
 * Every use case has access to an instance of this shared gamestate
 */
public class LobbyManager {

    /**
     * Pairs Player objects with the corresponding Listener (join public lobby thread)
     * which is waiting for the player to either be sorted into a game
     * or cancel their waiting
     */
    public static class PlayerObserverLink {
        private final Player player;
        private final PlayerPoolListener playerPoolListener;

        public PlayerObserverLink (Player p, PlayerPoolListener o) {
            this.player = p;
            this.playerPoolListener = o;
        }

        public Player getPlayer() {
            return this.player;
        }

        public PlayerPoolListener getPlayerPoolListener () {
            return this.playerPoolListener;
        }
    }

    private final List<PlayerObserverLink> playerPool;
    private Game game;
    private final GameFactory gameFac;
    private final PlayerFactory playerFac;
    private final Timer sortPlayersTimer;
    private boolean startedSortTimer;
    private final Lock playerPoolLock;

    /**
     * @param playerFac Inject a factory to determine how players are made
     * @param gameFac Inject a factory to determine how games are made
     * @param playerPoolLock The lock used for synchronization with other object that access the player pool
     */
    public LobbyManager (PlayerFactory playerFac, GameFactory gameFac, Lock playerPoolLock) {
        this.gameFac = gameFac;
        this.playerFac = playerFac;
        this.playerPool = new CopyOnWriteArrayList<>();
        this.sortPlayersTimer = new Timer();
        this.startedSortTimer = false;
        this.playerPoolLock = playerPoolLock;
    }

    /**
     * @return If a game has been started and has not yet ended
     */
    public boolean isGameRunning () { return !(isGameNull() || isGameEnded()); }

    /**
     * @return If a game instance is null, meaning, no game exists
     */
    public boolean isGameNull () { return game == null; }

    /**
     * @return If a game exists but has ended, meaning, the final timer
     * iteration has finished executing
     */
    public boolean isGameEnded () { return game.isTimerStopped(); }

    /**
     * Wrapper for switchTurn
     */
    public void switchTurn() {
        game.switchTurn();
    }

    /**
     * Set the game to null.
     * @throws GameRunningException if the game is running and you tried to set it to null
     */
    public void setGameNull () throws GameRunningException {
        if (isGameRunning()) {
            throw new GameRunningException("Cannot set game to null while game is still running");
        }
        this.game = null;
    }

    /**
     * Called from sort player timer task use case to cancel it once game ends
     * @return the timer which repeatedly sorts the players
     */
    public Timer getSortPlayersTimer () { return this.sortPlayersTimer; }

    /**
     * @return a shallow copy of the player pool
     */
    public List<PlayerObserverLink> getPool () {
        return new ArrayList<PlayerObserverLink>(playerPool);
    }

    /**
     * Remove a player from the pool and call the linked PlayerPoolListener's
     * onJoinGamePlayer method, indicating that the player has joined the game
     * Notice that this method is not thread-safe!
     * @param p Player you would like to remove
     * @throws PlayerNotFoundException if the player was not found in the pool
     * @throws GameDoesntExistException if the game isn't running, in which case, player can't join it
     */
    public void removeFromPoolJoin (Player p) throws PlayerNotFoundException, GameDoesntExistException {
        PlayerObserverLink l = getLinkFromPlayer(p);

        // This error should not happen unless use case logic is broken
        if (l == null) {
            throw new PlayerNotFoundException(
                    String.format("Player with id %s was not in the pool", p.getPlayerId())
            );
        }

        if (!isGameRunning()) {
            throw new GameDoesntExistException("Game is either null or has ended. Players cannot join it");
        }
        // Below this, we assume that l is in pool and game is running. We assume a proper
        // lock architecture that ensures that this assumption cannot change from another thread

        // Removes the link from pool
        playerPool.remove(l);

        // Passes game to the corresponding listener
        l.playerPoolListener.onJoinGamePlayer(game);

    }

    /**
     * This method removes all the players from the pool
     */
    private void clearPool() {
        playerPool.clear();
    }

    /**
     * This method removes notifies all the players that the game has started and removes them from the pool
     */
    public void removeAllFromPoolJoin() {
        for(PlayerObserverLink playerObserverLink: playerPool) {
            // We need to lock the critical section for every player
            Lock lock = playerObserverLink.getPlayerPoolListener().getLock();
            lock.lock();
            playerObserverLink.playerPoolListener.onJoinGamePlayer(this.game);
            lock.unlock();
        }
        this.clearPool();
    }

    /**
     * This method calls onCancelPlayer on every player and removes all the players from the pool
     */
    public void removeAllFromPoolCancel() {
        for(PlayerObserverLink playerObserverLink: playerPool) {
            playerObserverLink.playerPoolListener.onCancelPlayer();
        }
        this.clearPool();
    }

    /**
     * Set the game attribute
     * @param game Game to be set for this lobby
     * @throws GameRunningException if game already exists
     */
    public void setGame (Game game) throws GameRunningException {
        if (!this.isGameNull()) {
            throw new GameRunningException(
                    "Trying to set an existing game");
        }
        this.game = game;
    }

    /**
     * Gets all the players from the players pool
     * @return an arraylist of players
     */
    public ArrayList<Player> getPlayersFromPool() {
        ArrayList<Player> players = new ArrayList<>();
        for(PlayerObserverLink playerObserverLink : playerPool)
            players.add(playerObserverLink.player);
        return players;
    }

    /**
     * Add word from the current-turn player to the story of our game
     * @param word String to add to the story
     * @param playerId String of the player who attempts to submit a word
     * @throws GameDoesntExistException if game does not exist
     * @throws PlayerNotFoundException if player cannot be found
     * @throws OutOfTurnException if this is not our player's turn
     * @throws InvalidWordException if the word is not valid
     */
    public void addWord (String word, String playerId) throws GameDoesntExistException, PlayerNotFoundException,
            OutOfTurnException, InvalidWordException {
        if (!this.isGameRunning()) {
            throw new GameDoesntExistException(
                    "The game you are trying to add word to does not exist");
        }
        if (this.game.getPlayerById(playerId) == null) {
            throw new PlayerNotFoundException(
                    "The player you are trying to add word is not found int the game");
        }
        if (!this.game.getCurrentTurnPlayer().getPlayerId().equals(playerId)) {
            throw new OutOfTurnException(
                    "Trying to submit a word out of turn");
        }
        Player author = this.game.getPlayerById(playerId);
        this.game.getStory().addWord(word, author);
    }

    /**
     * Create a game based on the provided settings and players from the pool
     * @param settings Map<String, Integer> String to add to the story
     */
    public Game newGameFromPool (Map<String, Integer> settings) {
        List<Player> initialPlayers = new ArrayList<>();
        for (PlayerObserverLink pol : this.playerPool) {
            initialPlayers.add(pol.getPlayer());
        }
        return this.gameFac.createGame(settings, initialPlayers);
    }

    /**
     * Removes a PlayerObserverLink from the pool via its player
     * Notice that this method is not thread-safe!
     * @param p the player in the POL to be removed
     * @throws PlayerNotFoundException if the player was not found in any POLs in playerPool
     */
    public void removeFromPoolCancel(Player p) throws PlayerNotFoundException {
        PlayerObserverLink pol = getLinkFromPlayer(p);
        if (pol == null) {
            throw new PlayerNotFoundException("Player not found");
        }
        playerPool.remove(pol);
        pol.playerPoolListener.onCancelPlayer();

    }

    /**
     * Removes a specified player from the game instance
     * @param p the player to be removed
     * @throws GameDoesntExistException when the game doesn't exist
     * @throws PlayerNotFoundException when p is not in the game
     */
    public void removePlayerFromGame(Player p) throws GameDoesntExistException, PlayerNotFoundException {
        if (game == null) {
            throw new GameDoesntExistException("Game does not exist");
        }
        if (game.getPlayerById(p.getPlayerId()) == null) {
            throw new PlayerNotFoundException("Player to remove is not in game");
        }
        game.removePlayer(p);
    }

    /**
     * Adds a player to the game instance
     * @param p the player to be added
     * @return if the player was successfully added
     * @throws GameDoesntExistException when the game doesn't exist
     */
    public boolean addPlayerToGame(Player p) throws GameDoesntExistException {
        if (game == null) {
            throw new GameDoesntExistException("Game does not exist");
        }
        return game.addPlayer(p);
    }

    /**
     * Creates a new player
     * @param displayName the display name for the player
     * @param id the unique id of the player
     * @return the created player instance
     */
    public Player createNewPlayer(String displayName, String id) throws
            IdInUseException, InvalidDisplayNameException {
        return playerFac.createPlayer(displayName, id);
    }

    /**
     * Helper method to find a PlayerObserverLink in the playerPool via its player
     * @param p the player in the PlayerObserverLink
     * @return the PlayerObserverLink containing player p, null if there isn't one
     */
    public PlayerObserverLink getLinkFromPlayer(Player p){
        for (PlayerObserverLink pol : playerPool) {
            if (pol.getPlayer().equals(p)) {
                return pol;
            }
        }
        return null;
    }

    /**
     * Creates PlayerObserverLink from p and o, which is then used to add the player to the pool.
     * This method engages the playerPoolLock lock
     * @param p the player in the PlayerObserverLink
     * @param o the PlayerPoolListener in the PlayerObserverLink
     */
    public void addPlayerToPool (Player p, PlayerPoolListener o) {
        playerPoolLock.lock();
        PlayerObserverLink pol = new PlayerObserverLink(p, o);
        this.playerPool.add(pol);
        playerPoolLock.unlock();
    }

    /**
     * Gets all the players from the game
     * @return an arraylist of players
     */
    public ArrayList<Player> getPlayersFromGame() {
        return new ArrayList<>(game.getPlayers());
    }

}
