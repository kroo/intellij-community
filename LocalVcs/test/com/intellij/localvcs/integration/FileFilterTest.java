package com.intellij.localvcs.integration;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import static org.easymock.classextension.EasyMock.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class FileFilterTest {
  private TestVirtualFile f1 = new TestVirtualFile(null, null, null);
  private TestVirtualFile f2 = new TestVirtualFile(null, null, null);

  private FileType binary = createFileType(true);
  private FileType nonBinary = createFileType(false);

  private FileIndex fi = createMock(FileIndex.class);
  private FileTypeManager tm = createMock(FileTypeManager.class);

  @Test
  public void testIsAllowedAndUnderContentRoot() {
    final boolean[] values = new boolean[2];
    FileFilter f = new FileFilter(null, null) {
      @Override
      public boolean isUnderContentRoot(VirtualFile f) {
        return values[0];
      }

      @Override
      public boolean isAllowed(VirtualFile f) {
        return values[1];
      }
    };

    values[0] = true;
    values[1] = true;
    assertTrue(f.isAllowedAndUnderContentRoot(null));

    values[0] = false;
    values[1] = true;
    assertFalse(f.isAllowedAndUnderContentRoot(null));

    values[0] = true;
    values[1] = false;
    assertFalse(f.isAllowedAndUnderContentRoot(null));
  }

  @Test
  public void testFilteringFileFromAnotherProject() {
    expect(fi.isInContent(f1)).andReturn(true);
    expect(fi.isInContent(f2)).andReturn(false);
    replay(fi);

    FileFilter f = new FileFilter(fi, tm);

    assertTrue(f.isUnderContentRoot(f1));
    assertFalse(f.isUnderContentRoot(f2));
  }

  @Test
  public void testFilteringFileOfUndesiredType() {
    expect(tm.isFileIgnored((String)anyObject())).andStubReturn(false);

    expect(tm.getFileTypeByFile(f1)).andStubReturn(nonBinary);
    expect(tm.getFileTypeByFile(f2)).andStubReturn(binary);
    replay(tm);

    FileFilter f = new FileFilter(fi, tm);

    assertTrue(f.isAllowed(f1));
    assertFalse(f.isAllowed(f2));
  }

  @Test
  public void testFilteringIgnoredFiles() {
    f1 = new TestVirtualFile("allowed", null, null);
    f2 = new TestVirtualFile("filtered", null, null);

    expect(tm.isFileIgnored("allowed")).andReturn(false);
    expect(tm.isFileIgnored("filtered")).andReturn(true);
    expect(tm.getFileTypeByFile((VirtualFile)anyObject())).andStubReturn(nonBinary);
    replay(tm);

    FileFilter f = new FileFilter(fi, tm);

    assertTrue(f.isAllowed(f1));
    assertFalse(f.isAllowed(f2));
  }

  @Test
  public void testFilteringIgnoredDirectories() {
    f1 = new TestVirtualFile("allowed", null);
    f2 = new TestVirtualFile("filtered", null);

    expect(tm.isFileIgnored("allowed")).andReturn(false);
    expect(tm.isFileIgnored("filtered")).andReturn(true);
    replay(tm);

    FileFilter f = new FileFilter(fi, tm);

    assertTrue(f.isAllowed(f1));
    assertFalse(f.isAllowed(f2));
  }

  @Test
  public void testDoesNotCheckFileTypeForDirectories() {
    f1 = new TestVirtualFile("dir", null);

    expect(tm.isFileIgnored("dir")).andReturn(false);
    replay(tm);
    FileFilter f = new FileFilter(fi, tm);

    assertTrue(f.isAllowed(f1));
    verify(tm);
  }

  private FileType createFileType(boolean isBinary) {
    FileType t = createMock(FileType.class);
    expect(t.isBinary()).andStubReturn(isBinary);
    replay(t);
    return t;
  }
}
