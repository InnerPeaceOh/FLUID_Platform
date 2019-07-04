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

#ifndef MESSAGING_LOOPER_H
#define MESSAGING_LOOPER_H

#include <pthread.h>
#include <messaging/Utils.h>
#include <messaging/MessageQueue.h>

namespace android {
namespace messaging {

class Runnable;

class Looper
{
public:
	virtual ~Looper();
	static bool prepare();
	static bool prepare(Runnable* onLooperReadyRunnable);
	static Looper* myLooper();
	static void loop();
	void quit();
	MessageQueue& myMessageQueue() {
		return *mMessageQueue;
	}

private:
	Looper();
	static void init();
	static void finalize(void* looper);

	MessageQueue* mMessageQueue;
	Runnable* mOnLooperReadyRunnable;
	static pthread_once_t sTlsOneTimeInitializer;
	static pthread_key_t sTlsKey;

	NO_COPY_CTOR_AND_ASSIGNMENT_OPERATOR(Looper)
};

} /* namespace messaging */
} /* namespace android */

#endif
