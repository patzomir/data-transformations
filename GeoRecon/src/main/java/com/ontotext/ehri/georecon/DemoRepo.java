package com.ontotext.ehri.georecon;

import org.openrdf.model.URI;
import org.openrdf.model.ValueFactory;
import org.openrdf.query.*;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.sail.SailException;
import org.openrdf.sail.nativerdf.NativeStore;

import java.io.File;
import java.util.Scanner;

/**
 * Execute SPARQL queries against the Sesame repository.
 */
public class DemoRepo {

    /**
     * Run the program.
     * @param args Command-line arguments: <repo dir>.
     */
    public static void main(String[] args) {

        // check arguments
        if (args.length != 1) {
            System.out.println("USAGE: java " + DemoRepo.class.getName() + " <repo dir>");
            System.exit(0);
        }

        File repo = new File(args[0]);

        // start repository
        try {
            System.out.println("Starting repository...");
            long start = System.currentTimeMillis();
            NativeStore store = new NativeStore(repo);
            Repository repository = new SailRepository(store);
            repository.initialize();
            RepositoryConnection connection = repository.getConnection();
            long time = System.currentTimeMillis() - start;
            System.out.println("Repository started in " + time + " ms.");

            // inform user
            System.out.println("Enter SPARQL queries or \"" + Demo.CMD_QUIT + "\" to quit.");
            Scanner scanner = new Scanner(System.in);
            String input;

            // execute user queries
            while (! (input = scanner.nextLine().trim()).equals(Demo.CMD_QUIT)) {

                try {
                    TupleQuery query = connection.prepareTupleQuery(QueryLanguage.SPARQL, input);
                    TupleQueryResult result = query.evaluate();
                    int numResult = 0;

                    // iterate through results
                    while (result.hasNext()) {
                        BindingSet bindings = result.next();
                        numResult++;

                        // print the result number
                        System.out.print("[" + numResult + "]:");

                        // print each variable and its value
                        for (Binding binding : bindings) {
                            System.out.print(" " + binding.getName() + "=\"" + binding.getValue().stringValue() + "\"");
                        }

                        System.out.println();
                    }

                    result.close();

                } catch (MalformedQueryException e) {
                    e.printStackTrace();
                } catch (RepositoryException e) {
                    e.printStackTrace();
                } catch (QueryEvaluationException e) {
                    e.printStackTrace();
                }
            }

            System.out.println("Closing repository...");
            start = System.currentTimeMillis();
            connection.close();
            repository.shutDown();
            store.shutDown();
            time = System.currentTimeMillis() - start;
            System.out.println("Repository closed in " + time + " ms.");
        } catch (RepositoryException e) {
            e.printStackTrace();
        } catch (SailException e) {
            e.printStackTrace();
        }
    }
}
