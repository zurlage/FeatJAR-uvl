package de.featjar.feature.model.io.uvl.visitor;

import de.featjar.base.data.Result;
import de.featjar.base.tree.visitor.ITreeVisitor;
import de.featjar.formula.structure.IExpression;
import de.featjar.formula.structure.formula.IFormula;
import de.featjar.formula.structure.formula.connective.*;
import de.featjar.formula.structure.formula.predicate.Literal;
import de.vill.model.constraint.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FormulaToUVLConstraintVisitor implements ITreeVisitor<IFormula, Constraint> {

    private Map<IExpression, Constraint> uvlConstraints;
    private Constraint rootConstraint;

    public FormulaToUVLConstraintVisitor() {
        reset();
    }

    @Override
    public void reset() {
        uvlConstraints = new HashMap<>();
        rootConstraint = null;
    }

    @Override
    public Result<Constraint> getResult() {
        if (rootConstraint == null) {
            return Result.empty();
        }
        return Result.of(rootConstraint);
    }

    @Override
    public TraversalAction lastVisit(List<IFormula> path) {
        final IFormula node = ITreeVisitor.getCurrentNode(path);

        Constraint constraint;

        if (node.getType() == And.class) {
            constraint = createAndConstraint(node);
        } else if (node.getType() == AtLeast.class) {
            return TraversalAction.FAIL;
        } else if (node.getType() == AtMost.class) {
            return TraversalAction.FAIL;
        } else if (node.getType() == Between.class) {
            return TraversalAction.FAIL;
        } else if (node.getType() == BiImplies.class) {
            constraint = createEquivalenceConstraint(node);
        } else if (node.getType() == Choose.class) {
            return TraversalAction.FAIL;
        } else if (node.getType() == Implies.class) {
            constraint = createImplicationConstraint(node);
        } else if (node.getType() == Not.class) {
            constraint = createNotConstraint(node);
        } else if (node.getType() == Or.class) {
            constraint = createOrConstraint(node);
        } else if (node.getType() == Reference.class) {
            return TraversalAction.CONTINUE;
        } else if (node.getType() == Literal.class) {
            constraint = createLiteralConstraint(node);
        } else {
            return TraversalAction.FAIL;
        }
        if (constraint == null) return TraversalAction.FAIL;
        uvlConstraints.put(node, constraint);
        rootConstraint = constraint;
        return TraversalAction.CONTINUE;
    }

    private Constraint createLiteralConstraint(IFormula node) {
        Literal literal = (Literal) node;
        if (!node.getChildren().isEmpty()) {
            if (literal.isPositive()) {
                return new LiteralConstraint(literal.getChildren().get(0).getName());
            } else {
                return new NotConstraint(
                        new LiteralConstraint(literal.getChildren().get(0).getName()));
            }
        }
        return null;
    }

    private EquivalenceConstraint createEquivalenceConstraint(IFormula node) {
        if (node.getChildren().size() > 1) {
            return new EquivalenceConstraint(
                    uvlConstraints.get(node.getChildren().get(0)),
                    uvlConstraints.get(node.getChildren().get(1)));
        }
        return null;
    }

    private ImplicationConstraint createImplicationConstraint(IFormula node) {
        if (node.getChildren().size() > 1) {
            return new ImplicationConstraint(
                    uvlConstraints.get(node.getChildren().get(0)),
                    uvlConstraints.get(node.getChildren().get(1)));
        }
        return null;
    }

    private NotConstraint createNotConstraint(IFormula node) {
        if (!node.getChildren().isEmpty()) {
            return new NotConstraint(uvlConstraints.get(node.getChildren().get(0)));
        }
        return null;
    }

    private Constraint createAndConstraint(IFormula node) {
        List<Constraint> constraints = node.getChildren().stream()
                .map((child) -> uvlConstraints.get(child))
                .collect(Collectors.toList());
        return createAndConstraint(constraints);
    }

    private Constraint createAndConstraint(List<Constraint> constraints) {
        if (constraints.size() == 2) {
            return new AndConstraint(constraints.get(0), constraints.get(1));
        } else if (constraints.size() > 2) {
            AndConstraint andConstraint = new AndConstraint(constraints.get(0), constraints.get(1));

            for (int i = 2; i < constraints.size(); i++) {
                andConstraint = new AndConstraint(andConstraint, constraints.get(i));
            }
            return andConstraint;
        } else if (constraints.size() == 1) {
            return constraints.get(0);
        }
        return null;
    }

    private Constraint createOrConstraint(IFormula node) {
        List<Constraint> constraints = node.getChildren().stream()
                .map((child) -> uvlConstraints.get(child))
                .collect(Collectors.toList());
        return createOrConstraint(constraints);
    }

    private Constraint createOrConstraint(List<Constraint> constraints) {
        if (constraints.size() == 2) {
            return new OrConstraint(constraints.get(0), constraints.get(1));
        } else if (constraints.size() > 2) {
            OrConstraint orConstraint = new OrConstraint(constraints.get(0), constraints.get(1));

            for (int i = 2; i < constraints.size(); i++) {
                orConstraint = new OrConstraint(orConstraint, constraints.get(i));
            }
            return orConstraint;
        } else if (constraints.size() == 1) {
            return constraints.get(0);
        }
        return null;
    }
}
