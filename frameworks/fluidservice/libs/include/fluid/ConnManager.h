#ifndef FLUID_CONNMANAGER_H
#define FLUID_CONNMANAGER_H

#include <utils/threads.h>
#include <cutils/ashmem.h>
#include <linux/in.h>
#include <linux/tcp.h>
#include <sys/socket.h>
#include <sys/mman.h>
#include <arpa/inet.h>
#include <fcntl.h>
#include <poll.h>
#include <binder/Parcel.h>
#include <fluid/Global.h>
#include <messaging/Looper.h>
#include <messaging/Handler.h>
#include <sys/stat.h>
#include <time.h>

#define LOG_FLUID "ConnManager"

using namespace android::messaging;

namespace android {
	
class FLUIDService;

class ConnManager 
{
public:
	ConnManager(FLUIDService* service);
	~ConnManager() {}
	void runSenderThread();
	void runListenThread();
	void connectToServer(const char* serverIp);
	void sendParcel(int sock, int code, const Parcel& parcel);
	void sendFile(int sock, int fd, uint32_t fileSize, const char *fileName);
	void sendReply(int sock, const Parcel &reply);
	void receiveParcel(int sock);
	void receiveFile(int sock);
	void receiveReply(int sock);
	void receiveHeartbeat(int sock);
	int64_t getNanoTime();
	void doNtp(const char* ipAddr);
	void listenNtp();

private:

	class ListenThread : public Thread
	{
		public:
			ListenThread(ConnManager* connManager);
			~ListenThread() {}
			virtual bool threadLoop();
			bool receiveMessage(int sock);
			bool openListenSock();
			void cleanupSock(int sock);
			bool setNonblocking(int sock);
			bool setNodelay(int sock);
			bool setReuseaddr(int sock);
			ConnManager *mConnManager;
			int mListenSock;
			struct pollfd mSocks[MAX_SOCK_NUM];
			const char* mIps[MAX_SOCK_NUM];
	};

	class SenderHandler : public Handler
	{
		public:
			SenderHandler() {}
			~SenderHandler() {}
			virtual void handleMessage(Message& message);
	};

	class SenderThread : public Thread
	{
		public:
			SenderThread();
			~SenderThread() {}
			virtual bool threadLoop();
			SenderHandler* getHandler();
			Looper* mLooper;
			SenderHandler* mHandler;
			Lock mLock;
			CondVar mCondVar;
	};

public:
	class HeartbeatThread : public Thread
	{
		public:
			HeartbeatThread(int sock, SenderHandler* handler, FLUIDService* service) {
				mSock = sock;
				mHandler = handler;
				mTimeout = 2;
				mId = 0;
				mReceived = false;
				mFLUIDService = service;
			}
			~HeartbeatThread() {}
			virtual bool threadLoop();
			int mSock;
			SenderHandler* mHandler;
			int mTimeout;
			int mId;
			bool mReceived;
			Lock mLock;
			FLUIDService* mFLUIDService;
	};

	ListenThread mListenThread;
	SenderThread mSenderThread;
	sp<HeartbeatThread> mHeartbeatThread;
	FLUIDService* mFLUIDService;
	int mServerSock = -1;	//temp
	int mClientSock = -1;	//temp
};

};
#endif
