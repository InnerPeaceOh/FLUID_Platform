/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "java_lang_Class.h"

#include <iostream>

#include "art_field-inl.h"
#include "art_method-inl.h"
#include "base/enums.h"
#include "class_linker-inl.h"
#include "common_throws.h"
#include "dex_file-inl.h"
#include "dex_file_annotations.h"
#include "jni_internal.h"
#include "mirror/class-inl.h"
#include "mirror/class_loader.h"
#include "mirror/field-inl.h"
#include "mirror/method.h"
#include "mirror/object-inl.h"
#include "mirror/object_array-inl.h"
#include "mirror/string-inl.h"
#include "native_util.h"
#include "nativehelper/jni_macros.h"
#include "nativehelper/ScopedLocalRef.h"
#include "nativehelper/ScopedUtfChars.h"
#include "nth_caller_visitor.h"
#include "obj_ptr-inl.h"
#include "reflection.h"
#include "scoped_fast_native_object_access-inl.h"
#include "scoped_thread_state_change-inl.h"
#include "utf.h"
#include "well_known_classes.h"

#if defined(__aarch64__) || defined(__arm__)
#include "jni.h"
#include "fluid/gadget.h"
#include "fluid/signature.h"
#include "fluid/java_type.h"
#include <unordered_map>
#include <fstream>
#include <sys/stat.h>
#endif

namespace art {

ALWAYS_INLINE static inline ObjPtr<mirror::Class> DecodeClass(
    const ScopedFastNativeObjectAccess& soa, jobject java_class)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  ObjPtr<mirror::Class> c = soa.Decode<mirror::Class>(java_class);
  DCHECK(c != nullptr);
  DCHECK(c->IsClass());
  // TODO: we could EnsureInitialized here, rather than on every reflective get/set or invoke .
  // For now, we conservatively preserve the old dalvik behavior. A quick "IsInitialized" check
  // every time probably doesn't make much difference to reflection performance anyway.
  return c;
}

// "name" is in "binary name" format, e.g. "dalvik.system.Debug$1".
static jclass Class_classForName(JNIEnv* env, jclass, jstring javaName, jboolean initialize,
                                 jobject javaLoader) {
  ScopedFastNativeObjectAccess soa(env);
  ScopedUtfChars name(env, javaName);
  if (name.c_str() == nullptr) {
    return nullptr;
  }

  // We need to validate and convert the name (from x.y.z to x/y/z).  This
  // is especially handy for array types, since we want to avoid
  // auto-generating bogus array classes.
  if (!IsValidBinaryClassName(name.c_str())) {
    soa.Self()->ThrowNewExceptionF("Ljava/lang/ClassNotFoundException;",
                                   "Invalid name: %s", name.c_str());
    return nullptr;
  }

  std::string descriptor(DotToDescriptor(name.c_str()));
  StackHandleScope<2> hs(soa.Self());
  Handle<mirror::ClassLoader> class_loader(
      hs.NewHandle(soa.Decode<mirror::ClassLoader>(javaLoader)));
  ClassLinker* class_linker = Runtime::Current()->GetClassLinker();
  Handle<mirror::Class> c(
      hs.NewHandle(class_linker->FindClass(soa.Self(), descriptor.c_str(), class_loader)));
  if (c == nullptr) {
    ScopedLocalRef<jthrowable> cause(env, env->ExceptionOccurred());
    env->ExceptionClear();
    jthrowable cnfe = reinterpret_cast<jthrowable>(
        env->NewObject(WellKnownClasses::java_lang_ClassNotFoundException,
                       WellKnownClasses::java_lang_ClassNotFoundException_init,
                       javaName,
                       cause.get()));
    if (cnfe != nullptr) {
      // Make sure allocation didn't fail with an OOME.
      env->Throw(cnfe);
    }
    return nullptr;
  }
  if (initialize) {
    class_linker->EnsureInitialized(soa.Self(), c, true, true);
  }
  return soa.AddLocalReference<jclass>(c.Get());
}

static jstring Class_getNameNative(JNIEnv* env, jobject javaThis) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<1> hs(soa.Self());
  ObjPtr<mirror::Class> c = DecodeClass(soa, javaThis);
  return soa.AddLocalReference<jstring>(mirror::Class::ComputeName(hs.NewHandle(c)));
}

// TODO: Move this to mirror::Class ? Other mirror types that commonly appear
// as arrays have a GetArrayClass() method.
static ObjPtr<mirror::Class> GetClassArrayClass(Thread* self)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  ObjPtr<mirror::Class> class_class = mirror::Class::GetJavaLangClass();
  return Runtime::Current()->GetClassLinker()->FindArrayClass(self, &class_class);
}

static jobjectArray Class_getInterfacesInternal(JNIEnv* env, jobject javaThis) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<4> hs(soa.Self());
  Handle<mirror::Class> klass = hs.NewHandle(DecodeClass(soa, javaThis));

  if (klass->IsProxyClass()) {
    return soa.AddLocalReference<jobjectArray>(klass->GetProxyInterfaces()->Clone(soa.Self()));
  }

  const DexFile::TypeList* iface_list = klass->GetInterfaceTypeList();
  if (iface_list == nullptr) {
    return nullptr;
  }

  const uint32_t num_ifaces = iface_list->Size();
  Handle<mirror::Class> class_array_class = hs.NewHandle(GetClassArrayClass(soa.Self()));
  Handle<mirror::ObjectArray<mirror::Class>> ifaces = hs.NewHandle(
      mirror::ObjectArray<mirror::Class>::Alloc(soa.Self(), class_array_class.Get(), num_ifaces));
  if (ifaces.IsNull()) {
    DCHECK(soa.Self()->IsExceptionPending());
    return nullptr;
  }

  // Check that we aren't in an active transaction, we call SetWithoutChecks
  // with kActiveTransaction == false.
  DCHECK(!Runtime::Current()->IsActiveTransaction());

  MutableHandle<mirror::Class> interface(hs.NewHandle<mirror::Class>(nullptr));
  for (uint32_t i = 0; i < num_ifaces; ++i) {
    const dex::TypeIndex type_idx = iface_list->GetTypeItem(i).type_idx_;
    interface.Assign(ClassLinker::LookupResolvedType(
        type_idx, klass->GetDexCache(), klass->GetClassLoader()));
    ifaces->SetWithoutChecks<false>(i, interface.Get());
  }

  return soa.AddLocalReference<jobjectArray>(ifaces.Get());
}

