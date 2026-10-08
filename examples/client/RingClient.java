package com.example.ringclient;
import android.content.*;import android.os.*;import android.util.SparseArray;
public final class RingClient implements AutoCloseable {
    public interface Result {void receive(Bundle response);}
    private final Context context;private final SparseArray<Result> pending=new SparseArray<>();private Messenger remote;private int number;private boolean bound;
    private final Messenger replies=new Messenger(new Handler(Looper.getMainLooper()){@Override public void handleMessage(Message m){Result result=pending.get(m.arg1);pending.remove(m.arg1);if(result!=null)result.receive(m.getData());}});
    private Result connected;
    private final ServiceConnection connection=new ServiceConnection(){public void onServiceConnected(ComponentName name,IBinder binder){remote=new Messenger(binder);send(1,new Bundle(),connected);}public void onServiceDisconnected(ComponentName name){remote=null;failPending("DISCONNECTED");}};
    public RingClient(Context context){this.context=context.getApplicationContext();}
    public boolean connect(Result hello){connected=hello;Intent i=new Intent("com.codex.ringlab.CONTROL").setComponent(new ComponentName("com.codex.ringlab","com.codex.ringlab.PublicControlService"));bound=context.bindService(i,connection,Context.BIND_AUTO_CREATE);return bound;}
    public void send(int command,Bundle data,Result result){if(remote==null){if(result!=null)result.receive(error("DISCONNECTED"));return;}data.putInt("api_version",1);Message request=Message.obtain(null,command);request.arg1=++number;request.replyTo=replies;request.setData(data);if(result!=null)pending.put(number,result);try{remote.send(request);}catch(RemoteException e){pending.remove(number);if(result!=null)result.receive(error("DISCONNECTED"));}}
    private Bundle error(String code){Bundle b=new Bundle();b.putBoolean("ok",false);b.putString("code",code);return b;}
    private void failPending(String code){for(int i=0;i<pending.size();i++)pending.valueAt(i).receive(error(code));pending.clear();}
    @Override public void close(){if(bound)context.unbindService(connection);bound=false;remote=null;failPending("CLIENT_CLOSED");}
}
