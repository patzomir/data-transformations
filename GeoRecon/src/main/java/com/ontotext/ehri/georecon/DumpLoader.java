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
import org.openrdf.sail.memory.MemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;

public class DumpLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(DumpLoader.class);
    private static final int BATCH_SIZE = 100000;

    public static void main(String[] args) {

        if (args.length != 2) {
            System.out.println("USAGE: java " + DumpLoader.class.getName() + " <dump file> <repo dir>");
            System.exit(0);
        }

        File dump = new File(args[0]);
        File repo = new File(args[1]);

        MemoryStore store = new MemoryStore(repo);
        store.setSyncDelay(-1);
        Repository repository = new SailRepository(store);

        try {
            repository.initialize();
            LOGGER.info("Repository initialized!");
            RepositoryConnection connection = repository.getConnection();
            LOGGER.info("Connection opened!");

            try {
                long start = System.currentTimeMillis();
                loadDump(dump, connection, store);
                long time = System.currentTimeMillis() - start;
                LOGGER.info("Dump loaded in " + time + " ms!");
            } catch (IOException e) {
                LOGGER.error("Caught exception!", e);
            } finally {
                connection.close();
                LOGGER.info("Connection closed!");
                repository.shutDown();
                LOGGER.info("Repository shut down!");
            }

        } catch (RepositoryException e) {
            LOGGER.error("Caught exception!", e);
        }
    }

    private static void loadDump(File dump, RepositoryConnection connection, MemoryStore store) throws IOException {
        FileReader fileReader = new FileReader(dump);
        BufferedReader bufferedReader = new BufferedReader(fileReader);
        RDFParser parser = Rio.createParser(RDFFormat.RDFXML);

        int timesAdded = 0;
        Model triples;

        try {

            while ((triples = collectTriples(bufferedReader, parser, BATCH_SIZE)).size() > 0) {
                connection.add(triples);
                store.sync();
                timesAdded++;
                int totalAdded = BATCH_SIZE * timesAdded;
                LOGGER.info(totalAdded + " places added so far");
            }

        } catch (IOException e) {
            LOGGER.error("Caught exception!", e);
        } catch (RepositoryException e) {
            LOGGER.error("Caught exception!", e);
        } catch (RDFHandlerException e) {
            LOGGER.error("Caught exception!", e);
        } catch (RDFParseException e) {
            LOGGER.error("Caught exception!", e);
        } catch (SailException e) {
            LOGGER.error("Caught exception!", e);
        } finally {
            bufferedReader.close();
            fileReader.close();
        }
    }

    private static Model collectTriples(BufferedReader reader, RDFParser parser, int maxNumPlaces)
            throws IOException, RDFParseException, RDFHandlerException {
        Model triples = new LinkedHashModel();
        RDFHandler handler = new StatementCollector(triples);
        parser.setRDFHandler(handler);

        int numPlaces = 0;
        URL geoLink = null;
        String line;

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
