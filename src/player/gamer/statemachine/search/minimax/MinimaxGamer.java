package player.gamer.statemachine.search.minimax;

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
public final class MinimaxGamer extends StateMachineGamer
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
		stateMap = new HashMap<MachineState, StateNode>();
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
		if (rootNode.maxScore >= 100) System.out.println("Victory is ours!");
		if (rootNode.maxScore <= 0 ) System.out.println("defeat...");
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
		HashSet<StateNode> seenNodes = new HashSet<StateNode>();
		fillNodeQueue(rootNode, seenNodes);
		
		//Execute BFS
		while (!nodeQueue.isEmpty())
		{
			if (System.currentTimeMillis() > timeout - (150 + 1.5 * NUM_PROBES * averageDepthTime)) break;
			StateNode curNode = nodeQueue.remove();
			List<Move> possMoves = curMachine.getLegalMoves(curNode.state, curRole);
			
			//Look at all possible moves from a state
			for (Move curMove : possMoves)
			{
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
							/*if (curMoveNode.minScore == 0 && nextNode.isTerminal) {
								seenNodes.add(nextNode);
								curMoveNode.nextStates.add(nextNode);
								break;
							}*/
						}
						curMoveNode.nextStates.add(nextNode);
						if (!seenNodes.contains(nextNode))
						{
							if (!nextNode.isExpanded)
							{
								nextNode.depth = curNode.depth+1;
								nextNode.searchScore = nextNode.depth;
								nodeQueue.add(nextNode);
							}
							else //Add everything nextNode needs to expand
							{
								fillNodeQueue(nextNode, seenNodes);
							}
							seenNodes.add(nextNode);
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
							nodeQueue.add(nextNode);
						}
						else 
						{
							//Is terminal, set the score.
							nextNode.isExpanded = true;
							nextNode.isTerminal = true;
							nextNode.searchScore = nextNode.depth;
							nextNode.maxScore = curMachine.getGoal(nextState, curRole);
							nextNode.stateScore = nextNode.maxScore + (nextNode.maxScore - 50.0)/100.0;
							//Update curMove
							if (nextNode.stateScore < curMoveNode.minScore || curMoveNode.minState == null)
							{
								curMoveNode.minScore = nextNode.stateScore;
								curMoveNode.minState = nextNode;
								/*if (curMoveNode.minScore == 0) {
									stateMap.put(nextState, nextNode);
									seenNodes.add(nextNode);
									curMoveNode.nextStates.add(nextNode);
									break;
								}*/
							}
						}
						
						//Add to Map and Queue
						stateMap.put(nextState, nextNode);
						seenNodes.add(nextNode);
						
						curMoveNode.nextStates.add(nextNode);
					}
				}
				//Add curMoveNode to curNode
				if (curMoveNode.minState != null && 
					(curMoveNode.minScore > curNode.maxScore || curNode.maxMove == null))
				{
					curNode.maxScore = curMoveNode.minScore;
					curNode.maxMove = curMoveNode;
					curNode.isTerminal = curMoveNode.minState.isTerminal;
					/*if (curNode.maxScore == 100 ) {
						curNode.nextMoves.add(curMoveNode);
						break;
					}*/
				}
				curNode.nextMoves.add(curMoveNode);
			}
			
			calcStateScore(curNode);
			
			curNode.isExpanded = true;
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
					curNode.stateScore = (numLegalMoves*curNode.stateScore + curMoveNode.minScore) / (numLegalMoves+1.0);
					numLegalMoves++;
				}
			}
			if (numLegalMoves == 0) curNode.stateScore = 50; //Should never be hit as long as we have a maxMove
		}
		else if (curNode.isTerminal)
		{
			curNode.stateScore = curNode.maxScore + (curNode.maxScore-50.0)/100.0;
		}
	}
	
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
			//if (rootNode.maxScore == 100 && rootNode.isTerminal) return;
			for (MoveNode curMove : rootNode.nextMoves)
			{
				//if (curMove.minScore == 0 && curMove.minState.isTerminal) continue;
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
				curMoveNode.minScore = curNode.stateScore;
				curMoveNode.minState = curNode;
				UpdateMoveParent(curMoveNode, seenNodes);
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
						curMoveNode.minScore = nextNode.stateScore;
						curMoveNode.minState = nextNode;
					}
				}
				UpdateMoveParent(curMoveNode, seenNodes);
			}
		}
	}
	
	public void UpdateMoveParent(MoveNode curMove, HashSet<StateNode> seenNodes) throws TransitionDefinitionException, MoveDefinitionException, GoalDefinitionException
	{
		StateNode prevState = curMove.prevState;
		if (curMove.minState != null && 
				(curMove.minScore > prevState.maxScore || prevState.maxMove == null))
		{
			prevState.maxScore = curMove.minScore;
			prevState.maxMove = curMove;
			prevState.isTerminal = curMove.minState.isTerminal;
			calcStateScore(prevState);
			UpdateStateParents(prevState, seenNodes);
		}
		else if (prevState.maxMove != null && prevState.maxMove.move.equals(curMove.move))
		{
			prevState.maxMove = null;
			for (MoveNode nextMove : prevState.nextMoves)
			{
				if (nextMove.minState != null && 
					(prevState.maxScore < nextMove.minScore || prevState.maxMove == null))
				{
					prevState.maxScore = nextMove.minScore;
					prevState.maxMove = nextMove;
					prevState.isTerminal = nextMove.minState.isTerminal;
				}
			}
			calcStateScore(prevState);
			UpdateStateParents(prevState, seenNodes);
		}
		else {
			double oldScore = prevState.stateScore;
			calcStateScore(prevState); //Just for safety
			if (oldScore != prevState.stateScore) UpdateStateParents(prevState, seenNodes);
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
		return "MinimaxDFS";
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
	}
	
	//What move this represents
	public Move move;
	
	//Tree linkages
	public StateNode prevState;
	public List<StateNode> nextStates;
	
	//Best move info
	public double minScore;
	public StateNode minState;	
}