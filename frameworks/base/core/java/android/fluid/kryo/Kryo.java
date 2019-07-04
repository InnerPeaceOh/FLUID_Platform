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

package android.fluid.kryo;

import static android.fluid.kryo.util.Util.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Currency;
import java.util.Date;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;

import android.fluid.objenesis.instantiator.ObjectInstantiator;
import android.fluid.objenesis.strategy.InstantiatorStrategy;
import android.fluid.objenesis.strategy.SerializingInstantiatorStrategy;
import android.fluid.objenesis.strategy.StdInstantiatorStrategy;

import android.fluid.kryo.factories.PseudoSerializerFactory;
import android.fluid.kryo.factories.ReflectionSerializerFactory;
import android.fluid.kryo.factories.SerializerFactory;
import android.fluid.kryo.io.Input;
import android.fluid.kryo.io.Output;
import android.fluid.kryo.serializers.ClosureSerializer;
import android.fluid.kryo.serializers.CollectionSerializer;
import android.fluid.kryo.serializers.DefaultArraySerializers.BooleanArraySerializer;
import android.fluid.kryo.serializers.DefaultArraySerializers.ByteArraySerializer;
import android.fluid.kryo.serializers.DefaultArraySerializers.CharArraySerializer;
import android.fluid.kryo.serializers.DefaultArraySerializers.DoubleArraySerializer;
import android.fluid.kryo.serializers.DefaultArraySerializers.FloatArraySerializer;
import android.fluid.kryo.serializers.DefaultArraySerializers.IntArraySerializer;
import android.fluid.kryo.serializers.DefaultArraySerializers.LongArraySerializer;
import android.fluid.kryo.serializers.DefaultArraySerializers.ObjectArraySerializer;
import android.fluid.kryo.serializers.DefaultArraySerializers.ShortArraySerializer;
import android.fluid.kryo.serializers.DefaultArraySerializers.StringArraySerializer;
import android.fluid.kryo.serializers.DefaultSerializers.BigDecimalSerializer;
import android.fluid.kryo.serializers.DefaultSerializers.BigIntegerSerializer;
import android.fluid.kryo.serializers.DefaultSerializers.BooleanSerializer;
import android.fluid.kryo.serializers.DefaultSerializers.ByteSerializer;
import android.fluid.kryo.serializers.DefaultSerializers.CalendarSerializer;
import android.fluid.kryo.serializers.DefaultSerializers.CharSerializer;
import android.fluid.kryo.serializers.DefaultSerializers.CharsetSerializer;
import android.fluid.kryo.serializers.DefaultSerializers.ClassSerializer;
import android.fluid.kryo.serializers.DefaultSerializers.CollectionsEmptyListSerializer;
import android.fluid.kryo.serializers.DefaultSerializers.CollectionsEmptyMapSerializer;
import android.fluid.kryo.serializers.DefaultSerializers.CollectionsEmptySetSerializer;
import android.fluid.kryo.serializers.DefaultSerializers.CollectionsSingletonListSerializer;
import android.fluid.kryo.serializers.DefaultSerializers.CollectionsSingletonMapSerializer;
import android.fluid.kryo.serializers.DefaultSerializers.CollectionsSingletonSetSerializer;
import android.fluid.kryo.serializers.DefaultSerializers.CurrencySerializer;
import android.fluid.kryo.serializers.DefaultSerializers.DateSerializer;
import android.fluid.kryo.serializers.DefaultSerializers.DoubleSerializer;
import android.fluid.kryo.serializers.DefaultSerializers.EnumSerializer;
import android.fluid.kryo.serializers.DefaultSerializers.EnumSetSerializer;
import android.fluid.kryo.serializers.DefaultSerializers.FloatSerializer;
import android.fluid.kryo.serializers.DefaultSerializers.IntSerializer;
import android.fluid.kryo.serializers.DefaultSerializers.KryoSerializableSerializer;
import android.fluid.kryo.serializers.DefaultSerializers.LocaleSerializer;
import android.fluid.kryo.serializers.DefaultSerializers.LongSerializer;
import android.fluid.kryo.serializers.DefaultSerializers.ShortSerializer;
import android.fluid.kryo.serializers.DefaultSerializers.StringBufferSerializer;
import android.fluid.kryo.serializers.DefaultSerializers.StringBuilderSerializer;
import android.fluid.kryo.serializers.DefaultSerializers.StringSerializer;
import android.fluid.kryo.serializers.DefaultSerializers.TimeZoneSerializer;
import android.fluid.kryo.serializers.DefaultSerializers.TreeMapSerializer;
import android.fluid.kryo.serializers.DefaultSerializers.TreeSetSerializer;
import android.fluid.kryo.serializers.DefaultSerializers.URLSerializer;
import android.fluid.kryo.serializers.DefaultSerializers.VoidSerializer;
import android.fluid.kryo.serializers.FieldSerializer;
import android.fluid.kryo.serializers.FieldSerializerConfig;
import android.fluid.kryo.serializers.GenericsResolver;
import android.fluid.kryo.serializers.MapSerializer;
import android.fluid.kryo.serializers.OptionalSerializers;
import android.fluid.kryo.serializers.TaggedFieldSerializerConfig;
import android.fluid.kryo.serializers.TimeSerializers;
import android.fluid.kryo.util.DefaultClassResolver;
import android.fluid.kryo.util.DefaultStreamFactory;
import android.fluid.kryo.util.IdentityMap;
import android.fluid.kryo.util.IntArray;
import android.fluid.kryo.util.MapReferenceResolver;
import android.fluid.kryo.util.ObjectMap;
import android.fluid.kryo.util.Util;
import com.esotericsoftware.reflectasm.ConstructorAccess;

/* mobiledui: start */
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.ref.WeakReference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.Reference;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.HashSet;
import java.util.Map.Entry;
import libcore.reflect.Types;
import dalvik.system.DexClassLoader;
import android.util.Log;
import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.os.Handler;
import android.content.Context;
import android.content.res.Resources;
/* mobiledui: end */

/** Maps classes to serializers so object graphs can be serialized automatically.
 * @author Nathan Sweet <misc@n4te.com> */
/** @hide */
public class Kryo {

	static public final byte NULL = 0;
	static public final byte NOT_NULL = 1;

	static private final int REF = -1;
	static private final int NO_REF = -2;

	private SerializerFactory defaultSerializer = new ReflectionSerializerFactory(FieldSerializer.class);
	private final ArrayList<DefaultSerializerEntry> defaultSerializers = new ArrayList(33);
	private final int lowPriorityDefaultSerializerCount;

	private final ClassResolver classResolver;
	private int nextRegisterID;
	private ClassLoader classLoader = getClass().getClassLoader();
	private InstantiatorStrategy strategy = new DefaultInstantiatorStrategy();
	private boolean registrationRequired;
	private boolean warnUnregisteredClasses;

	private int depth, maxDepth = Integer.MAX_VALUE;
	private boolean autoReset = true;
	private volatile Thread thread;
	private ObjectMap context, graphContext;

	private ReferenceResolver referenceResolver;
	private final IntArray readReferenceIds = new IntArray(0);
	private boolean references, copyReferences = true;
	private Object readObject;

	private int copyDepth;
	private boolean copyShallow;
	private IdentityMap originalToCopy;
	private Object needsCopyReference;
	private GenericsResolver genericsResolver = new GenericsResolver();

	private FieldSerializerConfig fieldSerializerConfig = new FieldSerializerConfig();
	private TaggedFieldSerializerConfig taggedFieldSerializerConfig = new TaggedFieldSerializerConfig();

	private StreamFactory streamFactory;

	/* mobiledui: start */
    private static final String DUI_TAG = "MOBILEDUI(Kryo)";
    private static final boolean DUI_DEBUG = false;
	static public final int NONE = 0;
	static public final int OUTER_CONTEXT = 1;
	static public final int INNER_CONTEXT = 2;
	static public final int MIGRATED = 0x00010000;
	static public final int RPC_INSTALLED = 0x00100000;
	static public final int DUMMY_OBJECT = 0x01000000;
	static public final int DUMMY_RESV = 0x02000000;
	static public final int DUMMY_IN_REMOTE = 0x10000000;
	static public final int REMOTE_DEVICE = 0x00000001;
	static public final int SKIP = 0;
	static public final int NO_SKIP = 1;

	public HashSet<String> transientClassSet = new HashSet<String>();
	public DexClassLoader mDexLoader;
	public Context mAppContext;	// Outer context (Activity)
	public View mDecor;
	public WindowManager mWindowManager;
	public Window mWindow;
	public Resources mResources;

	private int mNextId = 1;
	public HashMap<Integer, Object> mIdToObj;
	static public boolean mPartialMode = false;
	public HashSet<Integer> mTargetObjIds = new HashSet<Integer>();
	public HashMap<String, HashMap<String, HashSet<String>>> mAnalysisData = new HashMap<String, HashMap<String, HashSet<String>>>();
	public HashMap<String, HashSet<String>> mRenderingObjs;
	public int mDepth = 0;

