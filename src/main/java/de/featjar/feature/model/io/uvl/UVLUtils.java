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
import de.featjar.base.data.BinomialCalculator;
import de.featjar.base.data.Range;
import de.featjar.base.data.Result;
import de.featjar.base.io.format.ParseException;
import de.featjar.base.tree.Trees;
import de.featjar.base.tree.visitor.TreePrinter;
import de.featjar.feature.model.*;
import de.featjar.formula.io.textual.ExpressionParser;
import de.featjar.formula.io.textual.UVLSymbols;
import de.featjar.formula.structure.IExpression;
import de.featjar.formula.structure.IFormula;
import de.featjar.formula.structure.connective.And;
import de.featjar.formula.structure.connective.Not;
import de.featjar.formula.structure.connective.Or;
import de.vill.model.FeatureType;
import de.vill.model.Group;
import de.vill.model.constraint.Constraint;
import java.util.*;

/**
 * Provides helper functions for uvl parsing and serialization.
 *
 * @author Andreas Gerasimow
 * @author Sebastion Krieter
 */
public class UVLUtils {
    public static List<IFormula> uvlConstraintToFormula(List<Constraint> uvlConstraints) throws ClassNotFoundException {
        List<IFormula> formulas = new ArrayList<>();
        for (Constraint constraint : uvlConstraints) {
            // TODO do not use raw constructor, get instance for
            final ExpressionParser nodeReader = new ExpressionParser();
            ClassLoader.getSystemClassLoader().loadClass("de.featjar.formula.io.textual.Symbols");
            nodeReader.setSymbols(UVLSymbols.INSTANCE);
            nodeReader.setIgnoreMissingFeatures(ExpressionParser.ErrorHandling.KEEP);
            nodeReader.setIgnoreUnparseableSubExpressions(ExpressionParser.ErrorHandling.KEEP);
            Result<IExpression> parse = nodeReader.parse(constraint.toString(false, ""));
            if (parse.isEmpty()) {
                FeatJAR.log().problems(parse.getProblems());
            } else {
                FeatJAR.log()
                        .info(Trees.traverse(parse.get(), new TreePrinter()).get());
            }
            formulas.add((IFormula) parse.get());
        }

        return formulas;
    }

    /**
     * Converts UVL feature model to FeatJAR feature model.
     * @param uvlFeatureModel The UVL feature model to convert.
     * @return A FeatJAR feature model.
     */
    public static IFeatureModel createFeatureModel(de.vill.model.FeatureModel uvlFeatureModel) throws ParseException {
        IFeatureModel featureModel = new FeatureModel();
        de.vill.model.Feature rootFeature = uvlFeatureModel.getRootFeature();
        UVLUtils.createFeatureTree(featureModel, rootFeature);

        return featureModel;
    }

    /**
     * Converts UVL feature to FeatJAR feature.
     * @param featureModel The corresponding feature model of the feature.
     * @param uvlFeature The UVL feature to convert.
     * @return A FeatJAR feature.
     */
    public static IFeature createFeature(IFeatureModel featureModel, de.vill.model.Feature uvlFeature)
            throws ParseException {
        IFeature feature = featureModel.mutate().addFeature(getName(uvlFeature));
        feature.mutate().setAbstract(getAttributeValue(uvlFeature, "abstract", Boolean.FALSE));
        feature.mutate().setType(getFeatureType(uvlFeature));
        return feature;
    }

