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
 * See <https://github.com/FeatJAR> for further information.
 */
package de.featjar.feature.model.io;

import static org.junit.jupiter.api.Assertions.assertTrue;

import de.featjar.base.FeatJAR;
import de.featjar.base.computation.Cache;
import de.featjar.base.data.Result;
import de.featjar.base.io.IO;
import de.featjar.base.log.CallerFormatter;
import de.featjar.base.log.Log;
import de.featjar.base.log.TimeStampFormatter;
import de.featjar.feature.model.IFeatureModel;
import de.featjar.feature.model.io.uvl.UVLFeatureModelFormat;
import de.featjar.feature.model.transformer.ComputeFormula;
import de.featjar.formula.structure.Expressions;
import de.featjar.formula.structure.formula.IFormula;
import java.io.IOException;
import java.nio.file.Paths;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class UVLFeatureModelFormatTest {
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

    @BeforeAll
    public static void init() {
        FeatJAR.configure()
                .log(c -> c.logToSystemOut(Log.Verbosity.MESSAGE, Log.Verbosity.INFO, Log.Verbosity.PROGRESS))
                .log(c -> c.logToSystemErr(Log.Verbosity.ERROR, Log.Verbosity.WARNING))
                .log(c -> c.addFormatter(new TimeStampFormatter()))
                .log(c -> c.addFormatter(new CallerFormatter()))
                .cache(c -> c.setCachePolicy(Cache.CachePolicy.CACHE_NONE))
                .initialize();
    }
}
