package com.ontotext.ehri.georecon;

import org.openrdf.model.Literal;
import org.openrdf.model.URI;
import org.openrdf.query.*;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.sail.SailException;
import org.openrdf.sail.nativerdf.NativeStore;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;

public class LabelAdder {

    private static final String QUERY_NAME = "PREFIX gn: <http://www.geonames.org/ontology#>\n" +
            "SELECT ?name ?parent WHERE {\n" +
            "    ?place gn:name ?name.\n" +
            "    ?place gn:parentFeature ?parent.\n" +
            "}";

    public static void main(String[] args) {

        // check arguments
        if (args.length != 3) {
            System.out.println("USAGE: java " + LabelAdder.class.getName() + " <repo dir> <input file> <output file>");
            System.exit(0);
        }

        File repoDir = new File(args[0]);
        File inputFile = new File(args[1]);
        File outputFile = new File(args[2]);

        // start repository
        try {
            NativeStore store = new NativeStore(repoDir);
            Repository repository = new SailRepository(store);
            repository.initialize();
            RepositoryConnection connection = repository.getConnection();

            FileReader fileReader = new FileReader(inputFile);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            FileWriter fileWriter = new FileWriter(outputFile);
            BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);

            try {
                String line = bufferedReader.readLine();

                // check if input file is empty
                if (line == null) {
                    System.err.println("input file is empty: " + inputFile.getAbsolutePath());
                    System.exit(1);
                }

                // write header line
                bufferedWriter.write("lineage" + Reconciler.COLUMN_SEPARATOR + line + "\n");

                while ((line = bufferedReader.readLine()) != null) {
                    String[] fields = Reconciler.COLUMN_SPLITTER.split(line);
                    if (fields[0].length() == 0) bufferedWriter.write(Reconciler.COLUMN_SEPARATOR + line + "\n");

                    String[] places = Reconciler.LIST_SPLITTER.split(fields[0]);
                    StringBuilder lineages = new StringBuilder();

                    for (String place : places) {
                        URL placeURL = new URL(place);
                        lineages.append(Reconciler.LIST_SEPARATOR);
                        lineages.append(lineage(connection, placeURL));
                    }

                    bufferedWriter.write(lineages.substring(Reconciler.LIST_SEPARATOR.length()) +
                            Reconciler.COLUMN_SEPARATOR + line + "\n");
                }

            } catch (IOException e) {
                e.printStackTrace();
            } catch (QueryEvaluationException e) {
                e.printStackTrace();
            } catch (MalformedQueryException e) {
                e.printStackTrace();
            } finally {
                bufferedWriter.close();
                fileWriter.close();
                bufferedReader.close();
                fileReader.close();
                connection.close();
                repository.shutDown();
                store.shutDown();
            }

        } catch (RepositoryException e) {
            e.printStackTrace();
        } catch (SailException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String lineage(RepositoryConnection connection, URL placeURL) throws MalformedQueryException, RepositoryException, QueryEvaluationException, MalformedURLException {
        StringBuilder lineage = new StringBuilder();
        TupleQuery query = connection.prepareTupleQuery(QueryLanguage.SPARQL, QUERY_NAME);
        URI placeURI = connection.getValueFactory().createURI(placeURL.toString());
        query.setBinding("place", placeURI);
        TupleQueryResult result = query.evaluate();
        String name = null;
        URL parent = null;

        // iterate through results
        while (result.hasNext()) {
            BindingSet bindings = result.next();
            name = bindings.getValue("name").stringValue();
            parent = new URL(bindings.getValue("parent").stringValue());
            break;
        }

        result.close();
        lineage.append(name);

        if (parent != null) {
            lineage.append(" <= ");
            lineage.append(lineage(connection, parent));
        }

        return lineage.toString();
    }
}
