package util.gdl.transforms;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import util.gdl.grammar.Gdl;
import util.gdl.grammar.GdlConstant;
import util.gdl.grammar.GdlDistinct;
import util.gdl.grammar.GdlLiteral;
import util.gdl.grammar.GdlNot;
import util.gdl.grammar.GdlPool;
import util.gdl.grammar.GdlRelation;
import util.gdl.grammar.GdlRule;
import util.gdl.grammar.GdlSentence;
import util.gdl.grammar.GdlTerm;
import util.gdl.grammar.GdlVariable;
import util.gdl.model.SentenceModel;
import util.gdl.model.SentenceModel.SentenceForm;
import util.gdl.transforms.ConstantFinder.ConstantChecker;
import util.propnet.factory.Assignments;

public class CondensationIsolator {

	public static List<Gdl> run(List<Gdl> description,
			boolean restrained,
			boolean moreRestrained,
			boolean ignoreConstants,
			boolean constConstraint,
			boolean useHeuristic,
			boolean analyticFunctionOrdering) {
		//This class is not put together in any "optimal" way, so it's left in
		//an unpolished state for now. A better version would use estimates of
		//the impact of breaking apart rules. (It also needs to stop itself from
		//making multiple new relations with the same meaning.)
		
		//This version will be rather advanced.
		//In particular, it will try to incorporate
		//1) More thorough scanning for condensations;
		//2) Condensations that are only safe to perform because of mutexes.
		
		//TODO: Don't perform condensations on stuff like (add _ _ _)...
		//In general, don't perform condensations where the headroom is huge?
		//Better yet... DON'T perform condensations on recursive functions!
		//As for headroom... maybe make sure that # of vars eliminated > # "kept"
		//Or make sure none are kept? Use directional connected components?
		
		description = GdlCleaner.run(description);
		description = DeORer.run(description);
		description = VariableConstrainer.replaceFunctionValuedVariables(description);
		
		//How do we define a condensation, and what needs to be true in it?
		//Definition: A condensation set is a set of conjuncts of a
		//sentence.
		//Restrictions:
		//1) There must be some variable not in the head of the sentence that
		//   appears exclusively in the condensation set. (This means we can
		//   easily find sets one of which must be a condensation set.)
		//2) For any variable appearing in a distinct or not conjunct in the set,
		//   there must be a positive conjunct in the set also containing that
		//   variable. This does apply to variables found in the head.
		//3) There must be at least one non-distinct literal outside the
		//   condensation set.
		
		//How mutexes work:
		//Say we have a rule
		//  (<= (r1 ?b)
		//      (r2 ?a ?b ?c)
		//      (r3 ?b ?c)
		//		(r4 ?a)
		//		(r5 ?c))
		//If we wanted to factor out ?a, we'd normally have to do
		/*  (<= (r6 ?b ?c)
		 * 		(r2 ?a ?b ?c)
		 * 		(r4 ?a))
		 *  (<= (r1 ?b)
		 * 		(r6 ?b ?c)
		 * 		(r3 ?b ?c)
		 * 		(r5 ?c))
		 * But if we know r2 is a mutex, instead we can do (notice r2 splitting):
		 *  (<= (r6 ?b)
		 * 		(r2 ?a ?b ?c)
		 * 		(r4 ?a))
		 *  (<= (r1 ?b)
		 *  	(r2 ?a ?b ?c)
		 *  	(r6 ?b)
		 *  	(r3 ?b ?c)
		 *  	(r5 ?c))
		 * Which in turn becomes:
		 *  (<= (r6 ?b)
		 * 		(r2 ?a ?b ?c)
		 * 		(r4 ?a))
		 *  (<= (r7 ?b)
		 *  	(r2 ?a ?b ?c)
		 *  	(r3 ?b ?c)
		 *  	(r5 ?c))
		 *  (<= (r1 ?b)
		 *  	(r6 ?b)
		 *		(r7 ?b))
		 * Both r6 and r7 can be further condensed to ignore ?c and ?a,
		 * respectively. What just happened?
		 * 1) The condensation set for ?a included the mutex r2.
		 * 2) r2 (by itself) would have required ?c to be included as an
		 *    argument passed back to the original rule, which is undesirable.
		 *    Instead, as it's a mutex, we leave a copy in the original rule
		 *    and don't include the ?c.
		 *    
		 * So, what kind of algorithm can we find to solve this task?
		 */
		List<Gdl> newDescription = new ArrayList<Gdl>();
		Queue<GdlRule> rulesToAdd = new LinkedList<GdlRule>();
		
		for(Gdl gdl : description) {
			if(gdl instanceof GdlRule)
				rulesToAdd.add((GdlRule) gdl);
			else
				newDescription.add(gdl);
		}
		
		//Don't use the model indiscriminately; it reflects the old description,
		//not necessarily the new one
		SentenceModel model = new SentenceModel(description);
		ConstantChecker checker = ConstantFinder.getConstants(description);
		Set<String> sentenceNames = new HashSet<String>(model.getSentenceNames());
		Set<SentenceForm> constantForms = model.getConstantSentenceForms();
		
		//Collection<SentenceForm> constantForms = model;
		while(!rulesToAdd.isEmpty()) {
			GdlRule curRule = rulesToAdd.remove();
			if(isRecursive(curRule)) {
				//Don't mess with it!
				newDescription.add(curRule);
				continue;
			}
			GdlSentence curRuleHead = curRule.getHead();
			if(ignoreConstants && SentenceModel.inSentenceFormGroup(curRuleHead, constantForms)) {
				newDescription.add(curRule);
				continue;
			}
			Set<GdlLiteral> condensationSet = getCondensationSet(curRule, model, checker, restrained, moreRestrained, constConstraint, constantForms, useHeuristic, analyticFunctionOrdering);
			if(condensationSet != null) {
				List<GdlRule> newRules = applyCondensation(condensationSet, curRule, sentenceNames); 
				rulesToAdd.addAll(newRules);
				//Since we're making only small changes, we can readjust
				//the model as we go, instead of recomputing it
				List<GdlRule> oldRules = Collections.singletonList(curRule);
				model.replaceRules(oldRules, newRules);
				checker.replaceRules(oldRules, newRules);
			} else {
				newDescription.add(curRule);
			}
		}
		return newDescription;
	}

