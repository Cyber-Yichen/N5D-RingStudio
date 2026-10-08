package com.codex.ringlab;
import java.io.*;import java.util.concurrent.*;
final class RootDriver {
    private Process process;private DataOutputStream writer;private BufferedReader reader;String configuration="";
    private final ScheduledExecutorService watchdog=Executors.newSingleThreadScheduledExecutor();
    void open(String apk)throws Exception{String quoted="'"+apk.replace("'","'\\''")+"'";process=new ProcessBuilder("/sbin/su","-c","CLASSPATH="+quoted+" /system/bin/app_process /system/bin com.codex.ringlab.DriverMain").redirectErrorStream(true).start();writer=new DataOutputStream(process.getOutputStream());reader=new BufferedReader(new InputStreamReader(process.getInputStream()));receive("__LIGHT_READY__",60000);}
    private void receive(String marker,long timeout)throws Exception{ScheduledFuture<?> timer=watchdog.schedule(()->{try{process.getOutputStream().close();}catch(Exception ignored){}process.destroy();},timeout,TimeUnit.MILLISECONDS);try{String line;StringBuilder diagnostic=new StringBuilder();while((line=reader.readLine())!=null){if(line.startsWith(marker))return;if(line.startsWith("__LIGHT_CONFIG__")){configuration=line.substring(17).trim();continue;}if(line.startsWith("__LIGHT_ERROR__"))throw new IOException(line);if(diagnostic.length()<3000)diagnostic.append(line).append('\n');}throw new IOException("本机灯控进程结束："+diagnostic);}finally{timer.cancel(false);}}
    void frame(int[] values,int level,int white,int logo)throws Exception{writer.writeByte(1);writer.writeByte(level);writer.writeByte(white);writer.writeByte(logo);byte[] bytes=new byte[96];for(int i=0;i<96;i++)bytes[i]=(byte)values[i];writer.write(bytes);writer.flush();receive("__LIGHT_OK__",4000);}
    void off()throws Exception{writer.writeByte(0);writer.flush();receive("__LIGHT_OK__",4000);}
    void close(){try{if(writer!=null){writer.writeByte(2);writer.flush();writer.close();}}catch(Exception ignored){}if(process!=null){try{Thread.sleep(120);}catch(Exception ignored){}process.destroy();}watchdog.shutdownNow();}
}
