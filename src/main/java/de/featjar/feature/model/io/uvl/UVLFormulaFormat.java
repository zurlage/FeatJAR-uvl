package de.featjar.feature.model.io.uvl;

import de.featjar.base.data.Problem;
import de.featjar.base.data.Result;
import de.featjar.base.io.format.IFormat;
import de.featjar.base.io.input.AInputMapper;
import de.featjar.base.tree.Trees;
import de.featjar.feature.model.IFeatureModel;
import de.featjar.feature.model.io.uvl.visitor.FormulaToUVLConstraintVisitor;
import de.featjar.formula.analysis.combinations.BinomialCalculator;
import de.featjar.formula.structure.formula.IFormula;
import de.featjar.formula.structure.formula.connective.And;
import de.featjar.formula.structure.formula.connective.Not;
import de.featjar.formula.structure.formula.connective.Or;
import de.featjar.formula.structure.formula.connective.Reference;
import de.vill.main.UVLModelFactory;
import de.vill.model.Attribute;
import de.vill.model.Feature;
import de.vill.model.Group;

import java.util.ArrayList;
import java.util.List;

public class UVLFormulaFormat implements IFormat<IFormula> {

    public static final String ROOT_FEATURE_NAME = "Formula";

    @Override
    public Result<IFormula> parse(AInputMapper inputMapper) {
        try {
            String content = inputMapper.get().text();
            UVLModelFactory uvlModelFactory = new UVLModelFactory();
            de.vill.model.FeatureModel uvlModel = uvlModelFactory.parse(content);
            IFeatureModel featureModel = UVLUtils.createFeatureModel(uvlModel);

            List<IFormula> constraints = UVLUtils.uvlConstraintToFormula(uvlModel.getConstraints());

            // TODO: Finish this

            IFormula formula = new Reference(List.of());

            return Result.of(formula);
        } catch (Exception e) {
            return Result.empty(e);
        }
    }

    @Override
    public Result<String> serialize(IFormula formula) {
        List<Problem> problems = new ArrayList<>();

        de.vill.model.FeatureModel uvlModel = new de.vill.model.FeatureModel();
        uvlModel.setNamespace("");
        de.vill.model.Feature uvlRootFeature = new Feature(ROOT_FEATURE_NAME);
        uvlRootFeature.getAttributes().put("abstract", new Attribute<>("abstract", true));
        uvlModel.setRootFeature(uvlRootFeature);
        uvlModel.getFeatureMap().put(ROOT_FEATURE_NAME, uvlRootFeature);

        de.vill.model.Group uvlRootGroup = new Group(Group.GroupType.OR);
        uvlRootFeature.addChildren(uvlRootGroup);

        formula.getVariableNames().forEach((variableName) -> {
            de.vill.model.Feature uvlFeature = new Feature(variableName);
            uvlModel.getFeatureMap().put(variableName, uvlFeature);
            uvlRootGroup.getFeatures().add(uvlFeature);
        });

        Result<de.vill.model.constraint.Constraint> uvlConstraint = Trees.traverse(formula, new FormulaToUVLConstraintVisitor());
        problems.addAll(uvlConstraint.getProblems());
        if (uvlConstraint.isEmpty()) {
            return Result.empty(problems);
        }
        uvlModel.getConstraints().add(uvlConstraint.get());
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
