#include <fluid/IFLUIDService.h>

namespace android {

IMPLEMENT_META_INTERFACE(FLUIDService, "android.fluid.IFLUIDService");

status_t BnFLUIDService::onTransact(
	uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags) {
	switch(code) {
		case BINDER::REGISTER_APP_BINDERS: {
			CHECK_INTERFACE(IFLUIDService, data, reply);
			sp<IBinder> appThread = data.readStrongBinder();
			sp<IBinder> token = data.readStrongBinder();
			registerAppBinders(appThread, token);
			return NO_ERROR;
		} break; 
		case BINDER::SEND_UI: {
			CHECK_INTERFACE(IFLUIDService, data, reply);
			Parcel request;
			request.appendFrom(&data, 0, data.dataSize());
			sendUi(request);
			return NO_ERROR;
		} break;
		case BINDER::SEND_RPC: {
			CHECK_INTERFACE(IFLUIDService, data, reply);
			Parcel request;
			request.appendFrom(&data, 0, data.dataSize());
			sendRpc(request, reply); 
			return NO_ERROR;
		} break;
		case BINDER::SEND_INPUT: {
			CHECK_INTERFACE(IFLUIDService, data, reply);
			Parcel request;
			request.appendFrom(&data, 0, data.dataSize());
			sendInput(request); 
			return NO_ERROR;
		} break;
		case BINDER::SEND_IME_INPUT: {
			CHECK_INTERFACE(IFLUIDService, data, reply);
			Parcel request;
			request.appendFrom(&data, 0, data.dataSize());
			sendIMEInput(request); 
			return NO_ERROR;
		} break;
		case BINDER::SEND_CACHE: {
			CHECK_INTERFACE(IFLUIDService, data, reply);
			Parcel request;
			request.appendFrom(&data, 0, data.dataSize());
			sendCache(request);
			return NO_ERROR;
		} break;
		case BINDER::ON_ACTIVITY_LAUNCHED: {
			CHECK_INTERFACE(IFLUIDService, data, reply);
			sp<IBinder> token = data.readStrongBinder();
			onActivityLaunched(token);
			return NO_ERROR;
		} break; 
		case BINDER::IS_HOST_DEVICE: {
			CHECK_INTERFACE(IFLUIDService, data, reply);
			bool res = isHostDevice();
			reply->writeNoException();
			reply->writeBool(res);
			return NO_ERROR;
		} break; 
		case BINDER::NOTIFY_APP_CRASH: {
			CHECK_INTERFACE(IFLUIDService, data, reply);
			sp<IBinder> appThread = data.readStrongBinder();
			notifyAppCrash(appThread);
			return NO_ERROR;
		} break; 
		case BINDER::REQUEST_CACHE: {
			CHECK_INTERFACE(IFLUIDService, data, reply);
			Parcel request;
			request.appendFrom(&data, 0, data.dataSize());
			uint8_t* ret = nullptr;
			requestCache(request, reply);
			return NO_ERROR;
		} break;
		default:
			return BBinder::onTransact(code, data, reply, flags);
	}
}

};
