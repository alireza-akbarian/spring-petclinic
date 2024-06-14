/*
 * Copyright 2012-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.samples.petclinic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * PetClinic Spring Boot Application.
 *
 * @author Dave Syer
 */
@SpringBootApplication
@ImportRuntimeHints(PetClinicRuntimeHints.class)
public class PetClinicApplication {

	public static void main(String[] args) {
		SpringApplication.run(PetClinicApplication.class, args);
		processData();
		processMoreData();
	}

	// Long method with duplicated code and magic numbers
	public static void processData() {
		int[] data = {1, 2, 3, 4, 5};
		int sum = 0;
		for (int i = 0; i < data.length; i++) {
			sum += data[i];
		}
		double average = sum / 5.0; // Magic number 5.0
		System.out.println("Sum: " + sum);
		System.out.println("Average: " + average);

		// Duplicated code for processing another set of data
		int[] moreData = {6, 7, 8, 9, 10};
		int moreSum = 0;
		for (int i = 0; i < moreData.length; i++) {
			moreSum += moreData[i];
		}
		double moreAverage = moreSum / 5.0; // Magic number 5.0
		System.out.println("Sum: " + moreSum);
		System.out.println("Average: " + moreAverage);
	}

	// Another long method with different data but same processing logic
	public static void processMoreData() {
		int[] data = {11, 12, 13, 14, 15};
		int sum = 0;
		for (int i = 0; i < data.length; i++) {
			sum += data[i];
		}
		double average = sum / 5.0; // Magic number 5.0
		System.out.println("Sum: " + sum);
		System.out.println("Average: " + average);

		// Duplicated code for processing another set of data
		int[] moreData = {16, 17, 18, 19, 20};
		int moreSum = 0;
		for (int i = 0; i < moreData.length; i++) {
			moreSum += moreData[i];
		}
		double moreAverage = moreSum / 5.0; // Magic number 5.0
		System.out.println("Sum: " + moreSum);
		System.out.println("Average: " + moreAverage);
	}

}
