/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/
package com.intel.qat;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdCompressCtx;
import com.github.luben.zstd.ZstdDecompressCtx;
import com.github.luben.zstd.ZstdException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

public class QatZstdSequenceProducerTests {
  private final String SAMPLE_TEXT_PATH = "src/test/resources/sample.txt";

  private byte[] readAllBytes(String fileName) throws IOException {
    return Files.readAllBytes(Path.of(fileName));
  }

  // TODO: catch a specific type of exception or remove the try/ctach altogether
  // test constructor
  @Test
  public void testDefaultConstructor() {
    try {
      QatZstdSequenceProducer seqprod = new QatZstdSequenceProducer();
      assertTrue(seqprod != null);
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  // test starting and stopping device
  @Test
  public void startAndStopQatDevice() {
    try {
      QatZstdSequenceProducer seqprod = new QatZstdSequenceProducer();
      int status;
      status = QatZstdSequenceProducer.startDevice();
      assertTrue(status == QatZstdSequenceProducer.Status.OK);
    } catch (Exception e) {
      fail(e.getMessage());
    }
    QatZstdSequenceProducer.stopDevice();
  }

  // calling startDevice() when it has already been started is okay
  @Test
  public void doubleStartQatDevice() {
    try {
      QatZstdSequenceProducer seqprod = new QatZstdSequenceProducer();
      int status;
      status = QatZstdSequenceProducer.startDevice();
      assertTrue(status == QatZstdSequenceProducer.Status.OK);
      status = QatZstdSequenceProducer.startDevice();
      assertTrue(status == QatZstdSequenceProducer.Status.OK);
    } catch (Exception e) {
      fail(e.getMessage());
    }
    QatZstdSequenceProducer.stopDevice();
  }

  // calling stopDevice() when it has not been started is okay
  @Test
  public void stopDeviceOnly() {
    try {
      QatZstdSequenceProducer.stopDevice();
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  //  no need to call stopDevice() twice, same as stopping it once, no test

  // test start -> stop -> start sequence works
  @Test
  public void restartQatDevice() {
    try {
      QatZstdSequenceProducer seqprod = new QatZstdSequenceProducer();
      int status;
      status = QatZstdSequenceProducer.startDevice();
      assertTrue(status == QatZstdSequenceProducer.Status.OK);
      QatZstdSequenceProducer.stopDevice();
      status = QatZstdSequenceProducer.startDevice();
      assertTrue(status == QatZstdSequenceProducer.Status.OK);
    } catch (Exception e) {
      fail(e.getMessage());
    }
    QatZstdSequenceProducer.stopDevice();
  }

  // getFunctionPointer() correctly returns a function pointer, which should never be NULL
  @Test
  public void getFunctionPointer() {
    try {
      QatZstdSequenceProducer seqprod = new QatZstdSequenceProducer();
      long funcPointer = seqprod.getFunctionPointer();
      assertTrue(funcPointer != 0);
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  // getFunctionPointer() works the same whether or not qatDevice was activated, no test

  // createState allocates memory and returns a pointer, which should never be NULL
  @Test
  public void createSeqProdState() {
    try {
      QatZstdSequenceProducer seqprod = new QatZstdSequenceProducer();
      long state = seqprod.createState();
      assertTrue(state != 0);
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  // createState() works the same whether or not qatDevice was activated, no test

  // create multiple states? - shouldn't be any difference, no test

  // freeState frees memory associated with the state struct, but does not change the pointer
  // TODO: make this test more meaningful, or remove it
  @Test
  public void freeSeqProdState() {
    try {
      QatZstdSequenceProducer seqprod = new QatZstdSequenceProducer();
      long state = seqprod.createState();
      seqprod.freeState(state);
      assertTrue(state != 0);
    } catch (Exception e) {
      fail(e.getMessage());
    }
  }

  // free state when passed NULL does nothing - should this be tested?

  // UNDEFINED BEHAVIOR, shouldn't test:
  // freeState when passed a random invalid pointer
  // freeState when passed a negative number

  // ZstdCCtx registerSequenceProducer with null object - not in qat-java, so don't test

  // ZstdCompressionCtx registerSequenceProducer with normal use cases

  @Test
  public void testHelloWorld() {
    try {
      ZstdCompressCtx cctx = new ZstdCompressCtx();
      ZstdDecompressCtx dctx = new ZstdDecompressCtx();
      QatZstdSequenceProducer.startDevice();
      cctx.registerSequenceProducer(new QatZstdSequenceProducer());

      byte[] src = "Hello, world!".getBytes();
      byte[] dst = new byte[(int) Zstd.compressBound(src.length)];
      byte[] dec = new byte[src.length];

      dst = cctx.compress(src);
      dctx.decompress(dec, dst);

      assertTrue(Arrays.equals(src, dec));
      QatZstdSequenceProducer.stopDevice();
    } catch (ZstdException e) {
      fail(e.getMessage());
    }
  }

  @Test
  public void testSimpleCompression() {
    try {
      ZstdCompressCtx cctx = new ZstdCompressCtx();
      ZstdDecompressCtx dctx = new ZstdDecompressCtx();
      QatZstdSequenceProducer.startDevice();
      cctx.registerSequenceProducer(new QatZstdSequenceProducer());

      byte[] src = readAllBytes(SAMPLE_TEXT_PATH);
      byte[] dst = new byte[(int) Zstd.compressBound(src.length)];
      byte[] dec = new byte[src.length];

      // TODO: use de/compression functions which return int val, and assertTrue(val > 0)
      dst = cctx.compress(src);
      dec = dctx.decompress(dst, src.length);

      assertTrue(Arrays.equals(src, dec));
      QatZstdSequenceProducer.stopDevice();
    } catch (IOException | ZstdException e) {
      fail(e.getMessage());
    }
  }

  // compress and decompress on sizes =0, <0 ...

  // compress/decompress with both byte[] and ByteBuffer

  // generate src with both readAllBytes(PATH) and getRandomBytes(length)

  // compress on various levels

  //
}
