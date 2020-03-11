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

import android.fluid.kryo.Kryo;
import android.fluid.kryo.KryoException;
import android.fluid.kryo.Registration;
import android.fluid.kryo.Serializer;
import android.fluid.kryo.io.Input;
import android.fluid.kryo.io.Output;
import android.fluid.kryo.serializers.FieldSerializer.CachedField;
import com.esotericsoftware.reflectasm.FieldAccess;

/* mobiledui: start */
import android.util.Log;
import java.lang.reflect.Array;
/* mobiledui: end */

/*** Defer generation of serializers until it is really required at run-time. By default, use reflection-based approach.
 * @author Nathan Sweet <misc@n4te.com>
 * @author Roman Levenstein <romixlev@gmail.com> */
/** @hide */
class ObjectField extends CachedField {
	/* mobiledui: start */
    private static final String DUI_TAG = "MOBILEDUI(ObjectField)";
    private static final boolean DUI_DEBUG = true;
	/* mobiledui: end */

	public Class[] generics;
	final FieldSerializer fieldSerializer;
	final Class type;
	final Kryo kryo;

	ObjectField (FieldSerializer fieldSerializer) {
		this.fieldSerializer = fieldSerializer;
		this.kryo = fieldSerializer.kryo;
		this.type = fieldSerializer.type;
	}

	public Object getField (Object object) throws IllegalArgumentException, IllegalAccessException {
		return field.get(object);
	}

	public void setField (Object object, Object value) throws IllegalArgumentException, IllegalAccessException {
		field.set(object, value);
	}

