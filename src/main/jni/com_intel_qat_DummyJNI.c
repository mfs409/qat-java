#include "com_intel_qat_DummyJNI.h"

#include <stdlib.h>

// qat is not used for Dummy
// #include "qatzip.h"
#include "util.h"
#include <stdio.h>

/*
 * Evaluates the maximum compressed size for the given buffer size.
 *
 * Class:     com_intel_qat_InternalJNI
 * Method:    maxCompressedSize
 * Signature: (JJ)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_DummyJNI_maxCompressedSize(
    JNIEnv *env, jclass obj, jint ratio, jlong src_size) {
  (void)env;
  (void)obj;

  // Dummy compression expands the original data by a factor of ratio
  return ratio * src_size;
}


/*
 * Compresses a byte array.
 *
 * Class:     com_intel_qat_InternalJNI
 * Method:    compressByteArray
 * Signature: (J[BII[BIII)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_DummyJNI_compressByteArray(
    JNIEnv *env, jobject obj, jbyteArray src_arr, jint src_pos,
    jint src_len, jbyteArray dst_arr, jint dst_pos, jint dst_len) {
  (void)obj;

  // QzSession_T *qz_session = (QzSession_T *)sess;

  unsigned char *src_ptr =
      (unsigned char *)(*env)->GetByteArrayElements(env, src_arr, NULL);
  unsigned char *dst_ptr =
      (unsigned char *)(*env)->GetByteArrayElements(env, dst_arr, NULL);

  // // get the expansion ratio from the environment
  // jclass dummy_class = (*env)->GetObjectClass(env, obj);
  // jfieldID fid_ratio = (*env)->GetFieldID(env, dummy_class, "inverseCompressionRatio", "I");
  // jint expansion_ratio = (*env)-> GetIntField(env, obj, fid_ratio);

  // for now just set the ratio to 2. TODO: implent arbirtrary ratios
  int expansion_ratio = 2;
  fprintf(stdout, "The original data will by expanded by a factor of %d\n", expansion_ratio);

  // int bytes_read = 0;
  int bytes_written = 0;

  // compress(env, qz_session, src_ptr + src_pos, src_len, dst_ptr + dst_pos,
  //          dst_len, &bytes_read, &bytes_written, retry_count);

  // right now a x2 ratio is hard-coded
  //TODO: add a nested for loop to adjust to expansion_ratio
  // for(int i=0; i<src_len && i<dst_len; i++){
  //   dst_ptr[dst_pos + i] = src_ptr[src_pos + i]; // this will copy the existing src array to dst
  //   dst_ptr[dst_pos + 1*src_len + i] = src_ptr[src_pos + i]; // dumb compression copies the same bytes 
  //   bytes_written ++;
  
  //   //bytes_read ++;
  // }
  for (int j = 0; j < expansion_ratio; j++) {
   for (int i = 0; i < src_len && bytes_written < dst_len; i++) {
      dst_ptr[dst_pos + bytes_written] = src_ptr[src_pos + i];
      bytes_written++;
    }
  }


  (*env)->ReleaseByteArrayElements(env, src_arr, (jbyte *)src_ptr, 0);
  (*env)->ReleaseByteArrayElements(env, dst_arr, (jbyte *)dst_ptr, 0);

  return bytes_written;
}