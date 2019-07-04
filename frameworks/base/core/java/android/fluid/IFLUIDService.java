package android.fluid;

import android.os.Binder;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.Message;
import android.os.Bundle;
import android.util.Log;
import android.view.InputEvent;
import android.view.MotionEvent;
import android.view.KeyEvent;
import com.android.internal.os.SomeArgs;
import android.text.TextUtils;

/** @hide */
public interface IFLUIDService extends IInterface {
	public static abstract class Stub extends Binder implements IFLUIDService {
		private static final String DESCRIPTOR = "android.fluid.IFLUIDService";
		
		// For sendIMEInput
		private static final int DO_GET_EXTRACTED_TEXT = 40;
		private static final int DO_COMMIT_TEXT = 50;
		private static final int DO_SET_COMPOSING_TEXT = 60;
		private static final int DO_PERFORM_PRIVATE_COMMAND = 120;
		private static final int DO_COMMIT_CONTENT = 160;

		public Stub() {
			this.attachInterface(this, DESCRIPTOR);
		}

		public static IFLUIDService asInterface(IBinder obj) {
			if (obj == null) {
				return null;
			}
			IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
			if (iin != null && iin instanceof IFLUIDService) {
				return (IFLUIDService)iin;
			}
			return new IFLUIDService.Stub.Proxy(obj);
		}

		@Override 
		public IBinder asBinder() {
			return this;
		}

		private static class Proxy implements IFLUIDService {
			private IBinder mRemote;

			Proxy(IBinder remote) {
				mRemote = remote;
			}

			@Override 
			public IBinder asBinder() {
				return mRemote;
			}

			public String getInterfaceDescriptor() {
				return DESCRIPTOR;
			}

			@Override 
			public void registerAppBinders(IBinder appThread, IBinder token) throws RemoteException
			{
				Parcel data = Parcel.obtain();
				Parcel reply = Parcel.obtain();
				try {
					data.writeInterfaceToken(DESCRIPTOR);
					data.writeStrongBinder(appThread);
					data.writeStrongBinder(token);
					mRemote.transact(Stub.REGISTER_APP_BINDERS_TRANSACTION, data, reply, 0);
					reply.readException();
				}
				finally {
					reply.recycle();
					data.recycle();
				}
			}

			@Override 
			public int sendUi(String packageName, byte[] viewData, String[] classNames) 
					throws RemoteException {
				Log.d("MOBILEDUI(IFLUIDService.java)", "sendUi(), viewData size = " + viewData.length);
				Parcel data = Parcel.obtain();
				Parcel reply = Parcel.obtain();
				int result;
				try {
					data.writeInterfaceToken(DESCRIPTOR);
					data.writeString(packageName);
					data.writeToAshmem(viewData);
					data.writeStringArray(classNames);
					mRemote.transact(Stub.SEND_UI_TRANSACTION, data, reply, 0);
					reply.readException();
					result = reply.readInt();
				}
				finally {
					data.recycle();
					reply.recycle();
				}
				return result;
			}

			@Override 
			public Parcel sendRpc(int objectId, String methodName, String methodSig, 
					String[] types, byte[] args)	
					throws RemoteException {
				Log.d("MOBILEDUI(IFLUIDService)", "sendRpc(), objectId = " + objectId
						+ ", methodName = " + methodName
						+ ", types = " + types
						+ ", args = " + args);
				Parcel data = Parcel.obtain();
				Parcel reply = Parcel.obtain();
				int result;
				try {
					data.writeInterfaceToken(DESCRIPTOR);
					data.writeInt(objectId);
					data.writeString(methodName);
					data.writeString(methodSig);
					data.writeStringArray(types);
					data.writeToAshmem(args);
					mRemote.transact(Stub.SEND_RPC_TRANSACTION, data, reply, 0);
				}
				finally {
					data.recycle();
				}
				return reply;
			}

