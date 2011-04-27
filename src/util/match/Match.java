package util.match;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import external.JSON.JSONArray;
import external.JSON.JSONException;
import external.JSON.JSONObject;

import util.game.Game;
import util.game.RemoteGameRepository;
import util.gdl.factory.GdlFactory;
import util.gdl.factory.exceptions.GdlFormatException;
import util.gdl.grammar.GdlSentence;
import util.statemachine.Move;
import util.statemachine.Role;
import util.symbol.factory.SymbolFactory;
import util.symbol.factory.exceptions.SymbolFormatException;
import util.symbol.grammar.SymbolList;

/**
 * Match encapsulates all of the information relating to a single match.
 * A match is a single play through a game, with a complete history that
 * lists what move each player made at each step through the match. This
 * also includes other relevant metadata about the match, including some
 * unique identifiers, configuration information, and so on.
 * 
 * NOTE: Match objects created by a player, representing state read from
 * a server, are not completely filled out. For example, they only get an
 * ephemeral Game object, which has a rulesheet but no key or metadata.
 * Gamers which do not derive from StateMachineGamer also do not keep any
 * information on what states have been observed, because (somehow) they
 * are representing games without using state machines. In general, these
 * player-created Match objects shouldn't be sent out into the ecosystem.
 * 
 * @author Sam
 */
public final class Match
{
    private final String matchId;
    private final String randomToken;
    private final String spectatorAuthToken;
    private final int playClock;
    private final int startClock;
    private final Date startTime;
	private final Game theGame;
	private final List<String> theRoleNames;
	private final List<List<GdlSentence>> moveHistory;
	private final List<Set<GdlSentence>> stateHistory;	
	private final List<Date> stateTimeHistory;
	private boolean isCompleted;	
	private final List<Integer> goalValues;

	public Match(String matchId, int startClock, int playClock, Game theGame)
	{
		this.matchId = matchId;
		this.startClock = startClock;
		this.playClock = playClock;
		this.theGame = theGame;
		
		this.startTime = new Date();
		this.randomToken = getRandomString(32);
		this.spectatorAuthToken = getRandomString(12);
		this.isCompleted = false;
		
		this.theRoleNames = new ArrayList<String>();
		for(Role r : Role.computeRoles(theGame.getRules())) {
		    this.theRoleNames.add(r.getName().getName().toString());
		}
		
		this.moveHistory = new ArrayList<List<GdlSentence>>();
		this.stateHistory = new ArrayList<Set<GdlSentence>>();
		this.stateTimeHistory = new ArrayList<Date>();
		
		this.goalValues = new ArrayList<Integer>();
	}
	
