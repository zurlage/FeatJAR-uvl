/*
 * Copyright (C) 2024 FeatJAR-Development-Team
 *
 * This file is part of FeatJAR-feature-model.
 *
 * feature-model is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3.0 of the License,
 * or (at your option) any later version.
 *
 * feature-model is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with feature-model. If not, see <https://www.gnu.org/licenses/>.
 *
 * See <https://github.com/FeatureIDE/FeatJAR-feature-model> for further information.
 */
package de.featjar.feature.model.io.uvl;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import de.featjar.analysis.sat4j.computation.ComputeAtomicSetsSAT4J;
import de.featjar.analysis.sat4j.computation.ComputeContradictingClauses;
import de.featjar.analysis.sat4j.computation.ComputeCoreSAT4J;
import de.featjar.analysis.sat4j.computation.ComputeIndeterminateSat4J;
import de.featjar.analysis.sat4j.computation.ComputeRedundantClausesSat4J;
import de.featjar.analysis.sat4j.computation.ComputeSatisfiableSAT4J;
import de.featjar.analysis.sat4j.computation.ComputeSolutionCountSAT4J;
import de.featjar.analysis.sat4j.computation.ComputeSolutionsSAT4J;

import de.featjar.base.FeatJAR;
import de.featjar.base.computation.Computations;
import de.featjar.base.data.Result;
import de.featjar.base.io.IO;

import de.featjar.feature.model.IFeatureModel;
import de.featjar.feature.model.io.uvl.UVLFeatureModelFormat;
import de.featjar.feature.model.transformer.ComputeFormula;

import de.featjar.formula.VariableMap;
import de.featjar.formula.assignment.BooleanAssignment;
import de.featjar.formula.assignment.BooleanAssignmentList;
import de.featjar.formula.assignment.BooleanClauseList;
import de.featjar.formula.assignment.BooleanSolutionList;
import de.featjar.formula.assignment.ComputeBooleanClauseList;
import de.featjar.formula.computation.ComputeCNFFormula;
import de.featjar.formula.computation.ComputeNNFFormula;

/* Runs essential SAT4J analyses for a Feature-Model
 * 
 * 
 * @author Manuel Dittrich
 * @author Malcolm Schulz
 * @author Jason Sparkes
 */

public class ComputeAnalysis{
	
	public void runAnalysis(ComputeBooleanClauseList cnf) {

        	BooleanClauseList clauseList = cnf.compute();
        	VariableMap variables = clauseList.getVariableMap();
        	
        	boolean isSatisfiable = satisfiableAnalysis(clauseList);
        	if (isSatisfiable) {
            	indeterminantClausesAnalysis(cnf, variables);
    			contradictingClausesAnalysis(clauseList, variables);
    			coreFeatureAnalysis(clauseList, variables);
    			possibleSolutionAnalysis(clauseList, variables);
    			atomicSetsAnalysis(cnf, variables);
    			
    			// TODO: Fix redundant analysis
    			//redundantFeatureAnalysis(clauseList, variables);
        	}
        	else {
        		FeatJAR.log().message("Clauses are not satisfiable, analysis will not proceed.");
        	}
	}

	private void atomicSetsAnalysis(ComputeBooleanClauseList clauseList, VariableMap variables) {
		FeatJAR.log().message("Running compute atomic sets analysis");
		BooleanAssignmentList atomics = clauseList
				.map(ComputeAtomicSetsSAT4J::new)
				.compute();
		if (atomics.size() <= 0) {
			FeatJAR.log().message('\n' + "No atomic sets found!");
		}
		else {
			for (int i = 0; i < atomics.size(); i++) {
				FeatJAR.log().message('\n' + "Atomic sets: " + '\n' + getFeaturesBooleanAssignmentSolutions(atomics.get(i).get(), variables));
			}
		}
	}

	private void possibleSolutionAnalysis(BooleanClauseList clauseList, VariableMap variables) {
		FeatJAR.log().message("Running compute solutions analysis");
		BooleanSolutionList solutions = Computations.of(clauseList)
				.map(ComputeSolutionsSAT4J::new)
				.compute();
		for (int i = 0; i < solutions.size(); i++) {
			FeatJAR.log().message('\n' + "Computed solutions: " + '\n' + getFeaturesBooleanAssignmentSolutions(solutions.toAssignmentList().get(i).get(), variables));
		}
		BigInteger solutionNumber = Computations.of(clauseList)
				.map(ComputeSolutionCountSAT4J::new)
				.compute();
		FeatJAR.log().message('\n' + "There are " + solutionNumber.intValue() + " different solutions");
	}

	private void coreFeatureAnalysis(BooleanClauseList clauseList, VariableMap variables) {
		FeatJAR.log().message("Running dead and core features analysis");
		BooleanAssignment deadFeatures = Computations.of(clauseList)
				.map(ComputeCoreSAT4J::new)
				.compute();
		
		FeatJAR.log().message('\n' + "Computed core and dead SAT4J features: " + '\n' + getCoreFeaturesSAT4J(deadFeatures, variables));
	}
	
