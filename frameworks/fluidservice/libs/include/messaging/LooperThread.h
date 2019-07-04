/*
 * Copyright (C) 2011 Daniel Himmelein
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

#ifndef MESSAGING_LOOPERTHREAD_H
#define MESSAGING_LOOPERTHREAD_H

#include <messaging/Utils.h>
#include <messaging/Thread.h>
#include <messaging/Looper.h>
#include <messaging/Handler.h>
#include <messaging/Lock.h>
#include <messaging/CondVar.h>

namespace android {
namespace messaging {

/*
 * Handy class for starting a new Thread that has a Looper and a Handler.
 * See LooperThreadExample.cpp for details.
 * T must be a subclass of class Handler.
 */
template<class T /*extends Handler*/>
class LooperThread :
	public Thread
{
public:
	LooperThread() :
		mLooper(NULL),
		mHandler(NULL),
		mCondVar(mLock),
		mIsDone(false) {
	}

	virtual ~LooperThread() {
	}

	virtual void run() {
		Looper::prepare();
		mLock.lock();
		mLooper = Looper::myLooper();
		mHandler = new T();
		mCondVar.notifyAll();
		mLock.unlock();
		Looper::loop();
		mLock.lock();
		mIsDone = true;
		mHandler->removeCallbacksAndMessages();
		delete mHandler;
		mLooper = NULL;
		mHandler = NULL;
		mLock.unlock();
	}

	Looper* getLooper() {
		AutoLock autoLock(mLock);
		if (!mIsDone && mLooper == NULL) {
			mCondVar.wait();
		}
		return mLooper;
	}

	T* getHandler() {
		AutoLock autoLock(mLock);
		if (!mIsDone && mHandler == NULL) {
			mCondVar.wait();
		}
		return mHandler;
	}

private:
	Looper* mLooper;
	T* mHandler;
	Lock mLock;
	CondVar mCondVar;
	bool mIsDone;

	NO_COPY_CTOR_AND_ASSIGNMENT_OPERATOR(LooperThread)
};

} /* namespace messaging */
} /* namespace android */

#endif 
