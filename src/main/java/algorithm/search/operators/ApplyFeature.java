package algorithm.search.operators;

import org.moeaframework.core.Variation;
import org.moeaframework.core.variable.BinaryVariable;

import algorithm.search.problems.Assigning.AssigningArchitecture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.moeaframework.core.PRNG;
import org.moeaframework.core.Solution;

public class ApplyFeature implements Variation {
    private String feature;
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

    public void setFeature(String feature) {
        this.feature = feature;
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
            Pattern featurePattern = Pattern.compile("\\{(\\w+)\\[(\\d+(?:,\\d+)*)?;(\\d+(?:,\\d+)*)?;(\\d+(?:,\\d+)*)?\\]\\}");
            Matcher featureInfo = featurePattern.matcher(this.feature);
            featureInfo.matches();
            String featureType = featureInfo.group(1);
            // Variables start at 1 because AssigningArchitecture has a combinatorial variable first
            if (featureType.equals("present")) {
                String[] instruments = featureInfo.group(3).split(",", 0);
                this.applyPresentFeature((AssigningArchitecture)child, instruments);
            }
            else if (featureType.equals("absent")) {
                String[] instruments = featureInfo.group(3).split(",", 0);
                this.applyAbsentFeature((AssigningArchitecture)child, instruments);
            }
            else if (featureType.equals("numOfInstruments")) {
                String instruments = featureInfo.group(3);
                Integer number = Integer.parseInt(featureInfo.group(4));
                this.applyNumInstrumentsFeature((AssigningArchitecture)child, instruments, number);
            }
            else if (featureType.equals("together")) {
                String instruments = featureInfo.group(3);
                this.applyTogetherFeature((AssigningArchitecture)child, instruments);
            }
            else if (featureType.equals("separate")) {
                String instruments = featureInfo.group(3);
                this.applySeparateFeature((AssigningArchitecture)child, instruments);
            }
            else if (featureType.equals("emptyOrbit")) {
                String orbits = featureInfo.group(2);
                this.applyEmptyOrbitFeature((AssigningArchitecture)child, orbits);
            }
            else if (featureType.equals("numOrbits")) {
                String numbers = featureInfo.group(4);
                this.applyNumOrbitsFeature((AssigningArchitecture)child, numbers);
            }
            else if (featureType.equals("inOrbit")) {
                String orbits = featureInfo.group(2);
                String instruments = featureInfo.group(3);
                this.applyInOrbitFeature((AssigningArchitecture)child, orbits, instruments);
            }
            else if (featureType.equals("notInOrbit")) {
                String orbits = featureInfo.group(2);
                String instruments = featureInfo.group(3);
                this.applyNotInOrbitFeature((AssigningArchitecture)child, orbits, instruments);
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
            int randomOrbit = PRNG.nextInt(0, this.numOrbits-1);
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
            int randomOrbit = PRNG.nextInt(0, this.numOrbits-1);
            ((BinaryVariable)solution.getVariable(randomOrbit*this.numInstruments + instrument + 1)).set(0, false);
        }
    }

    public void applyNumInstrumentsFeature(AssigningArchitecture solution, String instruments, Integer number) {
        if (instruments == null || instruments.length() == 0) {
            //numOfInstruments[;;n]: Number of instruments in total
            boolean hasFeature = false;
            int count = 0;
            ArrayList<Integer> instrIndices = new ArrayList<>(); 
            for (int i = 1; i < solution.getNumberOfVariables(); i += 1) {
                if (((BinaryVariable)solution.getVariable(i)).get(0)) {
                    count += 1;
                    instrIndices.add(i);
                }
            }
            if (count == number) {
                hasFeature = true;
            }
            // Then, if solution doesn't have feature, apply it randomly
            if (!hasFeature) {
                Collections.shuffle(instrIndices);
                while (count != number) {
                    if (count > number) {
                        // Remove instruments until we have the right number
                        ((BinaryVariable)solution.getVariable(instrIndices.remove(0))).set(0, false);
                        count -= 1;
                    }
                    else {
                        // Add instruments until we have the right number
                        int randomInstrument = PRNG.nextInt(0, this.numOrbits*this.numInstruments-1);
                        BinaryVariable var = (BinaryVariable)solution.getVariable(randomInstrument + 1);
                        if (!var.get(0)) {
                            var.set(0, true);
                            count += 1;
                        }
                    }
                }
            }
        }
        else {
            //numOfInstruments[;i;n]: Number of instrument i
            //numOfInstruments[;i,j,k;n]: Number of instruments in set {i, j, k} across all orbits

            String[] instrumentsList = instruments.split(",", 0);
            ArrayList<Integer> instrArrList = new ArrayList<>();
            for (String s: instrumentsList) {
                instrArrList.add(Integer.parseInt(s));
            }
            boolean hasFeature = false;
            int count = 0;
            ArrayList<Integer> instrIndices = new ArrayList<>();
            for (int i = 1; i < solution.getNumberOfVariables(); i += 1) {
                int instrNumber = i % this.numInstruments;
                if (instrArrList.contains(instrNumber)) {
                    if (((BinaryVariable)solution.getVariable(i)).get(0)) {
                        count += 1;
                        instrIndices.add(i);
                    }
                }
            }
            if (count == number) {
                hasFeature = true;
            }
            // Then, if solution doesn't have feature, apply it randomly
            if (!hasFeature) {
                Collections.shuffle(instrIndices);
                while (count != number) {
                    if (count > number) {
                        // Remove instruments until we have the right number
                        ((BinaryVariable)solution.getVariable(instrIndices.remove(0))).set(0, false);
                        count -= 1;
                    }
                    else {
                        // Add instruments until we have the right number
                        int randomOrbit = PRNG.nextInt(0, this.numOrbits-1);
                        int randomInstrument = instrArrList.get(PRNG.nextInt(0, instrArrList.size()-1));
                        BinaryVariable var = (BinaryVariable)solution.getVariable(randomOrbit*this.numInstruments + randomInstrument + 1);
                        if (!var.get(0)) {
                            var.set(0, true);
                            count += 1;
                        }
                    }
                }
            }
        }
    }

