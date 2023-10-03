package com.intel.qat;

import static com.intel.qat.QatZipper.Algorithm;

import java.nio.ByteBuffer;

public abstract class ZipperBackend {

  // TODO: How to store these fields?

  private boolean isValid;

  private int retryCount;

  // TODO: validateParams() shouldn't be in ZipperBackend. Each conrete Backend
  // should probably have something like it though
  /**
   * Validates compression level and retry counts.
   *
   * @param algorithm the compression {@link algorithm}
   * @param level the compression level.
   * @param retryCount how many times to seek for a hardware resources before giving up.
   * @return true if validation was successful, false otherwise.
   */
  abstract boolean validateParams(Algorithm algorithm, int level, int retryCount);

  /**
   * Returns the maximum compression length for the specified source length. Use this method to
   * estimate the size of a buffer for compression given the size of a source buffer.
   *
   * @param len the length of the source array or buffer.
   * @return the maximum compression length for the specified length.
   */
  public abstract int maxCompressedSize(long len);

  /**
   * Compresses the source array and stores the result in the destination array. Returns the actual
   * number of bytes of the compressed data.
   *
   * @param src the source array holding the source data
   * @param dst the destination array for the compressed data
   * @return the size of the compressed data in bytes
   */
  public abstract int compress(byte[] src, byte[] dst);

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
  public abstract int compress(
      byte[] src, int srcOffset, int srcLen, byte[] dst, int dstOffset, int dstLen);

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
  public abstract int compress(ByteBuffer src, ByteBuffer dst);

  /**
   * Decompresses the source array and stores the result in the destination array. Returns the
   * actual number of bytes of decompressed data.
   *
   * @param src the source array holding the compressed data
   * @param dst the destination array for the decompressed data
   * @return the size of the decompressed data in bytes
   */
  public abstract int decompress(byte[] src, byte[] dst);

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
  public abstract int decompress(
      byte[] src, int srcOffset, int srcLen, byte[] dst, int dstOffset, int dstLen);

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
  public abstract int decompress(ByteBuffer src, ByteBuffer dst);

  /**
   * Ends the current QAT session by freeing up resources. A new session must be used after a
   * successful call of this method.
   *
   * @throws QatException if QAT session cannot be gracefully ended.
   */
  public abstract void end() throws QatException;
}