	public void useAnalysisResult() {
		try {
			File analysisDir = new File("data/local/tmp/rendering_analysis");
			File[] fileList = analysisDir.listFiles();
			for (int i = 0; i < fileList.length; i++) {
				File file = fileList[i];

				HashMap<String, HashSet<String>> metadata = new HashMap<String, HashSet<String>>();
				BufferedReader in = new BufferedReader(new FileReader(file));
				String str = null;
				Class clazz = null;
				String clazzName = null;
				HashSet<String> fieldNames = null;
				while ((str = in.readLine()) != null) {
					if (str.startsWith("</") && str.endsWith(">")) {
						assert clazzName.equals(str.substring(2, str.length() - 1));
						metadata.put(clazzName, fieldNames);
						clazz = null;
					}
					else if (str.startsWith("<") && str.endsWith(">")) {
						assert clazz == null;
						clazzName = str.substring(1, str.length() - 1);
						fieldNames = new HashSet<String>();
					}
					else {
						assert fieldNames != null;
						fieldNames.add(str);
					}
				}
				mAnalysisData.put(file.getName(), metadata);
			}
		} catch (Exception e) { e.printStackTrace(); }
	}

	public void findRenderingObjs(Object headObj) {
		if (!needPartialMigration(headObj.getClass())) 
			return;
		Queue<Object> queue = new LinkedList<Object>();
		HashSet<Integer> visited = new HashSet<Integer>();
		ArrayList<Object> dummyCandidates = new ArrayList<Object>();
		ArrayList<Object> syntheticFields = new ArrayList<Object>();

		if (headObj.zObjectId == 0) {
			headObj.zObjectId = assignObjId();
			mIdToObj.put(headObj.zObjectId, headObj);
		}
		queue.add(headObj);
		if(!isRenderingObj(headObj.getClass()))
			return;
		mTargetObjIds.add(headObj.zObjectId);
		try {
			while (!queue.isEmpty()) {
				Object obj = queue.poll();
				if (visited.contains(obj.zObjectId)) 
					continue;
				visited.add(obj.zObjectId);
				Class clazz = obj.getClass();
				while (clazz != Object.class) {
					Field[] declaredFields = clazz.getDeclaredFields();
					if (declaredFields == null)
						continue;
					for (Field field : declaredFields) {
						boolean isRendering = isRenderingObj(clazz, field.getName());

						if (!field.isAccessible())
							field.setAccessible(true);
						Object adjObj = field.get(obj);
						if (adjObj == null) 
							continue;
						if (field.isSynthetic()) {
							boolean needDummyObj = (adjObj != null
									&& needPartialMigration(field.getType()) 
									&& needPartialMigration(adjObj.getClass())
									&& !isRenderingObj(clazz, field.getName()));
							if (needDummyObj) {
								dummyCandidates.add(obj);
								syntheticFields.add(adjObj);
							}
						}
						
						int modifiers = field.getModifiers();
						if (Modifier.isStatic(modifiers) 
								|| Modifier.isTransient(modifiers)
								|| !needPartialMigration(field.getType())
								|| !needPartialMigration(adjObj.getClass()))
							continue;
						if (!visited.contains(adjObj.zObjectId)) {
							if (adjObj.zObjectId == 0) {
								adjObj.zObjectId = assignObjId();
								mIdToObj.put(adjObj.zObjectId, adjObj);
							}	
							Class adjClazz = adjObj.getClass();
							if (isRendering) {
								if (!Map.class.isAssignableFrom(adjClazz) 
										&& !visited.contains(adjObj.zObjectId)) {
									queue.add(adjObj);
								}
								mTargetObjIds.add(adjObj.zObjectId);
							}

							if (adjClazz.isArray()) {
								Object[] arr = (Object[])adjObj;
								for (int i = 0; i < arr.length; i++) {
									if (arr[i] == null) 
										continue;
									if (needPartialMigration(arr[i].getClass())) {
										if (arr[i].zObjectId == 0) {
											arr[i].zObjectId = assignObjId();
											mIdToObj.put(arr[i].zObjectId, arr[i]);
										}	
										if (isRendering && !visited.contains(arr[i].zObjectId)) {
											queue.add(arr[i]);
											mTargetObjIds.add(arr[i].zObjectId);
										}
									}
								}
							}
							else if (Map.class.isAssignableFrom(adjClazz)) {
								Map map = (Map)adjObj;
								for (Iterator iter = map.entrySet().iterator(); iter.hasNext();) {
									Entry entry = (Entry)iter.next();
									Object key = entry.getKey();
									if (key != null && needPartialMigration(key.getClass())) {
										if (key.zObjectId == 0) {
											key.zObjectId = assignObjId();
											mIdToObj.put(key.zObjectId, key);
										}	
										if (isRendering && !visited.contains(key.zObjectId)) {
											queue.add(key);
											mTargetObjIds.add(key.zObjectId);
										}
									}

									Object value = entry.getValue();
									if (value != null && needPartialMigration(value.getClass())) {
										if (value.zObjectId == 0) {
											value.zObjectId = assignObjId();
											mIdToObj.put(value.zObjectId, value);
										}	
										if (isRendering && !visited.contains(value.zObjectId)) {
											queue.add(value);
											mTargetObjIds.add(value.zObjectId);
										}
									}
								}
							}
							else if (Collection.class.isAssignableFrom(adjClazz)) {
								Collection collection = (Collection)adjObj;
								for (Object elem : collection) {
									if (elem == null) 
										continue;
									if (needPartialMigration(elem.getClass())) {
										if (elem.zObjectId == 0) {
											elem.zObjectId = assignObjId();
											mIdToObj.put(elem.zObjectId, elem);
										}
										if (isRendering && !visited.contains(elem.zObjectId)) {
											queue.add(elem);
											mTargetObjIds.add(elem.zObjectId);
										}
									}
								}
							}
						}
					}
					clazz = clazz.getSuperclass();
				}
			}
			for (int i = 0; i < dummyCandidates.size(); i++) {
				Object obj = dummyCandidates.get(i);
				Object adjObj = syntheticFields.get(i);
				if (!mTargetObjIds.contains(adjObj.zObjectId))
					obj.zFLUIDFlags |= DUMMY_RESV;
			}
		} catch (Exception ex) { ex.printStackTrace(); }
	}

	public void writeFLUIDObject (Output output, Object object) {
		if (DUI_DEBUG) 
			Log.d(DUI_TAG, "writeFLUIDObject() start, object = " + object.getClass() + ", objectId = " + object.zObjectId + ", FLUIDFlags = " + object.zFLUIDFlags);
		
		findRenderingObjs(object);

		// If the object was already migrated, send only its id.
		if (object.zObjectId != 0 && ((object.zFLUIDFlags & MIGRATED) != 0)
				&& !ViewGroup.LayoutParams.class.isAssignableFrom(object.getClass())) {
			output.writeVarInt(object.zObjectId, true);
			Log.d(DUI_TAG, "writeFLUIDObject() bp 2, objectId = " + object.zObjectId);
			return;
		}
		else
			output.writeVarInt(0, true);

		// All rendering objects must not be skipped.
		if (!isRenderingObj(object.getClass())) {
			Class clazz = object.getClass();
			output.writeVarInt(SKIP, true);
			output.writeVarInt(object.zObjectId, true);
			writeClass(output, clazz);
			if ((clazz.zFLUIDFlags & Kryo.RPC_INSTALLED) == 0) 
				Kryo.installRpcGadget(clazz);
			object.zFLUIDFlags |= Kryo.DUMMY_IN_REMOTE;
			Log.d(DUI_TAG, "writeFLUIDObject() bp 3, objectId = " + object.zObjectId);
			return;
		}
		else
			output.writeVarInt(NO_SKIP, true);

		mPartialMode = true;
		output.writeString(object.getClass().getName());
		writeObject(output, object);
		mPartialMode = false;
	}

	public Object readFLUIDObject (Input input, Class type, Object uiObj) {
		if (DUI_DEBUG) {
			Log.d(DUI_TAG, "readFLUIDObject(Object) start, type = " + type
					+ ", uiObj = " + uiObj);
		}

		// Restore the cached object
		int objectId = input.readVarInt(true);
		if (objectId != 0) {
			return mIdToObj.get(objectId);
		}

		// uiObj is a rendering object
		input.readVarInt(true);

		String className = input.readString();
		Class clazz = null;
		try {
			clazz = Class.forName(className);
		} catch (ClassNotFoundException e) {
			try {
				clazz = mDexLoader.loadClass(className);
			} catch (ClassNotFoundException e2) { e2.printStackTrace(); }
		}
		uiObj = readObject(input, clazz, uiObj);
		return uiObj;
	}

	public Object readFLUIDObject (Input input, Class type) {
		if (DUI_DEBUG) Log.d(DUI_TAG, "readFLUIDObject() start, type = " + type);

		// Restore the cached object
		int objectId = input.readVarInt(true);
		if (objectId != 0) {
			return mIdToObj.get(objectId);
		}

		int isSkipped = input.readVarInt(true);
		if (isSkipped == SKIP) {
			objectId = input.readVarInt(true);
			Class clazz = readClass(input).getType();
			if ((clazz.zFLUIDFlags & Kryo.RPC_INSTALLED) == 0) 
				Kryo.installRpcGadget(clazz);
			Object value = newInstance(clazz);
			value.zFLUIDFlags |= Kryo.DUMMY_OBJECT;
			value.zObjectId = objectId;
			mIdToObj.put(objectId, value);
			Log.d(DUI_TAG, "readFLUIDObject2() value = " + value.getClass() + ", objectId = " + objectId);
			return value;
		}

		String className = input.readString();
		Class clazz = null;
		try {
			clazz = Class.forName(className);
		} catch (ClassNotFoundException e) {
			try {
				clazz = mDexLoader.loadClass(className);
			} catch (ClassNotFoundException e2) { e2.printStackTrace(); }
		}
		return readObject(input, clazz);
	}