    public void applyTogetherFeature(AssigningArchitecture solution, String instruments) {
        // together[;i,j,k;]: Instruments in set {i,j,k} are together in at least one orbit
        String[] instrumentsList = instruments.split(",", 0);
        ArrayList<Integer> instrArrList = new ArrayList<>();
        for (String s: instrumentsList) {
            instrArrList.add(Integer.parseInt(s));
        }
        boolean hasFeature = false;
        for (int o = 0; o < this.numOrbits; o += 1) {
            boolean togetherInOrbit = true;
            int startIndex = 1 + o*this.numInstruments;
            for (Integer instr: instrArrList) {
                if (!((BinaryVariable)solution.getVariable(startIndex + instr)).get(0)) {
                    togetherInOrbit = false;
                }
            }
            if (togetherInOrbit) {
                hasFeature = true;
                break;
            }
        }

        // Then, if solution doesn't have feature, apply it randomly
        if (!hasFeature) {
            // Add instruments until we have an orbit with all the instruments together
            int randomOrbit = PRNG.nextInt(0, this.numOrbits-1);
            int startIndex = 1 + randomOrbit*this.numInstruments;
            for (Integer instr: instrArrList) {
                ((BinaryVariable)solution.getVariable(startIndex + instr)).set(0, true);
            }
        }
    }

    public void applySeparateFeature(AssigningArchitecture solution, String instruments) {
        // separate[;i,j,k;]: Instruments in set {i,j,k} are always in separate orbits
        String[] instrumentsList = instruments.split(",", 0);
        ArrayList<Integer> instrArrList = new ArrayList<>();
        for (String s: instrumentsList) {
            instrArrList.add(Integer.parseInt(s));
        }
        boolean hasFeature = true;
        for (int o = 0; o < this.numOrbits; o += 1) {
            int count = 0;
            int startIndex = 1 + o*this.numInstruments;
            for (Integer instr: instrArrList) {
                if (((BinaryVariable)solution.getVariable(startIndex + instr)).get(0)) {
                    count += 1;
                }
            }
            if (count > 1) {
                hasFeature = false;
                break;
            }
        }

        // Then, if solution doesn't have feature, apply it randomly
        if (!hasFeature) {
            // Remove instruments until we have all orbits with separate instruments
            for (int o = 0; o < this.numOrbits; o += 1) {
                int startIndex = 1 + o*this.numInstruments;
                ArrayList<Integer> instrIndexes = new ArrayList<>();
                for (Integer instr: instrArrList) {
                    if (((BinaryVariable)solution.getVariable(startIndex + instr)).get(0)) {
                        instrIndexes.add(startIndex + instr);
                    }
                }
                Collections.shuffle(instrIndexes);
                while (instrIndexes.size() > 1) {
                    ((BinaryVariable)solution.getVariable(instrIndexes.remove(0))).set(0, false);
                }
            }
        }
    }

    public void applyEmptyOrbitFeature(AssigningArchitecture solution, String orbits) {
        // emptyOrbit[o;;]: No instruments in orbit o
        String[] orbitsList = orbits.split(",", 0);
        Integer orbit = Integer.parseInt(orbitsList[0]);
        boolean hasFeature = true;
        for (int i = 0; i < this.numInstruments; i += 1) {
            int index = 1 + orbit*this.numInstruments + i;
            if (((BinaryVariable)solution.getVariable(index)).get(0)) {
                hasFeature = false;
            }
        }

        // Then, if solution doesn't have feature, apply it randomly
        if (!hasFeature) {
            // Remove instruments until the orbit in question is empty
            for (int o = 0; o < this.numOrbits; o += 1) {
                for (int i = 0; i < this.numInstruments; i += 1) {
                    int index = 1 + orbit*this.numInstruments + i;
                    ((BinaryVariable)solution.getVariable(index)).set(0, false);
                }
            }
        }
    }

