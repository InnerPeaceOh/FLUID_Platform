/* Copyright (c) 2008, Nathan Sweet
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following
 * conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 * - Neither the name of Esoteric Software nor the names of its contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. */

package android.fluid.kryo.serializers;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.reflect.Method;

import android.fluid.kryo.Kryo;
import android.fluid.kryo.KryoException;
import android.fluid.kryo.Serializer;
import android.fluid.kryo.io.Input;
import android.fluid.kryo.io.KryoObjectInput;
import android.fluid.kryo.io.KryoObjectOutput;
import android.fluid.kryo.io.Output;
import android.fluid.kryo.util.ObjectMap;

/** Writes using the objects externalizable interface if it can reliably do so. Typically, a object can be efficiently written
 * with Kryo and Java's externalizable interface. However, there may be behavior problems if the class uses either the
 * 'readResolve' or 'writeReplace' methods. We will fall back onto the standard {@link JavaSerializer} if we detect either of
 * these methods.
 * <p/>
 * Note that this class does not specialize the type on {@code Externalizable}. That is because if we fall back on the
 * {@code JavaSerializer} it may have an {@code readResolve} method that returns an object of a different type.
 *
 * @author Robert DiFalco <robert.difalco@gmail.com> */
/** @hide */
public class ExternalizableSerializer extends Serializer {

	private ObjectMap<Class, JavaSerializer> javaSerializerByType;

	private KryoObjectInput objectInput = null;
	private KryoObjectOutput objectOutput = null;

	@Override
	public void write (Kryo kryo, Output output, Object object) {
		JavaSerializer serializer = getJavaSerializerIfRequired(object.getClass());
		if (serializer == null) {
			writeExternal(kryo, output, object);
		} else {
			serializer.write(kryo, output, object);
		}
	}

	@Override
	public Object read (Kryo kryo, Input input, Class type) {
		JavaSerializer serializer = getJavaSerializerIfRequired(type);
		if (serializer == null) {
			return readExternal(kryo, input, type);
		} else {
			return serializer.read(kryo, input, type);
		}
	}

	private void writeExternal (Kryo kryo, Output output, Object object) {
		try {
			((Externalizable)object).writeExternal(getObjectOutput(kryo, output));
		} catch (ClassCastException e) {
			throw new KryoException(e);
		} catch (IOException e) {
			throw new KryoException(e);
		}
	}

	private Object readExternal (Kryo kryo, Input input, Class type) {
		try {
			Externalizable object = (Externalizable)kryo.newInstance(type);
			object.readExternal(getObjectInput(kryo, input));
			return object;
		} catch (ClassCastException e) {
			throw new KryoException(e);
		} catch (ClassNotFoundException e) {
			throw new KryoException(e);
		} catch (IOException e) {
			throw new KryoException(e);
		}
	}

	@SuppressWarnings("unchecked")
	private ObjectOutput getObjectOutput (Kryo kryo, Output output) {
		if (objectOutput == null) {
			objectOutput = new KryoObjectOutput(kryo, output);
		} else {
			objectOutput.setOutput(output);
		}

		return objectOutput;
	}

	@SuppressWarnings("unchecked")
	private ObjectInput getObjectInput (Kryo kryo, Input input) {
		if (objectInput == null) {
			objectInput = new KryoObjectInput(kryo, input);
		} else {
			objectInput.setInput(input);
		}

		return objectInput;
	}

	/** Determines if this class requires the fall-back {@code JavaSerializer}. If the class does not require any specialized Java
	 * serialization features then null will be returned.
	 *
	 * @param type the type we wish to externalize
	 * @return a {@code JavaSerializer} if the type requires more than simple externalization. */
	private JavaSerializer getJavaSerializerIfRequired (Class type) {
		JavaSerializer javaSerializer = getCachedSerializer(type);
		if (javaSerializer == null && isJavaSerializerRequired(type)) {
			javaSerializer = new JavaSerializer();
		}

		return javaSerializer;
	}

	private JavaSerializer getCachedSerializer (Class type) {
		if (javaSerializerByType == null) {
			javaSerializerByType = new ObjectMap<Class, JavaSerializer>();
			return null;
		}

		return javaSerializerByType.get(type);
	}

	private boolean isJavaSerializerRequired (Class type) {
		return (hasInheritableReplaceMethod(type, "writeReplace") || hasInheritableReplaceMethod(type, "readResolve"));
	}

	/* find out if there are any pesky serialization extras on this class */
	private static boolean hasInheritableReplaceMethod (Class type, String methodName) {
		Method method = null;
		Class<?> current = type;
		while (current != null) {
			try {
				method = current.getDeclaredMethod(methodName);
				break;
			} catch (NoSuchMethodException ex) {
				current = current.getSuperclass();
			}
		}

		return ((method != null) && (method.getReturnType() == Object.class));
	}
}