	@SuppressWarnings("unused")
	private static void saveKif(List<Gdl> description) {
		//Save the description in a new file
		//Useful for debugging chains of condensations to see
		//which cause decreased performance
		String filename = "ci0.kif";
		int filenum = 0;
		File file = null;
		while(file == null || file.exists()) {
			filenum++;
			filename = "ci" + filenum + ".kif";
			file = new File(filename);
			file = new File("games/rulesheets", filename);
		}
		BufferedWriter out = null;
		try {
			out = new BufferedWriter(new FileWriter(file));
			for(Gdl gdl : description) {
				out.append(gdl.toString() + "\n");
			}
		} catch(IOException e) {
			e.printStackTrace();
		} finally {
			try {
				out.close();
			} catch (IOException e) {}
			catch (NullPointerException e) {}
		}
	}

	private static boolean isRecursive(GdlRule rule) {
		for(GdlLiteral literal : rule.getBody())
			if(literal instanceof GdlSentence)
				if(((GdlSentence) literal).getName().equals(rule.getHead().getName()))
					//A good approximation
					return true;
		return false;
	}

	private static List<GdlRule> applyCondensation(
			Set<GdlLiteral> condensationSet, GdlRule rule,
			Set<String> sentenceNames) {

		Set<GdlVariable> varsInCondensationSet = new HashSet<GdlVariable>();
		for(GdlLiteral literal : condensationSet)
			varsInCondensationSet.addAll(SentenceModel.getVariables(literal));
		Set<GdlVariable> varsToKeep = new HashSet<GdlVariable>();
		//Which vars do we "keep" (put in our new condensed literal)?
		//Vars that are both:
		//1) In the condensation set, in a non-mutex literal
		//2) Either in the head or somewhere else outside the condensation set
		for(GdlLiteral literal : condensationSet)
			varsToKeep.addAll(SentenceModel.getVariables(literal));
		Set<GdlVariable> varsToKeep2 = new HashSet<GdlVariable>();
		varsToKeep2.addAll(SentenceModel.getVariables(rule.getHead()));
		for(GdlLiteral literal : rule.getBody())
			if(!condensationSet.contains(literal))
				varsToKeep2.addAll(SentenceModel.getVariables(literal));
		varsToKeep.retainAll(varsToKeep2);
		
		//Now we're ready to split it apart
		//Let's make the new rule
		List<GdlTerm> orderedVars = new ArrayList<GdlTerm>(varsToKeep);
		GdlConstant condenserName;
		for(int i = 0; ; i++) {
			String candidateName = rule.getHead().getName().getValue() + "_tmp" + i;
			if(!sentenceNames.contains(candidateName)) {
				condenserName = GdlPool.getConstant(candidateName);
				sentenceNames.add(candidateName);
				//Success!
				break;
			}
		}
		//Make the rule head
		GdlSentence condenserHead;
		if(orderedVars.isEmpty()) {
			condenserHead = GdlPool.getProposition(condenserName);
		} else {
			condenserHead = GdlPool.getRelation(condenserName, orderedVars);
		}
		List<GdlLiteral> condenserBody = new ArrayList<GdlLiteral>(condensationSet);
		GdlRule condenserRule = GdlPool.getRule(condenserHead, condenserBody);
		//TODO: Look for existing rules matching the new one
		
		List<GdlLiteral> remainingLiterals = new ArrayList<GdlLiteral>();
		for(GdlLiteral literal : rule.getBody())
			if(!condensationSet.contains(literal))
				remainingLiterals.add(literal);

		remainingLiterals.add(condenserHead);
		GdlRule modifiedRule = GdlPool.getRule(rule.getHead(), remainingLiterals);
		
		List<GdlRule> newRules = new ArrayList<GdlRule>(2);
		newRules.add(condenserRule);
		newRules.add(modifiedRule);
		return newRules;
	}