static mirror::ObjectArray<mirror::Field>* GetDeclaredFields(
    Thread* self, ObjPtr<mirror::Class> klass, bool public_only, bool force_resolve)
      REQUIRES_SHARED(Locks::mutator_lock_) {
  StackHandleScope<1> hs(self);
  IterationRange<StrideIterator<ArtField>> ifields = klass->GetIFields();
  IterationRange<StrideIterator<ArtField>> sfields = klass->GetSFields();
  size_t array_size = klass->NumInstanceFields() + klass->NumStaticFields();
  if (public_only) {
    // Lets go subtract all the non public fields.
    for (ArtField& field : ifields) {
      if (!field.IsPublic()) {
        --array_size;
      }
    }
    for (ArtField& field : sfields) {
      if (!field.IsPublic()) {
        --array_size;
      }
    }
  }
  size_t array_idx = 0;
  auto object_array = hs.NewHandle(mirror::ObjectArray<mirror::Field>::Alloc(
      self, mirror::Field::ArrayClass(), array_size));
  if (object_array == nullptr) {
    return nullptr;
  }
  for (ArtField& field : ifields) {
    if (!public_only || field.IsPublic()) {
      auto* reflect_field = mirror::Field::CreateFromArtField<kRuntimePointerSize>(self,
                                                                                   &field,
                                                                                   force_resolve);
      if (reflect_field == nullptr) {
        if (kIsDebugBuild) {
          self->AssertPendingException();
        }
        // Maybe null due to OOME or type resolving exception.
        return nullptr;
      }
      object_array->SetWithoutChecks<false>(array_idx++, reflect_field);
    }
  }
  for (ArtField& field : sfields) {
    if (!public_only || field.IsPublic()) {
      auto* reflect_field = mirror::Field::CreateFromArtField<kRuntimePointerSize>(self,
                                                                                   &field,
                                                                                   force_resolve);
      if (reflect_field == nullptr) {
        if (kIsDebugBuild) {
          self->AssertPendingException();
        }
        return nullptr;
      }
      object_array->SetWithoutChecks<false>(array_idx++, reflect_field);
    }
  }
  DCHECK_EQ(array_idx, array_size);
  return object_array.Get();
}

static jobjectArray Class_getDeclaredFieldsUnchecked(JNIEnv* env, jobject javaThis,
                                                     jboolean publicOnly) {
  ScopedFastNativeObjectAccess soa(env);
  return soa.AddLocalReference<jobjectArray>(
      GetDeclaredFields(soa.Self(), DecodeClass(soa, javaThis), publicOnly != JNI_FALSE, false));
}

static jobjectArray Class_getDeclaredFields(JNIEnv* env, jobject javaThis) {
  ScopedFastNativeObjectAccess soa(env);
  return soa.AddLocalReference<jobjectArray>(
      GetDeclaredFields(soa.Self(), DecodeClass(soa, javaThis), false, true));
}

static jobjectArray Class_getPublicDeclaredFields(JNIEnv* env, jobject javaThis) {
  ScopedFastNativeObjectAccess soa(env);
  return soa.AddLocalReference<jobjectArray>(
      GetDeclaredFields(soa.Self(), DecodeClass(soa, javaThis), true, true));
}

// Performs a binary search through an array of fields, TODO: Is this fast enough if we don't use
// the dex cache for lookups? I think CompareModifiedUtf8ToUtf16AsCodePointValues should be fairly
// fast.
ALWAYS_INLINE static inline ArtField* FindFieldByName(ObjPtr<mirror::String> name,
                                                      LengthPrefixedArray<ArtField>* fields)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  if (fields == nullptr) {
    return nullptr;
  }
  size_t low = 0;
  size_t high = fields->size();
  const bool is_name_compressed = name->IsCompressed();
  const uint16_t* const data = (is_name_compressed) ? nullptr : name->GetValue();
  const uint8_t* const data_compressed = (is_name_compressed) ? name->GetValueCompressed()
                                                              : nullptr;
  const size_t length = name->GetLength();
  while (low < high) {
    auto mid = (low + high) / 2;
    ArtField& field = fields->At(mid);
    int result = 0;
    if (is_name_compressed) {
      size_t field_length = strlen(field.GetName());
      size_t min_size = (length < field_length) ? length : field_length;
      result = memcmp(field.GetName(), data_compressed, min_size);
      if (result == 0) {
        result = field_length - length;
      }
    } else {
      result = CompareModifiedUtf8ToUtf16AsCodePointValues(field.GetName(), data, length);
    }
    // Alternate approach, only a few % faster at the cost of more allocations.
    // int result = field->GetStringName(self, true)->CompareTo(name);
    if (result < 0) {
      low = mid + 1;
    } else if (result > 0) {
      high = mid;
    } else {
      return &field;
    }
  }
  if (kIsDebugBuild) {
    for (ArtField& field : MakeIterationRangeFromLengthPrefixedArray(fields)) {
      CHECK_NE(field.GetName(), name->ToModifiedUtf8());
    }
  }
  return nullptr;
}

ALWAYS_INLINE static inline mirror::Field* GetDeclaredField(Thread* self,
                                                            ObjPtr<mirror::Class> c,
                                                            ObjPtr<mirror::String> name)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  ArtField* art_field = FindFieldByName(name, c->GetIFieldsPtr());
  if (art_field != nullptr) {
    return mirror::Field::CreateFromArtField<kRuntimePointerSize>(self, art_field, true);
  }
  art_field = FindFieldByName(name, c->GetSFieldsPtr());
  if (art_field != nullptr) {
    return mirror::Field::CreateFromArtField<kRuntimePointerSize>(self, art_field, true);
  }
  return nullptr;
}

