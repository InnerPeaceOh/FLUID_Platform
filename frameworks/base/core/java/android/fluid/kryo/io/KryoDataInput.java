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

package android.fluid.kryo.io;

import java.io.DataInput;
import java.io.EOFException;
import java.io.IOException;

import android.fluid.kryo.KryoException;

/** Best attempt adapter for {@link DataInput}. Currently only {@link #readLine()} is unsupported. Other methods behave slightly
 * differently. For example, {@link #readUTF()} may return a null string.
 *
 * @author Robert DiFalco <robert.difalco@gmail.com> */
/** @hide */
public class KryoDataInput implements DataInput {

	protected Input input;

	public KryoDataInput (Input input) {
		setInput(input);
	}

	public void setInput (Input input) {
		this.input = input;
	}

	public void readFully (byte[] b) throws IOException {
		readFully(b, 0, b.length);
	}

	public void readFully (byte[] b, int off, int len) throws IOException {
		try {
			input.readBytes(b, off, len);
		} catch (KryoException e) {
			throw new EOFException(e.getMessage());
		}
	}

	public int skipBytes (int n) throws IOException {
		return (int)input.skip((long)n);
	}

	public boolean readBoolean () throws IOException {
		return input.readBoolean();
	}

	public byte readByte () throws IOException {
		return input.readByte();
	}

	public int readUnsignedByte () throws IOException {
		return input.readByteUnsigned();
	}

	public short readShort () throws IOException {
		return input.readShort();
	}

	public int readUnsignedShort () throws IOException {
		return input.readShortUnsigned();
	}

	public char readChar () throws IOException {
		return input.readChar();
	}

	public int readInt () throws IOException {
		return input.readInt();
	}

	public long readLong () throws IOException {
		return input.readLong();
	}

	public float readFloat () throws IOException {
		return input.readFloat();
	}

	public double readDouble () throws IOException {
		return input.readDouble();
	}

	/** This is not currently implemented. The method will currently throw an {@link java.lang.UnsupportedOperationException}
	 * whenever it is called.
	 *
	 * @throws UnsupportedOperationException when called.
	 * @deprecated this method is not supported in this implementation. */
	public String readLine () throws UnsupportedOperationException {
		throw new UnsupportedOperationException();
	}

	/** Reads the length and string of UTF8 characters, or null. This can read strings written by
	 * {@link KryoDataOutput#writeUTF(String)}, {@link android.fluid.kryo.io.Output#writeString(String)},
	 * {@link android.fluid.kryo.io.Output#writeString(CharSequence)}, and
	 * {@link android.fluid.kryo.io.Output#writeAscii(String)}.
	 *
	 * @return May be null. */
	public String readUTF () throws IOException {
		return input.readString();
	}
}
