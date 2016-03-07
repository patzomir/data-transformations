package com.ontotext.tybus;

import java.io.*;

/**
 * Learn typos from a list of tokens. Each token is assumed to be on a new line. If the model file does not exist,
 * a new model will be created. Otherwise, the existing model will be enriched with the new tokens.
 */
public class LearnTypos {

    /**
     * Run the program.
     * @param args Command-line arguments: <token file> <model file>.
     */
    public static void main(String[] args) {

        // check number of command-line arguments
        if (args.length != 2) {
            System.out.println("USAGE: java " + LearnTypos.class.getName() + " <token file> <model file>");
            System.exit(0);
        }

        // check if token file exists
        File tokenFile = new File(args[0]);
        if (! tokenFile.isFile()) {
            System.err.println("ERROR: token file \"" + tokenFile.getAbsolutePath() + "\" does not exist");
            System.exit(1);
        }

        // check if model file exists
        File modelFile = new File(args[1]);
        Model model;
        if (! modelFile.isFile()) {
            System.out.println("Creating new model...");
            model = new Model();
        } else {
            System.out.println("Loading existing model...");
            model = (Model) Tools.deserialize(modelFile);
        }

        // add tokens to model
        try {
            System.out.println("Adding token to model...");
            FileReader fileReader = new FileReader(tokenFile);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String line;

            try {

                // treat each line as a token
                while ((line = bufferedReader.readLine()) != null) {
                    model.addToken(line.trim());
                }

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                bufferedReader.close();
                fileReader.close();
            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // serialize model
        System.out.println("Saving model to file...");
        Tools.serialize(modelFile, model);

        System.out.println("Done!");
    }
}