	private static Set<GdlLiteral> getCondensationSet(GdlRule rule,
			SentenceModel model,
			ConstantChecker checker,
			boolean restrained, boolean moreRestrained,
			boolean constConstraint, Set<SentenceForm> constantForms,
			boolean useHeuristic,
			boolean analyticFunctionOrdering) {
		//We use each variable as a starting point
		List<GdlVariable> varsInRule = SentenceModel.getVariables(rule);
		List<GdlVariable> varsInHead = SentenceModel.getVariables(rule.getHead());
		List<GdlVariable> varsNotInHead = new ArrayList<GdlVariable>(varsInRule);
		varsNotInHead.removeAll(varsInHead);

		for(GdlVariable var : varsNotInHead) {
			Set<GdlLiteral> minSet = new HashSet<GdlLiteral>();
			for(GdlLiteral literal : rule.getBody())
				if(SentenceModel.getVariables(literal).contains(var))
					minSet.add(literal);
			
			//#1 is already done
			//Now we try #2
			Set<GdlVariable> varsNeeded = new HashSet<GdlVariable>();
			Set<GdlVariable> varsSupplied = new HashSet<GdlVariable>();
			for(GdlLiteral literal : minSet)
				if(literal instanceof GdlRelation)
					varsSupplied.addAll(SentenceModel.getVariables(literal));
				else if(literal instanceof GdlDistinct || literal instanceof GdlNot)
					varsNeeded.addAll(SentenceModel.getVariables(literal));
			varsNeeded.removeAll(varsSupplied);
			if(restrained && !varsNeeded.isEmpty())
				continue;

			List<Set<GdlLiteral>> candidateSuppliersList = new ArrayList<Set<GdlLiteral>>();
			for(GdlVariable varNeeded : varsNeeded) {
				Set<GdlLiteral> suppliers = new HashSet<GdlLiteral>();
				for(GdlLiteral literal : rule.getBody())
					if(literal instanceof GdlRelation)
						if(SentenceModel.getVariables(literal).contains(varNeeded))
							suppliers.add(literal);
				candidateSuppliersList.add(suppliers);
			}

			//TODO: Now... I'm not sure if we want to minimize the number of
			//literals added, or the number of variables added
			//Right now, I don't have time to worry about optimization
			//Currently, we pick one at random
			//TODO: Optimize this
			Set<GdlLiteral> literalsToAdd = new HashSet<GdlLiteral>();
			for(Set<GdlLiteral> suppliers : candidateSuppliersList)
				if(Collections.disjoint(suppliers, literalsToAdd))
					literalsToAdd.add(suppliers.iterator().next());
			minSet.addAll(literalsToAdd);

			if(!useHeuristic && goodCondensationSet(minSet, rule, moreRestrained, constConstraint, constantForms))
				return minSet;

			if(useHeuristic && goodCondensationSetByHeuristic(minSet, rule, model, checker, analyticFunctionOrdering))
				return minSet;
			
		}
		return null;
	}

