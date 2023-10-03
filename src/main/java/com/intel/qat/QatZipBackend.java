/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

package com.intel.qat;

import static com.intel.qat.QatZipper.Algorithm;
import static com.intel.qat.QatZipper.Mode;

import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;

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
 *   QatZipBackend qzip = new QatZipBackend();
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
 * To release QAT resources used by this <code>QatZipBackend</code>, the <code>end()</code> method
 * should be called explicitly. If not, resources will stay alive until this <code>QatZipBackend
 * </code> becomes phantom reachable.
 */
public class QatZipBackend extends ZipperBackend {
  /** The default compression level is 6. */
  public static final int DEFAULT_COMPRESS_LEVEL = 6;

  /**
   * The default number of times QatZipBackend attempts to acquire hardware resources is <code>0
   * </code>.
   */
  public static final int DEFAULT_RETRY_COUNT = 0;

  private boolean isValid;

  private int retryCount;

  /** A reference to a QAT session in C. */
  long session;

  /** The mode of execution for QAT. */
  public static enum Mode {
    /**
     * A hardware-only execution mode. QatZipBackend would fail if hardware resources cannot be
     * acquired after finite retries.
     */
    HARDWARE,

    /**
     * A hardware execution mode with a software fail over. QatZipBackend would fail over to
     * software execution mode if hardware resources cannot be acquired after finite retries.
     */
    AUTO;
  }

  /** The compression algorithm to use. DEFLATE and LZ4 are supported. */
  public static enum Algorithm {
    /** The deflate compression algorithm. */
    DEFLATE,

    /** The LZ4 compression algorithm. */
    LZ4
  }

  /**
   * Creates a new QatZipBackend that uses {@link Algorithm#DEFLATE}, {@link
   * DEFAULT_COMPRESS_LEVEL}, {@link Mode#HARDWARE}, and {@link DEFAULT_RETRY_COUNT}.
   */
  public QatZipBackend() {
    this(
        QatZipper.Algorithm.DEFLATE,
        DEFAULT_COMPRESS_LEVEL,
        QatZipper.Mode.HARDWARE,
        DEFAULT_RETRY_COUNT);
  }

  /**
   * Creates a new QatZipBackend with the specified execution {@link Mode}. Uses {@link
   * Algorithm#DEFLATE} compression algorithm, {@link DEFAULT_COMPRESS_LEVEL} compression level, and
   * {@link DEFAULT_RETRY_COUNT} retries.
   *
   * @param mode the {@link Mode} of QAT execution
   */
  public QatZipBackend(QatZipper.Mode mode) {
    this(QatZipper.Algorithm.DEFLATE, DEFAULT_COMPRESS_LEVEL, mode, DEFAULT_RETRY_COUNT);
  }

  /**
   * Creates a new QatZipBackend with the specified compression {@link Algorithm}. Uses {@link
   * DEFAULT_COMPRESS_LEVEL} compression level, {@link Mode#HARDWARE} execution mode, and {@link
   * DEFAULT_RETRY_COUNT} retries.
   *
   * @param algorithm the compression {@link Algorithm}
   */
  public QatZipBackend(QatZipper.Algorithm algorithm) {
    this(algorithm, DEFAULT_COMPRESS_LEVEL, QatZipper.Mode.HARDWARE, DEFAULT_RETRY_COUNT);
  }

  /**
   * Creates a new QatZipBackend with the specified {@link Algorithm} and {@link Mode} of execution.
   * Uses {@link DEFAULT_COMPRESS_LEVEL} compression level and {@link DEFAULT_RETRY_COUNT} retries.
   *
   * @param algorithm the compression {@link Algorithm}
   * @param mode the {@link Mode} of QAT execution
   */
  public QatZipBackend(QatZipper.Algorithm algorithm, QatZipper.Mode mode) {
    this(algorithm, DEFAULT_COMPRESS_LEVEL, mode, DEFAULT_RETRY_COUNT);
  }

  /**
   * Creates a new QatZipBackend with the specified {@link Algorithm} and compression level. Uses
   * {@link Mode#HARDWARE} execution mode and {@link DEFAULT_RETRY_COUNT} retries.
   *
   * @param algorithm the compression algorithm (deflate or LZ4).
   * @param level the compression level.
   */
  public QatZipBackend(QatZipper.Algorithm algorithm, int level) {
    this(algorithm, level, QatZipper.Mode.HARDWARE, DEFAULT_RETRY_COUNT);
  }

