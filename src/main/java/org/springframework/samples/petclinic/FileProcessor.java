package org.springframework.samples.petclinic;

import java.io.*;

public class FileProcessor {

    public String readFile(String fileName) {
        StringBuilder content = new StringBuilder();

        try {
            FileReader fileReader = new FileReader(fileName);
            BufferedReader bufferedReader = new BufferedReader(fileReader);
            String line;

            while ((line = bufferedReader.readLine()) != null) {
                content.append(line).append("\n");
            }

            bufferedReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return content.toString();
    }
}
