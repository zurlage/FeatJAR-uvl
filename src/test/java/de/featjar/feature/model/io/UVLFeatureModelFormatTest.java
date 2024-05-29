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

import de.featjar.base.FeatJAR;
import java.io.IOException;
import org.junit.jupiter.api.Test;

public class UVLFeatureModelFormatTest { // extends Common {

    /*
    @Test
    public void graphVizFeatureModelFormat() throws IOException {
        Result<IFeatureModel> featureModel =
                IO.load(Paths.get("src/test/resources/single/Server.uvl"), new UVLFeatureModelFormat());
        assertTrue(featureModel.isPresent(), featureModel.printProblems());
        Result<IFormula> formula =
                featureModel.toComputation().map(ComputeFormula::new).computeResult();
        assertTrue(formula.isPresent(), formula.printProblems());
        FeatJAR.log().info(Expressions.print(formula.get()));
    }
    */

    @Test
    void testConvertFormatCommand() throws IOException {
        // TODO: Write test
        System.out.println("Testing ConvertFormatCommand");
        // String testFile = new
        // String(Files.readAllBytes(Path.of("./src/test/java/de/featjar/res/testConvertFormatCommand.dimacs")));
        FeatJAR.main(
                "convert-format --input ./src/test/resources/xml/test.xml --format de.featjar.feature.model.io.uvl.UVLFormulaFormat"
                        .split(" "));
        // ProcessOutput output = runProcess(sat4jstring + " convert-format-sat4j --input
        // ../formula/src/testFixtures/resources/GPL/model.xml --format
        // de.featjar.formula.io.xml.XMLFeatureModelFormulaFormat");
        // Assertions.assertTrue(output.errorString.isBlank());
        // Assertions.assertEquals(output.outputString.trim(), testFile.trim());
    }
}
