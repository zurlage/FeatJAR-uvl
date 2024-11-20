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
package de.featjar.feature.model.io;

import de.featjar.FormatTest;
import de.featjar.analysis.sat4j.computation.ComputeSatisfiableSAT4J;
import de.featjar.base.computation.Computations;
import de.featjar.base.data.Result;
import de.featjar.base.data.identifier.Identifiers;
import de.featjar.base.io.format.IFormat;
import de.featjar.base.io.input.FileInputMapper;
import de.featjar.base.io.input.StringInputMapper;
import de.featjar.feature.model.*;
import de.featjar.feature.model.io.uvl.UVLFeatureModelFormat;
import de.featjar.formula.assignment.ComputeBooleanClauseList;
import de.featjar.formula.computation.ComputeCNFFormula;
import de.featjar.formula.computation.ComputeNNFFormula;
import de.featjar.formula.structure.IFormula;
import de.featjar.formula.structure.connective.*;
import de.featjar.formula.structure.predicate.Literal;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class UVLFeatureModelFormatTest {

    private static FeatureModel featureModel;

    @BeforeAll
    public static void setup() {
        FeatureModel featureModel = new FeatureModel(Identifiers.newCounterIdentifier());

        // features
        IFeatureTree rootTree =
                featureModel.mutate().addFeatureTreeRoot(featureModel.mutate().addFeature("root"));
        rootTree.mutate().setAnd();

        IFeature childFeature1 = featureModel.mutate().addFeature("Test1");
        IFeatureTree childTree1 = rootTree.mutate().addFeatureBelow(childFeature1);

        IFeature childFeature2 = featureModel.mutate().addFeature("Test2");
        IFeatureTree childTree2 = rootTree.mutate().addFeatureBelow(childFeature2);

        IFeature childFeature3 = featureModel.mutate().addFeature("Test3");
        IFeatureTree childTree3 = childTree1.mutate().addFeatureBelow(childFeature3);
        childTree3.mutate().setAlternative();

        IFeature childFeature4 = featureModel.mutate().addFeature("Test4");
        childTree1.mutate().addFeatureBelow(childFeature4);

        IFeature childFeature5 = featureModel.mutate().addFeature("Test5");
        IFeatureTree childTree5 = childTree2.mutate().addFeatureBelow(childFeature5);
        childTree5.mutate().setOr();

        IFeature childFeature6 = featureModel.mutate().addFeature("Test6");
        childTree2.mutate().addFeatureBelow(childFeature6);

        IFeature childFeature7 = featureModel.mutate().addFeature("Test7");
        IFeatureTree childTree7 = rootTree.mutate().addFeatureBelow(childFeature7);
        childTree7.mutate().setMandatory();

        IFormula formula1 = new Or(
                new And(new Literal("Test1"), new Literal("Test2")),
                new BiImplies(new Literal("Test3"), new Literal("Test4")),
                new Implies(new Literal("Test5"), new Literal("Test6")),
                new Not(new Literal("Test7")));

        // constraints
        featureModel.mutate().addConstraint(formula1);
        UVLFeatureModelFormatTest.featureModel = featureModel;
    }

    @Test
    void testFixtures() {
        FormatTest.testParseAndSerialize("uvl/ABC-nAnBnC", new UVLFeatureModelFormat());
        FormatTest.testParseAndSerialize("uvl/nA", new UVLFeatureModelFormat());
        FormatTest.testParseAndSerialize("uvl/nAB", new UVLFeatureModelFormat());
    }

    @Test
    void testUVLFeatureModelFormatSerialize() throws IOException {
    	UVLFeatureModelFormat format = new UVLFeatureModelFormat();
        Result<String> featureModelString = format.serialize(featureModel);

        if (featureModelString.isEmpty()) {
            Assertions.fail();
        }

        String expected = new String(
                Files.readAllBytes(Path.of("src", "test", "resources", "uvl", "featureModelSerializeResult.uvl")), StandardCharsets.UTF_8);
        Assertions.assertEquals(expected, featureModelString.get());
    }

    @Test
    void testUVLFeatureModelFormatParse() throws IOException {
        IFormat<IFeatureModel> format = new UVLFeatureModelFormat();
        Result<IFeatureModel> result = format.parse(new FileInputMapper(
                Path.of("src", "test", "resources", "uvl", "featureModelSerializeResult.uvl"),
                Charset.defaultCharset()));

        if (result.isEmpty()) {
            Assertions.fail();
        }

        IFeatureModel parsedFeatureModel = result.get();

        // testing root
        IFeature rootFeature = parsedFeatureModel.getFeature("root").get();
        List<String> rootChildrenNames = rootFeature.getFeatureTree().get().getChildren().stream()
                .map((it) -> it.getFeature().getName().get())
                .collect(Collectors.toList());
        Assertions.assertEquals(3, rootChildrenNames.size());
        Assertions.assertTrue(rootChildrenNames.contains("Test1"));
        Assertions.assertTrue(rootChildrenNames.contains("Test2"));
        Assertions.assertTrue(rootChildrenNames.contains("Test7"));

        // testing Test1 feature
        IFeature test1Feature = parsedFeatureModel.getFeature("Test1").get();
        Assertions.assertTrue(test1Feature.getFeatureTree().get().getGroup().isAnd());
        Assertions.assertTrue(test1Feature.getFeatureTree().get().isOptional());
        List<String> test1ChildrenNames = test1Feature.getFeatureTree().get().getChildren().stream()
                .map((it) -> it.getFeature().getName().get())
                .collect(Collectors.toList());
        Assertions.assertEquals(2, test1ChildrenNames.size());
        Assertions.assertTrue(test1ChildrenNames.contains("Test3"));
        Assertions.assertTrue(test1ChildrenNames.contains("Test4"));

        // testing Test2 feature
        IFeature test2Feature = parsedFeatureModel.getFeature("Test2").get();
        Assertions.assertTrue(test2Feature.getFeatureTree().get().getGroup().isAnd());
        Assertions.assertTrue(test2Feature.getFeatureTree().get().isOptional());
        List<String> test2ChildrenNames = test2Feature.getFeatureTree().get().getChildren().stream()
                .map((it) -> it.getFeature().getName().get())
                .collect(Collectors.toList());
        Assertions.assertEquals(2, test2ChildrenNames.size());
        Assertions.assertTrue(test2ChildrenNames.contains("Test5"));
        Assertions.assertTrue(test2ChildrenNames.contains("Test6"));

        // testing Test3 feature
        IFeature test3Feature = parsedFeatureModel.getFeature("Test3").get();
        Assertions.assertTrue(test3Feature.getFeatureTree().get().getGroup().isAlternative());
        Assertions.assertTrue(test3Feature.getFeatureTree().get().getChildren().isEmpty());

        // testing Test4 feature
        IFeature test4Feature = parsedFeatureModel.getFeature("Test4").get();
        Assertions.assertTrue(test4Feature.getFeatureTree().get().getGroup().isAlternative());
        Assertions.assertTrue(test4Feature.getFeatureTree().get().getChildren().isEmpty());

        // testing Test5 feature
        IFeature test5Feature = parsedFeatureModel.getFeature("Test5").get();
        Assertions.assertTrue(test5Feature.getFeatureTree().get().getGroup().isOr());
        Assertions.assertTrue(test5Feature.getFeatureTree().get().getChildren().isEmpty());

        // testing Test6 feature
        IFeature test6Feature = parsedFeatureModel.getFeature("Test6").get();
        Assertions.assertTrue(test6Feature.getFeatureTree().get().getGroup().isOr());
        Assertions.assertTrue(test6Feature.getFeatureTree().get().getChildren().isEmpty());

        // testing Test7 feature
        IFeature test7Feature = parsedFeatureModel.getFeature("Test7").get();
        Assertions.assertTrue(test7Feature.getFeatureTree().get().getGroup().isAnd());
        Assertions.assertTrue(test7Feature.getFeatureTree().get().isMandatory());
        Assertions.assertTrue(test7Feature.getFeatureTree().get().getChildren().isEmpty());

        Assertions.assertEquals(1, parsedFeatureModel.getConstraints().size());
        IFormula constraint =
                parsedFeatureModel.getConstraints().iterator().next().getFormula();
        IFormula constraint2 = featureModel.getConstraints().iterator().next().getFormula();

        Boolean notEquivalent = Computations.of((IFormula) new Not(new BiImplies(constraint, constraint2)))
                .map(ComputeNNFFormula::new)
                .map(ComputeCNFFormula::new)
                .map(ComputeBooleanClauseList::new)
                .map(Computations::getKey)
                .map(ComputeSatisfiableSAT4J::new)
                .compute();

        Assertions.assertFalse(notEquivalent);
    }
    
    @Test
    void testUVLFileToFeatureModelToUVLFile() throws IOException {

        Path uvlFile = Path.of("src", "test", "resources", "uvl", "featureModelSerializeResult.uvl" );
        //Paths.get
        
        String fileContent = new String(Files.readAllBytes(uvlFile), StandardCharsets.UTF_8);

        IFormat<IFeatureModel> format = new UVLFeatureModelFormat();
        Result<IFeatureModel> parseResult = format.parse(new FileInputMapper(uvlFile, StandardCharsets.UTF_8));

        Assertions.assertTrue(parseResult.isPresent(), "Parsing of UVL file failed");
        IFeatureModel parsedFeatureModel = parseResult.get();

        Result<String> serializedResult = format.serialize(parsedFeatureModel);

        Assertions.assertTrue(serializedResult.isPresent(), "Serialization of IFeatureModel failed");
        String serializedContent = serializedResult.get();
        
        Assertions.assertTrue(Objects.equals(fileContent.replaceAll("\\r", ""), serializedContent.replaceAll("\\r", "")), "Serialized content does not match the original file content");
    }
    
    @Test
    void testFeatureModeltoUVLtoFeatureModel() throws IOException {
    	
    	IFeatureModel originalFeatureModel = featureModel;
    	
    	IFormat<IFeatureModel> format = new UVLFeatureModelFormat();
    	
    	Result<String> serializedFeatureModel = format.serialize(originalFeatureModel);
    	Assertions.assertTrue(serializedFeatureModel.isPresent(), "Serialization of IFeatureModel failed");
    	
    	String serializedFeatureModelString = serializedFeatureModel.get();
    	
    	
    	Result<IFeatureModel> parsedFeatureModelResult = format.parse(new StringInputMapper(serializedFeatureModelString, StandardCharsets.UTF_8, "uvl"));
    	Assertions.assertTrue(parsedFeatureModelResult.isPresent(), "Parsing of UVL file failed");
    	
    	IFeatureModel parsedFeatureModel = parsedFeatureModelResult.get();

    	System.out.println(serializedFeatureModelString);
    	
    	String originalContraints = ""; 
    	int remainingLength = serializedFeatureModelString.length();
    	for(int i = 0; i < serializedFeatureModelString.length(); i++) {
    		remainingLength--;
    		if(serializedFeatureModelString.charAt(i) == '(') {
    			while(remainingLength > 0) {
    				remainingLength--;
    				i++;
    				switch(serializedFeatureModelString.charAt(i)) {
    					case '&':
    						originalContraints += "and, "; 
    						break;
    					case '<':
    						originalContraints += "biimplies, ";
    						i += 2;
    						remainingLength -= 2; 
    						break;
    					case '=':
    						originalContraints += "implies, ";
    						i++;
    						remainingLength--;
    						break;
    					case '!':
    						originalContraints += "..., ";
    						break;
    				}
    			}
    		}
    	}
    	originalContraints = originalContraints.substring(0,originalContraints.length()-2);
    	System.out.println(originalContraints);
    	
    	//Assertions.assertEquals(originalFeatureModel, parsedFeatureModel, "Parsed FeatureModel does not match the original FeatureModel");
        
    	//Collection<IConstraint> originalConstraints = originalFeatureModel.getConstraints();
    	//Collection<IConstraint> parsedConstraints = parsedFeatureModel.getConstraints();
    	
    	List<IConstraint> originalConstraintsList = originalFeatureModel.getConstraints().stream().collect(Collectors.toList());
    	List<IConstraint> parsedConstraintsList = parsedFeatureModel.getConstraints().stream().collect(Collectors.toList());
    	
    	
    	Assertions.assertEquals(originalConstraintsList, parsedConstraintsList, "Parsed Constraints does not match the original Constraints");
    }
    
}