	public void write (Output output, Object object) {
		try {
			Object value = getField(object);

			/* mobiledui: start */
			if (DUI_DEBUG) {
				Log.d(DUI_TAG, "write() start, depth = " + kryo.mDepth
						+ ", field = " + field.getName() 
						+ ", field type = " + field.getType()
						+ ", field.isSynthetic = " + field.isSynthetic()
						+ ", value = " + ((value != null)? value.getClass() : "null")
						+ ", duiFlags = " + ((value != null)? value.zFLUIDFlags : "null")
						+ ", objectId = " + ((value != null)? value.zObjectId : "null")
						+ ", pos = " + output.position());
			}
			kryo.mDepth++;

			boolean needDummyObj1 = (value != null 
					&& kryo.needPartialMigration(field.getType()) 
					&& kryo.needPartialMigration(value.getClass())
					&& !kryo.isRenderingObj(type, field.getName())
					&& !kryo.mTargetObjIds.contains(value.zObjectId));
			boolean needDummyObj2 = (value != null && ((value.zFLUIDFlags & Kryo.DUMMY_RESV) != 0));

			if (DUI_DEBUG) {
				Log.d(DUI_TAG, "write() needDummyObj1 = " + needDummyObj1 + ", needDummyObj2 = " + needDummyObj2);
				if (value != null) {
					Log.d(DUI_TAG, "" + kryo.needPartialMigration(field.getType())
							+ ", " + kryo.needPartialMigration(value.getClass())
							+ ", " + kryo.isRenderingObj(type, field.getName())
							+ ", " + kryo.mTargetObjIds.contains(value.zObjectId));
				}
			}

			if (Kryo.mPartialMode && (needDummyObj1 || needDummyObj2)) {
				// In the remote device, a dummy object for this field will be created.
				Class clazz = value.getClass();
				output.writeVarInt(Kryo.SKIP, true);
				output.writeVarInt(value.zObjectId, true);
				if (valueClass == null)
					kryo.writeClass(output, clazz);

				// If this filed is array type, dummy objects for its elements will be created.
				if (clazz.isArray()) {
					Object[] arr = (Object[])value;
					int length = arr.length;
					output.writeVarInt(length, true);
					for (int i = 0; i < length; i++) {
						if (arr[i] != null) {
							Class elemClazz = arr[i].getClass();
							output.writeVarInt(Kryo.NOT_NULL, true);
							output.writeVarInt(arr[i].zObjectId, true);
							kryo.writeClass(output, elemClazz);

							if ((elemClazz.zFLUIDFlags & Kryo.RPC_INSTALLED) == 0) 
								Kryo.installRpcGadget(elemClazz);
							arr[i].zFLUIDFlags |= Kryo.DUMMY_IN_REMOTE;
						}
						else
							output.writeVarInt(Kryo.NULL, true);
					}
				}
				else {
					if ((clazz.zFLUIDFlags & Kryo.RPC_INSTALLED) == 0) 
						Kryo.installRpcGadget(clazz);
				}
				value.zFLUIDFlags |= Kryo.DUMMY_IN_REMOTE;
				value.zFLUIDFlags &= ~Kryo.DUMMY_RESV;

				kryo.mDepth--;
				if (DUI_DEBUG) {
					Log.d(DUI_TAG, "write() end 2, depth = " + kryo.mDepth 
							+ ", field = " + field.getName());
				}
				return;
			}
			else 
				output.writeVarInt(Kryo.NO_SKIP, true);
			/* mobiledui: end */

			Serializer serializer = this.serializer;
			if (valueClass == null) {
				// The concrete type of the field is unknown, write the class first.
				if (value == null) {
					kryo.writeClass(output, null);
					/* mobiledui: start */
					kryo.mDepth--;
					if (DUI_DEBUG) 
						Log.d(DUI_TAG, "write() end 3, depth = " + kryo.mDepth + ", field = " + field.getName());
					/* mobiledui: end */
					return;
				}
				Registration registration = kryo.writeClass(output, value.getClass());
				if (serializer == null) serializer = registration.getSerializer();
				serializer.setGenerics(kryo, generics);

				kryo.writeObject(output, value, serializer);
			} else {
				// The concrete type of the field is known, always use the same serializer.
				if (serializer == null) this.serializer = serializer = kryo.getSerializer(valueClass);
				// if (generics != null)
				serializer.setGenerics(kryo, generics);

				if (canBeNull) {
					kryo.writeObjectOrNull(output, value, serializer);
				} else {
					if (value == null) {
						throw new KryoException(
							"Field value is null but canBeNull is false: " + this + " (" + object.getClass().getName() + ")");
					}
					kryo.writeObject(output, value, serializer);
				}
			}
		} catch (IllegalAccessException ex) {
			throw new KryoException("Error accessing field: " + this + " (" + object.getClass().getName() + ")", ex);
		} catch (KryoException ex) {
			ex.addTrace(this + " (" + object.getClass().getName() + ")");
			throw ex;
		} catch (RuntimeException runtimeEx) {
			KryoException ex = new KryoException(runtimeEx);
			ex.addTrace(this + " (" + object.getClass().getName() + ")");
			throw ex;
		} finally {
			// if(typeVar2concreteClass != null)
			// kryo.popGenericsScope();
		}

		/* mobiledui: start */
		kryo.mDepth--;
		if (DUI_DEBUG) 
			Log.d(DUI_TAG, "write() end, depth = " + kryo.mDepth + ", field = " + field.getName() + ", pos = " + output.position());
		/* mobiledui: end */
	}

