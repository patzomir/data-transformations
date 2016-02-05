package com.ontotext.ehri.georecon;

import com.ontotext.ehri.georecon.place.Place;
import com.ontotext.ehri.georecon.place.PlaceIndex;
import com.ontotext.ehri.georecon.place.PlaceType;
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
import java.net.MalformedURLException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Build place index from Sesame repository and serialize it to disk.
 */
public class IndexBuilder {
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexBuilder.class);

    // regular expression for extracting GeoNames ID from GeoNames URL
    private static final Pattern EXTRACT_GEOID = Pattern.compile("sws\\.geonames\\.org/(\\d+)");

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

    // query the alternative names of a place (variable place must be bound)
    private static final String QUERY_NAMES = "PREFIX gn: <http://www.geonames.org/ontology#>\n" +
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
            serializeIndex(index, file);
            time = System.currentTimeMillis() - start;
            LOGGER.info("index serialized in " + time + " ms");
        } catch (RepositoryException e) {
            LOGGER.error("exception while building index", e);
        } catch (SailException e) {
            LOGGER.error("exception while building index", e);
        } catch (MalformedURLException e) {
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
     * @throws MalformedURLException
     */
    private static PlaceIndex buildIndex(File repo) throws RepositoryException, SailException, MalformedURLException {
        PlaceIndex index = new PlaceIndex();

        // start repository
        NativeStore store = new NativeStore(repo);
        Repository repository = new SailRepository(store);
        repository.initialize();
        RepositoryConnection connection = repository.getConnection();

        // create the Earth
        Place earth = new Place(6295630, 0, 0, 6814400000L, null, null);
        ValueFactory factory = repository.getValueFactory();
        Value earthValue = factory.createURI(earth.toURL().toString());

        try {
            TupleQuery queryChildren = connection.prepareTupleQuery(QueryLanguage.SPARQL, QUERY_CHILDREN);
            TupleQuery queryNames = connection.prepareTupleQuery(QueryLanguage.SPARQL, QUERY_NAMES);

            // add descendants of the Earth
            queryChildren.setBinding("parent", earthValue);
            addChildren(index, earth, queryChildren, queryNames);

        } catch (MalformedQueryException e) {
            LOGGER.error("exception while adding places", e);
        } catch (RepositoryException e) {
            LOGGER.error("exception while adding places", e);
        } catch (QueryEvaluationException e) {
            LOGGER.error("exception while adding places", e);
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
     * @param queryNames The prepared query for names.
     * @throws QueryEvaluationException
     */
    private static void addChildren(PlaceIndex index, Place parent, TupleQuery queryChildren, TupleQuery queryNames)
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

                // obtain the alternative names of the child
                Value place = childBindings.getValue("place");
                queryNames.setBinding("place", place);
                TupleQueryResult resultNames = queryNames.evaluate();

                try {

                    // add the child and each alternative name to the index
                    while (resultNames.hasNext()) {
                        BindingSet nameBindings = resultNames.next();
                        Value altName = nameBindings.getValue("name");
                        index.add(child, altName.stringValue());
                    }

                } catch (Exception e) {
                    LOGGER.error("exception while adding alternative names of " + child.getGeoID(), e);
                } finally {
                    resultNames.close();
                }

                // log some debug information
                LOGGER.debug("added \"" + name.stringValue() + "\"\n" + child.ancestry());

                // bind the parent variable to this child and add its children
                queryChildren.setBinding("parent", place);
                addChildren(index, child, queryChildren, queryNames);
            }

        } catch (Exception e) {
            LOGGER.error("exception while adding children of " + parent.getGeoID(), e);
        } finally {
            resultChildren.close();
        }
    }

    /**
     * Build a place with the given parent place and variable bindings.
     * @param parent The parent place.
     * @param bindings The variable bindings from the children query.
     * @return A place with the given parent build from the given variable bindings, or null if something bad happens.
     */
    private static Place buildPlace(Place parent, BindingSet bindings) {
        Value placeValue = bindings.getValue("place");
        Value featureValue = bindings.getValue("feature");
        Value latitudeValue = bindings.getValue("latitude");
        Value longitudeValue = bindings.getValue("longitude");
        Value populationValue = bindings.getValue("population");

        // cannot build place without GeoID
        Matcher idMatcher = EXTRACT_GEOID.matcher(placeValue.stringValue());
        if (! idMatcher.find()) {
            LOGGER.warn("cannot extract GeoID from URL: " + placeValue.stringValue());
            return null;
        }

        // parse information about the place
        int geoID = Integer.parseInt(idMatcher.group(1));
        double latitude = Double.parseDouble(latitudeValue.stringValue());
        double longitude = Double.parseDouble(longitudeValue.stringValue());
        long population = 0;
        if (populationValue != null) population = Long.parseLong(populationValue.stringValue());
        PlaceType type = classifyPlace(featureValue.stringValue());

        // build the place and return it
        Place place = new Place(geoID, latitude, longitude, population, type, parent);
        return place;
    }

    /**
     * Classify a place given its GeoNames feature code.
     * @param feature The GeoNames feature code.
     * @return The corresponding place type.
     */
    private static PlaceType classifyPlace(String feature) {
        if (feature.startsWith("A.ADM")) return PlaceType.ADM;
        if (feature.startsWith("A.PCL")) return PlaceType.PCL;
        if (feature.startsWith("P.PPL")) return PlaceType.PPL;
        return PlaceType.OTHER;
    }

    /**
     * Serialize a place index to file.
     * @param index The place index.
     * @param file The file.
     * @throws IOException
     */
    public static void serializeIndex(PlaceIndex index, File file) throws IOException {
        FileOutputStream fileOutput = new FileOutputStream(file);
        ObjectOutputStream objectOutput = new ObjectOutputStream(fileOutput);

        try {
            objectOutput.writeObject(index);
        } catch (IOException e) {
            LOGGER.error("exception while serializing index to: " + file.getAbsolutePath(), e);
        } finally {
            objectOutput.close();
            fileOutput.close();
        }
    }

    /**
     * Deserialize a place index from file.
     * @param file The file.
     * @return The place index.
     * @throws IOException
     */
    public static PlaceIndex deserializeIndex(File file) throws IOException {
        FileInputStream fileInput = new FileInputStream(file);
        ObjectInputStream objectInput = new ObjectInputStream(fileInput);

        try {
            PlaceIndex placeIndex = (PlaceIndex) objectInput.readObject();
            return placeIndex;
        } catch (ClassNotFoundException e) {
            LOGGER.error("exception while deserializing index from: " + file.getAbsolutePath(), e);
        } finally {
            objectInput.close();
            fileInput.close();
            return null;
        }
    }
}
