package ru.lex42.shinobiviewer;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Minimal read-only provider; only explicitly URI-granted saved exports are accessible. */
public final class SavedMediaProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    private File file(Uri uri)throws FileNotFoundException{
        if(uri.getPathSegments().size()!=1)throw new FileNotFoundException();
        try{
            File external=getContext().getExternalFilesDir(null);if(external==null)throw new FileNotFoundException();
            File root=new File(external,"exports").getCanonicalFile();
            File candidate=new File(root,uri.getLastPathSegment()).getCanonicalFile();
            if(!root.equals(candidate.getParentFile())||!candidate.isFile())throw new FileNotFoundException();
            return candidate;
        }catch(IOException e){throw new FileNotFoundException();}
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
        if(!"r".equals(mode))throw new FileNotFoundException("Read only");
        return ParcelFileDescriptor.open(file(uri),ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public String getType(Uri uri){return uri.getLastPathSegment()!=null&&uri.getLastPathSegment().endsWith(".png")?"image/png":"video/mp4";}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){
        String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
        MatrixCursor out=new MatrixCursor(columns);
        try{File file=file(uri);Object[] row=new Object[columns.length];for(int i=0;i<columns.length;i++)if(OpenableColumns.DISPLAY_NAME.equals(columns[i]))row[i]=file.getName();else if(OpenableColumns.SIZE.equals(columns[i]))row[i]=file.length();out.addRow(row);}catch(FileNotFoundException ignored){}
        return out;
    }
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException();}
}
