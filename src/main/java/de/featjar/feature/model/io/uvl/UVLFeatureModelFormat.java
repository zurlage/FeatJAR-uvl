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

import de.featjar.base.FeatJAR;
import de.featjar.base.data.Problem;
import de.featjar.base.data.Range;
import de.featjar.base.data.Result;
import de.featjar.base.io.format.IFormat;
import de.featjar.base.io.format.ParseException;
import de.featjar.base.io.input.AInputMapper;
import de.featjar.base.tree.Trees;
import de.featjar.base.tree.visitor.TreePrinter;
import de.featjar.feature.model.*;
import de.featjar.formula.io.textual.ExpressionParser;
import de.featjar.formula.io.textual.ExpressionParser.ErrorHandling;
import de.featjar.formula.io.textual.ShortSymbols;
import de.featjar.formula.structure.IExpression;
import de.featjar.formula.structure.formula.IFormula;
import de.vill.main.UVLModelFactory;
import de.vill.model.FeatureType;
import de.vill.model.constraint.Constraint;
import java.util.*;

/**
 * Parses and writes feature models from and to UVL files.
 *
 * @author Sebastian Krieter
 */
public class UVLFeatureModelFormat implements IFormat<IFeatureModel> {

    @Override
    public Result<IFeatureModel> parse(AInputMapper inputMapper) {
        try {
            String content = inputMapper.get().text();
            UVLModelFactory uvlModelFactory = new UVLModelFactory();
            de.vill.model.FeatureModel uvlModel = uvlModelFactory.parse(content);

            // TODO do not use raw constructor, get instance for IFeatureModel
            IFeatureModel featureModel = new FeatureModel();
            de.vill.model.Feature rootFeature = uvlModel.getRootFeature();
            IFeature feature = createFeature(featureModel, rootFeature);
            IFeatureTree tree = featureModel.mutate().addFeatureTreeRoot(feature);

            createFeatureTree(featureModel, rootFeature, tree);

            List<Constraint> constraints = uvlModel.getConstraints();
            for (Constraint constraint : constraints) {
                final ExpressionParser nodeReader = new ExpressionParser();
                ClassLoader.getSystemClassLoader().loadClass("de.featjar.formula.io.textual.Symbols");
                nodeReader.setSymbols(ShortSymbols.INSTANCE);
                nodeReader.setIgnoreMissingFeatures(ErrorHandling.KEEP);
                nodeReader.setIgnoreUnparseableSubExpressions(ErrorHandling.KEEP);
                Result<IExpression> parse = nodeReader.parse(constraint.toString(false, ""));
                if (parse.isEmpty()) {
                    FeatJAR.log().problems(parse.getProblems());
                } else {
                    FeatJAR.log()
                            .info(Trees.traverse(parse.get(), new TreePrinter()).get());
                }
                featureModel.mutate().addConstraint((IFormula) parse.get());
            }
            return Result.of(featureModel);
        } catch (Exception e) {
            return Result.empty(e);
        }
    }

    @Override
    public Result<String> serialize(IFeatureModel fm) {
        List<Problem> problems = new ArrayList<>(); // problem create problem set
        try {
            if (fm.getRootFeatures().isEmpty()) {
                problems.add(new Problem("No root features exist.", Problem.Severity.ERROR));
                return Result.empty(problems);
            }

            IFeature rootFeature = fm.getRootFeatures().get(0);
            problems.add(new Problem("UVL supports only one root feature. If there are more than one root features in the model, the first one will be used.", Problem.Severity.WARNING));

            Result<IFeatureTree> featureTree = fm.getFeatureTree(rootFeature);
            problems.addAll(featureTree.getProblems());
            if (featureTree.isEmpty()) {
                return Result.empty(problems);
            }

            Result<de.vill.model.FeatureModel> uvlModel = Trees.traverse(featureTree.get(), new FeatureTreeVisitor());
            problems.addAll(uvlModel.getProblems());
            if (uvlModel.isEmpty()) {
                return Result.empty(problems);
            }

            for (IConstraint constraint : fm.getConstraints()) {
                Result<de.vill.model.constraint.Constraint> uvlConstraint = Trees.traverse(constraint.getFormula(), new ConstraintTreeVisitor());
                problems.addAll(uvlConstraint.getProblems());
                if (uvlConstraint.isEmpty()) {
                    return Result.empty(problems);
                }
                uvlModel.get().getConstraints().add(uvlConstraint.get());
            }

            return Result.of(uvlModel.toString(), problems);
        } catch (Exception e) {
            return Result.empty(e);
        }
    }

