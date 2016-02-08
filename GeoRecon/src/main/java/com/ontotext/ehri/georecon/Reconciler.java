package com.ontotext.ehri.georecon;

import com.ontotext.ehri.georecon.place.Place;
import com.ontotext.ehri.georecon.place.PlaceIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
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

                bufferedWriter.write(outputColumn + COLUMN_SEPARATOR + line);

                while ((line = bufferedReader.readLine()) != null) {
                    fields = COLUMN_SPLITTER.split(line);
                    String result = reconcile(index, fields[columnIndex]);

                    if (result == null) result = "";
                    bufferedWriter.write(result + COLUMN_SEPARATOR + line);
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

        Iterator<Place> iterator = places.iterator();
        Place chosen = iterator.next();

        while (iterator.hasNext()) {
            Place next = iterator.next();

            if (chosen.isDescendantOf(next)) continue;
            else if (next.isDescendantOf(chosen)) chosen = next;
            else chosen = chosen.closestCommonAncestor(next);
        }

        return chosen.toURL().toString();
    }
}
