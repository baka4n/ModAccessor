package dev.vfyjxf.gradle.accessor;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

public abstract class MergeAccessTransformerTask extends Copy {

    public abstract ConfigurableFileCollection getAccessTransformerFiles();

    public abstract RegularFileProperty getAccessTransformerFileMergeTo();

    @TaskAction
    public void mergeATFiles() {
        var atFiles = getAccessTransformerFiles().getFiles();
        List<String> list = atFiles.stream()
                .filter(File::exists)
                .filter(File::isFile)
                .flatMap(file -> {
                    try {
                        return Files.readAllLines(file.toPath()).stream();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }).toList();
        File resultFile = getAccessTransformerFileMergeTo().getAsFile().get();
        //在文件不存在是确保文件创建出来
        if (!resultFile.exists()) {
            resultFile.getParentFile().mkdirs();
            try {
                resultFile.createNewFile();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }


    }

}
