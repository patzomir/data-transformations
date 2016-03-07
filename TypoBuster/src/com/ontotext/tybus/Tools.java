package com.ontotext.tybus;

import java.io.*;

/**
 * Helper methods for other classes.
 */
public class Tools {

    /**
     * Serialize an object to a file.
     * @param file A file.
     * @param object An object.
     */
    public static void serialize(File file, Object object) {

        try {
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(fileOutputStream);
            objectOutputStream.writeObject(object);
            objectOutputStream.close();
            fileOutputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Deserialize an object from a file.
     * @param file A file.
     * @return The object.
     */
    public static Object deserialize(File file) {
        Object object = null;

        try {
            FileInputStream fileInputStream = new FileInputStream(file);
            ObjectInputStream objectInputStream = new ObjectInputStream(fileInputStream);
            object = objectInputStream.readObject();
            objectInputStream.close();
            fileInputStream.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            return object;
        }
    }
}
