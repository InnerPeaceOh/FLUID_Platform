#include <fluid/ConnManager.h>
#include <fluid/FLUIDService.h>

namespace android {

ConnManager::ConnManager(FLUIDService* service) : mListenThread(this) {
	mFLUIDService = service;
}

void ConnManager::runSenderThread() {
	FLUIDLOG("runSenderThread()");
	mSenderThread.run("SenderThread");
}

void ConnManager::runListenThread() {
	FLUIDLOG("runListenThread()");
	mListenThread.run("ListenThread");
}

void ConnManager::connectToServer(const char* serverIp) {
	FLUIDLOG("connectToServer(), serverIp = %s", serverIp);
	int sock = socket(AF_INET, SOCK_STREAM, 0);
	struct sockaddr_in serverAddr;
	serverAddr.sin_family = AF_INET;
	serverAddr.sin_addr.s_addr = inet_addr(serverIp);
	serverAddr.sin_port = htons(LISTEN_PORT);
	while(1) {
		if (connect(sock, (struct sockaddr *)&serverAddr, sizeof(serverAddr)) < 0) {
			FLUIDLOG("connect() is failed with err %d.", errno);
			sleep(1);
			continue;
		}
		else {
			FLUIDLOG("Connectted to %s with sock %d.", serverIp, sock);
			break;
		}
	}
	mListenThread.mSocks[sock].fd = sock;
	mListenThread.mSocks[sock].events = POLLIN;
	mListenThread.mIps[sock] = serverIp;
	mServerSock = sock; 
}

void ConnManager::sendParcel(int sock, int code, const Parcel& parcel) {
	int type = MSG::PARCEL_TYPE, ashmemFd = 0;
	size_t parcelSize = parcel.dataSize(), refCount = parcel.objectsCount(), ashmemSize = 0;
	const uint8_t *parcelData = parcel.data();
	const size_t *refData = (size_t*)parcel.objects();
	int msgLen = MSG::PARCEL_TYPE_MSGLEN + parcelSize + refCount * sizeof(size_t);
	if (refCount) {
		parcel.setDataPosition(refData[0]);
		ashmemFd = dup(parcel.readFileDescriptor());
		ashmemSize = ashmem_get_size_region(ashmemFd);
		msgLen += (sizeof(size_t) + ashmemSize);
	}

	char *buffer = new char[msgLen], *ptr = buffer;
	memcpy(ptr, &type, sizeof(int)); ptr += sizeof(int);
	memcpy(ptr, &msgLen, sizeof(int)); ptr += sizeof(int);
	memcpy(ptr, &code, sizeof(int)); ptr += sizeof(int);
	memcpy(ptr, &parcelSize, sizeof(size_t)); ptr += sizeof(size_t);
	memcpy(ptr, parcelData, parcelSize); ptr += parcelSize;
	memcpy(ptr, &refCount, sizeof(size_t)); ptr += sizeof(size_t);
	if (refCount) {
		memcpy(ptr, refData, refCount * sizeof(size_t)); 
		ptr += (refCount * sizeof(size_t));
		memcpy(ptr, &ashmemSize, sizeof(size_t)); ptr += sizeof(size_t);
		uint8_t *base = (uint8_t*) mmap(NULL, ashmemSize, PROT_READ, MAP_SHARED, ashmemFd, 0);
		memcpy(ptr, base, ashmemSize); ptr += ashmemSize;
		close(ashmemFd);
		munmap((void*) base, ashmemSize);
	}

	SenderHandler *handler = mSenderThread.getHandler();
	Message& msg = handler->obtainMessage(sock, msgLen, 0);
	msg.obj = buffer;
	handler->sendMessage(msg);
}

void ConnManager::sendFile(int sock, int fd, uint32_t fileSize, const char *fileName) {
	FLUIDLOG("sendFile(), sock = %d, fileSize = %d, fd = %d, fileName = %s", sock, fileSize, fd, fileName);
	int type = MSG::FILE_TYPE, fileNameLen = strlen(fileName), 
			msgLen = MSG::FILE_TYPE_MSGLEN + fileNameLen + fileSize, readBytes = 0;
	char *buffer = new char[msgLen], *ptr = buffer;

	memcpy(ptr, &type, sizeof(int)); ptr += sizeof(int);
	memcpy(ptr, &msgLen, sizeof(int)); ptr += sizeof(int);
	memcpy(ptr, &fileSize, sizeof(uint32_t)); ptr += sizeof(uint32_t);
	while((readBytes = read(fd, ptr, 1024 * 128)) > 0)
		ptr += readBytes;
	memcpy(ptr, &fileNameLen, sizeof(int)); ptr += sizeof(int);
	memcpy(ptr, fileName, fileNameLen); ptr += fileNameLen;

	SenderHandler *handler = mSenderThread.getHandler();
	Message& msg = handler->obtainMessage(sock, msgLen, 0);
	msg.obj = buffer;
	handler->sendMessage(msg);
}

void ConnManager::sendReply(int sock, const Parcel &reply) {
	int type = MSG::REPLY_TYPE, ashmemFd = 0;
	size_t replySize = reply.dataSize(), refCount = reply.objectsCount(), ashmemSize = 0;
	const uint8_t *replyData = reply.data();
	const size_t *refData = (size_t*)reply.objects();
	int msgLen = MSG::REPLY_TYPE_MSGLEN + replySize + refCount * sizeof(size_t);
	if (refCount) {
		reply.setDataPosition(refData[0]);
		ashmemFd = dup(reply.readFileDescriptor());
		ashmemSize = ashmem_get_size_region(ashmemFd);
		msgLen += (sizeof(size_t) + ashmemSize);
	}

	char *buffer = new char[msgLen], *ptr = buffer;
	memcpy(ptr, &type, sizeof(int)); ptr += sizeof(int);
	memcpy(ptr, &msgLen, sizeof(int)); ptr += sizeof(int);
	memcpy(ptr, &replySize, sizeof(size_t)); ptr += sizeof(size_t);
	memcpy(ptr, replyData, replySize); ptr += replySize;
	memcpy(ptr, &refCount, sizeof(size_t)); ptr += sizeof(size_t);
	if (refCount) {
		memcpy(ptr, refData, refCount * sizeof(size_t)); 
		ptr += (refCount * sizeof(size_t));
		memcpy(ptr, &ashmemSize, sizeof(size_t)); ptr += sizeof(size_t);
		uint8_t *base = (uint8_t*) mmap(NULL, ashmemSize, PROT_READ, MAP_SHARED, ashmemFd, 0);
		memcpy(ptr, base, ashmemSize); ptr += ashmemSize;
		close(ashmemFd);
		munmap((void*) base, ashmemSize);
	}

	SenderHandler *handler = mSenderThread.getHandler();
	Message& msg = handler->obtainMessage(sock, msgLen, 0);
	msg.obj = buffer;
	handler->sendMessage(msg);
}

void ConnManager::receiveParcel(int sock) {
	int msgLen = 0, bufferLen = 0, code = 0, ashmemFd = 0;
	size_t parcelSize = 0, refCount = 0, ashmemSize = 0;
	char *buffer = NULL, *ptr = NULL;
	uint8_t *parcelData = NULL;
	size_t *refData = NULL;

	recv(sock, &msgLen, sizeof(int), MSG_WAITALL);
	bufferLen = msgLen - 8; // already read 8 bytes, i.e., type & msgLen
	buffer = new char[bufferLen];
	ptr = buffer;
	
	int recvLen = recv(sock, buffer, bufferLen, MSG_WAITALL);
	memcpy(&code, ptr, sizeof(int)); ptr += sizeof(int);
	memcpy(&parcelSize, ptr, sizeof(size_t)); ptr += sizeof(size_t);
	parcelData = new uint8_t[parcelSize];
	memcpy(parcelData, ptr, parcelSize); ptr += parcelSize;
	memcpy(&refCount, ptr, sizeof(size_t)); ptr += sizeof(size_t);
	if (refCount) {
		refData = new size_t[refCount];
		memcpy(refData, ptr, refCount * sizeof(size_t));
		ptr += (refCount * sizeof(size_t));

		memcpy(&ashmemSize, ptr, sizeof(size_t)); ptr += sizeof(size_t);
		ashmemFd = ashmem_create_region("Parcel Blob", ashmemSize);
		ashmem_set_prot_region(ashmemFd, PROT_READ | PROT_WRITE);
		uint8_t *base = (uint8_t*) mmap(NULL, ashmemSize, 
								PROT_READ | PROT_WRITE, MAP_SHARED, ashmemFd, 0);
		memcpy(base, ptr, ashmemSize); ptr += ashmemSize;
		munmap((void*) base, ashmemSize); 
	}

	Parcel *parcel = new Parcel();
	parcel->setData(parcelData, parcelSize);
	if (refCount) {
		parcel->setDataPosition(refData[0]);
		parcel->writeDupFileDescriptor(ashmemFd);
		parcel->setDataPosition(0);
		parcel->setObjects(refData, refCount);
		close(ashmemFd);
	}

	delete[] parcelData;
	delete[] refData;
	delete[] buffer;

	FLUIDService::MainHandler *handler = mFLUIDService->mMainThread.getHandler();
	Message& msg = handler->obtainMessage(code);
	msg.obj = parcel;
	handler->sendMessage(msg);
}

void ConnManager::receiveFile(int sock) {
	int msgLen = 0, fileNameLen = 0, bufferLen = 0;
	uint32_t fileSize = 0;
	char *buffer = NULL, *ptr = NULL, *filePos = NULL;
	char *fileName = NULL;

	recv(sock, &msgLen, sizeof(int), MSG_WAITALL);
	bufferLen = msgLen - 8; // already read 8 bytes, i.e., type & msgLen
	buffer = new char[bufferLen];
	ptr = buffer;

	int recvLen = recv(sock, buffer, bufferLen, MSG_WAITALL);
	memcpy(&fileSize, ptr, sizeof(uint32_t)); ptr += sizeof(uint32_t);
	filePos = ptr; ptr += fileSize;
	memcpy(&fileNameLen, ptr, sizeof(int)); ptr += sizeof(int);
	if (fileNameLen) {
		fileName = new char[fileNameLen + 1];
		memcpy(fileName, ptr, fileNameLen);
		ptr += fileNameLen;
		fileName[fileNameLen] = '\0';
	}

	char filePath[500] = "/data/data/com.fluid.wrapperapp/app_fluid_apk/";
	mkdir(filePath, 0777);
	strcat(filePath, fileName);
	mkdir(filePath, 0777);
	strcat(filePath, "/base.apk");
	int fd = open(filePath, O_WRONLY | O_CREAT | O_TRUNC, 0777);
	write(fd, filePos, fileSize);
	close(fd);
	FLUIDLOG("receiveFile(), fileSize = %d", fileSize);

	delete[] fileName;
	delete[] buffer;
}

void ConnManager::receiveReply(int sock) {
	int msgLen = 0, bufferLen = 0, ashmemFd = 0;
	size_t replySize = 0, refCount = 0, ashmemSize = 0;
	char *buffer = NULL, *ptr = NULL;
	uint8_t *replyData = NULL;
	size_t *refData = NULL;

	recv(sock, &msgLen, sizeof(int), MSG_WAITALL);
	bufferLen = msgLen - 8; // already read 8 bytes, i.e., type & msgLen
	buffer = new char[bufferLen];
	ptr = buffer;
	
	int recvLen = recv(sock, buffer, bufferLen, MSG_WAITALL);
	memcpy(&replySize, ptr, sizeof(size_t)); ptr += sizeof(size_t);
	replyData = new uint8_t[replySize];
	memcpy(replyData, ptr, replySize); ptr += replySize;
	memcpy(&refCount, ptr, sizeof(size_t)); ptr += sizeof(size_t);
	if (refCount) {
		refData = new size_t[refCount];
		memcpy(refData, ptr, refCount * sizeof(size_t));
		ptr += (refCount * sizeof(size_t));

		memcpy(&ashmemSize, ptr, sizeof(size_t)); ptr += sizeof(size_t);
		ashmemFd = ashmem_create_region("Parcel Blob", ashmemSize);
		ashmem_set_prot_region(ashmemFd, PROT_READ | PROT_WRITE);
		uint8_t *base = (uint8_t*) mmap(NULL, ashmemSize, 
								PROT_READ | PROT_WRITE, MAP_SHARED, ashmemFd, 0);
		memcpy(base, ptr, ashmemSize); ptr += ashmemSize;
		munmap((void*) base, ashmemSize); 
	}

	Parcel *reply = mFLUIDService->mReply;
	reply->setData(replyData, replySize);
	if (refCount) {
		reply->setDataPosition(refData[0]);
		reply->writeDupFileDescriptor(ashmemFd);
		reply->setDataPosition(0);
		reply->setObjects(refData, refCount);
		close(ashmemFd);
	}
	mFLUIDService->mReplySema.signal();

	delete[] replyData;
	delete[] refData;
	delete[] buffer;
}

ConnManager::ListenThread::ListenThread(ConnManager *connManager) {
	mConnManager = connManager;
	mListenSock = 0;
}

bool ConnManager::ListenThread::threadLoop() {
	FLUIDLOG("ListenThread::threadLoop()");

    if (!openListenSock()) {
		FLUIDLOG("ListenThread::threadLoop(), Failed to open listen socket");
		return false;
    }
	else {
		mSocks[0].fd = mListenSock;
		mSocks[0].events = POLLIN;
	}
	while(!Thread::exitPending()) {
		int res = poll(mSocks, MAX_SOCK_NUM, POLL_TIMEOUT);
		if (mSocks[0].revents & POLLIN) {
			struct sockaddr_in clientAddr;
			socklen_t clientLen = sizeof(clientAddr);
			int clientSock = accept(mListenSock, (struct sockaddr*)&clientAddr, &clientLen);
			if (clientSock < 0) {
				FLUIDLOG("ListenThread::threadLoop(), Failed accept on socket %d with err %d", mListenSock, errno);
			} else {
				if (!setNodelay(clientSock))
					cleanupSock(clientSock);
				else {
					FLUIDLOG("ListenThread::threadLoop(), New client is connected with sock = %d", clientSock);
					mSocks[clientSock].fd = clientSock;
					mSocks[clientSock].events = POLLIN;
					mIps[clientSock] = inet_ntoa(clientAddr.sin_addr);

					mConnManager->mClientSock = clientSock;
					mConnManager->mFLUIDService->registerApp();
				}
			}
			if (--res <= 0)
				continue;
		}
		for (int i = 1; i < MAX_SOCK_NUM; i++) {
			if (mSocks[i].fd < 0)
				continue;
			if (mSocks[i].revents & POLLIN) {
				if (!receiveMessage(mSocks[i].fd)) 
					cleanupSock(mSocks[i].fd);
				if (--res <= 0)
					break;
			}
		}
	}
	return false;
}

bool ConnManager::ListenThread::receiveMessage(int sock) {
	int type = 0, strLen = 0;
	int recvLen = recv(sock, &type, sizeof(int), MSG_WAITALL);
	switch (type) {
		case MSG::PARCEL_TYPE: {
			mConnManager->receiveParcel(sock);
		} break;
		case MSG::FILE_TYPE: {
			mConnManager->receiveFile(sock);
		} break;
		case MSG::REPLY_TYPE: {
			mConnManager->receiveReply(sock);
		} break;
		case MSG::HEARTBEAT_TYPE: {
			mConnManager->receiveHeartbeat(sock);
		} break;
		default: {
			FLUIDLOG("receiveMessage(), Peer has closed the socket. sock = %d", sock);
			cleanupSock(sock);
		}
	}
	return true;
}

bool ConnManager::ListenThread::openListenSock() {
    bool ret = false;
    struct sockaddr_in addr;
    cleanupSock(mListenSock);
    if ((mListenSock = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP)) < 0) {
		FLUIDLOG("ListenThread::openListenSock(), socket() is failed with err %d", errno);
        goto bailout;
    }
    if (!setNonblocking(mListenSock) || !setReuseaddr(mListenSock))
        goto bailout;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = INADDR_ANY;
    addr.sin_port = htons(LISTEN_PORT);
    if (bind(mListenSock, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
		FLUIDLOG("ListenThread::openListenSock(), bind() is failed with err %d", errno);
        goto bailout;
    }
    if (listen(mListenSock, 5) < 0) {
		FLUIDLOG("ListenThread::openListenSock(), listen() is failed with err %d", errno);
        goto bailout;
    }
    ret = true;
bailout:
    if (!ret)
        cleanupSock(mListenSock);
	return ret;
}

void ConnManager::ListenThread::cleanupSock(int sock) {
	FLUIDLOG("ListenThread::cleanupSock(), sock = %d", sock);
    if (sock >= 0) {
        shutdown(sock, SHUT_RDWR);
        close(sock);
		mSocks[sock].fd = -1;
		mIps[sock] = NULL;
    }
}

bool ConnManager::ListenThread::setNonblocking(int sock) {
    int flags = fcntl(sock, F_GETFL);
    if (fcntl(sock, F_SETFL, flags | O_NONBLOCK) < 0) {
		FLUIDLOG("ListenThread::setNonblocking(), Failed to set socket (%d) to non-blocking mode (errno %d)", sock, errno);
        return false;
    }
    return true;
}

bool ConnManager::ListenThread::setNodelay(int sock) {
    int tmp = 1;
    if (setsockopt(sock, IPPROTO_TCP, TCP_NODELAY, &tmp, sizeof(tmp)) < 0) {
		FLUIDLOG("ListenThread::setNodelay(), Failed to set socket (%d) to no-delay mode (errno %d)", sock, errno);
        return false;
    }
    return true;
}

bool ConnManager::ListenThread::setReuseaddr(int sock) {
	int sockopt = 1;
	if(setsockopt(sock, SOL_SOCKET, SO_REUSEADDR, (char*) &sockopt, sizeof(sockopt)) == -1) {
		FLUIDLOG("ListenThread::setReuseaddr(), Failed to set socket (%d) to SO_REUSEADDR (errno %d)", sock, errno);
        return false;
	}
	return true;
}

void ConnManager::SenderHandler::handleMessage(Message &message) {
	int sock = message.what;
	char* buffer = (char*) message.obj;
	int msgLen = message.arg1;
	int flags = message.arg2;
	int sendLen = send(sock, buffer, msgLen, flags);
	delete[] buffer;
}

ConnManager::SenderThread::SenderThread() : mCondVar(mLock) {}

bool ConnManager::SenderThread::threadLoop() {
	FLUIDLOG("SenderThread::threadLoop()");
	Looper::prepare();
	mLooper = Looper::myLooper();
	mHandler = new SenderHandler();
	mCondVar.notifyAll();
	Looper::loop();
	return false;
}

ConnManager::SenderHandler* ConnManager::SenderThread::getHandler() {
	AutoLock autoLock(mLock);
	if (mHandler == NULL) {
		mCondVar.wait();
	}
	return mHandler;
}

int64_t ConnManager::getNanoTime() {
	const int64_t NSPS = 1000000000;
	struct timespec ts;
	int status = clock_gettime(CLOCK_MONOTONIC, &ts);
	int64_t nsec = static_cast<int64_t>(ts.tv_nsec);
	int64_t sec  = static_cast<int64_t>(ts.tv_sec);
	int64_t total_ns = nsec + (sec * NSPS);
	return total_ns;
}

void ConnManager::doNtp(const char* ipAddr) {
	struct sockaddr_in serverAddr;
	int ntpSock = socket(AF_INET, SOCK_STREAM, 0);
	serverAddr.sin_family = AF_INET;
	serverAddr.sin_addr.s_addr = inet_addr(ipAddr);
	serverAddr.sin_port = htons(NTP_PORT);
    int tmp = 1;
    setsockopt(ntpSock, IPPROTO_TCP, TCP_NODELAY, &tmp, sizeof(tmp));
	while(1) {
		if (connect(ntpSock, (struct sockaddr *)&serverAddr, sizeof(serverAddr)) < 0)
		{
			FLUIDLOG("doNtp() Connect failed with err %d.", errno);
			sleep(1);
			continue;
		}
		else 
			break;
	}

	double avgDelta = 0.0;
	for (int i = 0; i < 24; i++) {
		int64_t t0 = getNanoTime(), t1, t2, t3;
		double delta = 0.0;
		send(ntpSock, &t0, sizeof(uint64_t), 0);
		recv(ntpSock, &t1, sizeof(uint64_t), MSG_WAITALL);
		recv(ntpSock, &t2, sizeof(uint64_t), MSG_WAITALL);
		t3 = getNanoTime();
		delta = ((t1 - t0) + (t2 - t3)) / 2.0;
		if (i > 1 && i < 22)
			avgDelta += delta;
	}
	avgDelta /= 20;
	FLUIDLOG("doNtp(), average delta = %lf msec", avgDelta);
	close(ntpSock);
}

void ConnManager::listenNtp() {
	int ntpSock = socket(AF_INET, SOCK_STREAM, IPPROTO_TCP);
	struct sockaddr_in addr;
	int sockopt = 1;
	setsockopt(ntpSock, SOL_SOCKET, SO_REUSEADDR, (char*) &sockopt, sizeof(sockopt));
	memset(&addr, 0, sizeof(addr));
	addr.sin_family = AF_INET;
	addr.sin_addr.s_addr = INADDR_ANY;
	addr.sin_port = htons(NTP_PORT);
	if (bind(ntpSock, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
		FLUIDLOG("listenNtp() Bind failed with err %d", errno);
	}
	if (listen(ntpSock, 5) < 0) {
		FLUIDLOG("listenNtp() Listen failed with err %d", errno);
	}
	struct sockaddr_in clientAddr;
	socklen_t clientLen = sizeof(clientAddr);
	int clientSock = accept(ntpSock, (struct sockaddr*)&clientAddr, &clientLen);
	while(1) {
		int64_t t0 = 0;
		ssize_t recvBytes = recv(clientSock, &t0, sizeof(uint64_t), MSG_WAITALL);
		int64_t t1 = getNanoTime();
		if (recvBytes == 0) {
			break;
		}
		send(clientSock, &t1, sizeof(uint64_t), 0);
		int64_t t2 = getNanoTime();
		send(clientSock, &t2, sizeof(uint64_t), 0);
		FLUIDLOG("listenNtp(), t0 = %llu, t1 = %llu, t2 = %llu", t0, t1, t2);
	}
	close(ntpSock);
}

bool ConnManager::HeartbeatThread::threadLoop() {
	FLUIDLOG("HeartbeatThread::threadLoop()");
	while(!Thread::exitPending()) {
		int type = MSG::HEARTBEAT_TYPE, msgLen = MSG::HEARTBEAT_TYPE_MSGLEN;
		char *buffer = new char[msgLen], *ptr = buffer;
		memcpy(ptr, &type, sizeof(int)); ptr += sizeof(int);
		memcpy(ptr, &mId, sizeof(int));
		Message& msg = mHandler->obtainMessage(mSock, msgLen, 0);
		msg.obj = buffer;
		mHandler->sendMessage(msg);
		sleep(mTimeout);

		AutoLock autoLock(mLock);
		if (!mReceived) {
			FLUIDLOG("Network disconnection!");
			mFLUIDService->notifyNetworkFault();
			return false;
		}
		mReceived = false;
		mId++;
	}
	return false;
}

void ConnManager::receiveHeartbeat(int sock) {
	int type = MSG::HEARTBEAT_TYPE, msgLen = MSG::HEARTBEAT_TYPE_MSGLEN;
	int recvLen = msgLen - 4;
	char *buffer = new char[recvLen], *ptr = buffer;
	int id = -1;
	recv(sock, buffer, recvLen, MSG_WAITALL);
	memcpy(&id, ptr, sizeof(int));
	delete buffer;

	if (mFLUIDService->mIsHostDevice) {
		AutoLock autoLock(mHeartbeatThread->mLock);
		mHeartbeatThread->mReceived = true;
	}
	else {
		buffer = new char[msgLen];
		ptr = buffer;
		memcpy(ptr, &type, sizeof(int)); ptr += sizeof(int);
		memcpy(ptr, &id, sizeof(int));
		SenderHandler *handler = mSenderThread.getHandler();
		Message& msg = handler->obtainMessage(sock, msgLen, 0);
		msg.obj = buffer;
		handler->sendMessage(msg);
	}
}

};
