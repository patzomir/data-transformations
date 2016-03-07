package com.ontotext.tybus;

import java.io.*;

/**
 * Correct typos with given model. Model is loaded from file and used to build an index. The index is used to correct
 * the typos in the input file and the result is written to the output file. Each token is assumed to be on a new line.
 */
public class BustTypos {

    /**
     * Run the program.
     * @param args Command-line arguments: <model file> <input file> <output file>.
     */
    public static void main(String[] args) {

        // check number of command-line arguments
        if (args.length != 3) {
            System.out.println("USAGE: java " + BustTypos.class.getName() + " <model file> <input file> <output file>");
            System.exit(0);
        }

        // check if model file exists
        File modelFile = new File(args[0]);
        if (! modelFile.isFile()) {
            System.err.println("ERROR: model file \"" + modelFile.getAbsolutePath() + "\" does not exist");
            System.exit(1);
        }

        // check if input file exists
        File inputFile = new File(args[1]);
        if (! inputFile.isFile()) {
            System.err.println("ERROR: input file \"" + inputFile.getAbsolutePath() + "\" does not exist");
            System.exit(1);
        }

        File outputFile = new File(args[2]);

        // deserialize model
        System.out.println("Loading model...");
        Model model = (Model) Tools.deserialize(modelFile);

        // build index from model
        System.out.println("Building index from model...");
        Index index = new Index(model);

        // correct typos in input file and write result to output file
        try {
            System.out.println("Busting typos...");
            FileReader fileReader = new FileReader(inputFile);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            FileWriter fileWriter = new FileWriter(outputFile);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
            String line;

            try {

                // treat each line as a typo
                while ((line = bufferedReader.readLine()) != null) {
                    line = index.correct(line.trim());
                    bufferedWriter.write(line + "\n");
                }

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                bufferedWriter.close();
                fileWriter.close();
                bufferedReader.close();
                fileReader.close();
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Done!");
    }
}
