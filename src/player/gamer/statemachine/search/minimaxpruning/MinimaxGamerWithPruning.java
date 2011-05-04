package player.gamer.statemachine.search.minimaxpruning;

import java.util.List;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.HashSet;

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
public final class MinimaxGamerWithPruning extends StateMachineGamer
{
	
	/**
	 * Does nothing for the metagame
	 */
	@Override
	public void stateMachineMetaGame(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		numMovesExpanded = 0;
		numDepthSamples = 0;
		averageDepthTime = 0;
		searchedMoves = new LinkedList<Move>();
		stateMap = new HashMap<MachineState, StateNode>(10000);
		nodeQueue = new PriorityQueue<StateNode>(10000);
		StateMachine curMachine = getStateMachine();
		MachineState initState = curMachine.getInitialState();
		Role initRole = getRole();
		searchTreeFromRoot(initState, initRole, timeout-20 );
	}
	
	/**
	 * Selects the first legal move
	 */
	@Override
	public Move stateMachineSelectMove(long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		long start = System.currentTimeMillis();
		numMovesExpanded = 0;
		numDepthSamples = 0;
		averageDepthTime = 0;
		searchedMoves.clear();
		nodeQueue.clear();
		//stateMap.clear();
		MachineState rootState = getCurrentState();
		Move selection = findBestMove(rootState, getRole(), timeout-30);
		
		long stop = System.currentTimeMillis();
		notifyObservers(new ReflexMoveSelectionEvent(searchedMoves, selection, stop - start));
		return selection;
	}
	
	public Move findBestMove(MachineState rootState, Role curRole, long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		searchTreeFromRoot(rootState, curRole, timeout);
		
		StateNode rootNode = stateMap.get(rootState);
		if (rootNode.maxMove == null)
			return getStateMachine().getRandomMove(rootState, curRole);
		return rootNode.maxMove.move;
	}
	
	public void searchTreeFromRoot(MachineState rootState, Role curRole, long timeout) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		StateMachine curMachine = getStateMachine();
		
		//For some reason, state is out of this tree, so create and find it
		if (!stateMap.containsKey(rootState)) 
		{
			StateNode rootNode = new StateNode();
			rootNode.state = rootState;
			rootNode.searchScore = 0;
			rootNode.depth = 0;
			rootNode.maxMove = null;
			rootNode.isExpanded = false;
			
			//Clear out structures for mystery node!
			nodeQueue.clear();
			stateMap.clear();
			nodeQueue.add(rootNode);
			stateMap.put(rootState, rootNode);
		}
		
		StateNode rootNode = stateMap.get(rootState);
		
		//Find fringe nodes
		List<StateNode> totalNodesToEnqueue = new LinkedList<StateNode>();
		List<StateNode> curNodesToEnqueue = new LinkedList<StateNode>();
		HashSet<StateNode> seenNodes = new HashSet<StateNode>();
		HashSet<StateNode> newSeenNodes = new HashSet<StateNode>();
		fillNodeQueue(rootNode, seenNodes);
		