static mirror::Field* GetPublicFieldRecursive(
    Thread* self, ObjPtr<mirror::Class> clazz, ObjPtr<mirror::String> name)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  DCHECK(clazz != nullptr);
  DCHECK(name != nullptr);
  DCHECK(self != nullptr);

  StackHandleScope<2> hs(self);
  MutableHandle<mirror::Class> h_clazz(hs.NewHandle(clazz));
  Handle<mirror::String> h_name(hs.NewHandle(name));

  // We search the current class, its direct interfaces then its superclass.
  while (h_clazz != nullptr) {
    mirror::Field* result = GetDeclaredField(self, h_clazz.Get(), h_name.Get());
    if ((result != nullptr) && (result->GetAccessFlags() & kAccPublic)) {
      return result;
    } else if (UNLIKELY(self->IsExceptionPending())) {
      // Something went wrong. Bail out.
      return nullptr;
    }

    uint32_t num_direct_interfaces = h_clazz->NumDirectInterfaces();
    for (uint32_t i = 0; i < num_direct_interfaces; i++) {
      ObjPtr<mirror::Class> iface = mirror::Class::ResolveDirectInterface(self, h_clazz, i);
      if (UNLIKELY(iface == nullptr)) {
        self->AssertPendingException();
        return nullptr;
      }
      result = GetPublicFieldRecursive(self, iface, h_name.Get());
      if (result != nullptr) {
        DCHECK(result->GetAccessFlags() & kAccPublic);
        return result;
      } else if (UNLIKELY(self->IsExceptionPending())) {
        // Something went wrong. Bail out.
        return nullptr;
      }
    }

    // We don't try the superclass if we are an interface.
    if (h_clazz->IsInterface()) {
      break;
    }

    // Get the next class.
    h_clazz.Assign(h_clazz->GetSuperClass());
  }
  return nullptr;
}

static jobject Class_getPublicFieldRecursive(JNIEnv* env, jobject javaThis, jstring name) {
  ScopedFastNativeObjectAccess soa(env);
  auto name_string = soa.Decode<mirror::String>(name);
  if (UNLIKELY(name_string == nullptr)) {
    ThrowNullPointerException("name == null");
    return nullptr;
  }
  return soa.AddLocalReference<jobject>(
      GetPublicFieldRecursive(soa.Self(), DecodeClass(soa, javaThis), name_string));
}

static jobject Class_getDeclaredField(JNIEnv* env, jobject javaThis, jstring name) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<3> hs(soa.Self());
  Handle<mirror::String> h_string = hs.NewHandle(soa.Decode<mirror::String>(name));
  if (h_string == nullptr) {
    ThrowNullPointerException("name == null");
    return nullptr;
  }
  Handle<mirror::Class> h_klass = hs.NewHandle(DecodeClass(soa, javaThis));
  Handle<mirror::Field> result =
      hs.NewHandle(GetDeclaredField(soa.Self(), h_klass.Get(), h_string.Get()));
  if (result == nullptr) {
    std::string name_str = h_string->ToModifiedUtf8();
    if (name_str == "value" && h_klass->IsStringClass()) {
      // We log the error for this specific case, as the user might just swallow the exception.
      // This helps diagnose crashes when applications rely on the String#value field being
      // there.
      // Also print on the error stream to test it through run-test.
      std::string message("The String#value field is not present on Android versions >= 6.0");
      LOG(ERROR) << message;
      std::cerr << message << std::endl;
    }
    // We may have a pending exception if we failed to resolve.
    if (!soa.Self()->IsExceptionPending()) {
      ThrowNoSuchFieldException(h_klass.Get(), name_str.c_str());
    }
    return nullptr;
  }
  return soa.AddLocalReference<jobject>(result.Get());
}

static jobject Class_getDeclaredConstructorInternal(
    JNIEnv* env, jobject javaThis, jobjectArray args) {
  ScopedFastNativeObjectAccess soa(env);
  DCHECK_EQ(Runtime::Current()->GetClassLinker()->GetImagePointerSize(), kRuntimePointerSize);
  DCHECK(!Runtime::Current()->IsActiveTransaction());
  ObjPtr<mirror::Constructor> result =
      mirror::Class::GetDeclaredConstructorInternal<kRuntimePointerSize, false>(
      soa.Self(),
      DecodeClass(soa, javaThis),
      soa.Decode<mirror::ObjectArray<mirror::Class>>(args));
  return soa.AddLocalReference<jobject>(result);
}

static ALWAYS_INLINE inline bool MethodMatchesConstructor(ArtMethod* m, bool public_only)
    REQUIRES_SHARED(Locks::mutator_lock_) {
  DCHECK(m != nullptr);
  return (!public_only || m->IsPublic()) && !m->IsStatic() && m->IsConstructor();
}

