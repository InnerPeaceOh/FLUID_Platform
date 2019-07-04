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
#ifndef _GADGET_H_
#define _GADGET_H_
#include "jni.h"
#include "jni_internal.h"
#include "thread-current-inl.h"
#include "signature.h"
#include "java_type.h"
#include "thread.h"
#include "art_method-inl.h"
#include "jni_env_ext-inl.h"
#include "dex_file_annotations.h"
#include "base/logging.h"
#include "base/mutex.h"
#include "mirror/class-inl.h"
#include "native/scoped_fast_native_object_access-inl.h"
#include <vector>
#include <unordered_map>
#include <mutex>
#include <dlfcn.h>
#include <fstream>

#define CHK_EXCP(env, ...)                                                     \
        do {                                                                   \
            jthrowable except;                                                 \
            if ((except = env->ExceptionOccurred())) {                         \
                env->ExceptionClear();                                         \
                LOG(FATAL) << "CHK_EXCP()";                                    \
            }                                                                  \
        } while (0);

namespace art {
namespace fluid {

extern JavaVM* g_jvm;
extern jclass g_object_clazz;
extern jclass g_view_clazz;
extern jfieldID g_remotable_fid;
extern jfieldID g_inmaster_fid;
extern jobject g_fluid_manager;
extern jmethodID g_rpc_translation;
extern jmethodID g_rpc_caching;
extern jmethodID g_ret_caching;
extern jmethodID g_cache_void;
extern jmethodID g_cache_bool;
extern jmethodID g_cache_char;
extern jmethodID g_cache_byte;
extern jmethodID g_cache_short;
extern jmethodID g_cache_int;
extern jmethodID g_cache_long;
extern jmethodID g_cache_float;
extern jmethodID g_cache_double;
extern jmethodID g_cache_object;
extern std::ofstream g_debug_ofs;

enum {
    PROC_SUCC = 0,
    PROC_FAIL = 1,
	MIGRATED = 0x00010000,
	DUMMY_OBJECT = 0x01000000,
	DUMMY_IN_REMOTE = 0x10000000,
	IN_REMOTE = 0x00001111,
};

class MethodBundleNative
{
  public:
    MethodBundleNative(const char* name_method, const char* sig_method,
	  const std::vector<char>& type_inputs, char type_output,
      jclass clazz, uint64_t origin_entry, uint64_t gadget_entry)
      : type_output_(type_output), 
		input_width_(0),
        clazz_(clazz),
		origin_entry_(origin_entry),
		gadget_entry_(gadget_entry),
        name_method_(name_method),
        sig_method_(sig_method),
		type_inputs_(type_inputs)
    {
        for (char type : type_inputs_) {
            if ((type == kTypeLong) || (type == kTypeFloat) || (type == kTypeDouble))
                input_width_ += kWidthQword;
            else
                input_width_ += kWidthDword;
        }
    }

	~MethodBundleNative() {
		JNIEnv* env;
		g_jvm->AttachCurrentThread(&env, nullptr);
		env->DeleteGlobalRef(clazz_);
	}


    int32_t GetInputWidth()
    {
        return input_width_;
    }

    const std::vector<char>& GetInputType()
    {
        return type_inputs_;
    }

    char GetOutputType()
    {
        return type_output_;
    }

    uint64_t GetOriginalEntry()
    {
        return origin_entry_;
    }

    uint64_t GetGadgetEntry()
    {
        return gadget_entry_;
    }

    jclass GetClass()
    {
        return clazz_;
    }

    const char* GetMethodName()
    {
        return name_method_;
    }

    const char* GetMethodSig()
    {
        return sig_method_;
    }

  private:
    char type_output_;
    int32_t input_width_;
    jclass clazz_;
    uint64_t origin_entry_;
    uint64_t gadget_entry_;
    const char* name_method_;
    const char* sig_method_;
    std::vector<char> type_inputs_;
};

class ReturnMetadata
{
  public:
    ReturnMetadata(int object_id, ArtMethod* method) {
		object_id_ = object_id;
		method_ = method;
	}

