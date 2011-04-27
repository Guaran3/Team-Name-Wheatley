package player.request.grammar;

import java.util.List;

import player.gamer.Gamer;
import player.gamer.event.GamerCompletedMatchEvent;
import player.gamer.event.GamerUnrecognizedMatchEvent;
import util.gdl.grammar.GdlSentence;
import util.logging.GamerLogger;

public final class StopRequest extends Request
{
	private final Gamer gamer;
	private final String matchId;
	private final List<GdlSentence> moves;

	public StopRequest(Gamer gamer, String matchId, List<GdlSentence> moves)
	{
		this.gamer = gamer;
		this.matchId = matchId;
		this.moves = moves;
	}
	
	@Override
	public String getMatchId() {
		return matchId;
	}

	@Override
	public String process(long receptionTime)
	{
        // First, check to ensure that this stop request is for the match
        // we're currently playing. If we're not playing a match, or we're
        // playing a different match, send back "busy".	    
		if (gamer.getMatch() == null || !gamer.getMatch().getMatchId().equals(matchId))
		{
		    GamerLogger.logError("GamePlayer", "Got stop message not intended for current game: ignoring.");
			gamer.notifyObservers(new GamerUnrecognizedMatchEvent(matchId));
			return "busy";
		}

		//TODO: Add goal values
		if(moves != null) {
			gamer.getMatch().appendMoves(moves);
		}
		gamer.getMatch().markCompleted(null);
		gamer.notifyObservers(new GamerCompletedMatchEvent());
		gamer.stop();
		
		// Once the match has ended, set 'roleName' and 'match'
		// to NULL to indicate that we're ready to begin a new match.
		gamer.setRoleName(null);
	    gamer.setMatch(null);
	    
		return "done";
	}

	@Override
	public String toString()
	{
		return "stop";
	}
}