static jobjectArray Class_getDeclaredConstructorsInternal(
    JNIEnv* env, jobject javaThis, jboolean publicOnly) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<2> hs(soa.Self());
  Handle<mirror::Class> h_klass = hs.NewHandle(DecodeClass(soa, javaThis));
  size_t constructor_count = 0;
  // Two pass approach for speed.
  for (auto& m : h_klass->GetDirectMethods(kRuntimePointerSize)) {
    constructor_count += MethodMatchesConstructor(&m, publicOnly != JNI_FALSE) ? 1u : 0u;
  }
  auto h_constructors = hs.NewHandle(mirror::ObjectArray<mirror::Constructor>::Alloc(
      soa.Self(), mirror::Constructor::ArrayClass(), constructor_count));
  if (UNLIKELY(h_constructors == nullptr)) {
    soa.Self()->AssertPendingException();
    return nullptr;
  }
  constructor_count = 0;
  for (auto& m : h_klass->GetDirectMethods(kRuntimePointerSize)) {
    if (MethodMatchesConstructor(&m, publicOnly != JNI_FALSE)) {
      DCHECK_EQ(Runtime::Current()->GetClassLinker()->GetImagePointerSize(), kRuntimePointerSize);
      DCHECK(!Runtime::Current()->IsActiveTransaction());
      auto* constructor = mirror::Constructor::CreateFromArtMethod<kRuntimePointerSize, false>(
          soa.Self(), &m);
      if (UNLIKELY(constructor == nullptr)) {
        soa.Self()->AssertPendingOOMException();
        return nullptr;
      }
      h_constructors->SetWithoutChecks<false>(constructor_count++, constructor);
    }
  }
  return soa.AddLocalReference<jobjectArray>(h_constructors.Get());
}

static jobject Class_getDeclaredMethodInternal(JNIEnv* env, jobject javaThis,
                                               jobject name, jobjectArray args) {
  ScopedFastNativeObjectAccess soa(env);
  DCHECK_EQ(Runtime::Current()->GetClassLinker()->GetImagePointerSize(), kRuntimePointerSize);
  DCHECK(!Runtime::Current()->IsActiveTransaction());
  ObjPtr<mirror::Method> result =
      mirror::Class::GetDeclaredMethodInternal<kRuntimePointerSize, false>(
          soa.Self(),
          DecodeClass(soa, javaThis),
          soa.Decode<mirror::String>(name),
          soa.Decode<mirror::ObjectArray<mirror::Class>>(args));
  return soa.AddLocalReference<jobject>(result);
}

static jobjectArray Class_getDeclaredMethodsUnchecked(JNIEnv* env, jobject javaThis,
                                                      jboolean publicOnly) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<2> hs(soa.Self());
  Handle<mirror::Class> klass = hs.NewHandle(DecodeClass(soa, javaThis));
  size_t num_methods = 0;
  for (auto& m : klass->GetDeclaredMethods(kRuntimePointerSize)) {
    auto modifiers = m.GetAccessFlags();
    // Add non-constructor declared methods.
    if ((publicOnly == JNI_FALSE || (modifiers & kAccPublic) != 0) &&
        (modifiers & kAccConstructor) == 0) {
      ++num_methods;
    }
  }
  auto ret = hs.NewHandle(mirror::ObjectArray<mirror::Method>::Alloc(
      soa.Self(), mirror::Method::ArrayClass(), num_methods));
  if (ret == nullptr) {
    soa.Self()->AssertPendingOOMException();
    return nullptr;
  }
  num_methods = 0;
  for (auto& m : klass->GetDeclaredMethods(kRuntimePointerSize)) {
    auto modifiers = m.GetAccessFlags();
    if ((publicOnly == JNI_FALSE || (modifiers & kAccPublic) != 0) &&
        (modifiers & kAccConstructor) == 0) {
      DCHECK_EQ(Runtime::Current()->GetClassLinker()->GetImagePointerSize(), kRuntimePointerSize);
      DCHECK(!Runtime::Current()->IsActiveTransaction());
      auto* method =
          mirror::Method::CreateFromArtMethod<kRuntimePointerSize, false>(soa.Self(), &m);
      if (method == nullptr) {
        soa.Self()->AssertPendingException();
        return nullptr;
      }
      ret->SetWithoutChecks<false>(num_methods++, method);
    }
  }
  return soa.AddLocalReference<jobjectArray>(ret.Get());
}

static jobject Class_getDeclaredAnnotation(JNIEnv* env, jobject javaThis, jclass annotationClass) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<2> hs(soa.Self());
  Handle<mirror::Class> klass(hs.NewHandle(DecodeClass(soa, javaThis)));

  // Handle public contract to throw NPE if the "annotationClass" argument was null.
  if (UNLIKELY(annotationClass == nullptr)) {
    ThrowNullPointerException("annotationClass");
    return nullptr;
  }

  if (klass->IsProxyClass() || klass->GetDexCache() == nullptr) {
    return nullptr;
  }
  Handle<mirror::Class> annotation_class(hs.NewHandle(soa.Decode<mirror::Class>(annotationClass)));
  return soa.AddLocalReference<jobject>(
      annotations::GetAnnotationForClass(klass, annotation_class));
}

static jobjectArray Class_getDeclaredAnnotations(JNIEnv* env, jobject javaThis) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<1> hs(soa.Self());
  Handle<mirror::Class> klass(hs.NewHandle(DecodeClass(soa, javaThis)));
  if (klass->IsProxyClass() || klass->GetDexCache() == nullptr) {
    // Return an empty array instead of a null pointer.
    ObjPtr<mirror::Class>  annotation_array_class =
        soa.Decode<mirror::Class>(WellKnownClasses::java_lang_annotation_Annotation__array);
    mirror::ObjectArray<mirror::Object>* empty_array =
        mirror::ObjectArray<mirror::Object>::Alloc(soa.Self(),
                                                   annotation_array_class.Ptr(),
                                                   0);
    return soa.AddLocalReference<jobjectArray>(empty_array);
  }
  return soa.AddLocalReference<jobjectArray>(annotations::GetAnnotationsForClass(klass));
}

static jobjectArray Class_getDeclaredClasses(JNIEnv* env, jobject javaThis) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<1> hs(soa.Self());
  Handle<mirror::Class> klass(hs.NewHandle(DecodeClass(soa, javaThis)));
  mirror::ObjectArray<mirror::Class>* classes = nullptr;
  if (!klass->IsProxyClass() && klass->GetDexCache() != nullptr) {
    classes = annotations::GetDeclaredClasses(klass);
  }
  if (classes == nullptr) {
    // Return an empty array instead of a null pointer.
    if (soa.Self()->IsExceptionPending()) {
      // Pending exception from GetDeclaredClasses.
      return nullptr;
    }
    ObjPtr<mirror::Class> class_array_class = GetClassArrayClass(soa.Self());
    if (class_array_class == nullptr) {
      return nullptr;
    }
    ObjPtr<mirror::ObjectArray<mirror::Class>> empty_array =
        mirror::ObjectArray<mirror::Class>::Alloc(soa.Self(), class_array_class, 0);
    return soa.AddLocalReference<jobjectArray>(empty_array);
  }
  return soa.AddLocalReference<jobjectArray>(classes);
}

