/*******************************************************************************
 * Copyright (C) 2023 Intel Corporation
 *
 * SPDX-License-Identifier: BSD
 ******************************************************************************/

#include "com_intel_qat_ZstdJNI.h"

#include <qatseqprod.h>
#include <stdatomic.h>
#include <stdlib.h>
#include <zstd.h>

#include "qatzip.h"
#include "util.h"

/**
 * Number of active sessions. Used to prevent accidentally stopping or starting
 * the QAT device when used by multiple threads.
 */
// static _Atomic int active_sessions = 0;
// static int initialized = 0;

/**
 * The fieldID for java.nio.ByteBuffer/position
 */
_Thread_local static jfieldID nio_bytebuffer_position_id;

/**
 * Compresses a buffer pointed to by the given source pointer and writes it to
 * the destination buffer pointed to by the destination pointer. The read and
 * write of the source and destination buffers is bounded by the source and
 * destination lengths respectively.
 *
 * @param env a pointer to the JNI environment.
 * @param cctx a pointer to the ZSTD_CCtx object.
 * @param src_ptr the source buffer.
 * @param src_len the size of the source buffer.
 * @param dst_ptr the destination buffer.
 * @param dst_len the size of the destination buffer.
 * @param bytes_read an out parameter that stores the bytes read from the source
 * buffer.
 * @param bytes_written an out parameter that stores the bytes written to the
 * destination buffer.
 * @param retry_count the number of compression retries before we give up.
 * @return QZ_OK (0) if successful, non-zero otherwise.
 */
static void compress(JNIEnv *env, ZSTD_CCtx *cctx, unsigned char *src_ptr,
                     unsigned int src_len, unsigned char *dst_ptr,
                     unsigned int dst_len, int *bytes_read, int *bytes_written,
                     int retry_count) {
  (void)retry_count;  // TODO: implement retry

  fprintf(stderr, "about to compress\n");
  fflush(stderr);
  fprintf(stderr, "src %p len %d dst %p len %d\n", src_ptr, src_len, dst_ptr,
          dst_len);
  fflush(stderr);
  fprintf(stderr, "cctx %p\n", (void *)cctx);
  fflush(stderr);

  size_t res = ZSTD_compress2(cctx, dst_ptr, dst_len, src_ptr, src_len);
  fprintf(stderr, "just compressed\n");
  fflush(stderr);
  if (ZSTD_isError(res)) {
    fprintf(stderr, "... and got zstd error: %s\n", ZSTD_getErrorName(res));
    fflush(stderr);
    throw_exception(env, res, ZSTD_getErrorName(res));
    return;
  }
  if (res != dst_len) {
    char *msg = malloc(1000);
    msg[0] = '\0';
    sprintf(msg, "Failed to fully compress the input: %lu of %d", res, dst_len);
    throw_exception(env, res, msg);
  }

  *bytes_read = src_len;
  *bytes_written = dst_len;
}

/**
 * Decmpresses a buffer pointed to by the given source pointer and writes it to
 * the destination buffer pointed to by the destination pointer. The read and
 * write of the source and destination buffers is bounded by the source and
 * destination lengths respectively.
 *
 * @param env a pointer to the JNI environment.
 * @param dctx a pointer to the ZSTD_DCtx object.
 * @param src_ptr the source buffer.
 * @param src_len the size of the source buffer.
 * @param dst_ptr the destination buffer.
 * @param dst_len the size of the destination buffer.
 * @param bytes_read an out parameter that stores the bytes read from the source
 * buffer.
 * @param bytes_written an out parameter that stores the bytes written to the
 * destination buffer.
 * @param retry_count the number of decompression retries before we give up.
 */
static void decompress(JNIEnv *env, ZSTD_DCtx *dctx, unsigned char *src_ptr,
                       unsigned int src_len, unsigned char *dst_ptr,
                       unsigned int dst_len, int *bytes_read,
                       int *bytes_written, int retry_count) {
  (void)retry_count;  // TODO: implement retry

  fprintf(stderr, "about to decompress\n");
  fflush(stderr);
  fprintf(stderr, "src %p len %d dst %p len %d\n", src_ptr, src_len, dst_ptr,
          dst_len);
  fflush(stderr);
  fprintf(stderr, "dctx %p\n", (void *)dctx);
  fflush(stderr);

  size_t res = ZSTD_decompressDCtx(dctx, dst_ptr, dst_len, src_ptr, src_len);
  fprintf(stderr, "just decompressed\n");
  fflush(stderr);
  if (ZSTD_isError(res)) {
    fprintf(stderr, "... and got zstd error: %s\n", ZSTD_getErrorName(res));
    fflush(stderr);
    throw_exception(env, res, ZSTD_getErrorName(res));
    return;
  }
  if (res != dst_len) {
    char *msg = malloc(1000);
    msg[0] = '\0';
    sprintf(msg, "Failed to fully decompress the input: %lu of %d", res, dst_len);
    throw_exception(env, res, msg);
    return;
  }

  *bytes_read = src_len;
  *bytes_written = dst_len;
}

