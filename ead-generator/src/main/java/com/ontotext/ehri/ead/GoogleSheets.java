package com.ontotext.ehri.ead;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;

/**
 * Code adapted from this example: https://developers.google.com/sheets/quickstart/java#step_3_set_up_the_sample
 */
public class GoogleSheets {

    /** Application name. */
    private static final String APPLICATION_NAME = "EAD Generator";

    /** Directory to store user credentials for this application. */
    private static final File DATA_STORE_DIR = new File(System.getProperty("user.home"), ".credentials/sheets.googleapis.com-ead-generator");

    /** Global instance of the {@link FileDataStoreFactory}. */
    private static FileDataStoreFactory DATA_STORE_FACTORY;

    /** Global instance of the JSON factory. */
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    /** Global instance of the HTTP transport. */
    private static HttpTransport HTTP_TRANSPORT;

    /**
     * Global instance of the scopes required by this application.
     *
     * If modifying these scopes, delete your previously saved credentials
     * at ~/.credentials/sheets.googleapis.com-ead-generator
     */
    private static final List<String> SCOPES = Arrays.asList(SheetsScopes.SPREADSHEETS_READONLY);

    static {
        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Creates an authorized Credential object.
     * @return an authorized Credential object.
     * @throws IOException
     */
    public static Credential authorize() throws IOException {

        // Load client secrets.
        InputStream in = GoogleSheets.class.getResourceAsStream("/client_secret.json");
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                        .setDataStoreFactory(DATA_STORE_FACTORY)
                        .setAccessType("offline")
                        .build();
        Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
        //System.out.println("Credentials saved to " + DATA_STORE_DIR.getAbsolutePath());
        return credential;
    }

    /**
     * Build and return an authorized Sheets API client service.
     * @return an authorized Sheets API client service
     * @throws IOException
     */
    public static Sheets getSheetsService() throws IOException {
        Credential credential = authorize();
        return new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    /**
     * Return the values of the given spreadsheet in the given range.
     * @param spreadsheetId The ID of the spreadsheet.
     * @param range The range to take.
     * @return The values as a list of lists of objects (rows containing cells containing values).
     * @throws IOException
     */
    public static List<List<Object>> getValues(String spreadsheetId, String range) throws IOException {
        Sheets service = getSheetsService();
        ValueRange response = service.spreadsheets().values().get(spreadsheetId, range).execute();
        return response.getValues();
    }

    /**
     * Return a string representation of the given spreadsheet values.
     * @param values The values as a list of lists of objects (rows containing cells containing values).
     * @param rowSep Row separator (e.g. "\n" for newline).
     * @param colSep Column separator (e.g. "\t" for tab).
     * @return A string representation of the given spreadsheet values.
     */
    public static String toString(List<List<Object>> values, String rowSep, String colSep) {
        StringBuilder valuesString = new StringBuilder();

        for (List<Object> row : values) {
            StringBuilder rowString = new StringBuilder();

            for (Object value : row) {
                rowString.append(colSep);
                rowString.append(value.toString());
            }

            valuesString.append(rowSep);
            valuesString.append(rowString.substring(colSep.length()));
        }

        return valuesString.substring(rowSep.length());
    }

    public static void main(String[] args) throws IOException {
        String spreadsheetId = "1H8bgPSWTvvfICZ6znvFpf4iDCib39KZ0jfgTYHmv5e0";
        String range = "A1:D";

        List<List<Object>> values = getValues(spreadsheetId, range);
        System.out.println(toString(values, "\n", "\t"));
    }
}
