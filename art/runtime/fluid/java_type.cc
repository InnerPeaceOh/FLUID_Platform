/**
 *   The MIT License (MIT)
 *   Copyright (C) 2016 ZongXian Shen <andy.zsshen@gmail.com>
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a
 *   copy of this software and associated documentation files (the "Software"),
 *   to deal in the Software without restriction, including without limitation
 *   the rights to use, copy, modify, merge, publish, distribute, sublicense,
 *   and/or sell copies of the Software, and to permit persons to whom the
 *   Software is furnished to do so, subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in
 *   all copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *   FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 *   THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *   LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *   OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 *   IN THE SOFTWARE.
 */

#if defined(__aarch64__) || defined(__arm__)
#include "java_type.h"
#include "gadget.h"

namespace art {
namespace fluid {

PrimitiveTypeWrapper::~PrimitiveTypeWrapper() {
  JNIEnv* env = Thread::Current()->GetJniEnv();
  env->DeleteGlobalRef(clazz_);
}

void PrimitiveTypeWrapper::LoadWrappers() {
  JNIEnv* env = Thread::Current()->GetJniEnv();
  // Load Boolean wrapper.
  char sig[kBlahSize];
  jclass clazz = env->FindClass(kNormBooleanObject);
  CHK_EXCP(env);

  snprintf(sig, kBlahSize, "(%c)%c", kSigBoolean, kSigVoid);
  jmethodID meth_ctor = env->GetMethodID(clazz, kFuncConstructor, sig);
  CHK_EXCP(env);

  snprintf(sig, kBlahSize, "()%c", kSigBoolean);
  jmethodID meth_access = env->GetMethodID(clazz, kFuncBooleanValue, sig);
  CHK_EXCP(env);

  jobject g_ref = env->NewGlobalRef(reinterpret_cast<jobject>(clazz));
  if (!g_ref) {
	LOG(FATAL) << "Allocate a global reference for Boolean class.";
  }
  jclass g_clazz = reinterpret_cast<jclass>(g_ref);
  PrimitiveTypeWrapper* wrapper = new(std::nothrow) PrimitiveTypeWrapper(
	  g_clazz, meth_ctor, meth_access);
  if (!wrapper) {
	LOG(FATAL) << "Allocate PrimitiveTypeWrapper for Boolean.";
  }
  g_primitive_map->insert(std::make_pair(kTypeBoolean,
		std::unique_ptr<PrimitiveTypeWrapper>(wrapper)));

  // Load Byte Wrapper.
  clazz = env->FindClass(kNormByteObject);
  CHK_EXCP(env);

  snprintf(sig, kBlahSize, "(%c)%c", kSigByte, kSigVoid);
  meth_ctor = env->GetMethodID(clazz, kFuncConstructor, sig);
  CHK_EXCP(env);

  snprintf(sig, kBlahSize, "()%c", kSigByte);
  meth_access = env->GetMethodID(clazz, kFuncByteValue, sig);
  CHK_EXCP(env);

  g_ref = env->NewGlobalRef(reinterpret_cast<jobject>(clazz));
  if (!g_ref) {
	LOG(FATAL) << "Allocate a global reference for Byte class.";
  }
  g_clazz = reinterpret_cast<jclass>(g_ref);
  wrapper = new(std::nothrow) PrimitiveTypeWrapper(g_clazz, meth_ctor, meth_access);
  if (!wrapper) {
	LOG(FATAL) << "Allocate PrimitiveTypeWrapper for Byte.";
  }
  g_primitive_map->insert(std::make_pair(kTypeByte,
		std::unique_ptr<PrimitiveTypeWrapper>(wrapper)));

  // Load Character Wrapper.
  clazz = env->FindClass(kNormCharObject);
  CHK_EXCP(env);

  snprintf(sig, kBlahSize, "(%c)%c", kSigChar, kSigVoid);
  meth_ctor = env->GetMethodID(clazz, kFuncConstructor, sig);
  CHK_EXCP(env);

  snprintf(sig, kBlahSize, "()%c", kSigChar);
  meth_access = env->GetMethodID(clazz, kFuncCharValue, sig);
  CHK_EXCP(env);

  g_ref = env->NewGlobalRef(reinterpret_cast<jobject>(clazz));
  if (!g_ref) {
	LOG(FATAL) << "Allocate a global reference for Character class.";
  }
  g_clazz = reinterpret_cast<jclass>(g_ref);
  wrapper = new(std::nothrow) PrimitiveTypeWrapper(g_clazz, meth_ctor, meth_access);
  if (!wrapper) {
	LOG(FATAL) << "Allocate PrimitiveTypeWrapper for Character.";
  }
  g_primitive_map->insert(std::make_pair(kTypeChar,
		std::unique_ptr<PrimitiveTypeWrapper>(wrapper)));

  // Load Short Wrapper.
  clazz = env->FindClass(kNormShortObject);
  CHK_EXCP(env);

  snprintf(sig, kBlahSize, "(%c)%c", kSigShort, kSigVoid);
  meth_ctor = env->GetMethodID(clazz, kFuncConstructor, sig);
  CHK_EXCP(env);

  snprintf(sig, kBlahSize, "()%c", kSigShort);
  meth_access = env->GetMethodID(clazz, kFuncShortValue, sig);
  CHK_EXCP(env);

  g_ref = env->NewGlobalRef(reinterpret_cast<jobject>(clazz));
  if (!g_ref) {
	LOG(FATAL) << "Allocate a global reference for Short class.";
  }
  g_clazz = reinterpret_cast<jclass>(g_ref);
  wrapper = new(std::nothrow) PrimitiveTypeWrapper(g_clazz, meth_ctor, meth_access);
  if (!wrapper) {
	LOG(FATAL) << "Allocate PrimitiveTypeWrapper for Short.";
  }
  g_primitive_map->insert(std::make_pair(kTypeShort,
		std::unique_ptr<PrimitiveTypeWrapper>(wrapper)));

  // Load Integer Wrapper.
  clazz = env->FindClass(kNormIntObject);
  CHK_EXCP(env);

  snprintf(sig, kBlahSize, "(%c)%c", kSigInt, kSigVoid);
  meth_ctor = env->GetMethodID(clazz, kFuncConstructor, sig);
  CHK_EXCP(env);

  snprintf(sig, kBlahSize, "()%c", kSigInt);
  meth_access = env->GetMethodID(clazz, kFuncIntValue, sig);
  CHK_EXCP(env);

  g_ref = env->NewGlobalRef(reinterpret_cast<jobject>(clazz));
  if (!g_ref) {
	LOG(FATAL) << "Allocate a global reference for Integer class.";
  }
  g_clazz = reinterpret_cast<jclass>(g_ref);
  wrapper = new(std::nothrow) PrimitiveTypeWrapper(g_clazz, meth_ctor, meth_access);
  if (!wrapper) {
	LOG(FATAL) << "Allocate PrimitiveTypeWrapper for Integer.";
  }
  g_primitive_map->insert(std::make_pair(kTypeInt,
		std::unique_ptr<PrimitiveTypeWrapper>(wrapper)));

  // Load Float Wrapper.
  clazz = env->FindClass(kNormFloatObject);
  CHK_EXCP(env);

  snprintf(sig, kBlahSize, "(%c)%c", kSigFloat, kSigVoid);
  meth_ctor = env->GetMethodID(clazz, kFuncConstructor, sig);
  CHK_EXCP(env);

  snprintf(sig, kBlahSize, "()%c", kSigFloat);
  meth_access = env->GetMethodID(clazz, kFuncFloatValue, sig);
  CHK_EXCP(env);

  g_ref = env->NewGlobalRef(reinterpret_cast<jobject>(clazz));
  if (!g_ref) {
	LOG(FATAL) << "Allocate a global reference for Float class.";
  }
  g_clazz = reinterpret_cast<jclass>(g_ref);
  wrapper = new(std::nothrow) PrimitiveTypeWrapper(g_clazz, meth_ctor, meth_access);
  if (!wrapper) {
	LOG(FATAL) << "Allocate PrimitiveTypeWrapper for Float.";
  }
  g_primitive_map->insert(std::make_pair(kTypeFloat,
		std::unique_ptr<PrimitiveTypeWrapper>(wrapper)));

  // Load Long Wrapper.
  clazz = env->FindClass(kNormLongObject);
  CHK_EXCP(env);

  snprintf(sig, kBlahSize, "(%c)%c", kSigLong, kSigVoid);
  meth_ctor = env->GetMethodID(clazz, kFuncConstructor, sig);
  CHK_EXCP(env);

  snprintf(sig, kBlahSize, "()%c", kSigLong);
  meth_access = env->GetMethodID(clazz, kFuncLongValue, sig);
  CHK_EXCP(env);

  g_ref = env->NewGlobalRef(reinterpret_cast<jobject>(clazz));
  if (!g_ref) {
	LOG(FATAL) << "Allocate a global reference for Long class.";
  }
  g_clazz = reinterpret_cast<jclass>(g_ref);
  wrapper = new(std::nothrow) PrimitiveTypeWrapper(g_clazz, meth_ctor, meth_access);
  if (!wrapper) {
	LOG(FATAL) << "Allocate PrimitiveTypeWrapper for Long.";
  }
  g_primitive_map->insert(std::make_pair(kTypeLong,
		std::unique_ptr<PrimitiveTypeWrapper>(wrapper)));

  // Load Double Wrapper.
  clazz = env->FindClass(kNormDoubleObject);
  CHK_EXCP(env);

  snprintf(sig, kBlahSize, "(%c)%c", kSigDouble, kSigVoid);
  meth_ctor = env->GetMethodID(clazz, kFuncConstructor, sig);
  CHK_EXCP(env);

  snprintf(sig, kBlahSize, "()%c", kSigDouble);
  meth_access = env->GetMethodID(clazz, kFuncDoubleValue, sig);
  CHK_EXCP(env);

  g_ref = env->NewGlobalRef(reinterpret_cast<jobject>(clazz));
  if (!g_ref) {
	LOG(FATAL) << "Allocate a global reference for Double class.";
  }
  g_clazz = reinterpret_cast<jclass>(g_ref);
  wrapper = new(std::nothrow) PrimitiveTypeWrapper(g_clazz, meth_ctor, meth_access);
  if (!wrapper) {
	LOG(FATAL) << "Allocate PrimitiveTypeWrapper for Double.";
  }
  g_primitive_map->insert(std::make_pair(kTypeDouble,
		std::unique_ptr<PrimitiveTypeWrapper>(wrapper)));
}

}
}
#endif
