package com.ontotext.ehri.georecon;

import java.io.File;

public class Reconciler {

    public static void main(String[] args) {

        // check arguments
        if (args.length != 5) {
            System.out.println("USAGE: java " + Reconciler.class.getName() +
                    " <index file> <input file> <input column> <output file> <output column>");
            System.exit(0);
        }

        File indexFile = new File(args[0]);
        File inputFile = new File(args[1]);
        File outputFile = new File(args[3]);

        // TODO
    }
}