	public boolean isRenderingObj(Class clazz) {
		Set<String> classSet = mRenderingObjs.keySet();
		while (clazz != Object.class) {
			if (classSet.contains(clazz.getName()))
				return true;
			clazz = clazz.getSuperclass();
		}
		return false;
	}

	public boolean isRenderingObj(Class clazz, String fieldName) {
		while (clazz != Object.class) {
			HashSet<String> fieldSet = mRenderingObjs.get(clazz.getName());
			if (fieldSet != null && fieldSet.contains(fieldName))
				return true;
			clazz = clazz.getSuperclass();
		}
		return false;
	}

	public boolean needPartialMigration(Class clazz) {
		if (clazz.isPrimitive() || isWrapper(clazz) 
				|| clazz == String.class
				|| clazz == Class.class
				|| clazz == WeakReference.class
				|| clazz.isEnum() || Enum.class.isAssignableFrom(clazz) 
				|| EnumSet.class.isAssignableFrom(clazz) 
				|| isTransientClass(clazz.getName())) {
			return false;
		}
		if (clazz.isArray()) {
			if (clazz.getComponentType().isPrimitive() 
					|| clazz.getComponentType() == String.class
					|| isWrapper(clazz.getComponentType()))
				return false;

			// For 2D array
			clazz = clazz.getComponentType();
			if (clazz.isArray() 
					&& (clazz.getComponentType().isPrimitive() || isWrapper(clazz.getComponentType())))
				return false;
		}

		return true;
	}

	public boolean isWrapper(Class clazz) {
		if (Boolean.class.equals(clazz)
				|| Character.class.equals(clazz)
				|| Byte.class.equals(clazz)
				|| Short.class.equals(clazz)
				|| Integer.class.equals(clazz)
				|| Long.class.equals(clazz)
				|| Float.class.equals(clazz)
				|| Double.class.equals(clazz))
			return true;
		return false;
	}

	public int assignObjId() {
		while (mIdToObj.containsKey(mNextId)) {
			mNextId++;
			if (mNextId > Integer.MAX_VALUE)
				mNextId = 1;
		}
		return mNextId;
	}

	// TODO: Naive implementation
	public void initTransientClass() {
		transientClassSet.add("android.app.ActivityThread");
		transientClassSet.add("android.fluid.FLUIDManager");
		transientClassSet.add("android.fluid.kryo.Kryo");
		transientClassSet.add("android.view.View$AttachInfo");
		transientClassSet.add("android.view.RenderNode");
		transientClassSet.add("android.content.Context");
		//transientClassSet.add("android.content.res.Resources");
		transientClassSet.add("android.content.pm.PackageManager");
		transientClassSet.add("android.view.accessibility.AccessibilityManager");
		transientClassSet.add("android.graphics.Typeface");
		transientClassSet.add("java.lang.reflect.Method");

		// For TextView
		// Should handle native objects
		transientClassSet.add("[Landroid.widget.Editor$TextRenderNode;");  
		// Should handle binder objects
		transientClassSet.add("android.widget.SpellChecker");

		// For ImageView
		// Should handle native objects
		//transientClassSet.add("android.graphics.Path");
	}

	public static void installRpcGadget(Class clazz) {
		try {
			if (clazz == Object.class || clazz == android.os.LocaleList.class) 
				return;
			while (clazz != Object.class) {
				if ((clazz.zFLUIDFlags & Kryo.RPC_INSTALLED) == 0) {
					Method[] methods = clazz.getDeclaredMethods();
					for (Method method : methods) {
						if (Modifier.isStatic(method.getModifiers()) || Modifier.isPrivate(method.getModifiers()))
							continue;
						Class.setRpcGadget(clazz, method.getName(), getSignature(method));
					}
					clazz.zFLUIDFlags |= Kryo.RPC_INSTALLED;
				}
				clazz = clazz.getSuperclass();
			}
		} catch (Exception e) { e.printStackTrace(); }
	}

	private static String getSignature(Method method) throws Exception {
		StringBuilder result = new StringBuilder();
		result.append('(');
		Class<?>[] parameterTypes = method.getParameterTypes();
		for (Class<?> parameterType : parameterTypes) {
			result.append(Types.getSignature(parameterType));
		}
		result.append(')');
		result.append(Types.getSignature(method.getReturnType()));
		return result.toString();
	}

	public boolean isTransientClass(String className) {
		return transientClassSet.contains(className);
	}
	/* mobiledui: end */

	/** Creates a new Kryo with a {@link DefaultClassResolver} and a {@link MapReferenceResolver}. */
	public Kryo () {
		this(new DefaultClassResolver(), new MapReferenceResolver(), new DefaultStreamFactory());
	}

	/** Creates a new Kryo with a {@link DefaultClassResolver}.
	 * @param referenceResolver May be null to disable references. */
	public Kryo (ReferenceResolver referenceResolver) {
		this(new DefaultClassResolver(), referenceResolver, new DefaultStreamFactory());
	}

	/** @param referenceResolver May be null to disable references. */
	public Kryo (ClassResolver classResolver, ReferenceResolver referenceResolver) {
		this(classResolver, referenceResolver, new DefaultStreamFactory());
	}

	/** @param referenceResolver May be null to disable references. */
	public Kryo (ClassResolver classResolver, ReferenceResolver referenceResolver, StreamFactory streamFactory) {
		if (classResolver == null) throw new IllegalArgumentException("classResolver cannot be null.");

		/* mobiledui: start */
		initTransientClass();
		useAnalysisResult();
		/* mobiledui: end */

		this.classResolver = classResolver;
		classResolver.setKryo(this);

		this.streamFactory = streamFactory;
		streamFactory.setKryo(this);

		this.referenceResolver = referenceResolver;
		if (referenceResolver != null) {
			referenceResolver.setKryo(this);
			references = true;
		}

		addDefaultSerializer(byte[].class, ByteArraySerializer.class);
		addDefaultSerializer(char[].class, CharArraySerializer.class);
		addDefaultSerializer(short[].class, ShortArraySerializer.class);
		addDefaultSerializer(int[].class, IntArraySerializer.class);
		addDefaultSerializer(long[].class, LongArraySerializer.class);
		addDefaultSerializer(float[].class, FloatArraySerializer.class);
		addDefaultSerializer(double[].class, DoubleArraySerializer.class);
		addDefaultSerializer(boolean[].class, BooleanArraySerializer.class);
		addDefaultSerializer(String[].class, StringArraySerializer.class);
		addDefaultSerializer(Object[].class, ObjectArraySerializer.class);
		addDefaultSerializer(KryoSerializable.class, KryoSerializableSerializer.class);
		addDefaultSerializer(BigInteger.class, BigIntegerSerializer.class);
		addDefaultSerializer(BigDecimal.class, BigDecimalSerializer.class);
		addDefaultSerializer(Class.class, ClassSerializer.class);
		addDefaultSerializer(Date.class, DateSerializer.class);
		addDefaultSerializer(Enum.class, EnumSerializer.class);
		addDefaultSerializer(EnumSet.class, EnumSetSerializer.class);
		addDefaultSerializer(Currency.class, CurrencySerializer.class);
		addDefaultSerializer(StringBuffer.class, StringBufferSerializer.class);
		addDefaultSerializer(StringBuilder.class, StringBuilderSerializer.class);
		addDefaultSerializer(Collections.EMPTY_LIST.getClass(), CollectionsEmptyListSerializer.class);
		addDefaultSerializer(Collections.EMPTY_MAP.getClass(), CollectionsEmptyMapSerializer.class);
		addDefaultSerializer(Collections.EMPTY_SET.getClass(), CollectionsEmptySetSerializer.class);
		addDefaultSerializer(Collections.singletonList(null).getClass(), CollectionsSingletonListSerializer.class);
		addDefaultSerializer(Collections.singletonMap(null, null).getClass(), CollectionsSingletonMapSerializer.class);
		addDefaultSerializer(Collections.singleton(null).getClass(), CollectionsSingletonSetSerializer.class);
		addDefaultSerializer(TreeSet.class, TreeSetSerializer.class);
		addDefaultSerializer(Collection.class, CollectionSerializer.class);
		addDefaultSerializer(TreeMap.class, TreeMapSerializer.class);
		addDefaultSerializer(Map.class, MapSerializer.class);
		addDefaultSerializer(TimeZone.class, TimeZoneSerializer.class);
		addDefaultSerializer(Calendar.class, CalendarSerializer.class);
		addDefaultSerializer(Locale.class, LocaleSerializer.class);
		addDefaultSerializer(Charset.class, CharsetSerializer.class);
		addDefaultSerializer(URL.class, URLSerializer.class);
		OptionalSerializers.addDefaultSerializers(this);
		TimeSerializers.addDefaultSerializers(this);
		lowPriorityDefaultSerializerCount = defaultSerializers.size();

		// Primitives and string. Primitive wrappers automatically use the same registration as primitives.
		register(int.class, new IntSerializer());
		register(String.class, new StringSerializer());
		register(float.class, new FloatSerializer());
		register(boolean.class, new BooleanSerializer());
		register(byte.class, new ByteSerializer());
		register(char.class, new CharSerializer());
		register(short.class, new ShortSerializer());
		register(long.class, new LongSerializer());
		register(double.class, new DoubleSerializer());
		register(void.class, new VoidSerializer());
	}

