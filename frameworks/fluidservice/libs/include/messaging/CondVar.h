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

#ifndef MESSAGING_CONDVAR_H
#define MESSAGING_CONDVAR_H

#include <stdint.h>
#include <pthread.h>
#include <messaging/Utils.h>

namespace android {
namespace messaging {

class Lock;

class CondVar {
public:
	CondVar(Lock& lock);
	~CondVar();
	void wait();
	void wait(uint32_t timeout);
	void wait(timespec& absTimestamp);
	void notify();
	void notifyAll();

private:
	pthread_cond_t mCondVar;
	pthread_condattr_t mCondVarAttributes;
	Lock& mCondVarLock;

	NO_COPY_CTOR_AND_ASSIGNMENT_OPERATOR(CondVar)
};

} /* namespace messaging */
} /* namespace android */

#endif 