    /**
     * Builds a FeatJAR feature model from a UVL root feature.
     * @param featureModel FeatJAR feature model to build.
     * @param rootUVLFeature UVL root feature from a UVL feature model.
     */
    public static void createFeatureTree(IFeatureModel featureModel, de.vill.model.Feature rootUVLFeature)
            throws ParseException {
        LinkedList<de.vill.model.Feature> featureStack = new LinkedList<>();
        LinkedList<IFeatureTree> featureTreeStack = new LinkedList<>();

        IFeature rootFeature = createFeature(featureModel, rootUVLFeature);
        IFeatureTree featureTree = featureModel.mutate().addFeatureTreeRoot(rootFeature);

        featureStack.push(rootUVLFeature);
        featureTreeStack.push(featureTree);

        while (!featureStack.isEmpty()) {
            de.vill.model.Feature feature = featureStack.pop();
            IFeatureTree tree = featureTreeStack.pop();

            if (feature.getParentGroup() != null && feature.getParentGroup().GROUPTYPE == Group.GroupType.MANDATORY) {
                tree.mutate().setMandatory();
            } else if (feature.getParentGroup() != null
                    && feature.getParentGroup().GROUPTYPE == Group.GroupType.OPTIONAL) {
                tree.mutate().setOptional();
            } else if (feature.getLowerBound() != null) {
                if (feature.getUpperBound() != null) {
                    tree.mutate()
                            .setFeatureRange(Range.of(
                                    Integer.parseInt(feature.getLowerBound()),
                                    Integer.parseInt(feature.getUpperBound())));
                } else {
                    tree.mutate().setFeatureRange(Range.atLeast(Integer.parseInt(feature.getLowerBound())));
                }
            } else {
                if (feature.getUpperBound() != null) {
                    tree.mutate().setFeatureRange(Range.atMost(Integer.parseInt(feature.getUpperBound())));
                } else {
                    tree.mutate().setFeatureRange(Range.atMost(1));
                }
            }

            List<de.vill.model.Group> children = feature.getChildren();
            for (de.vill.model.Group group : children) {
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
                                Integer.parseInt(feature.getLowerBound()), Integer.parseInt(feature.getUpperBound()));
                        break;
                    default:
                        throw new ParseException(String.valueOf(group.GROUPTYPE));
                }
                int groupID = tree.getGroups().size();
                tree.mutate().addGroup(groupRange);
                for (de.vill.model.Feature childFeature : group.getFeatures()) {
                    featureStack.push(childFeature);
                    IFeature child = createFeature(featureModel, childFeature);
                    IFeatureTree childTree = tree.mutate().addFeatureBelow(child);
                    childTree.mutate().setGroupID(groupID);
                    featureTreeStack.push(childTree);
                }
            }
        }
    }

    /**
     * Converts UVL feature type to FeatJAR feature type.
     * @param uvlFeature UVL feature to retrieve the type.
     * @return FeatJAR feature type.
     */
    public static Class<?> getFeatureType(de.vill.model.Feature uvlFeature) throws ParseException {
        FeatureType featureType = uvlFeature.getFeatureType();
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

    /**
     * Retrieves name and namespace of a UVL feature.
     * @param feature UVL feature to retrieve the name and namespace.
     * @return Name of the feature. If the feature has a namespace, the return value will be in the following format: <namespace>::<feature name>
     */
    public static String getName(de.vill.model.Feature feature) {
        String nameSpace = feature.getNameSpace();
        return (nameSpace != null && !nameSpace.isBlank() ? nameSpace + "::" : "") + feature.getFeatureName();
    }

    /**
     * Retrieves attribute value of a UVL feature.
     * @param feature UVL feature to retrieve the attribute value.
     * @param key Key name of the attribute.
     * @param defaultValue Default value if the attribute does not exist.
     * @return The attribute of the feature.
     */
    @SuppressWarnings("unchecked")
    public static <T> T getAttributeValue(de.vill.model.Feature feature, String key, T defaultValue) {
        return Optional.ofNullable(feature.getAttributes().get(key))
                .map(a -> (T) a.getValue())
                .orElse(defaultValue);
    }

    /**
     * Creates a new formula where exactly k of the n provided formulas must be satisfied.
     * @param elements The n formulas.
     * @param k Specifies how many of the n formulas must exactly be satisfied.
     * @param negated Negates all literals.
     * @return n choose k formula.
     */
    public static IFormula nchoosek(IFormula[] elements, int k, boolean negated) {
        final int n = elements.length;

        // return tautology
        if ((k == 0) || (k == (n + 1))) {
            return new Or(new Not(elements[0]), elements[0]);
        }

        // return contradiction
        if ((k < 0) || (k > (n + 1))) {
            return new And(new Not(elements[0]), elements[0]);
        }

        final IFormula[] newNodes = new IFormula[(int) binomial(n, k)];
        int j = 0;

        // negate all elements
        if (negated) {
            negateNodes(elements);
        }

        final IFormula[] clause = new IFormula[k];
        final int[] index = new int[k];

        // the position that is currently filled in clause
        int level = 0;
        index[level] = -1;

        while (level >= 0) {
            // fill this level with the next element
            index[level]++;
            // did we reach the maximum for this level
            if (index[level] >= (n - (k - 1 - level))) {
                // go to previous level
                level--;
            } else {
                clause[level] = elements[index[level]];
                if (level == (k - 1)) {
                    newNodes[j++] = new Or(clause);
                } else {
                    // go to next level
                    level++;
                    // allow only ascending orders (to prevent from duplicates)
                    index[level] = index[level - 1];
                }
            }
        }
        if (j != newNodes.length) {
            throw new RuntimeException("Pre-calculation of the number of elements failed!");
        }
        return new And(newNodes);
    }

    /**
     * The number of ways selecting k things out of n things in a set.
     * @param n Size of the set.
     * @param k Size of the possible subsets.
     * @return Result of n choose k.
     */
    public static long binomial(int n, int k) {
        return new BinomialCalculator(n, k).binomial();
    }

    protected static void negateNodes(IFormula[] nodes) {
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = new Not(nodes[i]);
        }
    }
}