static jclass Class_getEnclosingClass(JNIEnv* env, jobject javaThis) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<1> hs(soa.Self());
  Handle<mirror::Class> klass(hs.NewHandle(DecodeClass(soa, javaThis)));
  if (klass->IsProxyClass() || klass->GetDexCache() == nullptr) {
    return nullptr;
  }
  return soa.AddLocalReference<jclass>(annotations::GetEnclosingClass(klass));
}

static jobject Class_getEnclosingConstructorNative(JNIEnv* env, jobject javaThis) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<1> hs(soa.Self());
  Handle<mirror::Class> klass(hs.NewHandle(DecodeClass(soa, javaThis)));
  if (klass->IsProxyClass() || klass->GetDexCache() == nullptr) {
    return nullptr;
  }
  ObjPtr<mirror::Object> method = annotations::GetEnclosingMethod(klass);
  if (method != nullptr) {
    if (soa.Decode<mirror::Class>(WellKnownClasses::java_lang_reflect_Constructor) ==
        method->GetClass()) {
      return soa.AddLocalReference<jobject>(method);
    }
  }
  return nullptr;
}

static jobject Class_getEnclosingMethodNative(JNIEnv* env, jobject javaThis) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<1> hs(soa.Self());
  Handle<mirror::Class> klass(hs.NewHandle(DecodeClass(soa, javaThis)));
  if (klass->IsProxyClass() || klass->GetDexCache() == nullptr) {
    return nullptr;
  }
  ObjPtr<mirror::Object> method = annotations::GetEnclosingMethod(klass);
  if (method != nullptr) {
    if (soa.Decode<mirror::Class>(WellKnownClasses::java_lang_reflect_Method) ==
        method->GetClass()) {
      return soa.AddLocalReference<jobject>(method);
    }
  }
  return nullptr;
}

static jint Class_getInnerClassFlags(JNIEnv* env, jobject javaThis, jint defaultValue) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<1> hs(soa.Self());
  Handle<mirror::Class> klass(hs.NewHandle(DecodeClass(soa, javaThis)));
  return mirror::Class::GetInnerClassFlags(klass, defaultValue);
}

static jstring Class_getInnerClassName(JNIEnv* env, jobject javaThis) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<1> hs(soa.Self());
  Handle<mirror::Class> klass(hs.NewHandle(DecodeClass(soa, javaThis)));
  if (klass->IsProxyClass() || klass->GetDexCache() == nullptr) {
    return nullptr;
  }
  mirror::String* class_name = nullptr;
  if (!annotations::GetInnerClass(klass, &class_name)) {
    return nullptr;
  }
  return soa.AddLocalReference<jstring>(class_name);
}

static jobjectArray Class_getSignatureAnnotation(JNIEnv* env, jobject javaThis) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<1> hs(soa.Self());
  Handle<mirror::Class> klass(hs.NewHandle(DecodeClass(soa, javaThis)));
  if (klass->IsProxyClass() || klass->GetDexCache() == nullptr) {
    return nullptr;
  }
  return soa.AddLocalReference<jobjectArray>(
      annotations::GetSignatureAnnotationForClass(klass));
}

static jboolean Class_isAnonymousClass(JNIEnv* env, jobject javaThis) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<1> hs(soa.Self());
  Handle<mirror::Class> klass(hs.NewHandle(DecodeClass(soa, javaThis)));
  if (klass->IsProxyClass() || klass->GetDexCache() == nullptr) {
    return false;
  }
  mirror::String* class_name = nullptr;
  if (!annotations::GetInnerClass(klass, &class_name)) {
    return false;
  }
  return class_name == nullptr;
}

static jboolean Class_isDeclaredAnnotationPresent(JNIEnv* env, jobject javaThis,
                                                  jclass annotationType) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<2> hs(soa.Self());
  Handle<mirror::Class> klass(hs.NewHandle(DecodeClass(soa, javaThis)));
  if (klass->IsProxyClass() || klass->GetDexCache() == nullptr) {
    return false;
  }
  Handle<mirror::Class> annotation_class(hs.NewHandle(soa.Decode<mirror::Class>(annotationType)));
  return annotations::IsClassAnnotationPresent(klass, annotation_class);
}

static jclass Class_getDeclaringClass(JNIEnv* env, jobject javaThis) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<1> hs(soa.Self());
  Handle<mirror::Class> klass(hs.NewHandle(DecodeClass(soa, javaThis)));
  if (klass->IsProxyClass() || klass->GetDexCache() == nullptr) {
    return nullptr;
  }
  // Return null for anonymous classes.
  if (Class_isAnonymousClass(env, javaThis)) {
    return nullptr;
  }
  return soa.AddLocalReference<jclass>(annotations::GetDeclaringClass(klass));
}

