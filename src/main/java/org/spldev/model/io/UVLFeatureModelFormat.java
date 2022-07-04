/* -----------------------------------------------------------------------------
 * Formula Lib - Library to represent and edit propositional formulas.
 * Copyright (C) 2021-2022  Sebastian Krieter
 *
 * This file is part of Formula Lib.
 *
 * Formula Lib is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * Formula Lib is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Formula Lib.  If not, see <https://www.gnu.org/licenses/>.
 *
 * See <https://github.com/skrieter/formula> for further information.
 * -----------------------------------------------------------------------------
 */
package org.spldev.model.io;

import de.neominik.uvl.UVLParser;
import org.spldev.model.FeatureModel;
import org.spldev.util.data.Result;
import org.spldev.util.io.format.Format;
import org.spldev.util.io.format.Input;

/**
 * Parses and writes feature models from and to UVL files.
 *
 * @author Dominik Engelhardt
 * @author Elias Kuiter
 */
public class UVLFeatureModelFormat implements Format<FeatureModel> {

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
		return "UVL";
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
	public Result<FeatureModel> parse(Input source) {
		final Object result = UVLParser.parse(source.toString()); // todo imports
		return null;
	}
}
