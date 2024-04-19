package org.springframework.samples.petclinic;

import java.io.*;

public class BadSmellCode {

    public void processData(String filePath) {
        try {
            File file = new File(filePath);
            FileReader fileReader = new FileReader(file);
            BufferedReader bufferedReader = new BufferedReader(fileReader);

            String line;
            while ((line = bufferedReader.readLine()) != null) {
                String[] parts = line.split(",");

                // Process data based on conditions
                if (parts.length >= 3) {
                    String name = parts[0];
                    int age = Integer.parseInt(parts[1]);
                    String gender = parts[2];

                    if (age >= 18 && age <= 65 && (gender.equals("Male") || gender.equals("Female"))) {
                        System.out.println(name + " is eligible.");
                    } else {
                        System.out.println(name + " is not eligible.");
                    }
                } else {
                    System.out.println("Invalid data format: " + line);
                }
            }

            bufferedReader.close(); // Resource leak potential
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void processDataAgain(String filePath) {
        try {
            File file = new File(filePath);
            FileReader fileReader = new FileReader(file);
            BufferedReader bufferedReader = new BufferedReader(fileReader);

			String test = "test";

            String line;
            while ((line = bufferedReader.readLine()) != null) {
                String[] parts = line.split(",");

                // Process data based on conditions (similar to processData method)
                if (parts.length >= 3) {
                    String name = parts[0];
                    int age = Integer.parseInt(parts[1]);
                    String gender = parts[2];

                    if (age >= 18 && age <= 65 && (gender.equals("Male") || gender.equals("Female"))) {
                        System.out.println(name + " is eligible.");
                    } else {
                        System.out.println(name + " is not eligible.");
                    }
                } else {
                    System.out.println("Invalid data format: " + line);
                }
            }

            bufferedReader.close(); // Resource leak potential
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
