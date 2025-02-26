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

package android.fluid.kryo.util;

import java.util.concurrent.ConcurrentHashMap;

/** A few utility methods, mostly for private use.
 * @author Nathan Sweet <misc@n4te.com> */
/** @hide */
public class Util {

	static public final boolean IS_ANDROID = "Dalvik".equals(System.getProperty("java.vm.name"));

	/** @deprecated Use {@link #IS_ANDROID} instead. */
	@Deprecated
	static public boolean isAndroid = IS_ANDROID;

	private static final ConcurrentHashMap<String, Boolean> classAvailabilities = new ConcurrentHashMap<String, Boolean>();

	public static boolean isClassAvailable (String className) {
		Boolean result = classAvailabilities.get(className);
		if (result == null) {
			try {
				Class.forName(className);
				result = true;
			} catch (Exception e) {
				result = false;
			}
			classAvailabilities.put(className, result);
		}
		return result;
	}

	/** Returns the primitive wrapper class for a primitive class.
	 * @param type Must be a primitive class. */
	static public Class getWrapperClass (Class type) {
		if (type == int.class)
			return Integer.class;
		else if (type == float.class)
			return Float.class;
		else if (type == boolean.class)
			return Boolean.class;
		else if (type == long.class)
			return Long.class;
		else if (type == byte.class)
			return Byte.class;
		else if (type == char.class)
			return Character.class;
		else if (type == short.class) //
			return Short.class;
		else if (type == double.class) return Double.class;
		return Void.class;
	}

	/** Returns the primitive class for a primitive wrapper class. Otherwise returns the type parameter.
	 * @param type Must be a wrapper class. */
	static public Class getPrimitiveClass (Class type) {
		if (type == Integer.class)
			return int.class;
		else if (type == Float.class)
			return float.class;
		else if (type == Boolean.class)
			return boolean.class;
		else if (type == Long.class)
			return long.class;
		else if (type == Byte.class)
			return byte.class;
		else if (type == Character.class)
			return char.class;
		else if (type == Short.class) //
			return short.class;
		else if (type == Double.class) //
			return double.class;
		else if (type == Void.class) return void.class;
		return type;
	}

	static public boolean isWrapperClass (Class type) {
		return type == Integer.class || type == Float.class || type == Boolean.class || type == Long.class || type == Byte.class
			|| type == Character.class || type == Short.class || type == Double.class;
	}

	/** Logs a message about an object. The log level and the string format of the object depend on the object type. */
	static public void log (String message, Object object) {
		if (object == null) {
			return;
		}
		Class type = object.getClass();
	}

	/** Returns the object formatted as a string. The format depends on the object's type and whether {@link Object#toString()} has
	 * been overridden. */
	static public String string (Object object) {
		if (object == null) return "null";
		Class type = object.getClass();
		if (type.isArray()) return className(type);
		try {
			if (type.getMethod("toString", new Class[0]).getDeclaringClass() == Object.class)
				return className(type);
		} catch (Exception ignored) {
		}
		try {
			return String.valueOf(object);
		} catch (Throwable e) {
			return className(type) + "(Exception " + e + " in toString)";
		}
	}

	/** Returns the class formatted as a string. The format varies depending on the type. */
	static public String className (Class type) {
		if (type.isArray()) {
			Class elementClass = getElementClass(type);
			StringBuilder buffer = new StringBuilder(16);
			for (int i = 0, n = getDimensionCount(type); i < n; i++)
				buffer.append("[]");
			return className(elementClass) + buffer;
		}
		if (type.isPrimitive() || type == Object.class || type == Boolean.class || type == Byte.class || type == Character.class
			|| type == Short.class || type == Integer.class || type == Long.class || type == Float.class || type == Double.class
			|| type == String.class) {
			return type.getSimpleName();
		}
		return type.getName();
	}

	/** Returns the number of dimensions of an array. */
	static public int getDimensionCount (Class arrayClass) {
		int depth = 0;
		Class nextClass = arrayClass.getComponentType();
		while (nextClass != null) {
			depth++;
			nextClass = nextClass.getComponentType();
		}
		return depth;
	}

	/** Returns the base element type of an n-dimensional array class. */
	static public Class getElementClass (Class arrayClass) {
		Class elementClass = arrayClass;
		while (elementClass.getComponentType() != null)
			elementClass = elementClass.getComponentType();
		return elementClass;
	}

	/** Converts an "int" value between endian systems. */
	static public int swapInt (int i) {
		return ((i & 0xFF) << 24) | ((i & 0xFF00) << 8) | ((i & 0xFF0000) >> 8) | ((i >> 24) & 0xFF);
	}

	/** Converts a "long" value between endian systems. */
	static public long swapLong (long value) {
		return (((value >> 0) & 0xff) << 56) | (((value >> 8) & 0xff) << 48) | (((value >> 16) & 0xff) << 40)
			| (((value >> 24) & 0xff) << 32) | (((value >> 32) & 0xff) << 24) | (((value >> 40) & 0xff) << 16)
			| (((value >> 48) & 0xff) << 8) | (((value >> 56) & 0xff) << 0);
	}
}
