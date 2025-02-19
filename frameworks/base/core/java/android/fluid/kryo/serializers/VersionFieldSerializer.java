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
import java.lang.reflect.Field;

import android.fluid.kryo.Kryo;
import android.fluid.kryo.KryoException;
import android.fluid.kryo.io.Input;
import android.fluid.kryo.io.Output;

/** Serializes objects using direct field assignment, with versioning backward compatibility. Allows fields to have a
 * <code>@Since(int)</code> annotation to indicate the version they were added. For a particular field, the value in
 * <code>@Since</code> should never change once created. This is less flexible than FieldSerializer, which can handle most classes
 * without needing annotations, but it provides backward compatibility. This means that new fields can be added, but removing,
 * renaming or changing the type of any field will invalidate previous serialized bytes. VersionFieldSerializer has very little
 * overhead (a single additional varint) compared to FieldSerializer. Forward compatibility is not supported.
 * @see TaggedFieldSerializer
 * @author Tianyi HE <hty0807@gmail.com> */
/** @hide */
public class VersionFieldSerializer<T> extends FieldSerializer<T> {
	private int typeVersion = 0; // Version of current type.
	private int[] fieldVersion; // Version of each field.
	private boolean compatible = true; // Whether current type is compatible with serialized objects with different version.

	public VersionFieldSerializer (Kryo kryo, Class type) {
		super(kryo, type);
		// Make sure this is done before any read / write operations.
		initializeCachedFields();
	}

	public VersionFieldSerializer (Kryo kryo, Class type, boolean compatible) {
		this(kryo, type);
		this.compatible = compatible;
	}

	@Override
	protected void initializeCachedFields () {
		CachedField[] fields = getFields();
		fieldVersion = new int[fields.length];
		for (int i = 0, n = fields.length; i < n; i++) {
			Field field = fields[i].getField();
			Since since = field.getAnnotation(Since.class);
			if (since != null) {
				fieldVersion[i] = since.value();
				// Use the maximum version among fields as the entire type's version.
				typeVersion = Math.max(fieldVersion[i], typeVersion);
			} else {
				fieldVersion[i] = 0;
			}
		}
		this.removedFields.clear();
	}

	@Override
	public void removeField (String fieldName) {
		super.removeField(fieldName);
		initializeCachedFields();
	}

	@Override
	public void removeField (CachedField field) {
		super.removeField(field);
		initializeCachedFields();
	}

	@Override
	public void write (Kryo kryo, Output output, T object) {
		CachedField[] fields = getFields();
		// Write type version.
		output.writeVarInt(typeVersion, true);
		// Write fields.
		for (int i = 0, n = fields.length; i < n; i++) {
			fields[i].write(output, object);
		}
	}

	@Override
	public T read (Kryo kryo, Input input, Class<T> type) {
		T object = create(kryo, input, type);
		kryo.reference(object);

		// Read input version.
		int version = input.readVarInt(true);
		if (!compatible && version != typeVersion) {
			// Reject to read
			throw new KryoException("Version not compatible: " + version + " <-> " + typeVersion);
		}
		CachedField[] fields = getFields();
		for (int i = 0, n = fields.length; i < n; i++) {
			// Field is not present in input, skip it.
			if (fieldVersion[i] > version) {
				continue;
			}
			fields[i].read(input, object);
		}
		return object;
	}

	/** Incremental modification of serialized objects must add {@link Since} for new fields. */
	@Retention(RetentionPolicy.RUNTIME)
	@Target(ElementType.FIELD)
	public @interface Since {
		/** Version of annotated field, default is 0, and must be incremental to maintain compatibility. */
		int value() default 0;
	}
}