	private static boolean goodCondensationSetByHeuristic(
			Set<GdlLiteral> minSet, GdlRule rule, SentenceModel model,
			ConstantChecker checker, boolean analyticFunctionOrdering) {
		//We actually want the sentence model here so we can see the domains
		//also, if it's a constant, ...
		//Anyway... we want to compare the heuristic for the number of assignments
		//and/or links that will be generated with or without the condensation set
		//Heuristic for a rule is A*(L+1), where A is the number of assignments and
		//L is the number of literals, unless L = 1, in which case the heuristic is
		//just A. This roughly captures the number of links that would be generated
		//if this rule were to be generated.
		//Obviously, there are differing degrees of accuracy with which we can
		//represent A.
		//One way is taking the product of all the variables in all the domains.
		//However, we can do better by actually asking the Assignments class for
		//its own heuristic of how it would implement the rule as-is.
		//The only tricky aspect here is that we need an up-to-date SentenceModel,
		//and in some cases this could be expensive to compute. Might as well try
		//it, though...

		//Heuristic for the rule as-is:

		long assignments = Assignments.getNumAssignmentsEstimate(rule, model, checker, analyticFunctionOrdering);
		int literals = rule.arity();
		if(literals > 1)
			literals++; //We have to "and" the literals together
		//Note that even though constants will be factored out, we're concerned here
		//with getting through them in a reasonable amount of time, so we do want to
		//count them. TODO: Not sure if they should be counted in L, though...
		long curRuleHeuristic = assignments * literals;
		//And if we split them up...
		//We actually need the sentence names here
		Set<String> sentenceNames = model.getSentenceNames();
		List<GdlRule> newRules = applyCondensation(minSet, rule, sentenceNames);
		GdlRule r1 = newRules.get(0), r2 = newRules.get(1);

		//Copy and modify the model
		List<GdlRule> oldRules = Collections.singletonList(rule);
		SentenceModel newModel = new SentenceModel(model);
		newModel.replaceRules(Collections.singletonList(rule), newRules);

		ConstantChecker newChecker = new ConstantChecker(checker);
		newChecker.replaceRules(oldRules, newRules);
		long a1 = Assignments.getNumAssignmentsEstimate(r1, newModel, newChecker, analyticFunctionOrdering);
		long a2 = Assignments.getNumAssignmentsEstimate(r2, newModel, newChecker, analyticFunctionOrdering);
		int l1 = r1.arity(); if(l1 > 1) l1++;
		int l2 = r2.arity(); if(l2 > 1) l2++;

		//Whether we split or not depends on what the two heuristics say
		long newRulesHeuristic = a1 * l1 + a2 * l2;
		return newRulesHeuristic < curRuleHeuristic;
	}

	private static boolean goodCondensationSet(Set<GdlLiteral> condensationSet, GdlRule rule,
			boolean moreRestrained, boolean constConstraint, Set<SentenceForm> constantForms) {
		List<GdlVariable> varsInHead = SentenceModel.getVariables(rule.getHead());
		if(moreRestrained) {
			//How many non-head variables will we need to "keep"?
			//If fewer than the number we're eliminating, don't bother
			//Alternative: If any, don't bother
			//TODO: Try factoring in domain sizes?
			Set<GdlVariable> varsToKeep = new HashSet<GdlVariable>();
			Set<GdlVariable> varsEliminated = new HashSet<GdlVariable>();
			for(GdlLiteral literal : rule.getBody())
				if(!condensationSet.contains(literal))
					varsToKeep.addAll(SentenceModel.getVariables(literal));
				else
					varsEliminated.addAll(SentenceModel.getVariables(literal));
			varsEliminated.removeAll(varsInHead);
			varsToKeep.retainAll(varsEliminated);
			varsEliminated.removeAll(varsToKeep);
			//TODO: Was this supposed to be continue?
			if(varsToKeep.size() >= varsEliminated.size())
				return false;
		}
		if(constConstraint) {
			//Here's a problem case with this example:
			//( <= ( next_tmp10 ?xAny ?yAny ) ( does ?a1 ( jump ?x1 ?y1 ?a4 ?a2 ?a3 ?a0 ) ) ( distinctCell ?xAny ?yAny ?x1 ?y1 ) )
			//We want this to be broken up
			//We want the "does" to be simplified before use
			//because otherwise we can't iterate over all the possibilities in time
			//What is the relevant exception here that makes this case different?
			//Maybe that we aren't adding any variables?
			
			//Check to see that we have a non-constant literal on each
			//side of the proposed division
			//Does each set contain a "live" non-constant sentence?
			boolean inLive = false, outLive = false;
			for(GdlLiteral l : rule.getBody()) {
				GdlSentence sentence;
				if(l instanceof GdlSentence)
					sentence = (GdlSentence) l;
				else if(l instanceof GdlNot)
					sentence = (GdlSentence) ((GdlNot) l).getBody();
				else
					continue; //the inner loop
				if(condensationSet.contains(l)) {
					if(!SentenceModel.inSentenceFormGroup(sentence, constantForms)) {
						inLive = true;
					}
				} else {
					if(!SentenceModel.inSentenceFormGroup(sentence, constantForms)) {
						outLive = true;
					}
				}
			}
			if(!inLive || !outLive) {
				
				return false;
			}
		}
		
		//Finally, check #3
		for(GdlLiteral literal : rule.getBody()) {
			if(literal instanceof GdlRelation || literal instanceof GdlNot)
				if(!condensationSet.contains(literal))
					return true;
		}
		return false;
	}

}