    int object_id_;
	ArtMethod* method_;
};

using BundleMap = std::unordered_map<jmethodID, MethodBundleNative*>;
extern BundleMap* g_bundle_map;
using PrimitiveMap = std::unordered_map<char, std::unique_ptr<PrimitiveTypeWrapper>>;
extern PrimitiveMap* g_primitive_map;
extern bool BoxInput(jobjectArray input_box, void** scan, const std::vector<char>& input_type)
														REQUIRES_SHARED(Locks::mutator_lock_);
extern bool EncapsulateObject(char type, void* reg, jobject* p_obj);

extern "C" int NeedRpcTranslation(void* art_method, void* receiver, void** stack) 
														REQUIRES_SHARED(Locks::mutator_lock_);
extern "C" void RpcTranslationNative(void** gp_reg, void** fp_reg, void** stack)
														REQUIRES_SHARED(Locks::mutator_lock_);
extern "C" void* ReturnCachingNative(void* ret, void** stack)
														REQUIRES_SHARED(Locks::mutator_lock_);
extern "C" void RpcCachingNative(void** gp_reg, void** fp_reg, void** stack)
														REQUIRES_SHARED(Locks::mutator_lock_);
extern "C" void* GetReturnAddr();
extern "C" void PrepareReturnCaching(void** gp_reg) REQUIRES_SHARED(Locks::mutator_lock_);

#if defined(__aarch64__)
class InputMarshaller
{
  public:
    InputMarshaller(void** gp_reg, void** fp_reg, void** stack) {
		x[0] = gp_reg[0];
		x[1] = gp_reg[1];
		x[2] = gp_reg[2];
		x[3] = gp_reg[3];
		x[4] = gp_reg[4];
		x[5] = gp_reg[5];
		x[6] = gp_reg[6];
		x[7] = gp_reg[7];
		d[0] = fp_reg[0];
		d[1] = fp_reg[1];
		d[2] = fp_reg[2];
		d[3] = fp_reg[3];
		d[4] = fp_reg[4];
		d[5] = fp_reg[5];
		d[6] = fp_reg[6];
		d[7] = fp_reg[7];
		stack_ = stack;
	}

    void Extract(const std::vector<char>& input_type, void**);

    void* GetReceiver()
    {
        return x[1];
    }

    jmethodID GetMethodID()
    {
        return reinterpret_cast<jmethodID>(x[0]);
    }

  private:
	// general-purpose registers
	void* x[8];
	// floating-point registers
	void* d[8];
	// stack address for additional arguments
    void** stack_;
};

extern "C" void* RpcGadgetTrampoline() __asm__("RpcGadgetTrampoline");

extern "C" void* ExecuteCacheNative(void** gp_reg, void** stack)
														REQUIRES_SHARED(Locks::mutator_lock_);
#elif defined(__arm__)
class InputMarshaller
{
  public:
    InputMarshaller(void** gp_reg, void** fp_reg, void** stack) {
		r[0] = gp_reg[0];
		r[1] = gp_reg[1];
		r[2] = gp_reg[2];
		r[3] = gp_reg[3];
		s[0] = fp_reg[0];
		s[1] = fp_reg[1];
		s[2] = fp_reg[2];
		s[3] = fp_reg[3];
		s[4] = fp_reg[4];
		s[5] = fp_reg[5];
		s[6] = fp_reg[6];
		s[7] = fp_reg[7];
		s[8] = fp_reg[8];
		s[9] = fp_reg[9];
		s[10] = fp_reg[10];
		s[11] = fp_reg[11];
		s[12] = fp_reg[12];
		s[13] = fp_reg[13];
		s[14] = fp_reg[14];
		s[15] = fp_reg[15];
		stack_ = stack;
	}

    void Extract(const std::vector<char>& input_type, void**);

    void* GetReceiver()
    {
        return r[1];
    }

    jmethodID GetMethodID()
    {
        return reinterpret_cast<jmethodID>(r[0]);
    }

  private:
	// general-purpose registers
	void* r[4];
	// floating-point registers
	void* s[16];
	// stack address for additional arguments
    void** stack_;
};

extern "C" void* RpcGadgetTrampoline() __asm__("RpcGadgetTrampoline");

extern "C" void ExecuteCacheNative(void** gp_reg, void** stack, void** ret_value)
														REQUIRES_SHARED(Locks::mutator_lock_);
#endif

}
}
#endif
#endif