/*
 * Setups a QAT session.
 *
 * Class:     com_intel_qat_InternalJNI
 * Method:    setup
 * Signature: (Lcom/intel/qat/QatZipper;III)V
 */
JNIEXPORT void JNICALL Java_com_intel_qat_ZstdJNI_setup(
    JNIEnv *env, jobject obj, jobject zstd_backend, jint sw_backup,
    jint comp_algorithm, jint level) {
  (void)obj;
  (void)comp_algorithm;

  // save the fieldID of nio.ByteBuffer.position
  nio_bytebuffer_position_id = (*env)->GetFieldID(
      env, (*env)->FindClass(env, "java/nio/ByteBuffer"), "position", "I");

  // Start QAT device
  // if (atomic_fetch_add(&active_sessions, 1) == 0) {
  // if (initialized == 0) {
  //   initialized = 1;
  int status0 = QZSTD_startQatDevice();
  if (status0 != QZSTD_OK) {
    throw_exception(env, status0, "Initializing QAT HW failed.");
    return;
  }
  // }

  // Create compression/decompression contexts
  ZSTD_CCtx *cctx = ZSTD_createCCtx();
  ZSTD_DCtx *dctx = ZSTD_createDCtx();

  // Create sequence producer state for QAT sequence producer
  void *sequenceProducerState = QZSTD_createSeqProdState();
  if (!sequenceProducerState) {
    throw_exception(env, -1,
                    "Initializing QAT sequence producer state failed.");
    return;
  }

  // Register qatSequenceProducer (from qatseqprod.h)
  ZSTD_registerSequenceProducer(cctx, sequenceProducerState,
                                qatSequenceProducer);

  // Enable sequence producer fallback (if sw_backup is true)
  size_t status =
      ZSTD_CCtx_setParameter(cctx, ZSTD_c_enableSeqProducerFallback, sw_backup);
  if (ZSTD_isError(status)) {
    // TODO: Should we check the zstd version?
    throw_exception(env, -1,
                    "Configuring sequence producer fallback failed. Are you "
                    "using zstd 1.5.5 or newer?");
    return;
  }

  // Set compression level
  status = ZSTD_CCtx_setParameter(cctx, ZSTD_c_compressionLevel, level);
  if (ZSTD_isError(status)) {
    throw_exception(env, -1, "Configuring compression level failed.");
    return;
  }

  jclass backend_class = (*env)->FindClass(env, "com/intel/qat/ZstdBackend");
  jfieldID cctx_field = (*env)->GetFieldID(env, backend_class, "cctx", "J");
  jfieldID dctx_field = (*env)->GetFieldID(env, backend_class, "dctx", "J");
  jfieldID seqProdState_field =
      (*env)->GetFieldID(env, backend_class, "sequenceProducerState", "J");
  (*env)->SetLongField(env, zstd_backend, cctx_field, (jlong)cctx);
  (*env)->SetLongField(env, zstd_backend, dctx_field, (jlong)dctx);
  (*env)->SetLongField(env, zstd_backend, seqProdState_field,
                       (jlong)sequenceProducerState);
}

