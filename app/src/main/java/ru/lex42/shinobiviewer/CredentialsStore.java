package ru.lex42.shinobiviewer;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Opt-in credentials encrypted by an app-owned, non-exportable Android Keystore key. */
public final class CredentialsStore {
    private static final String ALIAS="shinobi.remembered-login.v1";
    private final SharedPreferences prefs;
    private final byte[] aad;
    public static final class Credential {
        public final String username,password;
        private Credential(String username,String password){this.username=username;this.password=password;}
    }
    public CredentialsStore(Context context){
        prefs=context.getApplicationContext().getSharedPreferences("encrypted_login",Context.MODE_PRIVATE);
        aad=(context.getPackageName()+":"+ALIAS).getBytes(StandardCharsets.UTF_8);
    }
    public synchronized Credential load(String server){
        String record=prefs.getString("record",null);if(record==null)return null;
        try{
            JSONObject sealed=new JSONObject(record);
            KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
            SecretKey key=(SecretKey)ks.getKey(ALIAS,null);if(key==null){clear();return null;}
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,Base64.decode(sealed.getString("iv"),Base64.NO_WRAP)));
            cipher.updateAAD(aad);
            byte[] plain=cipher.doFinal(Base64.decode(sealed.getString("data"),Base64.NO_WRAP));
            JSONObject data;
            try{data=new JSONObject(new String(plain,StandardCharsets.UTF_8));}finally{java.util.Arrays.fill(plain,(byte)0);}
            if(!normalize(server).equals(data.getString("server")))return null;
            return new Credential(data.getString("username"),data.getString("password"));
        }catch(Exception unavailable){clear();return null;}
    }
    public synchronized void save(String server,String username,String password){
        try{
            KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);
            SecretKey key=(SecretKey)ks.getKey(ALIAS,null);
            if(key==null){
                KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
                generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build());
                key=generator.generateKey();
            }
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key);cipher.updateAAD(aad);
            byte[] plain=new JSONObject().put("server",normalize(server)).put("username",username).put("password",password).toString().getBytes(StandardCharsets.UTF_8);
            byte[] encrypted;try{encrypted=cipher.doFinal(plain);}finally{java.util.Arrays.fill(plain,(byte)0);}
            String sealed=new JSONObject().put("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)).put("data",Base64.encodeToString(encrypted,Base64.NO_WRAP)).toString();
            if(!prefs.edit().putString("record",sealed).commit())throw new IllegalStateException();
        }catch(Exception failure){throw new IllegalStateException("Не удалось безопасно сохранить вход.");}
    }
    public synchronized void clear(){
        prefs.edit().remove("record").commit();
        try{KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);ks.deleteEntry(ALIAS);}catch(Exception ignored){}
    }
    private static String normalize(String server)throws Exception{
        URI u=new URI(server.trim());String scheme=u.getScheme();
        if(u.getHost()==null||u.getUserInfo()!=null||u.getQuery()!=null||u.getFragment()!=null||!("https".equalsIgnoreCase(scheme)||"http".equalsIgnoreCase(scheme)))throw new IllegalArgumentException();
        int port=u.getPort();if(port==("https".equalsIgnoreCase(scheme)?443:80))port=-1;
        return new URI(scheme.toLowerCase(java.util.Locale.ROOT),null,u.getHost().toLowerCase(java.util.Locale.ROOT),port,u.getPath()==null?"":u.getPath().replaceAll("/+$",""),null,null).toASCIIString();
    }
}