  /**
   * Creates a new QatZipBackend with the specified {@link Algorithm}, compression level, and {@link
   * Mode}. Uses {@link DEFAULT_RETRY_COUNT} retries.
   *
   * @param algorithm the compression algorithm (deflate or LZ4).
   * @param level the compression level.
   * @param mode the mode of operation (HARDWARE - only hardware, AUTO - hardware with a software
   *     failover.)
   */
  public QatZipBackend(QatZipper.Algorithm algorithm, int level, QatZipper.Mode mode) {
    this(algorithm, level, mode, DEFAULT_RETRY_COUNT);
  }

  /**
   * Creates a new QatZipBackend with the specified parameters.
   *
   * @param algorithm the compression {@link Algorithm}
   * @param level the compression level.
   * @param mode the {@link Mode} of QAT execution
   * @param retryCount the number of attempts to acquire hardware resources
   * @throws QatException if QAT session cannot be created.
   */
  QatZipBackend(QatZipper.Algorithm algorithm, int level, QatZipper.Mode mode, int retryCount)
      throws QatException {
    if (!validateParams(algorithm, level, retryCount))
      throw new IllegalArgumentException("Invalid compression level or retry count.");

    this.retryCount = retryCount;
    InternalJNI.setup(this, mode.ordinal(), algorithm.ordinal(), level);
    isValid = true;
  }

  /**
   * Validates compression level and retry counts.
   *
   * @param algorithm the compression {@link algorithm}
   * @param level the compression level.
   * @param retryCount how many times to seek for a hardware resources before giving up.
   * @return true if validation was successful, false otherwise.
   */
  private boolean validateParams(Algorithm algorithm, int level, int retryCount) {
    return !(retryCount < 0 || level < 1 || level > 9);
  }

