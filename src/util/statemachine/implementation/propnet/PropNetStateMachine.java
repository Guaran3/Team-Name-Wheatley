package util.statemachine.implementation.propnet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import util.gdl.grammar.Gdl;
import util.gdl.grammar.GdlConstant;
import util.gdl.grammar.GdlProposition;
import util.gdl.grammar.GdlRelation;
import util.gdl.grammar.GdlSentence;
import util.gdl.grammar.GdlTerm;
import util.propnet.architecture.Component;
import util.propnet.architecture.PropNet;
import util.propnet.architecture.components.Proposition;
import util.propnet.factory.CachedPropNetFactory;
import util.propnet.factory.OptimizingPropNetFactory;
import util.statemachine.MachineState;
import util.statemachine.Move;
import util.statemachine.Role;
import util.statemachine.StateMachine;
import util.statemachine.exceptions.GoalDefinitionException;
import util.statemachine.exceptions.MoveDefinitionException;
import util.statemachine.exceptions.TransitionDefinitionException;
import util.statemachine.implementation.prover.query.ProverQueryBuilder;

@SuppressWarnings("unused")
public class PropNetStateMachine extends StateMachine {
    /** The underlying proposition network  */
    private PropNet propNet;
    /** The topological ordering of the propositions */
    private List<Proposition> ordering;
    /** The player roles */
    private List<Role> roles;
    
    //saves state information to prevent recomputation....
    private MachineState saved = null;
    
    //we should also save what the propositions are so we don't make them over and over again...
    private Map<GdlTerm, Proposition> inputProps = null;
    private Map<GdlTerm, Proposition> baseProps = null;
    private Proposition initProp = null;
    private Proposition terminalProp = null;
    private Map<Role, Set<Proposition>> legalProps = null;
    private Map<Role, Set<Proposition>> goalProps = null;
    
    
    
    /**
     * Initializes the PropNetStateMachine. You should compute the topological
     * ordering here. Additionally you may compute the initial state here, at
     * your discretion.
     */
    @Override
    public void initialize(List<Gdl> description) {
        propNet = OptimizingPropNetFactory.create(description);
        roles = propNet.getRoles();
        
        inputProps = propNet.getInputPropositions();
        baseProps = propNet.getBasePropositions();
        legalProps = propNet.getLegalPropositions();
        goalProps = propNet.getGoalPropositions();
        
        initProp = propNet.getInitProposition();
        terminalProp = propNet.getTerminalProposition();

        
        
        ordering = getOrdering();
    }    
    
	/**
	 * Computes if the state is terminal. Should return the value
	 * of the terminal proposition for the state.
	 */
	@Override
	public boolean isTerminal(MachineState state) {
		if(saved != state)
				updateState(state, null);
		// TODO: Compute whether the MachineState is terminal.
		return terminalProp.getValue();
	}
	
	/**
	 * Computes the goal for a role in the current state.
	 * Should return the value of the goal proposition that
	 * is true for that role. If there is not exactly one goal
	 * proposition true for that role, then you should throw a
	 * GoalDefinitionException because the goal is ill-defined. 
	 */
	@Override
	public int getGoal(MachineState state, Role role)
	throws GoalDefinitionException {
		if(saved != state)
			updateState(state, null);
		
		Integer goal = null;
		
		for(Proposition prop : goalProps.get(role)) {
			if (prop.getValue()) {
				if (goal != null) {
					throw new GoalDefinitionException(state, role);
				}
				goal = getGoalValue(prop);
			}
		}
		
		if (goal == null)
			throw new GoalDefinitionException(state, role);
		// TODO: Compute the goal for role in state.
		return goal;
	}
	
	/**
	 * Returns the initial state. The initial state can be computed
	 * by only setting the truth value of the INIT proposition to true,
	 * and then computing the resulting state.
	 */
	@Override
	public MachineState getInitialState() {
		saved = null;
		for (Proposition p : inputProps.values()) {
			p.setValue(false);
		}
		for (Proposition p : baseProps.values()) {
			p.setValue(false);
		}

		initProp.setValue(true);

		for (Proposition p : ordering){
			if (p.getInputs().size() == 1) {
				p.setValue(p.getSingleInput().getValue());
			}
		}		
		System.out.println("getInitialState " + getStateFromBase());
		return getStateFromBase();
	}
	
	/**
	 * Computes the legal moves for role in state.
	 */
	@Override
	public List<Move> getLegalMoves(MachineState state, Role role)
	throws MoveDefinitionException {
		// TODO: Compute legal moves.
		if (saved != state)
			updateState(state, null);

		List<Move> moves = new ArrayList<Move>();
		for (Proposition p : legalProps.get(role)) {
			if (p.getValue()) {
				moves.add(getMoveFromProposition(p));
			}			
		}
		return moves;
	}
	
	/**
	 * Computes the next state given state and the list of moves.
	 */
	@Override
	public MachineState getNextState(MachineState state, List<Move> moves)
	throws TransitionDefinitionException {
		updateState(state, moves);		
		return getStateFromBase();
	}
	
	public void updateState(MachineState state, List<Move> moves) {
		//This if condition doesn't seem to improve the efficiency.
		//if (savedState == null || state != savedState) {
			// Set base propositions
			for (Proposition p :baseProps.values()) {
				p.setValue(false);
			}

			for (GdlSentence s : state.getContents()) {
				baseProps.get(s.toTerm()).setValue(true);
			}
		//}

		// Set input propositions
		for (Proposition p : inputProps.values()) {
			p.setValue(false);
		}

		if (moves != null) {			
			List<GdlTerm> does = toDoes(moves);
			for (GdlTerm term : does) {
				Proposition p = inputProps.get(term);
				p.setValue(true);
			}
		}

		initProp.setValue(false);

		// Propagate the values
		for (Proposition p : ordering){
			if (p.getInputs().size() == 1) {
				p.setValue(p.getSingleInput().getValue());
			}
		}

		// When moves = null, clear the cache since it's already one move ahead of the state. 
		if (moves != null)
			saved = null;
		else
			saved = state;
	}
	
