package com.ontotext.ehri.georecon;

import com.ontotext.ehri.georecon.place.PlaceIndex;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;

/**
 * Demonstrate the GeoNames reconciler.
 */
public class Demo {
    private static final String CMD_QUIT = "/q";

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
                System.out.println(index.getOne(input).ancestry());
            }

            System.out.println("Bye!");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