	public Match(String theJSON, Game theGame) throws JSONException, SymbolFormatException, GdlFormatException {
        JSONObject theMatchObject = new JSONObject(theJSON);

        this.matchId = theMatchObject.getString("matchId");
        this.startClock = theMatchObject.getInt("startClock");
        this.playClock = theMatchObject.getInt("playClock");
        if (theGame == null) {
            this.theGame = RemoteGameRepository.loadSingleGame(theMatchObject.getString("gameMetaURL"));
            if (this.theGame == null) {
                throw new RuntimeException("Could not find metadata for game referenced in Match object: " + theMatchObject.getString("gameMetaURL"));
            }
        } else {
            this.theGame = theGame;
        }
        
        this.startTime = new Date(theMatchObject.getLong("startTime"));
        this.randomToken = theMatchObject.getString("randomToken");
        this.spectatorAuthToken = null;
        this.isCompleted = theMatchObject.getBoolean("isCompleted");

        this.theRoleNames = new ArrayList<String>();
        for(Role r : Role.computeRoles(theGame.getRules())) {
            this.theRoleNames.add(r.getName().getName().toString());
        }
        
        this.moveHistory = new ArrayList<List<GdlSentence>>();
        this.stateHistory = new ArrayList<Set<GdlSentence>>();
        this.stateTimeHistory = new ArrayList<Date>();
        
        JSONArray theMoves = theMatchObject.getJSONArray("moves");
        for (int i = 0; i < theMoves.length(); i++) {
            List<GdlSentence> theMove = new ArrayList<GdlSentence>();
            JSONArray moveElements = theMoves.getJSONArray(i);
            for (int j = 0; j < moveElements.length(); j++) {
                theMove.add((GdlSentence)GdlFactory.create(moveElements.getString(j)));
            }
            moveHistory.add(theMove);
        }
        JSONArray theStates = theMatchObject.getJSONArray("states");
        for (int i = 0; i < theStates.length(); i++) {
            Set<GdlSentence> theState = new HashSet<GdlSentence>();
            SymbolList stateElements = (SymbolList) SymbolFactory.create(theStates.getString(i));
            for (int j = 0; j < stateElements.size(); j++)
            {
                theState.add((GdlSentence)GdlFactory.create("( true " + stateElements.get(j).toString() + " )"));
            }
            stateHistory.add(theState);
        }              
        JSONArray theStateTimes = theMatchObject.getJSONArray("stateTimes");        
        for (int i = 0; i < theStateTimes.length(); i++) {
            this.stateTimeHistory.add(new Date(theStateTimes.getLong(i)));
        }
        
        this.goalValues = new ArrayList<Integer>();
        try {
            JSONArray theGoalValues = theMatchObject.getJSONArray("goalValues");
            for (int i = 0; i < theGoalValues.length(); i++) {            
                this.goalValues.add(theGoalValues.getInt(i));
            }
        } catch(JSONException je) {}
	}
	
	/* Mutators */

	public void appendMoves(List<GdlSentence> moves) {	    
		moveHistory.add(moves);
	}

	public void appendMoves2(List<Move> moves) {
	    // NOTE: This is appendMoves2 because it Java can't handle two
	    // appendMove methods that both take List objects with different
	    // templatized parameters.
        if (moves.get(0) instanceof Move) {
            List<GdlSentence> theMoves = new ArrayList<GdlSentence>();
            for(Move m : moves) {
                theMoves.add(m.getContents());
            }
            appendMoves(theMoves);          
        }
	}
	
	public void appendState(Set<GdlSentence> state) {
	    stateHistory.add(state);
	    stateTimeHistory.add(new Date());
	}
	
	public void markCompleted(List<Integer> theGoalValues) {
	    this.isCompleted = true;
	    if (theGoalValues != null) {
	        this.goalValues.addAll(theGoalValues);
	    }
	}
	
	/* Complex accessors */
		
    public String toJSON() {
        StringBuilder theJSON = new StringBuilder();
        
        theJSON.append("{\n");
        // Uniquification variables
        theJSON.append("    \"matchId\": \"" + matchId + "\",\n");
        theJSON.append("    \"randomToken\": \"" + randomToken + "\",\n");
        theJSON.append("    \"startTime\": " + startTime.getTime() + ",\n");
        // Game information
        if (getGameName() != null) {
            theJSON.append("    \"gameName\": \"" + getGameName() + "\",\n");
        } else {
            theJSON.append("    \"gameName\": null,\n");
        }
        if (getGameRepositoryURL() != null) {
            theJSON.append("    \"gameMetaURL\": \"" + getGameRepositoryURL() + "\",\n");
        } else {
            theJSON.append("    \"gameMetaURL\": null,\n");
        }
        theJSON.append("    \"gameRoleNames\": " + renderArrayAsJSON(theRoleNames, true) + ",\n");
        // States/moves
        theJSON.append("    \"isCompleted\": " + isCompleted + ",\n");
        theJSON.append("    \"states\": " + renderArrayAsJSON(renderStateHistory(stateHistory), true) + ",\n");
        theJSON.append("    \"moves\": " + renderArrayAsJSON(renderMoveHistory(moveHistory), false) + ",\n");
        theJSON.append("    \"stateTimes\": " + renderArrayAsJSON(stateTimeHistory, false) + ",\n");
        if (goalValues.size() > 0) {
            theJSON.append("    \"goalValues\": " + renderArrayAsJSON(goalValues, false) + ",\n");
        }
        // Protocol information
        theJSON.append("    \"startClock\": " + startClock + ",\n");
        theJSON.append("    \"playClock\": " + playClock + "\n");
        theJSON.append("}");
        
        return theJSON.toString();
    }
	
