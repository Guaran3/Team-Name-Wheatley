package player.gamer.statemachine.reflex.random;

import java.util.List;
import java.util.Random;

import player.gamer.statemachine.StateMachineGamer;
import player.gamer.statemachine.reflex.event.ReflexMoveSelectionEvent;
import player.gamer.statemachine.reflex.gui.ReflexDetailPanel;
import util.statemachine.Move;
import util.statemachine.StateMachine;
import util.statemachine.exceptions.GoalDefinitionException;
import util.statemachine.exceptions.MoveDefinitionException;
import util.statemachine.exceptions.TransitionDefinitionException;
import util.statemachine.implementation.prover.ProverStateMachine;
import apps.player.detail.DetailPanel;

/**
 * RandomGamer is a very simple state-machine-based Gamer that will always
 * pick a random legal move that it finds at any state in the game. This
 * is one of a family of simple "reflex" gamers which act entirely on reflex
 * (picking the first legal move, or a random move) regardless of the current
 * state of the game.
 * 
 * This is not really a serious approach to playing games, and is included in
 * this package merely as an example of a functioning Gamer.
 */
public final class RandomGamer extends StateMachineGamer
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

		List<Move> moves = getStateMachine().getLegalMoves(getCurrentState(), getRole());
		Move selection = moves.get(rGen.nextInt(moves.size()));

		long stop = System.currentTimeMillis();

		notifyObservers(new ReflexMoveSelectionEvent(moves, selection, stop - start));
		return selection;
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
		return "Random";
	}

	@Override
	public DetailPanel getDetailPanel() {
		return new ReflexDetailPanel();
	}
	
	private Random rGen;
}
