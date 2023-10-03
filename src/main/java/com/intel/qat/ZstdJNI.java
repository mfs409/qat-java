/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/
package com.intel.qat;

import java.nio.ByteBuffer;

/** Class with static native function declaration */
class ZstdJNI {
  /** This class contains static native method interface declarations required by JNI. */
  private ZstdJNI() {}

  /** loads libqatzip.so while loading through static block */
  static {
    Native.loadLibrary();
  }

  static native void setup(ZstdBackend backend, int mode, int codec, int level);

  static native int maxCompressedSize(long sourceSize);

  static native int compressByteArray(
      long cctx,
      byte[] src,
      int srcOff,
      int srcLen,
      byte[] dst,
      int dstOff,
      int dstLen,
      int retryCount);

  static native int decompressByteArray(
      long dctx,
      byte[] src,
      int srcOff,
      int srcLen,
      byte[] dst,
      int dstOff,
      int dstLen,
      int retryCount);

  static native int compressByteBuffer(
      long cctx,
      ByteBuffer srcBuffer,
      byte[] src,
      int srcOff,
      int srcLen,
      byte[] dst,
      int dstOff,
      int dstLen,
      int retryCount);

  static native int decompressByteBuffer(
      long dctx,
      ByteBuffer srcBuffer,
      byte[] src,
      int srcOff,
      int srcLen,
      byte[] dst,
      int dstOff,
      int dstLen,
      int retryCount);

  static native int compressDirectByteBuffer(
      long cctx,
      ByteBuffer src,
      int srcOff,
      int srcLen,
      ByteBuffer dst,
      int dstOff,
      int dstLen,
      int retryCount);

  static native int decompressDirectByteBuffer(
      long dctx,
      ByteBuffer src,
      int srcOff,
      int srcLen,
      ByteBuffer dst,
      int dstOff,
      int dstLen,
      int retryCount);

  static native int compressDirectByteBufferSrc(
      long cctx,
      ByteBuffer src,
      int srcOff,
      int srcLen,
      byte[] dstArr,
      int dstOff,
      int dstLen,
      int retryCount);

  static native int decompressDirectByteBufferSrc(
      long dctx,
      ByteBuffer src,
      int srcOff,
      int srcLen,
      byte[] dstArr,
      int dstOff,
      int dstLen,
      int retryCount);

  static native int compressDirectByteBufferDst(
      long cctx,
      ByteBuffer src,
      byte[] srcArr,
      int srcOff,
      int srcLen,
      ByteBuffer dst,
      int dstOff,
      int dstLen,
      int retryCount);

  static native int decompressDirectByteBufferDst(
      long dctx,
      ByteBuffer src,
      byte[] srcArr,
      int srcOff,
      int srcLen,
      ByteBuffer dst,
      int dstOff,
      int dstLen,
      int retryCount);

  static native int teardown(long cctx, long dctx, long sequenceProducerState);
}
