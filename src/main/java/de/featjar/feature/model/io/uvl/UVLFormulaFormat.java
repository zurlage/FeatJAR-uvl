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
package de.featjar.feature.model.io.uvl;

import de.featjar.base.data.Problem;
import de.featjar.base.data.Result;
import de.featjar.base.io.format.IFormat;
import de.featjar.base.io.input.AInputMapper;
import de.featjar.base.tree.Trees;
import de.featjar.feature.model.IFeatureModel;
import de.featjar.feature.model.IFeatureTree;
import de.featjar.feature.model.io.uvl.visitor.FeatureTreeToFormulaVisitor;
import de.featjar.feature.model.io.uvl.visitor.FormulaToUVLConstraintVisitor;
import de.featjar.formula.structure.IFormula;
import de.featjar.formula.structure.connective.And;
import de.vill.main.UVLModelFactory;
import de.vill.model.Attribute;
import de.vill.model.Feature;
import de.vill.model.Group;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses and writes formulas from and to UVL files.
 *
 * @author Sebastian Krieter
 * @author Andreas Gerasimow
 */
public class UVLFormulaFormat implements IFormat<IFormula> {

    public static final String ROOT_FEATURE_NAME = "Formula";

    @Override
    public Result<IFormula> parse(AInputMapper inputMapper) {
        List<Problem> problems = new ArrayList<>();
        try {
            String content = inputMapper.get().text();
            UVLModelFactory uvlModelFactory = new UVLModelFactory();
            de.vill.model.FeatureModel uvlModel = uvlModelFactory.parse(content);
            IFeatureModel featureModel = UVLUtils.createFeatureModel(uvlModel);

            if (featureModel.getRoots().isEmpty()) {
                problems.add(new Problem("No root features exist.", Problem.Severity.ERROR));
                return Result.empty(problems);
            }

            IFeatureTree rootFeature = featureModel.getRoots().get(0);
            problems.add(new Problem(
                    "UVL supports only one root feature. If there are more than one root features in the model, the first one will be used.",
                    Problem.Severity.WARNING));

            Result<IFormula> result = Trees.traverse(rootFeature, new FeatureTreeToFormulaVisitor());

            if (result.isEmpty()) {
                problems.addAll(result.getProblems());
                return Result.empty(problems);
            }

            List<IFormula> formulas = UVLUtils.uvlConstraintToFormula(uvlModel.getConstraints());
            formulas.add(result.get());
            IFormula formula = new And(formulas);

            return Result.of(formula, problems);
        } catch (Exception e) {
            return Result.empty(e);
        }
    }

    @Override
    public Result<String> serialize(IFormula formula) {
        List<Problem> problems = new ArrayList<>();

        de.vill.model.FeatureModel uvlModel = new de.vill.model.FeatureModel();
        uvlModel.setNamespace("formula");
        de.vill.model.Feature uvlRootFeature = new Feature(ROOT_FEATURE_NAME);
        uvlRootFeature.getAttributes().put("abstract", new Attribute<>("abstract", true));
        uvlModel.setRootFeature(uvlRootFeature);
        uvlModel.getFeatureMap().put(ROOT_FEATURE_NAME, uvlRootFeature);

        de.vill.model.Group uvlRootGroup = new Group(Group.GroupType.OPTIONAL);
        uvlRootFeature.addChildren(uvlRootGroup);

        formula.getVariableNames().forEach((variableName) -> {
            de.vill.model.Feature uvlFeature = new Feature(variableName);
            uvlModel.getFeatureMap().put(variableName, uvlFeature);
            uvlRootGroup.getFeatures().add(uvlFeature);
        });

        Result<de.vill.model.constraint.Constraint> uvlConstraint =
                Trees.traverse(formula, new FormulaToUVLConstraintVisitor());
        problems.addAll(uvlConstraint.getProblems());
        if (uvlConstraint.isEmpty()) {
            return Result.empty(problems);
        }

        uvlModel.getOwnConstraints().add(uvlConstraint.get());
        return Result.of(uvlModel.toString(), problems);
    }

    @Override
    public boolean supportsParse() {
        return true;
    }

    @Override
    public boolean supportsSerialize() {
        return true;
    }

    @Override
    public String getFileExtension() {
        return "uvl";
    }

    @Override
    public String getName() {
        return "Universal Variability Language";
    }
}
