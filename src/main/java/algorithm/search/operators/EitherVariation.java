package algorithm.search.operators;

import java.util.Arrays;

import org.moeaframework.core.PRNG;
import org.moeaframework.core.Solution;
import org.moeaframework.core.Variation;

public class EitherVariation implements Variation {

    private final Variation var1;
    private final Variation var2;

    private double prob1;
    private double prob2;

    public EitherVariation(Variation var1, Variation var2, double prob1, double prob2) {
        this.var1 = var1;
        this.var2 = var2;
        this.prob1 = prob1;
        this.prob2 = prob2;
    }

    /**
	 * Returns the number of solutions that must be supplied to the
	 * {@code evolve} method.
	 * 
	 * @return the number of solutions that must be supplied to the
	 *         {@code evolve} method
	 */
    @Override
    public int getArity() {
        return Math.max(this.var1.getArity(), this.var2.getArity());
    }

    /**
	 * Evolves one or more parent solutions (specified by {@code getArity}) and
	 * produces one or more child solutions. By contract, the parents must not
	 * be modified. The copy constructor should be used to create copies of the
	 * parents with these copies subsequently modified.
	 * 
	 * @param parents the array of parent solutions
	 * @return an array of child solutions
	 * @throws IllegalArgumentException if an incorrect number of parents was
	 *         supplied {@code (parents.length != getArity())}
	 */
    @Override
    public Solution[] evolve(Solution[] parents) {
        double randomOp = PRNG.nextDouble();
        if (randomOp < this.prob1) {
            return var1.evolve(Arrays.copyOfRange(parents, 0, var1.getArity()));
        }
        else {
            return var2.evolve(Arrays.copyOfRange(parents, 0, var2.getArity()));
        }
    }

    public void setProbabilities(double prob1, double prob2) {
        this.prob1 = prob1;
        this.prob2 = prob2;
    }

    public Variation getVar1() {
        return var1;
    }

    public Variation getVar2() {
        return var2;
    }
}
