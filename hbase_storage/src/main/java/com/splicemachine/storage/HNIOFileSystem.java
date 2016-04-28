package com.splicemachine.storage;

import com.splicemachine.access.api.DistributedFileSystem;
import com.splicemachine.access.api.FileInfo;
import com.splicemachine.si.api.data.ExceptionFactory;
import com.splicemachine.utils.SpliceLogUtils;
import org.apache.commons.io.FileUtils;
import org.apache.hadoop.fs.ContentSummary;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.permission.AclStatus;
import org.apache.hadoop.fs.permission.FsAction;
import org.apache.hadoop.hdfs.protocol.AclException;
import org.apache.log4j.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.util.Map;
import java.util.Set;

/**
 * @author Scott Fines
 *         Date: 1/21/16
 */
public class HNIOFileSystem extends DistributedFileSystem{
    private final org.apache.hadoop.fs.FileSystem fs;
    private final ExceptionFactory exceptionFactory;
    private static Logger LOG=Logger.getLogger(HNIOFileSystem.class);

    public HNIOFileSystem(org.apache.hadoop.fs.FileSystem fs,ExceptionFactory ef){
        this.fs=fs;
        this.exceptionFactory = ef;
    }

    @Override
    public void delete(Path path) throws IOException{
        delete(path,false);
    }

    @Override
    public void delete(Path path,boolean recursive) throws IOException{
        fs.delete(toHPath(path),recursive);
    }

    @Override
    public void delete(String dir,boolean recursive) throws IOException{
        org.apache.hadoop.fs.Path p=new org.apache.hadoop.fs.Path(dir);
        fs.delete(p,recursive);
    }

    @Override
    public void delete(String dir,String fileName,boolean recursive) throws IOException{
        org.apache.hadoop.fs.Path p=new org.apache.hadoop.fs.Path(dir,fileName);
        fs.delete(p,recursive);
    }

    @Override
    public Path getPath(String directory,String fileName){
        return Paths.get(directory,fileName);
    }

    @Override
    public Path getPath(String fullPath){
        return Paths.get(fullPath);
    }

    @Override
    public FileInfo getInfo(String filePath) throws IOException{
        org.apache.hadoop.fs.Path f=new org.apache.hadoop.fs.Path(filePath);
        ContentSummary contentSummary=fs.getContentSummary(f);
        return new HFileInfo(f,contentSummary);
    }

    @Override
    public String getScheme(){
        return fs.getScheme();
    }

    @Override
    public FileSystem newFileSystem(URI uri,Map<String, ?> env) throws IOException{
        throw new UnsupportedOperationException("IMPLEMENT");
    }

    @Override
    public FileSystem getFileSystem(URI uri){
        throw new UnsupportedOperationException("IMPLEMENT");
    }

