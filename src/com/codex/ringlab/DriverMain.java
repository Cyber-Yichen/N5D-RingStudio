package com.codex.ringlab;
import java.io.*;import java.util.*;

public final class DriverMain {
    private static final String P="/sys/class/leds/aw20072_led/";
    private static FileOutputStream device;private static int lastLevel=-1,lastWhite=-1,lastLogo=-1;
    private static String read(String path)throws Exception{ByteArrayOutputStream b=new ByteArrayOutputStream();try(InputStream in=new FileInputStream(path)){byte[] x=new byte[1024];int n;while((n=in.read(x))>0)b.write(x,0,n);}return b.toString("UTF-8");}
    private static void sys(String name,String value)throws Exception{try(FileOutputStream out=new FileOutputStream(P+name)){out.write((value+"\n").getBytes("UTF-8"));}}
    private static void packet(int register,byte[] values)throws Exception{byte[] p=new byte[values.length+3];p[0]=1;p[1]=0x3a;p[2]=(byte)register;System.arraycopy(values,0,p,3,values.length);device.write(p);}
    private static void reg(int r,int v)throws Exception{packet(r,new byte[]{(byte)v});}
    private static void page(int p)throws Exception{reg(0xf0,p);}
    private static void off()throws Exception{page(0xc0);reg(3,0x10);page(0xc1);packet(0,new byte[96]);page(0xc2);packet(0,new byte[96]);page(0xc0);sys("brightness","0");lastLevel=lastWhite=-1;lastLogo=0;for(String path:new String[]{"/sys/class/leds/aw91xxx_led/brightness","/sys/class/leds/led-white/brightness"})try(FileOutputStream o=new FileOutputStream(path)){o.write(new byte[]{'0','\n'});}catch(Exception ignored){}}
    private static void wake()throws Exception{sys("reg","f0 c0");sys("reg","03 10");sys("brightness","255");Thread.sleep(1000);sys("reg","f0 c0");sys("reg","80 07");String configured=read(P+"reg");if(!configured.contains("reg:0x00=0x18")||!configured.contains("reg:0x80=0x07"))throw new IOException("驱动未完成灯光通道初始化");System.out.println("__LIGHT_CONFIG__ SIZE=7 ID=18");System.out.flush();page(0xc1);packet(0,new byte[96]);page(0xc0);reg(3,0x18);lastLevel=lastWhite=-1;}
    public static void main(String[] args){boolean awake=false;try{
        String binding=new File(P+"device").getCanonicalPath();
        if(!binding.endsWith("/6-003a")||!read(P+"device/name").trim().equals("aw20108_led")||!read(P+"hwen").contains("hwen=1"))throw new IOException("灯芯片接口不匹配");
        sys("reg","f0 c0");String registers=read(P+"reg");
        boolean standby=registers.contains("reg:0x03=0x10"),configured=registers.contains("reg:0x80=0x07");
        if(!registers.contains("reg:0x00=0x18")||(!configured&&!(standby&&registers.contains("reg:0x80=0x00")))||(!standby&&!registers.contains("reg:0x03=0x18")))throw new IOException("灯芯片配置不匹配");
        device=new FileOutputStream("/dev/aw20072_led");System.out.println("__LIGHT_READY__");System.out.flush();DataInputStream in=new DataInputStream(System.in);
        while(true){int op=in.read();if(op<0||op==2)break;if(op==0){off();awake=false;System.out.println("__LIGHT_OK__");System.out.flush();continue;}if(op!=1)throw new IOException("无效帧类型");int level=in.readUnsignedByte();int white=in.readUnsignedByte();int logo=in.readUnsignedByte();byte[] rgb=new byte[96];in.readFully(rgb);if(!awake){wake();awake=true;}long start=System.nanoTime();
            if(level!=lastLevel||white!=lastWhite){byte[] fade=new byte[96];Arrays.fill(fade,0,72,(byte)level);Arrays.fill(fade,72,96,(byte)white);page(0xc2);packet(0,fade);lastLevel=level;lastWhite=white;}
            if(logo!=lastLogo){try(FileOutputStream out=new FileOutputStream("/sys/class/leds/aw91xxx_led/brightness")){out.write((logo+"\n").getBytes("UTF-8"));}lastLogo=logo;}
            for(int i=0;i<96;i++)rgb[i]=(byte)((rgb[i]&255)>>>2);page(0xc1);packet(0,rgb);
            System.out.println("__LIGHT_OK__ "+((System.nanoTime()-start)/1000000));System.out.flush();
        }
    }catch(Exception e){System.out.println("__LIGHT_ERROR__ "+e.toString());System.out.flush();}finally{if(device!=null){try{off();device.close();}catch(Exception ignored){}}}}
}
