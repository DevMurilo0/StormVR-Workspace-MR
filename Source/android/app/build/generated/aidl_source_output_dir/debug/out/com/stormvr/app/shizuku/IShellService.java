/*
 * This file is auto-generated.  DO NOT MODIFY.
 */
package com.stormvr.app.shizuku;
// Servico que roda DENTRO do processo iniciado pelo Shizuku (uid shell/2000 via ADB, ou root). Criado por Shizuku.bindUserService.
public interface IShellService extends android.os.IInterface
{
  /** Default implementation for IShellService. */
  public static class Default implements com.stormvr.app.shizuku.IShellService
  {
    // Metodo de encerramento reservado pelo Shizuku (transacao 16777114): obrigatorio no UserService.
    @Override public void destroy() throws android.os.RemoteException
    {
    }
    // Executa argv no processo privilegiado. Retorna [codigoDeSaida, stdout, stderr].
    @Override public java.lang.String[] exec(java.lang.String[] argv, long timeoutMs) throws android.os.RemoteException
    {
      return null;
    }
    // Helper do abridor de apps (mesmo protocolo de texto do RootHelper): uma linha entra, uma linha de resposta sai ("" = sem resposta).
    @Override public java.lang.String helper(java.lang.String line) throws android.os.RemoteException
    {
      return null;
    }
    // Toques/teclas: sem esperar resposta (a ordem das chamadas é preservada).
    @Override public void helperSend(java.lang.String line) throws android.os.RemoteException
    {
    }
    @Override
    public android.os.IBinder asBinder() {
      return null;
    }
  }
  /** Local-side IPC implementation stub class. */
  public static abstract class Stub extends android.os.Binder implements com.stormvr.app.shizuku.IShellService
  {
    /** Construct the stub at attach it to the interface. */
    public Stub()
    {
      this.attachInterface(this, DESCRIPTOR);
    }
    /**
     * Cast an IBinder object into an com.stormvr.app.shizuku.IShellService interface,
     * generating a proxy if needed.
     */
    public static com.stormvr.app.shizuku.IShellService asInterface(android.os.IBinder obj)
    {
      if ((obj==null)) {
        return null;
      }
      android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
      if (((iin!=null)&&(iin instanceof com.stormvr.app.shizuku.IShellService))) {
        return ((com.stormvr.app.shizuku.IShellService)iin);
      }
      return new com.stormvr.app.shizuku.IShellService.Stub.Proxy(obj);
    }
    @Override public android.os.IBinder asBinder()
    {
      return this;
    }
    @Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
    {
      java.lang.String descriptor = DESCRIPTOR;
      if (code >= android.os.IBinder.FIRST_CALL_TRANSACTION && code <= android.os.IBinder.LAST_CALL_TRANSACTION) {
        data.enforceInterface(descriptor);
      }
      switch (code)
      {
        case INTERFACE_TRANSACTION:
        {
          reply.writeString(descriptor);
          return true;
        }
      }
      switch (code)
      {
        case TRANSACTION_destroy:
        {
          this.destroy();
          reply.writeNoException();
          break;
        }
        case TRANSACTION_exec:
        {
          java.lang.String[] _arg0;
          _arg0 = data.createStringArray();
          long _arg1;
          _arg1 = data.readLong();
          java.lang.String[] _result = this.exec(_arg0, _arg1);
          reply.writeNoException();
          reply.writeStringArray(_result);
          break;
        }
        case TRANSACTION_helper:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          java.lang.String _result = this.helper(_arg0);
          reply.writeNoException();
          reply.writeString(_result);
          break;
        }
        case TRANSACTION_helperSend:
        {
          java.lang.String _arg0;
          _arg0 = data.readString();
          this.helperSend(_arg0);
          break;
        }
        default:
        {
          return super.onTransact(code, data, reply, flags);
        }
      }
      return true;
    }
    private static class Proxy implements com.stormvr.app.shizuku.IShellService
    {
      private android.os.IBinder mRemote;
      Proxy(android.os.IBinder remote)
      {
        mRemote = remote;
      }
      @Override public android.os.IBinder asBinder()
      {
        return mRemote;
      }
      public java.lang.String getInterfaceDescriptor()
      {
        return DESCRIPTOR;
      }
      // Metodo de encerramento reservado pelo Shizuku (transacao 16777114): obrigatorio no UserService.
      @Override public void destroy() throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          boolean _status = mRemote.transact(Stub.TRANSACTION_destroy, _data, _reply, 0);
          _reply.readException();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
      }
      // Executa argv no processo privilegiado. Retorna [codigoDeSaida, stdout, stderr].
      @Override public java.lang.String[] exec(java.lang.String[] argv, long timeoutMs) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String[] _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeStringArray(argv);
          _data.writeLong(timeoutMs);
          boolean _status = mRemote.transact(Stub.TRANSACTION_exec, _data, _reply, 0);
          _reply.readException();
          _result = _reply.createStringArray();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      // Helper do abridor de apps (mesmo protocolo de texto do RootHelper): uma linha entra, uma linha de resposta sai ("" = sem resposta).
      @Override public java.lang.String helper(java.lang.String line) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        android.os.Parcel _reply = android.os.Parcel.obtain();
        java.lang.String _result;
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(line);
          boolean _status = mRemote.transact(Stub.TRANSACTION_helper, _data, _reply, 0);
          _reply.readException();
          _result = _reply.readString();
        }
        finally {
          _reply.recycle();
          _data.recycle();
        }
        return _result;
      }
      // Toques/teclas: sem esperar resposta (a ordem das chamadas é preservada).
      @Override public void helperSend(java.lang.String line) throws android.os.RemoteException
      {
        android.os.Parcel _data = android.os.Parcel.obtain();
        try {
          _data.writeInterfaceToken(DESCRIPTOR);
          _data.writeString(line);
          boolean _status = mRemote.transact(Stub.TRANSACTION_helperSend, _data, null, android.os.IBinder.FLAG_ONEWAY);
        }
        finally {
          _data.recycle();
        }
      }
    }
    static final int TRANSACTION_destroy = (android.os.IBinder.FIRST_CALL_TRANSACTION + 16777114);
    static final int TRANSACTION_exec = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
    static final int TRANSACTION_helper = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
    static final int TRANSACTION_helperSend = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
  }
  public static final java.lang.String DESCRIPTOR = "com.stormvr.app.shizuku.IShellService";
  // Metodo de encerramento reservado pelo Shizuku (transacao 16777114): obrigatorio no UserService.
  public void destroy() throws android.os.RemoteException;
  // Executa argv no processo privilegiado. Retorna [codigoDeSaida, stdout, stderr].
  public java.lang.String[] exec(java.lang.String[] argv, long timeoutMs) throws android.os.RemoteException;
  // Helper do abridor de apps (mesmo protocolo de texto do RootHelper): uma linha entra, uma linha de resposta sai ("" = sem resposta).
  public java.lang.String helper(java.lang.String line) throws android.os.RemoteException;
  // Toques/teclas: sem esperar resposta (a ordem das chamadas é preservada).
  public void helperSend(java.lang.String line) throws android.os.RemoteException;
}
