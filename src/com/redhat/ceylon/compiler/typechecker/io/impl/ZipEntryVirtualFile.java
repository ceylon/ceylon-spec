package com.redhat.ceylon.compiler.typechecker.io.impl;

import com.redhat.ceylon.compiler.typechecker.io.VirtualFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.io.FilterInputStream;

/**
 * @author Emmanuel Bernard <emmanuel@hibernate.org>
 */
class ZipEntryVirtualFile implements VirtualFile {

    public static final List<VirtualFile> EMPTY_CHILDREN = Collections.unmodifiableList( new ArrayList<VirtualFile>(0) );
    private final String name;
    private final String path;
    private final ZipEntry entry;
    private final ZipFile zipFile;

    public ZipEntryVirtualFile(ZipEntry entry, ZipFile zipFile) {
        this.name = Helper.getSimpleName(entry);
        this.entry = entry;
        String tempPath = zipFile.getName() + "!/" + entry.getName();
        this.path = tempPath.endsWith("/") ? tempPath.substring(0, tempPath.length() - 1 ) : tempPath;
        this.zipFile = zipFile;
    }

    @Override
    public boolean exists() {
        return true;
    }

    @Override
    public boolean isFolder() {
        return false;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public InputStream getInputStream() {
        try {
            return new FilterInputStream(zipFile.getInputStream( entry )) {
                // Do nothing since the ZipInputStream will be closed by the ZipFile.close call
                @Override
                public void close() throws IOException {  
                }
            };
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<VirtualFile> getChildren() {
        return EMPTY_CHILDREN;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("ZipEntryVirtualFile");
        sb.append("{name='").append(name).append('\'');
        sb.append('}');
        return sb.toString();
    }

    @Override
    public int hashCode() {
        return getPath().hashCode();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof VirtualFile) {
            return ((VirtualFile) obj).getPath().equals(getPath());
        }
        else {
            return super.equals(obj);
        }
    }

    @Override
    public int compareTo(VirtualFile o) {
        return getPath().compareTo(o.getPath());
    }
}
