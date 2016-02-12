package com.ontotext.ehri.georecon;

import com.ontotext.ehri.georecon.place.Place;
import com.ontotext.ehri.georecon.place.PlaceIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Reconcile access points using a place index built from the GeoNames RDF dump.
 */
public class Reconciler {
    private static final Logger LOGGER = LoggerFactory.getLogger(Reconciler.class);

    // separator for columns in file
    public static final String COLUMN_SEPARATOR = "\t";
    public static final Pattern COLUMN_SPLITTER = Pattern.compile(COLUMN_SEPARATOR);

    // separator for items in lists
    public static final String LIST_SEPARATOR = ",";
    public static final Pattern LIST_SPLITTER = Pattern.compile(LIST_SEPARATOR);

    // some access-point types in input file
    private static final String CORP_TYPE = "corporateBodyAccess";
    private static final String CREA_TYPE = "creatorAccess";
    private static final String GENR_TYPE = "genreAccess";
    private static final String PERS_TYPE = "personAccess";

    /**
     * Run the program.
     * @param args Command-line arguments: <index file> <stopword file> <input file> <input column> <type column> <output file> <output column>.
     */
    public static void main(String[] args) {

        // check arguments
        if (args.length != 6) {
            System.out.println("USAGE: java " + Reconciler.class.getName() +
                    " <index file> <stopword file> <input file> <input column> <type column> <output file> <output column>");
            System.exit(0);
        }

        File indexFile = new File(args[0]);
        File stopwordFile = new File(args[1]);
        File inputFile = new File(args[2]);
        String inputColumnName = args[3];
        String typeColumnName = args[4];
        File outputFile = new File(args[5]);
        String outputColumnName = args[6];

        try {
            LOGGER.info("loading index...");
            long start = System.currentTimeMillis();
            PlaceIndex index = Tools.deserializeIndex(indexFile);
            long time = System.currentTimeMillis() - start;
            LOGGER.info("index loaded in " + time + " ms");

            LOGGER.info("reading stopwords...");
            start = System.currentTimeMillis();
            FileReader fileReader = new FileReader(stopwordFile);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            Set<String> stopwords = new HashSet<String>();
            String line;

            try {

                // add stopwords
                while ((line = bufferedReader.readLine()) != null) {
                    stopwords.add(PlaceIndex.normalizeName(line));
                }

            } finally {
                bufferedReader.close();
                fileReader.close();
            }

            time = System.currentTimeMillis() - start;
            LOGGER.info("stopwords read in " + time + " ms");

            LOGGER.info("reconciling access points...");
            start = System.currentTimeMillis();
            fileReader = new FileReader(inputFile);
            bufferedReader = new BufferedReader(fileReader);
            FileWriter fileWriter = new FileWriter(outputFile);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);

            try {
                line = bufferedReader.readLine();

                // check if input file is empty
                if (line == null) {
                    LOGGER.error("input file is empty: " + inputFile.getAbsolutePath());
                    System.exit(1);
                }

                // split line into fields
                String[] fields = COLUMN_SPLITTER.split(line);
                int inputColumn = -1;
                int typeColumn = -1;

                // find the indexes of the input column and type column
                for (int i = 0; i < fields.length; i++) {
                    if (fields[i].equals(inputColumnName)) inputColumn = i;
                    else if (fields[i].equals(typeColumnName)) typeColumn = i;
                }

                // check if indexes were found
                if (inputColumn == -1) {
                    LOGGER.error("no input column with name: " + inputColumnName);
                    System.exit(1);
                } else if (typeColumn == -1) {
                    LOGGER.warn("no type column with name: " + typeColumnName);
                }

                // write header line
                bufferedWriter.write(outputColumnName + COLUMN_SEPARATOR + line + "\n");

                // process each line
                while ((line = bufferedReader.readLine()) != null) {
                    fields = COLUMN_SPLITTER.split(line);
                    if (fields[typeColumn].equals(CORP_TYPE) ||
                            fields[typeColumn].equals(CREA_TYPE) ||
                            fields[typeColumn].equals(GENR_TYPE) ||
                            fields[typeColumn].equals(PERS_TYPE)) continue;

                    // split list of atoms and reconcile them
                    String[] atoms = LIST_SPLITTER.split(fields[inputColumn]);

                    // remove stopwords
                    for (String atom : atoms) {
                        if (stopwords.contains(PlaceIndex.normalizeName(atom))) atom = null;
                    }

                    // reconcile
                    Place place = reconcileDeep(index, atoms);

                    // write result
                    String result = "";
                    if (place != null) result = place.toString();
                    bufferedWriter.write(result + COLUMN_SEPARATOR + line + "\n");
                }

            } catch (IOException e) {
                LOGGER.error("exception while reconciling", e);
            } finally {
                bufferedWriter.close();
                fileWriter.close();
                bufferedReader.close();
                fileReader.close();
            }

            time = System.currentTimeMillis() - start;
            LOGGER.info("access points reconciled in " + time + " ms");
        } catch (IOException e) {
            LOGGER.error("exception while reconciling", e);
        }
    }

    /**
     * Shallow reconciliation considers only the most relevant match for each atom. For places that belong to the same
     * lineage, it chooses the most specific place (e.g. "Amsterdam" from "Netherlands, Noord-Holland, Amsterdam").
     * Otherwise, it chooses the closest common ancestor (e.g. "Netherlands" from "Amsterdam, Utrecht, Groningen").
     * @param index The index to use for lookup.
     * @param atoms The atomic access points.
     * @return The chosen place, or null if none of the atoms matches any place.
     */
    public static Place reconcileShallow(PlaceIndex index, String[] atoms) {
        Set<Place> candidates = new HashSet<Place>();

        // for each atom, add the most relevant match to the set of candidates
        for (String atom : atoms) {
            if (atom == null) continue;
            Place match = index.getOne(atom);
            if (match != null) candidates.add(match);
        }

        // initialize merged lineage
        Deque<Set<Place>> mergedLineage = new LinkedList<Set<Place>>();

        // iterate through candidate lineages
        for (Place candidate : candidates) {
            Deque<Place> lineage = candidate.lineage();

            // extend merged lineage if necessary
            while (mergedLineage.size() < lineage.size()) {
                mergedLineage.add(new HashSet<Place>());
            }

            Iterator<Set<Place>> mergedIterator = mergedLineage.iterator();

            // add each place in the candidate lineage to the merged lineage
            for (Place place : lineage) {
                mergedIterator.next().add(place);
            }
        }

        Place chosenOne = null;

        for (Set<Place> places : mergedLineage) {

            // move the chosen one downwards till lineages diverge
            if (places.size() == 1) chosenOne = places.iterator().next();
            else break;
        }

        if (chosenOne != null && chosenOne.equals(Place.ROOT)) return null;
        return chosenOne;
    }

    /**
     * Deep reconciliation tries to find a combination of matches for atoms, such that there is a descendant relation
     * between the matches of all atoms. If such a combination exists, it returns the most specific match. Otherwise,
     * it falls back to shallow reconciliation, which returns the closest common ancestor from the best matches only.
     * @param index The index to use for lookup.
     * @param atoms The atomic access points.
     * @return The chosen place, or null if none of the atoms matches any place.
     */
    public static Place reconcileDeep(PlaceIndex index, String[] atoms) {
        Set<Set<Place>> candidates = new HashSet<Set<Place>>();

        // for each atom, add all matches to the set of candidates
        for (String atom : atoms) {
            if (atom == null) continue;
            Set<Place> matches = index.get(atom);
            if (matches != null) candidates.add(matches);
        }

        // return null if there are no matches for any of the atoms
        if (candidates.isEmpty()) return null;

        // pick a random candidate out of the set
        Set<Place> randomCandidate = candidates.iterator().next();
        candidates.remove(randomCandidate);
        Place chosenOne = null;

        // iterate through the matches for the random candidate
        for (Place randomCandidateMatch : randomCandidate) {
            chosenOne = randomCandidateMatch;

            // iterate through the other candidates
            for (Set<Place> otherCandidate : candidates) {
                Place commonDescendant = null;

                // iterate through the matches for the other candidate
                for (Place otherCandidateMatch : otherCandidate) {
                    commonDescendant = findDescendant(randomCandidateMatch, otherCandidateMatch);

                    // stop iterating if there is a common descendant and update the chosen one
                    if (commonDescendant != null) {
                        chosenOne = findDescendant(chosenOne, commonDescendant);
                        if (chosenOne == null) return reconcileShallow(index, atoms);
                        break;
                    }
                }

                // go to the next match if there is no common descendant and reset the chosen one
                if (commonDescendant == null) {
                    chosenOne = null;
                    break;
                }
            }

            // stop iterating if there are no candidates without a common descendant
            if (chosenOne != null) {
                break;
            }
        }

        // return the chosen one if found
        if (chosenOne != null) return chosenOne;

        // return the closest common ancestor among the best matches as fallback
        return reconcileShallow(index, atoms);
    }

    /**
     * Find the descendant among two places.
     * @param one The first of the two places.
     * @param two The second of the two places.
     * @return The descendant among the two places, or null if there is no such relation between them.
     */
    private static Place findDescendant(Place one, Place two) {
        Place closestCommon = one.closestCommon(two);
        if (one.equals(closestCommon)) return two;
        if (two.equals(closestCommon)) return one;
        return null;
    }
}