		//Execute BFS
		while (!nodeQueue.isEmpty())
		{
			if (System.currentTimeMillis() > timeout - (100 + 1.5 * NUM_PROBES * averageDepthTime)) break;
			StateNode curNode = nodeQueue.remove();
			
			//Error check
			if (curNode.isExpanded) continue;
			List<Move> possMoves = curMachine.getLegalMoves(curNode.state, curRole);
			totalNodesToEnqueue.clear();
			newSeenNodes.clear();
			//Look at all possible moves from a state
			for (Move curMove : possMoves)
			{
				curNodesToEnqueue.clear();
				searchedMoves.add(curMove);
				MoveNode curMoveNode = new MoveNode();
				curMoveNode.move = curMove;
				curMoveNode.prevState = curNode;
				curMoveNode.minState = null;
				List<List<Move>> curMoveLists = curMachine.getLegalJointMoves(curNode.state, curRole, curMove);
				
				//Look at all possible moveLists with chosen move
				for (List<Move> curMoveList : curMoveLists)
				{
					MachineState nextState = curMachine.getNextState(curNode.state, curMoveList);
					//Check if state already exists
					if (stateMap.containsKey(nextState))
					{
						//Link up states
						StateNode nextNode = stateMap.get(nextState);
						if (curMoveNode.nextStates.contains(nextNode)) continue;
						nextNode.prevMoves.add(curMoveNode);
						if (nextNode.isExpanded &&
							(nextNode.stateScore < curMoveNode.minScore || curMoveNode.minState == null))
						{
							curMoveNode.minScore = nextNode.stateScore;
							curMoveNode.minState = nextNode;
							if (curMoveNode.minScore <= 0 && nextNode.fullExpanded && nextNode.isTerminal) {
								curMoveNode.fullExpanded = true;
								curMoveNode.nextStates.add(nextNode);
								newSeenNodes.removeAll(curNodesToEnqueue);
								newSeenNodes.add(nextNode);
								curNodesToEnqueue.clear();
								//curNodesToEnqueue.add(nextNode);
								break;
							}
						}
						curMoveNode.nextStates.add(nextNode);
						if (!seenNodes.contains(nextNode) && !newSeenNodes.contains(nextNode))
						{
							if (!nextNode.isExpanded)
							{
								nextNode.depth = curNode.depth+1;
								nextNode.searchScore = nextNode.depth;
							}
							curNodesToEnqueue.add(nextNode);
							newSeenNodes.add(nextNode);
						}
					}
					else
					{
						//Create node, add to Queue
						StateNode nextNode = new StateNode();
						nextNode.state = nextState;
						nextNode.prevMoves.add(curMoveNode);
						nextNode.depth = curNode.depth + 1;
						nextNode.maxMove = null;
						
						if (!curMachine.isTerminal(nextState))
						{				
							//Add to queue to be expanded
							nextNode.isExpanded = false;
							nextNode.searchScore = nextNode.depth;
							curNodesToEnqueue.add(nextNode);
						}
						else 
						{
							//Is terminal, set the score.
							nextNode.isExpanded = true;
							nextNode.isTerminal = true;
							nextNode.fullExpanded = true;
							nextNode.searchScore = nextNode.depth;
							nextNode.maxScore = curMachine.getGoal(nextState, curRole);
							//Do NOT use calcStateScore here.
							nextNode.stateScore = nextNode.maxScore + (nextNode.maxScore - 50.0)/100.0;
							//Update curMove
							if (nextNode.stateScore < curMoveNode.minScore || curMoveNode.minState == null)
							{
								curMoveNode.minScore = nextNode.stateScore;
								curMoveNode.minState = nextNode;
								if (curMoveNode.minScore <= 0) {
									curMoveNode.fullExpanded = true;
									stateMap.put(nextState, nextNode);
									curMoveNode.nextStates.add(nextNode);
									newSeenNodes.removeAll(curNodesToEnqueue);
									newSeenNodes.add(nextNode);
									curNodesToEnqueue.clear();
									break;
								}
							}
						}
						
						//Add to Map and Queue
						stateMap.put(nextState, nextNode);
						//seenNodes.add(nextNode);
						newSeenNodes.add(nextNode);
						
						curMoveNode.nextStates.add(nextNode);
					}
				}
				
				checkMoveFullExpansion(curMoveNode);
				//Add curMoveNode to curNode
				if (curMoveNode.minState != null && 
					(curMoveNode.minScore >= curNode.maxScore || curNode.maxMove == null))
				{
					curNode.maxScore = curMoveNode.minScore;
					curNode.maxMove = curMoveNode;
					curNode.isTerminal = curMoveNode.minState.isTerminal;
					if (curNode.maxScore >= 100 && curMoveNode.fullExpanded && curNode.isTerminal) {
						curNode.fullExpanded = true;
						curNode.nextMoves.add(curMoveNode);
						newSeenNodes.clear();
						totalNodesToEnqueue.clear();
						newSeenNodes.add(curMoveNode.minState);
						break;
					}
				}
				
				totalNodesToEnqueue.addAll(curNodesToEnqueue);
				curNode.nextMoves.add(curMoveNode);
			}
			
			seenNodes.addAll(newSeenNodes);
			
			for(StateNode toExpandState : totalNodesToEnqueue)
			{
				fillNodeQueue(toExpandState, seenNodes);
			}
			
			calcStateScore(curNode);
			
			curNode.isExpanded = true;
			checkStateFullExpansion(curNode);
			UpdateStateParents(curNode, seenNodes);
			numMovesExpanded++;
		}
		
	}
	
	public void calcStateScore(StateNode curNode) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		StateMachine curMachine = getStateMachine();
		
		if (curNode.maxMove == null)
		{
			long start = System.currentTimeMillis();
			if (NUM_PROBES == 0) curNode.stateScore = 50;
			else curNode.stateScore = 0;
			MachineState probeState = null;
			for (int i=0; i<NUM_PROBES; i++)
			{
				probeState = curMachine.performDepthCharge(curNode.state, null);
				curNode.stateScore += curMachine.getGoal(probeState, getRole())/NUM_PROBES;
			}
			if (NUM_PROBES > 0) averageDepthTime = (numDepthSamples * averageDepthTime + (System.currentTimeMillis() - start)) / (numDepthSamples + NUM_PROBES);
			numDepthSamples += NUM_PROBES;
		}
		else if (!curNode.isTerminal)
		{
			curNode.stateScore = 0;
			int numLegalMoves = 0;
			for (MoveNode curMoveNode : curNode.nextMoves)
			{
				if (curMoveNode.minState != null)
				{
					int numAdditionalMoves = curMoveNode.minState.isTerminal ? 4 : 1;
					curNode.stateScore = (numLegalMoves*curNode.stateScore + numAdditionalMoves*curMoveNode.minScore) / (numLegalMoves+numAdditionalMoves);
					numLegalMoves += numAdditionalMoves;
				}
			}
			if (numLegalMoves == 0) curNode.stateScore = 50; //Should never be hit as long as we have a maxMove
		}
		else if (curNode.isTerminal)
		{
			//We only want to add in the modifier when we discover a terminal node, not every time.
			//This is what made our player be a jackass, and also what makes it give up so quickly.
			//This current addition will make further losses more beneficial and closer wins more beneficial
			curNode.stateScore = curNode.maxScore - (curNode.maxScore-50.0)/100000.0;
			//Right now, this allows up 1000 moves til the loss. I think thats' enough
		}
	}
	
	
	/*
	//Takes a state node and prunes it's children.
	//Should only be called the first time a stateNode becomes fully expanded
	public void pruneStateNode(StateNode rootNode, HashSet<StateNode> seenNodes)
	{
		if (!rootNode.fullExpanded) return;
		for (MoveNode nextMove : rootNode.nextMoves)
		{
			if (!nextMove.fullExpanded) removeMoveNode(nextMove, seenNodes);
		}
	}
	
	//Should only be called the first time a MoveNode becomes fully expanded
	public void pruneMoveNode(MoveNode rootNode, HashSet<StateNode> seenNodes)
	{
		if (!rootNode.fullExpanded) return;
		for (StateNode nextState : rootNode.nextStates)
		{
			if (!nextState.fullExpanded) removeStateNode(nextState, seenNodes);
		}
	}
	
	//Will not remove cycles, sadly, and any nodes which descend from a cycle.
	public void removeStateNode(StateNode rootNode, HashSet<StateNode> seenNodes)
	{
		if (rootNode.fullExpanded || !seenNodes.contains(rootNode)) return;
		for (MoveNode prevMove : rootNode.prevMoves)
		{
			if (!prevMove.fullExpanded && seenNodes.contains(prevMove.prevState)) return;
		}
		if (!rootNode.isExpanded)
		{
			seenNodes.remove(rootNode);
			nodeQueue.remove(rootNode);
		}
		else
		{
			for (MoveNode nextMove : rootNode.nextMoves)
			{
				removeMoveNode(nextMove, seenNodes);
			}
		}
	}
	
	public void removeMoveNode(MoveNode rootNode, HashSet<StateNode> seenNodes)
	{
		if (rootNode.fullExpanded || !rootNode.prevState.fullExpanded) return;
		for(StateNode nextState : rootNode.nextStates)
		{
			removeStateNode(nextState, seenNodes);
		}
	}*/
	
	public void fillNodeQueue(StateNode rootNode, HashSet<StateNode> seenNodes) 
	{
		seenNodes.add(rootNode);
		if (!rootNode.isExpanded)
		{
			nodeQueue.add(rootNode);
			return;
		}
		else
		{
			if (rootNode.fullExpanded) return;
			for (MoveNode curMove : rootNode.nextMoves)
			{
				if (curMove.fullExpanded) continue;
 				for (StateNode nextState : curMove.nextStates)
				{
					if (!seenNodes.contains(nextState))
					{
						nextState.searchScore = nextState.searchScore - nextState.depth + rootNode.depth + 1;
						nextState.depth = rootNode.depth+1;
						fillNodeQueue(nextState, seenNodes);
					}
				}
			}
		}
	}
	
	public void UpdateStateParents(StateNode curNode, HashSet<StateNode> seenNodes) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		for (MoveNode curMoveNode : curNode.prevMoves)
		{
			//Don't need to trace back up a path if this nodes hasn't been seen
			//WARNING: This can lead to bad things if we every reach a state we thought was unreachable.
			if (!seenNodes.contains(curMoveNode.prevState)) continue;
			//If new score is less than old min score;
			if (curMoveNode.minScore > curNode.stateScore || curMoveNode.minState == null)
			{
				if (curMoveNode.minScore > 0 || !curMoveNode.fullExpanded || curMoveNode.fullExpanded == curNode.fullExpanded)
				{
					curMoveNode.minScore = curNode.stateScore;
					curMoveNode.minState = curNode;
					checkMoveFullExpansion(curMoveNode);
					UpdateMoveParent(curMoveNode, seenNodes);
				}
			}
			else if (curMoveNode.minState != null && curMoveNode.minState.state.equals(curNode.state))
			{
				//Find new min
				curMoveNode.minState = null;
				for(StateNode nextNode : curMoveNode.nextStates)
				{
					if (nextNode.isExpanded &&
						(curMoveNode.minScore > nextNode.stateScore || curMoveNode.minState == null))
					{
						if (curMoveNode.minScore > 0 || !curMoveNode.fullExpanded || curMoveNode.fullExpanded == nextNode.fullExpanded)
						{
							curMoveNode.minScore = nextNode.stateScore;
							curMoveNode.minState = nextNode;
						}
					}
				}
				checkMoveFullExpansion(curMoveNode);
				UpdateMoveParent(curMoveNode, seenNodes);
			}
			else
			{
				checkMoveFullExpansion(curMoveNode);
				UpdateMoveParent(curMoveNode, seenNodes);
			}
		}
	}
	
	public void UpdateMoveParent(MoveNode curMove, HashSet<StateNode> seenNodes) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		StateNode prevState = curMove.prevState;
		if (curMove.minState == null) return;
		else if (curMove.minScore > prevState.maxScore || prevState.maxMove == null)
		{
			if (prevState.maxScore < 100 || !prevState.fullExpanded || prevState.fullExpanded == curMove.fullExpanded)
			{
				prevState.maxScore = curMove.minScore;
				prevState.maxMove = curMove;
				prevState.isTerminal = curMove.minState.isTerminal;
				checkStateFullExpansion(prevState);
				calcStateScore(prevState);
				UpdateStateParents(prevState, seenNodes);
			}
		}
		else if (prevState.maxMove != null && prevState.maxMove.move.equals(curMove.move))
		{
			prevState.maxMove = null;
			for (MoveNode nextMove : prevState.nextMoves)
			{
				if (nextMove.minState != null && 
					(prevState.maxScore < nextMove.minScore || prevState.maxMove == null))
				{
					if (prevState.maxScore < 100 || !prevState.fullExpanded || prevState.fullExpanded == nextMove.fullExpanded)
					{
						prevState.maxScore = nextMove.minScore;
						prevState.maxMove = nextMove;
						prevState.isTerminal = nextMove.minState.isTerminal;
					}
				}
			}
			checkStateFullExpansion(prevState);
			calcStateScore(prevState);
			UpdateStateParents(prevState, seenNodes);
		}
		else {
			checkStateFullExpansion(prevState);
			calcStateScore(prevState); //Just for safety
			UpdateStateParents(prevState, seenNodes);
		}
	}
	
	public void checkStateFullExpansion(StateNode curNode)
	{
		if (curNode.fullExpanded) return;
		if (curNode.maxScore >= 100 && curNode.maxMove.fullExpanded && curNode.isTerminal)
		{
			curNode.fullExpanded = true;
			return;
		}
		else
		{
			for(MoveNode curMove : curNode.nextMoves)
			{
				if (!curMove.fullExpanded) return;
			}
			curNode.fullExpanded = true;
		}
	}
	
	public void checkMoveFullExpansion(MoveNode curNode)
	{
		if (curNode.fullExpanded) return;
		if (curNode.minScore <= 0 && curNode.minState.fullExpanded && curNode.minState.isTerminal)
		{
			curNode.fullExpanded = true;
			return;
		}
		else
		{
			for(StateNode curState : curNode.nextStates)
			{
				if (!curState.fullExpanded) return;
			}
			curNode.fullExpanded = true;
		}
	}
	
	@Override
	public void stateMachineStop() {
		stateMap.clear();
		searchedMoves.clear();
		nodeQueue.clear();
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
		return "MinimaxBFSPruning";
	}

	@Override
	public DetailPanel getDetailPanel() {
		return new ReflexDetailPanel();
	}
	
	public static final int NUM_PROBES = 0;
	
	private long numMovesExpanded;
	private long averageDepthTime;
	private long numDepthSamples;
	private List<Move> searchedMoves;
	private HashMap<MachineState, StateNode> stateMap;
	private PriorityQueue<StateNode> nodeQueue;
}