static jobject Class_newInstance(JNIEnv* env, jobject javaThis) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<4> hs(soa.Self());
  Handle<mirror::Class> klass = hs.NewHandle(DecodeClass(soa, javaThis));
  if (UNLIKELY(klass->GetPrimitiveType() != 0 || klass->IsInterface() || klass->IsArrayClass() ||
               klass->IsAbstract())) {
    soa.Self()->ThrowNewExceptionF("Ljava/lang/InstantiationException;",
                                   "%s cannot be instantiated",
                                   klass->PrettyClass().c_str());
    return nullptr;
  }
  auto caller = hs.NewHandle<mirror::Class>(nullptr);
  // Verify that we can access the class.
  if (!klass->IsPublic()) {
    caller.Assign(GetCallingClass(soa.Self(), 1));
    if (caller != nullptr && !caller->CanAccess(klass.Get())) {
      soa.Self()->ThrowNewExceptionF(
          "Ljava/lang/IllegalAccessException;", "%s is not accessible from %s",
          klass->PrettyClass().c_str(), caller->PrettyClass().c_str());
      return nullptr;
    }
  }
  auto* constructor = klass->GetDeclaredConstructor(
      soa.Self(),
      ScopedNullHandle<mirror::ObjectArray<mirror::Class>>(),
      kRuntimePointerSize);
  if (UNLIKELY(constructor == nullptr)) {
    soa.Self()->ThrowNewExceptionF("Ljava/lang/InstantiationException;",
                                   "%s has no zero argument constructor",
                                   klass->PrettyClass().c_str());
    return nullptr;
  }
  // Invoke the string allocator to return an empty string for the string class.
  if (klass->IsStringClass()) {
    gc::AllocatorType allocator_type = Runtime::Current()->GetHeap()->GetCurrentAllocator();
    ObjPtr<mirror::Object> obj = mirror::String::AllocEmptyString<true>(soa.Self(), allocator_type);
    if (UNLIKELY(soa.Self()->IsExceptionPending())) {
      return nullptr;
    } else {
      return soa.AddLocalReference<jobject>(obj);
    }
  }
  auto receiver = hs.NewHandle(klass->AllocObject(soa.Self()));
  if (UNLIKELY(receiver == nullptr)) {
    soa.Self()->AssertPendingOOMException();
    return nullptr;
  }
  // Verify that we can access the constructor.
  auto* declaring_class = constructor->GetDeclaringClass();
  if (!constructor->IsPublic()) {
    if (caller == nullptr) {
      caller.Assign(GetCallingClass(soa.Self(), 1));
    }
    if (UNLIKELY(caller != nullptr && !VerifyAccess(receiver.Get(),
                                                          declaring_class,
                                                          constructor->GetAccessFlags(),
                                                          caller.Get()))) {
      soa.Self()->ThrowNewExceptionF(
          "Ljava/lang/IllegalAccessException;", "%s is not accessible from %s",
          constructor->PrettyMethod().c_str(), caller->PrettyClass().c_str());
      return nullptr;
    }
  }
  // Ensure that we are initialized.
  if (UNLIKELY(!declaring_class->IsInitialized())) {
    if (!Runtime::Current()->GetClassLinker()->EnsureInitialized(
        soa.Self(), hs.NewHandle(declaring_class), true, true)) {
      soa.Self()->AssertPendingException();
      return nullptr;
    }
  }
  // Invoke the constructor.
  JValue result;
  uint32_t args[1] = { static_cast<uint32_t>(reinterpret_cast<uintptr_t>(receiver.Get())) };
  constructor->Invoke(soa.Self(), args, sizeof(args), &result, "V");
  if (UNLIKELY(soa.Self()->IsExceptionPending())) {
    return nullptr;
  }
  // Constructors are ()V methods, so we shouldn't touch the result of InvokeMethod.
  return soa.AddLocalReference<jobject>(receiver.Get());
}

#if defined(__aarch64__) || defined(__arm__)
static void Class_initRpcGadget(JNIEnv* env, jclass) {
  jsize vm_count;
  JNI_GetCreatedJavaVMs(&fluid::g_jvm, 1, &vm_count);

  fluid::BundleMap* bundle_map = new(std::nothrow)fluid::BundleMap();
  if (!bundle_map)
	  LOG(FATAL) << "Class_initRpcGadget(), Allocate map for MethodBundleNative.";
  fluid::g_bundle_map = bundle_map;

  jclass object_clazz = env->FindClass("java/lang/Object");
  jobject object_clazz_ref = env->NewGlobalRef(reinterpret_cast<jobject>(object_clazz));
  fluid::g_object_clazz = reinterpret_cast<jclass>(object_clazz_ref);

  jclass view_clazz = env->FindClass("android/view/View");
  jobject view_clazz_ref = env->NewGlobalRef(reinterpret_cast<jobject>(view_clazz));
  fluid::g_view_clazz = reinterpret_cast<jclass>(view_clazz_ref);

  jclass fluid_manager_clazz = env->FindClass("android/fluid/FLUIDManager");
  fluid::g_rpc_translation = env->GetMethodID(fluid_manager_clazz, "rpcTranslation", 
	"(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)V"); 
  fluid::g_rpc_caching = env->GetMethodID(fluid_manager_clazz, "rpcCaching", 
	"(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)V"); 
  fluid::g_ret_caching = env->GetMethodID(fluid_manager_clazz, "returnCaching", 
	"(ILjava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V"); 
  fluid::g_cache_void = env->GetMethodID(fluid_manager_clazz, "executeCacheWithVoid", 
	"(ILjava/lang/String;Ljava/lang/String;)V"); 
  fluid::g_cache_bool = env->GetMethodID(fluid_manager_clazz, "executeCacheWithBool", 
	"(ILjava/lang/String;Ljava/lang/String;)Z"); 
  fluid::g_cache_char = env->GetMethodID(fluid_manager_clazz, "executeCacheWithChar", 
	"(ILjava/lang/String;Ljava/lang/String;)C"); 
  fluid::g_cache_byte = env->GetMethodID(fluid_manager_clazz, "executeCacheWithByte", 
	"(ILjava/lang/String;Ljava/lang/String;)B"); 
  fluid::g_cache_short = env->GetMethodID(fluid_manager_clazz, "executeCacheWithShort", 
	"(ILjava/lang/String;Ljava/lang/String;)S"); 
  fluid::g_cache_int = env->GetMethodID(fluid_manager_clazz, "executeCacheWithInt", 
	"(ILjava/lang/String;Ljava/lang/String;)I"); 
  fluid::g_cache_long = env->GetMethodID(fluid_manager_clazz, "executeCacheWithLong", 
	"(ILjava/lang/String;Ljava/lang/String;)J"); 
  fluid::g_cache_float = env->GetMethodID(fluid_manager_clazz, "executeCacheWithFloat", 
	"(ILjava/lang/String;Ljava/lang/String;)F"); 
  fluid::g_cache_double = env->GetMethodID(fluid_manager_clazz, "executeCacheWithDouble", 
	"(ILjava/lang/String;Ljava/lang/String;)D"); 
  fluid::g_cache_object = env->GetMethodID(fluid_manager_clazz, "executeCacheWithObject", 
	"(ILjava/lang/String;Ljava/lang/String;)Ljava/lang/Object;"); 

  fluid::g_primitive_map = new(std::nothrow)fluid::PrimitiveMap();
  fluid::PrimitiveTypeWrapper::LoadWrappers();

  fluid::g_debug_ofs.open("/data/local/tmp/rpc_installed_methods", std::ofstream::out);
  fluid::g_debug_ofs.close();
  chmod("/data/local/tmp/rpc_installed_methods", 0666);
}

