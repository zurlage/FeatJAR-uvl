package org.spldev.model.io;

import org.junit.jupiter.api.Test;
import org.spldev.model.FeatureModel;
import org.spldev.util.data.Result;
import org.spldev.util.io.IO;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class UVLFeatureModelFormatTest {
	@Test
	public void main() {
		Result<FeatureModel> featureModelResult = IO.load(Paths.get(
			"src/test/resources/testFeatureModels/Server.uvl"), new UVLFeatureModelFormat());
		assertTrue(featureModelResult.isPresent());
	}
}
