package com.ontotext.ehri.georecon;

import com.ontotext.ehri.georecon.place.Place;
import com.ontotext.ehri.georecon.place.PlaceIndex;
import org.openrdf.model.Value;
import org.openrdf.model.ValueFactory;
import org.openrdf.query.*;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryException;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.sail.SailException;
import org.openrdf.sail.nativerdf.NativeStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

/**
 * Build place index from Sesame repository and serialize it to disk.
 */
public class IndexBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexBuilder.class);

    // prefix added before GeoNames feature codes
    private static String FEATURE_PREFIX = "http://www.geonames.org/ontology#";

    // query the children of a place (variable parent must be bound)
    private static final String QUERY_CHILDREN = "PREFIX gn: <http://www.geonames.org/ontology#>\n" +
            "PREFIX wgs84_pos: <http://www.w3.org/2003/01/geo/wgs84_pos#>\n" +
            "SELECT ?place ?name ?feature ?latitude ?longitude ?population WHERE {\n" +
            "    ?place gn:parentFeature ?parent;\n" +
            "        gn:name ?name;\n" +
            "        gn:featureCode ?feature;\n" +
            "        wgs84_pos:lat ?latitude;\n" +
            "        wgs84_pos:long ?longitude.\n" +
            "    OPTIONAL {\n" +
            "        ?place gn:population ?population.\n" +
            "    }\n" +
            "}";

    // query the official names of a place (variable place must be bound)
    private static final String QUERY_NAMES_OFF = "PREFIX gn: <http://www.geonames.org/ontology#>\n" +
            "SELECT ?name WHERE {\n" +
            "    ?place gn:officialName ?name.\n" +
            "}";

    // query the alternative names of a place (variable place must be bound)
    private static final String QUERY_NAMES_ALT = "PREFIX gn: <http://www.geonames.org/ontology#>\n" +
            "SELECT ?name WHERE {\n" +
            "    ?place gn:alternateName ?name.\n" +
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

            LOGGER.info("serializing index...");
            start = System.currentTimeMillis();
            Tools.serializeIndex(index, file);
            time = System.currentTimeMillis() - start;
            LOGGER.info("index serialized in " + time + " ms");
        } catch (RepositoryException e) {
            LOGGER.error("exception while building index", e);
        } catch (SailException e) {
            LOGGER.error("exception while building index", e);
        } catch (IOException e) {
            LOGGER.error("exception while serializing index", e);
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

        // start repository
        NativeStore store = new NativeStore(repo);
        Repository repository = new SailRepository(store);
        repository.initialize();
        RepositoryConnection connection = repository.getConnection();

        // create the root place
        ValueFactory factory = repository.getValueFactory();
        Value root = factory.createURI(Place.ROOT.toURL().toString());

        try {

            // prepare SPARQL queries
            TupleQuery queryChildren = connection.prepareTupleQuery(QueryLanguage.SPARQL, QUERY_CHILDREN);
            TupleQuery queryNamesOff = connection.prepareTupleQuery(QueryLanguage.SPARQL, QUERY_NAMES_OFF);
            TupleQuery queryNamesAlt = connection.prepareTupleQuery(QueryLanguage.SPARQL, QUERY_NAMES_ALT);

            // add children recursively, starting from the root
            queryChildren.setBinding("parent", root);
            addChildren(index, Place.ROOT, queryChildren, queryNamesOff, queryNamesAlt);

        } catch (RepositoryException e) {
            LOGGER.error("exception while building index", e);
        } finally {
            connection.close();
            repository.shutDown();
            store.shutDown();
            return index;
        }
    }

    /**
     * Recursively add the children of a given place to the given index.
     * @param index The index to add children to.
     * @param parent The parent place.
     * @param queryChildren The prepared query for children.
     * @param queryNamesOff The prepared query for official names.
     * @param queryNamesAlt The prepared query for alternative names.
     * @throws QueryEvaluationException
     */
    private static void addChildren(PlaceIndex index, Place parent, TupleQuery queryChildren,
                                    TupleQuery queryNamesOff, TupleQuery queryNamesAlt)
            throws QueryEvaluationException {
        TupleQueryResult resultChildren = queryChildren.evaluate();

        try {

            // build each child place
            while (resultChildren.hasNext()) {
                BindingSet childBindings = resultChildren.next();
                Place child = buildPlace(parent, childBindings);
                if (child == null) continue;

                // add the child and its main name to the index
                Value name = childBindings.getValue("name");
                index.add(child, name.stringValue());
                Value place = childBindings.getValue("place");

                // obtain the official names of the child
                queryNamesOff.setBinding("place", place);
                TupleQueryResult resultNamesOff = queryNamesOff.evaluate();

                try {

                    // add the child and each official name to the index
                    while (resultNamesOff.hasNext()) {
                        BindingSet nameBindings = resultNamesOff.next();
                        Value offName = nameBindings.getValue("name");
                        index.add(child, offName.stringValue());
                    }

                } catch (QueryEvaluationException e) {
                    LOGGER.error("exception while querying official names of place: " + child.toString(), e);
                } finally {
                    resultNamesOff.close();
                }

                queryNamesAlt.setBinding("place", place);
                TupleQueryResult resultNamesAlt = queryNamesAlt.evaluate();

                try {

                    // add the child and each alternative name to the index
                    while (resultNamesAlt.hasNext()) {
                        BindingSet nameBindings = resultNamesAlt.next();
                        Value altName = nameBindings.getValue("name");
                        index.add(child, altName.stringValue());
                    }

                } catch (QueryEvaluationException e) {
                    LOGGER.error("exception while querying alternative names of place: " + child.toString(), e);
                } finally {
                    resultNamesAlt.close();
                }

                // bind the parent variable to this child and add its children
                queryChildren.setBinding("parent", place);
                addChildren(index, child, queryChildren, queryNamesOff, queryNamesAlt);
            }

        } catch (QueryEvaluationException e) {
            LOGGER.error("exception while querying children of place: " + parent.toString(), e);
        } finally {
            resultChildren.close();
        }
    }

    /**
     * Build a place with the given parent place and variable bindings.
     * @param parent The parent place.
     * @param bindings The variable bindings from the children query.
     * @return A place with the given parent built from the given variable bindings, or null if the GeoNames ID cannot
     * be extracted from the GeoNames URL.
     */
    private static Place buildPlace(Place parent, BindingSet bindings) {
        Value placeValue = bindings.getValue("place");
        Value featureValue = bindings.getValue("feature");
        Value latitudeValue = bindings.getValue("latitude");
        Value longitudeValue = bindings.getValue("longitude");
        Value populationValue = bindings.getValue("population");

        // extract GeoNames ID from GeoNames URL
        int geoID;
        try {
            String geoURL = placeValue.stringValue();
            int startPos = Place.URL_PREFIX.length();
            int endPos = geoURL.length() - Place.URL_SUFFIX.length();
            geoID = Integer.parseInt(geoURL.substring(startPos, endPos));
        } catch (NumberFormatException e) {
            LOGGER.warn("cannot extract ID from URL: " + placeValue.stringValue(), e);
            return null; // cannot build place without ID
        }

        // parse coordinates if available
        double latitude = 0;
        double longitude = 0;
        try {
            latitude = Double.parseDouble(latitudeValue.stringValue());
            longitude = Double.parseDouble(longitudeValue.stringValue());
        } catch (NumberFormatException e) {
            LOGGER.warn("cannot parse coordinates", e);
        }

        // parse population if available
        long population = 0;
        if (populationValue != null) {
            try {
                population = Long.parseLong(populationValue.stringValue());
            } catch (NumberFormatException e) {
                LOGGER.warn("cannot parse population", e);
            }
        }

        // extract feature if available
        String feature = "";
        String featureURL = featureValue.stringValue();
        if (featureURL.startsWith(FEATURE_PREFIX)) feature = featureURL.substring(FEATURE_PREFIX.length());
        else LOGGER.warn("cannot extract feature from URL: " + featureURL);

        // build place
        return new Place(geoID, latitude, longitude, population, feature, parent);
    }
}