    private IFeature createFeature(IFeatureModel featureModel, de.vill.model.Feature rootFeature)
            throws ParseException {
        IFeature feature = featureModel.mutate().addFeature(getName(rootFeature));
        feature.mutate().setAbstract(getAttributeValue(rootFeature, "abstract", Boolean.FALSE));
        feature.mutate().setType(getFeatureType(rootFeature));
        return feature;
    }

    private void createFeatureTree(IFeatureModel featureModel, de.vill.model.Feature parentFeature, IFeatureTree tree)
            throws ParseException {
        if (parentFeature.getLowerBound() != null) {
            if (parentFeature.getUpperBound() != null) {
                tree.mutate()
                        .setFeatureRange(Range.of(
                                Integer.parseInt(parentFeature.getLowerBound()),
                                Integer.parseInt(parentFeature.getUpperBound())));
            } else {
                tree.mutate().setFeatureRange(Range.atLeast(Integer.parseInt(parentFeature.getLowerBound())));
            }
        } else {
            if (parentFeature.getUpperBound() != null) {
                tree.mutate().setFeatureRange(Range.atMost(Integer.parseInt(parentFeature.getUpperBound())));
            } else {
                tree.mutate().setFeatureRange(Range.atMost(1));
            }
        }
        List<de.vill.model.Group> children = parentFeature.getChildren();
        for (de.vill.model.Group group : children) {
            List<de.vill.model.Feature> features = group.getFeatures();
            Range groupRange;
            switch (group.GROUPTYPE) {
                case MANDATORY:
                case OPTIONAL:
                    groupRange = Range.atLeast(0);
                    break;
                case ALTERNATIVE:
                    groupRange = Range.exactly(1);
                    break;
                case OR:
                    groupRange = Range.atLeast(1);
                    break;
                case GROUP_CARDINALITY:
                    groupRange = Range.of(
                            Integer.parseInt(parentFeature.getLowerBound()),
                            Integer.parseInt(parentFeature.getUpperBound()));
                    break;
                default:
                    throw new ParseException(String.valueOf(group.GROUPTYPE));
            }
            int groupID = tree.getGroups().size();
            tree.mutate().addGroup(groupRange);
            for (de.vill.model.Feature childFeature : features) {
                IFeature child = createFeature(featureModel, childFeature);
                IFeatureTree childTree = tree.mutate().addFeatureBelow(child);
                childTree.mutate().setGroupID(groupID);
                createFeatureTree(featureModel, childFeature, childTree);
            }
        }
    }

    private Class<?> getFeatureType(de.vill.model.Feature rootFeature) throws ParseException {
        FeatureType featureType = rootFeature.getFeatureType();
        if (featureType == null) {
            return Boolean.class;
        } else {
            switch (featureType) {
                case BOOL:
                    return Boolean.class;
                case INT:
                    return Integer.class;
                case REAL:
                    return Double.class;
                case STRING:
                    return String.class;
                default:
                    throw new ParseException(String.valueOf(featureType));
            }
        }
    }

    private String getName(de.vill.model.Feature feature) {
        String nameSpace = feature.getNameSpace();
        return (nameSpace != null && !nameSpace.isBlank() ? nameSpace + "::" : "") + feature.getFeatureName();
    }

    @SuppressWarnings("unchecked")
    private <T> T getAttributeValue(de.vill.model.Feature feature, String key, T defaultValue) {
        return Optional.ofNullable(feature.getAttributes().get(key))
                .map(a -> (T) a.getValue())
                .orElse(defaultValue);
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