	/**
	 * This should compute the topological ordering of propositions.
	 * Each component is either a proposition, logical gate, or transition.
	 * Logical gates and transitions only have propositions as inputs.
	 * 
	 * The base propositions and input propositions should always be exempt
	 * from this ordering.
	 * 
	 * The base propositions values are set from the MachineState that
	 * operations are performed on and the input propositions are set from
	 * the Moves that operations are performed on as well (if any).
	 * 
	 * @return The order in which the truth values of propositions need to be set.
	 */
	public List<Proposition> getOrdering()
	{
	    // List to contain the topological ordering.
	    List<Proposition> order = new LinkedList<Proposition>();
	    				
		// All of the components in the PropNet
		List<Component> components = new ArrayList<Component>(propNet.getComponents());
		
		// All of the propositions in the PropNet.
		List<Proposition> propositions = new ArrayList<Proposition>(propNet.getPropositions());
		
	    // TODO: Compute the topological ordering.		
		Set<Proposition> visited = new HashSet<Proposition>();
		Set<Proposition> unvisited = new HashSet<Proposition>();

		visited.addAll(baseProps.values());
		visited.addAll(inputProps.values());
		visited.add(propNet.getInitProposition()); // not sure if necessary, but shouldn't hurt

		unvisited.addAll(propositions);
		unvisited.removeAll(visited);

		while (!unvisited.isEmpty()) {
			// Pick next proposition whose inputs have all been visited
			Proposition nextProposition = null;
			for (Proposition unvisitedProp : unvisited) {
				// Calculate all propositional inputs of unvisitedProp
				Set<Proposition> inputs = new HashSet<Proposition>();
		        Set<Component> toCheck = new HashSet<Component>();

		        toCheck.add(unvisitedProp);
		        while (!toCheck.isEmpty()) {
		        	Component comp = toCheck.iterator().next();
		        	toCheck.remove(comp);
		        	for (Component input : comp.getInputs()) {
		            	if (input instanceof Proposition)
		            		inputs.add((Proposition) input);
		                else
		                	toCheck.add(input);
		            }
		        }

				if (visited.containsAll(inputs)) {
					nextProposition = unvisitedProp;
					break;
				}
			}

			// Add to visited set and remove from unvisited set
			visited.add(nextProposition);
			unvisited.remove(nextProposition);

			// Add to order
			order.add(nextProposition);
		}

		return order;
	}
	
	/* Already implemented for you */
	@Override
	public Move getMoveFromSentence(GdlSentence sentence) {
		return new PropNetMove(sentence);
	}
	
	/* Already implemented for you */
	@Override
	public MachineState getMachineStateFromSentenceList(
			Set<GdlSentence> sentenceList) {
		return new PropNetMachineState(sentenceList);
	}
	
	/* Already implemented for you */
	@Override
	public Role getRoleFromProp(GdlProposition proposition) {
		return new PropNetRole(proposition);
	}
	
	/* Already implemented for you */
	@Override
	public List<Role> getRoles() {
		return roles;
	}

	/* Helper methods */
		
	/**
	 * The Input propositions are indexed by (does ?player ?action).
	 * 
	 * This translates a list of Moves (backed by a sentence that is simply ?action)
	 * into GdlTerms that can be used to get Propositions from inputPropositions.
	 * and accordingly set their values etc.  This is a naive implementation when coupled with 
	 * setting input values, feel free to change this for a more efficient implementation.
	 * 
	 * @param moves
	 * @return
	 */
	private List<GdlTerm> toDoes(List<Move> moves)
	{
		List<GdlTerm> doeses = new ArrayList<GdlTerm>(moves.size());
		Map<Role, Integer> roleIndices = getRoleIndices();
		
		for (int i = 0; i < roles.size(); i++)
		{
			int index = roleIndices.get(roles.get(i));
			doeses.add(ProverQueryBuilder.toDoes(roles.get(i), moves.get(index)).toTerm());
		}
		return doeses;
	}
	
	/**
	 * Takes in a Legal Proposition and returns the appropriate corresponding Move
	 * @param p
	 * @return a PropNetMove
	 */
	public static Move getMoveFromProposition(Proposition p)
	{
		return new PropNetMove(p.getName().toSentence().get(1).toSentence());
	}
	
	/**
	 * Helper method for parsing the value of a goal proposition
	 * @param goalProposition
	 * @return the integer value of the goal proposition
	 */	
    private int getGoalValue(Proposition goalProposition)
	{
		GdlRelation relation = (GdlRelation) goalProposition.getName().toSentence();
		GdlConstant constant = (GdlConstant) relation.get(1);
		return Integer.parseInt(constant.toString());
	}
	
	/**
	 * A Naive implementation that computes a PropNetMachineState
	 * from the true BasePropositions.  This is correct but slower than more advanced implementations
	 * You need not use this method!
	 * @return PropNetMachineState
	 */	
	public PropNetMachineState getStateFromBase()
	{
		Set<GdlSentence> contents = new HashSet<GdlSentence>();
		for (Proposition p : propNet.getBasePropositions().values())
		{
			p.setValue(p.getSingleInput().getValue());
			if (p.getValue())
			{
				contents.add(p.getName().toSentence());
			}

		}
		return new PropNetMachineState(contents);
	}
}