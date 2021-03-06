package com.ontotext.ehri.georecon;

import com.ontotext.ehri.georecon.place.Place;
import com.ontotext.ehri.georecon.place.PlaceIndex;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;
import java.util.SortedSet;

/**
 * Demonstrate the GeoNames reconciler.
 */
public class Demo {

    // quit command
    public static final String CMD_QUIT = "/q";

    /**
     * Run the program.
     * @param args Command-line arguments: <index file>.
     */
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
            System.out.println("Index loaded in " + time + " ms.");

            // inform user
            System.out.println("Enter lists of places or \"" + CMD_QUIT + "\" to quit.");
            System.out.println("Use \"" + Reconciler.LIST_SEPARATOR + "\" to separate items in lists.");
            Scanner scanner = new Scanner(System.in);
            String input;

            // reconcile user input
            while (! (input = scanner.nextLine().trim()).equals(CMD_QUIT)) {
                String[] atoms = Reconciler.LIST_SPLITTER.split(input);
                SortedSet<Place> recons = Reconciler.reconcile(index, atoms, false);

                // print result from reconciliation if any
                if (recons == null) {
                    System.out.println("Not found.");
                } else {

                    for (Place recon : recons) {
                        System.out.print(recon.toString() + " ");
                    }

                    System.out.println();
                }

                // for each atom, get all matches in order of relevance
                for (String atom : atoms) {
                    SortedSet<Place> matches = index.get(atom);
                    if (matches == null) continue;

                    // print some additional information
                    for (Place match : matches) {
                        System.out.println("\"" + atom + "\": " + match.lineageString());
                    }
                }
            }

            System.out.println("Bye!");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
