package player.gamer;

import java.util.*;
import player.gamer.exception.MetaGamingException;
import player.gamer.exception.MoveSelectionException;
import player.gamer.Gamer;
import util.gdl.grammar.*;
import util.prover.aima.AimaProver;

public class LegalGameplayer extends Gamer {

	private AimaProver prover;
	private Set<GdlSentence> currentState;
	private List<GdlProposition> roles;
	
	@Override
	public void metaGame(long timeout) throws MetaGamingException {
		prover = new AimaProver(new HashSet<Gdl>(getMatch().getGame().getRules()));
		GdlRelation initQuery = GdlPool.getRelation(GdlPool.getConstant("init"), new GdlTerm[] { GdlPool.getVariable("?x") });
		currentState = getCurrentState(prover.askAll(initQuery, new HashSet<GdlSentence>()));
		getMatch().appendState(currentState);
		roles = new ArrayList<GdlProposition>();
       
		for (Gdl gdl : getMatch().getGame().getRules()) {
            if (gdl instanceof GdlRelation) {
                GdlRelation relation = (GdlRelation) gdl;               
                if (relation.getName().getValue().equals("role")) {
                    roles.add((GdlProposition) relation.get(0).toSentence());
                }
            }
        }
		
	}
	private Set<GdlSentence> getCurrentState(Set<GdlSentence> values) {
		Set<GdlSentence> facts = new HashSet<GdlSentence>();
		
		for (GdlSentence result : values) {
			facts.add(GdlPool.getRelation(GdlPool.getConstant("true"), new GdlTerm[] { result.get(0) }));
		}
		
		return facts;
	}

	private Set<GdlSentence> getContext(List<GdlSentence> moves) {
		Set<GdlSentence> context = new HashSet<GdlSentence>(currentState);
		
		int count = 0;
		for (GdlSentence move : moves) {
			context.add(GdlPool.getRelation(GdlPool.getConstant("does"), new GdlTerm[] { roles.get(count++).toTerm(), move.toTerm() }));
		}
		
		return context;
	}
	
	private Set<GdlSentence> getNextState(List<GdlSentence> moves) {
		GdlRelation nextQuery = GdlPool.getRelation(GdlPool.getConstant("next"), new GdlTerm[] { GdlPool.getVariable("?x") });
		return getCurrentState(prover.askAll(nextQuery, getContext(moves)));
	}
	
	@Override
	public GdlSentence selectMove(long timeout) throws MoveSelectionException {
	

		List<GdlSentence> prevMove = getMatch().getMostRecentMoves();
		if (prevMove != null) {
			currentState = getNextState(prevMove);
			getMatch().appendState(currentState);
		}

		GdlRelation legalQuery = GdlPool.getRelation(GdlPool.getConstant("legal"), new GdlTerm[] { getRoleName().toTerm(), GdlPool.getVariable("?x")});
		Set<GdlSentence> legalMoves = prover.askAll(legalQuery, currentState);
		return legalMoves.iterator().next().get(1).toSentence();


	}

	@Override
	public void stop() {
		List<GdlSentence> prevMoves = getMatch().getMostRecentMoves();
		
		if (prevMoves != null) {
			List<Integer> goals = new ArrayList<Integer>();
			currentState = getNextState(prevMoves);
			getMatch().appendState(currentState);
		
			for (GdlProposition role : roles) {
				Set<GdlSentence> goalData = prover.askAll(GdlPool.getRelation(GdlPool.getConstant("goal"), new GdlTerm[] { role.toTerm(), GdlPool.getVariable("?x")}), getContext(prevMoves));
				GdlConstant constant = (GdlConstant) ((GdlRelation) goalData.iterator().next()).get(1);
				goals.add(Integer.parseInt(constant.toString()));
			}
			
			getMatch().markCompleted(goals);
		}
		
		// TODO Auto-generated method stub

	}
	


	@Override
	public String getName() {
		// TODO Auto-generated method stub
		return "Team Name firstmove";
	}

}