    public void applyNumOrbitsFeature(AssigningArchitecture solution, String numbers) {
        // numOrbits[;;n1,n2]: Between n1 and n2 orbits are used
        String[] numbersList = numbers.split(",", 0);
        Integer lowerBound = Integer.parseInt(numbersList[0]);
        Integer upperBound = lowerBound;
        if (numbersList.length > 1) {
            upperBound = Integer.parseInt(numbersList[1]);
        }
        boolean hasFeature = true;
        int count = 0;
        ArrayList<Integer> usedOrbits = new ArrayList<>();
        ArrayList<Integer> unusedOrbits = new ArrayList<>();
        for (int o = 0; o < this.numOrbits; o += 1) {
            boolean usedOrbit = false;
            for (int i = 0; i < this.numInstruments; i += 1) {
                int index = 1 + o*this.numInstruments + i;
                if (((BinaryVariable)solution.getVariable(index)).get(0)) {
                    usedOrbit = true;
                }
            }
            if (usedOrbit) {
                count += 1;
                usedOrbits.add(o);
            }
            else {
                unusedOrbits.add(o);
            }
        }
        hasFeature = count >= lowerBound && count <= upperBound;

        // Then, if solution doesn't have feature, apply it randomly
        if (!hasFeature) {
            Collections.shuffle(usedOrbits);
            Collections.shuffle(unusedOrbits);
            while (count > upperBound || count < lowerBound) {
                if (count > upperBound) {
                    // Remove instruments until we have enough empty orbits
                    int orbit = usedOrbits.remove(0);
                    for (int i = 0; i < this.numInstruments; i += 1) {
                        int index = 1 + orbit*this.numInstruments + i;
                        ((BinaryVariable)solution.getVariable(index)).set(0, false);
                    }

                    count -= 1;
                }
                else if (count < lowerBound) {
                    // Add instruments until we have enough full orbits
                    int orbit = unusedOrbits.remove(0);
                    int randomInstrument = PRNG.nextInt(0, this.numInstruments-1);
                    int index = 1 + orbit*this.numInstruments + randomInstrument;
                    ((BinaryVariable)solution.getVariable(index)).set(0, true);
                    count += 1;
                }
            }
        }
    }
    
    public void applyInOrbitFeature(AssigningArchitecture solution, String orbits, String instruments) {
        // inOrbit[o;i,j,k;]: Instruments i,j,k in orbit o
        Integer orbit = Integer.parseInt(orbits);
        String[] instrumentsList = instruments.split(",", 0);
        ArrayList<Integer> instrArrList = new ArrayList<>();
        for (String s: instrumentsList) {
            instrArrList.add(Integer.parseInt(s));
        }
        boolean hasFeature = true;
        for (Integer i: instrArrList) {
            int index = 1 + orbit*this.numInstruments + i;
            if (!((BinaryVariable)solution.getVariable(index)).get(0)) {
                hasFeature = false;
            }
        }

        // Then, if solution doesn't have feature, apply it randomly
        if (!hasFeature) {
            // Add instruments to orbit o to ensure feature is there
            for (Integer i: instrArrList) {
                int index = 1 + orbit*this.numInstruments + i;
                ((BinaryVariable)solution.getVariable(index)).set(0, true);
            }
        }
    }

    public void applyNotInOrbitFeature(AssigningArchitecture solution, String orbits, String instruments) {
        // inOrbit[o;i,j,k;]: Instruments i,j,k in orbit o
        Integer orbit = Integer.parseInt(orbits);
        String[] instrumentsList = instruments.split(",", 0);
        ArrayList<Integer> instrArrList = new ArrayList<>();
        for (String s: instrumentsList) {
            instrArrList.add(Integer.parseInt(s));
        }
        boolean hasFeature = true;
        for (Integer i: instrArrList) {
            int index = 1 + orbit*this.numInstruments + i;
            if (((BinaryVariable)solution.getVariable(index)).get(0)) {
                hasFeature = false;
            }
        }

        // Then, if solution doesn't have feature, apply it randomly
        if (!hasFeature) {
            // Add instruments to orbit o to ensure feature is there
            for (Integer i: instrArrList) {
                int index = 1 + orbit*this.numInstruments + i;
                ((BinaryVariable)solution.getVariable(index)).set(0, false);
            }
        }
    }
}