	// --- Default serializers ---
	/** Sets the serializer factory to use when no {@link #addDefaultSerializer(Class, Class) default serializers} match an
	 * object's type. Default is {@link ReflectionSerializerFactory} with {@link FieldSerializer}.
	 * @see #newDefaultSerializer(Class) */
	public void setDefaultSerializer (SerializerFactory serializer) {
		if (serializer == null) throw new IllegalArgumentException("serializer cannot be null.");
		defaultSerializer = serializer;
	}

	/** Sets the serializer to use when no {@link #addDefaultSerializer(Class, Class) default serializers} match an object's type.
	 * Default is {@link FieldSerializer}.
	 * @see #newDefaultSerializer(Class) */
	public void setDefaultSerializer (Class<? extends Serializer> serializer) {
		if (serializer == null) throw new IllegalArgumentException("serializer cannot be null.");
		defaultSerializer = new ReflectionSerializerFactory(serializer);
	}

	/** Instances of the specified class will use the specified serializer when {@link #register(Class)} or
	 * {@link #register(Class, int)} are called.
	 * @see #setDefaultSerializer(Class) */
	public void addDefaultSerializer (Class type, Serializer serializer) {
		if (type == null) throw new IllegalArgumentException("type cannot be null.");
		if (serializer == null) throw new IllegalArgumentException("serializer cannot be null.");
		DefaultSerializerEntry entry = new DefaultSerializerEntry(type, new PseudoSerializerFactory(serializer));
		defaultSerializers.add(defaultSerializers.size() - lowPriorityDefaultSerializerCount, entry);
	}

	/** Instances of the specified class will use the specified factory to create a serializer when {@link #register(Class)} or
	 * {@link #register(Class, int)} are called.
	 * @see #setDefaultSerializer(Class) */
	public void addDefaultSerializer (Class type, SerializerFactory serializerFactory) {
		if (type == null) throw new IllegalArgumentException("type cannot be null.");
		if (serializerFactory == null) throw new IllegalArgumentException("serializerFactory cannot be null.");
		DefaultSerializerEntry entry = new DefaultSerializerEntry(type, serializerFactory);
		defaultSerializers.add(defaultSerializers.size() - lowPriorityDefaultSerializerCount, entry);
	}

	/** Instances of the specified class will use the specified serializer when {@link #register(Class)} or
	 * {@link #register(Class, int)} are called. Serializer instances are created as needed via
	 * {@link ReflectionSerializerFactory#makeSerializer(Kryo, Class, Class)}. By default, the following classes have a default
	 * serializer set:
	 * <p>
	 * <table>
	 * <tr>
	 * <td>boolean</td>
	 * <td>Boolean</td>
	 * <td>byte</td>
	 * <td>Byte</td>
	 * <td>char</td>
	 * <tr>
	 * </tr>
	 * <td>Character</td>
	 * <td>short</td>
	 * <td>Short</td>
	 * <td>int</td>
	 * <td>Integer</td>
	 * <tr>
	 * </tr>
	 * <td>long</td>
	 * <td>Long</td>
	 * <td>float</td>
	 * <td>Float</td>
	 * <td>double</td>
	 * <tr>
	 * </tr>
	 * <td>Double</td>
	 * <td>String</td>
	 * <td>byte[]</td>
	 * <td>char[]</td>
	 * <td>short[]</td>
	 * <tr>
	 * </tr>
	 * <td>int[]</td>
	 * <td>long[]</td>
	 * <td>float[]</td>
	 * <td>double[]</td>
	 * <td>String[]</td>
	 * <tr>
	 * </tr>
	 * <td>Object[]</td>
	 * <td>Map</td>
	 * <td>BigInteger</td>
	 * <td>BigDecimal</td>
	 * <td>KryoSerializable</td>
	 * </tr>
	 * <tr>
	 * <td>Collection</td>
	 * <td>Date</td>
	 * <td>Collections.emptyList</td>
	 * <td>Collections.singleton</td>
	 * <td>Currency</td>
	 * </tr>
	 * <tr>
	 * <td>StringBuilder</td>
	 * <td>Enum</td>
	 * <td>Collections.emptyMap</td>
	 * <td>Collections.emptySet</td>
	 * <td>Calendar</td>
	 * </tr>
	 * <tr>
	 * <td>StringBuffer</td>
	 * <td>Class</td>
	 * <td>Collections.singletonList</td>
	 * <td>Collections.singletonMap</td>
	 * <td>TimeZone</td>
	 * </tr>
	 * <tr>
	 * <td>TreeMap</td>
	 * <td>EnumSet</td>
	 * </tr>
	 * </table>
	 * <p>
	 * Note that the order default serializers are added is important for a class that may match multiple types. The above default
	 * serializers always have a lower priority than subsequent default serializers that are added. */
	public void addDefaultSerializer (Class type, Class<? extends Serializer> serializerClass) {
		if (type == null) throw new IllegalArgumentException("type cannot be null.");
		if (serializerClass == null) throw new IllegalArgumentException("serializerClass cannot be null.");
		DefaultSerializerEntry entry = new DefaultSerializerEntry(type, new ReflectionSerializerFactory(serializerClass));
		defaultSerializers.add(defaultSerializers.size() - lowPriorityDefaultSerializerCount, entry);
	}

	/** Returns the best matching serializer for a class. This method can be overridden to implement custom logic to choose a
	 * serializer. */
	public Serializer getDefaultSerializer (Class type) {
		if (type == null) throw new IllegalArgumentException("type cannot be null.");

		final Serializer serializerForAnnotation = getDefaultSerializerForAnnotatedType(type);
		if (serializerForAnnotation != null) return serializerForAnnotation;

		for (int i = 0, n = defaultSerializers.size(); i < n; i++) {
			DefaultSerializerEntry entry = defaultSerializers.get(i);
			if (entry.type.isAssignableFrom(type)) {
				Serializer defaultSerializer = entry.serializerFactory.makeSerializer(this, type);
				return defaultSerializer;
			}
		}

		return newDefaultSerializer(type);
	}

	protected Serializer getDefaultSerializerForAnnotatedType (Class type) {
		if (type.isAnnotationPresent(DefaultSerializer.class)) {
			DefaultSerializer defaultSerializerAnnotation = (DefaultSerializer)type.getAnnotation(DefaultSerializer.class);
			return ReflectionSerializerFactory.makeSerializer(this, defaultSerializerAnnotation.value(), type);
		}

		return null;
	}

	/** Called by {@link #getDefaultSerializer(Class)} when no default serializers matched the type. Subclasses can override this
	 * method to customize behavior. The default implementation calls {@link SerializerFactory#makeSerializer(Kryo, Class)} using
	 * the {@link #setDefaultSerializer(Class) default serializer}. */
	protected Serializer newDefaultSerializer (Class type) {
		return defaultSerializer.makeSerializer(this, type);
	}

	// --- Registration ---

	/** Registers the class using the lowest, next available integer ID and the {@link Kryo#getDefaultSerializer(Class) default
	 * serializer}. If the class is already registered, no change will be made and the existing registration will be returned.
	 * Registering a primitive also affects the corresponding primitive wrapper.
	 * <p>
	 * Because the ID assigned is affected by the IDs registered before it, the order classes are registered is important when
	 * using this method. The order must be the same at deserialization as it was for serialization. */
	public Registration register (Class type) {
		Registration registration = classResolver.getRegistration(type);
		if (registration != null) return registration;
		return register(type, getDefaultSerializer(type));
	}

	/** Registers the class using the specified ID and the {@link Kryo#getDefaultSerializer(Class) default serializer}. If the
	 * class is already registered this has no effect and the existing registration is returned. Registering a primitive also
	 * affects the corresponding primitive wrapper.
	 * <p>
	 * IDs must be the same at deserialization as they were for serialization.
	 * @param id Must be >= 0. Smaller IDs are serialized more efficiently. IDs 0-8 are used by default for primitive types and
	 *           String, but these IDs can be repurposed. */
	public Registration register (Class type, int id) {
		Registration registration = classResolver.getRegistration(type);
		if (registration != null) return registration;
		return register(type, getDefaultSerializer(type), id);
	}

	/** Registers the class using the lowest, next available integer ID and the specified serializer. If the class is already
	 * registered, the existing entry is updated with the new serializer. Registering a primitive also affects the corresponding
	 * primitive wrapper.
	 * <p>
	 * Because the ID assigned is affected by the IDs registered before it, the order classes are registered is important when
	 * using this method. The order must be the same at deserialization as it was for serialization. */
	public Registration register (Class type, Serializer serializer) {
		Registration registration = classResolver.getRegistration(type);
		if (registration != null) {
			registration.setSerializer(serializer);
			return registration;
		}
		return classResolver.register(new Registration(type, serializer, getNextRegistrationId()));
	}

	/** Registers the class using the specified ID and serializer. Providing an ID that is already in use by the same type will
	 * cause the old entry to be overwritten. Registering a primitive also affects the corresponding primitive wrapper.
	 * <p>
	 * IDs must be the same at deserialization as they were for serialization.
	 * @param id Must be >= 0. Smaller IDs are serialized more efficiently. IDs 0-9 are used by default for primitive types
	 *           and their wrappers, String, and void, but these IDs can be repurposed. */
	public Registration register (Class type, Serializer serializer, int id) {
		if (id < 0) throw new IllegalArgumentException("id must be >= 0: " + id);
		return register(new Registration(type, serializer, id));
	}

