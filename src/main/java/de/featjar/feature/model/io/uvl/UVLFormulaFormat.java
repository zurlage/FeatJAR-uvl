package de.featjar.feature.model.io.uvl;

import de.featjar.base.data.Result;
import de.featjar.base.io.format.IFormat;
import de.featjar.base.io.input.AInputMapper;
import de.featjar.formula.analysis.combinations.BinomialCalculator;
import de.featjar.formula.structure.formula.IFormula;
import de.featjar.formula.structure.formula.connective.And;
import de.featjar.formula.structure.formula.connective.Not;
import de.featjar.formula.structure.formula.connective.Or;

public class UVLFormulaFormat implements IFormat<IFormula> {

    private BinomialCalculator bc;

    public UVLFormulaFormat() {
        bc = new BinomialCalculator(100,100);
    }

    @Override
    public Result<IFormula> parse(AInputMapper inputMapper) {
        return Result.empty();
    }

    @Override
    public Result<String> serialize(IFormula fm) {
        return Result.empty();
    }

    @Override
    public boolean supportsParse() {
        return true;
    }

    @Override
    public boolean supportsSerialize() {
        return true;
    }

    @Override
    public String getFileExtension() {
        return "uvl";
    }

    @Override
    public String getName() {
        return "Universal Variability Language";
    }

    protected IFormula[] nchoosek(IFormula[] elements, int k, boolean negated) {
        final int n = elements.length;

        // return tautology
        if ((k == 0) || (k == (n + 1))) {
            return new IFormula[] { new Or(new Not(elements[0]), elements[0])};
        }

        // return contradiction
        if ((k < 0) || (k > (n + 1))) {
            return new IFormula[] { new And(new Not(elements[0]), elements[0]) };
        }

        final IFormula[] newNodes = new IFormula[(int) binomial(n, k)];
        int j = 0;

        // negate all elements
        if (negated) {
            negateNodes(elements);
        }

        final IFormula[] clause = new IFormula[k];
        final int[] index = new int[k];

        // the position that is currently filled in clause
        int level = 0;
        index[level] = -1;

        while (level >= 0) {
            // fill this level with the next element
            index[level]++;
            // did we reach the maximum for this level
            if (index[level] >= (n - (k - 1 - level))) {
                // go to previous level
                level--;
            } else {
                clause[level] = elements[index[level]];
                if (level == (k - 1)) {
                    newNodes[j++] = new Or(clause);
                } else {
                    // go to next level
                    level++;
                    // allow only ascending orders (to prevent from duplicates)
                    index[level] = index[level - 1];
                }
            }
        }
        if (j != newNodes.length) {
            throw new RuntimeException("Pre-calculation of the number of elements failed!");
        }
        return newNodes;
    }

    public static long binomial(int n, int k) {
       return new BinomialCalculator(n, k).binomial();
    }

    protected static void negateNodes(IFormula[] nodes) {
        for (int i = 0; i < nodes.length; i++) {
            nodes[i] = new Not(nodes[i]);
        }
    }
}
