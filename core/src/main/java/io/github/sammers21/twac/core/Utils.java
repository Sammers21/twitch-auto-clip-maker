package io.github.sammers21.twac.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class Utils {

    public static String version() throws IOException {
        InputStream resourceAsStream = Utils.class.getResourceAsStream("/version.txt");
        BufferedReader reader = new BufferedReader(new InputStreamReader(resourceAsStream));
        return reader.readLine();
    }
}