	/** Stores the specified registration. If the ID is already in use by the same type, the old entry is overwritten. Registering
	 * a primitive also affects the corresponding primitive wrapper.
	 * <p>
	 * IDs must be the same at deserialization as they were for serialization.
	 * <p>
	 * Registration can be suclassed to efficiently store per type information, accessible in serializers via
	 * {@link Kryo#getRegistration(Class)}. */
	public Registration register (Registration registration) {
		int id = registration.getId();
		if (id < 0) throw new IllegalArgumentException("id must be > 0: " + id);

		Registration existing = getRegistration(registration.getId());

		return classResolver.register(registration);
	}

	/** Returns the lowest, next available integer ID. */
	public int getNextRegistrationId () {
		while (nextRegisterID != -2) {
			if (classResolver.getRegistration(nextRegisterID) == null) return nextRegisterID;
			nextRegisterID++;
		}
		throw new KryoException("No registration IDs are available.");
	}

	/** If the class is not registered and {@link Kryo#setRegistrationRequired(boolean)} is false, it is automatically registered
	 * using the {@link Kryo#addDefaultSerializer(Class, Class) default serializer}.
	 * @throws IllegalArgumentException if the class is not registered and {@link Kryo#setRegistrationRequired(boolean)} is true.
	 * @see ClassResolver#getRegistration(Class) */
	public Registration getRegistration (Class type) {
		if (type == null) throw new IllegalArgumentException("type cannot be null.");

		Registration registration = classResolver.getRegistration(type);
		if (registration == null) {
			if (Proxy.isProxyClass(type)) {
				// If a Proxy class, treat it like an InvocationHandler because the concrete class for a proxy is generated.
				registration = getRegistration(InvocationHandler.class);
			} else if (!type.isEnum() && Enum.class.isAssignableFrom(type) && !Enum.class.equals(type)) {
				// This handles an enum value that is an inner class. Eg: enum A {b{}};
				registration = getRegistration(type.getEnclosingClass());
			} else if (EnumSet.class.isAssignableFrom(type)) {
				registration = classResolver.getRegistration(EnumSet.class);
			} else if (isClosure(type)) {
				registration = classResolver.getRegistration(ClosureSerializer.Closure.class);
			}
			if (registration == null) {
				if (registrationRequired) {
					throw new IllegalArgumentException(unregisteredClassMessage(type));
				}
				registration = classResolver.registerImplicit(type);
			}
		}
		return registration;
	}

	protected String unregisteredClassMessage (Class type) {
		return "Class is not registered: " + className(type) + "\nNote: To register this class use: kryo.register("
			+ className(type) + ".class);";
	}

	/** @see ClassResolver#getRegistration(int) */
	public Registration getRegistration (int classID) {
		return classResolver.getRegistration(classID);
	}

	/** Returns the serializer for the registration for the specified class.
	 * @see #getRegistration(Class)
	 * @see Registration#getSerializer() */
	public Serializer getSerializer (Class type) {
		return getRegistration(type).getSerializer();
	}

	// --- Serialization ---

	/** Writes a class and returns its registration.
	 * @param type May be null.
	 * @return Will be null if type is null.
	 * @see ClassResolver#writeClass(Output, Class) */
	public Registration writeClass (Output output, Class type) {
		if (output == null) throw new IllegalArgumentException("output cannot be null.");
		try {
			return classResolver.writeClass(output, type);
		} finally {
			if (depth == 0 && autoReset) reset();
		}
	}

	/** Writes an object using the registered serializer. */
	public void writeObject (Output output, Object object) {
		if (output == null) throw new IllegalArgumentException("output cannot be null.");
		if (object == null) throw new IllegalArgumentException("object cannot be null.");

		/* mobiledui: start */
		if (DUI_DEBUG)
			Log.d(DUI_TAG, "writeObject() start, object = " + object.getClass() + ", objectId = " + object.zObjectId);

		// If the object was already migrated, send only its id.
		if (object.zObjectId != 0 && ((object.zFLUIDFlags & MIGRATED) != 0)
				&& !ViewGroup.LayoutParams.class.isAssignableFrom(object.getClass())) {
			output.writeVarInt(object.zObjectId, true);
			Log.d(DUI_TAG, "writeObject() end bp 2, objectId = " + object.zObjectId);
			return;
		}
		else
			output.writeVarInt(0, true);
		/* mobiledui: end */

		beginObject();
		try {
			if (references && writeReferenceOrNull(output, object, false)) {
				getRegistration(object.getClass()).getSerializer().setGenerics(this, null);
				return;
			}
			getRegistration(object.getClass()).getSerializer().write(this, output, object);
		} finally {
			if (--depth == 0 && autoReset) reset();
		}
		/* mobiledui: start */
		if (DUI_DEBUG) 
			Log.d(DUI_TAG, "writeObject() end, objectId = " + object.zObjectId);
		/* mobiledui: end */
	}

	/** Writes an object using the specified serializer. The registered serializer is ignored. */
	public void writeObject (Output output, Object object, Serializer serializer) {
		if (output == null) throw new IllegalArgumentException("output cannot be null.");
		if (object == null) throw new IllegalArgumentException("object cannot be null.");
		if (serializer == null) throw new IllegalArgumentException("serializer cannot be null.");
		/* mobiledui: start */
		if (DUI_DEBUG) 
			Log.d(DUI_TAG, "writeObject(Serializer) start, object = " + object.getClass() + ", objectId = " + object.zObjectId + ", serializer = " + serializer);

		// If the object was already migrated, send only its id.
		if (object.zObjectId != 0 && ((object.zFLUIDFlags & MIGRATED) != 0)
				&& !ViewGroup.LayoutParams.class.isAssignableFrom(object.getClass())) {
			output.writeVarInt(object.zObjectId, true);
			return;
		}
		else
			output.writeVarInt(0, true);
		/* mobiledui: end */

		beginObject();
		try {
			if (references && writeReferenceOrNull(output, object, false)) {
				serializer.setGenerics(this, null);
				return;
			}
			serializer.write(this, output, object);
		} finally {
			if (--depth == 0 && autoReset) reset();
		}
		/* mobiledui: start */
		if (DUI_DEBUG) 
			Log.d(DUI_TAG, "writeObject(Serializer) end, objectId = " + object.zObjectId);
		/* mobiledui: end */
	}

	/** Writes an object or null using the registered serializer for the specified type.
	 * @param object May be null. */
	public void writeObjectOrNull (Output output, Object object, Class type) {
		if (output == null) throw new IllegalArgumentException("output cannot be null.");
		beginObject();
		try {
			Serializer serializer = getRegistration(type).getSerializer();
			if (references) {
				if (writeReferenceOrNull(output, object, true)) {
					serializer.setGenerics(this, null);
					return;
				}
			} else if (!serializer.getAcceptsNull()) {
				if (object == null) {
					output.writeByte(NULL);
					return;
				}
				output.writeByte(NOT_NULL);
			}
			serializer.write(this, output, object);
		} finally {
			if (--depth == 0 && autoReset) reset();
		}
	}

	/** Writes an object or null using the specified serializer. The registered serializer is ignored.
	 * @param object May be null. */
	public void writeObjectOrNull (Output output, Object object, Serializer serializer) {
		if (output == null) throw new IllegalArgumentException("output cannot be null.");
		if (serializer == null) throw new IllegalArgumentException("serializer cannot be null.");

		/* mobiledui: start */
		if (DUI_DEBUG)
			Log.d(DUI_TAG, "writeObjectOrNull(Serializer) start, object = " + object + ", objectId = " + ((object != null)? object.zObjectId : "null") + ", serializer = " + serializer);

		if (object != null && object.zObjectId != 0) {
			output.writeVarInt(NOT_NULL, true);
			// If the object was already migrated, send only its id.
			if (object != null && object.zObjectId != 0 && (object.zFLUIDFlags & MIGRATED) != 0) {
				output.writeVarInt(object.zObjectId, true);
				return;
			}
			else
				output.writeVarInt(0, true);
		}
		else
			output.writeVarInt(NULL, true);
		/* mobiledui: end */

		beginObject();
		try {
			if (references) {
				if (writeReferenceOrNull(output, object, true)) {
					serializer.setGenerics(this, null);
					return;
				}
			} else if (!serializer.getAcceptsNull()) {
				if (object == null) {
					output.writeByte(NULL);
					return;
				}
				output.writeByte(NOT_NULL);
			}
			serializer.write(this, output, object);
		} finally {
			if (--depth == 0 && autoReset) reset();
		}
		/* mobiledui: start */
		if (DUI_DEBUG) 
			Log.d(DUI_TAG, "writeObjectOrNull(Serializer) end, objectId = " + object.zObjectId);
		/* mobiledui: end */
	}

