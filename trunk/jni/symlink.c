#include <stdio.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <string.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Class:     org_dyndns_fules_Symlink
 * Method:    create
 * Signature: (Ljava/lang/String;Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_org_dyndns_fules_Symlink_create(JNIEnv *env, jclass clazz, jstring sFrom, jstring sTo) {
	const char *from = (*env)->GetStringUTFChars(env, sFrom, 0);
	const char *to   = (*env)->GetStringUTFChars(env, sTo, 0);
	int result = symlink(from, to);
	(*env)->ReleaseStringUTFChars(env, sFrom, from);
	(*env)->ReleaseStringUTFChars(env, sTo, to);
	return result;
} 

/*
 * Class:     org_dyndns_fules_Symlink
 * Method:    isLink
 * Signature: (Ljava/lang/String;)B
 */
JNIEXPORT jboolean JNICALL Java_org_dyndns_fules_Symlink_isLink(JNIEnv *env, jclass clazz, jstring sName) {
	const char *name = (*env)->GetStringUTFChars(env, sName, 0);
    struct stat st;
	int result = !lstat(name, &st);
    result &= S_ISLNK(st.st_mode);
	(*env)->ReleaseStringUTFChars(env, sName, name);
	return result;
} 

/*
 * Class:     org_dyndns_fules_Symlink
 * Method:    readLink
 * Signature: (Ljava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jobject JNICALL Java_org_dyndns_fules_Symlink_readLink(JNIEnv *env, jclass clazz, jstring sName) {
    char lname[1024];
	const char *name = (*env)->GetStringUTFChars(env, sName, 0);
    jobject res = NULL;
	int result = readlink(name, lname, sizeof(lname)-1);
    if (result >= 0) {
        lname[result] = '\0';
        res = (*env)->NewStringUTF(env, lname);
    }
	(*env)->ReleaseStringUTFChars(env, sName, name);
	return res;
} 

#ifdef __cplusplus
}
#endif

