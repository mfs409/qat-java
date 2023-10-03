package com.intel.qat;

import com.intel.qat.QatZipper.Algorithm;
import com.intel.qat.QatZipper.Mode;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;

public class ZstdBackend extends ZipperBackend {
  private int retryCount;
  long cctx;
  long dctx;
  long sequenceProducerState;
  private boolean isValid;

  public ZstdBackend(Algorithm algorithm, int level, Mode mode, int retryCount) {
    assert algorithm == Algorithm.ZSTD;
    if (!validateParams(algorithm, level, retryCount))
      throw new IllegalArgumentException("Invalid compression level or retry count.");

    this.retryCount = retryCount;
    ZstdJNI.setup(this, mode.ordinal(), algorithm.ordinal(), level);
    this.isValid = true;
  }

  @Override
  public int compress(byte[] src, byte[] dst) {
    return compress(src, 0, src.length, dst, 0, dst.length);
  }

  @Override
  public int compress(
      byte[] src, int srcOffset, int srcLen, byte[] dst, int dstOffset, int dstLen) {
    if (!isValid) throw new IllegalStateException("QAT session has been closed.");
    if (src == null || dst == null || srcLen == 0 || dst.length == 0)
      throw new IllegalArgumentException(
          "Either source or destination array or both have size 0 or null value.");

    if (srcOffset < 0 || (srcLen > src.length) || srcOffset >= src.length)
      throw new ArrayIndexOutOfBoundsException("Source offset is out of bounds.");

    int compressedSize =
        ZstdJNI.compressByteArray(cctx, src, srcOffset, srcLen, dst, dstOffset, dstLen, retryCount);

    return compressedSize;
  }

  @Override
  public int compress(ByteBuffer src, ByteBuffer dst) {
    if (!isValid) throw new IllegalStateException("QAT session has been closed.");
    if ((src == null || dst == null)
        || (src.position() == src.limit() || dst.position() == dst.limit()))
      throw new IllegalArgumentException();

    if (dst.isReadOnly()) throw new ReadOnlyBufferException();

    int compressedSize = 0;
    if (src.hasArray() && dst.hasArray()) {
      compressedSize =
          ZstdJNI.compressByteBuffer(
              cctx,
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
          ZstdJNI.compressDirectByteBuffer(
              cctx,
              src,
              src.position(),
              src.remaining(),
              dst,
              dst.position(),
              dst.remaining(),
              retryCount);
    } else if (src.hasArray() && dst.isDirect()) {
      compressedSize =
          ZstdJNI.compressDirectByteBufferDst(
              cctx,
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
          ZstdJNI.compressDirectByteBufferSrc(
              cctx,
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
          ZstdJNI.compressByteBuffer(cctx, src, srcArr, 0, srcLen, dstArr, 0, dstLen, retryCount);
      src.position(srcPos + src.position());
      dst.put(dstArr, 0, compressedSize);
    }

    return compressedSize;
  }

  @Override
  public int decompress(byte[] src, byte[] dst) {
    return decompress(src, 0, src.length, dst, 0, dst.length);
  }

  @Override
  public int decompress(
      byte[] src, int srcOffset, int srcLen, byte[] dst, int dstOffset, int dstLen) {
    if (!isValid) throw new IllegalStateException("QAT session has been closed.");
    if (src == null || dst == null || srcLen == 0 || dst.length == 0)
      throw new IllegalArgumentException("Empty source or/and destination byte array(s).");

    if (srcOffset < 0 || (srcLen > src.length) || srcOffset >= src.length)
      throw new ArrayIndexOutOfBoundsException("Source offset is out of bounds.");

    int decompressedSize =
        ZstdJNI.decompressByteArray(
            dctx, src, srcOffset, srcLen, dst, dstOffset, dstLen, retryCount);

    return decompressedSize;
  }

  @Override
  public int decompress(ByteBuffer src, ByteBuffer dst) {
    if ((src == null || dst == null)
        || (src.position() == src.limit() || dst.position() == dst.limit()))
      throw new IllegalArgumentException();

    if (dst.isReadOnly()) throw new ReadOnlyBufferException();

    int decompressedSize = 0;
    if (src.hasArray() && dst.hasArray()) {
      decompressedSize =
          ZstdJNI.decompressByteBuffer(
              dctx,
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
          ZstdJNI.decompressDirectByteBuffer(
              dctx,
              src,
              src.position(),
              src.remaining(),
              dst,
              dst.position(),
              dst.remaining(),
              retryCount);
    } else if (src.hasArray() && dst.isDirect()) {
      decompressedSize =
          ZstdJNI.decompressDirectByteBufferDst(
              dctx,
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
          ZstdJNI.decompressDirectByteBufferSrc(
              dctx,
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
          ZstdJNI.decompressByteBuffer(dctx, src, srcArr, 0, srcLen, dstArr, 0, dstLen, retryCount);
      src.position(srcPos + src.position());
      dst.put(dstArr, 0, decompressedSize);
    }

    if (decompressedSize < 0) throw new QatException("QAT: Compression failed");

    return decompressedSize;
  }

  @Override
  public void end() throws QatException {
    if (!isValid) return;
    isValid = false;
    ZstdJNI.teardown(cctx, dctx, sequenceProducerState);
    cctx = 0;
    dctx = 0;
    sequenceProducerState = 0;
  }

  @Override
  public int maxCompressedSize(long len) {
    if (!isValid) throw new IllegalStateException("QAT session has been closed.");
    return ZstdJNI.maxCompressedSize(len);
  }

  @Override
  boolean validateParams(Algorithm algorithm, int level, int retryCount) {
    // TODO
    return true;
  }
}
