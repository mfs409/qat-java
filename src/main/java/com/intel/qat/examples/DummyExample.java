/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat.examples;

// import com.intel.qat.QatException;
import com.intel.qat.DummyBackend;
import java.util.Arrays;

public class DummyExample {

  public static void main(String[] args) {
    // to run call 'java --module-path target/classes  -m
    // com.intel.qat/com.intel.qat.examples.DummyExample'
    try {
      String inputStr = "The quick brown fox jumps over the lazy dog.";
      System.out.println("Original data: " + inputStr);
      byte[] input = Arrays.copyOf(inputStr.getBytes(), inputStr.length());
      //   // debugging: print out the compressed bytes
      //   for (byte b : input) {
      //     System.out.print(b + " ");
      //   }
      //   System.out.println("that was the input bytes^^");

      // replace with DummyBackend
      DummyBackend dummy = new DummyBackend();

      // Create a buffer with enough size for compression
      byte[] compressedData = new byte[dummy.maxCompressedLength(input.length)];

      // Compress the bytes
      int compressedSize = dummy.compress(input, compressedData);

      //   // debugging: print out the compressed bytes
      //   for (byte b : compressedData) {
      //     System.out.print(b + " ");
      //   }
      //   System.out.println("");

      // Convert 'compressed' bytes into a String
      String compressStr = new String(compressedData, 0, compressedSize);
      System.out.println("Compressed data: " + compressStr);

      // TODO: implement decompression
      //   // Decompress the bytes into a String
      //   byte[] decompressedData = new byte[input.length];
      //   int decompressedSize = dummy.decompress(compressedData, decompressedData);

      //   // Release resources
      //   qzip.end();

      //   // Convert the bytes into a String
      //   String outputStr = new String(decompressedData, 0, decompressedSize);
      //   System.out.println("Decompressed data: " + outputStr);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
