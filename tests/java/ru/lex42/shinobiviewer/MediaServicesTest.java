package ru.lex42.shinobiviewer;
import java.net.URL;
import java.lang.reflect.Method;
public final class MediaServicesTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        check(MediaFiles.safeName("../../camera\\folder.mp4",".mp4").equals(".._.._camera_folder.mp4"),"Unsafe export filename");
        check(MediaFiles.safeName("..",".png").equals("Камеры.png"),"Hidden path segment");
        check(MediaFiles.safeName("demo.MP4",".mp4").equals("demo.mp4"),"Extension normalization");
        check(MediaFiles.safeName("a".repeat(200),".png").length()==104,"Filename bound");
        Method origin=MediaFiles.class.getDeclaredMethod("sameOrigin",URL.class,URL.class);origin.setAccessible(true);
        check((boolean)origin.invoke(null,new URL("https://cam.example/path"),new URL("https://cam.example:443/file")),"Default port equivalence");
        check(!(boolean)origin.invoke(null,new URL("https://cam.example/path"),new URL("http://cam.example/file")),"TLS downgrade rejected");
        check(!(boolean)origin.invoke(null,new URL("https://cam.example/path"),new URL("https://other.example/file")),"Cross-origin rejected");
        Method normalize=CredentialsStore.class.getDeclaredMethod("normalize",String.class);normalize.setAccessible(true);
        check(normalize.invoke(null,"HTTPS://CAM.example:443/base/").equals("https://cam.example/base"),"Credential origin normalized");
        for(String invalid:new String[]{"https://user:pass@cam.example","https://cam.example/?token=x","file:///tmp/x"}){
            boolean rejected=false;try{normalize.invoke(null,invalid);}catch(java.lang.reflect.InvocationTargetException expected){rejected=true;}check(rejected,"Invalid credential origin rejected");
        }
        System.out.println("MediaServicesTest: 11 checks passed");
    }
}
