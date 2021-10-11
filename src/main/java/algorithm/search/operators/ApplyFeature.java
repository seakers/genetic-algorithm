package algorithm.search.operators;

import org.moeaframework.core.Variation;
import org.moeaframework.core.variable.BinaryVariable;

import algorithm.search.problems.Assigning.AssigningArchitecture;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.moeaframework.core.PRNG;
import org.moeaframework.core.Solution;
import org.moeaframework.core.Variable;

public class ApplyFeature implements Variation {
    private final String feature;
    private final int numInstruments;
    private final int numOrbits;

    public ApplyFeature(String feature, int numInstruments, int numOrbits) {
        this.feature = feature;
        this.numInstruments = numInstruments;
        this.numOrbits = numOrbits;
    }

    /**
	 * Returns the number of solutions that must be supplied to the
	 * {@code evolve} method.
	 * 
	 * @return the number of solutions that must be supplied to the
	 *         {@code evolve} method
	 */
	public int getArity() {
        return 1;
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
	public Solution[] evolve(Solution[] parents) throws IllegalArgumentException {
        if (parents.length != getArity()) {
            throw new IllegalArgumentException("The wrong number of parents were provided!");
        }
        Solution child = parents[0].copy();
        if (child instanceof AssigningArchitecture) {
            // First, preprocess feature to get all the information {baseFeature[orbits;instruments;numbers]}
            Pattern featurePattern = Pattern.compile("{(\\w+)\\[(\\d+(?:,\\d+)*)?;(\\d+(?:,\\d+)*)?;(\\d+(?:,\\d+)*)?\\]}");
            Matcher featureInfo = featurePattern.matcher(this.feature);
            String featureType = featureInfo.group(1);
            // Variables start at 1 because AssigningArchitecture has a combinatorial variable first
            if (featureType == "present") {
                String[] instruments = featureInfo.group(3).split(",", 0);
                this.applyPresentFeature((AssigningArchitecture)child, instruments);
            }
            else if (featureType == "absent") {
                String[] instruments = featureInfo.group(3).split(",", 0);
                this.applyAbsentFeature((AssigningArchitecture)child, instruments);
            }
            else if (featureType == "numOfInstruments") {
                String instruments = featureInfo.group(3);
                Integer number = Integer.parseInt(featureInfo.group(4));
                this.applyNumInstrumentsFeature((AssigningArchitecture)child, instruments, number);
            }
        }
        return new Solution[]{child};
    }

    public void applyPresentFeature(AssigningArchitecture solution, String[] instruments) {
        // First, check if solution already has feature
        boolean hasFeature = false;
        Integer instrument = Integer.parseInt(instruments[0]);
        for (int i = 1 + instrument; i < solution.getNumberOfVariables(); i += this.numInstruments) {
            if (((BinaryVariable)solution.getVariable(i)).get(0)) {
                hasFeature = true;
            }
        }
        // Then, if solution doesn't have feature, apply it randomly
        if (!hasFeature) {
            int randomOrbit = PRNG.nextInt(0, this.numOrbits);
            ((BinaryVariable)solution.getVariable(randomOrbit*this.numInstruments + instrument + 1)).set(0, true);
        }
    }

    public void applyAbsentFeature(AssigningArchitecture solution, String[] instruments) {
        // First, check if solution already has feature
        boolean hasFeature = false;
        Integer instrument = Integer.parseInt(instruments[0]);
        for (int i = 1 + instrument; i < solution.getNumberOfVariables(); i += this.numInstruments) {
            if (!((BinaryVariable)solution.getVariable(i)).get(0)) {
                hasFeature = true;
            }
        }
        // Then, if solution doesn't have feature, apply it randomly
        if (!hasFeature) {
            int randomOrbit = PRNG.nextInt(0, this.numOrbits);
            ((BinaryVariable)solution.getVariable(randomOrbit*this.numInstruments + instrument + 1)).set(0, false);
        }
    }

    public void applyNumInstrumentsFeature(AssigningArchitecture solution, String instruments, Integer number) {
        if (instruments.length() == 0) {
            //numOfInstruments[;;n]: Number of instruments in total
            boolean hasFeature = false;
            int count = 0;
            for (int i = 1; i < solution.getNumberOfVariables(); i += 1) {
                if (!((BinaryVariable)solution.getVariable(i)).get(0)) {
                    hasFeature = true;
                }
            }
            // Then, if solution doesn't have feature, apply it randomly
            if (!hasFeature) {
                int randomOrbit = PRNG.nextInt(0, this.numOrbits);
                ((BinaryVariable)solution.getVariable(randomOrbit*this.numInstruments + instrument + 1)).set(0, false);
            }
        }
        

        //numOfInstruments[;i;n]: Number of instrument i
        String[] instruments = featureInfo.group(3).split(",", 0);
        boolean hasFeature = false;
        Integer instrument = Integer.parseInt(instruments[0]);
        for (int i = 1 + instrument; i < solution.getNumberOfVariables(); i += this.numInstruments) {
            if (!((BinaryVariable)solution.getVariable(i)).get(0)) {
                hasFeature = true;
            }
        }
        // Then, if solution doesn't have feature, apply it randomly
        if (!hasFeature) {
            int randomOrbit = PRNG.nextInt(0, this.numOrbits);
            ((BinaryVariable)solution.getVariable(randomOrbit*this.numInstruments + instrument + 1)).set(0, false);
        }
        
        //numOfInstruments[;i,j,k;n]: Number of instruments in set {i, j, k} across all orbits
        String[] instruments = featureInfo.group(3).split(",", 0);
        boolean hasFeature = false;
        Integer instrument = Integer.parseInt(instruments[0]);
        for (int i = 1 + instrument; i < solution.getNumberOfVariables(); i += this.numInstruments) {
            if (!((BinaryVariable)solution.getVariable(i)).get(0)) {
                hasFeature = true;
            }
        }
        // Then, if solution doesn't have feature, apply it randomly
        if (!hasFeature) {
            int randomOrbit = PRNG.nextInt(0, this.numOrbits);
            ((BinaryVariable)solution.getVariable(randomOrbit*this.numInstruments + instrument + 1)).set(0, false);
        }

        // First, check if solution already has feature
        boolean hasFeature = false;
        Integer instrument = Integer.parseInt(instruments[0]);
        for (int i = 1 + instrument; i < solution.getNumberOfVariables(); i += this.numInstruments) {
            if (!((BinaryVariable)solution.getVariable(i)).get(0)) {
                hasFeature = true;
            }
        }
        // Then, if solution doesn't have feature, apply it randomly
        if (!hasFeature) {
            int randomOrbit = PRNG.nextInt(0, this.numOrbits);
            ((BinaryVariable)solution.getVariable(randomOrbit*this.numInstruments + instrument + 1)).set(0, false);
        }
    }
    
}
