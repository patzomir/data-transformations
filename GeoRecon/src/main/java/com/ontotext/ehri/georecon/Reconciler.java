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
                    Place place = reconcile(index, atoms);

                    // ignore if access-point type is not allowed
                    if (! ALLOWED_TYPES.contains(fields[typeColumn])) place = null;

                    // ignore if original access point looks like person
                    if (PERS_PATTERN.matcher(fields[originalColumn]).matches()) place = null;

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
}