static void Class_setFLUIDManagerObj(JNIEnv* env, jclass, jobject managerObj) {
  fluid::g_fluid_manager = (jobject) env->NewGlobalRef(managerObj);
}

static void Class_setRpcGadget(JNIEnv* env, jclass, jobject clazzObj, jstring methodName, jstring methodSig) {
  ScopedFastNativeObjectAccess soa(env);
  StackHandleScope<2> hs(soa.Self());
  Handle<mirror::Class> klass = hs.NewHandle(DecodeClass(soa, clazzObj));
  jboolean is_copy = JNI_FALSE;
  const char* method_name = env->GetStringUTFChars(methodName, &is_copy);
  const char* method_sig = env->GetStringUTFChars(methodSig, &is_copy);
  
  if (strcmp(method_name, "serializeUi") == 0 
		  || strcmp(method_name, "restoreUi") == 0
		  || strcmp(method_name, "rpcTranslation") == 0
		  || strcmp(method_name, "executeRpc") == 0
		  || strcmp(method_name, "dispatchIMEInput") == 0
		  || strcmp(method_name, "flattenForFLUID") == 0
		  || strcmp(method_name, "unflattenForFLUID") == 0
		  || strcmp(method_name, "toString") == 0
		  || strcmp(method_name, "draw") == 0
		  || strcmp(method_name, "layout") == 0
		  || strcmp(method_name, "measure") == 0
		  || strcmp(method_name, "onDraw") == 0
		  || strcmp(method_name, "onLayout") == 0
		  || strcmp(method_name, "onMeasure") == 0
		  || strcmp(method_name, "dispatchGetDisplayList") == 0
		  || strcmp(method_name, "updateDisplayListIfDirty") == 0
		  || strcmp(method_name, "dispatchDetachedFromWindowForFLUID") == 0
		  || strcmp(method_name, "clearLayoutParamsForFLUID") == 0
		  || strcmp(method_name, "resetLayoutParamsForFLUID") == 0
		  || strcmp(method_name, "setScaleForFLUID") == 0
		  || strcmp(method_name, "setVisibility") == 0
		  || strcmp(method_name, "invalidate") == 0
		  || strcmp(method_name, "getSystemService") == 0
		  || strcmp(method_name, "getId") == 0
		  || strcmp(method_name, "indexOfChild") == 0
//		  || strncmp(method_name, "get", 3) == 0
//		  || strncmp(method_name, "can", 3) == 0
//		  || strncmp(method_name, "has", 3) == 0
//		  || strncmp(method_name, "is", 2) == 0
//		  || strncmp(method_name, "on", 2) == 0
//		  || strncmp(method_name, "resolve", 7) == 0
//		  || strcmp(method_name, "computeScroll") == 0
//		  || strcmp(method_name, "performClick") == 0
//		  || strcmp(method_name, "generateLayoutParams") == 0
//		  || strcmp(method_name, "focusSearch") == 0
	 ) {
	  return;
  }

  char valid_method_sig[1024];
  snprintf(valid_method_sig, 1024, "%s", method_sig);
  char* ptr = valid_method_sig;
  while (*ptr) {
	  if (*ptr == '.')
		  *ptr = '/';
	  ptr++;
  }

  // Get the entry point (reference) to the target method
  jclass clazz = reinterpret_cast<jclass>(clazzObj);
  jmethodID meth_id = env->GetMethodID(clazz, method_name, valid_method_sig);

  ArtMethod *method = reinterpret_cast<ArtMethod*>(meth_id);
  if (method == NULL) 
	  LOG(FATAL) << "error! method is null";
  const void* origin_entry = method->GetEntryPointFromQuickCompiledCode();
  const void* gadget_entry = reinterpret_cast<void*>(fluid::RpcGadgetTrampoline);

  mirror::Class* declaring_class = method->GetDeclaringClass();
  if (klass.Get() != declaring_class) 
	  return;

  fluid::MethodSignatureParser parser(valid_method_sig);
  parser.Parse();
  const std::vector<char>& type_inputs = parser.GetInputType();
  char type_output = parser.GetOutputType();
  jobject g_ref = env->NewGlobalRef(reinterpret_cast<jobject>(clazz));
  jclass g_clazz = reinterpret_cast<jclass>(g_ref);
  fluid::MethodBundleNative* bundle_native = new(std::nothrow) fluid::MethodBundleNative(
		  method_name, method_sig, type_inputs, type_output, g_clazz, 
		  (uint64_t)origin_entry, (uint64_t)gadget_entry);
  if (!bundle_native)
	  LOG(FATAL) << "Allocate MethodBundleNative for " << method_name << " (" << valid_method_sig << ")";
  fluid::g_bundle_map->insert(std::make_pair(meth_id, bundle_native));
  method->SetEntryPointFromQuickCompiledCode(gadget_entry);
  method->SetOriginalEntryPoint(origin_entry);
//  if (kDebugFLUID) LOG(INFO) << "Class_setRpcGadget(), entry_point_from_api = " << method->GetEntryPointFromQuickCompiledCode() 
//	  << ", origin_entry_point = " << method->GetOriginalEntryPoint()
//	  << ", current_entry_point = " << method->GetCurrentEntryPoint();

  fluid::g_debug_ofs.open("/data/local/tmp/rpc_installed_methods", std::ofstream::out | std::ofstream::app);
  fluid::g_debug_ofs << getpid() << ", " << method << ", " << method->PrettyMethod() << ", " << method->GetCurrentEntryPoint() << ", " << method->GetOriginalEntryPoint() << std::endl;
  fluid::g_debug_ofs.close();
}

