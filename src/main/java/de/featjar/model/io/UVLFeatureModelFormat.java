/* -----------------------------------------------------------------------------
 * uvl - Universal Variability Language
 * Copyright (C) 2022 Elias Kuiter
 * 
 * This file is part of uvl.
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
 * See <https://github.com/FeatJAR/uvl> for further information.
 * -----------------------------------------------------------------------------
 */
package de.featjar.model.io;

import de.featjar.formula.structure.Formula;
import de.featjar.formula.structure.atomic.literal.VariableMap;
import de.featjar.formula.structure.atomic.predicate.Equals;
import de.featjar.formula.structure.compound.*;
import de.featjar.formula.structure.compound.And;
import de.featjar.formula.structure.compound.Not;
import de.featjar.formula.structure.compound.Or;
import de.featjar.model.Attributes;
import de.featjar.model.Constraint;
import de.featjar.model.Feature;
import de.featjar.model.util.Attribute;
import de.featjar.util.io.Input;
import de.featjar.util.io.format.Format;
import de.featjar.util.io.format.ParseException;
import de.featjar.util.io.format.ParseProblem;
import de.neominik.uvl.UVLParser;
import de.neominik.uvl.ast.*;
import de.featjar.model.FeatureModel;
import de.featjar.model.util.Identifier;
import de.featjar.util.data.Problem;
import de.featjar.util.data.Result;
import de.featjar.util.io.InputMapper;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Parses and writes feature models from and to UVL files.
 *
 * @author Dominik Engelhardt
 * @author Elias Kuiter
 */
public class UVLFeatureModelFormat implements Format<FeatureModel> {
	public static final String NAMESPACE = Attributes.class.getCanonicalName();
	public static final Attribute<String> UVL_NAMESPACE = new Attribute<>(NAMESPACE, "uvl_namespace", String.class);

	protected FeatureModel featureModel;
	protected UVLModel uvlModel;
	private VariableMap variableMap; // todo remove in favor of FeatureModel.variableMap
	protected List<Problem> parseProblems = new ArrayList<>();

	@Override
	public UVLFeatureModelFormat getInstance() {
		return new UVLFeatureModelFormat();
	}

	@Override
	public String getFileExtension() {
		return "uvl";
	}

	@Override
	public String getName() {
		return "Universal Variability Language";
	}

	@Override
	public boolean supportsParse() {
		return true;
	}

	@Override
	public boolean supportsSerialize() {
		return false;
	}

	@Override
	public Result<FeatureModel> parse(InputMapper inputMapper, Supplier<FeatureModel> supplier) {
		featureModel = supplier.get();
		return parse(inputMapper);
	}

	@Override
	public Result<FeatureModel> parse(InputMapper inputMapper) {
		if (featureModel == null)
			featureModel = new FeatureModel(Identifier.newCounter());
		variableMap = new VariableMap();
		parseProblems.clear();
		try {
			final Object result = UVLParser.parse(inputMapper.get().readText().orElseThrow(), namespace -> {
				Path path = Paths.get(String.format("%s.%s", namespace.replace(".", "/"), getFileExtension()));
				Optional<String> content = inputMapper.resolve(path)
						.flatMap(inputMapper::get)
						.map(Input::readText)
						.flatMap(Result::toOptional);
				if (content.isPresent())
					return content.get();
				else {
					parseProblems.add(new Problem("could not parse UVL submodel for namespace " + namespace + " at " + path, Problem.Severity.WARNING));
					return null;
				}
			});
			if (result == null) {
				return Result.empty(parseProblems);
			} else if (result instanceof UVLModel) {
				uvlModel = (UVLModel) result;
				constructFeatureModel();
			} else if (result instanceof ParseError) {
				parseProblems.add(new ParseProblem(result.toString(), ((ParseError) result).getLine(),
					Problem.Severity.ERROR));
				return Result.empty(parseProblems);
			}
			return Result.of(featureModel, parseProblems);
		} catch (final ParseException e) {
			return Result.empty(new ParseProblem(e.getMessage(), e.getLineNumber(), Problem.Severity.ERROR));
		} catch (final Exception e) {
			return Result.empty(new Problem(e));
		}
	}

