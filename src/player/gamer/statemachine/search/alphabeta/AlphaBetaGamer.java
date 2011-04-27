package player.gamer.statemachine.search.alphabeta;

import java.util.List;
import java.util.Random;

import player.gamer.statemachine.StateMachineGamer;
import player.gamer.statemachine.reflex.event.ReflexMoveSelectionEvent;
import player.gamer.statemachine.reflex.gui.ReflexDetailPanel;
import util.statemachine.MachineState;
import util.statemachine.Move;
import util.statemachine.Role;
import util.statemachine.StateMachine;
import util.statemachine.exceptions.GoalDefinitionException;
import util.statemachine.exceptions.MoveDefinitionException;
import util.statemachine.exceptions.TransitionDefinitionException;
import util.statemachine.implementation.prover.ProverStateMachine;
import apps.player.detail.DetailPanel;

/**
 * Minimax gamer is a BAMF that you do NOT want to mess with.
 * Once, minimax gamer killed a man.
 */
public final class AlphaBetaGamer extends StateMachineGamer
{
	
	/**
	 * Does nothing for the metagame
	 */
	@Override
	public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		// Do nothing.
		rGen = new Random();
	}
	
	/**
	 * Selects the first legal move
	 */
	@Override
	public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		long start = System.currentTimeMillis();
		
		StateMachine curMachine = getStateMachine();
		MachineState rootState = getCurrentState();
		List<Move> moves = curMachine.getLegalMoves(rootState, getRole());
		ScoredMove bestMove =  findBestMove(rootState, getRole(), -1, 101, start, timeout - 20);
		Move selection = bestMove.move;

		long stop = System.currentTimeMillis();
		notifyObservers(new ReflexMoveSelectionEvent(moves, selection, stop - start));
		return selection;
	}
	
	private ScoredMove findBestMove(MachineState rootState, Role curRole, int curAlpha, int curBeta, long startTime, long retTimeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		ScoredMove maxMove = new ScoredMove();
		StateMachine curMachine = getStateMachine();
		List<Move> moves = curMachine.getLegalMoves(rootState, curRole);
		maxMove.move = moves.get(rGen.nextInt(moves.size()));
		maxMove.score = curAlpha;

		for (Move move : moves)
		{
			if (System.currentTimeMillis() > retTimeout-20) break;
			List<List<Move>> oppoMoves = curMachine.getLegalJointMoves(rootState, curRole, move);
			int minScore = curBeta;
			for (List<Move> curMoveList : oppoMoves)
			{
				if (System.currentTimeMillis() > retTimeout-20) break;
				MachineState nextState = curMachine.getNextState(rootState, curMoveList);
				if (curMachine.isTerminal(nextState))
				{
					int goalScore = curMachine.getGoal(nextState, curRole);
					if (goalScore <= minScore) 
					{
						minScore = goalScore;
						if (minScore <= maxMove.score) break;
					}
				}
				else {
					ScoredMove curScoredMove = findBestMove(nextState, curRole, maxMove.score, minScore, startTime, retTimeout-20);
					if (curScoredMove.score <= minScore)
					{
						minScore = curScoredMove.score;
						if (minScore <= maxMove.score) break;
					}
				}
			}
			if (System.currentTimeMillis() > retTimeout-20) break;
			if (minScore > maxMove.score)
			{
				maxMove.move = move;
				maxMove.score = minScore;
				if (maxMove.score > curBeta) break;
			}
		}
		
		return maxMove;
	}
	
	@Override
	public void stateMachineStop() {
		// Do nothing.
	}

	/**
	 * Uses a ProverStateMachine
	 */
	@Override
	public StateMachine getInitialStateMachine() {
		return new ProverStateMachine();
	}
	@Override
	public String getName() {
		return "AlphaBeta";
	}

	@Override
	public DetailPanel getDetailPanel() {
		return new ReflexDetailPanel();
	}
	
	private Random rGen;
}

class TreeNode
{
	public TreeNode()
	{
		state = null;
		moves = null;
		score = -1;
	}
	
	public MachineState state;
	public List<ScoredMove> moves;
	public int score;
}

class ScoredMove
{
	public ScoredMove()
	{
		move = null;
		score = -1;
	}
	
	public Move move;
	public int score;
}