	public void read (Input input, Object object) {
		try {
			Object value;

			/* mobiledui: start */
			if (DUI_DEBUG) {
				Log.d(DUI_TAG, "read() start, depth = " + kryo.mDepth
						+ ", field = " + field.getName() 
						+ ", field type = " + field.getType()
						+ ", pos = " + input.position());
			}
			kryo.mDepth++;

			boolean needDummyObj = (input.readVarInt(true) == Kryo.SKIP);

			if (needDummyObj) {
				int objectId = input.readVarInt(true);
				// TODO: it's correct?
				//if (kryo.mTargetObjIds.contains(objectId)) {
				//	return;
				//}
				Class clazz = (valueClass != null)? valueClass : kryo.readClass(input).getType();

				// If this filed is array type, dummy objects for its elements will be created.
				if (clazz.isArray()) {
					int length = input.readVarInt(true);
					Object[] arr = (Object[])Array.newInstance(clazz.getComponentType(), length);
					for (int i = 0; i < length; i++) {
						if (input.readVarInt(true) == 1) {
							int elemObjId = input.readVarInt(true);
							Class elemClazz = kryo.readClass(input).getType();
							arr[i] = kryo.newInstance(elemClazz);
							if ((elemClazz.zFLUIDFlags & Kryo.RPC_INSTALLED) == 0) 
								Kryo.installRpcGadget(elemClazz);
							arr[i].zFLUIDFlags |= Kryo.DUMMY_OBJECT;
							arr[i].zObjectId = elemObjId;
							if (kryo.mIdToObj.get(elemObjId) == null)
								kryo.mIdToObj.put(elemObjId, arr[i]);
						}
					}
					value = (Object)arr;
				}
				else {
					value = kryo.newInstance(clazz);
					if ((clazz.zFLUIDFlags & Kryo.RPC_INSTALLED) == 0) 
						Kryo.installRpcGadget(clazz);
				}
				value.zFLUIDFlags |= Kryo.DUMMY_OBJECT;
				value.zObjectId = objectId;
				if (kryo.mIdToObj.get(objectId) == null)
					kryo.mIdToObj.put(objectId, value);
				setField(object, value);

				kryo.mDepth--;
				if (DUI_DEBUG) 
					Log.d(DUI_TAG, "read() end 2, depth = " + kryo.mDepth + ", field = " + field.getName() + ", objectId = " + value.zObjectId + ", duiFlags = " + value.zFLUIDFlags);
				return;
			}
			/* mobiledui: end */

			Class concreteType = valueClass;
			Serializer serializer = this.serializer;
			if (concreteType == null) {
				Registration registration = kryo.readClass(input);
				if (registration == null)
					value = null;
				else {
					if (serializer == null) serializer = registration.getSerializer();
					serializer.setGenerics(kryo, generics);
					value = kryo.readObject(input, registration.getType(), serializer);
				}
			} else {
				if (serializer == null) this.serializer = serializer = kryo.getSerializer(valueClass);
				serializer.setGenerics(kryo, generics);
				if (canBeNull)
					value = kryo.readObjectOrNull(input, concreteType, serializer);
				else
					value = kryo.readObject(input, concreteType, serializer);
			}

			setField(object, value);
			/* mobiledui: start */
			kryo.mDepth--;
			if (DUI_DEBUG) {
				Log.d(DUI_TAG, "read() end, depth = " + kryo.mDepth
						+ ", field = " + field.getName() 
						+ ", value = " + ((value != null)? value.getClass() : "null")
						+ ", zFLUIDFlags = " + ((value != null)? value.zFLUIDFlags : "null")
						+ ", zObjectId = " + ((value != null)? value.zObjectId : "null")
						+ ", pos = " + input.position());
			}
			/* mobiledui: end */
		} catch (IllegalAccessException ex) {
			throw new KryoException("Error accessing field: " + this + " (" + type.getName() + ")", ex);
		} catch (KryoException ex) {
			ex.addTrace(this + " (" + type.getName() + ")");
			throw ex;
		} catch (RuntimeException runtimeEx) {
			KryoException ex = new KryoException(runtimeEx);
			ex.addTrace(this + " (" + type.getName() + ")");
			throw ex;
		} finally {
			// if(typeVar2concreteClass != null)
			// kryo.popGenericsScope();
		}
	}
	/* mobiledui: end */

	public void copy (Object original, Object copy) {
		try {
			if (accessIndex != -1) {
				FieldAccess access = (FieldAccess)fieldSerializer.access;
				access.set(copy, accessIndex, kryo.copy(access.get(original, accessIndex)));
			} else
				setField(copy, kryo.copy(getField(original)));
		} catch (IllegalAccessException ex) {
			throw new KryoException("Error accessing field: " + this + " (" + type.getName() + ")", ex);
		} catch (KryoException ex) {
			ex.addTrace(this + " (" + type.getName() + ")");
			throw ex;
		} catch (RuntimeException runtimeEx) {
			KryoException ex = new KryoException(runtimeEx);
			ex.addTrace(this + " (" + type.getName() + ")");
			throw ex;
		}
	}

	final static class ObjectIntField extends ObjectField {
		public ObjectIntField (FieldSerializer fieldSerializer) {
			super(fieldSerializer);
		}

		public Object getField (Object object) throws IllegalArgumentException, IllegalAccessException {
			return field.getInt(object);
		}

		public void write (Output output, Object object) {
			try {
				if (varIntsEnabled)
					output.writeInt(field.getInt(object), false);
				else
					output.writeInt(field.getInt(object));
			} catch (Exception e) {
				KryoException ex = new KryoException(e);
				ex.addTrace(this + " (" + type.getName() + ")");
				throw ex;
			}
		}