	private void constructFeatureModel() throws ParseException {
		Arrays.stream(uvlModel.getImports()).forEach(this::parseImport);
		if (uvlModel.getRootFeatures().length == 1) {
			final de.neominik.uvl.ast.Feature f = uvlModel.getRootFeatures()[0];
			parseFeature(null, f, uvlModel);
		} else {
			throw new UnsupportedOperationException();
//			String rootName = MULTI_ROOT_PREFIX + 0;
//			for (int i = 1; uvlModel.getAllFeatures().keySet().contains(rootName); i++) {
//				rootName = MULTI_ROOT_PREFIX + i;
//			}
//			root = factory.createFeature(fm, rootName);
//			root.getStructure().setAbstract(true);
//			root.getProperty().setImplicit(true);
//			fm.addFeature(root);
//			Arrays.stream(uvlModel.getRootFeatures()).forEachOrdered(f -> parseFeature(fm, root, f, uvlModel));
//			root.getStructure().getChildren().forEach(fs -> fs.setMandatory(true));
		}
		final List<Object> ownConstraints = Arrays.asList(uvlModel.getOwnConstraints());
		Arrays.stream(uvlModel.getConstraints()).filter(c -> !ownConstraints.contains(c)).forEach(c -> parseConstraint(c, false));
		ownConstraints.forEach(c -> parseConstraint(c, true));
		featureModel.mutate().setAttributeValue(UVL_NAMESPACE, uvlModel.getNamespace());
	}

//	protected static final String EXTENDED_ATTRIBUTE_NAME = "extended__";
//	private static final String MULTI_ROOT_PREFIX = "Abstract_";

	private de.featjar.model.Feature parseFeature(Feature parentFeature, de.neominik.uvl.ast.Feature parsedFeature, UVLModel submodel) {
		final de.neominik.uvl.ast.Feature resolvedFeature = (de.neominik.uvl.ast.Feature) UVLParser.resolve(parsedFeature, uvlModel);

		boolean duplicateFeature = false;
		if (featureModel.getFeatures().stream().anyMatch(feature -> feature.getName().equals(parsedFeature.getName()))) {
			parseProblems.add(new Problem("Duplicate feature name " + parsedFeature.getName(), Problem.Severity.ERROR));
			duplicateFeature = true;
		}

		// Validate imported feature
		if ((parentFeature == null ? -1 : parentFeature.getName().lastIndexOf('.')) < parsedFeature.getName().lastIndexOf('.')) {
			// Update current submodel or add an error if the feature does not exist
			boolean invalid = true;
			Optional<UVLModel> sub;
			// Find submodel declaring the current feature, iterate in case a submodel has an imported root feature
			while ((sub = Arrays.stream(submodel.getSubmodels())
					.filter(m -> Arrays.stream(m.getRootFeatures()).map(de.neominik.uvl.ast.Feature::getName).anyMatch(parsedFeature.getName()::equals)).findFirst()).isPresent()) {
				submodel = sub.get();
				invalid = false;
			}
			if (invalid) {
				parseProblems.add(new Problem("Feature " + parsedFeature.getName() + " does not exist",  Problem.Severity.ERROR));
			}

			// Check for invalid attributes and child groups
			if (!parsedFeature.getAttributes().isEmpty()) {
				parseProblems.add(new Problem("Invalid attribute of imported feature " + parsedFeature.getName(),  Problem.Severity.ERROR));
			}
			if (parsedFeature.getGroups().length != 0) {
				parseProblems.add(new Problem("Invalid group of imported feature " + parsedFeature.getName(),  Problem.Severity.ERROR));
			}
		}
		final UVLModel finalSubmodel = submodel;

		de.featjar.model.Feature newFeature = parentFeature == null ? featureModel.getRootFeature() : parentFeature.mutate().createFeatureBelow();
		newFeature.mutate(mutator -> {
			mutator.setName(resolvedFeature.getName());
			mutator.setAbstract(isAbstract(resolvedFeature));
		});
		if (parsedFeature.getName().contains(".")) {
			//feature.setType(MultiFeature.TYPE_INTERFACE);
		}
		if (!duplicateFeature) { // Don't process groups for duplicate feature names, as this can cause infinite recursion
			Arrays.stream(parsedFeature.getGroups()).forEach(group -> parseGroup(newFeature, group, finalSubmodel));
		}
		parseAttributes(newFeature, parsedFeature);
		return newFeature;
	}