			@Override 
			public int sendInput(int id, InputEvent event, boolean isMotionEvent) 
					throws RemoteException {
				Log.d("MOBILEDUI(IFLUIDService)", "sendInput(), id = " + id
						+ ", event = " + event
						+ ", isMotionEvent = " + isMotionEvent);
				Parcel data = Parcel.obtain();
				Parcel reply = Parcel.obtain();
				int result;
				try {
					data.writeInterfaceToken(DESCRIPTOR);
					data.writeInt(id);
					if (isMotionEvent)
						((MotionEvent)event).writeToParcel(data, 0);
					else
						((KeyEvent)event).writeToParcel(data, 0);
					data.writeBoolean(event.mIsFromHost);
					data.writeFloat(event.mScale);
					data.writeBoolean(isMotionEvent);
					mRemote.transact(Stub.SEND_INPUT_TRANSACTION, data, reply, 0);
					reply.readException();
					result = reply.readInt();
				}
				finally {
					reply.recycle();
					data.recycle();
				}
				return result;
			}

			@Override 
			public int sendIMEInput(int id, Message msg) 
					throws RemoteException {
				Log.d("MOBILEDUI(IFLUIDService)", "sendIMEInput(), id = " + id
						+ ", msg = " + msg);

				Message newMsg = Message.obtain();
				newMsg.what = msg.what;
				newMsg.arg1 = msg.arg1;
				newMsg.arg2 = msg.arg2;
				String str = null;
				CharSequence text =null;
				Bundle bundle = null;
				switch (msg.what) {
					case DO_GET_EXTRACTED_TEXT: {
						SomeArgs args = (SomeArgs)msg.obj;
						newMsg.obj = args.arg1;
						break;
					}
					case DO_COMMIT_TEXT: {
						text = (CharSequence)msg.obj;
						break;
					}
					case DO_SET_COMPOSING_TEXT: {
						text = (CharSequence)msg.obj;
						break;
					}
					case DO_PERFORM_PRIVATE_COMMAND: {
						SomeArgs args = (SomeArgs)msg.obj;
						str = (String) args.arg1;
						bundle = (Bundle)args.arg2;
						break;
					}
					case DO_COMMIT_CONTENT: {
						SomeArgs args = (SomeArgs)msg.obj;
						newMsg.obj = args.arg1;
						bundle = (Bundle)args.arg2;
						break;
					}
					default: {
						if (msg.obj instanceof SomeArgs 
							|| msg.obj instanceof CharSequence)
							break;
						newMsg.obj = msg.obj;
					}
				}

				Parcel data = Parcel.obtain();
				Parcel reply = Parcel.obtain();
				int result;
				try {
					data.writeInterfaceToken(DESCRIPTOR);
					data.writeInt(id);
					newMsg.writeToParcel(data, 0);
					if (text != null) {
						data.writeBoolean(true);
						TextUtils.writeToParcel(text, data, 0);
					}
					else 
						data.writeBoolean(false);
					data.writeString(str);
					if (bundle != null) {
						data.writeBoolean(true);
						bundle.writeToParcel(data, 0);
					}
					else 
						data.writeBoolean(false);

					mRemote.transact(Stub.SEND_IME_INPUT_TRANSACTION, data, reply, 0);
					reply.readException();
					result = reply.readInt();
				}
				finally {
					reply.recycle();
					data.recycle();
				}
				return result;
			}

			@Override 
			public int sendCache(int objectId, byte[] cacheData)	
					throws RemoteException {
				Log.d("MOBILEDUI(IFLUIDService)", "sendCache(), objectId = " + objectId
						+ ", cacheData = " + cacheData);
				Parcel data = Parcel.obtain();
				Parcel reply = Parcel.obtain();
				int result;
				try {
					data.writeInterfaceToken(DESCRIPTOR);
					data.writeInt(objectId);
					data.writeToAshmem(cacheData);
					mRemote.transact(Stub.SEND_CACHE_TRANSACTION, data, reply, 0);
					reply.readException();
					result = reply.readInt();
				}
				finally {
					data.recycle();
					reply.recycle();
				}
				return result;
			}

