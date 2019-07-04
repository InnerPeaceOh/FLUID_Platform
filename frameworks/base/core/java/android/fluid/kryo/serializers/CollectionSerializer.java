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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collection;

import android.fluid.kryo.Kryo;
import android.fluid.kryo.Serializer;
import android.fluid.kryo.io.Input;
import android.fluid.kryo.io.Output;

/* mobiledui: start */
import java.util.Arrays;
/* mobiledui: end */

/** Serializes objects that implement the {@link Collection} interface.
 * <p>
 * With the default constructor, a collection requires a 1-3 byte header and an extra 2-3 bytes is written for each element in the
 * collection. The alternate constructor can be used to improve efficiency to match that of using an array instead of a
 * collection.
 * @author Nathan Sweet <misc@n4te.com> */
/** @hide */
public class CollectionSerializer extends Serializer<Collection> {
	/* mobiledui: start */
    private static final String DUI_TAG = "MOBILEDUI(CollectionSerializer)";
    private static final boolean DUI_DEBUG = false;
	/* mobiledui: end */
	private boolean elementsCanBeNull = true;
	private Serializer serializer;
	private Class elementClass;
	private Class genericType;

	public CollectionSerializer () {
	}

	/** @see #setElementClass(Class, Serializer) */
	public CollectionSerializer (Class elementClass, Serializer serializer) {
		setElementClass(elementClass, serializer);
	}

	/** @see #setElementClass(Class, Serializer)
	 * @see #setElementsCanBeNull(boolean) */
	public CollectionSerializer (Class elementClass, Serializer serializer, boolean elementsCanBeNull) {
		setElementClass(elementClass, serializer);
		this.elementsCanBeNull = elementsCanBeNull;
	}

	/** @param elementsCanBeNull False if all elements are not null. This saves 1 byte per element if elementClass is set. True if
	 *           it is not known (default). */
	public void setElementsCanBeNull (boolean elementsCanBeNull) {
		this.elementsCanBeNull = elementsCanBeNull;
	}

	/** @param elementClass The concrete class of each element. This saves 1-2 bytes per element. Set to null if the class is not
	 *           known or varies per element (default).
	 * @param serializer The serializer to use for each element. */
	public void setElementClass (Class elementClass, Serializer serializer) {
		this.elementClass = elementClass;
		this.serializer = serializer;
	}

	public void setGenerics (Kryo kryo, Class[] generics) {
		genericType = null;
		if (generics != null && generics.length > 0) {
			if (kryo.isFinal(generics[0])) genericType = generics[0];
		}
	}

	public void write (Kryo kryo, Output output, Collection collection) {
		/* mobiledui: start */
		output.writeVarInt(collection.zObjectId, true);
		Class clazz = collection.getClass();
		if ((clazz.zFLUIDFlags & Kryo.RPC_INSTALLED) == 0) 
			Kryo.installRpcGadget(clazz);
		/* mobiledui: end */

		int length = collection.size();
		output.writeVarInt(length, true);
		Serializer serializer = this.serializer;
		if (genericType != null) {
			if (serializer == null) serializer = kryo.getSerializer(genericType);
			genericType = null;
		}
		if (serializer != null) {
			if (elementsCanBeNull) {
				for (Object element : collection)
					kryo.writeObjectOrNull(output, element, serializer);
			} else {
				for (Object element : collection)
					kryo.writeObject(output, element, serializer);
			}
		} else {
//			for (Object element : collection)
//				kryo.writeClassAndObject(output, element);
			/* mobiledui: start */
			for (Object element : collection) {
				boolean needDummyObj = (element != null && ((element.zFLUIDFlags & Kryo.DUMMY_RESV) != 0));
				if (needDummyObj) {
					output.writeVarInt(Kryo.SKIP, true);
					output.writeVarInt(element.zObjectId, true);
					Class elemClazz = element.getClass();
					kryo.writeClass(output, elemClazz);
					if ((elemClazz.zFLUIDFlags & Kryo.RPC_INSTALLED) == 0) 
						Kryo.installRpcGadget(elemClazz);
					element.zFLUIDFlags |= Kryo.DUMMY_IN_REMOTE;
					element.zFLUIDFlags &= ~Kryo.DUMMY_RESV;
				}
				else {
					output.writeVarInt(Kryo.NO_SKIP, true);
					kryo.writeClassAndObject(output, element);
				}
			}
			/* mobiledui: end */
		}
		/* mobiledui: start */
		if (collection.zObjectId != 0)
			collection.zFLUIDFlags |= (Kryo.MIGRATED | Kryo.REMOTE_DEVICE);
		/* mobiledui: end */
	}

	/** Used by {@link #read(Kryo, Input, Class)} to create the new object. This can be overridden to customize object creation, eg
	 * to call a constructor with arguments. The default implementation uses {@link Kryo#newInstance(Class)}. */
	protected Collection create (Kryo kryo, Input input, Class<Collection> type) {
		return kryo.newInstance(type);
	}