  /**
   * Returns the maximum compression length for the specified source length. Use this method to
   * estimate the size of a buffer for compression given the size of a source buffer.
   *
   * @param len the length of the source array or buffer.
   * @return the maximum compression length for the specified length.
   */
  public int maxCompressedLength(long len) {
    if (!isValid) throw new IllegalStateException("QAT session has been closed.");

    return InternalJNI.maxCompressedSize(session, len);
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
    return compress(src, 0, src.length, dst, 0, dst.length);
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
    if (!isValid) throw new IllegalStateException("QAT session has been closed.");

    if (src == null || dst == null || srcLen == 0 || dst.length == 0)
      throw new IllegalArgumentException(
          "Either source or destination array or both have size 0 or null value.");

    if (srcOffset < 0 || (srcLen > src.length) || srcOffset >= src.length)
      throw new ArrayIndexOutOfBoundsException("Source offset is out of bounds.");

    int compressedSize =
        InternalJNI.compressByteArray(
            session, src, srcOffset, srcLen, dst, dstOffset, dstLen, retryCount);

    return compressedSize;
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
    if (!isValid) throw new IllegalStateException("QAT session has been closed.");

    if ((src == null || dst == null)
        || (src.position() == src.limit() || dst.position() == dst.limit()))
      throw new IllegalArgumentException();

    if (dst.isReadOnly()) throw new ReadOnlyBufferException();

    int compressedSize = 0;
    if (src.hasArray() && dst.hasArray()) {
      compressedSize =
          InternalJNI.compressByteBuffer(
              session,
              src,
              src.array(),
              src.position(),
              src.remaining(),
              dst.array(),
              dst.position(),
              dst.remaining(),
              retryCount);
      dst.position(dst.position() + compressedSize);
    } else if (src.isDirect() && dst.isDirect()) {
      compressedSize =
          InternalJNI.compressDirectByteBuffer(
              session,
              src,
              src.position(),
              src.remaining(),
              dst,
              dst.position(),
              dst.remaining(),
              retryCount);
    } else if (src.hasArray() && dst.isDirect()) {
      compressedSize =
          InternalJNI.compressDirectByteBufferDst(
              session,
              src,
              src.array(),
              src.position(),
              src.remaining(),
              dst,
              dst.position(),
              dst.remaining(),
              retryCount);
    } else if (src.isDirect() && dst.hasArray()) {
      compressedSize =
          InternalJNI.compressDirectByteBufferSrc(
              session,
              src,
              src.position(),
              src.remaining(),
              dst.array(),
              dst.position(),
              dst.remaining(),
              retryCount);
      dst.position(dst.position() + compressedSize);
    } else {
      int srcLen = src.remaining();
      int dstLen = dst.remaining();

      byte[] srcArr = new byte[srcLen];
      byte[] dstArr = new byte[dstLen];

      src.get(srcArr);
      dst.get(dstArr);

      src.position(src.position() - srcLen);
      dst.position(dst.position() - dstLen);

      int srcPos = src.position();
      compressedSize =
          InternalJNI.compressByteBuffer(
              session, src, srcArr, 0, srcLen, dstArr, 0, dstLen, retryCount);
      src.position(srcPos + src.position());
      dst.put(dstArr, 0, compressedSize);
    }

    return compressedSize;
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
    return decompress(src, 0, src.length, dst, 0, dst.length);
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
    if (!isValid) throw new IllegalStateException("QAT session has been closed.");

    if (src == null || dst == null || srcLen == 0 || dst.length == 0)
      throw new IllegalArgumentException("Empty source or/and destination byte array(s).");

    if (srcOffset < 0 || (srcLen > src.length) || srcOffset >= src.length)
      throw new ArrayIndexOutOfBoundsException("Source offset is out of bounds.");

    int decompressedSize =
        InternalJNI.decompressByteArray(
            session, src, srcOffset, srcLen, dst, dstOffset, dstLen, retryCount);

    return decompressedSize;
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
    if (!isValid) throw new IllegalStateException("QAT session has been closed.");

    if ((src == null || dst == null)
        || (src.position() == src.limit() || dst.position() == dst.limit()))
      throw new IllegalArgumentException();

    if (dst.isReadOnly()) throw new ReadOnlyBufferException();

    int decompressedSize = 0;
    if (src.hasArray() && dst.hasArray()) {
      decompressedSize =
          InternalJNI.decompressByteBuffer(
              session,
              src,
              src.array(),
              src.position(),
              src.remaining(),
              dst.array(),
              dst.position(),
              dst.remaining(),
              retryCount);
      dst.position(dst.position() + decompressedSize);
    } else if (src.isDirect() && dst.isDirect()) {
      decompressedSize =
          InternalJNI.decompressDirectByteBuffer(
              session,
              src,
              src.position(),
              src.remaining(),
              dst,
              dst.position(),
              dst.remaining(),
              retryCount);
    } else if (src.hasArray() && dst.isDirect()) {
      decompressedSize =
          InternalJNI.decompressDirectByteBufferDst(
              session,
              src,
              src.array(),
              src.position(),
              src.remaining(),
              dst,
              dst.position(),
              dst.remaining(),
              retryCount);
    } else if (src.isDirect() && dst.hasArray()) {
      decompressedSize =
          InternalJNI.decompressDirectByteBufferSrc(
              session,
              src,
              src.position(),
              src.remaining(),
              dst.array(),
              dst.position(),
              dst.remaining(),
              retryCount);
      dst.position(dst.position() + decompressedSize);
    } else {
      int srcLen = src.remaining();
      int dstLen = dst.remaining();

      byte[] srcArr = new byte[srcLen];
      byte[] dstArr = new byte[dstLen];

      src.get(srcArr);
      dst.get(dstArr);

      src.position(src.position() - srcLen);
      dst.position(dst.position() - dstLen);

      int srcPos = src.position();
      decompressedSize =
          InternalJNI.decompressByteBuffer(
              session, src, srcArr, 0, srcLen, dstArr, 0, dstLen, retryCount);
      src.position(srcPos + src.position());
      dst.put(dstArr, 0, decompressedSize);
    }

    if (decompressedSize < 0) throw new QatException("QAT: Compression failed");

    return decompressedSize;
  }

  /**
   * Ends the current QAT session by freeing up resources. A new session must be used after a
   * successful call of this method.
   *
   * @throws QatException if QAT session cannot be gracefully ended.
   */
  public void end() throws QatException {
    if (!isValid) throw new IllegalStateException("QAT session has been closed.");
    InternalJNI.teardown(session);
    isValid = false;
  }
}
