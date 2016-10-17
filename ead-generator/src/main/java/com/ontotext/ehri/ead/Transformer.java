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

    public static void transform(org.basex.query.value.map.Map namespaces, String structurePath, String configuration, File inputDir, File outputDir) {
        String query = slurpText("/xquery/transform.xqy", ENCODING);
        Context context = new Context();

        try (QueryProcessor processor = new QueryProcessor(query, context)) {
            processor.bind("namespaces", namespaces, "map(xs:string, xs:string)");
            processor.bind("structure-path", structurePath, "xs:string");
            processor.bind("configuration", configuration, "xs:string");

            for (File inputFile : inputDir.listFiles()) {
                File outputFile = new File(outputDir, inputFile.getName());
                processor.bind("source-path", inputFile.getAbsolutePath(), "xs:string");
                processor.bind("target-path", outputFile.getAbsolutePath(), "xs:string");
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
            Map<String, String> namespacesJava = (Map) config.get("namespaces");
            org.basex.query.value.map.Map namespaces = org.basex.query.value.map.Map.EMPTY;
            for (String key : namespacesJava.keySet()) {
                String value = namespacesJava.get(key);

                try {
                    namespaces = namespaces.put(
                            new Str(key.getBytes(ENCODING), AtomType.STR),
                            new Str(value.getBytes(ENCODING), AtomType.STR),
                            null);
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
                System.out.print("transforming files for \"" + institution + "\"...");
                long start = System.currentTimeMillis();

                // fetch table in TSV format
                String mappingTableId = (String) transformation.get("mapping-table");
                String mappingTable = GoogleSheets.toString(GoogleSheets.getValues(mappingTableId, "A1:D"), "\n", "\t");

                String inputDir = (String) transformation.get("input-dir");
                String outputDir = (String) transformation.get("output-dir");

                transform(namespaces, structureFile, mappingTable, new File(inputDir), new File(outputDir));
                long time = System.currentTimeMillis() - start;
                System.out.println(" " + time + " ms");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        transformAll();
    }
}