	/** Writes the class and object or null using the registered serializer.
	 * @param object May be null. */
	public void writeClassAndObject (Output output, Object object) {
		if (output == null) throw new IllegalArgumentException("output cannot be null.");
		
		/* mobiledui: start */
		if (DUI_DEBUG) {
			Log.d(DUI_TAG, "writeClassAndObject() start, object = " + object
					+ ", class = " + ((object != null)? object.getClass() : "null")
					+ ", objectId = " + ((object != null)? object.zObjectId : "null"));
		}
		/* mobiledui: end */

		beginObject();
		try {
			if (object == null) {
				writeClass(output, null);
				return;
			}
			Registration registration = writeClass(output, object.getClass());

			/* mobiledui: start */
			// If the object was already migrated, send only its id.
			if (object.zObjectId != 0 && (object.zFLUIDFlags & MIGRATED) != 0) {
				output.writeVarInt(object.zObjectId, true);
				return;
			}
			else
				output.writeVarInt(0, true);
			/* mobiledui: end */

			if (references && writeReferenceOrNull(output, object, false)) {
				registration.getSerializer().setGenerics(this, null);
				/* mobiledui: start */
				if (DUI_DEBUG) Log.d(DUI_TAG, "writeClassAndObject() end 2, objectId = " + object.zObjectId);
				/* mobiledui: end */
				return;
			}

			registration.getSerializer().write(this, output, object);
		} finally {
			if (--depth == 0 && autoReset) reset();
		}
		/* mobiledui: start */
		if (DUI_DEBUG) Log.d(DUI_TAG, "writeClassAndObject() end, objectId = " + object.zObjectId);
		/* mobiledui: end */
	}

	/** @param object May be null if mayBeNull is true.
	 * @return true if no bytes need to be written for the object. */
	boolean writeReferenceOrNull (Output output, Object object, boolean mayBeNull) {
		if (object == null) {
			output.writeVarInt(Kryo.NULL, true);
			return true;
		}
		if (!referenceResolver.useReferences(object.getClass())) {
			if (mayBeNull) output.writeVarInt(Kryo.NOT_NULL, true);
			return false;
		}

		// Determine if this object has already been seen in this object graph.
		int id = referenceResolver.getWrittenId(object);

		// If not the first time encountered, only write reference ID.
		if (id != -1) {
			output.writeVarInt(id + 2, true); // + 2 because 0 and 1 are used for NULL and NOT_NULL.
			return true;
		}

		// Otherwise write NOT_NULL and then the object bytes.
		id = referenceResolver.addWrittenObject(object);
		output.writeVarInt(NOT_NULL, true);
		return false;
	}

	/** Reads a class and returns its registration.
	 * @return May be null.
	 * @see ClassResolver#readClass(Input) */
	public Registration readClass (Input input) {
		if (input == null) throw new IllegalArgumentException("input cannot be null.");
		try {
			return classResolver.readClass(input);
		} finally {
			if (depth == 0 && autoReset) reset();
		}
	}

	/* mobiledui: start */
	public Object readObject (Input input, Class type, Object object) {
		if (input == null) throw new IllegalArgumentException("input cannot be null.");
		if (type == null) throw new IllegalArgumentException("type cannot be null.");

		// If the object was already migrated, find it.
		int objectId = input.readVarInt(true);
		if (DUI_DEBUG) 
			Log.d(DUI_TAG, "readObject(Object) start, type = " + type + ", objectId = " + objectId);
		if (objectId != 0) {
			return mIdToObj.get(objectId);
		}

		beginObject();
		try {
			if (references) {
				int stackSize = readReferenceOrNull(input, type, false);
				if (stackSize == REF) {
					object = readObject;
					return readObject;
				}
			}
			getRegistration(type).getSerializer().read(this, input, type, object);
			if (DUI_DEBUG)
				Log.d(DUI_TAG, "readObject(Object) end, objectId = " + object.zObjectId);
		} finally {
			if (--depth == 0 && autoReset) reset();
		}
		return object;
	}
	/* mobiledui: end */

	/** Reads an object using the registered serializer. */
	public <T> T readObject (Input input, Class<T> type) {
		if (input == null) throw new IllegalArgumentException("input cannot be null.");
		if (type == null) throw new IllegalArgumentException("type cannot be null.");

		/* mobiledui: start */
		int objectId = input.readVarInt(true);
		if (DUI_DEBUG) 
			Log.d(DUI_TAG, "readObject() start, type = " + type + ", objectId = " + objectId);
		if (objectId != 0) {
			return (T) mIdToObj.get(objectId);
		}
		/* mobiledui: end */

		beginObject();
		try {
			T object;
			if (references) {
				int stackSize = readReferenceOrNull(input, type, false);
				if (stackSize == REF) return (T)readObject;

				object = (T)getRegistration(type).getSerializer().read(this, input, type);
				if (stackSize == readReferenceIds.size) reference(object);
			} else {
				object = (T)getRegistration(type).getSerializer().read(this, input, type);
			}

			/* mobiledui: start */
			if (DUI_DEBUG)
				Log.d(DUI_TAG, "readObject() end, objectId = " + object.zObjectId);
			/* mobiledui: end */

			return object;
		} finally {
			if (--depth == 0 && autoReset) reset();
		}
	}

	/** Reads an object using the specified serializer. The registered serializer is ignored. */
	public <T> T readObject (Input input, Class<T> type, Serializer serializer) {
		if (input == null) throw new IllegalArgumentException("input cannot be null.");
		if (type == null) throw new IllegalArgumentException("type cannot be null.");
		if (serializer == null) throw new IllegalArgumentException("serializer cannot be null.");

		/* mobiledui: start */
		int objectId = input.readVarInt(true);
		if (DUI_DEBUG) { 
			Log.d(DUI_TAG, "readObject(Serializer) start, type = " + type 
					+ ", serializer = " + serializer + ", objectId = " + objectId);
		}
		if (objectId != 0) {
			return (T) mIdToObj.get(objectId);
		}
		/* mobiledui: end */

		beginObject();
		try {
			T object;
			if (references) {
				int stackSize = readReferenceOrNull(input, type, false);
				if (stackSize == REF) return (T)readObject;

				object = (T)serializer.read(this, input, type);

				if (stackSize == readReferenceIds.size) reference(object);
			} else {
				object = (T)serializer.read(this, input, type);
			}

			/* mobiledui: start */
			if (DUI_DEBUG) 
				Log.d(DUI_TAG, "readObject(Serializer) end, objectId = " + object.zObjectId);
			/* mobiledui: end */
			
			return object;
		} finally {
			if (--depth == 0 && autoReset) reset();
		}
	}

	/** Reads an object or null using the registered serializer.
	 * @return May be null. */
	public <T> T readObjectOrNull (Input input, Class<T> type) {
		if (input == null) throw new IllegalArgumentException("input cannot be null.");
		if (type == null) throw new IllegalArgumentException("type cannot be null.");
		beginObject();
		try {
			T object;
			if (references) {
				int stackSize = readReferenceOrNull(input, type, true);
				if (stackSize == REF) return (T)readObject;
				object = (T)getRegistration(type).getSerializer().read(this, input, type);
				if (stackSize == readReferenceIds.size) reference(object);
			} else {
				Serializer serializer = getRegistration(type).getSerializer();
				if (!serializer.getAcceptsNull() && input.readByte() == NULL) {
					return null;
				}
				object = (T)serializer.read(this, input, type);
			}
			return object;
		} finally {
			if (--depth == 0 && autoReset) reset();
		}
	}

	/** Reads an object or null using the specified serializer. The registered serializer is ignored.
	 * @return May be null. */
	public <T> T readObjectOrNull (Input input, Class<T> type, Serializer serializer) {
		if (input == null) throw new IllegalArgumentException("input cannot be null.");
		if (type == null) throw new IllegalArgumentException("type cannot be null.");
		if (serializer == null) throw new IllegalArgumentException("serializer cannot be null.");

		/* mobiledui: start */
		if (DUI_DEBUG) 
			Log.d(DUI_TAG, "readObjectOrNull(Serializer) start, type = " + type + ", serializer = " + serializer);
		int isNotNull = input.readVarInt(true);
		int objectId = 0;
		if (isNotNull == 1) {
			objectId = input.readVarInt(true);
			if (objectId != 0) {
				return (T) mIdToObj.get(objectId);
			}
		}
		/* mobiledui: end */

		beginObject();
		try {
			T object;
			if (references) {
				int stackSize = readReferenceOrNull(input, type, true);
				if (stackSize == REF) return (T)readObject;
				object = (T)serializer.read(this, input, type);

				if (stackSize == readReferenceIds.size) reference(object);
			} else {
				if (!serializer.getAcceptsNull() && input.readByte() == NULL) {
					return null;
				}
				object = (T)serializer.read(this, input, type);
			}
			/* mobiledui: start */
			if (DUI_DEBUG) 
				Log.d(DUI_TAG, "readObjectOrNull(Serializer) end, objectId = " + object.zObjectId);
			/* mobiledui: end */
			return object;
		} finally {
			if (--depth == 0 && autoReset) reset();
		}
	}

	/** Reads the class and object or null using the registered serializer.
	 * @return May be null. */
	public Object readClassAndObject (Input input) {
		if (input == null) throw new IllegalArgumentException("input cannot be null.");

		/* mobiledui: start */
		if (DUI_DEBUG) Log.d(DUI_TAG, "readClassAndObject() start");
		/* mobiledui: end */

		beginObject();
		try {
			Registration registration = readClass(input);
			if (registration == null) return null;
			Class type = registration.getType();

			/* mobiledui: start */
			int objectId = input.readVarInt(true);
			if (objectId != 0) {
				return mIdToObj.get(objectId);
			}
			/* mobiledui: end */

			Object object;
			if (references) {
				registration.getSerializer().setGenerics(this, null);
				int stackSize = readReferenceOrNull(input, type, false);
				if (stackSize == REF) return readObject;

				object = registration.getSerializer().read(this, input, type);
				if (stackSize == readReferenceIds.size) reference(object);
			} else
				object = registration.getSerializer().read(this, input, type);
			/* mobiledui: start */
			if (DUI_DEBUG) Log.d(DUI_TAG, "readClassAndObject() end, objectId = " + ((object != null)? object.zObjectId : -1));
			/* mobiledui: end */
			return object;
		} finally {
			if (--depth == 0 && autoReset) reset();
		}
	}

