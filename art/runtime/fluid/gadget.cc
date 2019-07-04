#if defined(__aarch64__) || defined(__arm__)
#include "gadget.h"

namespace art {
namespace fluid {

JavaVM* g_jvm;
jclass g_object_clazz;
jclass g_view_clazz;
jfieldID g_remotable_fid;
jfieldID g_inmaster_fid;
jobject g_fluid_manager = nullptr;
jmethodID g_rpc_translation = nullptr;
jmethodID g_rpc_caching = nullptr;
jmethodID g_ret_caching = nullptr;
jmethodID g_cache_void = nullptr;
jmethodID g_cache_bool = nullptr;
jmethodID g_cache_char = nullptr;
jmethodID g_cache_byte = nullptr;
jmethodID g_cache_short = nullptr;
jmethodID g_cache_int = nullptr;
jmethodID g_cache_long = nullptr;
jmethodID g_cache_float = nullptr;
jmethodID g_cache_double = nullptr;
jmethodID g_cache_object = nullptr;
BundleMap* g_bundle_map = nullptr;
PrimitiveMap* g_primitive_map = nullptr;
std::ofstream g_debug_ofs;

int NeedRpcTranslation(void* art_method, void* receiver, void** stack) {
#if defined(__arm__)
	memcpy(stack - 56, stack, 40);
#endif
	ObjPtr<mirror::Object> obj(reinterpret_cast<mirror::Object*>(receiver));
	uint32_t fluid_flags = obj->GetFLUIDFlags(false);

	if ((fluid_flags & MIGRATED) != 0 && (fluid_flags & IN_REMOTE) != 0) {
		Thread* self = Thread::Current();
		ManagedStack* current_fragment = self->GetNonConstManagedStack();
		ArtMethod** orig_frame = current_fragment->GetTopQuickFrame();
		ArtMethod** cur_frame = reinterpret_cast<ArtMethod**>(stack);
		current_fragment->SetTopQuickFrame(cur_frame);
		self->fluid_caller_frame_stack_.push_back(reinterpret_cast<ArtMethod**>(stack));

		uint8_t* stack_addr_for_ret = 
			reinterpret_cast<uint8_t*>(stack) - sizeof(void*);
		uintptr_t ret_addr = *reinterpret_cast<uintptr_t*>(stack_addr_for_ret);
		self->fluid_ret_addrs_.push_back(reinterpret_cast<void*>(ret_addr));

		checking_call_stack = true;
		int need_rpc = self->CheckCallStack(fluid_flags);
		checking_call_stack = false;
		current_fragment->SetTopQuickFrame(orig_frame);

		if (need_rpc == 0 || need_rpc == 3 
				|| (self->fluid_ret_addrs_.size() > 1 && need_rpc == 1)) {
			self->fluid_caller_frame_stack_.pop_back();
			self->fluid_ret_addrs_.pop_back();
			return 0;
		}

		if (!self->fluid_ret_metadata_.empty() && need_rpc == 2) {
			if (kDebugFLUID) {
				ArtMethod* method = reinterpret_cast<ArtMethod*>(art_method);
				LOG(INFO) << "NeedRpcTranslation() 4"
					<< ", method = " << method->PrettyMethod()
					<< ", receiver = " << receiver
					<< ", z_fluid_flags_ = " << obj->GetFLUIDFlags(false)
					<< ", z_object_id_ = " << obj->GetObjectId(false)
					<< ", need_rpc = " << need_rpc
					<< ", fluid_ret_addrs_ size = " << self->fluid_ret_addrs_.size() 
					<< ", stack = " << stack;
			}
			return 4;
		}

		if (kDebugFLUID) {
			ArtMethod* method = reinterpret_cast<ArtMethod*>(art_method);
			LOG(INFO) << "NeedRpcTranslation() 1"
				<< ", method = " << method->PrettyMethod()
				<< ", receiver = " << receiver
				<< ", z_fluid_flags_ = " << obj->GetFLUIDFlags(false)
				<< ", z_object_id_ = " << obj->GetObjectId(false)
				<< ", need_rpc = " << need_rpc
				<< ", fluid_ret_addrs_ size = " << self->fluid_ret_addrs_.size() 
				<< ", stack = " << stack;
		}
		return 1;
	}
	else if ((fluid_flags & DUMMY_OBJECT) != 0 && (fluid_flags & MIGRATED) == 0) {
		Thread* self = Thread::Current();
		if (kDebugFLUID) {
			ArtMethod* method = reinterpret_cast<ArtMethod*>(art_method);
			LOG(INFO) << "NeedRpcTranslation() 2"
				<< ", method = " << method->PrettyMethod()
				<< ", receiver = " << receiver
				<< ", z_fluid_flags_ = " << obj->GetFLUIDFlags(false)
				<< ", z_object_id_ = " << obj->GetObjectId(false)
				<< ", stack = " << stack;
		}
		self->fluid_caller_frame_stack_.push_back(reinterpret_cast<ArtMethod**>(stack));

		uint8_t* stack_addr_for_ret = 
			reinterpret_cast<uint8_t*>(stack) - sizeof(void*);
		uintptr_t ret_addr = *reinterpret_cast<uintptr_t*>(stack_addr_for_ret);
		self->fluid_ret_addrs_.push_back(reinterpret_cast<void*>(ret_addr));

		return 2;
	}
	else if ((fluid_flags & DUMMY_IN_REMOTE) != 0 && (fluid_flags & MIGRATED) == 0) {
		Thread* self = Thread::Current();
		ManagedStack* current_fragment = self->GetNonConstManagedStack();
		ArtMethod** orig_frame = current_fragment->GetTopQuickFrame();
		ArtMethod** cur_frame = reinterpret_cast<ArtMethod**>(stack);
		current_fragment->SetTopQuickFrame(cur_frame);
		self->fluid_caller_frame_stack_.push_back(reinterpret_cast<ArtMethod**>(stack));

		uint8_t* stack_addr_for_ret = 
			reinterpret_cast<uint8_t*>(stack) - sizeof(void*);
		uintptr_t ret_addr = *reinterpret_cast<uintptr_t*>(stack_addr_for_ret);
		self->fluid_ret_addrs_.push_back(reinterpret_cast<void*>(ret_addr));

		checking_call_stack = true;
		int need_rpc = self->CheckCallStack(fluid_flags);
		checking_call_stack = false;
		current_fragment->SetTopQuickFrame(orig_frame);

		if (need_rpc == 3 || need_rpc == 1) {
			ArtMethod* method = reinterpret_cast<ArtMethod*>(art_method);
			if (kDebugFLUID) {
				LOG(INFO) << "NeedRpcTranslation() 3"
					<< ", method = " << method->PrettyMethod()
					<< ", receiver = " << receiver
					<< ", z_fluid_flags_ = " << obj->GetFLUIDFlags(false)
					<< ", z_object_id_ = " << obj->GetObjectId(false)
					<< ", need_rpc = " << need_rpc
					<< ", stack = " << stack;
			}
			return 3;
		}
		self->fluid_caller_frame_stack_.pop_back();
		self->fluid_ret_addrs_.pop_back();
		return 0;
	}
	else {
		return 0;
	}
}

void RpcTranslationNative(void** gp_reg, void** fp_reg, void** stack) {
	Thread* self = Thread::Current();
	MutexLock mutex(self, *g_fluid_mutex);
	if (kDebugFLUID)
		LOG(INFO) << "============ Enter RpcTranslationNative() ============";
	JNIEnv* env = self->GetJniEnv();
	ArtMethod* method = reinterpret_cast<ArtMethod*>(gp_reg[0]);
	if (kDebugFLUID) {
		LOG(INFO) << method->PrettyMethod()
			<< " [ entry point = " << method->GetEntryPointFromQuickCompiledCode()
			<< ", ArtMethod = " << method << " ]"
			<< ", thread = " << self
			<< ", gp_reg = " << gp_reg << ", fp_reg = " << fp_reg << ", stack = " << stack;
//		for (int i = 30; i >= 0; i--)
//			LOG(INFO) << &gp_reg[i] << ", x" << i << " = " << gp_reg[i];
	}

	// Reconfigure a stack frame
	ManagedStack* current_fragment = self->GetNonConstManagedStack();
	ArtMethod** orig_frame = current_fragment->GetTopQuickFrame();
	ArtMethod** cur_frame = reinterpret_cast<ArtMethod**>(stack);
	current_fragment->SetTopQuickFrame(cur_frame);
	if (kDebugFLUID) {
		LOG(INFO) << "current_fragment = " << current_fragment
			<< ", next_fragment = " << current_fragment->GetLink()
			<< ", orig_frame = " << orig_frame;
	}
	//self->PrintCallStack();

	// Prepare input objects
	jmethodID meth_id = reinterpret_cast<jmethodID>(method);
	auto iter = g_bundle_map->find(meth_id);
	MethodBundleNative* bundle = iter->second;
	InputMarshaller input_marshaller(gp_reg, fp_reg, stack);
	const std::vector<char>& input_type = bundle->GetInputType();
	int32_t input_count = input_type.size();
	std::unique_ptr<void*[]> arguments(new(std::nothrow) void*[input_count]);
	input_marshaller.Extract(input_type, arguments.get());
	jobjectArray input_box = env->NewObjectArray(input_count, g_object_clazz, nullptr);
	if (BoxInput(input_box, arguments.get(), input_type) != PROC_SUCC)
		LOG(FATAL) << "Failed in input boxing";
	jstring method_name = env->NewStringUTF(bundle->GetMethodName());
	jstring method_sig = env->NewStringUTF(bundle->GetMethodSig());

	void* receiver = gp_reg[1];
	ObjPtr<mirror::Object> obj(reinterpret_cast<mirror::Object*>(receiver));
	jobject ref_receiver = reinterpret_cast<JNIEnvExt*>(env)->AddLocalReference<jobject>(obj);
	env->CallVoidMethod(g_fluid_manager, g_rpc_translation, 
			ref_receiver, method_name, method_sig, input_box);

	env->DeleteLocalRef(method_name);
	env->DeleteLocalRef(method_sig);
	env->DeleteLocalRef(input_box);
	env->DeleteLocalRef(ref_receiver);
	current_fragment->SetTopQuickFrame(orig_frame);
	self->fluid_caller_frame_stack_.pop_back();

	if (kDebugFLUID)
		LOG(INFO) << "============ Exit RpcTranslationNative() ============";
}

void* ReturnCachingNative(void* ret, void** stack) {
	Thread* self = Thread::Current();
	MutexLock mutex(self, *g_fluid_mutex);
	if (kDebugFLUID) 
		LOG(INFO) << "============ Enter ReturnCachingNative() ============";
	JNIEnv* env = self->GetJniEnv();
	ReturnMetadata* metadata = self->fluid_ret_metadata_.back();
	int object_id = metadata->object_id_;
	ArtMethod* method = metadata->method_;
	if (kDebugFLUID) {
		LOG(INFO) << method->PrettyMethod() 
			<< " [ entry point = " << method->GetEntryPointFromQuickCompiledCode()
			<< ", ArtMethod = " << method << " ]"
			<< ", thread = " << self 
			<< ", object_id = " << object_id
			<< ", ret = " << ret << ", stack = " << stack;
	}

	// Reconfigure a stack frame
	ManagedStack* current_fragment = self->GetNonConstManagedStack();
	ArtMethod** orig_frame = current_fragment->GetTopQuickFrame();
	ArtMethod** cur_frame = reinterpret_cast<ArtMethod**>(stack);
	current_fragment->SetTopQuickFrame(cur_frame);
	if (kDebugFLUID) {
		LOG(INFO) << "current_fragment = " << current_fragment
			<< ", next_fragment = " << current_fragment->GetLink()
			<< ", orig_frame = " << orig_frame;
	}
	//self->PrintCallStack();

	// Prepare input objects
	jmethodID meth_id = reinterpret_cast<jmethodID>(method);
	auto iter = g_bundle_map->find(meth_id);
	MethodBundleNative* bundle = iter->second;
	jstring method_name = env->NewStringUTF(bundle->GetMethodName());
	jstring method_sig = env->NewStringUTF(bundle->GetMethodSig());
	char type = bundle->GetOutputType();
	jobject ret_object = nullptr;
	if (type != kTypeVoid)
		EncapsulateObject(type, ret, &ret_object);

	env->CallVoidMethod(g_fluid_manager, g_ret_caching, 
			object_id, method_name, method_sig, ret_object);

	env->DeleteLocalRef(method_name);
	env->DeleteLocalRef(method_sig);
	env->DeleteLocalRef(ret_object);
	current_fragment->SetTopQuickFrame(orig_frame);

	self->fluid_ret_metadata_.pop_back();
	delete metadata;
	self->fluid_caller_frame_stack_.pop_back();
	void* ret_addr = self->fluid_ret_addrs_.back();
	self->fluid_ret_addrs_.pop_back();
	if (kDebugFLUID) 
		LOG(INFO) << "============ Exit ReturnCachingNative() ============";
	return ret_addr;
}

void RpcCachingNative(void** gp_reg, void** fp_reg, void** stack) {
	Thread* self = Thread::Current();
	MutexLock mutex(self, *g_fluid_mutex);
	if (kDebugFLUID)
		LOG(INFO) << "============ Enter RpcCachingNative() ============";
	JNIEnv* env = self->GetJniEnv();
	ArtMethod* method = reinterpret_cast<ArtMethod*>(gp_reg[0]);
	if (kDebugFLUID) {
		LOG(INFO) << method->PrettyMethod()
			<< " [ entry point = " << method->GetEntryPointFromQuickCompiledCode()
			<< ", ArtMethod = " << method << " ]"
			<< ", thread = " << self
			<< ", gp_reg = " << gp_reg << ", fp_reg = " << fp_reg << ", stack = " << stack;
//		for (int i = 30; i >= 0; i--)
//			LOG(INFO) << &gp_reg[i] << ", x" << i << " = " << gp_reg[i];
	}

	// Reconfigure a stack frame
	ManagedStack* current_fragment = self->GetNonConstManagedStack();
	ArtMethod** orig_frame = current_fragment->GetTopQuickFrame();
	ArtMethod** cur_frame = reinterpret_cast<ArtMethod**>(stack);
	current_fragment->SetTopQuickFrame(cur_frame);
	if (kDebugFLUID) {
		LOG(INFO) << "current_fragment = " << current_fragment
			<< ", next_fragment = " << current_fragment->GetLink()
			<< ", orig_frame = " << orig_frame;
	}
	//self->PrintCallStack();

	// Prepare input objects
	jmethodID meth_id = reinterpret_cast<jmethodID>(method);
	auto iter = g_bundle_map->find(meth_id);
	MethodBundleNative* bundle = iter->second;
	InputMarshaller input_marshaller(gp_reg, fp_reg, stack);
	const std::vector<char>& input_type = bundle->GetInputType();
	int32_t input_count = input_type.size();
	std::unique_ptr<void*[]> arguments(new(std::nothrow) void*[input_count]);
	input_marshaller.Extract(input_type, arguments.get());
	jobjectArray input_box = env->NewObjectArray(input_count, g_object_clazz, nullptr);
	if (BoxInput(input_box, arguments.get(), input_type) != PROC_SUCC)
		LOG(FATAL) << "Failed in input boxing";
	jstring method_name = env->NewStringUTF(bundle->GetMethodName());
	jstring method_sig = env->NewStringUTF(bundle->GetMethodSig());

	void* receiver = gp_reg[1];
	ObjPtr<mirror::Object> obj(reinterpret_cast<mirror::Object*>(receiver));
	jobject ref_receiver = reinterpret_cast<JNIEnvExt*>(env)->AddLocalReference<jobject>(obj);
	env->CallVoidMethod(g_fluid_manager, g_rpc_caching, 
			ref_receiver, method_name, method_sig, input_box);

	env->DeleteLocalRef(method_name);
	env->DeleteLocalRef(method_sig);
	env->DeleteLocalRef(input_box);
	env->DeleteLocalRef(ref_receiver);
	current_fragment->SetTopQuickFrame(orig_frame);
	self->fluid_caller_frame_stack_.pop_back();

	if (kDebugFLUID) 
		LOG(INFO) << "============ Exit RpcCachingNative() ============";
}

void* GetReturnAddr() {
	Thread* self = Thread::Current();
	void* ret_addr = self->fluid_ret_addrs_.back();
	self->fluid_ret_addrs_.pop_back();
	return ret_addr;
}

void PrepareReturnCaching(void** gp_reg) {
	Thread* self = Thread::Current();
	ArtMethod* method = reinterpret_cast<ArtMethod*>(gp_reg[0]);
	void* receiver = gp_reg[1];
	ObjPtr<mirror::Object> obj(reinterpret_cast<mirror::Object*>(receiver));
	ReturnMetadata* metadata = new ReturnMetadata(obj->GetObjectId(false), method);
	self->fluid_ret_metadata_.push_back(metadata);
}

bool BoxInput(jobjectArray input_box, void** arguments, const std::vector<char>& input_type) {
	JNIEnv* env = Thread::Current()->GetJniEnv();
	off_t idx = 0;
	for (char type : input_type) {
		jobject obj;
		if (EncapsulateObject(type, arguments[idx], &obj) == PROC_FAIL)
			return PROC_FAIL;
		env->SetObjectArrayElement(input_box, idx++, obj);
		if (type != kTypeObject)
			env->DeleteLocalRef(obj);
		else
			reinterpret_cast<JNIEnvExt*>(env)->DeleteLocalRef(obj);
		CHK_EXCP(env);
	}
	return PROC_SUCC;
}

bool EncapsulateObject(char type, void* reg, jobject* p_obj) {
	JNIEnv* env = Thread::Current()->GetJniEnv();
	if (type == kTypeVoid) {
		*p_obj = nullptr;
		return PROC_SUCC;
	}

	jmethodID meth_ctor;
	jclass clazz;
	if (type != kTypeObject) {
		auto iter = g_primitive_map->find(type);
		std::unique_ptr<PrimitiveTypeWrapper>& wrapper = iter->second;
		clazz = wrapper->GetClass();
		meth_ctor = wrapper->GetConstructor();
	}

	switch (type) {
		case kTypeBoolean: {
			uintptr_t inter = reinterpret_cast<uintptr_t>(reg);
			jboolean real = static_cast<jboolean>(inter);
			*p_obj = env->NewObject(clazz, meth_ctor, real);
			CHK_EXCP(env);
			break;
		}
		case kTypeByte: {
			uintptr_t inter = reinterpret_cast<uintptr_t>(reg);
			jbyte real = static_cast<jbyte>(inter);
			*p_obj = env->NewObject(clazz, meth_ctor, real);
			CHK_EXCP(env);
			break;
		}
		case kTypeChar: {
			uintptr_t inter = reinterpret_cast<uintptr_t>(reg);
			jchar real = static_cast<jchar>(inter);
			*p_obj = env->NewObject(clazz, meth_ctor, real);
			CHK_EXCP(env);
			break;
		}
		case kTypeShort: {
			uintptr_t inter = reinterpret_cast<uintptr_t>(reg);
			jshort real = static_cast<jshort>(inter);
			*p_obj = env->NewObject(clazz, meth_ctor, real);
			CHK_EXCP(env);
			break;
		}
		case kTypeInt: {
			uintptr_t inter = reinterpret_cast<uintptr_t>(reg);
			jint real = static_cast<jint>(inter);
			*p_obj = env->NewObject(clazz, meth_ctor, real);
			CHK_EXCP(env);
			break;
		}
		case kTypeLong: {
			uintptr_t inter = reinterpret_cast<uintptr_t>(reg);
			jlong real = static_cast<jlong>(inter);
			*p_obj = env->NewObject(clazz, meth_ctor, real);
			CHK_EXCP(env);
			break;
		}
		case kTypeFloat: {
			float real;
			memcpy(&real, &reg, sizeof(float));
			*p_obj = env->NewObject(clazz, meth_ctor, real);
			CHK_EXCP(env);
			break;
		}
		case kTypeDouble: {
			double real;
			memcpy(&real, &reg, sizeof(float));
			*p_obj = env->NewObject(clazz, meth_ctor, real);
			CHK_EXCP(env);
			break;
		}
		case kTypeObject: {
			if (reg) {
			    ScopedFastNativeObjectAccess soa(env);
			    ObjPtr<mirror::Object> obj(reinterpret_cast<mirror::Object*>(reg));
			    *p_obj = soa.AddLocalReference<jobject>(obj);
			}
			else
			    *p_obj = nullptr;
			break;
		}
	}
	return PROC_SUCC;
}

#if defined(__aarch64__)
void* ExecuteCacheNative(void** gp_reg, void** stack) {
	Thread* self = Thread::Current();
	MutexLock mutex(self, *g_fluid_mutex);
	if (kDebugFLUID) 
		LOG(INFO) << "============ Enter ExecuteCacheNative() ============";
	JNIEnv* env = self->GetJniEnv();
	ArtMethod* method = reinterpret_cast<ArtMethod*>(gp_reg[0]);
	ObjPtr<mirror::Object> object(reinterpret_cast<mirror::Object*>(gp_reg[1]));
	int object_id = object->GetObjectId(false);
	if (kDebugFLUID) {
		LOG(INFO) << method->PrettyMethod()
			<< " [ entry point = " << method->GetEntryPointFromQuickCompiledCode()
			<< ", ArtMethod = " << method << " ]"
			<< ", thread = " << self
			<< ", object_id = " << object_id
			<< ", gp_reg = " << gp_reg << ", stack = " << stack;
//		for (int i = 30; i >= 0; i--)
//			LOG(INFO) << &gp_reg[i] << ", x" << i << " = " << gp_reg[i];
	}

	// Reconfigure a stack frame
	ManagedStack* current_fragment = self->GetNonConstManagedStack();
	ArtMethod** orig_frame = current_fragment->GetTopQuickFrame();
	ArtMethod** cur_frame = reinterpret_cast<ArtMethod**>(stack);
	current_fragment->SetTopQuickFrame(cur_frame);
	if (kDebugFLUID) {
		LOG(INFO) << "current_fragment = " << current_fragment
			<< ", next_fragment = " << current_fragment->GetLink()
			<< ", orig_frame = " << orig_frame;
	}
	//self->PrintCallStack();

	// Prepare input objects
	jmethodID meth_id = reinterpret_cast<jmethodID>(method);
	auto iter = g_bundle_map->find(meth_id);
	MethodBundleNative* bundle = iter->second;
	jstring method_name = env->NewStringUTF(bundle->GetMethodName());
	jstring method_sig = env->NewStringUTF(bundle->GetMethodSig());
	void* result = nullptr;

	Primitive::Type returnType = method->GetReturnTypePrimitive();
	if (returnType == Primitive::Type::kPrimVoid) {
		env->CallVoidMethod(g_fluid_manager, g_cache_void, object_id, method_name, method_sig);
	}
	else if (returnType == Primitive::Type::kPrimBoolean) {
		jboolean res = env->CallBooleanMethod(g_fluid_manager, g_cache_bool, object_id, method_name, method_sig);
		LOG(INFO) << "boolean res = " << (res == true)? "true" : "false";
		result = reinterpret_cast<void*>(res);
	}
	else if (returnType == Primitive::Type::kPrimByte) {
		jbyte res = env->CallByteMethod(g_fluid_manager, g_cache_byte, object_id, method_name, method_sig);
		LOG(INFO) << "byte res = " << res;
		result = reinterpret_cast<void*>(res);
	}
	else if (returnType == Primitive::Type::kPrimChar) {
		jchar res = env->CallCharMethod(g_fluid_manager, g_cache_char, object_id, method_name, method_sig);
		LOG(INFO) << "char res = " << res;
		result = reinterpret_cast<void*>(res);
	}
	else if (returnType == Primitive::Type::kPrimShort) {
		jshort res = env->CallShortMethod(g_fluid_manager, g_cache_short, object_id, method_name, method_sig);
		LOG(INFO) << "short res = " << res;
		result = reinterpret_cast<void*>(res);
	}
	else if (returnType == Primitive::Type::kPrimInt) {
		jint res = env->CallIntMethod(g_fluid_manager, g_cache_int, object_id, method_name, method_sig);
		LOG(INFO) << "int res = " << res;
		result = reinterpret_cast<void*>(res);
	}
	else if (returnType == Primitive::Type::kPrimLong) {
		jlong res = env->CallLongMethod(g_fluid_manager, g_cache_long, object_id, method_name, method_sig);
		LOG(INFO) << "long res = " << res;
		result = reinterpret_cast<void*>(res);
	}
	else if (returnType == Primitive::Type::kPrimFloat) {
		jfloat res = env->CallFloatMethod(g_fluid_manager, g_cache_float, object_id, method_name, method_sig);
		LOG(INFO) << "float res = " << res;
		memcpy(&result, &res, sizeof(jfloat));
	}
	else if (returnType == Primitive::Type::kPrimDouble) {
		jdouble res = env->CallDoubleMethod(g_fluid_manager, g_cache_double, object_id, method_name, method_sig);
		LOG(INFO) << "double res = " << res;
		memcpy(&result, &res, sizeof(jdouble));
	}
	else {
		jobject res = env->CallObjectMethod(g_fluid_manager, g_cache_object, object_id, method_name, method_sig);
		LOG(INFO) << "object res = " << res;
		ObjPtr<mirror::Object> obj_ptr = self->DecodeJObject(res);
		result = reinterpret_cast<void*>(obj_ptr.Ptr());
	}

	env->DeleteLocalRef(method_name);
	env->DeleteLocalRef(method_sig);
	current_fragment->SetTopQuickFrame(orig_frame);
	self->fluid_caller_frame_stack_.pop_back();
	if (kDebugFLUID) 
		LOG(INFO) << "============ Exit ExecuteCacheNative() ============";
	return result;
}

void InputMarshaller::Extract(const std::vector<char>& input_type, void** arguments) {
	off_t gp_idx = 2, fp_idx = 0;
	for (char type : input_type) {
		switch (type) {
			case kTypeBoolean:
			case kTypeByte:
			case kTypeChar:
			case kTypeShort:
			case kTypeInt:
			case kTypeLong:
			case kTypeObject:
				if (gp_idx < 8)
					*arguments++ = x[gp_idx++];
				else
					*arguments++ = *stack_++;
				break;
			case kTypeFloat:
			case kTypeDouble:
				if (fp_idx < 8)
					*arguments++ = d[fp_idx++];
				else
					*arguments++ = *stack_++;
				break;
		}
	}
}

#elif defined(__arm__)
void ExecuteCacheNative(void** gp_reg, void** stack, void** ret_value) {
	Thread* self = Thread::Current();
	MutexLock mutex(self, *g_fluid_mutex);
	if (kDebugFLUID) 
		LOG(INFO) << "============ Enter ExecuteCacheNative() ============";
	JNIEnv* env = self->GetJniEnv();
	ArtMethod* method = reinterpret_cast<ArtMethod*>(gp_reg[0]);
	ObjPtr<mirror::Object> object(reinterpret_cast<mirror::Object*>(gp_reg[1]));
	int object_id = object->GetObjectId(false);
	if (kDebugFLUID) {
		LOG(INFO) << method->PrettyMethod()
			<< " [ entry point = " << method->GetEntryPointFromQuickCompiledCode()
			<< ", ArtMethod = " << method << " ]"
			<< ", thread = " << self
			<< ", object_id = " << object_id
			<< ", gp_reg = " << gp_reg << ", stack = " << stack;
//		for (int i = 30; i >= 0; i--)
//			LOG(INFO) << &gp_reg[i] << ", x" << i << " = " << gp_reg[i];
	}

	// Reconfigure a stack frame
	ManagedStack* current_fragment = self->GetNonConstManagedStack();
	ArtMethod** orig_frame = current_fragment->GetTopQuickFrame();
	ArtMethod** cur_frame = reinterpret_cast<ArtMethod**>(stack);
	current_fragment->SetTopQuickFrame(cur_frame);
	if (kDebugFLUID) {
		LOG(INFO) << "current_fragment = " << current_fragment
			<< ", next_fragment = " << current_fragment->GetLink()
			<< ", orig_frame = " << orig_frame;
	}
	//self->PrintCallStack();

	// Prepare input objects
	jmethodID meth_id = reinterpret_cast<jmethodID>(method);
	auto iter = g_bundle_map->find(meth_id);
	MethodBundleNative* bundle = iter->second;
	jstring method_name = env->NewStringUTF(bundle->GetMethodName());
	jstring method_sig = env->NewStringUTF(bundle->GetMethodSig());

	Primitive::Type returnType = method->GetReturnTypePrimitive();
	if (returnType == Primitive::Type::kPrimVoid) {
		env->CallVoidMethod(g_fluid_manager, g_cache_void, object_id, method_name, method_sig);
	}
	else if (returnType == Primitive::Type::kPrimBoolean) {
		jboolean res = env->CallBooleanMethod(g_fluid_manager, g_cache_bool, object_id, method_name, method_sig);
		LOG(INFO) << "boolean res = " << (res == true)? "true" : "false";
		*ret_value = reinterpret_cast<void*>(res);
	}
	else if (returnType == Primitive::Type::kPrimByte) {
		jbyte res = env->CallByteMethod(g_fluid_manager, g_cache_byte, object_id, method_name, method_sig);
		LOG(INFO) << "byte res = " << res;
		*ret_value = reinterpret_cast<void*>(res);
	}
	else if (returnType == Primitive::Type::kPrimChar) {
		jchar res = env->CallCharMethod(g_fluid_manager, g_cache_char, object_id, method_name, method_sig);
		LOG(INFO) << "char res = " << res;
		*ret_value = reinterpret_cast<void*>(res);
	}
	else if (returnType == Primitive::Type::kPrimShort) {
		jshort res = env->CallShortMethod(g_fluid_manager, g_cache_short, object_id, method_name, method_sig);
		LOG(INFO) << "short res = " << res;
		*ret_value = reinterpret_cast<void*>(res);
	}
	else if (returnType == Primitive::Type::kPrimInt) {
		jint res = env->CallIntMethod(g_fluid_manager, g_cache_int, object_id, method_name, method_sig);
		LOG(INFO) << "int res = " << res;
		*ret_value = reinterpret_cast<void*>(res);
	}
	else if (returnType == Primitive::Type::kPrimLong) {
		jlong res = env->CallLongMethod(g_fluid_manager, g_cache_long, object_id, method_name, method_sig);
		LOG(INFO) << "long res = " << res;
		memcpy(ret_value, &res, sizeof(jlong));
	}
	else if (returnType == Primitive::Type::kPrimFloat) {
		jfloat res = env->CallFloatMethod(g_fluid_manager, g_cache_float, object_id, method_name, method_sig);
		LOG(INFO) << "float res = " << res;
		memcpy(ret_value, &res, sizeof(jfloat));
	}
	else if (returnType == Primitive::Type::kPrimDouble) {
		jdouble res = env->CallDoubleMethod(g_fluid_manager, g_cache_double, object_id, method_name, method_sig);
		LOG(INFO) << "double res = " << res;
		memcpy(ret_value, &res, sizeof(jdouble));
	}
	else {
		jobject res = env->CallObjectMethod(g_fluid_manager, g_cache_object, object_id, method_name, method_sig);
		LOG(INFO) << "object res = " << res;
		ObjPtr<mirror::Object> obj_ptr = self->DecodeJObject(res);
		*ret_value = reinterpret_cast<void*>(obj_ptr.Ptr());
	}

	env->DeleteLocalRef(method_name);
	env->DeleteLocalRef(method_sig);
	current_fragment->SetTopQuickFrame(orig_frame);
	self->fluid_caller_frame_stack_.pop_back();
	if (kDebugFLUID) 
		LOG(INFO) << "============ Exit ExecuteCacheNative() ============";
}

void InputMarshaller::Extract(const std::vector<char>& input_type, void** arguments) {
	off_t gp_idx = 2, fp_idx = 0;
	for (char type : input_type) {
		switch (type) {
			case kTypeBoolean:
			case kTypeByte:
			case kTypeChar:
			case kTypeShort:
			case kTypeInt:
			case kTypeObject:
				if (gp_idx < 4)
					*arguments++ = r[gp_idx++];
				else
					*arguments++ = *stack_++;
				break;
			case kTypeLong:
				if (gp_idx == 2) {
					*arguments++ = r[gp_idx++];
					*arguments++ = r[gp_idx++];
				}
				else if (gp_idx == 3) {
					*arguments++ = *stack_++;
					*arguments++ = r[gp_idx++];
				}
				else {
					*arguments++ = *(++stack_);
					*arguments++ = *(stack_ - 1);
					++stack_;
				}
				break;
			case kTypeFloat:
				if (fp_idx < 16)
					*arguments++ = s[fp_idx++];
				else
					*arguments++ = *stack_++;
				break;
			case kTypeDouble:
				if (fp_idx < 15) {
					*arguments++ = s[fp_idx++];
					*arguments++ = s[fp_idx++];
				}
				else if (fp_idx == 15) {
					*arguments++ = *stack_++;
					*arguments++ = s[fp_idx++];
				}
				else {
					*arguments++ = *(++stack_);
					*arguments++ = *(stack_ - 1);
					++stack_;
				}
				break;
		}
	}
}
#endif

}
}
#endif 
