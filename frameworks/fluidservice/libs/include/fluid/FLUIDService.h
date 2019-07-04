#ifndef FLUID_FLUIDSERVICE_H
#define FLUID_FLUIDSERVICE_H

#include <binder/IServiceManager.h>
#include <binder/Parcel.h>
#include <binder/IPCThreadState.h>
#include <binder/BpBinder.h>
#include <fluid/IFLUIDService.h>
#include <fluid/ConnManager.h>
#include <fluid/Global.h>
#include <messaging/Semaphore.h>

#define LOG_FLUID "FLUIDService"

namespace android {

class FLUIDService : public BnFLUIDService
{
public:
	static void instantiate(const char* hostIP = NULL);
	FLUIDService(const char* hostIP = NULL);
	~FLUIDService();
	void registerAppBinders(sp<IBinder> appThread, sp<IBinder> token); 
	int sendUi(Parcel& request);
	int sendRpc(Parcel& request, Parcel* reply);
	int sendInput(Parcel& request);
	int sendIMEInput(Parcel& request);
	int sendCache(Parcel& request);
	int restoreUi(Parcel* request);
	int executeRpc(Parcel* request);
	int dispatchInput(Parcel* request);
	int dispatchIMEInput(Parcel* request);
	int storeCache(Parcel* request);
	void registerApp();
	void onActivityLaunched(sp<IBinder> token);
	bool isHostDevice();
	void notifyAppCrash(sp<IBinder> appThread);
	void notifyRemoteCrash(Parcel* request);
	void notifyNetworkFault();
	int requestCache(Parcel& request, Parcel* reply);
	int getCache(Parcel* request);
	void sendApk(const char* packageName);
	bool equals(char *dirName, const char *pkgName);
	sp<IBinder> readBinder(Parcel &parcel, int index);
	virtual status_t onTransact(uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags);
	Semaphore mReplySema;
	Parcel* mReply;

	class MainHandler : public Handler
	{
		public:
			MainHandler(FLUIDService* service) { 
				mFLUIDService = service; 
			}
			~MainHandler() {}
			virtual void handleMessage(Message& message);
			FLUIDService* mFLUIDService;
	};

	class MainThread : public Thread
	{
		public:
			MainThread();
			~MainThread() {}
			virtual bool threadLoop();
			MainHandler* getHandler();
			Looper* mLooper;
			MainHandler* mHandler;
			Lock mLock;
			CondVar mCondVar;
			FLUIDService* mFLUIDService;
	};
	MainThread mMainThread;

	class DeathNotifier : public IBinder::DeathRecipient
	{
		public:
			DeathNotifier(FLUIDService* service) {
				mFLUIDService = service;
			}
			virtual void binderDied(const wp<IBinder>& who) {
				FLUIDLOG("binderDied()");
				mFLUIDService->notifyAppCrash(nullptr);
			}
			FLUIDService* mFLUIDService;
	};
	
	bool mIsHostDevice;

private:
	ConnManager mConnManager;
	Semaphore mActivitySema;
	bool mIsWaiting = false;
	sp<IBinder> mActivityManager;
	sp<IBinder> mPackageManager;
	sp<IBinder> mSystemUiThread;
	int mAmHandle;
	int mPmHandle;
	int mSystemUiHandle;
	sp<IBinder> mSystemUiToken;
	sp<IBinder> mAppThread;
	int mAppHandle;
	sp<IBinder> mAppToken;
	sp<DeathNotifier> mDeathNotifier;
	bool mIsCrash;
};

};
#endif
