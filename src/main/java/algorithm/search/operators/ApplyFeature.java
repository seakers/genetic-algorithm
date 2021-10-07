package algorithm.search.operators;

import org.moeaframework.core.Variation;
import org.moeaframework.core.variable.BinaryVariable;

import algorithm.search.problems.Assigning.AssigningArchitecture;

import org.moeaframework.core.Solution;
import org.moeaframework.core.Variable;

public class ApplyFeature implements Variation {
    private final String feature;
    private final int numInstruments;
    private final int numOrbits;

    public ApplyFeature(String feature) {
        this.feature = feature;
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
            // Variables start at 1 because AssigningArchitecture has a combinatorial variable first
            for (int i = 1; i < child.getNumberOfVariables(); i++) {
                Variable var = child.getVariable(i);
                if (var instanceof BinaryVariable) {
                    BinaryVariable binVar = (BinaryVariable)var;
                    //check if the number of satellites is within the allowable bounds
                    while (constelVar.getNumberOfSatellites() < constelVar.getSatelliteBound().getLowerBound()) {
                        //turn on a random satellite that is off
                        ArrayList<BooleanSatelliteVariable> offSats = new ArrayList<>();
                        for (SatelliteVariable sat : constelVar.getSatelliteVariables()) {
                            if (!((BooleanSatelliteVariable) sat).getManifest()) {
                                offSats.add((BooleanSatelliteVariable) sat);
                            }
                        }
                        int select = PRNG.nextInt(offSats.size());
                        offSats.get(select).setManifest(true);
                    }
                    while (constelVar.getNumberOfSatellites() > constelVar.getSatelliteBound().getUpperBound()) {
                        //turn off a random satellite that is on
                        ArrayList<BooleanSatelliteVariable> onSats = new ArrayList<>();
                        for (SatelliteVariable sat : constelVar.getSatelliteVariables()) {
                            if (((BooleanSatelliteVariable) sat).getManifest()) {
                                onSats.add((BooleanSatelliteVariable) sat);
                            }
                        }
                        int select = PRNG.nextInt(onSats.size());
                        onSats.get(select).setManifest(false);
                    }
                } else if(var instanceof ConstellationVariable){
                    ConstellationVariable constelVar = (ConstellationVariable)var;
                    //check if the number of satellites is within the allowable bounds
                    if (constelVar.getNumberOfSatellites() < constelVar.getSatelliteBound().getLowerBound()) {
    
                    } 
                    while(constelVar.getNumberOfSatellites() > constelVar.getSatelliteBound().getUpperBound()) {
                        //remove a random sallite from the chromosome
                        ArrayList<SatelliteVariable> sats = new ArrayList<>(constelVar.getSatelliteVariables());
                        sats.remove(PRNG.nextInt(sats.size()));
                        constelVar.setSatelliteVariables(sats);
                    }
                }
            }
        }
        return new Solution[]{child};
    }
    
}
