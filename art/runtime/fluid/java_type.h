/**
 *   The MIT License (MIT)
 *   Copyright (C) 2016 ZongXian Shen <andy.zsshen@gmail.com>
 *
 *   Permission is hereby granted, free of charge, to any person obtaining a
 *   copy of this software and associated documentation files (the "Software"),
 *   to deal in the Software without restriction, including without limitation
 *   the rights to use, copy, modify, merge, publish, distribute, sublicense,
 *   and/or sell copies of the Software, and to permit persons to whom the
 *   Software is furnished to do so, subject to the following conditions:
 *
 *   The above copyright notice and this permission notice shall be included in
 *   all copies or substantial portions of the Software.
 *
 *   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *   FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 *   THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *   LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *   OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
 *   IN THE SOFTWARE.
 */

#if defined(__aarch64__) || defined(__arm__)
#ifndef _JAVA_TYPE_H_
#define _JAVA_TYPE_H_
#include "jni.h"
#include "jni_internal.h"

namespace art {
namespace fluid {
// Auxiliary buffer size.
static const int kBlahSize = 512;

class PrimitiveTypeWrapper
{
  public:
    PrimitiveTypeWrapper(jclass clazz, jmethodID meth_ctor, jmethodID meth_access)
     : clazz_(clazz),
       meth_ctor_(meth_ctor),
       meth_access_(meth_access) {}

    ~PrimitiveTypeWrapper();

    jclass GetClass() {
        return clazz_;
    }

    jmethodID GetConstructor() {
        return meth_ctor_;
    }

    jmethodID GetAccessor() {
        return meth_access_;
    }

    static void LoadWrappers();

  private:
    jclass clazz_;
    jmethodID meth_ctor_;
    jmethodID meth_access_;
};
}
}

#endif	
#endif