			@Override 
			public void onActivityLaunched(IBinder token) throws RemoteException
			{
				Parcel data = Parcel.obtain();
				Parcel reply = Parcel.obtain();
				try {
					data.writeInterfaceToken(DESCRIPTOR);
					data.writeStrongBinder(token);
					mRemote.transact(Stub.ON_ACTIVITY_LAUNCHED_TRANSACTION, data, reply, 0);
					reply.readException();
				}
				finally {
					reply.recycle();
					data.recycle();
				}
			}

			@Override 
			public boolean isHostDevice() throws RemoteException
			{
				Parcel data = Parcel.obtain();
				Parcel reply = Parcel.obtain();
				boolean result;
				try {
					data.writeInterfaceToken(DESCRIPTOR);
					mRemote.transact(Stub.IS_HOST_DEVICE_TRANSACTION, data, reply, 0);
					reply.readException();
					result = reply.readBoolean();
				}
				finally {
					reply.recycle();
					data.recycle();
				}
				return result;
			}

			@Override 
			public void notifyAppCrash(IBinder appThread) throws RemoteException
			{
				Parcel data = Parcel.obtain();
				Parcel reply = Parcel.obtain();
				try {
					data.writeInterfaceToken(DESCRIPTOR);
					data.writeStrongBinder(appThread);
					mRemote.transact(Stub.NOTIFY_APP_CRASH_TRANSACTION, data, reply, 0);
					reply.readException();
				}
				finally {
					reply.recycle();
					data.recycle();
				}
			}

			@Override 
			public byte[][] requestCache(int objectId, String methodName, String methodSig)	
					throws RemoteException {
				Log.d("MOBILEDUI(IFLUIDService)", "requestCache(), objectId = " + objectId
						+ ", methodName = " + methodName + ", methodSig = " + methodSig);
				Parcel data = Parcel.obtain();
				Parcel reply = Parcel.obtain();
				byte[][] result = new byte[2][];
				try {
					data.writeInterfaceToken(DESCRIPTOR);
					data.writeInt(objectId);
					data.writeString(methodName);
					data.writeString(methodSig);
					mRemote.transact(Stub.REQUEST_CACHE_TRANSACTION, data, reply, 0);
					reply.readException();
					result[0] = reply.createByteArray();
					result[1] = reply.createByteArray();
				}
				finally {
					data.recycle();
					reply.recycle();
				}
				return result;
			}
		}
		static final int REGISTER_APP_BINDERS_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION;
		static final int SEND_UI_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION + 1;
		static final int SEND_RPC_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION + 2;
		static final int SEND_INPUT_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION + 3;
		static final int SEND_IME_INPUT_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION + 4;
		static final int SEND_CACHE_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION + 5;
		static final int ON_ACTIVITY_LAUNCHED_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION + 200;
		static final int IS_HOST_DEVICE_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION + 201;
		static final int NOTIFY_APP_CRASH_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION + 202;
		static final int REQUEST_CACHE_TRANSACTION = IBinder.FIRST_CALL_TRANSACTION + 203;
	}

	public void registerAppBinders(IBinder appThread, IBinder token) throws RemoteException;
	public int sendUi(String packageName, byte[] viewData, String[] className) throws RemoteException;
	public Parcel sendRpc(int objectId, String methodName, String methodSig, String[] types, byte[] args) throws RemoteException;
	public int sendCache(int objectId, byte[] cacheData) throws RemoteException;	
	public int sendInput(int id, InputEvent event, boolean isMotionEvent) throws RemoteException;
	public int sendIMEInput(int id, Message msg) throws RemoteException;
	public void onActivityLaunched(IBinder token) throws RemoteException;
	public boolean isHostDevice() throws RemoteException;
	public void notifyAppCrash(IBinder appThread) throws RemoteException;
	public byte[][] requestCache(int objectId, String methodName, String methodSig) throws RemoteException;
}
