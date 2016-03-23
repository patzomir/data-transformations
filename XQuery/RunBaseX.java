import org.basex.core.Context;
import org.basex.query.QueryException;
import org.basex.query.QueryProcessor;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Execute an XQuery file with BaseX.
 */
public class RunBaseX {

    /**
     * Run the program.
     * @param args Command-line arguments: <XQuery file>.
     */
    public static void main(String[] args) {

        // check arguments
        if (args.length < 1) {
            System.err.println("USAGE: java " + RunBaseX.class.getName() + " <XQuery file> <XQuery arguments...>");
            System.exit(1);
        }

        // initialize context
        Context context = new Context();

        try {

            // read query from file
            String query = slurpFile(args[0], StandardCharsets.UTF_8);
            QueryProcessor processor = new QueryProcessor(query, context);

            // bind XQuery arguments to external variables
            for (int i = 1; i < args.length; i++) {
                processor.bind("arg" + i, args[i], "xs:string");
            }

            // execute query
            long start = System.currentTimeMillis();
            processor.value();
            long time = System.currentTimeMillis() - start;
            processor.close();
            System.out.println("XQuery executed in " + time + " ms!");

        } catch (QueryException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Read a text file into a string and return it.
     * @param file The path to the text file.
     * @param encoding The encoding of the text file.
     * @return The content of the text file.
     * @throws IOException
     */
    private static String slurpFile(String file, Charset encoding) throws IOException {
        byte[] encoded = Files.readAllBytes(Paths.get(file));
        return new String(encoded, encoding);
    }
}
