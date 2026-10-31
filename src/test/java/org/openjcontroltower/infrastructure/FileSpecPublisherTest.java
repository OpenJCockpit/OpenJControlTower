package org.openjcontroltower.infrastructure;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openjcontroltower.domain.Models.*;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FileSpecPublisherTest {
    @TempDir Path base;
    private Path repo() throws Exception {
        Path repo=Files.createDirectory(base.resolve("repo"));Files.createDirectory(repo.resolve(".git"));return repo;
    }
    private static Spec spec(String id,String body,String path) {
        return new Spec(id,"p","Implement\nlogin","implement-login","DRAFT",body,List.of(),path);
    }
    @Test void writesCockpitCompatibleFolderAndIndex() throws Exception {
        Path repo=repo();
        Project p=new Project("p","Example",repo.toString(),"c","wf");
        Spec spec=spec("SPEC-1","# Login","001-implement-login/spec.md");
        FileSpecPublisher publisher=new FileSpecPublisher(base.toString());
        publisher.publish(p,spec);
        assertEquals("# Login",Files.readString(repo.resolve("specs/001-implement-login/spec.md")));
        assertTrue(Files.exists(repo.resolve("specs/001-implement-login/review.md")));
        assertTrue(Files.readString(repo.resolve("specs/_index.md")).contains("SPEC-1"));
        assertThrows(IllegalArgumentException.class,()->publisher.publish(p,spec));
        publisher.publish(p,spec("SPEC-2","# Two","002-two/spec.md"));
        String index=Files.readString(repo.resolve("specs/_index.md"));
        assertTrue(index.startsWith("# Specification index\n\n- [SPEC-1]"));
        assertTrue(index.contains("- [SPEC-2](002-two/spec.md) — Implement login\n"));
        try (var files=Files.list(repo.resolve("specs"))) {
            assertTrue(files.noneMatch(f->f.getFileName().toString().endsWith(".tmp")),"temporary index files are cleaned up");
        }
    }
    @Test void rejectsOutsideWorkspaceAndNonGitDirectories() throws Exception {
        FileSpecPublisher publisher=new FileSpecPublisher(base.toString());
        assertThrows(IllegalArgumentException.class,()->publisher.validateRoot(base.toString()));
        Path notGit=Files.createDirectory(base.resolve("empty"));
        assertThrows(IllegalArgumentException.class,()->publisher.validateRoot(notGit.toString()));
        assertThrows(IllegalArgumentException.class,()->publisher.validateRoot(base.resolve("missing").toString()));
        Path outside=Files.createTempDirectory("outside");
        Files.createDirectory(outside.resolve(".git"));
        assertThrows(IllegalArgumentException.class,()->publisher.validateRoot(outside.toString()));
    }
    @Test void rejectsSymlinkedSpecsAndTraversal() throws Exception {
        Path repo=repo();
        Project p=new Project("p","Example",repo.toString(),"c","wf");
        FileSpecPublisher publisher=new FileSpecPublisher(base.toString());
        assertThrows(IllegalArgumentException.class,()->publisher.publish(p,spec("S","x","../escape/spec.md")));
        Path elsewhere=Files.createDirectory(base.resolve("elsewhere"));
        Files.delete(repo.resolve("specs"));
        Files.createSymbolicLink(repo.resolve("specs"),elsewhere);
        assertThrows(IllegalArgumentException.class,()->publisher.publish(p,spec("S","x","001-x/spec.md")));
    }
    @Test void reportsPartialAndFailedWrites() throws Exception {
        Path repo=repo();
        Project p=new Project("p","Example",repo.toString(),"c","wf");
        FileSpecPublisher publisher=new FileSpecPublisher(base.toString());
        // An unpaired surrogate cannot be encoded as UTF-8, so the first file write fails.
        IllegalStateException partial=assertThrows(IllegalStateException.class,()->publisher.publish(p,spec("S","\uD800","001-x/spec.md")));
        assertTrue(partial.getMessage().startsWith("Partially written spec directory"));
        // The parent folder of a nested path does not exist: directory creation fails.
        IllegalStateException failed=assertThrows(IllegalStateException.class,()->publisher.publish(p,spec("S","x","missing/002-y/spec.md")));
        assertEquals("Could not publish spec",failed.getMessage());
    }
    @Test void fallsBackWhenAtomicMoveIsUnsupported() throws Exception {
        Path repo=repo();
        List<List<java.nio.file.CopyOption>> moves=new ArrayList<>();
        FileSpecPublisher publisher=new FileSpecPublisher(base.toString(),(source,target,options)->{
            moves.add(Arrays.asList(options));
            if (moves.size()==1) throw new AtomicMoveNotSupportedException(source.toString(),target.toString(),"unsupported");
            return Files.move(source,target,options);
        });
        publisher.publish(new Project("p","Example",repo.toString(),"c","wf"),spec("SPEC-1","# Body","001-x/spec.md"));
        assertEquals(List.of(StandardCopyOption.REPLACE_EXISTING),moves.get(1));
        assertTrue(Files.readString(repo.resolve("specs/_index.md")).contains("SPEC-1"));
    }
}
