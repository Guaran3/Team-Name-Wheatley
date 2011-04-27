package player.gamer.statemachine.human.event;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import util.observer.Event;
import util.statemachine.Move;

public final class HumanNewMovesEvent extends Event
{

	private final List<Move> moves;
	private final Move selection;

	public HumanNewMovesEvent(List<Move> moves, Move selection)
	{
	    Collections.sort(moves, new Comparator<Move>(){public int compare(Move o1, Move o2) {return o1.toString().compareTo(o2.toString());}});
		this.moves = moves;
		this.selection = selection;
	}

	public List<Move> getMoves()
	{
		return moves;
	}

	public Move getSelection()
	{
		return selection;
	}

}