/*
 * Compresses a byte array.
 *
 * Class:     com_intel_qat_InternalJNI
 * Method:    compressByteArray
 * Signature: (J[BII[BIII)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_ZstdJNI_compressByteArray(
    JNIEnv *env, jobject obj, jlong long_cctx, jbyteArray src_arr, jint src_pos,
    jint src_len, jbyteArray dst_arr, jint dst_pos, jint dst_len,
    jint retry_count) {
  (void)obj;

  ZSTD_CCtx *cctx = (ZSTD_CCtx *)long_cctx;

  unsigned char *src_ptr =
      (unsigned char *)(*env)->GetByteArrayElements(env, src_arr, NULL);
  unsigned char *dst_ptr =
      (unsigned char *)(*env)->GetByteArrayElements(env, dst_arr, NULL);

  int bytes_read = 0;
  int bytes_written = 0;

  compress(env, cctx, src_ptr + src_pos, src_len, dst_ptr + dst_pos, dst_len,
           &bytes_read, &bytes_written, retry_count);

  (*env)->ReleaseByteArrayElements(env, src_arr, (jbyte *)src_ptr, 0);
  (*env)->ReleaseByteArrayElements(env, dst_arr, (jbyte *)dst_ptr, 0);

  return bytes_written;
}

/*
 * Decompresses a byte array.
 *
 * Class:     com_intel_qat_InternalJNI
 * Method:    decompressByteArray
 * Signature: (J[BII[BIII)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_ZstdJNI_decompressByteArray(
    JNIEnv *env, jobject obj, jlong long_dctx, jbyteArray src_arr, jint src_pos,
    jint src_len, jbyteArray dst_arr, jint dst_pos, jint dst_len,
    jint retry_count) {
  (void)obj;

  ZSTD_DCtx *dctx = (ZSTD_DCtx *)long_dctx;
  unsigned char *src_ptr =
      (unsigned char *)(*env)->GetByteArrayElements(env, src_arr, NULL);
  unsigned char *dst_ptr =
      (unsigned char *)(*env)->GetByteArrayElements(env, dst_arr, NULL);

  int bytes_read = 0;
  int bytes_written = 0;

  decompress(env, dctx, src_ptr + src_pos, src_len, dst_ptr + dst_pos, dst_len,
             &bytes_read, &bytes_written, retry_count);

  (*env)->ReleaseByteArrayElements(env, src_arr, (jbyte *)src_ptr, 0);
  (*env)->ReleaseByteArrayElements(env, dst_arr, (jbyte *)dst_ptr, 0);

  return bytes_written;
}

/*
 * Compresses a byte buffer.
 *
 * Class:     com_intel_qat_InternalJNI
 * Method:    compressByteBuffer
 * Signature: (JLjava/nio/ByteBuffer;[BII[BIII)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_ZstdJNI_compressByteBuffer(
    JNIEnv *env, jobject obj, jlong long_cctx, jobject src_buf,
    jbyteArray src_arr, jint src_pos, jint src_len, jbyteArray dst_arr,
    jint dst_pos, jint dst_len, jint retry_count) {
  (void)obj;

  ZSTD_CCtx *cctx = (ZSTD_CCtx *)long_cctx;

  unsigned char *src_ptr =
      (unsigned char *)(*env)->GetByteArrayElements(env, src_arr, NULL);
  unsigned char *dst_ptr =
      (unsigned char *)(*env)->GetByteArrayElements(env, dst_arr, NULL);

  int bytes_read = 0;
  int bytes_written = 0;

  compress(env, cctx, src_ptr + src_pos, src_len, dst_ptr + dst_pos, dst_len,
           &bytes_read, &bytes_written, retry_count);

  (*env)->ReleaseByteArrayElements(env, src_arr, (jbyte *)src_ptr, 0);
  (*env)->ReleaseByteArrayElements(env, dst_arr, (jbyte *)dst_ptr, 0);

  (*env)->SetIntField(env, src_buf, nio_bytebuffer_position_id,
                      src_pos + bytes_read);

  return bytes_written;
}

/*
 * Decompresses a byte buffer.
 *
 * Class:     com_intel_qat_InternalJNI
 * Method:    decompressByteBuffer
 * Signature: (JLjava/nio/ByteBuffer;[BII[BIII)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_ZstdJNI_decompressByteBuffer(
    JNIEnv *env, jobject obj, jlong long_dctx, jobject src_buf,
    jbyteArray src_arr, jint src_pos, jint src_len, jbyteArray dst_arr,
    jint dst_pos, jint dst_len, jint retry_count) {
  (void)obj;

  ZSTD_DCtx *dctx = (ZSTD_DCtx *)long_dctx;
  unsigned char *src_ptr =
      (unsigned char *)(*env)->GetByteArrayElements(env, src_arr, NULL);
  unsigned char *dst_ptr =
      (unsigned char *)(*env)->GetByteArrayElements(env, dst_arr, NULL);

  int bytes_read = 0;
  int bytes_written = 0;

  decompress(env, dctx, src_ptr + src_pos, src_len, dst_ptr + dst_pos, dst_len,
             &bytes_read, &bytes_written, retry_count);

  (*env)->ReleaseByteArrayElements(env, src_arr, (jbyte *)src_ptr, 0);
  (*env)->ReleaseByteArrayElements(env, dst_arr, (jbyte *)dst_ptr, 0);

  (*env)->SetIntField(env, src_buf, nio_bytebuffer_position_id,
                      src_pos + bytes_read);

  return bytes_written;
}

/*
 *  Compresses a direct byte buffer.
 *
 * Class:     com_intel_qat_InternalJNI
 * Method:    compressDirectByteBuffer
 * Signature: (JLjava/nio/ByteBuffer;IILjava/nio/ByteBuffer;III)I
 */