	// TODO: Fix redundant analysis
	private void redundantFeatureAnalysis(BooleanClauseList clauseList, VariableMap variables) {
		FeatJAR.log().message("Running redundant clauses analysis");
		BooleanClauseList redundantClauses = Computations.of(clauseList)
				.map(ComputeRedundantClausesSat4J::new)
				.compute();
		
		if (redundantClauses.size() == 0) {
			FeatJAR.log().message("No redundant clauses found");
		}
		else {
			for (int i = 0; i < redundantClauses.size(); i++) {
				FeatJAR.log().message("Redundant clauses: " + getFeaturesBooleanAssignment(redundantClauses.toAssignmentList().get(i).get(), variables));
			}
		}
	}
	
	private void indeterminantClausesAnalysis(ComputeBooleanClauseList clauseList, VariableMap variables) {
		FeatJAR.log().message("Running indeterminant clauses analysis");
		BooleanAssignment indeterminantClauses = clauseList
				.map(ComputeIndeterminateSat4J::new)
				.compute();
		FeatJAR.log().message('\n' + "Indeterminant clauses: " + '\n'+ getFeaturesBooleanAssignment(indeterminantClauses, variables));
	}

	private boolean satisfiableAnalysis(BooleanClauseList clauseList) {
		FeatJAR.log().message("Running satisfiable formula analysis");
		Boolean satisfiable = Computations.of(clauseList)
				.map(ComputeSatisfiableSAT4J::new)
				.compute();
		FeatJAR.log().message('\n' + "Satisfiable: " + '\n' + satisfiable.toString());
		return satisfiable;
	}

	private void contradictingClausesAnalysis(BooleanClauseList clauseList, VariableMap variables) {
		FeatJAR.log().message("Running contradicting clauses analysis");
		BooleanClauseList contradictingClauses = Computations.of(clauseList)
				.map(ComputeContradictingClauses::new)
				.compute();
		if (contradictingClauses.size() == 0) {
				FeatJAR.log().message('\n' + "No contradicting clauses found!");
		}
		else {
			for (int i = 0; i < contradictingClauses.size(); i++) {
				FeatJAR.log().message('\n' + "Contradicting clauses: " + '\n' + getFeaturesBooleanAssignment(contradictingClauses.toAssignmentList().get(i).get(), variables));
			}
		}
	}
	
	public String getFeaturesBooleanAssignment(BooleanAssignment featureList, VariableMap variables) {
		if (featureList.size() == 0) {
			return "No features matching";
		}
		
		StringBuilder featureString = new StringBuilder();
		for(int i = 0; i < featureList.size(); i++) {
			if(featureList.get(i) > 0) {

				featureString.append(variables.get(featureList.get(i)).get() + ", ");
			}
		}
		if (featureString.length() > 2) {
			featureString.delete(featureString.length() - 2, featureString.length() - 1);
		}
		return featureString.toString();
	}
	
	public String getFeaturesBooleanAssignmentSolutions(BooleanAssignment featureList, VariableMap variables) {
		if (featureList.size() == 0) {
			return "No features matching";
		}
		
		StringBuilder featureString = new StringBuilder();
		for(int i = 0; i < featureList.size(); i++) {
			if(featureList.get(i) > 0) {
				featureString.append(variables.get(featureList.get(i)).get() + ", ");
			}
			else {
				featureString.append("Not " + variables.get(Math.abs(featureList.get(i))).get() + ", ");
			}
		}
		if (featureString.length() > 2) {
			featureString.delete(featureString.length() - 2, featureString.length() - 1);
		}
		return featureString.toString();
	}
	
	public String getCoreFeaturesSAT4J(BooleanAssignment featureList, VariableMap variables) {
		if (featureList.size() == 0) {
			return "No features matching";
		}
		
		StringBuilder deadFeature = new StringBuilder("Dead features: ");
		StringBuilder coreFeature = new StringBuilder("Core features: ");
		StringBuilder featureString = new StringBuilder();
		
		for(int i = 0; i < featureList.size(); i++) {
			if(featureList.get(i) > 0) {
				coreFeature.append(variables.get(featureList.get(i)).get() + ", ");
			}
			else {
				deadFeature.append(variables.get(Math.abs(featureList.get(i))).get() + ", ");
			}
		}
		if (deadFeature.toString().equals("Dead features: ")) {
			deadFeature.append("No dead features found!");
		}
		else if (coreFeature.toString().equals("Core features: ")) {
			coreFeature.append("No core features found!");
		}
		else {
			deadFeature.delete(deadFeature.length() - 2, deadFeature.length() - 1);
			coreFeature.delete(coreFeature.length() - 2, coreFeature.length() - 1);
		}
		featureString.append(coreFeature.toString() + '\n' + deadFeature.toString());
		return featureString.toString();
	}
	
	public static void main(String[] args) {
		FeatJAR.initialize();
		Result<IFeatureModel> featureModel = IO.load(Paths.get(args[0]), new UVLFeatureModelFormat());
		ComputeBooleanClauseList clauseList = Computations.of(featureModel.get())
				.map(ComputeFormula::new)
    			.map(ComputeNNFFormula::new)
            	.map(ComputeCNFFormula::new)
            	.map(ComputeBooleanClauseList::new);

		new ComputeAnalysis().runAnalysis(clauseList);
	}
	
}