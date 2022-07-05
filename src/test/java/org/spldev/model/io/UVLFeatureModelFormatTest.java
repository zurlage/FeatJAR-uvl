package org.spldev.model.io;

import org.junit.jupiter.api.Test;
import org.spldev.model.FeatureModel;
import org.spldev.model.io.xml.XMLFeatureModelFormat;
import org.spldev.util.data.Result;
import org.spldev.util.io.FileHandler;

import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class UVLFeatureModelFormatTest {
	@Test
	public void main() {
		Result<FeatureModel> featureModelResult = FileHandler.load(Paths.get(
			"src/test/resources/testFeatureModels/Server.uvl"), new UVLFeatureModelFormat());
		assertTrue(featureModelResult.isPresent());
	}
}