		public void read (Input input, Object object) {
			try {
				if (varIntsEnabled)
					field.setInt(object, input.readInt(false));
				else
					field.setInt(object, input.readInt());
			} catch (Exception e) {
				KryoException ex = new KryoException(e);
				ex.addTrace(this + " (" + type.getName() + ")");
				throw ex;
			}
		}

		public void copy (Object original, Object copy) {
			try {
				field.setInt(copy, field.getInt(original));
			} catch (Exception e) {
				KryoException ex = new KryoException(e);
				ex.addTrace(this + " (" + type.getName() + ")");
				throw ex;
			}
		}
	}

	final static class ObjectFloatField extends ObjectField {
		public ObjectFloatField (FieldSerializer fieldSerializer) {
			super(fieldSerializer);
		}

		public Object getField (Object object) throws IllegalArgumentException, IllegalAccessException {
			return field.getFloat(object);
		}

		public void write (Output output, Object object) {
			try {
				output.writeFloat(field.getFloat(object));
			} catch (Exception e) {
				KryoException ex = new KryoException(e);
				ex.addTrace(this + " (" + type.getName() + ")");
				throw ex;
			}
		}

		public void read (Input input, Object object) {
			try {
				field.setFloat(object, input.readFloat());
			} catch (Exception e) {
				KryoException ex = new KryoException(e);
				ex.addTrace(this + " (" + type.getName() + ")");
				throw ex;
			}
		}

		public void copy (Object original, Object copy) {
			try {
				field.setFloat(copy, field.getFloat(original));
			} catch (Exception e) {
				KryoException ex = new KryoException(e);
				ex.addTrace(this + " (" + type.getName() + ")");
				throw ex;
			}
		}
	}

	final static class ObjectShortField extends ObjectField {
		public ObjectShortField (FieldSerializer fieldSerializer) {
			super(fieldSerializer);
		}

		public Object getField (Object object) throws IllegalArgumentException, IllegalAccessException {
			return field.getShort(object);
		}

		public void write (Output output, Object object) {
			try {
				output.writeShort(field.getShort(object));
			} catch (Exception e) {
				KryoException ex = new KryoException(e);
				ex.addTrace(this + " (" + type.getName() + ")");
				throw ex;
			}
		}

		public void read (Input input, Object object) {
			try {
				field.setShort(object, input.readShort());
			} catch (Exception e) {
				KryoException ex = new KryoException(e);
				ex.addTrace(this + " (" + type.getName() + ")");
				throw ex;
			}
		}

		public void copy (Object original, Object copy) {
			try {
				field.setShort(copy, field.getShort(original));
			} catch (Exception e) {
				KryoException ex = new KryoException(e);
				ex.addTrace(this + " (" + type.getName() + ")");
				throw ex;
			}
		}
	}

	final static class ObjectByteField extends ObjectField {
		public ObjectByteField (FieldSerializer fieldSerializer) {
			super(fieldSerializer);
		}

		public Object getField (Object object) throws IllegalArgumentException, IllegalAccessException {
			return field.getByte(object);
		}

		public void write (Output output, Object object) {
			try {
				output.writeByte(field.getByte(object));
			} catch (Exception e) {
				KryoException ex = new KryoException(e);
				ex.addTrace(this + " (" + type.getName() + ")");
				throw ex;
			}
		}

		public void read (Input input, Object object) {
			try {
				field.setByte(object, input.readByte());
			} catch (Exception e) {
				KryoException ex = new KryoException(e);
				ex.addTrace(this + " (" + type.getName() + ")");
				throw ex;
			}
		}

		public void copy (Object original, Object copy) {
			try {
				field.setByte(copy, field.getByte(original));
			} catch (Exception e) {
				KryoException ex = new KryoException(e);
				ex.addTrace(this + " (" + type.getName() + ")");
				throw ex;
			}
		}
	}

	final static class ObjectBooleanField extends ObjectField {
		public ObjectBooleanField (FieldSerializer fieldSerializer) {
			super(fieldSerializer);
		}

		public Object getField (Object object) throws IllegalArgumentException, IllegalAccessException {
			return field.getBoolean(object);
		}

