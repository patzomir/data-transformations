package com.ontotext.ehri.georecon;

import com.ontotext.ehri.georecon.place.PlaceIndex;
import org.openrdf.model.Value;
import org.openrdf.query.*;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.sail.SailException;
import org.openrdf.sail.nativerdf.NativeStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Build place index from Sesame repository and serialize it to disk.
 */
public class IndexBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexBuilder.class);

    // query information about all places
    private static final String QUERY_PLACES = "PREFIX gn: <http://www.geonames.org/ontology#>\n" +
            "PREFIX wgs84_pos: <http://www.w3.org/2003/01/geo/wgs84_pos#>\n" +
            "SELECT * WHERE {\n" +
            "    ?geoURL gn:name ?name;\n" +
            "        wgs84_pos:lat ?latitude;\n" +
            "        wgs84_pos:long ?longitude;\n" +
            "        gn:featureCode ?feature;\n" +
            "        gn:parentFeature ?parent.\n" +
            "    OPTIONAL {\n" +
            "        ?geoURL gn:population ?population.\n" +
            "    }\n" +
            "}";

    // query alternative names of a given place (need to bind "geoURL")
    private static final String QUERY_ALTNAMES = "PREFIX gn: <http://www.geonames.org/ontology#>\n" +
            "SELECT ?altName WHERE {\n" +
            "    ?geoURL gn:alternateName ?altName\n" +
            "}";

    /**
     * Run the program.
     * @param args Command-line arguments: <repo dir> <index file>.
     */
    public static void main(String[] args) {

        // check arguments
        if (args.length != 2) {
            System.out.println("USAGE: java " + IndexBuilder.class.getName() + " <repo dir> <index file>");
            System.exit(0);
        }

        File repo = new File(args[0]);
        File file = new File(args[1]);

        try {
            LOGGER.info("building index...");
            long start = System.currentTimeMillis();
            PlaceIndex index = buildIndex(repo);
            long time = System.currentTimeMillis() - start;
            LOGGER.info("index built in " + time + " ms");
        } catch (RepositoryException e) {
            LOGGER.error("exception while building index", e);
        } catch (SailException e) {
            LOGGER.error("exception while building index", e);
        }
    }

    /**
     * Build place index from Sesame repository and return it.
     * @param repo The repository directory.
     * @return The built index.
     * @throws RepositoryException
     * @throws SailException
     */
    private static PlaceIndex buildIndex(File repo) throws RepositoryException, SailException {
        PlaceIndex index = new PlaceIndex();
        NativeStore store = new NativeStore(repo);
        Repository repository = new SailRepository(store);
        repository.initialize();
        RepositoryConnection connection = repository.getConnection();

        try {
            TupleQuery queryPlaces = connection.prepareTupleQuery(QueryLanguage.SPARQL, QUERY_PLACES);
            TupleQuery queryAltNames = connection.prepareTupleQuery(QueryLanguage.SPARQL, QUERY_ALTNAMES);

            TupleQueryResult places = queryPlaces.evaluate();

            try {

                while (places.hasNext()) {
                    BindingSet placeBindings = places.next();
                    Value geoURL = placeBindings.getValue("geoURL");
                    Value name = placeBindings.getValue("name");
                    Value latitude = placeBindings.getValue("latitude");
                    Value longitude = placeBindings.getValue("longitude");
                    Value feature = placeBindings.getValue("feature");
                    Value parent = placeBindings.getValue("parent");
                    Value population = placeBindings.getValue("population");

                    queryAltNames.setBinding("geoURL", geoURL);
                    TupleQueryResult altNames = queryAltNames.evaluate();

                    while (altNames.hasNext()) {
                        BindingSet altNameBindings = altNames.next();
                        Value altName = altNameBindings.getValue("altName");
                    }
                }

            } catch (QueryEvaluationException e) {
                LOGGER.error("exception while retrieving places", e);
            } finally {
                places.close();
            }

        } catch (MalformedQueryException e) {
            LOGGER.error("exception while querying places", e);
        } catch (RepositoryException e) {
            LOGGER.error("exception while querying places", e);
        } catch (QueryEvaluationException e) {
            LOGGER.error("exception while querying places", e);
        } finally {
            connection.close();
            repository.shutDown();
            store.shutDown();
        }

        return index;
    }
}
