package com.ontotext.ehri.georecon;

import org.openrdf.model.Model;
import org.openrdf.model.URI;
import org.openrdf.model.ValueFactory;
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
            LOGGER.error("exception while loading dump", e);
        } catch (SailException e) {
            LOGGER.error("exception while loading dump", e);
        } catch (IOException e) {
            LOGGER.error("exception while loading dump", e);
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
            LOGGER.error("exception while adding triples", e);
        } catch (RepositoryException e) {
            LOGGER.error("exception while adding triples", e);
        } catch (RDFParseException e) {
            LOGGER.error("exception while adding triples", e);
        } catch (IOException e) {
            LOGGER.error("exception while adding triples", e);
        } finally {
            connection.close();
            fixData(repository);
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

    /**
     * Fix some broken data.
     * @param repository Sesame repository.
     * @throws RepositoryException
     */
    private static void fixData(Repository repository) throws RepositoryException {
        LOGGER.info("linking historical states...");
        ValueFactory factory = repository.getValueFactory();
        URI parent = factory.createURI("http://www.geonames.org/ontology#parentFeature");
        URI europe = factory.createURI("http://sws.geonames.org/6255148/");
        URI czechoslovakia = factory.createURI("http://sws.geonames.org/8505031/");
        URI czech = factory.createURI("http://sws.geonames.org/3077311/");
        URI slovakia = factory.createURI("http://sws.geonames.org/3057568/");
        URI yugoslavia = factory.createURI("http://sws.geonames.org/7500737/");
        URI serbiamontenegro = factory.createURI("http://sws.geonames.org/8505033/");
        URI serbia = factory.createURI("http://sws.geonames.org/6290252/");
        URI kosovo = factory.createURI("http://sws.geonames.org/831053/");
        URI montenegro = factory.createURI("http://sws.geonames.org/3194884/");
        URI bosniaherzegovina = factory.createURI("http://sws.geonames.org/3277605/");
        URI croatia = factory.createURI("http://sws.geonames.org/3202326/");
        URI macedonia = factory.createURI("http://sws.geonames.org/718075/");
        URI slovenia = factory.createURI("http://sws.geonames.org/3190538/");
        RepositoryConnection connection = repository.getConnection();

        try {

            // remove existing parent links
            connection.remove(czechoslovakia, parent, null);
            connection.remove(czech, parent, null);
            connection.remove(slovakia, parent, null);
            connection.remove(yugoslavia, parent, null);
            connection.remove(serbiamontenegro, parent, null);
            connection.remove(serbia, parent, null);
            connection.remove(kosovo, parent, null);
            connection.remove(montenegro, parent, null);
            connection.remove(bosniaherzegovina, parent, null);
            connection.remove(croatia, parent, null);
            connection.remove(macedonia, parent, null);
            connection.remove(slovenia, parent, null);

            // add new parent links
            connection.add(czechoslovakia, parent, europe);
            connection.add(czech, parent, czechoslovakia);
            connection.add(slovakia, parent, czechoslovakia);
            connection.add(yugoslavia, parent, europe);
            connection.add(serbiamontenegro, parent, yugoslavia);
            connection.add(serbia, parent, serbiamontenegro);
            connection.add(kosovo, parent, serbia);
            connection.add(montenegro, parent, serbiamontenegro);
            connection.add(bosniaherzegovina, parent, yugoslavia);
            connection.add(croatia, parent, yugoslavia);
            connection.add(macedonia, parent, yugoslavia);
            connection.add(slovenia, parent, yugoslavia);

        } catch (RepositoryException e) {
            LOGGER.error("exception while fixing data", e);
        } finally {
            connection.close();
        }

        LOGGER.info("historical states linked");
    }
}
