package org.openjcontroltower.infrastructure;

import org.openjcontroltower.domain.Models.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;

/** Filesystem adapter restricted to one preconfigured repository workspace. */
@Component
public class FileSpecPublisher {
    /** Seam for filesystems (e.g. some network volumes) that reject ATOMIC_MOVE. */
    @FunctionalInterface interface FileMover { Path move(Path source,Path target,CopyOption... options) throws IOException; }
    private final Path base;
    private final FileMover mover;
    @Autowired
    public FileSpecPublisher(@Value("${controltower.repositories-base}") String base) { this(base,Files::move); }
    FileSpecPublisher(String base,FileMover mover) {
        this.base=Path.of(base).toAbsolutePath().normalize();this.mover=mover;
    }
    public Path validateRoot(String rootPath) {
        try {
            Files.createDirectories(base);
            Path allowed=base.toRealPath();
            Path root=Path.of(rootPath).toRealPath();
            if (!root.startsWith(allowed) || root.equals(allowed))
                throw new IllegalArgumentException("Repository must be inside the configured workspace");
            if (!Files.isDirectory(root.resolve(".git")))
                throw new IllegalArgumentException("Repository must contain a .git directory");
            return root;
        } catch (IOException e) { throw new IllegalArgumentException("Repository path is not accessible",e); }
    }
    public synchronized void publish(Project project,Spec spec) {
        try {
            Path root=validateRoot(project.rootPath());
            Path specs=root.resolve("specs");
            if (Files.isSymbolicLink(specs)) throw new IllegalArgumentException("Symbolic-link specs directories are forbidden");
            Files.createDirectories(specs);
            Path folder=specs.resolve(Path.of(spec.specPath()).getParent()).normalize();
            if (!folder.startsWith(specs) || Files.exists(folder))
                throw new IllegalArgumentException("Spec directory already exists or is unsafe");
            Files.createDirectory(folder);
            try {
                Files.writeString(folder.resolve("spec.md"),spec.body(),StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
                Files.writeString(folder.resolve("plan.md"),"# Plan\n\nTo be developed during implementation.\n",StandardOpenOption.CREATE_NEW);
                Files.writeString(folder.resolve("tasks.md"),"# Tasks\n\n- [ ] Implement acceptance criteria.\n",StandardOpenOption.CREATE_NEW);
                Files.writeString(folder.resolve("review.md"),"# Review\n\nPending implementation.\n",StandardOpenOption.CREATE_NEW);
            } catch (IOException e) { throw new IllegalStateException("Partially written spec directory; inspect " + folder,e); }
            Path index=specs.resolve("_index.md");
            String existing=Files.exists(index) ? Files.readString(index) : "# Specification index\n\n";
            atomicReplace(index,existing+"- ["+spec.id()+"]("+spec.specPath()+") — "+spec.title().replace("\n"," ")+"\n");
        } catch (IOException e) { throw new IllegalStateException("Could not publish spec",e); }
    }
    private void atomicReplace(Path file,String content) throws IOException {
        Path temporary=Files.createTempFile(file.getParent(),".index-",".tmp");
        try {
            Files.writeString(temporary,content,StandardCharsets.UTF_8);
            try { mover.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { mover.move(temporary,file,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
