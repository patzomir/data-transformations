package com.ontotext.ehri.ead;

import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.util.List;
import java.util.Map;

public class Transformer {

    public static void transform(Map<String, String> namespaces, String structureFile, String mappingTable, String inputDir, String outputDir) {
        // TODO: implement me
    }

    public static void transformAll() {

        try (InputStream configStream = Transformer.class.getResourceAsStream("/config.yml")) {
            Yaml yaml = new Yaml();
            Map config = (Map) yaml.load(configStream);
            Map namespaces = (Map) config.get("namespaces");
            String structureFile = (String) config.get("structure-file");

            for (Map transformation : (List<Map>) config.get("transformations")) {
                String institution = (String) transformation.get("institution");
                String mappingTable = (String) transformation.get("mapping-table");
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
