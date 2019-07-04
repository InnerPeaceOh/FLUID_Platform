#ifndef FLUID_IFLUIDMANAGER_H
#define FLUID_IFLUIDMANAGER_H

#include <binder/IInterface.h>
#include <binder/Parcel.h>
#include <fluid/Global.h>
#include <dirent.h>

#define LOG_FLUID "IFLUIDService"

namespace android {

class IFLUIDService : public IInterface
{
public:
	DECLARE_META_INTERFACE(FLUIDService);
	virtual void registerAppBinders(sp<IBinder> appThread, sp<IBinder> token) = 0;
	virtual int sendUi(Parcel& data) = 0;
	virtual int sendCache(Parcel& data) = 0;
	virtual int sendRpc(Parcel& data, Parcel* reply) = 0;
	virtual int sendInput(Parcel& data) = 0;
	virtual int sendIMEInput(Parcel& data) = 0;
	virtual void onActivityLaunched(sp<IBinder> token) = 0;
	virtual bool isHostDevice() = 0;
	virtual void notifyAppCrash(sp<IBinder> appThread) = 0;
	virtual int requestCache(Parcel& data, Parcel* reply) = 0;
};

class BpFLUIDService : public BpInterface<IFLUIDService>
{
public:
	BpFLUIDService(const sp<IBinder>& impl) : BpInterface<IFLUIDService>(impl) {}
	void registerAppBinders(sp<IBinder>, sp<IBinder>) {}
	int sendUi(Parcel&) { return -1; }
	int sendCache(Parcel&) { return -1; }
	int sendRpc(Parcel&, Parcel*) { return -1; }
	int sendInput(Parcel&) { return -1; }
	int sendIMEInput(Parcel&) { return -1; }
	void onActivityLaunched(sp<IBinder> token) {}
	bool isHostDevice() { return false; }
	void notifyAppCrash(sp<IBinder> appThread) {}
	int requestCache(Parcel&, Parcel*) { return -1; }
};

class BnFLUIDService : public BnInterface<IFLUIDService>
{
public:
	virtual status_t onTransact(uint32_t code, 
								const Parcel& data, 
								Parcel* reply, 
								uint32_t flags = 0);
};

};
#endif
