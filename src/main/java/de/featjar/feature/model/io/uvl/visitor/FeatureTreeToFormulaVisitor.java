/*
 * Copyright (C) 2024 FeatJAR-Development-Team
 *
 * This file is part of FeatJAR-uvl.
 *
 * uvl is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3.0 of the License,
 * or (at your option) any later version.
 *
 * uvl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with uvl. If not, see <https://www.gnu.org/licenses/>.
 *
 * See <https://github.com/FeatureIDE/FeatJAR-uvl> for further information.
 */
package de.featjar.feature.model.io.uvl.visitor;

import de.featjar.base.data.Problem;
import de.featjar.base.data.Result;
import de.featjar.base.tree.visitor.ITreeVisitor;
import de.featjar.feature.model.*;
import de.featjar.feature.model.io.uvl.UVLUtils;
import de.featjar.formula.structure.IFormula;
import de.featjar.formula.structure.connective.And;
import de.featjar.formula.structure.connective.Implies;
import de.featjar.formula.structure.connective.Or;
import de.featjar.formula.structure.predicate.Literal;
import de.featjar.formula.structure.predicate.True;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Converts a {@link IFeatureTree} to an {@link IFormula}.
 *
 * @author Andreas Gerasimow
 */
public class FeatureTreeToFormulaVisitor implements ITreeVisitor<IFeatureTree, IFormula> {

    private HashMap<IFeatureTree, IFormula> formulas = new HashMap<>();
    private IFormula rootFormula;
    List<Problem> problems;

    public FeatureTreeToFormulaVisitor() {
        reset();
    }

    @Override
    public void reset() {
        formulas = new HashMap<>();
        rootFormula = null;
        problems = new ArrayList<>();
    }

    @Override
    public Result<IFormula> getResult() {
        if (rootFormula == null) {
            return Result.empty(problems);
        }
        return Result.of(rootFormula, problems);
    }

    @Override
    public TraversalAction lastVisit(List<IFeatureTree> path) {
        final IFeatureTree node = ITreeVisitor.getCurrentNode(path);
        IFeature feature = node.getFeature();

        Result<String> featureName = feature.getName();
        problems.addAll(featureName.getProblems());
        if (featureName.isEmpty()) {
            problems.add(new Problem("Feature has no name"));
            return TraversalAction.FAIL;
        }

        if (node.getGroups().isEmpty()) {
            problems.add(new Problem(featureName.get() + " has no group."));
            return TraversalAction.FAIL;
        }

        FeatureTree.Group group = node.getGroups().get(0);

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

            if (childrenFormula.getChildren().isEmpty()) {
                currentFormula = new Literal(featureName.get());
            } else if (node.isOptional()) {
                currentFormula = new Implies(new Literal(featureName.get()), childrenFormula);
            } else if (node.isMandatory()) {
                currentFormula = new And(new Literal(featureName.get()), childrenFormula);
            } else {
                problems.add(new Problem(featureName.get() + " is neither an optional nor a mandatory feature."));
                return TraversalAction.FAIL;
            }
        }

        formulas.put(node, currentFormula);
        rootFormula = currentFormula;

        return TraversalAction.CONTINUE;
    }
}
