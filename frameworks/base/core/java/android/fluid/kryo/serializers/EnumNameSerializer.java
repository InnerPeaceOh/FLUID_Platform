
package android.fluid.kryo.serializers;

import android.fluid.kryo.Kryo;
import android.fluid.kryo.KryoException;
import android.fluid.kryo.Serializer;
import android.fluid.kryo.io.Input;
import android.fluid.kryo.io.Output;

/** Serializes enums using the enum's name. This prevents invalidating previously serialized byts when the enum order changes.
 * @author KwonNam Son <kwon37xi@gmail.com> */
/** @hide */
public class EnumNameSerializer extends Serializer<Enum> {
	private final Class<? extends Enum> enumType;
	private final Serializer stringSerializer;

	public EnumNameSerializer (Kryo kryo, Class<? extends Enum> type) {
		this.enumType = type;
		stringSerializer = kryo.getSerializer(String.class);
		setImmutable(true);
	}

	public void write (Kryo kryo, Output output, Enum object) {
		kryo.writeObject(output, object.name(), stringSerializer);
	}

	public Enum read (Kryo kryo, Input input, Class<Enum> type) {
		String name = kryo.readObject(input, String.class, stringSerializer);
		try {
			return Enum.valueOf(enumType, name);
		} catch (IllegalArgumentException e) {
			throw new KryoException("Invalid name for enum \"" + enumType.getName() + "\": " + name, e);
		}
	}
}