	/** Returns {@link #REF} if a reference to a previously read object was read, which is stored in {@link #readObject}. Returns a
	 * stack size (> 0) if a reference ID has been put on the stack. */
	int readReferenceOrNull (Input input, Class type, boolean mayBeNull) {
		if (type.isPrimitive()) type = getWrapperClass(type);
		boolean referencesSupported = referenceResolver.useReferences(type);
		int id;
		if (mayBeNull) {
			id = input.readVarInt(true);
			if (id == Kryo.NULL) {
				readObject = null;
				return REF;
			}
			if (!referencesSupported) {
				readReferenceIds.add(NO_REF);
				return readReferenceIds.size;
			}
		} else {
			if (!referencesSupported) {
				readReferenceIds.add(NO_REF);
				return readReferenceIds.size;
			}
			id = input.readVarInt(true);
		}
		if (id == NOT_NULL) {
			// First time object has been encountered.
			id = referenceResolver.nextReadId(type);
			readReferenceIds.add(id);
			return readReferenceIds.size;
		}
		// The id is an object reference.
		id -= 2; // - 2 because 0 and 1 are used for NULL and NOT_NULL.
		readObject = referenceResolver.getReadObject(type, id);
		return REF;
	}

	/** Called by {@link Serializer#read(Kryo, Input, Class)} and {@link Serializer#copy(Kryo, Object)} before Kryo can be used to
	 * deserialize or copy child objects. Calling this method is unnecessary if Kryo is not used to deserialize or copy child
	 * objects.
	 * @param object May be null, unless calling this method from {@link Serializer#copy(Kryo, Object)}. */
	public void reference (Object object) {
		if (copyDepth > 0) {
			if (needsCopyReference != null) {
				if (object == null) throw new IllegalArgumentException("object cannot be null.");
				originalToCopy.put(needsCopyReference, object);
				needsCopyReference = null;
			}
		} else if (references && object != null) {
			int id = readReferenceIds.pop();
			if (id != NO_REF) referenceResolver.setReadObject(id, object);
		}
	}

	/** Resets unregistered class names, references to previously serialized or deserialized objects, and the
	 * {@link #getGraphContext() graph context}. If {@link #setAutoReset(boolean) auto reset} is true, this method is called
	 * automatically when an object graph has been completely serialized or deserialized. If overridden, the super method must be
	 * called. */
	public void reset () {
		depth = 0;
		if (graphContext != null) graphContext.clear();
		classResolver.reset();
		if (references) {
			referenceResolver.reset();
			readObject = null;
		}

		copyDepth = 0;
		if (originalToCopy != null) originalToCopy.clear(2048);
	}

	/** Returns a deep copy of the object. Serializers for the classes involved must support {@link Serializer#copy(Kryo, Object)}.
	 * @param object May be null. */
	public <T> T copy (T object) {
		if (object == null) return null;
		if (copyShallow) return object;
		copyDepth++;
		try {
			if (originalToCopy == null) originalToCopy = new IdentityMap();
			Object existingCopy = originalToCopy.get(object);
			if (existingCopy != null) return (T)existingCopy;

			if (copyReferences) needsCopyReference = object;
			Object copy;
			if (object instanceof KryoCopyable)
				copy = ((KryoCopyable)object).copy(this);
			else
				copy = getSerializer(object.getClass()).copy(this, object);
			if (needsCopyReference != null) reference(copy);
			return (T)copy;
		} finally {
			if (--copyDepth == 0) reset();
		}
	}

	/** Returns a deep copy of the object using the specified serializer. Serializers for the classes involved must support
	 * {@link Serializer#copy(Kryo, Object)}.
	 * @param object May be null. */
	public <T> T copy (T object, Serializer serializer) {
		if (object == null) return null;
		if (copyShallow) return object;
		copyDepth++;
		try {
			if (originalToCopy == null) originalToCopy = new IdentityMap();
			Object existingCopy = originalToCopy.get(object);
			if (existingCopy != null) return (T)existingCopy;

			if (copyReferences) needsCopyReference = object;
			Object copy;
			if (object instanceof KryoCopyable)
				copy = ((KryoCopyable)object).copy(this);
			else
				copy = serializer.copy(this, object);
			if (needsCopyReference != null) reference(copy);
			return (T)copy;
		} finally {
			if (--copyDepth == 0) reset();
		}
	}

	/** Returns a shallow copy of the object. Serializers for the classes involved must support
	 * {@link Serializer#copy(Kryo, Object)}.
	 * @param object May be null. */
	public <T> T copyShallow (T object) {
		if (object == null) return null;
		copyDepth++;
		copyShallow = true;
		try {
			if (originalToCopy == null) originalToCopy = new IdentityMap();
			Object existingCopy = originalToCopy.get(object);
			if (existingCopy != null) return (T)existingCopy;

			if (copyReferences) needsCopyReference = object;
			Object copy;
			if (object instanceof KryoCopyable)
				copy = ((KryoCopyable)object).copy(this);
			else
				copy = getSerializer(object.getClass()).copy(this, object);
			if (needsCopyReference != null) reference(copy);
			return (T)copy;
		} finally {
			copyShallow = false;
			if (--copyDepth == 0) reset();
		}
	}

	/** Returns a shallow copy of the object using the specified serializer. Serializers for the classes involved must support
	 * {@link Serializer#copy(Kryo, Object)}.
	 * @param object May be null. */
	public <T> T copyShallow (T object, Serializer serializer) {
		if (object == null) return null;
		copyDepth++;
		copyShallow = true;
		try {
			if (originalToCopy == null) originalToCopy = new IdentityMap();
			Object existingCopy = originalToCopy.get(object);
			if (existingCopy != null) return (T)existingCopy;

			if (copyReferences) needsCopyReference = object;
			Object copy;
			if (object instanceof KryoCopyable)
				copy = ((KryoCopyable)object).copy(this);
			else
				copy = serializer.copy(this, object);
			if (needsCopyReference != null) reference(copy);
			return (T)copy;
		} finally {
			copyShallow = false;
			if (--copyDepth == 0) reset();
		}
	}

	// --- Utility ---

	private void beginObject () {
		if (depth == maxDepth) throw new KryoException("Max depth exceeded: " + depth);
		depth++;
	}

	public ClassResolver getClassResolver () {
		return classResolver;
	}

	/** @return May be null. */
	public ReferenceResolver getReferenceResolver () {
		return referenceResolver;
	}

	/** Sets the classloader to resolve unregistered class names to classes. The default is the loader that loaded the Kryo
	 * class. */
	public void setClassLoader (ClassLoader classLoader) {
		if (classLoader == null) throw new IllegalArgumentException("classLoader cannot be null.");
		this.classLoader = classLoader;
	}

	public ClassLoader getClassLoader () {
		return classLoader;
	}

	/** If true, an exception is thrown when an unregistered class is encountered. Default is false.
	 * <p>
	 * If false, when an unregistered class is encountered, its fully qualified class name will be serialized and the
	 * {@link #addDefaultSerializer(Class, Class) default serializer} for the class used to serialize the object. Subsequent
	 * appearances of the class within the same object graph are serialized as an int id.
	 * <p>
	 * Registered classes are serialized as an int id, avoiding the overhead of serializing the class name, but have the drawback
	 * of needing to know the classes to be serialized up front. */
	public void setRegistrationRequired (boolean registrationRequired) {
		this.registrationRequired = registrationRequired;
	}

	public boolean isRegistrationRequired () {
		return registrationRequired;
	}

	/** If true, kryo writes a warn log telling about the classes unregistered. Default is false.
	 * <p>
	 * If false, no log are written when unregistered classes are encountered.
	 * </p>
	*/
	public void setWarnUnregisteredClasses (boolean warnUnregisteredClasses) {
		this.warnUnregisteredClasses = warnUnregisteredClasses;
	}

	public boolean isWarnUnregisteredClasses () {
		return warnUnregisteredClasses;
	}

	/** If true, each appearance of an object in the graph after the first is stored as an integer ordinal. When set to true,
	 * {@link MapReferenceResolver} is used. This enables references to the same object and cyclic graphs to be serialized, but
	 * typically adds overhead of one byte per object. Default is true.
	 * @return The previous value. */
	public boolean setReferences (boolean references) {
		if (references == this.references) return references;
		this.references = references;
		if (references && referenceResolver == null) referenceResolver = new MapReferenceResolver();
		return !references;
	}

	/** If true, when {@link #copy(Object)} and other copy methods encounter an object for the first time the object is copied and
	 * on subsequent encounters the copied object is used. If false, the overhead of tracking which objects have already been
	 * copied is avoided because each object is copied every time it is encountered, however a stack overflow will occur if an
	 * object graph is copied that contains a circular reference. Default is true. */
	public void setCopyReferences (boolean copyReferences) {
		this.copyReferences = copyReferences;
	}

