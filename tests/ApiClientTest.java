package ru.lex42.shinobiviewer;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import org.json.*;
public final class ApiClientTest {
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[] args)throws Exception {
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  String base="http://127.0.0.1:"+server.getAddress().getPort();
  server.createContext("/",exchange->{String path=exchange.getRequestURI().getPath(),query=exchange.getRequestURI().getQuery(),body;
   if(path.contains("/monitor/"))body="[{\"mid\":\"one\",\"name\":\"Холл\",\"path\":\"/Streaming/Channels/102\",\"details\":{\"stream_type\":\"mp4\",\"archivePreviewMonitor\":\"helper\",\"substream\":{\"input\":{\"fulladdress\":\"rtsp://private/Channels/101\"},\"output\":{\"stream_type\":\"mp4\"}}}},{\"mid\":\"helper\",\"details\":{\"archivePrimaryMonitor\":\"one\"}}]";
   else if(query!=null&&query.contains("variants=1"))body="{\"ok\":true,\"version\":1,\"variants\":{\"original\":{\"url\":\"/token/videos/group/one/a.mp4\"},\"low\":{\"url\":\"/token/videos/group/one/a.mp4?quality=low\"}}}";
   else body="{\"ok\":true,\"videos\":[{\"mid\":\"one\",\"time\":\"2026-11-10T10:00:00Z\",\"end\":\"2026-11-10T10:05:00Z\",\"href\":\"/token/videos/group/one/a.mp4\"}]}";
   byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
  });server.start();
  try{
   ApiClient api=new ApiClient(base,"token","group","uid");
   try{api.recordings("other",0,0);throw new AssertionError();}catch(java.io.IOException expected){}
   var cameras=api.getCameras();check(cameras.size()==1);var c=cameras.get(0);check(c.primaryIsLow&&c.dynamicAvailable&&c.hasLowArchive);check(!api.requiresDynamic(c,false)&&api.requiresDynamic(c,true));check(api.liveUrl(c,false).endsWith("/one/s.mp4"));check(api.liveUrl(c,true).endsWith("/one/1/s.mp4"));
   var records=api.recordings("one",0,0);check(records.size()==1);check(records.get(0).endMs-records.get(0).startMs==300000);check(api.variants(records.get(0)).lowUrl.endsWith("quality=low"));
   check(api.recordings("one",ApiClient.parseTime("2026-11-10T10:04:00Z"),ApiClient.parseTime("2026-11-10T10:06:00Z")).size()==1);
   check(api.recordings("one",ApiClient.parseTime("2026-11-10T10:05:00Z"),0).isEmpty());
   try{api.variants(new ApiClient.Recording("one","bad","https://evil.invalid/a",1,2));throw new AssertionError();}catch(java.io.IOException expected){}
   try{ArchiveVariants.parse(URI.create(base+"/a"),new JSONObject("{\"ok\":true,\"variants\":{\"original\":{\"url\":\"https://evil.invalid/a\"}}}"));throw new AssertionError();}catch(java.io.IOException expected){}
   check(ArchiveVariants.parse(URI.create(base+"/a"),new JSONObject("{\"ok\":true,\"variants\":{\"original\":{\"url\":\"/a\"},\"low\":null}}" )).low==null);
   check(ApiClient.parseTime("2026-11-10 10:00:00")==ApiClient.parseTime("2026-11-10T10:00:00Z"));
   System.out.println("ApiClientTest: passed (permissions, quality, intervals, variants, origin isolation)");
  }finally{server.stop(0);}
 }
}