jint JNICALL Java_com_intel_qat_ZstdJNI_compressDirectByteBuffer(
    JNIEnv *env, jobject obj, jlong long_cctx, jobject src_buf, jint src_pos,
    jint src_len, jobject dst_buf, jint dst_pos, jint dst_len,
    jint retry_count) {
  (void)obj;

  ZSTD_CCtx *cctx = (ZSTD_CCtx *)long_cctx;
  unsigned char *src_ptr =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, src_buf);
  unsigned char *dst_ptr =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, dst_buf);

  int bytes_read = 0;
  int bytes_written = 0;

  compress(env, cctx, src_ptr + src_pos, src_len, dst_ptr + dst_pos, dst_len,
           &bytes_read, &bytes_written, retry_count);

  // set src and dest buffer positions
  (*env)->SetIntField(env, src_buf, nio_bytebuffer_position_id,
                      src_pos + bytes_read);
  (*env)->SetIntField(env, dst_buf, nio_bytebuffer_position_id,
                      dst_pos + bytes_written);

  return bytes_written;
}

/*
 *  Decompresses a direct byte buffer.
 *
 * Class:     com_intel_qat_InternalJNI
 * Method:    decompressDirectByteBuffer
 * Signature: (JLjava/nio/ByteBuffer;IILjava/nio/ByteBuffer;III)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_ZstdJNI_decompressDirectByteBuffer(
    JNIEnv *env, jobject obj, jlong long_dctx, jobject src_buf, jint src_pos,
    jint src_len, jobject dst_buf, jint dst_pos, jint dst_len,
    jint retry_count) {
  (void)obj;

  ZSTD_DCtx *dctx = (ZSTD_DCtx *)long_dctx;
  unsigned char *src_ptr =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, src_buf);
  unsigned char *dst_ptr =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, dst_buf);

  int bytes_read = 0;
  int bytes_written = 0;

  decompress(env, dctx, src_ptr + src_pos, src_len, dst_ptr + dst_pos, dst_len,
             &bytes_read, &bytes_written, retry_count);

  // set src and dest buffer positions
  (*env)->SetIntField(env, src_buf, nio_bytebuffer_position_id,
                      src_pos + bytes_read);
  (*env)->SetIntField(env, dst_buf, nio_bytebuffer_position_id,
                      dst_pos + bytes_written);

  return bytes_written;
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    compressDirectByteBufferSrc
 * Signature: (JLjava/nio/ByteBuffer;II[BIII)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_ZstdJNI_compressDirectByteBufferSrc(
    JNIEnv *env, jobject obj, jlong long_cctx, jobject src_buf, jint src_pos,
    jint src_len, jbyteArray dst_arr, jint dst_pos, jint dst_len,
    jint retry_count) {
  (void)obj;

  ZSTD_CCtx *cctx = (ZSTD_CCtx *)long_cctx;
  unsigned char *src_ptr =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, src_buf);
  unsigned char *dst_ptr =
      (unsigned char *)(*env)->GetByteArrayElements(env, dst_arr, NULL);

  int bytes_read = 0;
  int bytes_written = 0;

  compress(env, cctx, src_ptr + src_pos, src_len, dst_ptr + dst_pos, dst_len,
           &bytes_read, &bytes_written, retry_count);

  (*env)->ReleaseByteArrayElements(env, dst_arr, (jbyte *)dst_ptr, 0);

  (*env)->SetIntField(env, src_buf, nio_bytebuffer_position_id,
                      src_pos + bytes_read);

  return bytes_written;
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    decompressDirectByteBufferSrc
 * Signature: (JLjava/nio/ByteBuffer;II[BIII)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_ZstdJNI_decompressDirectByteBufferSrc(
    JNIEnv *env, jobject obj, jlong long_dctx, jobject src_buf, jint src_pos,
    jint src_len, jbyteArray dst_arr, jint dst_pos, jint dst_len,
    jint retry_count) {
  (void)obj;

  ZSTD_DCtx *dctx = (ZSTD_DCtx *)long_dctx;
  unsigned char *src_ptr =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, src_buf);
  unsigned char *dst_ptr =
      (unsigned char *)(*env)->GetByteArrayElements(env, dst_arr, NULL);

  int bytes_read = 0;
  int bytes_written = 0;

  decompress(env, dctx, src_ptr + src_pos, src_len, dst_ptr + dst_pos, dst_len,
             &bytes_read, &bytes_written, retry_count);

  (*env)->ReleaseByteArrayElements(env, dst_arr, (jbyte *)dst_ptr, 0);

  (*env)->SetIntField(env, src_buf, nio_bytebuffer_position_id,
                      src_pos + bytes_read);

  return bytes_written;
}

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    compressDirectByteBufferDst
 * Signature: (J[BIILjava/nio/ByteBuffer;III)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_ZstdJNI_compressDirectByteBufferDst(
    JNIEnv *env, jobject obj, jlong long_cctx, jobject src_buf,
    jbyteArray src_arr, jint src_pos, jint src_len, jobject dst_buf,
    jint dst_pos, jint dst_len, jint retry_count) {
  (void)obj;

  ZSTD_CCtx *cctx = (ZSTD_CCtx *)long_cctx;
  unsigned char *src_ptr =
      (unsigned char *)(*env)->GetByteArrayElements(env, src_arr, NULL);
  unsigned char *dst_ptr =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, dst_buf);

  int bytes_read = 0;
  int bytes_written = 0;

  compress(env, cctx, src_ptr + src_pos, src_len, dst_ptr + dst_pos, dst_len,
           &bytes_read, &bytes_written, retry_count);

  (*env)->ReleaseByteArrayElements(env, src_arr, (jbyte *)src_ptr, 0);

  (*env)->SetIntField(env, src_buf, nio_bytebuffer_position_id,
                      src_pos + bytes_read);
  (*env)->SetIntField(env, dst_buf, nio_bytebuffer_position_id,
                      dst_pos + bytes_written);

  return bytes_written;
}
/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    decompressDirectByteBufferDst
 * Signature: (JLjava/nio/ByteBuffer;[BIILjava/nio/ByteBuffer;III)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_ZstdJNI_decompressDirectByteBufferDst(
    JNIEnv *env, jobject obj, jlong long_dctx, jobject src_buf,
    jbyteArray src_arr, jint src_pos, jint src_len, jobject dst_buf,
    jint dst_pos, jint dst_len, jint retry_count) {
  (void)obj;

  ZSTD_DCtx *dctx = (ZSTD_DCtx *)long_dctx;
  unsigned char *src_ptr =
      (unsigned char *)(*env)->GetByteArrayElements(env, src_arr, NULL);
  unsigned char *dst_ptr =
      (unsigned char *)(*env)->GetDirectBufferAddress(env, dst_buf);

  int bytes_read = 0;
  int bytes_written = 0;

  decompress(env, dctx, src_ptr + src_pos, src_len, dst_ptr + dst_pos, dst_len,
             &bytes_read, &bytes_written, retry_count);

  (*env)->ReleaseByteArrayElements(env, src_arr, (jbyte *)src_ptr, 0);

  (*env)->SetIntField(env, src_buf, nio_bytebuffer_position_id,
                      src_pos + bytes_read);
  (*env)->SetIntField(env, dst_buf, nio_bytebuffer_position_id,
                      dst_pos + bytes_written);

  return bytes_written;
}

/*
 * Evaluates the maximum compressed size for the given buffer size.
 *
 * Class:     com_intel_qat_InternalJNI
 * Method:    maxCompressedSize
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_ZstdJNI_maxCompressedSize(
    JNIEnv *env, jclass obj, jlong src_size) {
  (void)env;
  (void)obj;

  return ZSTD_compressBound(src_size);
}

/*
 * Tearsdown the given QAT session.
 *
 * Class:     com_intel_qat_ZstdJNI
 * Method:    teardown
 * Signature: (JJJ)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_ZstdJNI_teardown(
    JNIEnv *env, jclass obj, jlong long_cctx, jlong long_dctx,
    jlong long_sequenceProducerState) {
  (void)obj;
  (void)env;

  ZSTD_CCtx *cctx = (ZSTD_CCtx *)long_cctx;
  ZSTD_DCtx *dctx = (ZSTD_DCtx *)long_dctx;
  void *sequenceProducerState = (void *)long_sequenceProducerState;

  fprintf(stderr, "about to free\n");
  fflush(stderr);

  if (cctx) ZSTD_freeCCtx(cctx);
  if (dctx) ZSTD_freeDCtx(dctx);
  if (sequenceProducerState) QZSTD_freeSeqProdState(sequenceProducerState);
  // if (atomic_fetch_sub(&active_sessions, 1) == 1) QZSTD_stopQatDevice();
  // if (atomic_fetch_sub(&active_sessions, 1) == 1) QZSTD_stopQatDevice();
  QZSTD_stopQatDevice();

  return QZ_OK;
}
