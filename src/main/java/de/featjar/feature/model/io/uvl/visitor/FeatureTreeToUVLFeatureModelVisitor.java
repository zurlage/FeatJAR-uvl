package de.featjar.feature.model.io.uvl.visitor;

import de.featjar.base.data.Problem;
import de.featjar.base.data.Result;
import de.featjar.base.io.format.ParseException;
import de.featjar.base.tree.visitor.ITreeVisitor;
import de.featjar.feature.model.Attributes;
import de.featjar.feature.model.FeatureTree;
import de.featjar.feature.model.IFeature;
import de.featjar.feature.model.IFeatureTree;
import de.vill.model.Attribute;
import de.vill.model.FeatureModel;
import de.vill.model.Group;

import java.util.ArrayList;
import java.util.List;

import static de.vill.model.FeatureType.*;
import static de.vill.model.FeatureType.STRING;

public class FeatureTreeToUVLFeatureModelVisitor implements ITreeVisitor<IFeatureTree, de.vill.model.FeatureModel> {

    private de.vill.model.FeatureModel uvlModel;
    private List<Problem> problemList;

    public FeatureTreeToUVLFeatureModelVisitor() {
        reset();
    }

    @Override
    public void reset() {
        uvlModel = new de.vill.model.FeatureModel();
        problemList = new ArrayList<>();
    }

    @Override
    public Result<FeatureModel> getResult() {
        return Result.of(uvlModel, problemList);
    }

    @Override
    public TraversalAction lastVisit(List<IFeatureTree> path) {
        final IFeatureTree node = ITreeVisitor.getCurrentNode(path);

        try {
            String[] namespaceAndName = getUVLNamespaceAndName(node.getFeature());
            String name;
            String namespace = "";
            if (namespaceAndName.length == 1) {
                name = namespaceAndName[0];
            } else if (namespaceAndName.length == 2) {
                namespace = namespaceAndName[0];
                name = namespaceAndName[1];
            } else {
                problemList.add(new Problem("Feature " + node.getFeature().getName().get() + " has an illegal name."));
                return TraversalAction.FAIL;
            }

            de.vill.model.Feature uvlFeature = new de.vill.model.Feature(name);
            uvlFeature.setNameSpace(namespace);
            uvlFeature.getAttributes().put("abstract", new Attribute<>("abstract", node.getFeature().isAbstract()));
            try {
                uvlFeature.setFeatureType(getUVLFeatureType(node.getFeature()));
            } catch (ParseException e) {
                problemList.add(new Problem("Type of feature " + node.getFeature().getName().get() + " cannot be parsed."));
                return TraversalAction.FAIL;
            }

            node
                    .getFeature().getAttributes().orElseThrow().entrySet().stream()
                    .filter((entry) -> !entry.getKey().equals(Attributes.ABSTRACT))
                    .forEach(entry ->
                            uvlFeature.getAttributes().put(entry.getKey().getName(), new Attribute<>(entry.getKey().getName(), entry.getValue()))
                    );

            FeatureTree.Group group = node.getGroup();

            de.vill.model.Group uvlGroup = new de.vill.model.Group(getUVLGroupType(group, node));
            uvlGroup.setParentFeature(uvlFeature);
            uvlGroup.setLowerBound(String.valueOf(group.getLowerBound()));
            uvlGroup.setUpperBound(String.valueOf(group.getUpperBound()));
            uvlGroup.getFeatures().addAll(getUVLChildrenFeatures(node.getChildren()));
            uvlFeature.addChildren(uvlGroup);
            uvlModel.getFeatureMap().put(name, uvlFeature);
        } catch (Exception e) {
            problemList.add(new Problem(e.getMessage()));
            return TraversalAction.FAIL;
        }

        return TraversalAction.CONTINUE;
    }

    private List<de.vill.model.Feature> getUVLChildrenFeatures(List<? extends IFeatureTree> features) throws Exception {
        List<de.vill.model.Feature> children = new ArrayList<>();
        for (IFeatureTree feature : features) {
            if (feature.getFeature().getName().isEmpty())
                throw new Exception("Feature has no name.");
            de.vill.model.Feature uvlFeature = uvlModel.getFeatureMap().get(feature.getFeature().getName().get());
            children.add(uvlFeature);
        }
        return children;
    }

    private String[] getUVLNamespaceAndName(IFeature feature) throws Exception {
        if (feature.getName().isEmpty())
            throw new Exception("Feature has no name.");
        return feature.getName().get().split("::");
    }

    private Group.GroupType getUVLGroupType(FeatureTree.Group group, IFeatureTree node) {
        if (group.isOr()) {
            return Group.GroupType.OR;
        }
        if (group.isAnd()) {
            if (node.isOptional()) {
                return Group.GroupType.OPTIONAL;
            }
            if (node.isMandatory()) {
                return Group.GroupType.MANDATORY;
            }
        }
        if (group.isAlternative()) {
            return Group.GroupType.ALTERNATIVE;
        }
        if (group.isCardinalityGroup()) {
            return Group.GroupType.GROUP_CARDINALITY;
        }

        return Group.GroupType.OPTIONAL;
    }

    private de.vill.model.FeatureType getUVLFeatureType(IFeature feature) throws ParseException {
        Class<?> featureType = feature.getType();
        if (featureType == null)
            return BOOL;
        else if (featureType == Boolean.class)
            return BOOL;
        else if (featureType == Integer.class)
            return INT;
        else if (featureType == Double.class)
            return REAL;
        else if (featureType == String.class)
            return STRING;
        else
            throw new ParseException(featureType.getName());
    }
}