	public Collection read (Kryo kryo, Input input, Class<Collection> type) {
		/* mobiledui: start */
		if (type.getName().equals("java.util.Arrays$ArrayList"))
			return readFixedSizeList(kryo, input, type);

		int objectId = input.readVarInt(true);
		/* mobiledui: end */

		Collection collection = create(kryo, input, type);
		kryo.reference(collection);
		int length = input.readVarInt(true);
		if (collection instanceof ArrayList) ((ArrayList)collection).ensureCapacity(length);
		Class elementClass = this.elementClass;
		Serializer serializer = this.serializer;
		if (genericType != null) {
			if (serializer == null) {
				elementClass = genericType;
				serializer = kryo.getSerializer(genericType);
			}
			genericType = null;
		}
		if (serializer != null) {
			if (elementsCanBeNull) {
				for (int i = 0; i < length; i++)
					collection.add(kryo.readObjectOrNull(input, elementClass, serializer));
			} else {
				for (int i = 0; i < length; i++)
					collection.add(kryo.readObject(input, elementClass, serializer));
			}
		} else {
//			for (int i = 0; i < length; i++)
//				collection.add(kryo.readClassAndObject(input));
			/* mobiledui: start */
			for (int i = 0; i < length; i++) {
				boolean needDummyObj = (input.readVarInt(true) == Kryo.SKIP);
				if (needDummyObj) {
					int elemObjId = input.readVarInt(true);
					Class elemClazz = kryo.readClass(input).getType();
					Object element = kryo.newInstance(elemClazz);
					if ((elemClazz.zFLUIDFlags & Kryo.RPC_INSTALLED) == 0) 
						Kryo.installRpcGadget(elemClazz);
					element.zFLUIDFlags |= Kryo.DUMMY_OBJECT;
					element.zObjectId = elemObjId;
					if (kryo.mIdToObj.get(elemObjId) == null)
						kryo.mIdToObj.put(elemObjId, element);
					collection.add(element);
				}
				else
					collection.add(kryo.readClassAndObject(input));
			}
			/* mobiledui: end */
		}

		/* mobiledui: start */
		if (objectId != 0) {
			collection.zObjectId = objectId;
			collection.zFLUIDFlags |= Kryo.MIGRATED;
			kryo.mIdToObj.put(objectId, collection);
		}
		Class clazz = collection.getClass();
		if ((clazz.zFLUIDFlags & Kryo.RPC_INSTALLED) == 0) 
			Kryo.installRpcGadget(clazz);
		/* mobiledui: end */

		return collection;
	}

	/* mobiledui: start */
	public Collection readFixedSizeList(Kryo kryo, Input input, Class<Collection> type) {
		int objectId = input.readVarInt(true);

		Collection collection = kryo.newInstance(ArrayList.class);
		int length = input.readVarInt(true);
		if (collection instanceof ArrayList) ((ArrayList)collection).ensureCapacity(length);
		Class elementClass = this.elementClass;
		Serializer serializer = this.serializer;
		if (genericType != null) {
			if (serializer == null) {
				elementClass = genericType;
				serializer = kryo.getSerializer(genericType);
			}
			genericType = null;
		}
		if (serializer != null) {
			if (elementsCanBeNull) {
				for (int i = 0; i < length; i++)
					collection.add(kryo.readObjectOrNull(input, elementClass, serializer));
			} else {
				for (int i = 0; i < length; i++)
					collection.add(kryo.readObject(input, elementClass, serializer));
			}
		} else {
//			for (int i = 0; i < length; i++)
//				collection.add(kryo.readClassAndObject(input));
			for (int i = 0; i < length; i++) {
				boolean needDummyObj = (input.readVarInt(true) == Kryo.SKIP);
				if (needDummyObj) {
					int elemObjId = input.readVarInt(true);
					Class elemClazz = kryo.readClass(input).getType();
					Object element = kryo.newInstance(elemClazz);
					if ((elemClazz.zFLUIDFlags & Kryo.RPC_INSTALLED) == 0) 
						Kryo.installRpcGadget(elemClazz);
					element.zFLUIDFlags |= Kryo.DUMMY_OBJECT;
					element.zObjectId = elemObjId;
					if (kryo.mIdToObj.get(elemObjId) == null)
						kryo.mIdToObj.put(elemObjId, element);
					collection.add(element);
				}
				else
					collection.add(kryo.readClassAndObject(input));
			}
		}
		Object[] arr = collection.toArray();

		collection = Arrays.asList(arr);
		if (objectId != 0) {
			collection.zObjectId = objectId;
			collection.zFLUIDFlags |= Kryo.MIGRATED;
			kryo.mIdToObj.put(objectId, collection);
		}
		Class clazz = collection.getClass();
		if ((clazz.zFLUIDFlags & Kryo.RPC_INSTALLED) == 0) 
			Kryo.installRpcGadget(clazz);

		return collection;
	}
	/* mobiledui: end */

	/** Used by {@link #copy(Kryo, Collection)} to create the new object. This can be overridden to customize object creation, eg
	 * to call a constructor with arguments. The default implementation uses {@link Kryo#newInstance(Class)}. */
	protected Collection createCopy (Kryo kryo, Collection original) {
		return kryo.newInstance(original.getClass());
	}

	public Collection copy (Kryo kryo, Collection original) {
		Collection copy = createCopy(kryo, original);
		kryo.reference(copy);
		for (Object element : original)
			copy.add(kryo.copy(element));
		return copy;
	}

	/** Used to annotate fields that are collections with specific Kryo serializers for their values. */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public @interface BindCollection {
		/** Serializer to be used for values
		 * 
		 * @return the class<? extends Serializer> used for values serialization */
		@SuppressWarnings("rawtypes")
		Class<? extends Serializer> elementSerializer() default Serializer.class;

		/** Class used for elements
		 * 
		 * @return the class used for elements */
		Class<?> elementClass() default Object.class;

		/** Indicates if elements can be null
		 * 
		 * @return true, if elements can be null */
		boolean elementsCanBeNull() default true;
	}
}
