/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/
package com.intel.qat;

/** Class with static native function declaration */
class DummyJNI {
  /** This class contains static native method interface declarations required by JNI. */
  private DummyJNI() {}

  /** loads libqatzip.so while loading through static block */
  static {
    Native.loadLibrary();
  }

  // Dummy does not need to setup a QAT session
  //   static native void setup(QatZipper qzip, int mode, int codec, int level);

  static native int maxCompressedSize(int inverseCompressionRatio, long sourceSize);

  static native int compressByteArray(
      byte[] src, int srcOff, int srcLen, byte[] dst, int dstOff, int dstLen);

  static native int decompressByteArray(
      byte[] src, int srcOff, int srcLen, byte[] dst, int dstOff, int dstLen);

  // Dummy does not yet support Byte buffers
}