	private void parseGroup(Feature parentFeature, Group group, UVLModel submodel) {
		if ("cardinality".equals(group.getType())) {
			if ((group.getLower() == 1) && (group.getUpper() == -1)) {
				group.setType("or");
			} else if ((group.getLower() == 1) && (group.getUpper() == 1)) {
				group.setType("alternative");
			} else if ((group.getLower() == 0) && (group.getUpper() == -1)) {
				group.setType("optional");
			} else if ((group.getLower() == group.getUpper()) && (group.getUpper() == group.getChildren().length)) {
				group.setType("mandatory");
			} else {
				group.setType("optional");
				parseProblems.add(new Problem(
						String.format("Failed to convert cardinality [%d..%d] to known group type at feature %s.", group.getLower(), group.getUpper(), parentFeature.getName()),
						Problem.Severity.WARNING));
			}
		}
		final List<Feature> children = Stream.of(group.getChildren()).map(f -> parseFeature(parentFeature, f, submodel)).collect(Collectors.toList());
		switch (group.getType()) {
			case "or":
				parentFeature.getFeatureTree().mutate().setOr();
				break;
			case "alternative":
				parentFeature.getFeatureTree().mutate().setAlternative();
				break;
			case "optional":
				break;
			case "mandatory":
				children.forEach(f -> f.getFeatureTree().mutate().setMandatory(true));
				break;
		}
	}

	private boolean isAbstract(de.neominik.uvl.ast.Feature f) {
		return Objects.equals(true, f.getAttributes().get("abstract"));
	}

	private void parseAttributes(Feature feature, de.neominik.uvl.ast.Feature f) {
		UVLParser.getAttributes(f).entrySet().stream().forEachOrdered(e -> parseAttribute(feature, e.getKey(), e.getValue()));
	}

	protected void parseAttribute(Feature feature, String attributeKey, Object attributeValue) {
		if (attributeKey.equals("constraint") || attributeKey.equals("constraints")) {
			if (attributeValue instanceof List<?>) {
				((List<?>) attributeValue).forEach(this::parseConstraint);
			} else {
				parseConstraint(attributeValue);
			}
		}
	}

	private void parseConstraint(Object c, boolean own) {
		//todo: use identifier in formula, not c
//		final Formula formula = parseConstraint(c);
//		if (formula != null) {
//			final Constraint newConstraint = featureModel.mutate().createConstraint(formula);
////				if (own) {
////					fm.addOwnConstraint(newConstraint);
////				} else {
////					newConstraint.setType(MultiFeature.TYPE_INTERFACE);
////					fm.addConstraint(newConstraint);
////				}
//		}
	}

	private Formula parseConstraint(Object c) {
		if (c instanceof String) {
			final String name = (String) c;
			checkReferenceValid(name);
			return variableMap.createLiteral((String) c);
		} else if (c instanceof de.neominik.uvl.ast.Not) {
			return new Not(parseConstraint(((de.neominik.uvl.ast.Not) c).getChild()));
		} else if (c instanceof de.neominik.uvl.ast.And) {
			return new And(parseConstraint(((de.neominik.uvl.ast.And) c).getLeft()), parseConstraint(((de.neominik.uvl.ast.And) c).getRight()));
		} else if (c instanceof de.neominik.uvl.ast.Or) {
			return new Or(parseConstraint(((de.neominik.uvl.ast.Or) c).getLeft()), parseConstraint(((de.neominik.uvl.ast.Or) c).getRight()));
		} else if (c instanceof Impl) {
			return new Implies(parseConstraint(((Impl) c).getLeft()), parseConstraint(((Impl) c).getRight()));
		} else if (c instanceof Equiv) {
			return new Biimplies(parseConstraint(((Equiv) c).getLeft()), parseConstraint(((Equiv) c).getRight()));
		}
		return null;
	}

