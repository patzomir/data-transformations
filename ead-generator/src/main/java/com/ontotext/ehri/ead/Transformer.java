package com.ontotext.ehri.ead;

import org.basex.core.Context;
import org.basex.query.QueryException;
import org.basex.query.QueryProcessor;
import org.basex.query.value.item.Str;
import org.basex.query.value.type.AtomType;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

public class Transformer {
    private static final String ENCODING = "UTF-8";

    private static String slurpText(String filePath, String encoding) {
        StringBuilder result = new StringBuilder();

        InputStream input = Transformer.class.getResourceAsStream(filePath);
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, encoding))) {
            String line;

            while ((line = reader.readLine()) != null) {
                result.append(line);
                result.append("\n");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return result.toString();
    }

    public static void transform(org.basex.query.value.map.Map namespaces, String structureFile, String mappingTable, String inputDir, String outputDir) {
        String query = slurpText("/xquery/transform.xqy", ENCODING);
        Context context = new Context();

        try (QueryProcessor processor = new QueryProcessor(query, context)) {
            processor.bind("namespaces", namespaces, "map(xs:string, xs:string)");
            processor.bind("structure-path", structureFile, "xs:string");
            processor.bind("configuration", mappingTable, "xs:string");

            for (File inputFile : new File(inputDir).listFiles()) {
                processor.bind("source-path", inputFile.getAbsolutePath(), "xs:string");
                processor.bind("target-path", new File(new File(outputDir), inputFile.getName()), "xs:string");
                processor.value();
            }

        } catch (QueryException e) {
            e.printStackTrace();
        } finally {
            context.close();
        }
    }

    public static void transformAll() {

        try (InputStream configStream = Transformer.class.getResourceAsStream("/config.yml")) {
            Yaml yaml = new Yaml();
            Map config = (Map) yaml.load(configStream);

            // convert from Java map to BaseX map
            Map<String, String> namespacesJavaMap = (Map) config.get("namespaces");
            org.basex.query.value.map.Map namespaces = org.basex.query.value.map.Map.EMPTY;
            for (String key : namespacesJavaMap.keySet()) {
                String value = namespacesJavaMap.get(key);

                try {
                    namespaces = namespaces.put(new Str(key.getBytes(ENCODING), AtomType.STR), new Str(value.getBytes(ENCODING), AtomType.STR), null);
                } catch (QueryException e) {
                    e.printStackTrace();
                }
            }

            // resolve relative file path
            String structureFile = (String) config.get("structure-file");
            try {
                structureFile = new File(Transformer.class.getResource(structureFile).toURI()).getAbsolutePath();
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }

            for (Map transformation : (List<Map>) config.get("transformations")) {
                String institution = (String) transformation.get("institution");

                // fetch table in TSV format
                String mappingTableId = (String) transformation.get("mapping-table");
                String mappingTable = GoogleSheets.toString(GoogleSheets.getValues(mappingTableId, "A1:D"), "\n", "\t");

                String inputDir = (String) transformation.get("input-dir");
                String outputDir = (String) transformation.get("output-dir");

                transform(namespaces, structureFile, mappingTable, inputDir, outputDir);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        transformAll();
    }
}
