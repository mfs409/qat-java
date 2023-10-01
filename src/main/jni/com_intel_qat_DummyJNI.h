/* Copied from com_intel_qat_InternalJNI.h - but 
a file like this should really be machine generated*/
#include <jni.h>

#ifndef _Included_com_intel_qat_InternalJNI
#define _Included_com_intel_qat_InternalJNI
#ifdef __cplusplus
extern "C" {
#endif

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    maxCompressedSize
 * Signature: (JJ)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_maxCompressedSize(
    JNIEnv *, jclass, jint, jlong);

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    compressByteArray
 * Signature: (J[BII[BIII)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_compressByteArray(
    JNIEnv *, jclass, jbyteArray, jint, jint, jbyteArray, jint, jint);

/*
 * Class:     com_intel_qat_InternalJNI
 * Method:    decompressByteArray
 * Signature: (J[BII[BIII)I
 */
JNIEXPORT jint JNICALL Java_com_intel_qat_InternalJNI_decompressByteArray(
    JNIEnv *, jclass, jbyteArray, jint, jint, jbyteArray, jint, jint);



#ifdef __cplusplus
}
#endif
#endif