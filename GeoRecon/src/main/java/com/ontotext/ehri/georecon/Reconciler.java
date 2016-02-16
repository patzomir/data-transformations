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

    // files with features and words to exclude
    private static final File STOPFEATS_FILE = new File("src/main/resources/stopfeats.lst");
    private static final File STOPWORDS_FILE = new File("src/main/resources/stopwords.lst");

    // collect features and words to exclude
    private static final Set<String> STOPFEATS = collectLines(STOPFEATS_FILE, false);
    private static final Set<String> STOPWORDS = collectLines(STOPWORDS_FILE, true);

    // set of allowed access-point types
    private static final Set<String> ALLOWED_TYPES = new HashSet<String>();
    static {
        ALLOWED_TYPES.add("placeAccess");
        ALLOWED_TYPES.add("subjectAccess");
    }

    // match acronyms except for some countries
    private static final Pattern ACRO_PATTERN = Pattern.compile("(?!^DDR|USA$)^[A-Z]+$");

    // match names of people
    private static final Pattern PERS_PATTERN = Pattern.compile("^[A-Z][a-z]+, [A-Z][a-z]+" +
            "( (vom|von dem|von der|von|von und zu|zu|zur|op ten|van|van de|van den|van der|de))?" +
            "( \\([^\\)]+\\))?$");

    /**
     * Run the program.
     * @param args Command-line arguments: <index file> <input file> <input column> <original column> <type column> <output file> <output column>.
     */
    public static void main(String[] args) {

        // check arguments
        if (args.length != 7) {
            System.out.println("USAGE: java " + Reconciler.class.getName() +
                    " <index file> <input file> <input column> <original column> <type column> <output file> <output column>");
            System.exit(0);
        }

        File indexFile = new File(args[0]);
        File inputFile = new File(args[1]);
        String inputColumnName = args[2];
        String originalColumnName = args[3];
        String typeColumnName = args[4];
        File outputFile = new File(args[5]);
        String outputColumnName = args[6];

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
                int originalColumn = -1;
                int typeColumn = -1;

                // find the indexes of the input column and type column
                for (int i = 0; i < fields.length; i++) {
                    if (fields[i].equals(inputColumnName)) inputColumn = i;
                    else if (fields[i].equals(originalColumnName)) originalColumn = i;
                    else if (fields[i].equals(typeColumnName)) typeColumn = i;
                }

                // check if indexes were found
                if (inputColumn == -1) {
                    LOGGER.error("no input column with name: " + inputColumnName);
                    System.exit(1);
                } else if (originalColumn == -1) {
                    LOGGER.error("no original column with name: " + originalColumnName);
                    System.exit(1);
                } else if (typeColumn == -1) {
                    LOGGER.error("no type column with name: " + typeColumnName);
                    System.exit(1);
                }

                // write header line
                bufferedWriter.write(outputColumnName + COLUMN_SEPARATOR + line + "\n");

                // process each line
                while ((line = bufferedReader.readLine()) != null) {
                    fields = COLUMN_SPLITTER.split(line);

                    // skip access-point types that are not allowed
                    if (! ALLOWED_TYPES.contains(fields[typeColumn])) continue;

                    // skip original access points that look like people
                    if (PERS_PATTERN.matcher(fields[originalColumn]).matches()) continue;

                    // reconcile atomized access points
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

    private static Set<String> collectLines(File file, boolean doNormalize) {
        Set<String> lines = new HashSet<String>();

        try {
            FileReader fileReader = new FileReader(file);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String line;

            try {

                while ((line = bufferedReader.readLine()) != null) {
                    line = line.trim();
                    if (doNormalize) line = PlaceIndex.normalizeName(line);
                    lines.add(line);
                }

            } catch (IOException e) {
                LOGGER.error("exception while collection lines from file: " + file.getAbsolutePath(), e);
            } finally {
                bufferedReader.close();
                fileReader.close();
            }

        } catch (IOException e) {
            LOGGER.error("exception while collection lines from file: " + file.getAbsolutePath(), e);
        } finally {
            return lines;
        }
    }

    /**
     * Lookup places from an array of atomized access points and return the most relevant place.
     * @param index The lookup index to use.
     * @param atoms An array of atomized access points.
     * @return The most relevant matching place or null if no matches are found.
     */
    public static Place reconcile(PlaceIndex index, String[] atoms) {
        List<Set<Place>> candidates = new ArrayList<Set<Place>>();

        // iterate through valid atoms
        for (String atom : atoms) {
            if (ACRO_PATTERN.matcher(atom).matches()) continue;
            if (STOPWORDS.contains(PlaceIndex.normalizeName(atom))) continue;

            // get matches for atom
            Set<Place> matches = index.get(atom);
            if (matches == null) continue;

            // iterate through valid matches
            for (Place match : matches) {
                if (STOPFEATS.contains(match.getFeature())) continue;
                int numAncestors = 0;

                // iterate through other valid atoms
                for (String otherAtom : atoms) {
                    if (otherAtom == atom) continue;
                    if (ACRO_PATTERN.matcher(otherAtom).matches()) continue;
                    if (STOPWORDS.contains(PlaceIndex.normalizeName(otherAtom))) continue;

                    // get matches for other atom
                    Set<Place> otherMatches = index.get(otherAtom);
                    if (otherMatches == null) continue;

                    // check if at least one of the matches is an ancestor
                    for (Place otherMatch : otherMatches) {
                        if (match.isDescendantOf(otherMatch)) {
                            numAncestors++;
                            break;
                        }
                    }
                }

                // extend candidate list if necessary
                while (candidates.size() <= numAncestors) {
                    candidates.add(new TreeSet<Place>());
                }

                // add this match to the set of matches with this many ancestors
                candidates.get(numAncestors).add(match);
            }
        }

        // return null if there are no matches for any atom
        if (candidates.isEmpty()) return null;

        // return the first match with the most ancestors
        return candidates.get(candidates.size() - 1).iterator().next();
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
