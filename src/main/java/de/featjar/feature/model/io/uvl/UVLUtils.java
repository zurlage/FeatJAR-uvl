package de.featjar.feature.model.io.uvl;

import de.featjar.base.FeatJAR;
import de.featjar.base.data.Range;
import de.featjar.base.data.Result;
import de.featjar.base.io.format.ParseException;
import de.featjar.base.tree.Trees;
import de.featjar.base.tree.visitor.TreePrinter;
import de.featjar.feature.model.FeatureModel;
import de.featjar.feature.model.IFeature;
import de.featjar.feature.model.IFeatureModel;
import de.featjar.feature.model.IFeatureTree;
import de.featjar.formula.analysis.combinations.BinomialCalculator;
import de.featjar.formula.io.textual.ExpressionParser;
import de.featjar.formula.io.textual.ShortSymbols;
import de.featjar.formula.structure.IExpression;
import de.featjar.formula.structure.formula.IFormula;
import de.featjar.formula.structure.formula.connective.And;
import de.featjar.formula.structure.formula.connective.Not;
import de.featjar.formula.structure.formula.connective.Or;
import de.vill.model.FeatureType;
import de.vill.model.constraint.Constraint;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class UVLUtils {
    public static List<IFormula> uvlConstraintToFormula(List<Constraint> uvlConstraints) throws ClassNotFoundException {
        List<IFormula> formulas = new ArrayList<>();
        for (Constraint constraint : uvlConstraints) {
            // TODO do not use raw constructor, get instance for
            final ExpressionParser nodeReader = new ExpressionParser();
            ClassLoader.getSystemClassLoader().loadClass("de.featjar.formula.io.textual.Symbols");
            nodeReader.setSymbols(ShortSymbols.INSTANCE);
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

    public static IFeatureModel createFeatureModel(de.vill.model.FeatureModel uvlFeatureModel) throws ParseException {
        IFeatureModel featureModel = new FeatureModel();
        de.vill.model.Feature rootFeature = uvlFeatureModel.getRootFeature();
        IFeature feature = createFeature(featureModel, rootFeature);
        IFeatureTree tree = featureModel.mutate().addFeatureTreeRoot(feature);

        UVLUtils.createFeatureTree(featureModel, rootFeature, tree);

        return featureModel;
    }

    public static IFeature createFeature(IFeatureModel featureModel, de.vill.model.Feature rootFeature)
            throws ParseException {
        IFeature feature = featureModel.mutate().addFeature(getName(rootFeature));
        feature.mutate().setAbstract(getAttributeValue(rootFeature, "abstract", Boolean.FALSE));
        feature.mutate().setType(getFeatureType(rootFeature));
        return feature;
    }

    public static void createFeatureTree(IFeatureModel featureModel, de.vill.model.Feature parentFeature, IFeatureTree tree)
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

    public static Class<?> getFeatureType(de.vill.model.Feature rootFeature) throws ParseException {
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

    public static String getName(de.vill.model.Feature feature) {
        String nameSpace = feature.getNameSpace();
        return (nameSpace != null && !nameSpace.isBlank() ? nameSpace + "::" : "") + feature.getFeatureName();
    }

    @SuppressWarnings("unchecked")
    public static <T> T getAttributeValue(de.vill.model.Feature feature, String key, T defaultValue) {
        return Optional.ofNullable(feature.getAttributes().get(key))
                .map(a -> (T) a.getValue())
                .orElse(defaultValue);
    }

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

    public static long binomial(int n, int k) {
        return new BinomialCalculator(n, k).binomial();
    }

    protected static void negateNodes(IFormula[] nodes) {
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = new Not(nodes[i]);
        }
    }
}