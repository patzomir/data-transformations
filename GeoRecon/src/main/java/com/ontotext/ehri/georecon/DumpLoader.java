package com.ontotext.ehri.georecon;

import org.openrdf.model.Model;
import org.openrdf.model.impl.LinkedHashModel;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.rio.*;
import org.openrdf.rio.helpers.StatementCollector;
import org.openrdf.sail.SailException;
import org.openrdf.sail.nativerdf.NativeStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;

/**
 * Load the GeoNames RDF dump ( http://download.geonames.org/all-geonames-rdf.zip ) into a Sesame repository.
 */
public class DumpLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(DumpLoader.class);

    // number of places to add in one batch
    private static final int BATCH_SIZE = 250000;

    /**
     * Run the program.
     * @param args Command-line arguments: <dump file> <repo dir>.
     */
    public static void main(String[] args) {

        // check arguments
        if (args.length != 2) {
            System.out.println("USAGE: java " + DumpLoader.class.getName() + " <dump file> <repo dir>");
            System.exit(0);
        }

        File dump = new File(args[0]);
        File repo = new File(args[1]);

        try {
            LOGGER.info("loading dump...");
            long start = System.currentTimeMillis();
            loadDump(dump, repo);
            long time = System.currentTimeMillis() - start;
            LOGGER.info("dump loaded in " + time + " ms");
        } catch (RepositoryException e) {
            LOGGER.error("exception while loading dump");
        } catch (SailException e) {
            LOGGER.error("exception while loading dump");
        } catch (IOException e) {
            LOGGER.error("exception while loading dump");
        }
    }

    /**
     * Load a GeoNames RDF dump into a Sesame repository.
     * @param dump The dump file.
     * @param repo The repository directory.
     * @throws RepositoryException
     * @throws SailException
     * @throws IOException
     */
    private static void loadDump(File dump, File repo) throws RepositoryException, SailException, IOException {
        FileReader fileReader = new FileReader(dump);
        BufferedReader bufferedReader = new BufferedReader(fileReader);
        RDFParser parser = Rio.createParser(RDFFormat.RDFXML);

        // repository which stores data directly to disk
        NativeStore store = new NativeStore(repo);
        Repository repository = new SailRepository(store);
        repository.initialize();
        RepositoryConnection connection = repository.getConnection();

        long start = System.currentTimeMillis();
        int batchNum = 0;
        Model triples;

        try {

            // add triples in batches
            while ((triples = collectTriples(bufferedReader, parser, BATCH_SIZE)).size() > 0) {
                connection.add(triples);
                batchNum++;
                int numAdded = BATCH_SIZE * batchNum;
                long time = System.currentTimeMillis() - start;
                LOGGER.info("batch " + batchNum + " finished in " + time + " ms (" + numAdded + " places added)");
                start = System.currentTimeMillis();
            }

        } catch (RDFHandlerException e) {
            LOGGER.error("exception while adding triples");
        } catch (RepositoryException e) {
            LOGGER.error("exception while adding triples");
        } catch (RDFParseException e) {
            LOGGER.error("exception while adding triples");
        } catch (IOException e) {
            LOGGER.error("exception while adding triples");
        } finally {
            connection.close();
            repository.shutDown();
            store.shutDown();
            bufferedReader.close();
            fileReader.close();
        }
    }

    /**
     * Collect a batch of RDF triples.
     * @param reader An open BufferedReader for reading the dump file.
     * @param parser An RDFParser for parsing the RDF content.
     * @param maxNumPlaces Maximum number of GeoNames places to parse.
     * @return The collected RDF triples.
     * @throws IOException
     * @throws RDFParseException
     * @throws RDFHandlerException
     */
    private static Model collectTriples(BufferedReader reader, RDFParser parser, int maxNumPlaces)
            throws IOException, RDFParseException, RDFHandlerException {
        Model triples = new LinkedHashModel();
        RDFHandler handler = new StatementCollector(triples);
        parser.setRDFHandler(handler);

        int numPlaces = 0;
        URL geoLink = null;
        String line;

        // read each line in the RDF dump
        while (numPlaces < maxNumPlaces && (line = reader.readLine()) != null) {
            line = line.trim();

            // URL line
            if (geoLink == null) {
                geoLink = new URL(line);

            // XML line
            } else {
                StringReader stringReader = new StringReader(line);
                parser.parse(stringReader, geoLink.toString());
                stringReader.close();
                numPlaces++;
                geoLink = null;
            }
        }

        return triples;
    }
}