class StateNode implements Comparable<StateNode>
{
	public StateNode()
	{
		state = null;
		prevMoves = new LinkedList<MoveNode>();
		nextMoves = new LinkedList<MoveNode>();
		maxScore = -1;
		maxMove = null;
		stateScore = 0;
		isTerminal = false;
		searchScore = 0;
		depth = 0;
		isExpanded = false;
		fullExpanded = false;
	}
	
	public int compareTo(StateNode s)
	{
		if (this.searchScore < s.searchScore) return -1;
		else if (this.searchScore > s.searchScore) return 1;
		else return 0;
	}
	
	//What state this represents
	public MachineState state;
	
	//Tree linkages and info
	public List<MoveNode> prevMoves;
	public List<MoveNode> nextMoves;
	public int depth;
	public boolean fullExpanded;
	
	//Best move info
	public double maxScore;
	public MoveNode maxMove;
	public double stateScore;
	public boolean isTerminal;
	
	//PQueue score
	public double searchScore;
	public boolean isExpanded;
}

class MoveNode
{
	public MoveNode()
	{
		move = null;
		prevState = null;
		nextStates = new LinkedList<StateNode>();
		minScore = 101;
		minState = null;
		fullExpanded = false;
	}
	
	//What move this represents
	public Move move;
	
	//Tree linkages
	public StateNode prevState;
	public List<StateNode> nextStates;
	public boolean fullExpanded;
	
	//Best move info
	public double minScore;
	public StateNode minState;	
}