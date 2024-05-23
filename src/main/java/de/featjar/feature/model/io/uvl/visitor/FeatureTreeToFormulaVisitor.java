package de.featjar.feature.model.io.uvl.visitor;

import de.featjar.base.data.Problem;
import de.featjar.base.data.Result;
import de.featjar.base.tree.visitor.ITreeVisitor;
import de.featjar.feature.model.FeatureTree;
import de.featjar.feature.model.IFeature;
import de.featjar.feature.model.IFeatureTree;
import de.featjar.feature.model.io.uvl.UVLUtils;
import de.featjar.formula.structure.formula.IFormula;
import de.featjar.formula.structure.formula.connective.And;
import de.featjar.formula.structure.formula.connective.Implies;
import de.featjar.formula.structure.formula.connective.Or;
import de.featjar.formula.structure.formula.predicate.Literal;
import de.featjar.formula.structure.formula.predicate.True;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;

public class FeatureTreeToFormulaVisitor implements ITreeVisitor<IFeatureTree, IFormula> {

    // private Stack<IFormula> formulaStack;
    private HashMap<IFeatureTree, IFormula> formulas = new HashMap<>();
    private IFormula rootFormula;
    List<Problem> problems;

    public FeatureTreeToFormulaVisitor() {
        reset();
    }

    @Override
    public void reset() {
        // formulaStack = new Stack<>();
        formulas = new HashMap<>();
        rootFormula = null;
        problems = new ArrayList<>();
    }

    @Override
    public Result<IFormula> getResult() {
        // return Result.of(formulaStack.peek());
        if (rootFormula == null) {
            return Result.empty(problems);
        }
        return Result.of(rootFormula, problems);
    }

    @Override
    public TraversalAction lastVisit(List<IFeatureTree> path) {
        final IFeatureTree node = ITreeVisitor.getCurrentNode(path);
        IFeature feature = node.getFeature();
        FeatureTree.Group group = node.getGroup();

        // IFormula[] children = new IFormula[node.getChildren().size()];
        // for (int i = 0; i < node.getChildren().size(); i++) {
        //     children[i] = formulaStack.pop();
        // }

        Result<String> featureName = feature.getName();
        problems.addAll(featureName.getProblems());
        if (featureName.isEmpty()) {
            problems.add(new Problem("Feature has no name"));
            return TraversalAction.FAIL;
        }

        IFormula currentFormula;

        if (node.getChildren().isEmpty()) { // is leaf node
            if (node.isOptional()) {
                currentFormula = True.INSTANCE;
            } else if (node.isMandatory()) {
                currentFormula = new Literal(featureName.get());
            } else {
                problems.add(new Problem(featureName.get() + " is neither an optional nor a mandatory feature."));
                return TraversalAction.FAIL;
            }
        } else { // node has children
            IFormula childrenFormula;
            if (group.isAlternative()) {
                IFormula[] children = node.getChildren().stream()
                        .map((child) -> formulas.get(child))
                        .toArray(IFormula[]::new);
                childrenFormula = UVLUtils.nchoosek(children, 1, false);
            } else if (group.isOr()) {
                IFormula[] children = node.getChildren().stream()
                        .map((child) -> formulas.get(child))
                        .toArray(IFormula[]::new);
                childrenFormula = new Or(children);
            } else if (group.isAnd()) {
                IFormula[] children = node.getChildren().stream()
                        .filter(IFeatureTree::isMandatory) // filter mandatory only
                        .map((child) -> formulas.get(child))
                        .toArray(IFormula[]::new);
                childrenFormula = new And(children);
            } else {
                problems.add(new Problem(featureName.get() + " has no group."));
                return TraversalAction.FAIL;
            }

            if (node.isOptional()) {
                currentFormula = new Implies(new Literal(featureName.get()), childrenFormula);
            } else if (node.isMandatory()) {
                currentFormula = new And(new Literal(featureName.get()), childrenFormula);
            } else {
                problems.add(new Problem(featureName.get() + " is neither an optional nor a mandatory feature."));
                return TraversalAction.FAIL;
            }
        }

        // formulaStack.add(currentFormula);
        formulas.put(node, currentFormula);
        rootFormula = currentFormula;

        return TraversalAction.CONTINUE;
    }
}
