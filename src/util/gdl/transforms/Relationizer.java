package util.gdl.transforms;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import util.gdl.grammar.Gdl;
import util.gdl.grammar.GdlConstant;
import util.gdl.grammar.GdlDistinct;
import util.gdl.grammar.GdlLiteral;
import util.gdl.grammar.GdlNot;
import util.gdl.grammar.GdlOr;
import util.gdl.grammar.GdlPool;
import util.gdl.grammar.GdlRelation;
import util.gdl.grammar.GdlRule;
import util.gdl.grammar.GdlSentence;
import util.gdl.grammar.GdlTerm;
import util.gdl.model.SentenceModel;
import util.gdl.model.SentenceModel.SentenceForm;

public class Relationizer {

	/**
	 * Searches the description for statements that are needlessly treated as
	 * base propositions when they could be expressed as simple relations, and
	 * replaces them with these simpler forms.
	 * 
	 * Some games have been written such that unchanging facts of the game
	 * are listed as base propositions. Often, this is so the fact can be
	 * accessed by a visualization. Gamers usually don't need this distinction,
	 * and can reduce the costs in time and memory of processing the game if
	 * these statements are instead transformed into sentences.
	 */
	public static List<Gdl> run(List<Gdl> description) {
		SentenceModel model = new SentenceModel(description);
		GdlConstant NEXT = GdlPool.getConstant("next");
		
		List<SentenceForm> nextFormsToReplace = new ArrayList<SentenceForm>();
		//Find the update rules for each "true" statement
		for(SentenceForm nextForm : model.getSentenceForms()) {
			if(nextForm.getName().equals(NEXT)) {
				//See if there is exactly one update rule, and it is the persistence rule
				Set<GdlRule> rules = model.getRules(nextForm);
				if(rules.size() == 1) {
					GdlRule rule = rules.iterator().next();
					//Persistence rule: Exactly one literal, the "true" form of the sentence
					if(rule.arity() == 1) {
						GdlLiteral literal = rule.get(0);
						if(literal instanceof GdlRelation) {
							//Check that it really is the true form
							SentenceForm trueForm = nextForm.getCopyWithName("true");
							if(trueForm.matches((GdlRelation) literal)) {
								GdlSentence head = rule.getHead();
								GdlSentence body = (GdlSentence) literal;
								//Check that the tuples are the same, and that they
								//consist of distinct variables
								List<GdlTerm> headTuple = SentenceModel.getTupleFromSentence(head);
								List<GdlTerm> bodyTuple = SentenceModel.getTupleFromSentence(body);
								if(headTuple.equals(bodyTuple)) {
									//Distinct variables?
									Set<GdlTerm> vars = new HashSet<GdlTerm>(headTuple);
									if(vars.size() == headTuple.size()) {
										nextFormsToReplace.add(nextForm);
									}
								}
							}
						}
					}
				}
			}
		}
		
		List<Gdl> newDescription = new ArrayList<Gdl>(description);
		//Now, replace the next forms
		for(SentenceForm nextForm : nextFormsToReplace) {
			SentenceForm initForm = nextForm.getCopyWithName("init");
			SentenceForm trueForm = nextForm.getCopyWithName("true");

			//Go through the rules and relations, making replacements as needed
			for(int i = 0; i < newDescription.size(); i++) {
				Gdl gdl = newDescription.get(i);
				if(gdl instanceof GdlRelation) {
					//Replace initForm
					GdlRelation relation = (GdlRelation) gdl;
					if(initForm.matches(relation)) {
						GdlSentence newSentence = relation.get(0).toSentence();
						newDescription.set(i, newSentence);
					}
				} else if(gdl instanceof GdlRule) {
					GdlRule rule = (GdlRule) gdl;
					//Remove persistence rule (i.e. rule with next form as head)
					GdlSentence head = rule.getHead();
					if(nextForm.matches(head)) {
						newDescription.remove(i);
						i--;
					} else {
						//Replace true in bodies of rules with relation-only form
						List<GdlLiteral> body = rule.getBody();
						List<GdlLiteral> newBody = replaceRelationInBody(body, trueForm);
						if(!body.equals(newBody)) {
							GdlRule newRule = GdlPool.getRule(head, newBody);
							newDescription.set(i, newRule);
						}
					}
				}
			}
		}
		return newDescription;
	}

	private static List<GdlLiteral> replaceRelationInBody(
			List<GdlLiteral> body, SentenceForm trueForm) {
		List<GdlLiteral> newBody = new ArrayList<GdlLiteral>();
		for(GdlLiteral literal : body) {
			newBody.add(replaceRelationInLiteral(literal, trueForm));
		}
		return newBody;
	}

	private static GdlLiteral replaceRelationInLiteral(GdlLiteral literal,
			SentenceForm trueForm) {
		if(literal instanceof GdlSentence) {
			GdlSentence sentence = (GdlSentence) literal;
			if(trueForm.matches(sentence)) {
				//Replace with the sentence contained in the true sentence...
				return sentence.get(0).toSentence();
			} else {
				return literal;
			}
		} else if(literal instanceof GdlNot) {
			GdlNot not = (GdlNot) literal;
			return GdlPool.getNot(replaceRelationInLiteral(not.getBody(), trueForm));
		} else if(literal instanceof GdlOr) {
			GdlOr or = (GdlOr) literal;
			List<GdlLiteral> newOrBody = new ArrayList<GdlLiteral>();
			for(int i = 0; i < or.arity(); i++)
				newOrBody.add(replaceRelationInLiteral(or.get(i), trueForm));
			return GdlPool.getOr(newOrBody);
		} else if(literal instanceof GdlDistinct) {
			return literal;
		} else {
			throw new RuntimeException("Unanticipated GDL literal type "+literal.getClass()+" encountered in Relationizer");
		}
	}
}