static void Class_cancelRpcGadget(JNIEnv*, jclass) {
  if (!fluid::g_bundle_map)
	  return;
  for (auto it = fluid::g_bundle_map->begin(); it != fluid::g_bundle_map->end(); it++) {
	  jmethodID meth_id = it->first;
	  fluid::MethodBundleNative* bundle_native = it->second;
	  ArtMethod *method = reinterpret_cast<ArtMethod*>(meth_id);
	  const void* entry_origin = method->GetOriginalEntryPoint();
	  method->SetOriginalEntryPoint(0);
	  method->SetEntryPointFromQuickCompiledCode(entry_origin);
	  delete bundle_native;
  }
  fluid::g_bundle_map->clear();
}
#else
static void Class_initRpcGadget(JNIEnv*, jclass) {
  LOG(INFO) << "Class_initRpcGadget(), it's not aarch64 nor arm.";
}

static void Class_setFLUIDManagerObj(JNIEnv*, jclass, jobject) {
  LOG(INFO) << "Class_setFLUIDManagerObj(), it's not aarch64 nor arm.";
}

static void Class_setRpcGadget(JNIEnv*, jclass, jobject, jstring, jstring) {
  LOG(INFO) << "Class_setRpcGadget(), it's not aarch64 nor arm.";
}

static void Class_cancelRpcGadget(JNIEnv*, jobject) {
  LOG(INFO) << "Class_cancelRpcGadget(), it's not aarch64 nor arm.";
}
#endif

static JNINativeMethod gMethods[] = {
  FAST_NATIVE_METHOD(Class, classForName,
                "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;"),
  FAST_NATIVE_METHOD(Class, getDeclaredAnnotation,
                "(Ljava/lang/Class;)Ljava/lang/annotation/Annotation;"),
  FAST_NATIVE_METHOD(Class, getDeclaredAnnotations, "()[Ljava/lang/annotation/Annotation;"),
  FAST_NATIVE_METHOD(Class, getDeclaredClasses, "()[Ljava/lang/Class;"),
  FAST_NATIVE_METHOD(Class, getDeclaredConstructorInternal,
                "([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;"),
  FAST_NATIVE_METHOD(Class, getDeclaredConstructorsInternal, "(Z)[Ljava/lang/reflect/Constructor;"),
  FAST_NATIVE_METHOD(Class, getDeclaredField, "(Ljava/lang/String;)Ljava/lang/reflect/Field;"),
  FAST_NATIVE_METHOD(Class, getPublicFieldRecursive, "(Ljava/lang/String;)Ljava/lang/reflect/Field;"),
  FAST_NATIVE_METHOD(Class, getDeclaredFields, "()[Ljava/lang/reflect/Field;"),
  FAST_NATIVE_METHOD(Class, getDeclaredFieldsUnchecked, "(Z)[Ljava/lang/reflect/Field;"),
  FAST_NATIVE_METHOD(Class, getDeclaredMethodInternal,
                "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;"),
  FAST_NATIVE_METHOD(Class, getDeclaredMethodsUnchecked,
                "(Z)[Ljava/lang/reflect/Method;"),
  FAST_NATIVE_METHOD(Class, getDeclaringClass, "()Ljava/lang/Class;"),
  FAST_NATIVE_METHOD(Class, getEnclosingClass, "()Ljava/lang/Class;"),
  FAST_NATIVE_METHOD(Class, getEnclosingConstructorNative, "()Ljava/lang/reflect/Constructor;"),
  FAST_NATIVE_METHOD(Class, getEnclosingMethodNative, "()Ljava/lang/reflect/Method;"),
  FAST_NATIVE_METHOD(Class, getInnerClassFlags, "(I)I"),
  FAST_NATIVE_METHOD(Class, getInnerClassName, "()Ljava/lang/String;"),
  FAST_NATIVE_METHOD(Class, getInterfacesInternal, "()[Ljava/lang/Class;"),
  FAST_NATIVE_METHOD(Class, getNameNative, "()Ljava/lang/String;"),
  FAST_NATIVE_METHOD(Class, getPublicDeclaredFields, "()[Ljava/lang/reflect/Field;"),
  FAST_NATIVE_METHOD(Class, getSignatureAnnotation, "()[Ljava/lang/String;"),
  FAST_NATIVE_METHOD(Class, isAnonymousClass, "()Z"),
  FAST_NATIVE_METHOD(Class, isDeclaredAnnotationPresent, "(Ljava/lang/Class;)Z"),
  FAST_NATIVE_METHOD(Class, newInstance, "()Ljava/lang/Object;"),
  FAST_NATIVE_METHOD(Class, initRpcGadget, "()V"),
  FAST_NATIVE_METHOD(Class, setFLUIDManagerObj, "(Ljava/lang/Object;)V"),
  FAST_NATIVE_METHOD(Class, setRpcGadget, "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/String;)V"),
  FAST_NATIVE_METHOD(Class, cancelRpcGadget, "()V"),
};

void register_java_lang_Class(JNIEnv* env) {
  REGISTER_NATIVE_METHODS("java/lang/Class");
}

}  // namespace art
