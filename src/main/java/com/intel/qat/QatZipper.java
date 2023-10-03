/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat;

import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;

/**
 * This class provides methods that can be used to compress and decompress data using {@link
 * Algorithm#DEFLATE} or {@link Algorithm#LZ4}.
 *
 * <p>The following code snippet demonstrates how to use the class to compress and decompress a
 * string.
 *
 * <blockquote>
 *
 * <pre>{@code
 * try {
 *   String inputStr = "Hello World!";
 *   byte[] input = inputStr.getBytes();
 *
 *   QatZipper qzip = new QatZipper();
 *
 *   // Create a buffer with enough size for compression
 *   byte[] output = new byte[qzip.maxCompressedLength(input.length)];
 *
 *   // Compress the bytes
 *   int resultLen = qzip.compress(input, output);
 *
 *   // Decompress the bytes into a String
 *   byte[] result = new byte[input.length];
 *   resultLen = qzip.decompress(output, result);
 *
 *   // Release resources
 *   qzip.end();
 *
 *   // Convert the bytes into a String
 *   String outputStr = new String(result, 0, resultLen);
 * } catch (QatException e) {
 * //
 * }
 * }</pre>
 *
 * </blockquote>
 *
 * To release QAT resources used by this <code>QatZipper</code>, the <code>end()</code> method
 * should be called explicitly. If not, resources will stay alive until this <code>QatZipper</code>
 * becomes phantom reachable.
 */
public class QatZipper {

  /** The backend wrapper */
  private ZipperBackend backend;

  /** The default compression level is 6. */
  public static final int DEFAULT_COMPRESS_LEVEL = 6;

  /**
   * The default number of times QatZipper attempts to acquire hardware resources is <code>0</code>.
   */
  public static final int DEFAULT_RETRY_COUNT = 0;

  // TODO: determine how to use Cleaners

  /** Cleaner instance associated with this object. */
  private static Cleaner cleaner;

  /** Cleaner.Cleanable instance representing QAT cleanup action. */
  private final Cleaner.Cleanable cleanable;

  static {
    SecurityManager sm = System.getSecurityManager();
    if (sm == null) {
      cleaner = Cleaner.create();
    } else {
      java.security.PrivilegedAction<Void> pa =
          () -> {
            cleaner = Cleaner.create();
            return null;
          };
      java.security.AccessController.doPrivileged(pa);
    }
  }

  /** A reference to a QAT session in C. */
  long session;

  /** The mode of execution for QAT. */
  public static enum Mode {
    /**
     * A hardware-only execution mode. QatZipper would fail if hardware resources cannot be acquired
     * after finite retries.
     */
    HARDWARE,

    /**
     * A hardware execution mode with a software fail over. QatZipper would fail over to software
     * execution mode if hardware resources cannot be acquired after finite retries.
     */
    AUTO;
  }

  /** The compression algorithm to use. DEFLATE and LZ4 are supported. */
  public static enum Algorithm {
    /** The deflate compression algorithm. */
    DEFLATE,

    /** The LZ4 compression algorithm. */
    LZ4,

    /** The Zstandard compression algorithm */
    ZSTD
  }

  /**
   * Creates a new QatZipper that uses {@link Algorithm#DEFLATE}, {@link DEFAULT_COMPRESS_LEVEL},
   * {@link Mode#HARDWARE}, and {@link DEFAULT_RETRY_COUNT}.
   */
  public QatZipper() {
    this(Algorithm.DEFLATE, DEFAULT_COMPRESS_LEVEL, Mode.HARDWARE, DEFAULT_RETRY_COUNT);
  }

  /**
   * Creates a new QatZipper with the specified execution {@link Mode}. Uses {@link
   * Algorithm#DEFLATE} compression algorithm, {@link DEFAULT_COMPRESS_LEVEL} compression level, and
   * {@link DEFAULT_RETRY_COUNT} retries.
   *
   * @param mode the {@link Mode} of QAT execution
   */
  public QatZipper(Mode mode) {
    this(Algorithm.DEFLATE, DEFAULT_COMPRESS_LEVEL, mode, DEFAULT_RETRY_COUNT);
  }

  /**
   * Creates a new QatZipper with the specified compression {@link Algorithm}. Uses {@link
   * DEFAULT_COMPRESS_LEVEL} compression level, {@link Mode#HARDWARE} execution mode, and {@link
   * DEFAULT_RETRY_COUNT} retries.
   *
   * @param algorithm the compression {@link Algorithm}
   */
  public QatZipper(Algorithm algorithm) {
    this(algorithm, DEFAULT_COMPRESS_LEVEL, Mode.HARDWARE, DEFAULT_RETRY_COUNT);
  }

  /**
   * Creates a new QatZipper with the specified {@link Algorithm} and {@link Mode} of execution.
   * Uses {@link DEFAULT_COMPRESS_LEVEL} compression level and {@link DEFAULT_RETRY_COUNT} retries.
   *
   * @param algorithm the compression {@link Algorithm}
   * @param mode the {@link Mode} of QAT execution
   */
  public QatZipper(Algorithm algorithm, Mode mode) {
    this(algorithm, DEFAULT_COMPRESS_LEVEL, mode, DEFAULT_RETRY_COUNT);
  }

  /**
   * Creates a new QatZipper with the specified {@link Algorithm} and compression level. Uses {@link
   * Mode#HARDWARE} execution mode and {@link DEFAULT_RETRY_COUNT} retries.
   *
   * @param algorithm the compression algorithm (deflate or LZ4).
   * @param level the compression level.
   */
  public QatZipper(Algorithm algorithm, int level) {
    this(algorithm, level, Mode.HARDWARE, DEFAULT_RETRY_COUNT);
  }

