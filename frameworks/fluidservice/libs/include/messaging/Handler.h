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

#ifndef MESSAGING_HANDLER_H
#define MESSAGING_HANDLER_H

#include <stdint.h>
#include <messaging/Utils.h>
#include <messaging/Message.h>
#include <messaging/Runnable.h>

namespace android {
namespace messaging {

class Looper;
class MessageQueue;

class Handler
{
public:
	Handler();
	Handler(Looper& looper);
	virtual ~Handler();

	Message& obtainMessage() {
		return Message::obtain(*this);
	}

	Message& obtainMessage(int32_t what) {
		return Message::obtain(*this, what);
	}

	Message& obtainMessage(int32_t what, int32_t arg1, int32_t arg2) {
		return Message::obtain(*this, what, arg1, arg2);
	}

	Message& obtainMessage(Runnable& callback) {
		return Message::obtain(*this, callback);
	}

	void dispatchMessage(Message& message);
	virtual void handleMessage(Message& message);
	bool sendMessage(Message& message);
	bool sendMessageDelayed(Message& message, uint32_t delay);
	bool sendMessageAtTime(Message& message, uint64_t execTimestamp);
	bool post(Runnable* runnable);
	bool postDelayed(Runnable* runnable, uint32_t delay);
	bool postAtTime(Runnable* runnable, uint64_t execTimestamp);
	bool removeMessages(int32_t what);
	bool removeCallbacks(Runnable* runnable);
	bool removeCallbacksAndMessages();

private:
	Message& getPostMessage(Runnable& runnable);

	void handleCallback(Message& message) {
		message.mCallback->run();
    }

	MessageQueue* mMessageQueue;

	NO_COPY_CTOR_AND_ASSIGNMENT_OPERATOR(Handler)
};

} /* namespace messaging */
} /* namespace android */

#endif
