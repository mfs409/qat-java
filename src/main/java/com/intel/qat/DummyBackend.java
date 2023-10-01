package com.intel.qat;

/**
 * The DummyBackend class is written to become familiarize the high level components of qat-java. It
 * models the proposed classes of QatZipBackend.java and ZstdBackend.java - taking most of the same
 * functionality as the original QatZipper class.
 *
 * <p>DummyBackend calls native code which executes a dummy compression and decompression algorithm.
 * The 'compression' increases the size of the data, and the 'decompression' decreases the size of
 * the data.
 *
 * <p>Note that QAT is never invoked - the native code makes no calls to lower level libraries.
 */
public class DummyBackend {

  // Dummy does not support compress levels
  // public static final int DEFAULT_COMPRESS_LEVEL;

  // Dummy does not supprt retries
  // public static final int DEFAULT_RETRY_COUNT = 0;

  private boolean isValid;

  // dummy compression expands the data instead of truly compressing
  private int inverseCompressionRatio;

  private Algorithm algorithm;

  // Dummy does not support retries
  // private int retryCount;

  // Dummy does not support a Cleaner
  // /** Cleaner instance associated with this object. */
  // private static Cleaner cleaner;
  // /** Cleaner.Cleanable instance representing QAT cleanup action. */
  // private final Cleaner.Cleanable cleanable;
  // static {
  //   SecurityManager sm = System.getSecurityManager();
  //   if (sm == null) {
  //     cleaner = Cleaner.create();
  //   } else {
  //     java.security.PrivilegedAction<Void> pa =
  //         () -> {
  //           cleaner = Cleaner.create();
  //           return null;
  //         };
  //     java.security.AccessController.doPrivileged(pa);
  //   }
  // }

  // Dummy does not have a session to reference
  //  long session;

  // Dummy has no Mode with which to execute QAT
  // public static enum Mode {

  /** The compression algorithm to use. DUMMY is supported */
  public static enum Algorithm {
    DUMMY,
    DUMMY2;
  }

  public DummyBackend() {
    this(Algorithm.DUMMY, 2);
  }

  public DummyBackend(int inverseCompressionRatio) {
    this(Algorithm.DUMMY, inverseCompressionRatio);
  }

  public DummyBackend(Algorithm algorithm, int inverseCompressionRatio) {
    this.isValid = validateParams(algorithm, inverseCompressionRatio);
    this.algorithm = algorithm;
    this.inverseCompressionRatio = inverseCompressionRatio;
  }

  /**
   * Validates inverseCompressionRatio
   *
   * @param algorithm the compression algorithm
   * @param ratio the inverse compression ratio.
   * @return true if validation was successful, false otherwise.
   */
  private boolean validateParams(Algorithm algorithm, int ratio) {
    return !(ratio < 1 || ratio > 9);
  }

  /**
   * Returns the maximum compression length for the specified source length. Use this method to
   * estimate the size of a buffer for compression given the size of a source buffer.
   *
   * @param len the length of the source array or buffer.
   * @return the maximum compression length for the specified length.
   */
  public int maxCompressedLength(long len) {
    if (!isValid) throw new IllegalStateException("DummyBackend is not valid.");

    return DummyJNI.maxCompressedSize(this.inverseCompressionRatio, len);
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
    // Dummy does not check validity
    // if (!isValid) throw new IllegalStateException("QAT session has been closed.");

    if (src == null || dst == null || srcLen == 0 || dst.length == 0)
      throw new IllegalArgumentException(
          "Either source or destination array or both have size 0 or null value.");

    if (srcOffset < 0 || (srcLen > src.length) || srcOffset >= src.length)
      throw new ArrayIndexOutOfBoundsException("Source offset is out of bounds.");

    int compressedSize = -1;
    if (algorithm == Algorithm.DUMMY) {
      compressedSize = DummyJNI.compressByteArray(src, srcOffset, srcLen, dst, dstOffset, dstLen);
      // InternalJNI.compressByteArray(
      //     session, src, srcOffset, srcLen, dst, dstOffset, dstLen, retryCount);
    } else {
      throw new IllegalArgumentException(
          "Algorithm " + algorithm.name() + " is not supported by DummyBackend");
    }

    return compressedSize;
  }

  // Compression with ByteBuffers in not yet supported by Dummy

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
    // Dummy does not check validity
    // if (!isValid) throw new IllegalStateException("QAT session has been closed.");

    if (src == null || dst == null || srcLen == 0 || dst.length == 0)
      throw new IllegalArgumentException("Empty source or/and destination byte array(s).");

    if (srcOffset < 0 || (srcLen > src.length) || srcOffset >= src.length)
      throw new ArrayIndexOutOfBoundsException("Source offset is out of bounds.");

    int decompressedSize = -1;
    if (algorithm == Algorithm.DUMMY) {
      decompressedSize = DummyJNI.compressByteArray(src, srcOffset, srcLen, dst, dstOffset, dstLen);
      // InternalJNI.compressByteArray(
      //     session, src, srcOffset, srcLen, dst, dstOffset, dstLen, retryCount);
    } else {
      throw new IllegalArgumentException(
          "Algorithm " + algorithm.name() + " is not supported by DummyBackend");
    }
    return decompressedSize;
  }

  // Compression with ByteBuffers in not yet supported by Dummy

}
