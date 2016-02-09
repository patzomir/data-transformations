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
    private static final String PERS_TYPE = "personAccess";

    /**
     * Run the program.
     * @param args Command-line arguments: <index file> <input file> <input column> <type column> <output file> <output column>.
     */
    public static void main(String[] args) {

        // check arguments
        if (args.length != 6) {
            System.out.println("USAGE: java " + Reconciler.class.getName() +
                    " <index file> <input file> <input column> <type column> <output file> <output column>");
            System.exit(0);
        }

        File indexFile = new File(args[0]);
        File inputFile = new File(args[1]);
        String inputColumnName = args[2];
        String typeColumnName = args[3];
        File outputFile = new File(args[4]);
        String outputColumnName = args[5];

        try {
            LOGGER.info("loading index...");
            long start = System.currentTimeMillis();
            PlaceIndex index = Tools.deserializeIndex(indexFile);
            long time = System.currentTimeMillis() - start;
            LOGGER.info("index loaded in " + time + " ms");

            LOGGER.info("reconciling access points...");
            start = System.currentTimeMillis();
            FileReader fileReader = new FileReader(inputFile);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            FileWriter fileWriter = new FileWriter(outputFile);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);

            try {
                String line = bufferedReader.readLine();

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
                    if (fields[typeColumn].equals(CORP_TYPE) || fields[typeColumn].equals(PERS_TYPE)) continue;

                    // split list of atoms and reconcile them
                    String[] atoms = LIST_SPLITTER.split(fields[inputColumn]);
                    Place place = reconcile(index, atoms);

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
     * Reconcile an array of atomic access points.
     * @param index The place index to use for lookup.
     * @param atoms The array of atomic access points.
     * @return The result of the reconciliation, or null if nothing was found.
     */
    public static Place reconcile(PlaceIndex index, String[] atoms) {
        Set<Place> candidates = new HashSet<Place>();

        // for each atom, addpublic the most relevant match to the set of candidates
        for (String atom : atoms) {
            Place match = index.getOne(atom);
            if (match != null) candidates.add(match);
        }

        // return null if no matching candidates were found
        if (candidates.size() == 0) return null;

        // initialize the merged lineage
        Iterator<Place> candidateIterator = candidates.iterator();
        Deque<Place> mergedLineage = candidateIterator.next().lineage();

        // merge lineages of candidates
        while (candidateIterator.hasNext()) {
            Place candidate = candidateIterator.next();
            mergedLineage = mergeLineages(mergedLineage, candidate.lineage());
        }

        // should not happen
        if (mergedLineage.size() == 0) {
            LOGGER.warn("merged lineage is empty");
            return null;
        }

        // return the last place in the merged lineage
        return mergedLineage.descendingIterator().next();
    }

    /**
     * Merge two lineages together. If the two lineages diverge, the merged lineage will be the longest common lineage,
     * starting from the root; otherwise it will be the longer lineage.
     * @param lineageOne The first lineage to merge.
     * @param lineageTwo The second lineage to merge.
     * @return The merged lineage.
     */
    private static Deque<Place> mergeLineages(Deque<Place> lineageOne, Deque<Place> lineageTwo) {
        Deque<Place> mergedLineage = new LinkedList<Place>();
        Iterator<Place> iteratorOne = lineageOne.descendingIterator();
        Iterator<Place> iteratorTwo = lineageTwo.descendingIterator();

        // iterate through both lineages
        while (iteratorOne.hasNext() && iteratorTwo.hasNext()) {
            Place one = iteratorOne.next();
            Place two = iteratorTwo.next();

            // return the merged lineage if the two lineages diverge
            if (one.equals(two)) mergedLineage.add(one);
            else return mergedLineage;
        }

        // add any remaining places from the first lineage
        while (iteratorOne.hasNext()) {
            mergedLineage.add(iteratorOne.next());
        }

        // add any remaining places from the second lineage
        while (iteratorTwo.hasNext()) {
            mergedLineage.add(iteratorTwo.next());
        }

        return mergedLineage;
    }
}