	/** The default configuration for {@link FieldSerializer} instances. Already existing serializer instances (e.g. implicitely
	 * created for already registered classes) are not affected by this configuration. You can override the configuration for a
	 * single {@link FieldSerializer}. */
	public FieldSerializerConfig getFieldSerializerConfig () {
		return fieldSerializerConfig;
	}

	public TaggedFieldSerializerConfig getTaggedFieldSerializerConfig () {
		return taggedFieldSerializerConfig;
	}

	/** Sets the reference resolver and enables references. */
	public void setReferenceResolver (ReferenceResolver referenceResolver) {
		if (referenceResolver == null) throw new IllegalArgumentException("referenceResolver cannot be null.");
		this.references = true;
		this.referenceResolver = referenceResolver;
	}

	public boolean getReferences () {
		return references;
	}

	/** Sets the strategy used by {@link #newInstantiator(Class)} for creating objects. See {@link StdInstantiatorStrategy} to
	 * create objects via without calling any constructor. See {@link SerializingInstantiatorStrategy} to mimic Java's built-in
	 * serialization.
	 * @param strategy May be null. */
	public void setInstantiatorStrategy (InstantiatorStrategy strategy) {
		this.strategy = strategy;
	}

	public InstantiatorStrategy getInstantiatorStrategy () {
		return strategy;
	}

	/** Returns a new instantiator for creating new instances of the specified type. By default, an instantiator is returned that
	 * uses reflection if the class has a zero argument constructor, an exception is thrown. If a
	 * {@link #setInstantiatorStrategy(InstantiatorStrategy) strategy} is set, it will be used instead of throwing an exception. */
	protected ObjectInstantiator newInstantiator (final Class type) {
		// InstantiatorStrategy.
		return strategy.newInstantiatorOf(type);
	}

	/** Creates a new instance of a class using {@link Registration#getInstantiator()}. If the registration's instantiator is null,
	 * a new one is set using {@link #newInstantiator(Class)}. */
	public <T> T newInstance (Class<T> type) {
		Registration registration = getRegistration(type);
		ObjectInstantiator instantiator = registration.getInstantiator();
		if (instantiator == null) {
			instantiator = newInstantiator(type);
			registration.setInstantiator(instantiator);
		}
		return (T)instantiator.newInstance();
	}

	/** Name/value pairs that are available to all serializers. */
	public ObjectMap getContext () {
		if (context == null) context = new ObjectMap();
		return context;
	}

	/** Name/value pairs that are available to all serializers and are cleared after each object graph is serialized or
	 * deserialized. */
	public ObjectMap getGraphContext () {
		if (graphContext == null) graphContext = new ObjectMap();
		return graphContext;
	}

	/** Returns the number of child objects away from the object graph root. */
	public int getDepth () {
		return depth;
	}

	/** Returns the internal map of original to copy objects when a copy method is used. This can be used after a copy to map old
	 * objects to the copies, however it is cleared automatically by {@link #reset()} so this is only useful when
	 * {@link #setAutoReset(boolean)} is false. */
	public IdentityMap getOriginalToCopyMap () {
		return originalToCopy;
	}

	/** If true (the default), {@link #reset()} is called automatically after an entire object graph has been read or written. If
	 * false, {@link #reset()} must be called manually, which allows unregistered class names, references, and other information to
	 * span multiple object graphs. */
	public void setAutoReset (boolean autoReset) {
		this.autoReset = autoReset;
	}

	/** Sets the maxiumum depth of an object graph. This can be used to prevent malicious data from causing a stack overflow.
	 * Default is {@link Integer#MAX_VALUE}. */
	public void setMaxDepth (int maxDepth) {
		if (maxDepth <= 0) throw new IllegalArgumentException("maxDepth must be > 0.");
		this.maxDepth = maxDepth;
	}

	/** Returns true if the specified type is final. Final types can be serialized more efficiently because they are
	 * non-polymorphic.
	 * <p>
	 * This can be overridden to force non-final classes to be treated as final. Eg, if an application uses ArrayList extensively
	 * but never uses an ArrayList subclass, treating ArrayList as final could allow FieldSerializer to save 1-2 bytes per
	 * ArrayList field. */
	public boolean isFinal (Class type) {
		if (type == null) throw new IllegalArgumentException("type cannot be null.");
		if (type.isArray()) return Modifier.isFinal(Util.getElementClass(type).getModifiers());
		return Modifier.isFinal(type.getModifiers());
	}

	/** Returns true if the specified type is a closure.
	 * <p>
	 * This can be overridden to support alternative implementations of clousres. Current version supports Oracle's Java8 only */
	protected boolean isClosure (Class type) {
		if (type == null) throw new IllegalArgumentException("type cannot be null.");
		return type.getName().indexOf('/') >= 0;
	}

	static final class DefaultSerializerEntry {
		final Class type;
		final SerializerFactory serializerFactory;

		DefaultSerializerEntry (Class type, SerializerFactory serializerFactory) {
			this.type = type;
			this.serializerFactory = serializerFactory;
		}
	}

	public GenericsResolver getGenericsResolver () {
		return genericsResolver;
	}

	public StreamFactory getStreamFactory () {
		return streamFactory;
	}

	public void setStreamFactory (StreamFactory streamFactory) {
		this.streamFactory = streamFactory;
	}

	/** Tells Kryo, if ASM-based backend should be used by new serializer instances created using this Kryo instance. Already
	 * existing serializer instances are not affected by this setting.
	 * 
	 * <p>
	 * By default, Kryo uses ASM-based backend.
	 * </p>
	 * 
	 * @param flag if true, ASM-based backend will be used. Otherwise Unsafe-based backend could be used by some serializers, e.g.
	 *           FieldSerializer
	 *
	 * @deprecated Use {@link #getFieldSerializerConfig()} to change the default {@link FieldSerializer} configuration. */
	@Deprecated
	public void setAsmEnabled (boolean flag) {
		fieldSerializerConfig.setUseAsm(flag);
	}

	/** @deprecated Use {@link #getFieldSerializerConfig()} to change the default {@link FieldSerializer} configuration. */
	@Deprecated
	public boolean getAsmEnabled () {
		return fieldSerializerConfig.isUseAsm();
	}

	static public class DefaultInstantiatorStrategy implements android.fluid.objenesis.strategy.InstantiatorStrategy {
		private InstantiatorStrategy fallbackStrategy;

		public DefaultInstantiatorStrategy () {
		}

		public DefaultInstantiatorStrategy (InstantiatorStrategy fallbackStrategy) {
			this.fallbackStrategy = fallbackStrategy;
		}

		public void setFallbackInstantiatorStrategy (final InstantiatorStrategy fallbackStrategy) {
			this.fallbackStrategy = fallbackStrategy;
		}

		public InstantiatorStrategy getFallbackInstantiatorStrategy () {
			return fallbackStrategy;
		}

		public ObjectInstantiator newInstantiatorOf (final Class type) {
			if (!Util.IS_ANDROID) {
				// Use ReflectASM if the class is not a non-static member class.
				Class enclosingType = type.getEnclosingClass();
				boolean isNonStaticMemberClass = enclosingType != null && type.isMemberClass()
					&& !Modifier.isStatic(type.getModifiers());
				if (!isNonStaticMemberClass) {
					try {
						final ConstructorAccess access = ConstructorAccess.get(type);
						return new ObjectInstantiator() {
							public Object newInstance () {
								try {
									return access.newInstance();
								} catch (Exception ex) {
									throw new KryoException("Error constructing instance of class: " + className(type), ex);
								}
							}
						};
					} catch (Exception ignored) {
					}
				}
			}
			// Reflection.
			try {
				Constructor ctor;
				try {
					ctor = type.getConstructor((Class[])null);
				} catch (Exception ex) {
					ctor = type.getDeclaredConstructor((Class[])null);
					ctor.setAccessible(true);
				}
				final Constructor constructor = ctor;
				return new ObjectInstantiator() {
					public Object newInstance () {
						try {
							/* mobiledui: start */
						    constructor.setAccessible(true);
							/* mobiledui: end */
							return constructor.newInstance();
						} catch (Exception ex) {
							throw new KryoException("Error constructing instance of class: " + className(type), ex);
						}
					}
				};
			} catch (Exception ignored) {
			}
			if (fallbackStrategy == null) {
				if (type.isMemberClass() && !Modifier.isStatic(type.getModifiers()))
					throw new KryoException("Class cannot be created (non-static member class): " + className(type));
				else {
					StringBuilder errorMessageSb = new StringBuilder("Class cannot be created (missing no-arg constructor): " + className(type));
					if (type.getSimpleName().equals("")) {
						errorMessageSb.append("\n\tThis is an anonymous class, which is not serializable by default in Kryo. Possible solutions: ")
							.append("1. Remove uses of anonymous classes, including double brace initialization, from the containing ")
							.append("class. This is the safest solution, as anonymous classes don't have predictable names for serialization.")
							.append("\n\t2. Register a FieldSerializer for the containing class and call ")
							.append( "FieldSerializer#setIgnoreSyntheticFields(false) on it. This is not safe but may be sufficient temporarily. ")
							.append("Use at your own risk.");
					}
					throw new KryoException(errorMessageSb.toString());
				}
			}
			// InstantiatorStrategy.
			return fallbackStrategy.newInstantiatorOf(type);
		}
	}

}
