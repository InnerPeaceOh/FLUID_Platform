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

#ifndef MESSAGING_LOCK_H
#define MESSAGING_LOCK_H

#include <stdint.h>
#include <pthread.h>
#include <messaging/Utils.h>

namespace android {
namespace messaging {

class Lock
{
public:
	Lock();
	~Lock();
	bool lock();
#ifndef __ANDROID__
	bool lock(uint32_t timeout);
#endif
	void unlock();

private:
	pthread_mutex_t mMutex;

	friend class CondVar;

	NO_COPY_CTOR_AND_ASSIGNMENT_OPERATOR(Lock)
};

class AutoLock
{
public:
	AutoLock(Lock& lock) :
		mLock(lock) {
		mLock.lock();
	}

	~AutoLock() {
		mLock.unlock();
	}

private:
	Lock& mLock;

	NO_COPY_CTOR_AND_ASSIGNMENT_OPERATOR(AutoLock)
};

} /* namespace messaging */
} /* namespace android */

#endif 
