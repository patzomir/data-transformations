package com.ontotext.ehri.georecon;

import com.ontotext.ehri.georecon.place.Place;
import com.ontotext.ehri.georecon.place.PlaceIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

public class Reconciler {
    private static final Logger LOGGER = LoggerFactory.getLogger(Reconciler.class);

    private static final String COLUMN_SEPARATOR = "\t";
    private static final Pattern COLUMN_SPLITTER = Pattern.compile(COLUMN_SEPARATOR);
    private static final Pattern LIST_SPLITTER = Pattern.compile(",");

    public static void main(String[] args) {

        // check arguments
        if (args.length != 5) {
            System.out.println("USAGE: java " + Reconciler.class.getName() +
                    " <index file> <input file> <input column> <output file> <output column>");
            System.exit(0);
        }

        File indexFile = new File(args[0]);
        File inputFile = new File(args[1]);
        String inputColumn = args[2];
        File outputFile = new File(args[3]);
        String outputColumn = args[4];

        try {
            LOGGER.info("Loading index...");
            long start = System.currentTimeMillis();
            PlaceIndex index = Tools.deserializeIndex(indexFile);
            long time = System.currentTimeMillis() - start;
            LOGGER.info("Index loaded in " + time + " ms!");

            LOGGER.info("Reconciling access points...");
            start = System.currentTimeMillis();
            FileReader fileReader = new FileReader(inputFile);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            FileWriter fileWriter = new FileWriter(outputFile);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);

            try {
                String line = bufferedReader.readLine();
                String[] fields = COLUMN_SPLITTER.split(line);
                int columnIndex = -1;

                for (int i = 0; i < fields.length; i++) {

                    if (inputColumn.equals(fields[i])) {
                        columnIndex = i;
                        break;
                    }
                }

                if (columnIndex == -1) {
                    LOGGER.error("no column named \"" + inputColumn + "\"");
                    System.exit(1);
                }

                bufferedWriter.write(outputColumn + COLUMN_SEPARATOR + line + "\n");

                while ((line = bufferedReader.readLine()) != null) {
                    fields = COLUMN_SPLITTER.split(line);
                    String result = reconcile(index, fields[columnIndex]);

                    if (result == null) result = "";
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
            LOGGER.info("Access points reconciled in " + time + " ms!");
        } catch (IOException e) {
            LOGGER.error("exception while reconciling", e);
        }
    }

    public static String reconcile(PlaceIndex index, String accessPoint) {
        Set<Place> places = new HashSet<Place>();
        String[] atoms = LIST_SPLITTER.split(accessPoint);

        for (String atom : atoms) {
            Place place = index.getOne(atom);
            if (place != null) places.add(place);
        }

        if (places.size() == 0) return null;

        Deque<Set<Place>> mergedLine = new LinkedList<Set<Place>>();

        for (Place place : places) {
            Deque<Place> ancestors = place.getAncestors();

            while (mergedLine.size() < ancestors.size() + 1) {
                mergedLine.add(new HashSet<Place>());
            }

            Iterator<Set<Place>> mergedIterator = mergedLine.iterator();
            Iterator<Place> ancestorIterator = ancestors.descendingIterator();

            while (ancestorIterator.hasNext()) {
                Place ancestor = ancestorIterator.next();
                mergedIterator.next().add(ancestor);
            }

            mergedIterator.next().add(place);
        }

        Place chosen = null;
        Iterator<Set<Place>> mergedIterator = mergedLine.iterator();

        while (mergedIterator.hasNext()) {
            Set<Place> mergedPlaces = mergedIterator.next();
            if (mergedPlaces.size() == 1) chosen = mergedPlaces.iterator().next();
            else break;
        }

        if (chosen == null) return null;
        return chosen.toURL().toString();
    }
}
