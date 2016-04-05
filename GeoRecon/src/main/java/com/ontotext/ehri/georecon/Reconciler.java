package com.ontotext.ehri.georecon;

import com.ontotext.ehri.georecon.place.Place;
import com.ontotext.ehri.georecon.place.PlaceIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
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
        ALLOWED_TYPES.add("corporateBodyAccess");
    }

    // match acronyms except for some countries
    private static final Pattern ACRO_PATTERN = Pattern.compile("(?!^DDR|USA|USSR$)^[\\d\\p{IsLu}]+$");

    // match names of people
    private static final Pattern PERS_PATTERN = Pattern.compile("(?!.*(Amsterdam|Brabant|Drenthe|Friesland|Gelderland|Groningen|Haarlem|Limburg))" + // exceptions
            "^((de|van de|van den|van der|vander|van|von|zur) )?" + // particles
            "\\p{IsLu}?\\p{IsLl}+, (?!(city|town|village|село|селище)$)" + // last name
            "(\\p{IsLu}?(\\p{IsLl}+(\\-\\p{IsLu}?\\p{IsLl}+)?|\\.|\\[\\?\\]))" + // first name
            "( (vom|von dem|von der|von|von und zu|zu|zur|op ten|van|van de|van den|van der|de))?" + // particles
            "( \\([^\\p{IsL}][^\\)]+\\)|\\s?\\p{IsLu}?\\[.+\\])?$"); // stuff in brackets

    // match junk to remove
    private static final Pattern JUNK_PATTERN = Pattern.compile("^(British Mandate For |Kreis )");

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
                    String[] atoms = LIST_SPLITTER.split(fields[inputColumn]);

                    // ignore if access-point type is not allowed
                    if (! ALLOWED_TYPES.contains(fields[typeColumn])) atoms = null;

                    // ignore if original access point looks like person
                    if (PERS_PATTERN.matcher(fields[originalColumn]).matches()) atoms = null;

                    // clean atoms
                    if (atoms != null) {

                        for (int i = 0; i < atoms.length; i++) {
                            if (STOPWORDS.contains(PlaceIndex.normalizeName(atoms[i]))) atoms[i] = null;
                            else if (ACRO_PATTERN.matcher(atoms[i]).matches()) atoms[i] = null;
                            else atoms[i] = JUNK_PATTERN.matcher(atoms[i]).replaceAll("");
                        }
                    }

                    // reconcile atoms
                    SortedSet<Place> places = reconcile(index, atoms, false);
                    StringBuilder result = new StringBuilder();

                    // collect result
                    if (places != null) {
                        Iterator<Place> iterator = places.iterator();
                        result.append(iterator.next().toString());

                        while (iterator.hasNext()) {
                            result.append(LIST_SEPARATOR + iterator.next().toString());
                        }
                    }

                    // write result
                    bufferedWriter.write(result.toString() + COLUMN_SEPARATOR + line + "\n");
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
     * Collect the lines in a file into a set.
     * @param file The file.
     * @param doNormalize If true, will perform normalization.
     * @return The set.
     */
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
     * Lookup places from an array of atomized access points and return the most relevant places.
     * @param index The lookup index to use.
     * @param atoms An array of atomized access points.
     * @param keepAncestors Do you want to keep ancestors or not?
     * @return The most relevant matching places or null if no matches are found.
     */
    public static SortedSet<Place> reconcile(PlaceIndex index, String[] atoms, boolean keepAncestors) {
        if (atoms == null) return null;
        SortedSet<Place> bestMatches = new TreeSet<Place>();

        // iterate through valid atoms
        for (String atom : atoms) {
            if (atom == null) continue;

            // get matches for atom
            SortedSet<Place> matches = index.get(atom);
            if (matches == null) continue;

            // map from number of ancestors to sets of matches
            List<SortedSet<Place>> numAncestors2matches = new ArrayList<SortedSet<Place>>();

            // iterate through valid matches
            for (Place match : matches) {
                if (STOPFEATS.contains(match.getFeature())) continue;
                int numAncestors = 0;

                // iterate through other valid atoms
                for (String otherAtom : atoms) {
                    if (otherAtom == null) continue;
                    if (otherAtom == atom) continue;

                    // get matches for other atom
                    SortedSet<Place> otherMatches = index.get(otherAtom);
                    if (otherMatches == null) continue;
                    if (otherMatches.equals(matches)) continue;

                    // iterate through valid matches of other atom
                    for (Place otherMatch : otherMatches) {
                        if (STOPFEATS.contains(otherMatch.getFeature())) continue;

                        // check if at least one of the matches of the other atom is ancestor
                        if (match.isDescendantOf(otherMatch)) {
                            numAncestors++;
                            break;
                        }
                    }
                }

                // extend list if necessary
                while (numAncestors2matches.size() <= numAncestors) {
                    numAncestors2matches.add(new TreeSet<Place>());
                }

                // add match to the set of matches with so many ancestors
                numAncestors2matches.get(numAncestors).add(match);
            }

            // possible if all matches of atom have stop-feature
            if (numAncestors2matches.isEmpty()) continue;

            // add best match of atom
            bestMatches.add(numAncestors2matches.get(numAncestors2matches.size() - 1).iterator().next());
        }

        // return null if there are no valid matches
        if (bestMatches.isEmpty()) return null;

        // return all best matches if ancestors are to be kept
        if (keepAncestors) return bestMatches;
        SortedSet<Place> bestMatchesNoAncestors = new TreeSet<Place>();

        // iterate through best matches
        for (Place match : bestMatches) {
            boolean isAncestor = false;

            // iterate through other best matches
            for (Place otherMatch : bestMatches) {
                if (otherMatch == match) continue;

                // check if at least one other best match is descendant
                if (otherMatch.isDescendantOf(match)) {
                    isAncestor = true;
                    break;
                }
            }

            // skip ancestor matches
            if (isAncestor) continue;
            bestMatchesNoAncestors.add(match);
        }

        return bestMatchesNoAncestors;
    }
}
