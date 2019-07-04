#include <fluid/FLUIDService.h>

namespace android {

void FLUIDService::instantiate(const char* hostIP) {
	if (hostIP) 
		defaultServiceManager()->addService(String16("fluid_service"), new FLUIDService(hostIP));
	else 
		defaultServiceManager()->addService(String16("fluid_service"), new FLUIDService());
}

FLUIDService::FLUIDService(const char* hostIP) : BnFLUIDService(), mConnManager(this) {
	FLUIDLOG("FLUIDService() start");

	mIsHostDevice = true;
	mMainThread.mFLUIDService = this;
	mMainThread.run("MainThread");

	mConnManager.runSenderThread();
	mConnManager.runListenThread();
	if (hostIP) {
		mIsHostDevice = false;
		mConnManager.connectToServer(hostIP);
	}
	mActivityManager = defaultServiceManager()->checkService(String16("activity"));
	mAmHandle = (mActivityManager->remoteBinder())->handle();
	mPackageManager = defaultServiceManager()->checkService(String16("package"));
	mPmHandle = (mPackageManager->remoteBinder())->handle();
	mDeathNotifier = new DeathNotifier(this);
	mIsCrash = false;
}

FLUIDService::~FLUIDService() {}

FLUIDService::MainThread::MainThread() : mCondVar(mLock) {}

bool FLUIDService::MainThread::threadLoop() {
	FLUIDLOG("MainThread::threadLoop()");
	Looper::prepare();
	mLooper = Looper::myLooper();
	mHandler = new MainHandler(mFLUIDService);
	mCondVar.notifyAll();
	Looper::loop();
	return false;
}

FLUIDService::MainHandler* FLUIDService::MainThread::getHandler() {
	AutoLock autoLock(mLock);
	if (mHandler == NULL) {
		mCondVar.wait();
	}
	return mHandler;
}

void FLUIDService::MainHandler::handleMessage(Message &message) {
	int code = message.what;
	Parcel* parcel = (Parcel*) message.obj;
	if (code == BINDER::RESTORE_UI)
		mFLUIDService->restoreUi(parcel);
	else if (code == BINDER::EXECUTE_RPC)
		mFLUIDService->executeRpc(parcel);
	else if (code == BINDER::DISPATCH_INPUT)
		mFLUIDService->dispatchInput(parcel);
	else if (code == BINDER::DISPATCH_IME_INPUT)
		mFLUIDService->dispatchIMEInput(parcel);
	else if (code == BINDER::STORE_CACHE)
		mFLUIDService->storeCache(parcel);
	else if (code == BINDER::NOTIFY_REMOTE_CRASH)
		mFLUIDService->notifyRemoteCrash(parcel);
	else if (code == BINDER::GET_CACHE)
		mFLUIDService->getCache(parcel);
	delete parcel;
}

void FLUIDService::registerAppBinders(sp<IBinder> appThread, sp<IBinder> token) {
	FLUIDLOG("registerAppToken()");
	mAppThread = appThread;
	mAppHandle = mAppThread->remoteBinder()->handle();
	mAppToken = token;
}

int FLUIDService::sendUi(Parcel& request) {
	FLUIDLOG("sendUi()");
	if (mIsHostDevice) {
		mConnManager.sendParcel(mConnManager.mClientSock, BINDER::RESTORE_UI, request);
		if (mConnManager.mHeartbeatThread == nullptr) {
			mConnManager.mHeartbeatThread = 
				new ConnManager::HeartbeatThread(mConnManager.mClientSock, mConnManager.mSenderThread.getHandler(), this);
		}
	}
	return 0;
}

int FLUIDService::sendRpc(Parcel& request, Parcel* reply) {
	FLUIDLOG("sendRpc()");
	if (mIsHostDevice) {
		mConnManager.sendParcel(mConnManager.mClientSock, BINDER::EXECUTE_RPC, request);
		reply->writeNoException();
		reply->writeInt32(0);
	}
	else {
		mReply = reply;
		mConnManager.sendParcel(mConnManager.mServerSock, BINDER::EXECUTE_RPC, request);
		mReplySema.wait();
	}
	return 0;
}

int FLUIDService::sendInput(Parcel& request) {
	FLUIDLOG("sendInput()");
	if (mIsHostDevice)
		mConnManager.sendParcel(mConnManager.mClientSock, BINDER::DISPATCH_INPUT, request);
	else 
		mConnManager.sendParcel(mConnManager.mServerSock, BINDER::DISPATCH_INPUT, request);
	return 0;
}

int FLUIDService::sendIMEInput(Parcel& request) {
	FLUIDLOG("sendIMEInput()");
	if (mIsHostDevice)
		mConnManager.sendParcel(mConnManager.mClientSock, BINDER::DISPATCH_IME_INPUT, request);
	else
		mConnManager.sendParcel(mConnManager.mServerSock, BINDER::DISPATCH_IME_INPUT, request);
	return 0;
}

int FLUIDService::sendCache(Parcel& request) {
	FLUIDLOG("sendCache()");
	if (mIsHostDevice) {
		mConnManager.sendParcel(mConnManager.mClientSock, BINDER::STORE_CACHE, request);
	}
	return 0;
}

int FLUIDService::restoreUi(Parcel* request) {
	FLUIDLOG("restoreUi()");

	if (mSystemUiThread != nullptr)
		mSystemUiThread->unlinkToDeath(mDeathNotifier);

	// Launch the FLUID proxy app
	mIsWaiting = true;
	Parcel newRequest, reply;
	newRequest.writeInterfaceToken(String16("android.app.IActivityManager"));
	IPCThreadState::self()->transact(mAmHandle, AM::START_FLUID_ACTIVITY_TRANSACTION, newRequest, &reply, REPLY_ON);
	mSystemUiThread = readBinder(reply, 0);
	mSystemUiHandle = mSystemUiThread->remoteBinder()->handle();
	mActivitySema.wait();
	mSystemUiThread->linkToDeath(mDeathNotifier);

	// Request UI restoration to system UI via IApplicationThread binder object
	request->setDataPosition(request->dataSize());
	request->writeStrongBinder(mSystemUiToken);
	IPCThreadState::self()->transact(mSystemUiHandle, AT::RESTORE_UI_TRANSACTION, *request, nullptr, REPLY_OFF);

	return 0;
}

int FLUIDService::executeRpc(Parcel* request) {
	FLUIDLOG("executeRpc()");
	Parcel reply;
	request->setDataPosition(request->dataSize());
	if (mIsHostDevice) {
		request->writeStrongBinder(mAppToken);
		IPCThreadState::self()->transact(mAppHandle, AT::EXECUTE_RPC_TRANSACTION, 
				*request, &reply, REPLY_ON);
		mConnManager.sendReply(mConnManager.mClientSock, reply);
	}
	else {
		request->writeStrongBinder(mSystemUiToken);
		IPCThreadState::self()->transact(mSystemUiHandle, AT::EXECUTE_RPC_TRANSACTION, 
				*request, nullptr, REPLY_OFF);
	}
	return 0;
}

int FLUIDService::dispatchInput(Parcel* request) {
	FLUIDLOG("dispatchInput()");
	request->setDataPosition(request->dataSize());
	if (mIsHostDevice) {
		request->writeStrongBinder(mAppToken);
		IPCThreadState::self()->transact(mAppHandle, AT::DISPATCH_INPUT_TRANSACTION, *request, nullptr, REPLY_OFF);
	}
	else {
		request->writeStrongBinder(mSystemUiToken);
		IPCThreadState::self()->transact(mSystemUiHandle, AT::DISPATCH_INPUT_TRANSACTION, *request, nullptr, REPLY_OFF);
	}
	return 0;
}

int FLUIDService::dispatchIMEInput(Parcel* request) {
	FLUIDLOG("dispatchIMEInput()");
	request->setDataPosition(request->dataSize());
	if (mIsHostDevice) {
		request->writeStrongBinder(mAppToken);
		IPCThreadState::self()->transact(mAppHandle, AT::DISPATCH_IME_INPUT_TRANSACTION, *request, nullptr, REPLY_OFF);
	}
	else {
		request->writeStrongBinder(mSystemUiToken);
		IPCThreadState::self()->transact(mSystemUiHandle, AT::DISPATCH_IME_INPUT_TRANSACTION, *request, nullptr, REPLY_OFF);
	}
	return 0;
}

int FLUIDService::storeCache(Parcel* request) {
	FLUIDLOG("storeCache()");
	request->setDataPosition(request->dataSize());
	IPCThreadState::self()->transact(mSystemUiHandle, AT::STORE_CACHE_TRANSACTION, *request, nullptr, REPLY_OFF);
	return 0;
}

void FLUIDService::registerApp() {
	sendApk("com.example.simpleui");
	//sendApk("com.mobiledui.experiment1");
	
	sendApk("com.simplemobiletools.musicplayer");
	sendApk("com.paypal.android.p2pmobile");
	sendApk("com.socialnmobile.dictapps.notepad.color.note");
	sendApk("com.byteexperts.texteditor");
	sendApk("com.instagram.android");
	sendApk("com.threestar.gallery");
	sendApk("com.atomicadd.fotos");
	sendApk("com.sp.protector.free");
	sendApk("com.alpha.applock");
	sendApk("org.videolan.vlc");
	sendApk("com.nhn.android.nmap");
	sendApk("com.mapswithme.maps.pro");
	sendApk("com.ebay.mobile");
	sendApk("com.booking");
	sendApk("com.cmcm.live");
	sendApk("kr.co.nowcom.mobile.afreeca");
	sendApk("com.eyewind.paperone");
	sendApk("com.ennesoft.paint");
	sendApk("com.hmobile.biblekjv");
	sendApk("com.sharpened.androidfileviewer");
}

void FLUIDService::onActivityLaunched(sp<IBinder> token) {
	if (mIsWaiting) {
		mSystemUiToken = token;
		mActivitySema.signal();
		mIsWaiting = false;
	}
}

bool FLUIDService::isHostDevice() {
	return mIsHostDevice;
}

void FLUIDService::notifyAppCrash(sp<IBinder> appThread) {
	if (appThread != nullptr) {
		mIsCrash = true;
		return;
	}

	if (mIsCrash) {
		mIsCrash = false;
		FLUIDLOG("notifyAppCrash()");
		Parcel notify;
		notify.writeInterfaceToken(String16("android.fluid.IFLUIDService"));
		if (mIsHostDevice)
			mConnManager.sendParcel(mConnManager.mClientSock, BINDER::NOTIFY_REMOTE_CRASH, notify);
		else 
			mConnManager.sendParcel(mConnManager.mServerSock, BINDER::NOTIFY_REMOTE_CRASH, notify);
	}
}

void FLUIDService::notifyRemoteCrash(Parcel* request) {
	FLUIDLOG("notifyRemoteCrash()");
	request->setDataPosition(request->dataSize());
	if (mIsHostDevice) {
		request->writeStrongBinder(mAppToken);
		IPCThreadState::self()->transact(mAppHandle, AT::NOTIFY_REMOTE_CRASH_TRANSACTION, *request, nullptr, REPLY_OFF);
	}
	else {
		request->writeStrongBinder(mSystemUiToken);
		IPCThreadState::self()->transact(mSystemUiHandle, AT::NOTIFY_REMOTE_CRASH_TRANSACTION, *request, nullptr, REPLY_OFF);
	}
}

void FLUIDService::notifyNetworkFault() {
	FLUIDLOG("notifyNetworkFault()");
	if (mIsHostDevice) {
		Parcel notify;
		notify.writeInterfaceToken(String16("android.fluid.IFLUIDService"));
		notify.writeStrongBinder(mAppToken);
		IPCThreadState::self()->transact(mAppHandle, AT::NOTIFY_NETWORK_FAULT_TRANSACTION, notify, nullptr, REPLY_OFF);
	}
}

int FLUIDService::requestCache(Parcel& request, Parcel* reply) {
	FLUIDLOG("requestCache()");
	if (!mIsHostDevice) {
		mReply = reply;
		mConnManager.sendParcel(mConnManager.mServerSock, BINDER::GET_CACHE, request);
		mReplySema.wait();
	}
	return 0;
}

int FLUIDService::getCache(Parcel* request) {
	FLUIDLOG("getCache()");
	Parcel reply;
	request->setDataPosition(request->dataSize());
	IPCThreadState::self()->transact(mAppHandle, AT::GET_CACHE_TRANSACTION, *request, &reply, REPLY_ON);
	mConnManager.sendReply(mConnManager.mClientSock, reply);
	return 0;
}

void FLUIDService::sendApk(const char* packageName) {
	char fileName[100] = "/data/app/";
	DIR *pDir = opendir("/data/app/");
	dirent *pFile = NULL;

	if (pDir) {
		bool found = false;
		while ((pFile = readdir(pDir)) != NULL) {
			if (equals(pFile->d_name, packageName)) {
				found = true;
				break;
			}
		}
		if (!found) return;
		strcat(fileName, pFile->d_name);
		strcat(fileName, "/base.apk");

		int32_t fd = open(fileName, O_RDONLY), bytes = 0;
		uint32_t fileSize = lseek(fd, 0, SEEK_END);
		lseek(fd, 0, SEEK_SET);
		mConnManager.sendFile(mConnManager.mClientSock, fd, fileSize, packageName);
		close(fd);
		closedir(pDir);
	}
}

bool FLUIDService::equals(char *dirName, const char *pkgName) {
	const char *ptr1 = dirName, *ptr2 = pkgName;

	while (*ptr2 != '\0') {
		if (*ptr2 != *ptr1)
			return false;
		ptr1++;
		ptr2++;
	}
	return true;
}

sp<IBinder> FLUIDService::readBinder(Parcel &parcel, int index) {
	const size_t* objects = (size_t*) parcel.objects();
	parcel.setDataPosition(objects[0]);
	return parcel.readStrongBinder();
}

status_t FLUIDService::onTransact(uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags) {
	return BnFLUIDService::onTransact(code, data, reply, flags);
}

};