    @Override
    public Path getPath(URI uri){
        return Paths.get(uri);
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path,Set<? extends OpenOption> options,FileAttribute<?>... attrs) throws IOException{
        throw new UnsupportedOperationException();
    }

    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir,DirectoryStream.Filter<? super Path> filter) throws IOException{
        throw new UnsupportedOperationException();
    }

    @Override
    public OutputStream newOutputStream(Path path,OpenOption... options) throws IOException{
        return fs.create(toHPath(path));
    }

    @Override
    public OutputStream newOutputStream(String dir,String fileName,OpenOption... options) throws IOException{
        org.apache.hadoop.fs.Path path=new org.apache.hadoop.fs.Path(dir,fileName);
        return fs.create(path);
    }

    @Override
    public OutputStream newOutputStream(String fullPath,OpenOption... options) throws IOException{
        org.apache.hadoop.fs.Path path=new org.apache.hadoop.fs.Path(fullPath);
        return fs.create(path);
    }

    @Override
    public void createDirectory(Path dir,FileAttribute<?>... attrs) throws IOException{
        org.apache.hadoop.fs.Path f=toHPath(dir);
        if (LOG.isDebugEnabled())
            SpliceLogUtils.debug(LOG, "createDirectory(): path=%s", f);
        try{
            FileStatus fileStatus=fs.getFileStatus(f);
            throw new FileAlreadyExistsException(dir.toString());
        }catch(FileNotFoundException fnfe){
            fs.mkdirs(f);
        }
    }

    @Override
    public boolean createDirectory(Path path,boolean errorIfExists) throws IOException{
        org.apache.hadoop.fs.Path f=toHPath(path);
        if (LOG.isDebugEnabled())
            SpliceLogUtils.debug(LOG, "createDirectory(): path=%s", f);
        try{
            FileStatus fileStatus=fs.getFileStatus(f);
            return !errorIfExists && fileStatus.isDirectory();
        }catch(FileNotFoundException fnfe){
            return fs.mkdirs(f);
        }
    }

    @Override
    public boolean createDirectory(String fullPath,boolean errorIfExists) throws IOException{
        if (LOG.isDebugEnabled())
            SpliceLogUtils.debug(LOG, "createDirectory(): path string=%s", fullPath);
        org.apache.hadoop.fs.Path f=new org.apache.hadoop.fs.Path(fullPath);
        if (LOG.isDebugEnabled())
            SpliceLogUtils.debug(LOG, "createDirectory(): hdfs path=%s", f);
        try{
            FileStatus fileStatus=fs.getFileStatus(f);
            if (LOG.isTraceEnabled())
                SpliceLogUtils.trace(LOG, "createDirectory(): file status=%s", fileStatus);
            return !errorIfExists && fileStatus.isDirectory();
        }catch(FileNotFoundException fnfe){
            if (LOG.isDebugEnabled())
                SpliceLogUtils.trace(LOG, "createDirectory(): directory not found so we will create it: %s", f);
            boolean created = fs.mkdirs(f);
            if (LOG.isDebugEnabled())
                SpliceLogUtils.trace(LOG, "createDirectory(): created=%s", created);
            return created;
        }
    }

    @Override
    public void touchFile(Path path) throws IOException{
        if(!fs.createNewFile(toHPath(path))){
            throw new FileAlreadyExistsException(path.toString());
        }
    }

    @Override
    public void touchFile(String dir, String fileName) throws IOException{
        org.apache.hadoop.fs.Path path=new org.apache.hadoop.fs.Path(dir,fileName);
        if(!fs.createNewFile(path)){
            throw new FileAlreadyExistsException(path.toString());
        }
    }

    @Override
    public void copy(Path source,Path target,CopyOption... options) throws IOException{
        throw new UnsupportedOperationException("IMPLEMENT");
    }

    @Override
    public void move(Path source,Path target,CopyOption... options) throws IOException{
        throw new UnsupportedOperationException("IMPLEMENT");
    }

    @Override
    public boolean isSameFile(Path path,Path path2) throws IOException{
        return path.equals(path2);
    }

    @Override
    public boolean isHidden(Path path) throws IOException{
        return false;
    }

    @Override
    public FileStore getFileStore(Path path) throws IOException{
        throw new UnsupportedOperationException("IMPLEMENT");
    }

    @Override
    public void checkAccess(Path path,AccessMode... modes) throws IOException{
        throw new UnsupportedOperationException("IMPLEMENT");
    }

    @Override
    public <V extends FileAttributeView> V getFileAttributeView(Path path,Class<V> type,LinkOption... options){
        throw new UnsupportedOperationException("IMPLEMENT");
    }

    @Override
    public <A extends BasicFileAttributes> A readAttributes(Path path,Class<A> type,LinkOption... options) throws IOException{
        throw new UnsupportedOperationException("IMPLEMENT");
    }

    @Override
    public Map<String, Object> readAttributes(Path path,String attributes,LinkOption... options) throws IOException{
        throw new UnsupportedOperationException("IMPLEMENT");
    }

    @Override
    public void setAttribute(Path path,String attribute,Object value,LinkOption... options) throws IOException{
        throw new UnsupportedOperationException("IMPLEMENT");
    }

    /* *************************************************************************************/
    /*private helper methods*/
    private org.apache.hadoop.fs.Path toHPath(Path path){
        return new org.apache.hadoop.fs.Path(path.toUri());
    }


    private class HFileInfo implements FileInfo{
        private final boolean isDir;
        private final AclStatus aclStatus;
        private final boolean isReadable;
        private final boolean isWritable;
        private org.apache.hadoop.fs.Path path;
        private ContentSummary contentSummary;

        public HFileInfo(org.apache.hadoop.fs.Path path,ContentSummary contentSummary) throws IOException{
            this.path=path;
            this.contentSummary=contentSummary;
            this.isDir = fs.isDirectory(path);
            AclStatus aclS;
            try{
                aclS=fs.getAclStatus(path);
            } catch (UnsupportedOperationException | AclException e) { // Runtime Exception for RawFS
                aclS = new AclStatus.Builder().owner("unknown").group("unknown").build();
            } catch(Exception e){
                e = exceptionFactory.processRemoteException(e); //strip any multi-retry errors out
                //noinspection ConstantConditions
                if(e instanceof UnsupportedOperationException|| e instanceof AclException){
                    /*
                     * Some Filesystems don't support aclStatus. In that case,
                     * we replace it with our own ACL status object
                     */
                    aclS=new AclStatus.Builder().owner("unknown").group("unknown").build();
                }else{
                    /*
                     * the remaining errors are of the category of FileNotFound,UnresolvedPath,
                     * etc. These are environmental, so we should throw up here.
                     */
                    throw new IOException(e);
                }
            }
            this.aclStatus = aclS;
            boolean readable;
            try{
                fs.access(path,FsAction.READ);
                readable= true;
            }catch(AccessDeniedException ade){
               readable = false;
            }
            boolean writable;
            try{
                fs.access(path,FsAction.WRITE);
                writable = true;
            }catch(AccessDeniedException ade){
                writable = false;
            }
            this.isReadable = readable;
            this.isWritable = writable;
        }

        @Override
        public String fileName(){
            return path.getName();
        }

        @Override
        public String fullPath(){
            return path.toString();
        }

        @Override
        public boolean isDirectory(){
            return isDir;
        }

        @Override
        public long fileCount(){
            return contentSummary.getFileCount();
        }

        @Override
        public long spaceConsumed(){
            return contentSummary.getSpaceConsumed();
        }

        @Override
        public long size(){
            return contentSummary.getLength();
        }

        @Override
        public boolean isReadable(){
            return isReadable;
        }

        @Override
        public String getUser(){
            return aclStatus.getOwner();
        }

        @Override
        public String getGroup(){
            return aclStatus.getGroup();
        }

        @Override
        public boolean isWritable(){
            return isWritable;
        }

        @Override
        public String toSummary() { // FileUtils.byteCountToDisplaySize
            StringBuilder sb = new StringBuilder();
            sb.append(this.isDirectory() ? "Directory = " : "File = ").append(fullPath());
            sb.append("\nFile Count = ").append(contentSummary.getFileCount());
            sb.append("\nSize = ").append(FileUtils.byteCountToDisplaySize(this.size()));
            // Not important to display here, but keep it around in case.
            // For import we only care about the actual file size, not space consumed.
            // if (this.spaceConsumed() != this.size())
            //     sb.append("\nSpace Consumed = ").append(FileUtils.byteCountToDisplaySize(this.spaceConsumed()));
            return sb.toString();
        }
    }
}