	private void checkReferenceValid(String name) {
		if (featureModel.getFeaturesByName(name).isEmpty()) {
			parseProblems.add(new Problem("Invalid reference: Feature " + name + " doesn't exist", Problem.Severity.ERROR));
			throw new RuntimeException("Invalid reference");
		}
	}

	private void parseImport(Import i) {
		//fm.addInstance(i.getNamespace(), i.getAlias());
	}
//
//	/**
//	 * @param error a {@link ParseError}
//	 * @return a {@link Problem}
//	 */
//	private Problem toProblem(ParseError error) {
//		return new Problem(error.toString(), error.getLine(), Severity.ERROR);
//	}
//
//	@Override
//	public String write(IFeatureModel fm) {
//		return deconstructFeatureModel(fm).toString();
//	}
//
//	private UVLModel deconstructFeatureModel(IFeatureModel fm) {
//		final UVLModel model = new UVLModel();
//		String namespace = fm.getStructure().getRoot().getFeature().getName();
//		List<IConstraint> constraints = fm.getConstraints();
//		if (fm instanceof MultiFeatureModel) {
//			final MultiFeatureModel mfm = (MultiFeatureModel) fm;
//			final FeatureAttribute<String> nsAttribute = mfm.getStringAttributes().getAttribute(NS_ATTRIBUTE_FEATURE, NS_ATTRIBUTE_NAME);
//			if (nsAttribute != null) {
//				namespace = nsAttribute.getValue();
//			}
//			model.setImports(mfm.getExternalModels().values().stream().map(um -> new Import(um.getModelName(), um.getVarName())).toArray(Import[]::new));
//			if (mfm.isMultiProductLineModel()) {
//				constraints = mfm.getOwnConstraints();
//			}
//		} else {
//			model.setImports(new Import[0]);
//		}
//		model.setNamespace(namespace);
//		final IFeatureStructure root = fm.getStructure().getRoot();
//		if (root.getFeature().getProperty().isImplicit() && root.isAnd() && root.hasChildren()
//				&& root.getChildren().stream().allMatch(IFeatureStructure::isMandatory) && root.getRelevantConstraints().isEmpty()) {
//			// Remove implicit root feature, use children as root features
//			model.setRootFeatures(root.getChildren().stream().map(child -> printFeature(child.getFeature())).toArray(Feature[]::new));
//		} else {
//			// Use single root feature
//			model.setRootFeatures(new Feature[] { printFeature(root.getFeature()) });
//		}
//		model.setConstraints(constraints.stream().map(this::printConstraint).toArray());
//		return model;
//	}
//
//	private Feature printFeature(IFeature feature) {
//		final Feature f = new Feature();
//		f.setName(feature.getName());
//		if (!f.getName().contains(".")) { // exclude references
//			f.setAttributes(printAttributes(feature));
//			f.setGroups(printGroups(feature));
//		}
//		return f;
//	}
//
//	/**
//	 * This method writes all attributes of the specified feature to a map that can be converted to UVL.
//	 *
//	 * @param feature the feature whose attributes are written
//	 * @return a map containing the attributes
//	 */
//	protected Map<String, Object> printAttributes(IFeature feature) {
//		final Map<String, Object> attributes = new TreeMap<>();
//		if (feature.getStructure().isAbstract()) {
//			attributes.put("abstract", true);
//		}
//		return attributes;
//	}
//
//	private Group constructGroup(IFeatureStructure fs, String type, Predicate<IFeatureStructure> pred) {
//		return new Group(type, 0, 0, fs.getChildren().stream().filter(pred).map(f -> printFeature(f.getFeature())).toArray(Feature[]::new));
//	}
//
//	private Group[] printGroups(IFeature feature) {
//		final IFeatureStructure fs = feature.getStructure();
//		if (!fs.hasChildren()) {
//			return new Group[] {};
//		}
//		if (fs.isAnd()) {
//			final List<Group> groups = new LinkedList<Group>();
//			for (int i = 0; i < fs.getChildrenCount(); i++) {
//				final Group group = getGroup(fs.getChildren().get(i), i);
//				groups.add(group);
//				i = (i + group.getChildren().length) - 1;
//			}
//			final Group[] groupArray = new Group[groups.size()];
//			for (int i = 0; i < groups.size(); i++) {
//				groupArray[i] = groups.get(i);
//			}
//			return groupArray;
//		} else if (fs.isOr()) {
//			return new Group[] { constructGroup(fs, "or", x -> true) };
//		} else if (fs.isAlternative()) {
//			return new Group[] { constructGroup(fs, "alternative", x -> true) };
//		}
//		return new Group[] {};
//	}
//
//	/**
//	 * a method to create a group for uvl starting with a feature that is either mandatory or optional and adding all features with the same property until a
//	 * feature with a different property comes in order
//	 *
//	 * @param feat the first feature of a new group
//	 * @param pos the position of the feature in the list of children of the parent feature
//	 * @return the new group with the given feature as start feature
//	 */
//	private Group getGroup(IFeatureStructure feat, int pos) {
//		final List<IFeatureStructure> featuresInGroup = new LinkedList<IFeatureStructure>();
//		featuresInGroup.add(feat);
//		for (int i = pos + 1; i < feat.getParent().getChildrenCount(); i++) {
//			if (feat.getParent().getChildren().get(i).isMandatory() == feat.isMandatory()) {
//				featuresInGroup.add(feat.getParent().getChildren().get(i));
//			} else {
//				break;
//			}
//		}
//		if (feat.isMandatory()) {
//			return constructGroup(feat.getParent(), "mandatory", c -> featuresInGroup.contains(c));
//		} else {
//			return constructGroup(feat.getParent(), "optional", c -> featuresInGroup.contains(c));
//		}
//	}
//
//	private Object printConstraint(IConstraint constraint) {
//		return printConstraint(constraint.getNode());
//	}
//
//	private Object printConstraint(Node n) {
//		if (n instanceof Literal) {
//			return ((Literal) n).var;
//		} else if (n instanceof org.prop4j.Not) {
//			return new Not(printConstraint(n.getChildren()[0]));
//		} else if (n instanceof org.prop4j.And) {
//			return printMultiArity(And::new, n.getChildren());
//		} else if (n instanceof org.prop4j.Or) {
//			return printMultiArity(Or::new, n.getChildren());
//		} else if (n instanceof Implies) {
//			return new Impl(printConstraint(n.getChildren()[0]), printConstraint(n.getChildren()[1]));
//		} else if (n instanceof Equals) {
//			return new Equiv(printConstraint(n.getChildren()[0]), printConstraint(n.getChildren()[1]));
//		}
//		return null;
//	}
//
//	private Object printMultiArity(BiFunction<Object, Object, Object> constructor, Node[] args) {
//		switch (args.length) {
//			case 0:
//				return null;
//			case 1:
//				return printConstraint(args[0]);
//			case 2:
//				return constructor.apply(printConstraint(args[0]), printConstraint(args[1]));
//			default:
//				return constructor.apply(printConstraint(args[0]), printMultiArity(constructor, Arrays.copyOfRange(args, 1, args.length)));
//		}
//	}
//
//	@Override
//	public boolean supportsContent(CharSequence content) {
//		return !content.toString().contains(EXTENDED_ATTRIBUTE_NAME);
//	}
//
//	@Override
//	public boolean supportsContent(LazyReader reader) {
//		return supportsContent((CharSequence) reader);
//	}
//
//	@Override
//	public boolean isValidFeatureName(String featureName) {
//		return featureName.matches("[^\\\"\\.\\n\\r]*");
//	}
//
//	@Override
//	public String getErrorMessage() {
//		return "The characters  \" and . are not allowed and the feature name has to be non-empty.";
//	}
}
