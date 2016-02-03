package com.ontotext.ehri.georecon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IndexBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexBuilder.class);

    public static void main(String[] args) {

        if (args.length != 2) {
            System.out.println("USAGE: java " + IndexBuilder.class.getName() + " <repo dir> <index file>");
            System.exit(0);
        }

        // TODO
    }
}
