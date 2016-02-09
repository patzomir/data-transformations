package com.ontotext.ehri.georecon;

import com.ontotext.ehri.georecon.place.Place;
import com.ontotext.ehri.georecon.place.PlaceIndex;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Demonstrate the GeoNames reconciler.
 */
public class Demo {
    private static final String CMD_QUIT = "/q";

    private static final Pattern LIST_SPLITTER = Pattern.compile(",");

    public static void main(String[] args) {

        // check arguments
        if (args.length != 1) {
            System.out.println("USAGE: java " + Demo.class.getName() + " <index file>");
            System.exit(0);
        }

        File file = new File(args[0]);

        try {
            System.out.println("Loading index...");
            long start = System.currentTimeMillis();
            PlaceIndex index = Tools.deserializeIndex(file);
            long time = System.currentTimeMillis() - start;
            System.out.println("Index loaded in " + time + " ms!");

            System.out.println("Enter places or \"" + CMD_QUIT + "\" to quit.");
            Scanner scanner = new Scanner(System.in);
            String input;

            while (! (input = scanner.nextLine()).equals(CMD_QUIT)) {
                String result = Reconciler.reconcile(index, input);

                if (result == null) System.out.println("not found");
                else System.out.println(result);

                String[] atoms = LIST_SPLITTER.split(input);

                for (String atom : atoms) {
                    Set<Place> hits = index.get(atom);
                    if (hits == null) continue;

                    for (Place hit : hits) {
                        System.out.println(atom + " => " + hit.toURL().toString());
                    }
                }
            }

            System.out.println("Bye!");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
