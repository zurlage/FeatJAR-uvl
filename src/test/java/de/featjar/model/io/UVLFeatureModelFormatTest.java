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

import de.featjar.model.io.xml.GraphVizFeatureModelFormat;
import de.featjar.util.io.IOMapper;
import org.junit.jupiter.api.Test;
import de.featjar.model.FeatureModel;
import de.featjar.util.data.Result;
import de.featjar.util.io.IO;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class UVLFeatureModelFormatTest {
	@Test
	public void main() {
		Result<FeatureModel> featureModelResult = IO.load(Paths.get(
			"src/test/resources/multi/Server.uvl"), new UVLFeatureModelFormat(), IOMapper.Options.INPUT_FILE_HIERARCHY);
		System.out.println(featureModelResult.getProblems());
		assertTrue(featureModelResult.isPresent());
		//GraphVizFeatureModelFormat.openInBrowser(featureModelResult.get());
		//System.out.println(featureModelResult.get().getConstraints());
	}
}