    public List<GdlSentence> getMostRecentMoves() {
        if (moveHistory.size() == 0)
            return null;
        return moveHistory.get(moveHistory.size()-1);
    }

    public Set<GdlSentence> getMostRecentState() {
        if (stateHistory.size() == 0)
            return null;
        return stateHistory.get(stateHistory.size()-1);        
    }
    
    public String getGameName() {
        return getGame().getName();
    }
    
    public String getGameRepositoryURL() {
        return getGame().getRepositoryURL();
    }
    
    public String toString() {
        return toJSON();
    }    
	
	/* Simple accessors */

    public String getMatchId() {
        return matchId;
    }
    
    public String getRandomToken() {
        return randomToken;
    }
    
    public String getSpectatorAuthToken() {
        return spectatorAuthToken;
    }
	
	public Game getGame() {
		return theGame;
	}

	public List<List<GdlSentence>> getMoveHistory() {
		return moveHistory;
	}
	
    public List<Set<GdlSentence>> getStateHistory() {
        return stateHistory;
    }
    
    public List<Date> getStateTimeHistory() {
        return stateTimeHistory;
    }    

	public int getPlayClock() {
		return playClock;
	}

	public int getStartClock() {
		return startClock;
	}
	
	public Date getStartTime() {
	    return startTime;
	}
	
	public List<String> getRoleNames() {
	    return theRoleNames;
	}
	
	public boolean isCompleted() {
	    return isCompleted;
	}
	
	public List<Integer> getGoalValues() {
	    return goalValues;
	}
	
	/* Static methods */
	
    public static String getRandomString(int nLength) {
        Random theGenerator = new Random();
        String theString = "";
        for (int i = 0; i < nLength; i++) {
            int nVal = theGenerator.nextInt(62);
            if (nVal < 26) theString += (char)('a' + nVal);
            else if (nVal < 52) theString += (char)('A' + (nVal-26));
            else if (nVal < 62) theString += (char)('0' + (nVal-52));
        }
        return theString;
    }
    
    private static String renderArrayAsJSON(List<?> theList, boolean useQuotes) {
        String s = "[";
        for (int i = 0; i < theList.size(); i++) {
            Object o = theList.get(i);
            // AppEngine-specific, not needed yet: if (o instanceof Text) o = ((Text)o).getValue();
            if (o instanceof Date) o = ((Date)o).getTime();
            
            if (useQuotes) s += "\"";
            s += o.toString();
            if (useQuotes) s += "\"";
            
            if (i < theList.size() - 1)
                s += ", ";
        }
        return s + "]";        
    }

    private static List<String> renderStateHistory(List<Set<GdlSentence>> stateHistory) {
        List<String> renderedStates = new ArrayList<String>();
        for (Set<GdlSentence> aState : stateHistory) {
            renderedStates.add(renderStateAsSymbolList(aState));
        }
        return renderedStates;
    }

    private static List<String> renderMoveHistory(List<List<GdlSentence>> moveHistory) {
        List<String> renderedMoves = new ArrayList<String>();
        for (List<GdlSentence> aMove : moveHistory) {
            renderedMoves.add(renderArrayAsJSON(aMove, true));
        }
        return renderedMoves;        
    }

    private static String renderStateAsSymbolList(Set<GdlSentence> theState) {
        // Strip out the TRUE proposition, since those are implied for states.
        String s = "( ";
        for (GdlSentence sent : theState) {
            String sentString = sent.toString();
            s += sentString.substring(6, sentString.length()-2).trim() + " ";
        }
        return s + ")";
    }    
}