		public void write (Output output, Object object) {
			try {
				output.writeBoolean(field.getBoolean(object));
			} catch (Exception e) {
				KryoException ex = new KryoException(e);
				ex.addTrace(this + " (" + type.getName() + ")");
				throw ex;
			}
		}

		public void read (Input input, Object object) {
			try {
				field.setBoolean(object, input.readBoolean());
			} catch (Exception e) {
				KryoException ex = new KryoException(e);
				ex.addTrace(this + " (" + type.getName() + ")");
				throw ex;
			}
		}

		public void copy (Object original, Object copy) {
			try {
				field.setBoolean(copy, field.getBoolean(original));
			} catch (Exception e) {
				KryoException ex = new KryoException(e);
				ex.addTrace(this + " (" + type.getName() + ")");
				throw ex;
			}
		}
	}

	final static class ObjectCharField extends ObjectField {
		public ObjectCharField (FieldSerializer fieldSerializer) {
			super(fieldSerializer);
		}

		public Object getField (Object object) throws IllegalArgumentException, IllegalAccessException {
			return field.getChar(object);
		}

		public void write (Output output, Object object) {
			try {
				output.writeChar(field.getChar(object));
			} catch (Exception e) {
				KryoException ex = new KryoException(e);
				ex.addTrace(this + " (" + type.getName() + ")");
				throw ex;
			}
		}

		public void read (Input input, Object object) {
			try {
				field.setChar(object, input.readChar());
			} catch (Exception e) {
				KryoException ex = new KryoException(e);
				ex.addTrace(this + " (" + type.getName() + ")");
				throw ex;
			}
		}

		public void copy (Object original, Object copy) {
			try {
				field.setChar(copy, field.getChar(original));
			} catch (Exception e) {
				KryoException ex = new KryoException(e);
				ex.addTrace(this + " (" + type.getName() + ")");
				throw ex;
			}
		}
	}

	final static class ObjectLongField extends ObjectField {
		public ObjectLongField (FieldSerializer fieldSerializer) {
			super(fieldSerializer);
		}

		public Object getField (Object object) throws IllegalArgumentException, IllegalAccessException {
			return field.getLong(object);
		}

		public void write (Output output, Object object) {
			try {
				if (varIntsEnabled)
					output.writeLong(field.getLong(object), false);
				else
					output.writeLong(field.getLong(object));
			} catch (Exception e) {
				KryoException ex = new KryoException(e);
				ex.addTrace(this + " (" + type.getName() + ")");
				throw ex;
			}
		}

		public void read (Input input, Object object) {
			try {
				if (varIntsEnabled)
					field.setLong(object, input.readLong(false));
				else
					field.setLong(object, input.readLong());
			} catch (Exception e) {
				KryoException ex = new KryoException(e);
				ex.addTrace(this + " (" + type.getName() + ")");
				throw ex;
			}
		}

		public void copy (Object original, Object copy) {
			try {
				field.setLong(copy, field.getLong(original));
			} catch (Exception e) {
				KryoException ex = new KryoException(e);
				ex.addTrace(this + " (" + type.getName() + ")");
				throw ex;
			}
		}
	}

	final static class ObjectDoubleField extends ObjectField {
		public ObjectDoubleField (FieldSerializer fieldSerializer) {
			super(fieldSerializer);
		}

		public Object getField (Object object) throws IllegalArgumentException, IllegalAccessException {
			return field.getDouble(object);
		}

		public void write (Output output, Object object) {
			try {
				output.writeDouble(field.getDouble(object));
			} catch (Exception e) {
				KryoException ex = new KryoException(e);
				ex.addTrace(this + " (" + type.getName() + ")");
				throw ex;
			}
		}

		public void read (Input input, Object object) {
			try {
				field.setDouble(object, input.readDouble());
			} catch (Exception e) {
				KryoException ex = new KryoException(e);
				ex.addTrace(this + " (" + type.getName() + ")");
				throw ex;
			}
		}

		public void copy (Object original, Object copy) {
			try {
				field.setDouble(copy, field.getDouble(original));
			} catch (Exception e) {
				KryoException ex = new KryoException(e);
				ex.addTrace(this + " (" + type.getName() + ")");
				throw ex;
			}
		}
	}
}
