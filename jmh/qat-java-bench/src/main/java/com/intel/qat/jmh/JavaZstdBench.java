/*
 * Copyright (c) 2014, Oracle America, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  * Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 *  * Neither the name of Oracle nor the names of its contributors may be used
 *    to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
 * THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.intel.qat.jmh;

import com.github.luben.zstd.ZstdCompressCtx;
import com.github.luben.zstd.ZstdDecompressCtx;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
public class JavaZstdBench {
  private static final int COMPRESSION_LEVEL = 6;

  private byte[] src;
  private byte[] dst;
  private byte[] compressed;
  private byte[] decompressed;

  @Param({""})
  String fileName;

  @Setup
  public void prepare() {
    try {
      // Create compressor and decompressor contexts
      ZstdCompressCtx cctx = new ZstdCompressCtx();
      ZstdDecompressCtx dctx = new ZstdDecompressCtx();

      // Read input
      src = Files.readAllBytes(Paths.get(fileName));
      dst = new byte[src.length];

      // Compress input
      cctx.setLevel(6);
      int compressedLength = cctx.compress(dst, src);

      // Prepare compressed array of size EXACTLY compressedLength
      compressed = new byte[compressedLength];
      System.arraycopy(dst, 0, compressed, 0, compressedLength);

      // Do decompression
      decompressed = new byte[src.length];
      int decompressedLength = dctx.decompress(decompressed, compressed);

      // Print compressed length and ratio
      System.out.println("\n-------------------------");
      System.out.printf(
          "Input size: %d, Compressed size: %d, ratio: %.2f\n",
          src.length, compressedLength, src.length * 1.0 / compressedLength);
      System.out.println("-------------------------");

      // Clean up and stop Qat Device
      // QatZstdSequenceProducer.stopDevice();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Benchmark
  @Warmup(iterations = 2)
  @Measurement(iterations = 3)
  @BenchmarkMode(Mode.Throughput)
  public void compress() {
    ZstdCompressCtx cctx = new ZstdCompressCtx();
    cctx.compress(dst, src);
  }

  @Benchmark
  @Warmup(iterations = 2)
  @Measurement(iterations = 3)
  @BenchmarkMode(Mode.Throughput)
  public void decompress() {
    ZstdDecompressCtx dctx = new ZstdDecompressCtx();
    dctx.decompress(decompressed, compressed);
  }

  @TearDown
  public void end() {
    // Do nothing
  }
}