  /**
   * Creates a new QatZipper with the specified {@link Algorithm}, compression level, and {@link
   * Mode}. Uses {@link DEFAULT_RETRY_COUNT} retries.
   *
   * @param algorithm the compression algorithm (deflate or LZ4).
   * @param level the compression level.
   * @param mode the mode of operation (HARDWARE - only hardware, AUTO - hardware with a software
   *     failover.)
   */
  public QatZipper(Algorithm algorithm, int level, Mode mode) {
    this(algorithm, level, mode, DEFAULT_RETRY_COUNT);
  }

  /**
   * Creates a new QatZipper with the specified parameters.
   *
   * @param algorithm the compression {@link Algorithm}
   * @param level the compression level.
   * @param mode the {@link Mode} of QAT execution
   * @param retryCount the number of attempts to acquire hardware resources
   * @throws QatException if QAT session cannot be created.
   */
  public QatZipper(Algorithm algorithm, int level, Mode mode, int retryCount) throws QatException {
    if (algorithm == Algorithm.ZSTD) {
      // The below line should be replaced with the comment calling ZstdBackend()
      backend = new QatZipBackend(Algorithm.ZSTD, level, mode, retryCount);
      // TODO: fix this conditonal during merge with ztsd-jni branch
      // backend = new ZstdBackend(algorithm, level, mode, retryCount);
    } else {
      backend = new QatZipBackend(algorithm, level, mode, retryCount);
    }
    cleanable = cleaner.register(this, new QatCleaner(backend));
  }

  /**
   * Returns the maximum compression length for the specified source length. Use this method to
   * estimate the size of a buffer for compression given the size of a source buffer.
   *
   * @param len the length of the source array or buffer.
   * @return the maximum compression length for the specified length.
   */
  public int maxCompressedLength(long len) {
    return backend.maxCompressedLength(len);
  }

  /**
   * Compresses the source array and stores the result in the destination array. Returns the actual
   * number of bytes of the compressed data.
   *
   * @param src the source array holding the source data
   * @param dst the destination array for the compressed data
   * @return the size of the compressed data in bytes
   */
  public int compress(byte[] src, byte[] dst) {
    return backend.compress(src, dst);
  }

  /**
   * Compresses the source array, starting at the specified offset, and stores the result in the
   * destination array starting at the specified destination offset. Returns the actual number of
   * bytes of data compressed.
   *
   * @param src the source array holding the source data
   * @param srcOffset the start offset of the source data
   * @param srcLen the length of source data to compress
   * @param dst the destination array for the compressed data
   * @param dstOffset the destination offset where to start storing the compressed data
   * @param dstLen the maximum length that can be written to the destination array
   * @return the size of the compressed data in bytes
   */
  public int compress(
      byte[] src, int srcOffset, int srcLen, byte[] dst, int dstOffset, int dstLen) {
    return backend.compress(src, srcOffset, srcLen, dst, dstOffset, dstLen);
  }

  /**
   * Compresses the source buffer and stores the result in the destination buffer. Returns actual
   * number of bytes of compressed data.
   *
   * <p>On Success, the positions of both the source and destinations buffers are advanced by the
   * number of bytes read from the source and the number of bytes of compressed data written to the
   * destination.
   *
   * @param src the source buffer holding the source data
   * @param dst the destination array that will store the compressed data
   * @return returns the size of the compressed data in bytes
   */
  public int compress(ByteBuffer src, ByteBuffer dst) {
    return backend.compress(src, dst);
  }

  /**
   * Decompresses the source array and stores the result in the destination array. Returns the
   * actual number of bytes of decompressed data.
   *
   * @param src the source array holding the compressed data
   * @param dst the destination array for the decompressed data
   * @return the size of the decompressed data in bytes
   */
  public int decompress(byte[] src, byte[] dst) {
    return backend.decompress(src, dst);
  }

  /**
   * Decompresses the source array, starting at the specified offset, and stores the result in the
   * destination array starting at the specified destination offset. Returns the actual number of
   * bytes of data decompressed.
   *
   * @param src the source array holding the compressed data
   * @param srcOffset the start offset of the source
   * @param srcLen the length of source data to decompress
   * @param dst the destination array for the decompressed data
   * @param dstOffset the destination offset where to start storing the decompressed data
   * @param dstLen the maximum length that can be written to the destination array
   * @return the size of the decompressed data in bytes
   */
  public int decompress(
      byte[] src, int srcOffset, int srcLen, byte[] dst, int dstOffset, int dstLen) {
    return backend.decompress(src, srcOffset, srcLen, dst, dstOffset, dstLen);
  }

  /**
   * Deompresses the source buffer and stores the result in the destination buffer. Returns actual
   * number of bytes of decompressed data.
   *
   * <p>On Success, the positions of both the source and destinations buffers are advanced by the
   * number of bytes of compressed data read from the source and the number of bytes of decompressed
   * data written to the destination.
   *
   * @param src the source buffer holding the compressed data
   * @param dst the destination array that will store the decompressed data
   * @return returns the size of the decompressed data in bytes
   */
  public int decompress(ByteBuffer src, ByteBuffer dst) {
    return backend.decompress(src, dst);
  }

  /**
   * Ends the current QAT session by freeing up resources. A new session must be used after a
   * successful call of this method.
   *
   * @throws QatException if QAT session cannot be gracefully ended.
   */
  public void end() throws QatException {
    backend.end();
  }

  // TODO: Review if the QatCleaner class works
  /** A class that represents a cleaner action for a QAT session. */
  static class QatCleaner implements Runnable {
    private ZipperBackend backend;

    /** Creates a new cleaner object that cleans up the specified session. */
    public QatCleaner(ZipperBackend backend) {
      this.backend = backend;
    }

    @Override
    public void run() {
      if (backend != null) {
        backend.end();
      }
    }
  }
}
